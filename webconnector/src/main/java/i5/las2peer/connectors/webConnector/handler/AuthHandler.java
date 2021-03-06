package i5.las2peer.connectors.webConnector.handler;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.CookieParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import i5.las2peer.api.security.Agent;
import i5.las2peer.api.security.AgentNotFoundException;
import i5.las2peer.connectors.webConnector.WebConnector;
import i5.las2peer.connectors.webConnector.util.AgentSession;
import i5.las2peer.connectors.webConnector.util.AuthenticationManager;
import i5.las2peer.p2p.EthereumNode;
import i5.las2peer.p2p.Node;
import i5.las2peer.security.AgentImpl;
import i5.las2peer.security.AnonymousAgentImpl;
import i5.las2peer.security.EthereumAgent;
import i5.las2peer.security.PassphraseAgentImpl;
import i5.las2peer.security.UserAgentImpl;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;

@Path(AuthHandler.RESOURCE_PATH)
public class AuthHandler {

	public static final String RESOURCE_PATH = DefaultHandler.ROOT_RESOURCE_PATH + "/auth";

	private final WebConnector connector;
	private final Node node;

	public AuthHandler(WebConnector connector) {
		this.connector = connector;
		this.node = connector.getL2pNode();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/login")
	public Response getLogin(@Context UriInfo uriInfo, @Context HttpHeaders httpHeaders) throws Exception {
		Agent agent = connector.authenticateAgent(httpHeaders.getRequestHeaders(),
				uriInfo.getQueryParameters().getFirst(AuthenticationManager.ACCESS_TOKEN_KEY));
		if (!(agent instanceof PassphraseAgentImpl)) {
			throw new BadRequestException("Invalid agent type");
		}
		// FIXME is the agent always unlocked at this point?
		// FIXME check if agent is anonymous -- guess that shouldn't be allowed as "login"
		return registerAgentSession((PassphraseAgentImpl) agent);
	}

	// TODO refactor and de-duplicate this code (see agentshandler, authenticationmanager)
	@POST
	@Path("/create")
	@Produces(MediaType.APPLICATION_JSON)
	public Response handleRegistration(String requestBody) throws Exception {
		if (requestBody.trim().isEmpty()) {
			throw new BadRequestException("No request body");
		}
		JSONObject payload;
		try {
			payload = (JSONObject) new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE).parse(requestBody);
		} catch (ParseException e) {
			throw new BadRequestException("Could not parse json request body");
		}
		String password = payload.getAsString("password");
		if (password == null || password.isEmpty()) {
			throw new BadRequestException("No password provided");
		}
		String username = payload.getAsString("username");
		if (username != null && !username.isEmpty()) {
			// check if username is already taken
			try {
				node.getAgentIdForLogin(username);
				throw new BadRequestException("Username already taken");
			} catch (AgentNotFoundException e) {
				// expected
			}
		}
		String email = payload.getAsString("email");
		if (email != null && !email.isEmpty()) {
			// check if email is already taken
			try {
				node.getAgentIdForEmail(email);
				throw new BadRequestException("Email already taken");
			} catch (AgentNotFoundException e) {
				// expected
			}
		}
		String ethereumMnemonic = payload.getAsString("mnemonic");

		// create new user agent and store in network
		UserAgentImpl agent;
		if (node instanceof EthereumNode) {
			EthereumNode ethNode = (EthereumNode) node;
			if (ethereumMnemonic != null) {
				agent = EthereumAgent.createEthereumAgent(username, password, ethNode.getRegistryClient(), ethereumMnemonic);
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
		return registerAgentSession(agent);
	}

	private Response registerAgentSession(PassphraseAgentImpl agent) {
		// register session, set cookie and send response
		AgentSession session = connector.getOrCreateSession(agent);
		boolean secureCookie = false; // FIXME DEBUG
		NewCookie cookie = new NewCookie(WebConnector.COOKIE_SESSIONID_KEY, session.getSessionId(), "/", null, 1, null,
				-1, null, secureCookie, true);
		JSONObject json = new JSONObject();
		json.put("code", Status.OK.getStatusCode());
		json.put("text", Status.OK.getStatusCode() + " - Login OK");
		json.put("agentid", agent.getIdentifier());
		if (agent instanceof UserAgentImpl) {
			UserAgentImpl user = (UserAgentImpl) agent;
			json.put("username", user.getLoginName());
			json.put("email", user.getEmail());
		}
		if (agent instanceof EthereumAgent) {
			json.put("ethaddress", ((EthereumAgent) agent).getEthereumAddress());
		}
		return Response.ok(json.toJSONString(), MediaType.APPLICATION_JSON).cookie(cookie).build();
	}

	@GET
	@Path("/logout")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getLogout(@CookieParam(WebConnector.COOKIE_SESSIONID_KEY) String sessionId) {
		return handleLogout(sessionId);
	}

	@POST
	@Path("/logout")
	@Produces(MediaType.APPLICATION_JSON)
	public Response postLogout(@CookieParam(WebConnector.COOKIE_SESSIONID_KEY) String sessionId) {
		return handleLogout(sessionId);
	}

	private Response handleLogout(String sessionId) {
		connector.destroySession(sessionId);
		JSONObject json = new JSONObject();
		json.put("code", Status.OK.getStatusCode());
		json.put("text", Status.OK.getStatusCode() + " - Logout successful");
		return Response.ok(json.toJSONString(), MediaType.APPLICATION_JSON).build();
	}

	@GET
	@Path("/validate")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getValidate(@CookieParam(WebConnector.COOKIE_SESSIONID_KEY) String sessionId) {
		AgentSession session = connector.getSessionById(sessionId);
		if (session != null) {
			AgentImpl activeAgent = session.getAgent();
			if (activeAgent != null && !(activeAgent instanceof AnonymousAgentImpl)) {
				JSONObject json = new JSONObject();
				json.put("code", Status.OK.getStatusCode());
				json.put("text", Status.OK.getStatusCode() + " - Session OK");
				json.put("agentid", activeAgent.getIdentifier());
				return Response.ok(json.toJSONString(), MediaType.APPLICATION_JSON).build();
			}
		}
		JSONObject json = new JSONObject();
		json.put("code", Status.OK.getStatusCode());
		json.put("text", Status.OK.getStatusCode() + " - Session invalid");
		return Response.ok(json.toJSONString(), MediaType.APPLICATION_JSON).build();
	}

}
