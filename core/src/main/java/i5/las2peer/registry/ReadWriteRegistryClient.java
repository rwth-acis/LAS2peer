package i5.las2peer.registry;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import org.web3j.abi.datatypes.Function;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.tx.Contract;
import org.web3j.tx.FastRawTransactionManager;
import org.web3j.tx.Transfer;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.StaticGasProvider;
import org.web3j.utils.Convert;
import org.web3j.utils.Convert.Unit;

import i5.las2peer.api.security.AgentLockedException;
import i5.las2peer.registry.contracts.ServiceRegistry;
import i5.las2peer.registry.contracts.UserRegistry;
import i5.las2peer.registry.data.RegistryConfiguration;
import i5.las2peer.registry.exceptions.EthereumException;
import i5.las2peer.security.EthereumAgent;
import i5.las2peer.serialization.SerializationException;
import i5.las2peer.serialization.SerializeTools;

/**
 * Facade providing simple read/write access to the registry smart contracts.
 *
 * Requires Ethereum credentials with sufficient Ether funds to pay for
 * transaction gas.
 *
 * @see ReadOnlyRegistryClient
 */
public class ReadWriteRegistryClient extends ReadOnlyRegistryClient {
	// see
	// https://www.reddit.com/r/ethereum/comments/5g8ia6/attention_miners_we_recommend_raising_gas_limit/
	BigInteger GAS_PRICE = BigInteger.valueOf(20_000_000_000L);

	// http://ethereum.stackexchange.com/questions/1832/cant-send-transaction-exceeds-block-gas-limit-or-intrinsic-gas-too-low
	BigInteger GAS_LIMIT_ETHER_TX = BigInteger.valueOf(21_000);

	/**
	 * Create client providing access to both read and write registry functions.
	 * 
	 * @param registryConfiguration addresses of registry contracts and Ethereum
	 *                              client HTTP JSON RPC API endpoint
	 */
	public ReadWriteRegistryClient(RegistryConfiguration registryConfiguration, Credentials credentials) {
		super(registryConfiguration, credentials);
	}

	/**
	 * Create new tag on blockchain.
	 * 
	 * @param tagName        tag name consisting of 1 to 32 UTF-8 characters
	 * @param tagDescription tag description of arbitrary length
	 * @throws EthereumException if transaction failed for some reason (gas?
	 *                           networking?)
	 */
	public void createTag(String tagName, String tagDescription) throws EthereumException {
		try {
			contracts.communityTagIndex.create(Util.padAndConvertString(tagName, 32), tagDescription).sendAsync().get();
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Tag name invalid (too long?)", e);
		} catch (Exception e) {
			throw new EthereumException(e);
		}
	}

	/**
	 * Register an Ethereum agent in the blockchain.
	 *
	 * This will register the user (login) name to the agent's Ethereum account and
	 * store some additional fields that help others verify the user's identity.
	 * (E.g., the las2peer public key is stored in order to verify message
	 * signatures etc.)
	 *
	 * As with the other delegated registry methods, the registration transaction
	 * itself is performed by the node operator; but the agent's credentials are
	 * used to sign a message consenting to the registration. The registry smart
	 * contracts verify that signature and perform the registration as if the
	 * transaction came directly from the agent. (This simply means that the user
	 * name is registered to the agent's account, not the node operator's.)
	 *
	 * This means that the node operator pays the transaction fees.
	 */
	public void registerUser(EthereumAgent agent)
			throws EthereumException, AgentLockedException, SerializationException {
		// TODO: check that this encoding is appropriate
		// (e.g., is there a more space-efficient way to store the public key?
		// probably.)
		// TODO: reconsider which fields are actually strictly necessary
		// (e.g., since we have login name and pubkey, do we really need the agentId?
		// what for?)
		byte[] name = Util.padAndConvertString(agent.getLoginName(), 32);
		byte[] agentId = Util.padAndConvertString(agent.getIdentifier(), 128);
		byte[] publicKey = SerializeTools.serialize(agent.getPublicKey());

		final Function function = new Function(UserRegistry.FUNC_REGISTER,
				Arrays.asList(new org.web3j.abi.datatypes.generated.Bytes32(name),
						new org.web3j.abi.datatypes.DynamicBytes(agentId),
						new org.web3j.abi.datatypes.DynamicBytes(publicKey)),
				Collections.emptyList());

		String consentee = agent.getEthereumAddress();
		byte[] signature = SignatureUtils.signFunctionCall(function, agent.getEthereumCredentials());

		if (txMan != null) {
			BigInteger txManNonce = txMan.getCurrentNonce();
			logger.info("[TX Nonce] before: " + txManNonce);
		}
		try {
			System.out.println("rreesseeettting  bbeefoorrre");

			System.out.println(txMan.getCurrentNonce());
			System.out.println("rreesseeettting");
			txMan.resetNonce();
			System.out.println("rreesseeettting  aaaaafftteeer");
			System.out.println(txMan.getCurrentNonce());
			System.out.println(txMan.getFromAddress());
			System.out.println(txMan.toString());

		} catch (Exception e) {
			System.out.println("EXCEEEEEEPPTIOOON" + e.toString());
		}
		System.out.println(contracts.userRegistry.getContractAddress());
		updateNonceTxMan = true;
		updateTxManNonce("0xb5A66D27457Af8be2a09F17adD73c2ae46520e69");
		try {
			System.out.println("calling reggii");

			contracts.userRegistry.delegatedRegister(name, agentId, publicKey, consentee, signature).sendAsync().get();
		} catch (Exception e) {
			throw new EthereumException("Could not register user", e);
		}
		if (txMan != null) {
			BigInteger txManNonce = txMan.getCurrentNonce();
			logger.info("[TX Nonce] after: " + txManNonce);
		}
	}

	public String registerReputationProfile(EthereumAgent agent) throws EthereumException {
		if (!agent.hasLoginName()) {
			throw new EthereumException("Could not create reputation profile: agent has no login name");
		}
		String agentAddress = agent.getEthereumAddress();
		byte[] profileName = Util.padAndConvertString(agent.getLoginName(), 32);
		logger.info("[Reputation] registering profile: " + agent.getLoginName());
		String txHash;
		try {
			TransactionReceipt txR = contracts.reputationRegistry.createProfile(agentAddress, profileName).sendAsync()
					.get();
			if (!txR.isStatusOK()) {
				logger.warning("trx fail with status " + txR.getStatus());
				logger.warning("gas used " + txR.getCumulativeGasUsed());
				logger.warning(txR.toString());
				throw new EthereumException("could not send transaction, transaction receipt not ok");
			}
			txHash = txR.getTransactionHash();
			if (txR != null) {
				Contracts.addTransactionReceipt(txR);
			}
			// waitForTransactionReceipt(txHash);
		} catch (Exception e) {
			throw new EthereumException("couldn't execute smart contract function call", e);
		}
		logger.info("[Reputation] opt-in complete. ");
		return txHash;
	}

	public String addGenericTransaction(EthereumAgent senderAgent, EthereumAgent receivingAgent, String message,
			BigInteger weiAmount) throws EthereumException {
		logger.info("[TX] adding generic transaction to: " + receivingAgent.getLoginName() + " | amount: "
				+ weiAmount.toString());
		logger.info("[TX] transaction message: " + message + "");

		String etherSendTxHash = sendEtherManaged(senderAgent.getEthereumAddress(), receivingAgent.getEthereumAddress(),
				weiAmount);
		// sendEther(receivingAgent.getEthereumAddress(),
		// Convert.toWei(weiAmount.toString(), Convert.Unit.ETHER));
		// sendEther(senderAgent.getEthereumAddress(),
		// receivingAgent.getEthereumAddress(), weiAmount).getTransactionHash();
		// waitForTransactionReceipt(etherSendTxHash);
		logger.info("[TX] sent funds, adding smart contract event for message and transaction type, txHash: "
				+ etherSendTxHash);

		BigInteger timestamp = BigInteger.valueOf(java.lang.System.currentTimeMillis());
		String txHash;
		try {
			TransactionReceipt txR = contracts.reputationRegistry
					.addGenericTransaction(receivingAgent.getEthereumAddress(), weiAmount, timestamp, etherSendTxHash,
							message, "GENERIC")
					.sendAsync().get();
			if (!txR.isStatusOK()) {
				logger.warning("trx fail with status " + txR.getStatus());
				logger.warning("gas used " + txR.getCumulativeGasUsed());
				logger.warning(txR.toString());
				throw new EthereumException("could not send transaction, transaction receipt not ok");
			}
			txHash = txR.getTransactionHash();
			if (txR != null) {
				Contracts.addTransactionReceipt(txR);
			}
			// waitForTransactionReceipt(txHash);
		} catch (Exception e) {
			throw new EthereumException("couldn't execute smart contract function call", e);
		}
		return txHash;
	}

	public String addUserRating(EthereumAgent receivingAgent, Integer rating) throws EthereumException {

		logger.info("rating user profile: " + receivingAgent.getLoginName() + " | " + rating + "("
				+ BigInteger.valueOf(rating).toString() + ")");

		// see reputation registry
		if (rating > RegistryConfiguration.UserRating_maxRatingValue
				|| rating < RegistryConfiguration.UserRating_minRatingValue) {
			throw new EthereumException("Could not add user rating: rating outside bounds ["
					+ RegistryConfiguration.UserRating_minRatingValue + " - "
					+ RegistryConfiguration.UserRating_maxRatingValue + "]");
		}

		BigInteger timestamp = BigInteger.valueOf(java.lang.System.currentTimeMillis());

		String txHash;
		try {
			TransactionReceipt txR = contracts.reputationRegistry
					.addTransaction(receivingAgent.getEthereumAddress(), BigInteger.valueOf(rating), timestamp)
					.sendAsync().get();
			if (!txR.isStatusOK()) {
				logger.warning("trx fail with status " + txR.getStatus());
				logger.warning("gas used " + txR.getCumulativeGasUsed());
				logger.warning(txR.toString());
				throw new EthereumException("could not send transaction, transaction receipt not ok");
			}
			txHash = txR.getTransactionHash();
			if (txR != null) {
				Contracts.addTransactionReceipt(txR);
			}
			// waitForTransactionReceipt(txHash);
		} catch (Exception e) {
			throw new EthereumException("couldn't execute smart contract function call", e);
		}
		return txHash;
	}

	public boolean hasReputationProfile(String profileName) throws EthereumException {
		Boolean hasProfile = Boolean.FALSE;
		try {
			hasProfile = contracts.reputationRegistry.hasProfile(profileName).sendAsync().get();
		} catch (Exception e) {
			throw new EthereumException("cannot check if profile exists");
		}
		return hasProfile.booleanValue();
	}

	/**
	 * Register a service name to the given author.
	 *
	 * The registration call is delegated, see description of
	 * {@link #registerUser(EthereumAgent)} for details.
	 *
	 * @param serviceName service (package) name of arbitrary length
	 * @param agent       Ethereum agent of the service author
	 */
	public void registerService(String serviceName, EthereumAgent agent)
			throws EthereumException, AgentLockedException {
		byte[] authorName = Util.padAndConvertString(agent.getLoginName(), 32);

		final Function function = new Function(ServiceRegistry.FUNC_REGISTER,
				Arrays.asList(new org.web3j.abi.datatypes.Utf8String(serviceName),
						new org.web3j.abi.datatypes.generated.Bytes32(authorName)),
				Collections.emptyList());

		String consentee = agent.getEthereumAddress();
		byte[] signature = SignatureUtils.signFunctionCall(function, agent.getEthereumCredentials());

		try {
			contracts.serviceRegistry.delegatedRegister(serviceName, authorName, consentee, signature).sendAsync()
					.get();
		} catch (Exception e) {
			throw new EthereumException("Failed to register service", e);
		}
	}

	/** @see #releaseService(String, int, int, int, EthereumAgent, byte[]) */
	public void releaseService(String serviceName, String versionString, EthereumAgent author)
			throws EthereumException, AgentLockedException {
		releaseService(serviceName, versionString, author, null);
	}

	/** @see #releaseService(String, int, int, int, EthereumAgent, byte[]) */
	public void releaseService(String serviceName, String versionString, EthereumAgent author, byte[] supplementHash)
			throws EthereumException, AgentLockedException {
		int[] version = Util.parseVersion(versionString);
		releaseService(serviceName, version[0], version[1], version[2], author, supplementHash);
	}

	/** @see #releaseService(String, int, int, int, EthereumAgent, byte[]) */
	public void releaseService(String serviceName, int versionMajor, int versionMinor, int versionPatch,
			EthereumAgent author) throws EthereumException, AgentLockedException {
		releaseService(serviceName, versionMajor, versionMinor, versionPatch, author, null);
	}

	/**
	 * Announce the release of a specific version of a service.
	 *
	 * The release call is delegated, see description of
	 * {@link #registerUser(EthereumAgent)} for details.
	 *
	 * @param serviceName    name of existing service
	 * @param versionMajor   major version, as used in semantic versioning
	 * @param versionMinor   minor version, as used in semantic versioning
	 * @param versionPatch   patch version, as used in semantic versioning
	 * @param author         agent of author of this version
	 * @param supplementHash hash of supplemental data in shared storage
	 */
	public void releaseService(String serviceName, int versionMajor, int versionMinor, int versionPatch,
			EthereumAgent author, byte[] supplementHash) throws EthereumException, AgentLockedException {
		if (observer.getReleaseByVersion(serviceName, versionMajor, versionMinor, versionPatch) != null) {
			logger.warning("Tried to submit duplicate release (name / version already exist), ignoring!");
			// TODO: handle in contracts, cause this is a race condition
			return;
		}

		byte[] authorName = Util.padAndConvertString(author.getLoginName(), 32);

		// TODO: hash parameter is unused. instead, we check whether the library
		// NetworkArtifact signature matches the
		// author (pubkey registered in the blockchain), and consider that good enough
		// (this leaves *some* room for undesired behavior: an author modifying his node
		// code and replacing an already
		// released version, but the potential for abuse seems pretty low)
		// NOTE: actually, hash is now (ab)used for supplement (which happens to also be
		// a hash)

		final Function function = new Function(ServiceRegistry.FUNC_RELEASE,
				Arrays.asList(new org.web3j.abi.datatypes.Utf8String(serviceName),
						new org.web3j.abi.datatypes.generated.Bytes32(authorName),
						new org.web3j.abi.datatypes.generated.Uint256(versionMajor),
						new org.web3j.abi.datatypes.generated.Uint256(versionMinor),
						new org.web3j.abi.datatypes.generated.Uint256(versionPatch),
						new org.web3j.abi.datatypes.DynamicBytes(supplementHash)),
				Collections.emptyList());

		String consentee = author.getEthereumAddress();
		byte[] signature = SignatureUtils.signFunctionCall(function, author.getEthereumCredentials());

		try {
			contracts.serviceRegistry.delegatedRelease(serviceName, authorName, BigInteger.valueOf(versionMajor),
					BigInteger.valueOf(versionMinor), BigInteger.valueOf(versionPatch), supplementHash, consentee,
					signature).sendAsync().get();
		} catch (Exception e) {
			throw new EthereumException("Failed to submit service release", e);
		}
	}

	/**
	 * Announce the deployment of a specific version of a service at the given node.
	 * 
	 * @param servicePackageName name of service (package). E.g.
	 *                           <code>com.example.some.package</code>.
	 * @param serviceClassName   name of the service class that is run. E.g.,
	 *                           <code>ExampleService</code>.
	 * @param versionMajor       major version, as used in semantic versioning
	 * @param versionMinor       minor version, as used in semantic versioning
	 * @param versionPatch       patch version, as used in semantic versioning
	 * @param nodeId             identifier of the node running the deployment
	 */
	public void announceDeployment(String servicePackageName, String serviceClassName, int versionMajor,
			int versionMinor, int versionPatch, String nodeId) throws EthereumException {
		try {
			contracts.serviceRegistry
					.announceDeployment(servicePackageName, serviceClassName, BigInteger.valueOf(versionMajor),
							BigInteger.valueOf(versionMinor), BigInteger.valueOf(versionPatch), nodeId)
					.sendAsync().get();
		} catch (Exception e) {
			throw new EthereumException("Failed to submit service deployment announcement (" + "DEBUG: "
					+ serviceClassName + ", " + e.getMessage() + ")", e);
		}
	}

	/**
	 * Announce that a service deployment is stopping or no longer accessible.
	 * 
	 * @param servicePackageName name of service (package). E.g.
	 *                           <code>com.example.some.package</code>.
	 * @param serviceClassName   name of the service class that is run. E.g.,
	 *                           <code>ExampleService</code>.
	 * @param versionMajor       major version, as used in semantic versioning
	 * @param versionMinor       minor version, as used in semantic versioning
	 * @param versionPatch       patch version, as used in semantic versioning
	 * @param nodeId             identifier of the node running the deployment
	 */
	// TODO: is there a more elegant way? referencing the tx is possible of course
	// but not really preferable
	public void announceDeploymentEnd(String servicePackageName, String serviceClassName, int versionMajor,
			int versionMinor, int versionPatch, String nodeId) throws EthereumException {
		try {
			contracts.serviceRegistry
					.announceDeploymentEnd(servicePackageName, serviceClassName, BigInteger.valueOf(versionMajor),
							BigInteger.valueOf(versionMinor), BigInteger.valueOf(versionPatch), nodeId)
					.sendAsync().get();
		} catch (Exception e) {
			throw new EthereumException("Failed to submit service deployment *end* announcement (" + "DEBUG: "
					+ serviceClassName + ", " + e.getMessage() + ")", e);
		}
	}

	public String sendEtherManaged(String senderAddress, String recipientAddress, BigInteger value)
			throws EthereumException {
		BigInteger nonce = this.getNonce(senderAddress);
		BigInteger gasPrice = GAS_PRICE;
		BigInteger gasLimit = GAS_LIMIT_ETHER_TX;
		logger.info("[ETH] Preparing raw transaction between accounts...");

		Transfer transfer = new Transfer(this.web3j, this.contracts.transactionManager);

		TransactionReceipt receipt = null;
		try {
			// through managed transaction with provided credentials
			receipt = transfer.sendFunds(recipientAddress, weiToEther(value), Unit.ETHER).sendAsync().get();
		} catch (InterruptedException | ExecutionException e) {
			throw new EthereumException("couldn't send ether", e);
		}

		if (receipt == null || !receipt.isStatusOK()) {
			throw new EthereumException("TX status field is not OK. TX failed.");
		}

		logger.info("[ETH] raw tx sent.");
		logger.info("[ETH] > senderAddress: " + senderAddress);
		logger.info("[ETH] > nonce: " + nonce);
		logger.info("[ETH] > gasPrice: " + gasPrice);
		logger.info("[ETH] > gasLimit: " + gasLimit);
		logger.info("[ETH] > recipientAddress: " + recipientAddress);
		logger.info("[ETH] > value: " + weiToEther(value).toString());

		return receipt.getTransactionHash();
	}

	/**
	 * Send ether to the recipient address.
	 * 
	 * This sends ether from the "owner" of this registry client (i.e., the account
	 * specified by the credentials which were used to construct this client), to
	 * the given recipient address.
	 * 
	 * The calling agent (i.e. ethereumAgent vs ethereumNode) influences who pays
	 * for the transaction fees. In smart contracts, this would also mean who is
	 * msg.sender
	 * 
	 * @param senderAddress
	 * @param recipientAddress
	 * @param valueInWei
	 * @return
	 * @throws EthereumException
	 */
	@Deprecated
	public TransactionReceipt sendEther(String senderAddress, String recipientAddress, BigInteger valueInWei)
			throws EthereumException {

		TransactionReceipt txR;
		String txHash;
		BigInteger nonce = BigInteger.valueOf(-1);
		try {
			nonce = this.getNonce(senderAddress);
			// this is a contract method call -> gas limit higher than simple fund transfer
			BigInteger gasPrice = GAS_PRICE;
			BigInteger gasLimit = GAS_LIMIT_ETHER_TX;
			Transaction transaction = Transaction.createEtherTransaction(senderAddress, nonce, gasPrice, gasLimit,
					recipientAddress, valueInWei);

			logger.info("[ETH] Preparing web3j transaction between accounts...");

			// directly through web3j since coinbase is one of 10 unlocked accounts.
			EthSendTransaction ethSendTransaction = web3j.ethSendTransaction(transaction).sendAsync().get();

			if (ethSendTransaction.hasError()) {
				Response.Error error = ethSendTransaction.getError();
				throw new EthereumException("Eth Transaction Error [" + error.getCode() + "]: " + error.getMessage());
			}
			txHash = ethSendTransaction.getTransactionHash();

			if (txHash.length() < 2) {
				throw new EthereumException("Could not create ethereum transaction");
			}

			logger.info("[ETH] waiting for receipt on [" + txHash + "]... ");
			txR = waitForReceipt(txHash);

		} catch (InterruptedException | ExecutionException e) {
			throw new EthereumException("Could not send ether to address '" + recipientAddress + "'", e);
		}

		if (txR == null) {
			throw new EthereumException("Could not create eth sending transaction.");
		}
		logger.info("[ETH] receipt for [" + txHash + "] received.");

		logger.info("[ETH] web3j transaction sent. ");
		logger.info("[ETH] > senderAddress: " + senderAddress);
		logger.info("[ETH] > nonce: " + nonce);
		logger.info("[ETH] > gasPrice: " + gasPrice);
		logger.info("[ETH] > gasLimit: " + gasLimit);
		logger.info("[ETH] > recipientAddress: " + recipientAddress);
		logger.info("[ETH] > valueInWei: " + valueInWei);

		return txR;
	}

	public String sendEtherFromCoinbase(String recipientAddress, BigInteger valueInWei) throws EthereumException {
		String coinbase = "";
		try {
			coinbase = this.getCoinbase().getResult();
		} catch (InterruptedException | ExecutionException e) {
			throw new EthereumException("Could not retreive coinbase address", e);
		}
		if (coinbase == "") {
			throw new EthereumException("Could not retreive coinbase address");
		}
		logger.info("[ETH] Sending Faucet Transaction");
		return sendEtherManaged(coinbase, recipientAddress, valueInWei);
	}

	public <T extends Contract> T deploySmartContract(Class<T> contractClass, String contractBinary) {
		T contract = null;
		try {
			contract = T.deployRemoteCall(contractClass, web3j, credentials, gasPrice, gasLimit, contractBinary, "",
					BigInteger.ZERO).send();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return contract;
	}

	public <T extends Contract> T loadSmartContract(Class<T> contractClass, String contractAddress) {
		T contract = null;
		try {
			Constructor<T> constructor = contractClass.getDeclaredConstructor(String.class, Web3j.class,
					Credentials.class, ContractGasProvider.class);
			constructor.setAccessible(true);
			contract = constructor.newInstance(contractAddress, web3j, credentials,
					new StaticGasProvider(gasPrice, gasLimit));

		} catch (Exception e) { // TODO Auto-generated catch block
			e.printStackTrace();
		}
		return contract;
	}
}
