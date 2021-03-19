package i5.las2peer.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.logging.Level;

import i5.las2peer.api.persistency.EnvelopeAccessDeniedException;
import i5.las2peer.api.persistency.EnvelopeAlreadyExistsException;
import i5.las2peer.api.persistency.EnvelopeException;
import i5.las2peer.api.persistency.EnvelopeNotFoundException;
import i5.las2peer.api.security.AgentAccessDeniedException;
import i5.las2peer.api.security.AgentException;
import i5.las2peer.api.security.AgentOperationFailedException;
import i5.las2peer.classLoaders.libraries.LibraryIdentifier;
import i5.las2peer.classLoaders.libraries.LoadedNetworkLibrary;
import i5.las2peer.classLoaders.libraries.SharedStorageRepository;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.p2p.EthereumNode;
import i5.las2peer.p2p.PastryNodeImpl;
import i5.las2peer.persistency.EnvelopeVersion;
import i5.las2peer.registry.exceptions.EthereumException;
import i5.las2peer.security.AgentImpl;
import i5.las2peer.security.EthereumAgent;
import i5.las2peer.security.GroupEthereumAgent;
import i5.las2peer.security.PassphraseAgentImpl;
import i5.las2peer.serialization.MalformedXMLException;
import i5.las2peer.serialization.SerializationException;

// A NOTE ON THE "SUPPLEMENT":
// The idea was that non-essential service registration info should not be stored directly on the blockchain but as a
// hash referencing the shared storage. This is a good idea.
// Executing it this way is pretty ugly though. Rather than adding some random JSON (of unknown / unchecked structure),
// in a separate step, in a separate envelope, as a separate parameter, ... that data should simply be part of the JAR.
// There is no need to treat it differently than any other file.
// I'm doing this anyway, because it's a pragmatic solution and actually matches my proposal ...
// But really this should be refactored!

public class PackageUploader {

	private static L2pLogger logger = L2pLogger.getInstance(PackageUploader.class);

	public static class ServiceVersionList extends LinkedList<String> {

		private static final long serialVersionUID = 1L;

	}

	/**
	 * @see #uploadServicePackage(PastryNodeImpl, String, String, String, String)
	 */
	public static void uploadServicePackage(PastryNodeImpl node, String serviceJarFilename,
			String developerAgentXMLFilename, String developerPassword)
			throws ServicePackageException, EnvelopeAlreadyExistsException {
		uploadServicePackage(node, serviceJarFilename, developerAgentXMLFilename, developerPassword, null);
	}

	/**
	 * @param serviceJarFilename        The service jar that should be uploaded.
	 * @param developerAgentXMLFilename The developers agent, who is responsible for
	 *                                  this service.
	 * @param developerPassword         The password for the developers agent.
	 * @param supplement                additional, optional metadata
	 * @see #uploadServicePackage(PastryNodeImpl, JarInputStream, AgentImpl, String)
	 */
	public static void uploadServicePackage(PastryNodeImpl node, String serviceJarFilename,
			String developerAgentXMLFilename, String developerPassword, String supplement)
			throws ServicePackageException, EnvelopeAlreadyExistsException {
		// early verify the developer agent to avoid needless heavy duty jar parsing
		AgentImpl devAgent = unlockDeveloperAgent(developerAgentXMLFilename, developerPassword);

		File file = new File(serviceJarFilename);
		try (InputStream inputStream = new FileInputStream(file);
				JarInputStream jarInputStream = new JarInputStream(inputStream)) {
			uploadServicePackage(node, jarInputStream, devAgent, supplement);
		} catch (EthereumException e) {
			logger.log(Level.SEVERE, "Exception while publishing service to Blockchain", e);
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Exception while reading jar file", e);
		}
	}

	/**
	 * Uploads the complete service (jar) and all its dependencies into the given
	 * nodes shared storage to be used for network class loading. The dependencies
	 * are read from the "Import-Library" statement inside the jars manifest file.
	 * All the files extracted from the jars are signed with the given developers
	 * agent to prevent manipulations.
	 *
	 * @param node           The node storage, where the files should be uploaded
	 *                       into.
	 * @param jarInputStream service JAR as JAR input stream
	 * @param devAgent       unlocked developer agent
	 * @throws ServicePackageException If an issue occurs with the service jar
	 *                                 itself or its dependencies (jars).
	 */
	public static void uploadServicePackage(PastryNodeImpl node, JarInputStream jarInputStream, AgentImpl devAgent,
			String supplement) throws ServicePackageException, EnvelopeAlreadyExistsException, EthereumException {
		try {
			long uploadStart = System.currentTimeMillis();
			// read general service information from jar manifest
			Manifest manifest = jarInputStream.getManifest();
			if (manifest == null) {
				throw new ServicePackageException("Service jar package contains no manifest file");
			}
			String serviceName = manifest.getMainAttributes()
					.getValue(LibraryIdentifier.MANIFEST_LIBRARY_NAME_ATTRIBUTE);
			if (serviceName == null) {
				throw new ServicePackageException("No service name value in manifest file. Please specify '"
						+ LibraryIdentifier.MANIFEST_LIBRARY_NAME_ATTRIBUTE + "'");
			}
			String serviceVersion = manifest.getMainAttributes()
					.getValue(LibraryIdentifier.MANIFEST_LIBRARY_VERSION_ATTRIBUTE);
			if (serviceVersion == null) {
				throw new ServicePackageException("No service version value in manifest file. Please specify '"
						+ LibraryIdentifier.MANIFEST_LIBRARY_VERSION_ATTRIBUTE + "'");
			}

			// read files from jar and generate hashes
			Map<String, byte[]> depHashes = new HashMap<>();
			Map<String, byte[]> jarFiles = new HashMap<>();
			JarEntry entry;
			while ((entry = jarInputStream.getNextJarEntry()) != null) {
				if (!entry.isDirectory()) {
					byte[] bytes = SimpleTools.toByteArray(jarInputStream);
					jarInputStream.closeEntry();
					byte[] hash = CryptoTools.getSecureHash(bytes);
					String filename = entry.getName();
					depHashes.put(filename, hash);
					jarFiles.put(filename, bytes);
				}
			}
			jarInputStream.close();

			uploadServicePackage(node, serviceName, serviceVersion, depHashes, jarFiles, devAgent, supplement);
			long uploadTime = System.currentTimeMillis() - uploadStart;
			logger.info("Service package '" + serviceName + "' uploaded in " + uploadTime + " ms");
		} catch (EnvelopeAlreadyExistsException e) {
			logger.log(Level.SEVERE, "Service package already exists!", e);
			throw e;
		} catch (IOException | AgentException | SerializationException | EnvelopeException | CryptoException e) {
			logger.log(Level.SEVERE, "Service package upload failed! " + e.toString(), e);
			e.printStackTrace();
		}
	}

	private static AgentImpl unlockDeveloperAgent(String agentXMLFile, String developerPassword)
			throws ServicePackageException {
		try {
			// read agent from given XML file
			AgentImpl agent = AgentImpl.createFromXml(new File(agentXMLFile));
			if (!(agent instanceof PassphraseAgentImpl)) {
				throw new ServicePackageException(
						"Developer agent of type '" + PassphraseAgentImpl.class.getCanonicalName() + "' expected got '"
								+ agent.getClass().getCanonicalName() + "' instead!");
			}
			// unlock agent (verify password)
			PassphraseAgentImpl devAgent = (PassphraseAgentImpl) agent;
			devAgent.unlock(developerPassword);
			return devAgent;
		} catch (MalformedXMLException | AgentAccessDeniedException | AgentOperationFailedException e) {
			throw new ServicePackageException(e);
		}
	}

	public static void uploadServicePackage(PastryNodeImpl node, String serviceName, String serviceVersion,
			Map<String, byte[]> depHashes, Map<String, byte[]> jarFiles, AgentImpl devAgent, String supplement)
			throws SerializationException, CryptoException, EnvelopeException, ServicePackageException, AgentException,
			EthereumException {
		if (serviceName == null) {
			throw new ServicePackageException("No service name given");
		} else if (serviceVersion == null) {
			throw new ServicePackageException("No service version given");
		}

		if (node instanceof EthereumNode) {
			registerService((EthereumNode) node, serviceName, serviceVersion, devAgent, supplement);
		}
		storeServiceFiles(node, jarFiles);
		LibraryIdentifier libId = storeServiceMetadata(node, serviceName, serviceVersion, depHashes, devAgent);
		EnvelopeVersion versionEnv = fetchOrCreateVersionsEnvelope(node, serviceName, devAgent, libId);
		node.storeEnvelope(versionEnv, devAgent);
	}

	public static void announceClusterServiceDeployment(PastryNodeImpl node, String serviceName,
			String clusterServiceName, String serviceVersion, String supplement) throws SerializationException,
			CryptoException, EnvelopeException, ServicePackageException, AgentException, EthereumException {
		if (serviceName == null) {
			throw new ServicePackageException("No service name given");
		} else if (clusterServiceName == null) {
			throw new ServicePackageException("No cluster service name  given");
		} else if (serviceVersion == null) {
			throw new ServicePackageException("No service version given");
		}
		if (node instanceof EthereumNode) {
			announceClusterServiceDeployment((EthereumNode) node, serviceName, clusterServiceName, serviceVersion,
					supplement, false);
		}
	}

	public static void registerClusterService(PastryNodeImpl node, String serviceName, String serviceVersion,
			AgentImpl devAgent, String supplement) throws SerializationException, CryptoException, EnvelopeException,
			ServicePackageException, AgentException, EthereumException {
		if (serviceName == null) {
			throw new ServicePackageException("No service name given");
		} else if (serviceVersion == null) {
			throw new ServicePackageException("No service version given");
		}
		if (node instanceof EthereumNode) {
			registerService((EthereumNode) node, serviceName, serviceVersion, devAgent, supplement);
		}
	}

	private static void registerService(EthereumNode node, String serviceName, String serviceVersion,
			AgentImpl devAgent, String supplement)
			throws AgentException, EnvelopeException, CryptoException, SerializationException, EthereumException {
		if (!(devAgent instanceof EthereumAgent)) {
			throw new AgentException("Cannot use non-Ethereum agent to upload services on this Ethereum-enabled node!");
		}
		byte[] supplementHash = storeSupplement(node, supplement);
		if(devAgent instanceof EthereumAgent){
			node.registerServiceInBlockchain(serviceName, serviceVersion, (EthereumAgent) devAgent, supplementHash);
		} else if(devAgent instanceof GroupEthereumAgent){
			node.registerServiceInBlockchain(serviceName, serviceVersion, (GroupEthereumAgent) devAgent, supplementHash);
		}
	}

	private static void announceClusterServiceDeployment(EthereumNode node, String serviceName,
			String clusterServiceName, String serviceVersion, String supplement, Boolean firstDeployment)
			throws AgentException, EnvelopeException, CryptoException, SerializationException, EthereumException {

		byte[] supplementHash = storeSupplement(node, supplement);
		node.announceClusterServiceDeployment(serviceName, clusterServiceName, serviceVersion, supplementHash,
				firstDeployment);
	}

	public static void announceUndeploymentOfClusterService(PastryNodeImpl node, String serviceName,
			String clusterServiceName, String serviceVersion) throws SerializationException, CryptoException,
			EnvelopeException, ServicePackageException, AgentException, EthereumException {
		if (serviceName == null) {
			throw new ServicePackageException("No service name given");
		} else if (clusterServiceName == null) {
			throw new ServicePackageException("No cluster service name  given");
		} else if (serviceVersion == null) {
			throw new ServicePackageException("No service version given");
		}
		if (node instanceof EthereumNode) {
			announceUndeploymentOfClusterService((EthereumNode) node, serviceName, clusterServiceName, serviceVersion);
		}
	}

	private static void announceUndeploymentOfClusterService(EthereumNode node, String serviceName,
			String clusterServiceName, String serviceVersion)
			throws AgentException, EnvelopeException, CryptoException, SerializationException, EthereumException {
		node.announceUndeploymentOfClusterService(serviceName, clusterServiceName, serviceVersion);
	}

	private static void storeServiceFiles(PastryNodeImpl node, Map<String, byte[]> jarFiles) throws EnvelopeException {
		// TODO upload all files async to the network ignore already existing files
		for (Entry<String, byte[]> entry : jarFiles.entrySet()) {
			logger.info("publishing file '" + entry.getKey() + "' from jar");
			node.storeHashedContent(entry.getValue());
		}
		// TODO wait for all async uploads
	}

	private static LibraryIdentifier storeServiceMetadata(PastryNodeImpl node, String serviceName,
			String serviceVersion, Map<String, byte[]> depHashes, AgentImpl devAgent)
			throws SerializationException, CryptoException, EnvelopeException, ServicePackageException {
		LibraryIdentifier libId = new LibraryIdentifier(serviceName, serviceVersion);
		// store metadata envelope for service
		LoadedNetworkLibrary netLib = new LoadedNetworkLibrary(node, libId, depHashes);
		// upload network library as XML representation
		String libEnvId = SharedStorageRepository.getLibraryEnvelopeIdentifier(netLib.getIdentifier());
		logger.info("publishing library '" + netLib.getIdentifier().toString() + "' to '" + libEnvId + "'");
		EnvelopeVersion libEnv = node.createUnencryptedEnvelope(libEnvId, devAgent.getPublicKey(),
				netLib.toXmlString());
		try {
			node.storeEnvelope(libEnv, devAgent);
		} catch (EnvelopeAlreadyExistsException e) {
			// TODO actually compare old and new service version to determine exact version
			// change required
			throw new ServicePackageException(
					"Service package upload failed! Version is already known in the network. To update increase version number",
					e);
		}

		return libId;
	}

	private static EnvelopeVersion fetchOrCreateVersionsEnvelope(PastryNodeImpl node, String serviceName,
			AgentImpl devAgent, LibraryIdentifier libId)
			throws EnvelopeException, CryptoException, SerializationException, ServicePackageException {
		String envVersionId = SharedStorageRepository.getLibraryVersionsEnvelopeIdentifier(serviceName);
		logger.info("publishing version information to '" + envVersionId + "'");
		// fetch or create versions envelope
		EnvelopeVersion versionEnv = null;
		try {
			EnvelopeVersion storedVersions = node.fetchEnvelope(envVersionId);
			// add version to list
			Serializable content = storedVersions.getContent();
			if (content instanceof ServiceVersionList) {
				ServiceVersionList versions = (ServiceVersionList) content;
				versions.add(libId.getVersion().toString());
				versionEnv = node.createUnencryptedEnvelope(storedVersions, versions);
			} else {
				throw new ServicePackageException("Invalid version envelope expected " + List.class.getCanonicalName()
						+ " but envelope contains " + content.getClass().getCanonicalName());
			}
		} catch (EnvelopeNotFoundException e) {
			ServiceVersionList versions = new ServiceVersionList();
			versions.add(libId.getVersion().toString());
			versionEnv = node.createUnencryptedEnvelope(envVersionId, devAgent.getPublicKey(), versions);
		} catch (EnvelopeAccessDeniedException e) {
			throw new ServicePackageException("Unencrypted content in service versions envelope expected", e);
		}
		return versionEnv;
	}

	private static byte[] storeSupplement(PastryNodeImpl node, String supplement)
			throws EnvelopeException, CryptoException {
		if (supplement == null) {
			supplement = "";
		}
		byte[] bytes = supplement.getBytes(StandardCharsets.UTF_8);
		byte[] hash = CryptoTools.getSecureHash(bytes);
		node.storeHashedContent(bytes);
		return hash;
	}

}
