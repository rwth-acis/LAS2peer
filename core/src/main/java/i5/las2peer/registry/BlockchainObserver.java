package i5.las2peer.registry;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.utils.Convert;

import i5.las2peer.api.p2p.ServiceNameVersion;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.registry.data.BlockchainTransactionData;
import i5.las2peer.registry.data.GenericTransactionData;
import i5.las2peer.registry.data.SenderReceiverDoubleKey;
import i5.las2peer.registry.data.ServiceDeploymentData;
import i5.las2peer.registry.data.ServiceReleaseData;
import i5.las2peer.registry.exceptions.EthereumException;
import io.reactivex.schedulers.Schedulers;

/**
 * Observes blockchain events related to the registry and updates its state
 * accordingly.
 *
 * The JavaRx Flowables use extra threads so that look-ups etc. don't block the
 * main thread.
 */
class BlockchainObserver {

	List<String> errors;
	/** Profiles to their owners */
	ConcurrentMap<String, String> profiles;

	/** User registrations and time stamps */
	ConcurrentMap<String, String> users;

	/** Tags to their description */
	ConcurrentMap<String, String> tags;

	/** Service names to name of their author/owner */
	ConcurrentMap<String, String> serviceNameToAuthor;

	/** Service names to list of releases */
	ConcurrentMap<String, List<ServiceReleaseData>> releases;

	/** Generic ETH Transactions: (from,to) => List<amount, message> */
	ConcurrentMap<SenderReceiverDoubleKey, List<GenericTransactionData>> genericTransactions;

	ConcurrentMap<SenderReceiverDoubleKey, List<BlockchainTransactionData>> transactionLog;

	/**
	 * Sorted mapping of Blocks to announcements because TreeMap is not thread-safe,
	 * we need to use a custom lock.
	 * https://riptutorial.com/java/example/30472/treemap-and-treeset-thread-safety
	 * this is used for the faucet to see how many svc announcements we did since
	 * the last faucet payout
	 * 
	 * e.g. Block 5 -> "com.example.service" -> List of NodeIDs hosting this service
	 */
	ReentrantReadWriteLock serviceAnnouncementsPerBlockTree__lock = new ReentrantReadWriteLock();
	TreeMap<BigInteger, HashMap<String, List<String>>> serviceAnnouncementsPerBlockTree;

	/**
	 * Nested map from service name and version components to that exact release.
	 *
	 * E.g., "com.example.service" -> 1 -> 0 -> 2 -> release 1.0.2.
	 */
	ConcurrentMap<String, Map<Integer, Map<Integer, Map<Integer, ServiceReleaseData>>>> releasesByVersion;

	/**
	 * Service name to service deployment announcements.
	 *
	 * The value type of this map should really be a set, but we want the ability to
	 * update entries (specifically their timestamps). So instead we use the awkward
	 * workaround of mapping the deployments to themself. Crucially, <b>the value is
	 * the definitive one</b>, i.e., the one with updated timestamp.
	 */
	// Java's Set is too limited; this is exactly the use case we have:
	// https://stackoverflow.com/questions/7283338/getting-an-element-from-a-set
	// so far, this is ugly but fine. maybe hide this under sane accessors
	// FIXME document "end" stuff
	ConcurrentMap<String, Map<ServiceDeploymentData, ServiceDeploymentData>> deployments;

	private static final ConcurrentMap<String, String> hashToNameCache = new ConcurrentHashMap<>();

	private static final Map<Contracts.ContractsConfig, BlockchainObserver> instances = new HashMap<>();

	private Contracts contracts;

	/**
	 * Hashes of transactions that have already been handled.
	 *
	 * The reason for this is that due to chain reorganizations, a transaction can
	 * be mined in several different blocks which are (temporarily) accepted as
	 * being part of the longest chain.
	 *
	 * Deduplicating them in this way is a simple, but ugly way out. It would be
	 * better to have a threshold for confirmed blocks (e.g., latest minus six), and
	 * even better to handle the reorganizations (as mentioned in the issue, geth
	 * has a "removed" field for this exact purpose).
	 *
	 * As long as we don't have truly orphaned blocks / txs, i.e., as long as all tx
	 * are mined in main chain blocks eventually, this approach should be fine.
	 *
	 * @see <a href=
	 *      "https://github.com/ethereum/web3.js/issues/398#issuecomment-189163101">web3.js
	 *      issue describing the same problem</a>
	 */
	private final Set<String> observedEventTxHashes = new HashSet<>();

	private static final L2pLogger logger = L2pLogger.getInstance(BlockchainObserver.class);

	/**
	 * Returns a BlockchainObserver instance for the given contracts configuration.
	 *
	 * The intent is to prevent redundant observers by returning an already existing
	 * instance when possible.
	 * 
	 * @param contractsConfig registry contract addresses and Ethereum client
	 *                        endpoint
	 * @return BlockchainObserver reflecting the contracts' state
	 */
	public static BlockchainObserver getInstance(Contracts.ContractsConfig contractsConfig) {
		logger.fine("Blockchain observer instance requested, looking up ...");
		instances.computeIfAbsent(contractsConfig, BlockchainObserver::new);
		return instances.get(contractsConfig);
	}

	private BlockchainObserver(Contracts.ContractsConfig contractsConfig) {
		logger.fine("Creating new blockchain observer");
		contracts = new Contracts.ContractsBuilder(contractsConfig).build();

		errors = new ArrayList<String>();
		profiles = new ConcurrentHashMap<>();
		users = new ConcurrentHashMap<>();
		tags = new ConcurrentHashMap<>();
		serviceNameToAuthor = new ConcurrentHashMap<>();
		releases = new ConcurrentHashMap<>();
		releasesByVersion = new ConcurrentHashMap<>();
		deployments = new ConcurrentHashMap<>();
		genericTransactions = new ConcurrentHashMap<>();
		transactionLog = new ConcurrentHashMap<>();
		serviceAnnouncementsPerBlockTree = new TreeMap<>();

		observeETHTransactions();
		observeUserVotingTransactions();
		observeGenericTransactions();
		observeErrorEvents();
		observeUserRegistrations();
		observeUserProfileCreations();
		observeTagCreations();
		observeServiceRegistrations();
		observeServiceReleases();
		observeServiceDeployments();
	}

	private boolean txHasAlreadyBeenHandled(String txHash) {
		if (observedEventTxHashes.contains(txHash)) {
			return true;
		} else {
			observedEventTxHashes.add(txHash);
			return false;
		}
	}

	private void observeETHTransactions() {
		contracts.getWeb3jClient().replayPastAndFutureTransactionsFlowable(DefaultBlockParameterName.EARLIEST)
				.observeOn(Schedulers.io()).subscribeOn(Schedulers.io()).subscribe(transaction -> {
					if (txHasAlreadyBeenHandled(transaction.getHash())) {
						return;
					}

					SenderReceiverDoubleKey transactionKey = new SenderReceiverDoubleKey(
							(transaction.getFrom() != null) ? transaction.getFrom() : "", // shouldn't be null
							(transaction.getTo() != null) ? transaction.getTo() : "");

					// prepare l2p-local class to hold transaction info
					BlockchainTransactionData btd = new BlockchainTransactionData(transaction);
					if (transaction.getBlockNumber() != null) {
						// add block timestamp to transaction info
						btd.setBlockTimeStamp(contracts.getBlockTimestamp(btd.getBlockNumber()));
					}

					// https://stackoverflow.com/a/51062494
					transactionLog.computeIfAbsent(transactionKey, k -> new ArrayList<>()).add(btd);

					logger.info("[ChainObserver] observed: " + btd.toString());

				}, e -> {
					e.printStackTrace();
					logger.severe("Error observing transaction event: " + e.toString());
				});
	}

	private void observeUserVotingTransactions() {
		contracts.reputationRegistry
				.transactionAddedEventFlowable(DefaultBlockParameterName.EARLIEST, DefaultBlockParameterName.LATEST)
				.observeOn(Schedulers.io()).subscribeOn(Schedulers.io()).subscribe(transaction -> {
					String txHash = transaction.log.getTransactionHash();
					if (txHasAlreadyBeenHandled(txHash)) {
						return;
					}

					String txSender = Util.getOrDefault(transaction.sender, "uknown sender");
					String txRecipient = Util.getOrDefault(transaction.recipient, "uknown recipient");

					BigInteger grade = Util.getOrDefault(transaction.grade, BigInteger.ZERO);
					BigInteger recipientNewScore = Util.getOrDefault(transaction.recipientNewScore, BigInteger.ZERO);

					BigInteger timestamp = Util.getOrDefault(transaction.timestamp, BigInteger.ZERO);

					SenderReceiverDoubleKey srdk = new SenderReceiverDoubleKey(txSender, txRecipient);
					GenericTransactionData gtd = new GenericTransactionData(txSender, txRecipient, BigInteger.ZERO, // amountInWei
							timestamp, "Rating: " + grade, "L2P USER RATING", txHash);

					if (!this.genericTransactions.containsKey(srdk)) {
						List<GenericTransactionData> lgtd = new ArrayList<GenericTransactionData>();
						lgtd.add(gtd);
						this.genericTransactions.put(srdk, lgtd);
					} else {
						this.genericTransactions.get(srdk).add(gtd);
					}

					logger.info("[ChainObserver] observed user voting: " + "[" + txSender + "]->[" + txRecipient + "]: "
							+ grade + ", new grade: " + recipientNewScore);
				}, e -> logger.severe("Error observing user voting event: " + e.toString()));
	}

	private void observeErrorEvents() {
		contracts.reputationRegistry
				.errorEventEventFlowable(DefaultBlockParameterName.EARLIEST, DefaultBlockParameterName.LATEST)
				.observeOn(Schedulers.io()).subscribeOn(Schedulers.io()).subscribe(error -> {
					if (txHasAlreadyBeenHandled(error.log.getTransactionHash())) {
						return;
					}

					String errorMsg = Util.getOrDefault(error.message, "no error message provided");

					this.errors.add(errorMsg);
					logger.severe("[ChainObserver] observed error event: " + errorMsg);
				}, e -> logger.severe("Error observing error event: " + e.toString()));
	}

	private void observeUserRegistrations() {
		contracts.userRegistry
				.userRegisteredEventFlowable(DefaultBlockParameterName.EARLIEST, DefaultBlockParameterName.LATEST)
				.observeOn(Schedulers.io()).subscribeOn(Schedulers.io()).subscribe(user -> {
					if (txHasAlreadyBeenHandled(user.log.getTransactionHash())) {
						return;
					}

					String userName = Util.recoverString(user.name);
					BigInteger timestamp = user.timestamp;
					Instant i = Instant.ofEpochSecond(timestamp.longValue());

					this.users.put(userName, i.toString());
					logger.info("[ChainObserver] observed user registration: " + "@[" + timestamp + "]: " + userName);
				}, e -> logger.severe("Error observing user registration event: " + e.toString()));
	}

	private void observeUserProfileCreations() {
		contracts.reputationRegistry
				.userProfileCreatedEventFlowable(DefaultBlockParameterName.EARLIEST, DefaultBlockParameterName.LATEST)
				.observeOn(Schedulers.io()).subscribeOn(Schedulers.io()).subscribe(profile -> {
					if (txHasAlreadyBeenHandled(profile.log.getTransactionHash())) {
						return;
					}
					String profileOwner = Util.getOrDefault(profile.owner, "???");
					String profileName = Util.recoverString(profile.name);
					this.profiles.put(profileOwner, profileName);
					logger.info("[ChainObserver] observed profile creation: [" + profileOwner + "]: " + profileName);

				}, e -> logger.severe("Error observing profile creation event: " + e.toString()));
	}

	public List<GenericTransactionData> getTransactionLogBySender(String sender) {
		List<GenericTransactionData> transactionList = new ArrayList<>();
		logger.fine("[TXLOG] searching for sender: " + sender);
		Set<SenderReceiverDoubleKey> transactions = genericTransactions.keySet();
		for (SenderReceiverDoubleKey key : transactions) {
			logger.fine("[TXLOG] analyzing key: " + key.toString());
			logger.fine("[TXLOG] " + key.getSender() + "|" + sender);
			if (key.equalsSender(sender) || key.getSender() == sender) {
				List<GenericTransactionData> keyTransactionList = genericTransactions.get(key);
				logger.fine("[TXLOG] match found! " + keyTransactionList.size() + " entries");
				for (GenericTransactionData genericTransactionData : keyTransactionList) {
					transactionList.add(genericTransactionData);
				}
			}
		}
		if (transactionList.size() > 0) {
			logger.fine("[TXLOG] found " + transactionList.size() + " entries for " + sender);
		}
		return transactionList;
	}

	public List<GenericTransactionData> getTransactionLogByReceiver(String receiver) {
		List<GenericTransactionData> transactionList = new ArrayList<>();
		logger.fine("[TXLOG] searching for receiver: " + receiver);
		Set<SenderReceiverDoubleKey> transactions = genericTransactions.keySet();
		for (SenderReceiverDoubleKey key : transactions) {
			logger.fine("[TXLOG] analyzing key: " + key.toString());
			logger.fine("[TXLOG] " + key.getReceiver() + "|" + receiver);
			if (key.equalsReceiver(receiver) || key.getReceiver() == receiver) {
				List<GenericTransactionData> keyTransactionList = genericTransactions.get(key);
				logger.fine("[TXLOG] match found! " + keyTransactionList.size() + " entries");
				for (GenericTransactionData genericTransactionData : keyTransactionList) {
					transactionList.add(genericTransactionData);
				}
			}
		}
		if (transactionList.size() > 0) {
			logger.fine("[TXLOG] found " + transactionList.size() + " entries for " + receiver);
		}
		return transactionList;
	}

	private void observeGenericTransactions() {
		contracts.reputationRegistry
				.genericTransactionAddedEventFlowable(DefaultBlockParameterName.EARLIEST,
						DefaultBlockParameterName.LATEST)
				.observeOn(Schedulers.io()).subscribeOn(Schedulers.io()).subscribe(transaction -> {
					if (txHasAlreadyBeenHandled(transaction.log.getTransactionHash())) {
						return;
					}
					String txSender = Util.getOrDefault(transaction.sender, "uknown sender");
					String txRecipient = Util.getOrDefault(transaction.recipient, "uknown recipient");

					String message = Util.getOrDefault(transaction.message, "no message");
					BigInteger weiAmount = Util.getOrDefault(transaction.weiAmount, BigInteger.ZERO);
					BigInteger timestamp = Util.getOrDefault(transaction.timestamp, BigInteger.ZERO);
					String transactionType = Util.getOrDefault(transaction.transactionType, "no txType");
					String txHash = Util.getOrDefault(transaction.txHash, "no txHash");

					SenderReceiverDoubleKey srdk = new SenderReceiverDoubleKey(txSender, txRecipient);
					GenericTransactionData gtd = new GenericTransactionData(txSender, txRecipient, weiAmount, timestamp,
							message, transactionType, txHash);

					if (!this.genericTransactions.containsKey(srdk)) {
						List<GenericTransactionData> lgtd = new ArrayList<GenericTransactionData>();
						lgtd.add(gtd);
						this.genericTransactions.put(srdk, lgtd);
					} else {
						this.genericTransactions.get(srdk).add(gtd);
					}

					String wei = Convert.fromWei(weiAmount.toString(), Convert.Unit.ETHER).toString();

					logger.info("[ChainObserver] observed generic transaction: [" + txSender + "->" + txRecipient + "]@"
							+ wei + ": " + message);

				}, e -> logger.severe("Error observing generic transaction: " + e.toString()));
	}

	private void observeTagCreations() {
		contracts.communityTagIndex
				.communityTagCreatedEventFlowable(DefaultBlockParameterName.EARLIEST, DefaultBlockParameterName.LATEST)
				.observeOn(Schedulers.io()).subscribeOn(Schedulers.io()).subscribe(tag -> {
					if (!txHasAlreadyBeenHandled(tag.log.getTransactionHash())) {
						String tagName = Util.recoverString(tag.name);

						// same issue as lookUpServiceName; let's just retry
						String tagDescription = "";
						for (int i = 0; i < 5; i++) {
							tagDescription = contracts.communityTagIndex
									.viewDescription(Util.padAndConvertString(tagName, 32)).sendAsync().get();
							if (!tagDescription.isEmpty()) {
								break;
							}
							logger.warning("Tag description returned empty, retrying");
						}
						logger.info("[ChainObserver] observed tag creation: " + tagName);
						tags.put(tagName, tagDescription);
					}
				}, e -> logger.severe("Error observing tag event: " + e.toString()));
	}

	/**
	 * Looks up service name from hash with caching and retry if unavailable.
	 *
	 * Result is cached because this is an RPC call and thus expensive (very roughly
	 * ~100ms, too expensive to call thousands of times).
	 *
	 * We retry because a chain reorganization can mean that we try to query the
	 * name in the brief window where the most recent block(s) becomes orphaned by a
	 * slightly longer chain segment, but before its transactions are included in
	 * one of those main-chain blocks. (As of this writing, that window is less than
	 * a second long, so one or two retries should do the trick.)
	 *
	 * This method is hopefully thread-safe, since it only modifies the cache using
	 * {@link java.util.concurrent.ConcurrentHashMap#putIfAbsent}.
	 *
	 * @param hashOfName raw hash bytes as returned by the smart contract
	 * @return service name
	 */
	private String lookupServiceName(byte[] hashOfName) throws EthereumException {
		String hashAsString = new String(hashOfName); // this is not human readable
		if (hashToNameCache.containsKey(hashAsString)) {
			return hashToNameCache.get(hashAsString);
		}
		logger.fine("Cache miss for " + Util.bytesToHexString(hashOfName) + ", querying Eth client ...");

		int retries = 120;
		int wait = 500;
		for (int i = 0; i < retries; i++) {
			try {
				logger.finer("Lookup attempt " + i + "  querying Eth client ...");
				String serviceName = contracts.serviceRegistry.hashToName(hashOfName).sendAsync().get();
				if (!serviceName.isEmpty()) {
					logger.finer("... succeeded.");
					hashToNameCache.putIfAbsent(hashAsString, serviceName);
					return serviceName;
				}
				logger.finer("... still not available, retrying.");
				Thread.sleep(wait);
			} catch (Exception e) {
				logger.severe("Error while looking up service name: " + e.getMessage());
			}
		}
		throw new EthereumException("Could not look up service name");
	}

	private void observeServiceRegistrations() {
		contracts.serviceRegistry
				.serviceCreatedEventFlowable(DefaultBlockParameterName.EARLIEST, DefaultBlockParameterName.LATEST)
				.observeOn(Schedulers.io()).subscribeOn(Schedulers.io()).subscribe(service -> {
					if (!txHasAlreadyBeenHandled(service.log.getTransactionHash())) {
						String serviceName = lookupServiceName(service.nameHash);
						serviceNameToAuthor.put(serviceName, Util.recoverString(service.author));
						logger.info("[ChainObserver] observed service registration :\n" + " > " + serviceName + " by "
								+ Util.recoverString(service.author));
					}
				}, e -> logger.severe("Error observing service registration event: " + e.toString()));
	}

	private void observeServiceReleases() {
		contracts.serviceRegistry
				.serviceReleasedEventFlowable(DefaultBlockParameterName.EARLIEST, DefaultBlockParameterName.LATEST)
				.observeOn(Schedulers.io()).subscribeOn(Schedulers.io()).subscribe(release -> {
					if (!txHasAlreadyBeenHandled(release.log.getTransactionHash())) {
						String serviceName = lookupServiceName(release.nameHash);
						ServiceReleaseData releaseData = new ServiceReleaseData(serviceName, release.versionMajor,
								release.versionMinor, release.versionPatch, release.hash, release.timestamp);

						releases.computeIfAbsent(releaseData.getServiceName(), k -> new ArrayList<>());
						releases.get(releaseData.getServiceName()).add(releaseData);
						storeReleaseByVersion(releaseData);
					}
				}, e -> logger.severe("Error observing service release event: " + e.toString()));
	}

	private void observeServiceDeployments() {
		// service deployment announcements and re-announcements
		contracts.serviceRegistry
				.serviceDeploymentEventFlowable(DefaultBlockParameterName.EARLIEST, DefaultBlockParameterName.LATEST)
				.observeOn(Schedulers.io()).subscribeOn(Schedulers.io()).subscribe(deployment -> {
					if (!txHasAlreadyBeenHandled(deployment.log.getTransactionHash())) {
						String serviceName = lookupServiceName(deployment.nameHash);
						ServiceDeploymentData deploymentData = new ServiceDeploymentData(serviceName,
								deployment.className, deployment.versionMajor, deployment.versionMinor,
								deployment.versionPatch, deployment.nodeId, deployment.timestamp);
						addOrUpdateDeployment(deploymentData);

						// save announcement log:
						// for each block number
						// for each service (mobsos success model is currently not using service version
						// anyway)
						// save nodeID who is hosting / has deployed this service
						// used for eth faucet calculations, where we count the number of announcements
						// since block X
						serviceAnnouncementsPerBlockTree__lock.writeLock().lock();
						try {
							serviceAnnouncementsPerBlockTree
									.computeIfAbsent(deployment.log.getBlockNumber(), k -> new HashMap<>())
									.computeIfAbsent(serviceName + "." + deployment.className, k -> new ArrayList<>())
									.add(deployment.nodeId);

							logger.info("[ChainObserver] observed service announcement (" + serviceName + "."
									+ deployment.className + "): \n" + "block #: " + deployment.log.getBlockNumber()
									+ "\n" + "node  #:" + deployment.nodeId);
						} finally {
							serviceAnnouncementsPerBlockTree__lock.writeLock().unlock();
						}
					}
				}, e -> logger.severe("Error observing service deployment event: " + e.toString()));

		contracts.serviceRegistry
				.clusterServiceDeploymentEventFlowable(DefaultBlockParameterName.EARLIEST,
						DefaultBlockParameterName.LATEST)
				.observeOn(Schedulers.io()).subscribeOn(Schedulers.io()).subscribe(deployment -> {
					if (!txHasAlreadyBeenHandled(deployment.log.getTransactionHash())) {
						String serviceName = lookupServiceName(deployment.nameHash);
						ServiceDeploymentData deploymentData = new ServiceDeploymentData(serviceName,
								deployment.clusterServiceName, deployment.versionMajor, deployment.versionMinor,
								deployment.versionPatch, deployment.timestamp, deployment.hash);
						addOrUpdateDeployment(deploymentData);

						// save announcement log:
						// for each block number
						// for each service (mobsos success model is currently not using service version
						// anyway)
						// save nodeID who is hosting / has deployed this service
						// used for eth faucet calculations, where we count the number of announcements
						// since block X
						serviceAnnouncementsPerBlockTree__lock.writeLock().lock();
						try {
							serviceAnnouncementsPerBlockTree
									.computeIfAbsent(deployment.log.getBlockNumber(), k -> new HashMap<>())
									.computeIfAbsent(serviceName + "." + "", k -> new ArrayList<>()).add("");

							logger.info("[ChainObserver] observed cluster service announcement (" + serviceName + "."
									+ "" + "): \n" + "block #: " + deployment.log.getBlockNumber() + "\n" + "node  #:"
									+ "");
						} finally {
							serviceAnnouncementsPerBlockTree__lock.writeLock().unlock();
						}
					}
				}, e -> logger.severe("Error observing service deployment event: " + e.toString()));

		// *end* of service deployment announcements
		// FIXME: this should work almost always, but it would be far safer to actually
		// add a timestamp
		// to the end event too, so that if (for whatever reason) the events are very
		// out of order, we
		// don't accidentally kill a newer deployment
		// (this would only last until the re-announcement anyway. still.)
		// TODO: yeah, this is definitely needed
		contracts.serviceRegistry
				.serviceDeploymentEndEventFlowable(DefaultBlockParameterName.EARLIEST, DefaultBlockParameterName.LATEST)
				.observeOn(Schedulers.io()).subscribeOn(Schedulers.io()).subscribe(stopped -> {
					if (!txHasAlreadyBeenHandled(stopped.log.getTransactionHash())) {
						String serviceName = lookupServiceName(stopped.nameHash);

						// for comparison only; remember: this event signifies the END of a deployment,
						// not actually a deployment
						ServiceDeploymentData deploymentThatEnded = new ServiceDeploymentData(serviceName,
								stopped.className, stopped.versionMajor, stopped.versionMinor, stopped.versionPatch,
								stopped.nodeId, stopped.timestamp, true);
						addOrUpdateDeployment(deploymentThatEnded);
					}
				}, e -> logger.severe("Error observing service deployment end event: " + e.toString()));

		contracts.serviceRegistry
				.clusterServiceDeploymentEndEventFlowable(DefaultBlockParameterName.EARLIEST,
						DefaultBlockParameterName.LATEST)
				.observeOn(Schedulers.io()).subscribeOn(Schedulers.io()).subscribe(stopped -> {
					if (!txHasAlreadyBeenHandled(stopped.log.getTransactionHash())) {
						String serviceName = lookupServiceName(stopped.nameHash);

						// for comparison only; remember: this event signifies the END of a deployment,
						// not actually a deployment
						ServiceDeploymentData deploymentThatEnded = new ServiceDeploymentData(serviceName,
								stopped.clusterServiceName, stopped.versionMajor, stopped.versionMinor,
								stopped.versionPatch, stopped.timestamp, true);
						addOrUpdateDeployment(deploymentThatEnded);
					}
				}, e -> logger.severe("Error observing cluster service deployment end event: " + e.toString()));
	}

	public HashMap<String, Integer> getNoOfServiceAnnouncementSinceBlockOrderedByHostingNode(
			BigInteger largerThanBlockNo, String searchingForService) {
		// NodeID -> no. of Announcements
		HashMap<String, Integer> retVal = new HashMap<>();
		int sumOfAnn = 0;

		serviceAnnouncementsPerBlockTree__lock.readLock().lock();
		try {
			SortedMap<BigInteger, HashMap<String, List<String>>> tailMap = serviceAnnouncementsPerBlockTree
					.tailMap(largerThanBlockNo);

			logger.info("[ChainObserver] searching for announcements of '" + searchingForService
					+ "', starting from block #" + largerThanBlockNo + ".");
			logger.info("                searching through " + tailMap.size() + " / "
					+ serviceAnnouncementsPerBlockTree.size() + " blocks due to ordering");

			for (Map.Entry<BigInteger, HashMap<String, List<String>>> entry : tailMap.entrySet()) {
				BigInteger announcedBlockNo = entry.getKey();
				HashMap<String, List<String>> serviceMap = entry.getValue();
				logger.fine("[ChainObserver]   processing block # " + announcedBlockNo + ":");
				for (Map.Entry<String, List<String>> innerEntry : serviceMap.entrySet()) {
					String announcedServiceName = innerEntry.getKey();
					List<String> hostingNodeIDList = innerEntry.getValue();
					// is this the service we're looking for?
					if (announcedServiceName.equals(searchingForService)) {
						logger.fine("[ChainObserver]     found service " + announcedServiceName + ", running at "
								+ hostingNodeIDList.size() + " nodes");
						// yes -> count no. of increments per hosting node
						hostingNodeIDList.forEach(hostingNodeID -> {
							retVal.merge(hostingNodeID, 1, (a, b) -> a + b);
						});
						sumOfAnn += 1;
					}
				}
			}
			retVal.put("_totalNoOfServiceAnnouncements", Integer.valueOf(sumOfAnn));
		} finally {
			serviceAnnouncementsPerBlockTree__lock.readLock().unlock();
		}
		retVal.forEach((nodeID, noOfAnnouncements) -> {
			logger.info("[ChainObserver] found announcement count: \n" + "                 nodeID: " + nodeID + "\n"
					+ "                noOfAnn: " + noOfAnnouncements);
		});
		return retVal;
	}

	/**
	 * If an older entry (identical except for lower timestamp) exists, update the
	 * timestamp. Otherwise just add the entry.
	 * 
	 * @param deployment deployment data to be added
	 */
	private void addOrUpdateDeployment(ServiceDeploymentData deployment) {
		deployments.computeIfAbsent(deployment.getServicePackageName(), k -> new HashMap<>());
		Map<ServiceDeploymentData, ServiceDeploymentData> existingDeployments = deployments
				.get(deployment.getServicePackageName());
		if (existingDeployments.containsKey(deployment)) {
			System.out.println("Deployment contained, maybe replacing");
			// other entry -- older or newer (or identical) -- already exists
			if (existingDeployments.get(deployment).getTimestamp().compareTo(deployment.getTimestamp()) < 0) {
				// existing entry is older => update
				System.out.println("Deployment contained,  replacing now");
				existingDeployments.replace(deployment, deployment);
			}
		} else {
			System.out.println("Deployment not contained,  not replacing but adding");
			existingDeployments.put(deployment, deployment);
		}
	}

	private void storeReleaseByVersion(ServiceReleaseData release) {
		// store release under "name -> x -> y -> z -> release" (i.e. essentially a map
		// of the name and the version triple)
		releasesByVersion.computeIfAbsent(release.getServiceName(), k -> new HashMap<>());
		releasesByVersion.get(release.getServiceName()).computeIfAbsent(release.getVersionMajor(),
				k -> new HashMap<>());
		releasesByVersion.get(release.getServiceName()).get(release.getVersionMajor())
				.computeIfAbsent(release.getVersionMinor(), k -> new HashMap<>());
		if (releasesByVersion.get(release.getServiceName()).get(release.getVersionMajor())
				.get(release.getVersionMinor()).containsKey(release.getVersionPatch())) {
			logger.warning("Tried to store duplicate release. Ignoring. FIXME: someone is misbehaving.");
		} else {
			releasesByVersion.get(release.getServiceName()).get(release.getVersionMajor())
					.get(release.getVersionMinor()).put(release.getVersionPatch(), release);
		}
	}

	/**
	 * Safely accesses the nested map, returning null if the entry does not exist.
	 */
	ServiceReleaseData getReleaseByVersion(String serviceName, int versionMajor, int versionMinor, int versionPatch) {
		try {
			return releasesByVersion.get(serviceName).get(versionMajor).get(versionMinor).get(versionPatch);
		} catch (NullPointerException e) {
			return null;
		}
	}
}
