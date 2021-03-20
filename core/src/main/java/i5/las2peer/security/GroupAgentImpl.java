package i5.las2peer.security;

import java.io.Serializable;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import i5.las2peer.p2p.Node;
import i5.las2peer.api.logging.MonitoringEvent;
import i5.las2peer.api.persistency.EnvelopeException;
import i5.las2peer.api.persistency.EnvelopeNotFoundException;
import i5.las2peer.api.security.Agent;
import i5.las2peer.api.security.AgentAccessDeniedException;
import i5.las2peer.api.security.AgentException;
import i5.las2peer.api.security.AgentLockedException;
import i5.las2peer.api.security.AgentNotFoundException;
import i5.las2peer.api.security.AgentOperationFailedException;
import i5.las2peer.api.security.AnonymousAgent;
import i5.las2peer.api.security.GroupAgent;
import i5.las2peer.communication.Message;
import i5.las2peer.communication.MessageException;
import i5.las2peer.persistency.EncodingFailedException;
import i5.las2peer.persistency.EnvelopeVersion;
import i5.las2peer.serialization.MalformedXMLException;
import i5.las2peer.serialization.SerializationException;
import i5.las2peer.serialization.SerializeTools;
import i5.las2peer.serialization.XmlAble;
import i5.las2peer.serialization.XmlTools;
import i5.las2peer.tools.CryptoException;
import i5.las2peer.tools.CryptoTools;

/**
 * An agent representing a group of other agents.
 * 
 * The storage of the group information is stored encrypted in a similar manner to
 * {@link i5.las2peer.persistency.EnvelopeVersion}:
 * 
 * The (symmetric) key to unlock the private key of the group is encrypted asymmetrically for each entitled agent (i.e.
 * <i>member</i> of the group).
 * 
 */
public class GroupAgentImpl extends AgentImpl implements GroupAgent {
	
	private Node node;
	private SecretKey symmetricGroupKey;
	private AgentImpl openedBy;
	protected String groupName = null;
	protected ArrayList<String> adminList = new ArrayList<String>();

	/**
	 * hashtable storing the encrypted versions of the group secret key for each member
	 */
	private HashMap<String, byte[]> htEncryptedKeyVersions = new HashMap<>();

	private Map<String, AgentImpl> membersToAdd = new HashMap<>();
	private Map<String, AgentImpl> membersToRemove = new HashMap<>();

	@SuppressWarnings("unchecked")
	protected GroupAgentImpl(PublicKey pubKey, byte[] encryptedPrivate, HashMap<String, byte[]> htEncryptedKeys)
			throws AgentOperationFailedException {
		super(pubKey, encryptedPrivate);

		htEncryptedKeyVersions = (HashMap<String, byte[]>) htEncryptedKeys.clone();
	}

	/**
	 * constructor for the {@link #createGroupAgent} factory simply necessary, since the secret key has to be stated for
	 * the constructor of the superclass
	 * 
	 * @param keys
	 * @param secret
	 * @param members
	 * @throws AgentOperationFailedException
	 * @throws CryptoException
	 * @throws SerializationException
	 */
	protected GroupAgentImpl(KeyPair keys, SecretKey secret, Agent[] members, String groupName)
			throws AgentOperationFailedException, CryptoException, SerializationException {
		super(keys, secret);

		symmetricGroupKey = secret;
		//TODO: add method to check if groupname exists already...
		this.groupName = groupName;
		for (Agent a : members) {
			try {
				addMember((AgentImpl) a, false);
			} catch (AgentLockedException e) {
				throw new IllegalStateException(e);
			}
		}

		lockPrivateKey();
	}
	


	/**
	 * decrypt the secret key of this group for the given agent (which is hopefully a member)
	 * 
	 * @param agent
	 * @throws SerializationException
	 * @throws CryptoException
	 * @throws AgentLockedException
	 * @throws AgentAccessDeniedException
	 */
	private void decryptSecretKey(AgentImpl agent)
			throws SerializationException, CryptoException, AgentLockedException, AgentAccessDeniedException {
		byte[] crypted = htEncryptedKeyVersions.get(agent.getIdentifier());

		if (crypted == null) {
			throw new AgentAccessDeniedException("the given agent is not listed as a group member!");
		}

		try {
			symmetricGroupKey = agent.decryptSymmetricKey(crypted);
		} catch (AgentLockedException e) {
			throw new AgentLockedException("The given agent is locked!", e);
		}
	}

	/**
	 * add a member to this group
	 * 
	 * @param a
	 * @throws CryptoException
	 * @throws SerializationException
	 * @throws AgentLockedException
	 */
	public void addMember(AgentImpl a) throws CryptoException, SerializationException, AgentLockedException {
		addMember(a, true);
	}
	
	/**
	 * add a member to the admin list of this group
	 * 
	 * @param a
	 */
	public void addAdmin(Agent a) {
		if(!adminList.contains(a.getIdentifier())) {
			adminList.add(a.getIdentifier());
		}
	}
	
	/**
	 * remove a member from the admin list of this group
	 * 
	 * @param a
	 */
	public void revokeAdmin(Agent a) {
		if(adminList.contains(a.getIdentifier())) {
			adminList.remove(a.getIdentifier());
		}
	}
	
	/**
	 * Check admin rights for member.
	 * 
	 * @param a Member to check admin rights for.
	 * @return if agent is admin
	 */
	public boolean isAdmin(Agent a){
		return adminList.contains(a.getIdentifier());
	}

	/**
	 * private version of adding members, mainly just for the constructor to add members without unlocking the private
	 * key of the group
	 * 
	 * @param a
	 * @param securityCheck
	 * @throws SerializationException
	 * @throws CryptoException
	 * @throws AgentLockedException
	 */
	private final void addMember(AgentImpl a, boolean securityCheck)
			throws CryptoException, SerializationException, AgentLockedException {
		if (securityCheck && isLocked()) {
			throw new AgentLockedException();
		}

		byte[] cryptedSecret = CryptoTools.encryptAsymmetric(symmetricGroupKey, a.getPublicKey());
		htEncryptedKeyVersions.put(a.getIdentifier(), cryptedSecret);
	}

	/**
	 * how many members does this group have
	 * 
	 * @return the number of group members
	 */
	@Override
	public int getSize() {
		return htEncryptedKeyVersions.size() + membersToAdd.size() - membersToRemove.size();
	}

	/**
	 * get an array with the ids of all direct group members without recursion
	 * 
	 * @return an array with the ids of all direct member agents
	 */
	@Override
	public String[] getMemberList() {
		ArrayList<String> elements = new ArrayList<>();
		elements.addAll(htEncryptedKeyVersions.keySet());
		elements.removeAll(membersToRemove.keySet());
		elements.addAll(membersToAdd.keySet());
		return elements.toArray(new String[0]);
	}

	/**
	 * returns the Agent by whom the private Key of this Group has been unlocked
	 * 
	 * @return the agent, who opened the private key of the group
	 */
	public AgentImpl getOpeningAgent() {
		return openedBy;
	}

	/**
	 * remove a member from this group
	 * 
	 * @param a
	 * @throws AgentLockedException
	 */
	public void removeMember(AgentImpl a) throws AgentLockedException {
		removeMember(a.getIdentifier());
	}

	/**
	 * remove a member from this group
	 * 
	 * @param id
	 * @throws AgentLockedException
	 */
	public void removeMember(String id) throws AgentLockedException {
		if (isLocked()) {
			throw new AgentLockedException();
		}

		htEncryptedKeyVersions.remove(id);
	}

	@Override
	public void lockPrivateKey() {
		super.lockPrivateKey();
		this.symmetricGroupKey = null;
		openedBy = null;
	}

	@Override
	public String toXmlString() {
		try {
			String keyList = "";

			for (String id : htEncryptedKeyVersions.keySet()) {
				keyList += "\t\t<keyentry forAgent=\"" + id + "\" encoding=\"base64\">"
						+ Base64.getEncoder().encodeToString(htEncryptedKeyVersions.get(id)) + "</keyentry>\n";
			}
			StringBuffer result = new StringBuffer("<las2peer:agent type=\"ethereumGroup\">\n" + "\t<id>" + getIdentifier()
					+ "</id>\n" + "\t<publickey encoding=\"base64\">" + SerializeTools.serializeToBase64(getPublicKey())
					+ "</publickey>\n" + "\t<privatekey encoding=\"base64\" encrypted=\""
					+ CryptoTools.getSymmetricAlgorithm() + "\">" + getEncodedPrivate() + "</privatekey>\n"
					+ "\t<unlockKeys method=\"" + CryptoTools.getAsymmetricAlgorithm() + "\">\n" + keyList
					+ "\t</unlockKeys>\n");
			if (groupName != null) {
				result.append("\t<groupName>" + groupName + "</groupName>\n");
			}
			
			String admins = "";
			for(int i = 0; i < adminList.size(); i++) {
				admins += "\t\t<admin id=\"" + i + "\" >" + adminList.get(i) + "</admin>\n";
			}
			result.append("\t<adminList>" + admins + "</adminList>\n");
			result.append("</las2peer:agent>\n");

			return result.toString();
		} catch (SerializationException e) {
			throw new RuntimeException("Serialization problems with keys");
		}
	}

	/**
	 * factory - create an instance of GroupAgent from its XML representation
	 * 
	 * @param xml
	 * @return a group agent
	 * @throws MalformedXMLException
	 */
	public static GroupAgentImpl createFromXml(String xml) throws MalformedXMLException {
		return createFromXml(XmlTools.getRootElement(xml, "las2peer:agent"));
	}

	/**
	 * factory - create an instance of GroupAgent based on a XML node
	 * 
	 * @param root
	 * @return a group agent
	 * @throws MalformedXMLException
	 */
	public static GroupAgentImpl createFromXml(Element root) throws MalformedXMLException {
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
			GroupAgentImpl result = new GroupAgentImpl(publicKey, encPrivate, htMemberKeys);
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
		} catch (SerializationException e) {
			throw new MalformedXMLException("Deserialization problems", e);
		} catch (AgentOperationFailedException e) {
			throw new MalformedXMLException("Security Problems creating an agent from the xml string", e);
		}
	}

	/**
	 * create a new group agent instance
	 * 
	 * @param members
	 * @param groupName
	 * @return a group agent
	 * @throws AgentOperationFailedException
	 * @throws CryptoException
	 * @throws SerializationException
	 */
	public static GroupAgentImpl createGroupAgent(Agent[] members, String groupName)
			throws AgentOperationFailedException, CryptoException, SerializationException {
		return new GroupAgentImpl(CryptoTools.generateKeyPair(), CryptoTools.generateSymmetricKey(), members, groupName);
	}

	@Override
	public void receiveMessage(Message message, AgentContext context) throws MessageException {
		// extract content from message
		Object content = null;
		try {
			message.open(this, getRunningAtNode());
			content = message.getContent();
		} catch (AgentException e1) {
			getRunningAtNode().observerNotice(MonitoringEvent.SERVICE_ERROR, e1.getMessage());
		} catch (InternalSecurityException e2) {
			getRunningAtNode().observerNotice(MonitoringEvent.SERVICE_ERROR, e2.getMessage());
		}
		if (content == null) {
			getRunningAtNode().observerNotice(MonitoringEvent.SERVICE_ERROR,
					"The message content is null. Dropping message!");
			return;
		}
		Serializable contentSerializable = null;
		XmlAble contentXmlAble = null;
		if (content instanceof Serializable) {
			contentSerializable = (Serializable) content;
		} else if (content instanceof XmlAble) {
			contentXmlAble = (XmlAble) content;
		} else {
			throw new MessageException("The content of the received message is neither Serializable nor XmlAble but "
					+ content.getClass());
		}
		// send message content to each member of this group
		for (String memberId : getMemberList()) {
			AgentImpl member = null;
			try {
				member = getRunningAtNode().getAgent(memberId);
			} catch (AgentException e1) {
				getRunningAtNode().observerNotice(MonitoringEvent.SERVICE_ERROR, e1.getMessage());
			}
			if (member == null) {
				getRunningAtNode().observerNotice(MonitoringEvent.SERVICE_ERROR,
						"No agent for group member " + memberId + " found! Skipping member.");
				continue;
			}
			try {
				Message msg = null;
				if (contentSerializable != null) {
					msg = new Message(this, member, contentSerializable);
				} else if (contentXmlAble != null) {
					msg = new Message(this, member, contentXmlAble);
				} else {
					getRunningAtNode().observerNotice(MonitoringEvent.SERVICE_ERROR,
							"The message content is null. Dropping message!");
					return;
				}
				getRunningAtNode().sendMessage(msg, null);
			} catch (EncodingFailedException e) {
				getRunningAtNode().observerNotice(MonitoringEvent.SERVICE_ERROR, e.getMessage());
			} catch (InternalSecurityException e) {
				getRunningAtNode().observerNotice(MonitoringEvent.SERVICE_ERROR, e.getMessage());
			} catch (SerializationException e) {
				getRunningAtNode().observerNotice(MonitoringEvent.SERVICE_ERROR, e.getMessage());
			}
		}
	}

	@Override
	public void notifyUnregister() {
		// do nothing
	}

	@Override
	public void addMember(Agent agent) throws AgentLockedException {
		if (isLocked()) {
			throw new AgentLockedException();
		}

		if (!htEncryptedKeyVersions.containsKey(agent.getIdentifier())) {
			membersToAdd.put(agent.getIdentifier(), (AgentImpl) agent);
		}
		membersToRemove.remove(agent.getIdentifier());

	}

	@Override
	public void revokeMember(Agent agent) throws AgentLockedException {
		if (isLocked()) {
			throw new AgentLockedException();
		}

		if (htEncryptedKeyVersions.containsKey(agent.getIdentifier())) {
			membersToRemove.put(agent.getIdentifier(), (AgentImpl) agent);
		} else if (membersToAdd.containsKey(agent)) {
			membersToAdd.remove(agent.getIdentifier());
		}
	}

	@Override
	public boolean hasMember(Agent agent) {
		return hasMember(agent.getIdentifier());
	}

	@Override
	public boolean hasMember(String agentId) {
		return (htEncryptedKeyVersions.get(agentId) != null || membersToAdd.containsKey(agentId))
				&& !membersToRemove.containsKey(agentId);
	}
	
	@Override
	public String getGroupName() {
		return groupName;
	}

	@Override
	public boolean hasGroupName() {
		return getGroupName() != null;
	}
	
	

	@Override
	public void unlock(Agent agent)
			throws AgentAccessDeniedException, AgentOperationFailedException, AgentLockedException {
		try {
			decryptSecretKey((AgentImpl) agent);
			openedBy = (AgentImpl) agent;
			super.unlockPrivateKey(symmetricGroupKey);
		} catch (CryptoException e) {
			throw new AgentAccessDeniedException("Permission denied", e);
		} catch (SerializationException e) {
			throw new AgentOperationFailedException("Agent corrupted", e);
		}
	}

	public void apply() throws AgentOperationFailedException, AgentLockedException {
		try {
			for (AgentImpl agent : membersToRemove.values()) {
				removeMember(agent);
			}
			membersToRemove.clear();

			for (AgentImpl agent : membersToAdd.values()) {
				addMember(agent);
			}
			membersToAdd.clear();
		} catch (CryptoException | SerializationException e) {
			throw new AgentOperationFailedException("Agent corrupted!", e);
		}
	}

}
