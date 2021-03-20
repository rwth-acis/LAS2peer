package i5.las2peer.security;

import i5.las2peer.api.security.Agent;
import i5.las2peer.api.security.AgentAccessDeniedException;
import i5.las2peer.api.security.AgentLockedException;
import i5.las2peer.api.security.AgentOperationFailedException;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.registry.CredentialUtils;
import i5.las2peer.registry.ReadWriteRegistryClient;
import i5.las2peer.registry.data.RegistryConfiguration;
import i5.las2peer.serialization.MalformedXMLException;
import i5.las2peer.serialization.SerializationException;
import i5.las2peer.serialization.SerializeTools;
import i5.las2peer.serialization.XmlTools;
import i5.las2peer.tools.CryptoException;
import i5.las2peer.tools.CryptoTools;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.web3j.crypto.Credentials;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;

import javax.crypto.SecretKey;

/**
 * User agent for las2peer networks with blockchain-based registry.
 *
 * In addition to the usual key pair, this agent has a separate Ethereum key
 * pair with an associated Ethereum address. (The address can be derived from
 * the private key.)
 *
 * In contrast to the regular las2peer key pair, the Ethereum key pair is not
 * stored as an encrypted private key, but instead as a mnemonic string which
 * serves the same function but is a standardized, human-readable
 * representation.
 */
public class GroupEthereumAgent extends GroupAgentImpl {
	// this should probably be configured elsewhere

	/**
	 * BIP39 mnemonic used together with the agent's password to generate the
	 * private key for Ethereum.
	 *
	 * (Instead of this, we could use the encrypted private key directly, like the
	 * regular agent key pair, but using the mnemonic abstraction allows the user to
	 * potentially use other wallet software/services and allows us to delegate the
	 * tricky crypto operations to a compatible library.)
	 */
	// should the salt be used, too? can't hurt, but not sure if there's an
	// advantage. I'm fairly certain Web3J uses its own salt and whatnot
	private String ethereumMnemonic;
	/** Ethereum account address. (This is not secret.) */
	private String ethereumAddress;

	/** Ethereum credentials; used for Ethereum message signing. */
	private Credentials credentials;

	/** This agent's registry client to make direct smart contract calls. */
	private ReadWriteRegistryClient registryClient;

	private static final L2pLogger logger = L2pLogger.getInstance(GroupEthereumAgent.class);

	protected GroupEthereumAgent(KeyPair pair, SecretKey secret, byte[] salt, String groupName, Agent[] members,
			String ethereumMnemonic) throws AgentOperationFailedException, CryptoException, SerializationException {
		super(pair, secret, members, groupName);
		checkGroupNameValidity(groupName);
		this.groupName = groupName;
		this.ethereumMnemonic = ethereumMnemonic;
		this.ethereumAddress = CredentialUtils.fromMnemonic(ethereumMnemonic, groupName).getAddress();
		logger.fine("creating ethereum agent [" + ethereumAddress + "]");
	}
// currently removed as I dont think i ll need this	

	protected GroupEthereumAgent(PublicKey pubKey, byte[] encryptedPrivate, HashMap<String, byte[]> htEncryptedKeys,
			String ethereumMnemonic, String ethereumAddress) throws AgentOperationFailedException {
		super(pubKey, encryptedPrivate, htEncryptedKeys);
		this.ethereumMnemonic = ethereumMnemonic;
		this.ethereumAddress = ethereumAddress;
		logger.fine("creating ethereum agent [" + ethereumAddress + "]");
	}

	void checkGroupNameValidity(String groupName) throws IllegalArgumentException {
		if (groupName.length() > 32) {
			throw new IllegalArgumentException("Login name must be at most 32 characters");
		}
	}

	// bit of unfortunate name here, but let's stick with it
	/**
	 * Removes decrypted private key and the registry client (which contains user
	 * credentials).
	 */
	@Override
	public void lockPrivateKey() {
		super.lockPrivateKey();
		this.credentials = null;
		this.registryClient = null;
	}

	@Override
	public void unlock(Agent agent) throws AgentAccessDeniedException, AgentOperationFailedException, AgentLockedException {
		super.unlock(agent);

		credentials = CredentialUtils.fromMnemonic(ethereumMnemonic, this.getGroupName());
		registryClient = new ReadWriteRegistryClient(new RegistryConfiguration(), credentials);

		ethereumAddress = credentials.getAddress();
		logger.fine("unlocked ethereum agent [" + ethereumAddress + "]");
	}

	@Override
	public boolean isLocked() {
		boolean ethereumIsLocked = (registryClient == null);
		boolean groupAgentIsLocked = super.isLocked();
		if (groupAgentIsLocked == ethereumIsLocked) {
			return groupAgentIsLocked;
		} else {
			// this hopefully only happens during initialization, where this is called from
			// a superclass's constructor
			if (ethereumMnemonic == null) {
				// we're almost certainly in the middle of initialization
				return groupAgentIsLocked;
			} else {
				throw new RuntimeException(
						"FIXME: inconsistent lock state should not be reachable -- figure out where this happens!");
			}
		}
	}

	@Override
	public String toXmlString() {
		String result = super.toXmlString();

			String keyList = "";

			/*
			 * for (String id : this.getMemberList()) { keyList +=
			 * "\t\t<keyentry forAgent=\"" + id + "\" encoding=\"base64\">" +
			 * Base64.getEncoder().encodeToString(htEncryptedKeyVersions.get(id)) +
			 * "</keyentry>\n"; }
			 */
			// result.replace("group", "ethereumGroup");
			String newResult= result.replace("\"</las2peer:agent>\\n\"", "\t<ethereumaddress>" + ethereumAddress + "</ethereumaddress>\n"
			+ "\t<ethereummnemonic>" + ethereumMnemonic + "</ethereummnemonic>\n" + "</las2peer:agent>\n");
			
					System.out.println("print it hehheeeeeerrre");
					System.out.println(result);
					System.out.println("NNNNNNNEEEEEWWWWWW");
					System.out.println(newResult);
					return newResult;

	}

	/**
	 * Creates new agent with given passphrase and login name.
	 * 
	 * @param loginName  name matching [a-zA-Z].{3,31} (hopefully UTF-8 characters,
	 *                   let's not get too crazy)
	 * @return new EthereumAgent instance
	 * @throws CryptoException if there is an internal error during Ethereum key
	 *                         creation
	 */
	public static GroupEthereumAgent createGroupEthereumAgentWithClient(String loginName, ReadWriteRegistryClient regClient,
			Agent[] members) throws CryptoException, AgentOperationFailedException, SerializationException {
		byte[] salt = CryptoTools.generateSalt();
		KeyPair keyPair = CryptoTools.generateKeyPair();
		return new GroupEthereumAgent(keyPair, CryptoTools.generateSymmetricKey(), salt, loginName, members,
				CredentialUtils.createMnemonic());
	}

// Currently  not needed, as groups will always be created using frontend/user-widget	
	/*
	 * // use this if you already want to use a mnemonic generated somewhere else //
	 * note that this still uses the password to generate the key pair public static
	 * EthereumAgent createEthereumAgent(String loginName, String passphrase,
	 * ReadWriteRegistryClient regClient, String ethereumMnemonic) throws
	 * CryptoException, AgentOperationFailedException { byte[] salt =
	 * CryptoTools.generateSalt(); KeyPair keyPair = CryptoTools.generateKeyPair();
	 * return new EthereumAgent(keyPair, passphrase, salt, loginName,
	 * ethereumMnemonic); }
	 */

	/**
	 * Gets registry client that uses the agent's credentials. May be
	 * <code>null</code>; will need to add an "at" here use {link #unlock(String)}.
	 */
	public ReadWriteRegistryClient getRegistryClient() {
		return registryClient;
	}

	/** @return address of the Ethereum key pair associated with the agent */
	public String getEthereumAddress() {
		return ethereumAddress;
	}

	public String getEthereumMnemonic() {
		return ethereumMnemonic;
	}

	public Credentials getEthereumCredentials() throws AgentLockedException {
		if (isLocked()) {
			throw new AgentLockedException();
		}
		return this.credentials;
	}

	public static GroupEthereumAgent createFromXml(String xml) throws MalformedXMLException {
		return createFromXml(XmlTools.getRootElement(xml, "las2peer:agent"));
	}
	
	public static GroupEthereumAgent createFromXml(Element root) throws MalformedXMLException {
		try {

			// read id field from XML
			Element elId = XmlTools.getSingularElement(root, "id");
			String id = elId.getTextContent();
			// read public key from XML
			Element pubKey = XmlTools.getSingularElement(root, "publickey");
			if (!pubKey.getAttribute("encoding").equals("base64")) {
				throw new MalformedXMLException("base64 encoding expected");
			}
			PublicKey publicKey = (PublicKey) SerializeTools.deserializeBase64(pubKey.getTextContent());
			if (!id.equalsIgnoreCase(CryptoTools.publicKeyToSHA512(publicKey))) {
				throw new MalformedXMLException("id does not match with public key");
			}
			// read private key from XML
			Element privKey = XmlTools.getSingularElement(root, "privatekey");
			if (!privKey.getAttribute("encrypted").equals(CryptoTools.getSymmetricAlgorithm())) {
				throw new MalformedXMLException(CryptoTools.getSymmetricAlgorithm() + " expected");
			}
			byte[] encPrivate = Base64.getDecoder().decode(privKey.getTextContent());
			// read member keys from XML
			Element encryptedKeys = XmlTools.getSingularElement(root, "unlockKeys");
			if (!encryptedKeys.getAttribute("method").equals(CryptoTools.getAsymmetricAlgorithm())) {
				throw new MalformedXMLException("base64 encoding expected");
			}

			HashMap<String, byte[]> htMemberKeys = new HashMap<>();
			NodeList enGroups = encryptedKeys.getElementsByTagName("keyentry");
			for (int n = 0; n < enGroups.getLength(); n++) {
				org.w3c.dom.Node node = enGroups.item(n);
				short nodeType = node.getNodeType();
				if (nodeType != org.w3c.dom.Node.ELEMENT_NODE) {
					throw new MalformedXMLException(
							"Node type (" + nodeType + ") is not type element (" + org.w3c.dom.Node.ELEMENT_NODE + ")");
				}
				Element elKey = (Element) node;
				if (!elKey.hasAttribute("forAgent")) {
					throw new MalformedXMLException("forAgent attribute expected");
				}
				if (!elKey.getAttribute("encoding").equals("base64")) {
					throw new MalformedXMLException("base64 encoding expected");
				}

				String agentId = elKey.getAttribute("forAgent");
				byte[] content = Base64.getDecoder().decode(elKey.getTextContent());
				htMemberKeys.put(agentId, content);
			}

			Element ethereumMnemonicElement = XmlTools.getSingularElement(root, "ethereummnemonic");
			String ethereumMnemonic = ethereumMnemonicElement.getTextContent();

			Element ethereumAddressElement = XmlTools.getSingularElement(root, "ethereumaddress");
			String ethereumAddress = ethereumAddressElement.getTextContent();
			GroupEthereumAgent result = new GroupEthereumAgent(publicKey, encPrivate, htMemberKeys, ethereumMnemonic,
					ethereumAddress);
			// read group Name
			Element groupName = XmlTools.getOptionalElement(root, "groupName");
			if (groupName != null) {
				result.groupName = groupName.getTextContent();
			}

			ArrayList<String> adminMembers = new ArrayList<String>();
			Element admins = XmlTools.getSingularElement(root, "adminList");
			enGroups = admins.getElementsByTagName("admin");
			System.out.println(enGroups);
			for (int n = 0; n < enGroups.getLength(); n++) {
				org.w3c.dom.Node node = enGroups.item(n);
				short nodeType = node.getNodeType();
				if (nodeType != org.w3c.dom.Node.ELEMENT_NODE) {
					throw new MalformedXMLException(
							"Node type (" + nodeType + ") is not type element (" + org.w3c.dom.Node.ELEMENT_NODE + ")");
				}
				Element elKey = (Element) node;
				adminMembers.add(elKey.getTextContent());
			}
			result.adminList = adminMembers;

			return result;

		} catch (SerializationException | AgentOperationFailedException e) {
			throw new MalformedXMLException("Deserialization problems", e);
		}
	}
}
