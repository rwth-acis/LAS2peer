package i5.las2peer.connectors.webConnector.handler;

import java.io.InputStream;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.logging.Level;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.CookieParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.media.multipart.FormDataParam;

import i5.las2peer.api.security.AgentAccessDeniedException;
import i5.las2peer.api.security.AgentException;
import i5.las2peer.api.security.AgentNotFoundException;
import i5.las2peer.connectors.webConnector.WebConnector;
import i5.las2peer.connectors.webConnector.util.AgentSession;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.p2p.EthereumNode;
import i5.las2peer.p2p.Node;
import i5.las2peer.registry.data.UserProfileData;
import i5.las2peer.registry.exceptions.EthereumException;
import i5.las2peer.registry.exceptions.NotFoundException;
import i5.las2peer.security.AgentImpl;
import i5.las2peer.security.EthereumAgent;
import i5.las2peer.security.GroupAgentImpl;
import i5.las2peer.security.GroupEthereumAgent;
import i5.las2peer.security.PassphraseAgentImpl;
import i5.las2peer.security.UserAgentImpl;
import i5.las2peer.serialization.SerializationException;
import i5.las2peer.tools.CryptoException;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;

@Path(AgentsHandler.RESOURCE_PATH)
public class AgentsHandler {

	public static final String RESOURCE_PATH = DefaultHandler.ROOT_RESOURCE_PATH + "/agents";

	private final L2pLogger logger = L2pLogger.getInstance(AgentsHandler.class);

	private final WebConnector connector;
	private final Node node;
	private final EthereumNode ethereumNode;

	public AgentsHandler(WebConnector connector) {
		this.connector = connector;
		this.node = connector.getL2pNode();
		ethereumNode = (node instanceof EthereumNode) ? (EthereumNode) node : null;
	}

	@POST
	@Path("/createAgent")
	@Produces(MediaType.APPLICATION_JSON)
	public Response handleCreateAgent(@FormDataParam("username") String username, @FormDataParam("email") String email,
			@FormDataParam("mnemonic") String ethereumMnemonic, @FormDataParam("password") String password)
			throws Exception {
		if (password == null || password.isEmpty()) {
			return Response.status(Status.BAD_REQUEST).entity("No password provided").build();
		}
		if (username != null && !username.isEmpty()) {
			// check if username is already taken
			try {
				node.getAgentIdForLogin(username);
				return Response.status(Status.BAD_REQUEST).entity("Username already taken").build();
			} catch (AgentNotFoundException e) {
				// expected
			}
		}
		if (email != null && !email.isEmpty()) {
			// check if email is already taken
			try {
				node.getAgentIdForEmail(email);
				return Response.status(Status.BAD_REQUEST).entity("Email already taken").build();
			} catch (AgentNotFoundException e) {
				// expected
			}
		}
		// create new user agent and store in network
		// TODO: deduplicate this / AuthHandler#handleRegistration
		UserAgentImpl agent;
		if (node instanceof EthereumNode) {
			EthereumNode ethNode = (EthereumNode) node;
			if (ethereumMnemonic != null && !ethereumMnemonic.isEmpty()) {
				agent = EthereumAgent.createEthereumAgent(username, password, ethNode.getRegistryClient(),
						ethereumMnemonic);
			} else {
				agent = EthereumAgent.createEthereumAgentWithClient(username, password, ethNode.getRegistryClient());
			}
		} else {
			agent = UserAgentImpl.createUserAgent(password);
		}
		agent.unlock(password);
		if (username != null && !username.isEmpty()) {
			agent.setLoginName(username);
		}
		if (email != null && !email.isEmpty()) {
			agent.setEmail(email);
		}
		node.storeAgent(agent);
		JSONObject json = new JSONObject().appendField("code", Status.OK.getStatusCode())
				.appendField("text", Status.OK.getStatusCode() + " - Agent created")
				.appendField("agentid", agent.getIdentifier()).appendField("username", agent.getLoginName())
				.appendField("email", agent.getEmail());
		if (agent instanceof EthereumAgent) {
			json.put("registryAddress", ((EthereumAgent) agent).getEthereumAddress());
		}
		return Response.ok(json.toJSONString(), MediaType.APPLICATION_JSON).build();
	}

	private JSONObject addAgentDetailsToJson(AgentImpl agent, JSONObject json)
			throws EthereumException, NotFoundException {
		// don't add mnemonic ( = defaults to false, override to true )
		return this.addAgentDetailsToJson(agent, json, false);
	}

	private JSONObject addAgentDetailsToJson(AgentImpl agent, JSONObject json, Boolean addMnemonic)
			throws EthereumException, NotFoundException {
		json.put("agentid", agent.getIdentifier());
		if (agent instanceof UserAgentImpl) {
			UserAgentImpl userAgent = (UserAgentImpl) agent;
			json.put("username", userAgent.getLoginName());
			json.put("email", userAgent.getEmail());
		}
		if (agent instanceof EthereumAgent) {
			EthereumAgent ethAgent = (EthereumAgent) agent;
			String ethAddress = ethAgent.getEthereumAddress();
			if (ethAddress.length() > 0) {
				json.put("ethAgentAddress", ethAddress);
				String accBalance = ethereumNode.getRegistryClient().getAccountBalance(ethAddress);
				json.put("ethAccBalance", accBalance);
				UserProfileData upd = null;
				try {
					upd = ethereumNode.getRegistryClient().getProfile(ethAddress);
					if (upd != null && !upd.getOwner().equals("0x0000000000000000000000000000000000000000")) {
						json.put("ethProfileOwner", upd.getOwner());
						json.put("ethCumulativeScore", upd.getCumulativeScore().toString());
						json.put("ethNoTransactionsSent", upd.getNoTransactionsSent().toString());
						json.put("ethNoTransactionsRcvd", upd.getNoTransactionsRcvd().toString());
						if (upd.getNoTransactionsRcvd().compareTo(BigInteger.ZERO) == 0) {
							json.put("ethRating", 0);
						} else {
							DecimalFormat f = new DecimalFormat("#.00");
							json.put("ethRating",
									f.format(upd.getCumulativeScore().divide(upd.getNoTransactionsRcvd())));
						}
					} else {
						json.put("ethRating", "0");
						json.put("ethCumulativeScore", "???");
						json.put("ethNoTransactionsSent", "???");
						json.put("ethNoTransactionsRcvd", "???");
					}
				} catch (EthereumException | NotFoundException e) {
					e.printStackTrace();
				}
			}

			json.put("ethAgentCredentialsAddress", ethAddress);

			if (addMnemonic && !agent.isLocked()) {
				json.put("ethMnemonic", ethAgent.getEthereumMnemonic());
			}
		}
		return json;
	}

	@POST
	@Path("/getAgent")
	@Produces(MediaType.APPLICATION_JSON)
	public Response handleGetAgent(@FormDataParam("agentid") String agentId, @FormDataParam("username") String username,
			@FormDataParam("email") String email) throws Exception {
		AgentImpl agent = getAgentByDetail(agentId, username, email);
		JSONObject json = new JSONObject();
		try {
			json = addAgentDetailsToJson(agent, json);
		} catch (EthereumException e) {
			return Response.status(Status.NOT_FOUND).entity("Agent not found").build();
		} catch (NotFoundException e) {
			return Response.status(Status.NOT_FOUND).entity("Username not registered").build();
		}
		return Response.ok(json.toJSONString(), MediaType.APPLICATION_JSON).build();
	}

	@POST
	@Path("/exportAgent")
	public Response handleExportAgent(@FormDataParam("agentid") String agentId,
			@FormDataParam("username") String username, @FormDataParam("email") String email) throws Exception {
		AgentImpl agent = getAgentByDetail(agentId, username, email);
		return Response.ok(agent.toXmlString(), MediaType.APPLICATION_XML).build();
	}

	private AgentImpl getAgentByDetail(String agentId, String username, String email) throws Exception {
		try {
			if (agentId == null || agentId.isEmpty()) {
				if (username != null && !username.isEmpty()) {
					agentId = node.getAgentIdForLogin(username);
				} else if (email != null && !email.isEmpty()) {
					agentId = node.getAgentIdForEmail(email);
				} else {
					throw new BadRequestException("No required agent detail provided");
				}
			}
			return node.getAgent(agentId);
		} catch (AgentNotFoundException e) {
			throw new BadRequestException("Agent not found");
		}
	}

	@POST
	@Path("/uploadAgent")
	public Response handleUploadAgent(@FormDataParam("agentFile") InputStream agentFile,
			@FormDataParam("password") String password,
			@CookieParam(WebConnector.COOKIE_SESSIONID_KEY) String sessionId) throws Exception {
		if (agentFile == null) {
			return Response.status(Status.BAD_REQUEST).entity("No agent file provided").build();
		}
		AgentImpl agent = AgentImpl.createFromXml(agentFile);
		if (agent instanceof PassphraseAgentImpl) {
			PassphraseAgentImpl passphraseAgent = (PassphraseAgentImpl) agent;
			if (password == null) {
				return Response.status(Status.BAD_REQUEST).entity("No password provided").build();
			}
			try {
				passphraseAgent.unlock(password);
			} catch (AgentAccessDeniedException e) {
				return Response.status(Status.BAD_REQUEST).entity("Invalid agent password").build();
			}
		} else if (agent instanceof GroupAgentImpl) {
			GroupAgentImpl groupAgent = (GroupAgentImpl) agent;
			AgentSession session = connector.getSessionById(sessionId);
			if (session == null) {
				return Response.status(Status.BAD_REQUEST)
						.entity("You have to be logged in, to unlock and update a group").build();
			}
			try {
				groupAgent.unlock(session.getAgent());
			} catch (AgentAccessDeniedException e) {
				return Response.status(Status.FORBIDDEN).entity("You have to be a member of the uploaded group")
						.build();
			}
		} else {
			return Response.status(Status.BAD_REQUEST)
					.entity("Invalid agent type '" + agent.getClass().getSimpleName() + "'").build();
		}
		node.storeAgent(agent);
		JSONObject json = new JSONObject();
		json.put("code", Status.OK.getStatusCode());
		json.put("text", Status.OK.getStatusCode() + " - Agent uploaded");
		json.put("agentid", agent.getIdentifier());
		return Response.ok(json.toJSONString(), MediaType.APPLICATION_JSON).build();
	}

	@POST
	@Path("/changePassphrase")
	public Response handleChangePassphrase(@FormDataParam("agentid") String agentId,
			@FormDataParam("passphrase") String passphrase, @FormDataParam("passphraseNew") String passphraseNew,
			@FormDataParam("passphraseNew2") String passphraseNew2) throws Exception {
		if (agentId == null || agentId.isEmpty()) {
			return Response.status(Status.BAD_REQUEST).entity("No agentid provided").build();
		}
		AgentImpl agent;
		try {
			agent = node.getAgent(agentId);
		} catch (AgentNotFoundException e) {
			return Response.status(Status.BAD_REQUEST).entity("Agent not found").build();
		}
		if (!(agent instanceof PassphraseAgentImpl)) {
			return Response.status(Status.BAD_REQUEST).entity("Invalid agent type").build();
		}
		PassphraseAgentImpl passAgent = (PassphraseAgentImpl) agent;
		if (passphrase == null || passphrase.isEmpty()) {
			return Response.status(Status.BAD_REQUEST).entity("No passphrase provided").build();
		}
		try {
			passAgent.unlock(passphrase);
		} catch (AgentAccessDeniedException e) {
			return Response.status(Status.BAD_REQUEST).entity("Invalid passphrase").build();
		}
		if (passphraseNew == null || passphraseNew.isEmpty()) {
			return Response.status(Status.BAD_REQUEST).entity("No passphrase to change provided").build();
		}
		if (passphraseNew2 == null || passphraseNew2.isEmpty()) {
			return Response.status(Status.BAD_REQUEST).entity("No passphrase repetition provided").build();
		}
		if (!passphraseNew.equals(passphraseNew2)) {
			return Response.status(Status.BAD_REQUEST).entity("New passphrase and repetition do not match").build();
		}
		passAgent.changePassphrase(passphraseNew);
		node.storeAgent(passAgent);
		JSONObject json = new JSONObject();
		json.put("code", Status.OK.getStatusCode());
		json.put("text", Status.OK.getStatusCode() + " - Passphrase changed");
		json.put("agentid", agent.getIdentifier());
		return Response.ok(json.toJSONString(), MediaType.APPLICATION_JSON).build();
	}

	@POST
	@Path("/createGroup")
	public Response handleCreateGroup(@CookieParam(WebConnector.COOKIE_SESSIONID_KEY) String sessionId,
			@FormDataParam("members") String members, @FormDataParam("name") String groupName) throws Exception {
		AgentSession session = connector.getSessionById(sessionId);
		if (session == null) {
			return Response.status(Status.FORBIDDEN).entity("You have to be logged in to create a group").build();
		}
		if (members == null) {
			return Response.status(Status.BAD_REQUEST).entity("No members provided").build();
		}

		if (groupName == null || groupName.equals("")) {
			return Response.status(Status.BAD_REQUEST).entity("No group name provided").build();
		}

		JSONArray jsonMembers = (JSONArray) new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE).parse(members);
		if (jsonMembers.isEmpty()) {
			return Response.status(Status.BAD_REQUEST).entity("Members list empty").build();
		}
		ArrayList<AgentImpl> memberAgents = new ArrayList<>(jsonMembers.size());
		for (Object objMember : jsonMembers) {
			if (objMember instanceof JSONObject) {
				JSONObject jsonMember = (JSONObject) objMember;
				String agentId = jsonMember.getAsString("agentid");
				try {
					String idofmember = node.getAgentIdForLogin(agentId);
					AgentImpl memberAgent = node.getAgent(idofmember);
					memberAgents.add(memberAgent);
				} catch (Exception e) {
					System.out.println("Exception " + e + "occured");
					System.out.println("Couldn't find agent based on name, trying id...");
					try {
						AgentImpl memberAgent = node.getAgent(agentId);
						memberAgents.add(memberAgent);

					} catch (AgentNotFoundException f) {
						logger.log(Level.WARNING, "Could not retrieve group member agent from network", f);
						continue;
					}
				}
			}
		}
		try {
			node.getAgentIdForGroupName(groupName);
			return Response.status(Status.BAD_REQUEST).entity("Groupname already taken").build();
		} catch (AgentNotFoundException e) {
			// expected
		}
		GroupAgentImpl groupAgent;
		if (node instanceof EthereumNode) {
			EthereumNode ethNode = (EthereumNode) node;
			groupAgent = GroupEthereumAgent.createGroupEthereumAgentWithClient(groupName, ethNode.getRegistryClient(),
					memberAgents.toArray(new AgentImpl[memberAgents.size()]));
		} else {
			groupAgent = GroupAgentImpl.createGroupAgent(memberAgents.toArray(new AgentImpl[memberAgents.size()]),
					groupName);
		}
		if (groupAgent instanceof GroupEthereumAgent) {
			System.out.println("oooookk heeere iiiss ggroupeetthaagennt");

		}
		groupAgent.unlock(session.getAgent());
		groupAgent.addAdmin(session.getAgent());
		System.out.println(session.getAgent().getIdentifier());
		node.storeAgent((GroupEthereumAgent) groupAgent);
		JSONObject json = new JSONObject();
		json.put("code", Status.OK.getStatusCode());
		json.put("text", Status.OK.getStatusCode() + " - GroupAgent created");
		json.put("agentid", groupAgent.getIdentifier());
		json.put("groupName", groupName);
		return Response.ok(json.toJSONString(), MediaType.APPLICATION_JSON).build();
	}

	@POST
	@Path("/loadGroup")
	public Response handleLoadGroup(@CookieParam(WebConnector.COOKIE_SESSIONID_KEY) String sessionId,
			@FormDataParam("groupIdentifier") String agentId) throws AgentException {
		AgentSession session = connector.getSessionById(sessionId);
		if (session == null) {
			return Response.status(Status.FORBIDDEN).entity("You have to be logged in to load a group").build();
		}
		if (agentId == null || agentId.isEmpty()) {
			return Response.status(Status.BAD_REQUEST).entity("No agent id provided").build();
		}
		AgentImpl agent;
		System.out.println("Trying to get agent by group name in laod group call");
		try {
			agent = getGroupByName(agentId);
		} catch (Exception e) {
			System.out.println("Exception " + e + "occured");
			System.out.println("Couldn't find agent based on group name, trying group id...");
			try {
				agent = node.getAgent(agentId);

			} catch (AgentNotFoundException f) {
				return Response.status(Status.BAD_REQUEST).entity("Agent not found").build();
			}
		}
		if (!(agent instanceof GroupAgentImpl)) {
			return Response.status(Status.BAD_REQUEST).entity("Agent is not a GroupAgent").build();
		}
		GroupAgentImpl groupAgent = (GroupAgentImpl) agent;
		try {
			groupAgent.unlock(session.getAgent());
		} catch (AgentAccessDeniedException e) {
			return Response.status(Status.BAD_REQUEST).entity("You must be a member of this group").build();
		}
		System.out.println(groupAgent.getGroupName());
		System.out.println(groupAgent.getIdentifier());
		JSONObject json = new JSONObject();
		json.put("code", Status.OK.getStatusCode());
		json.put("text", Status.OK.getStatusCode() + " - GroupAgent loaded");
		json.put("agentid", groupAgent.getIdentifier());
		JSONArray memberList = new JSONArray();
		for (String memberid : groupAgent.getMemberList()) {
			JSONObject member = new JSONObject();
			member.put("agentid", memberid);
			try {
				AgentImpl memberAgent = node.getAgent(memberid);
				if (memberAgent instanceof UserAgentImpl) {
					UserAgentImpl memberUserAgent = (UserAgentImpl) memberAgent;
					member.put("username", memberUserAgent.getLoginName());
					member.put("email", memberUserAgent.getEmail());
				}
			} catch (AgentException e) {
				logger.log(Level.WARNING, "Could not retrieve group member agent from network", e);
			}
			memberList.add(member);
		}
		json.put("members", memberList);
		return Response.ok(json.toJSONString(), MediaType.APPLICATION_JSON).build();
	}

	private AgentImpl getGroupByName(String groupName) throws Exception {
		try {
			String agentId = node.getAgentIdForGroupName(groupName);
			System.out.println("Agent id is" + agentId);
			return node.getAgent(agentId);
		} catch (AgentNotFoundException e) {
			throw new BadRequestException("Agent not found");
		}
	}

	@POST
	@Path("/changeGroup")
	public Response handleChangeGroup(@CookieParam(WebConnector.COOKIE_SESSIONID_KEY) String sessionId,
			@FormDataParam("agentid") String agentId, @FormDataParam("members") String members)
			throws AgentException, CryptoException, SerializationException, ParseException {
		AgentSession session = connector.getSessionById(sessionId);
		if (session == null) {
			return Response.status(Status.FORBIDDEN).entity("You have to be logged in to change a group").build();
		}
		if (agentId == null || agentId.isEmpty()) {
			return Response.status(Status.BAD_REQUEST).entity("No agent id provided").build();
		}
		if (members == null) {
			return Response.status(Status.BAD_REQUEST).entity("No members to change provided").build();
		}
		System.out.println(members);
		JSONArray changedMembers = (JSONArray) new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE).parse(members);
		System.out.println(changedMembers);
		if (changedMembers.isEmpty()) {
			return Response.status(Status.BAD_REQUEST).entity("Changed members list must not be empty").build();
		}
		AgentImpl agent;
		try {
			// for some reason, when loading group using group name results in identifier
			// being group name
			agent = getGroupByName(agentId);
		} catch (Exception e) {
			System.out.println("Exception " + e + "occured");
			System.out.println("Couldn't find agent based on group name, trying group id...");
			try {
				agent = node.getAgent(agentId);

			} catch (AgentNotFoundException f) {
				return Response.status(Status.BAD_REQUEST).entity("Agent not found").build();
			}
		}
		if (!(agent instanceof GroupAgentImpl)) {
			return Response.status(Status.BAD_REQUEST).entity("Agent is not a GroupAgent").build();
		}
		GroupAgentImpl groupAgent = (GroupAgentImpl) agent;
		try {
			groupAgent.unlock(session.getAgent());
		} catch (AgentAccessDeniedException e) {
			return Response.status(Status.BAD_REQUEST).entity("You must be a member of this group").build();
		}
		// add new members // how?, currently only possile to remove users on agent
		// tools frontend
		HashSet<String> memberIds = new HashSet<>();
		for (Object obj : changedMembers) {
			System.out.println(obj);
			System.out.println(obj.toString());
			try {
				JSONObject json = (JSONObject) new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE).parse((String) obj);
				obj = json;
			} catch (Exception e) {
				System.out.println("Could not convert string to json lol");
			}
			if (obj instanceof JSONObject) {
				JSONObject json = (JSONObject) obj;
				String memberid = json.getAsString("agentid");
				if (memberid == null || memberid.isEmpty()) {
					logger.fine("Skipping invalid member id '" + memberid + "'");
					continue;
				}
				try {
					String idofmember = node.getAgentIdForLogin(memberid);
					AgentImpl memberAgent = node.getAgent(idofmember);
					groupAgent.addMember(memberAgent);
					memberIds.add(idofmember.toLowerCase());
				} catch (Exception e) {
					System.out.println("Exception " + e + "occured");
					System.out.println("Couldn't find agent based on name, trying id...");
					try {
						AgentImpl memberAgent = node.getAgent(memberid);
						groupAgent.addMember(memberAgent);
						memberIds.add(memberid.toLowerCase());
					} catch (AgentNotFoundException f) {
						logger.log(Level.WARNING, "Could not retrieve group member agent from network", f);
						continue;
					}
				}
			} else {
				logger.info("Skipping invalid member object '" + obj.getClass().getCanonicalName() + "'");
			}
		}
		/*
		 * if (!memberIds.contains(session.getAgent().getIdentifier().toLowerCase())) {
		 * return Response.status(Status.BAD_REQUEST).
		 * entity("You can't remove yourself from a group").build(); }
		 */
		if (!groupAgent.isAdmin(session.getAgent())) {
			return Response.status(Status.BAD_REQUEST).entity("You must be an admin of this group").build();
		}
		// remove all non members
		for (String oldMemberId : groupAgent.getMemberList()) {
			if (!memberIds.contains(oldMemberId)) {
				groupAgent.removeMember(oldMemberId);
				logger.info("Removed old member '" + oldMemberId + "' from group");
			}
		}
		// store changed group
		node.storeAgent(groupAgent);
		JSONObject json = new JSONObject();
		json.put("code", Status.OK.getStatusCode());
		json.put("text", Status.OK.getStatusCode() + " - GroupAgent changed");
		json.put("agentid", groupAgent.getIdentifier());
		return Response.ok(json.toJSONString(), MediaType.APPLICATION_JSON).build();
	}

}
