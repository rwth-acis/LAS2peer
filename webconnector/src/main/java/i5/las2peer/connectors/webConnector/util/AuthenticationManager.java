package i5.las2peer.connectors.webConnector.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Level;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;

import com.nimbusds.oauth2.sdk.ErrorObject;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPRequest.Method;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.openid.connect.sdk.UserInfoErrorResponse;
import com.nimbusds.openid.connect.sdk.UserInfoResponse;
import com.nimbusds.openid.connect.sdk.UserInfoSuccessResponse;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;

import i5.las2peer.api.security.*;
import i5.las2peer.connectors.webConnector.WebConnector;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.p2p.EthereumNode;
import i5.las2peer.security.*;
import i5.las2peer.tools.CryptoTools;
import net.minidev.json.JSONObject;

public class AuthenticationManager {

	private final L2pLogger logger = L2pLogger.getInstance(AuthenticationManager.class.getName());

	public static final String ACCESS_TOKEN_KEY = "access-token";
	public static final String OIDC_PROVIDER_KEY = "oidc_provider";

	private final WebConnector connector;

	public AuthenticationManager(WebConnector connector) {
		this.connector = connector;
	}

	// FIXME: "bearer" authorization is broken, because for the OIDC auto-registration we require both a token and a
	// password, and when sending a bearer authorization header, there can't also be the basic authorization header
	// containing the password
	// NOTE: the frontend currently always sends the access token when it has it, because it doesn't know whether the
	// OIDC agent has already been registered
	/**
	 * Returns agent for various authentication methods, including OIDC with auto-registration.
	 *
	 * For "regular" login, a prefixed identifier (containing agent ID, login, email, or OIDC sub) and a password are
	 * provided via the basic authorization header. This only attempts a login, no registration.
	 *
	 * However, whenever an access token is passed via any means, we use a different flow: OIDC "auto-registration".
	 * This means that we access the user profile from the OIDC server, verifying the access token.
	 * Then we use the (also required!) password from the basic authorization header (the username is ignored) and
	 * attempt to register the agent.
	 * If it exists, we try to unlock the existing agent.
	 */
	public AgentImpl authenticateAgent(MultivaluedMap<String, String> requestHeaders, String accessTokenQueryParam) {
		String authorizationHeader = requestHeaders.getFirst(HttpHeaders.AUTHORIZATION);
		String accessTokenHeader = requestHeaders.getFirst(ACCESS_TOKEN_KEY);

		String accessToken = extractToken(accessTokenHeader, authorizationHeader, accessTokenQueryParam);
		Credentials credentials = extractBasicAuthCredentials(authorizationHeader);

		try {
			if (accessToken != null) {
				if (credentials == null) {
					throw new BadRequestException("We require a password (in basic authorization header) when a OIDC access token sent! (see Javadoc)");
				}
				String oidcProviderHeader = requestHeaders.getFirst(OIDC_PROVIDER_KEY);
				return authenticateOIDC(accessToken, oidcProviderHeader, credentials);
			} else if (credentials != null) {
				// basic authentication
				return authenticateCredentials(credentials);
			} else {
				// anonymous login
				return AnonymousAgentImpl.getInstance();
			}
		} catch (AgentNotFoundException e) {
			logger.warning("agent not found");
			throw new NotAuthorizedException("agent not found", e);
		} catch (AgentAccessDeniedException e) {
			logger.warning("passphrase invalid");
			throw new NotAuthorizedException("passphrase invalid", e);
		} catch (AgentException e) {
			logger.warning("AgentException when trying to auth agent: " + e.getMessage());
			throw new NotAuthorizedException("not sure what went wrong", e);
		}
	}

	/**
	 * Returns unlocked agent corresponding to the given credentials.
	 * The identifier is not simply an agent ID (sorry) but starts with a namespace prefix as defined in
	 * {@link UserAgentManager} followed by the corresponding string. This allows logging in via either
	 * the agent ID, a login ID, an email address, and so on.
	 *
	 * {@see UserAgentManager#getAgentId(String)}
	 */
	private AgentImpl authenticateCredentials(Credentials credentials) throws AgentException {
		String prefixedIdentifier = credentials.identifier;
		String agentId;
		logger.info("attempting login with id: " + prefixedIdentifier);
		System.out.println(credentials.identifier);
		System.out.println(credentials.password);
		try {
			System.out.println("first");
			agentId = connector.getL2pNode().getUserManager().getAgentId(prefixedIdentifier);
			System.out.println("second");
		} catch (IllegalArgumentException e) {
			System.out.println("first exc");
			// no valid prefix. we could just throw an error here, but for potential backwards compatibility,
			// let's be forgiving and try to guess the type
			if (CryptoTools.isAgentID(prefixedIdentifier)) {
				System.out.println("third");
				agentId = prefixedIdentifier;
				System.out.println("fourth");
			} else {
				System.out.println("fifth");
				agentId = connector.getL2pNode().getAgentIdForLogin(prefixedIdentifier);
				System.out.println("sixt");
			}
		}
		
		System.out.println("seven");
		AgentImpl agent = connector.getL2pNode().getAgent(agentId);
		System.out.println("eight");
		((PassphraseAgentImpl) agent).unlock(credentials.password);
		System.out.println("nine");
		if (agent instanceof PassphraseAgentImpl) {
			((PassphraseAgentImpl) agent).unlock(credentials.password);
			logger.fine("passphrase accepted. Agent unlocked");
		}
		return agent;
	}

	/**
	 * Attempts to find an existing agent and unlock it, otherwise registers a new one.
	 *
	 * For registration, uses OIDC profile "preferred_username" as login name, ignoring identifier in credentials.
	 * For log-in, uses provided credentials, ignoring all OIDC data including the token.
	 *
	 * {@see UserAgentManager#getAgentId(String)}
	 */
	private PassphraseAgentImpl authenticateOIDC(String token, String oidcProviderHeader, Credentials credentials) throws AgentException {
		try {
			logger.info("OIDC sub found. Authenticating...");
			AgentImpl existingAgent = authenticateCredentials(credentials);
			System.out.println("authenticated");
			if (existingAgent instanceof UserAgentImpl) {
				System.out.println("existingAgent");
				return (UserAgentImpl) existingAgent;
			} else {
				System.out.println("noooot existingAgent");
				logger.warning("OIDC credentials were valid but agent had unexpected type");
				throw new AgentException("credentials were valid but agent had unexpected type");
			}
		} catch (AgentNotFoundException e) {
			// expected - auto-register
			logger.info("OIDC sub uknown. Auto-register..." + e);
			return createNewOidcAgent(token, oidcProviderHeader, credentials);
		}
	}

	/**
	 * @param basicAuthHeader basic authentication header containing base64 encoding of "identifier:password"
	 */
	private Credentials extractBasicAuthCredentials(String basicAuthHeader) {
		if (basicAuthHeader == null) {
			return null;
		}
		try {
			String encodedCredentials = basicAuthHeader.substring("BASIC ".length());
			String decodedCredentials = new String(Base64.getDecoder().decode(encodedCredentials), StandardCharsets.UTF_8);
			int separatorPos = decodedCredentials.indexOf(':');

			String identifier = decodedCredentials.substring(0, separatorPos);
			String password = decodedCredentials.substring(separatorPos + 1);
			return new Credentials(identifier, password);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private String extractToken(String accessTokenHeader, String authorizationHeader, String accessTokenQueryParam) {
		if (accessTokenHeader != null) {
			// get OIDC parameters from headers
			return accessTokenHeader;
		} else if (authorizationHeader != null && authorizationHeader.toLowerCase().startsWith("bearer ")) {
			// get BEARER token from Authentication field
			return authorizationHeader.substring("BEARER ".length());
		} else { // get OIDC parameters from GET values
			return accessTokenQueryParam;
		}
	}

	private JSONObject getOidcProviderInfo(String oidcProviderURI) {
		if (connector.oidcProviders.contains(oidcProviderURI)
				&& connector.oidcProviderInfos.get(oidcProviderURI) != null) {
			return connector.oidcProviderInfos.get(oidcProviderURI);
		} else {
			throw new IllegalArgumentException("The given OIDC provider (" + oidcProviderURI + ") is not whitelisted!"
					+ "Please make sure the complete OIDC provider URI is added to the config.");
		}
	}

	// send request to OpenID Connect user info endpoint to retrieve
	// complete user information in exchange for access token.
	private UserInfo retrieveOidcUserInfo(String endpoint, String authToken) {
		HTTPResponse hrs;
		try {
			URI userinfoEndpointUri = new URI(endpoint);
			HTTPRequest hrq = new HTTPRequest(Method.GET, userinfoEndpointUri.toURL());
			hrq.setAuthorization("Bearer " + authToken);

			// TODO process all error cases that can happen (in particular invalid tokens)
			hrs = hrq.send();
		} catch (IOException | URISyntaxException e) {
			throw new InternalServerErrorException("Fetching OIDC user info failed", e);
		}

		// process response from OpenID Connect user info endpoint
		UserInfoResponse userInfoResponse;
		try {
			userInfoResponse = UserInfoResponse.parse(hrs);
		} catch (ParseException e) {
			throw new InternalServerErrorException("Couldn't parse UserInfo response", e);
		}

		if (userInfoResponse instanceof UserInfoErrorResponse) {
			// failed request for OpenID Connect user info
			UserInfoErrorResponse uier = (UserInfoErrorResponse) userInfoResponse;
			ErrorObject err = uier.getErrorObject();
			String cause = "Session expired?";
			if (err != null) {
				cause = err.getDescription();
			}
			throw new NotAuthorizedException("Open ID Connect UserInfo request failed! Cause: " + cause);
		} else {
			return ((UserInfoSuccessResponse) userInfoResponse).getUserInfo();
		}
	}

	// FIXME find out why that lock is needed (is it??)
	/** @deprecated */
	private PassphraseAgentImpl getExistingOidcAgent(String oidcAgentId, String password) throws AgentException {
		try {
			connector.getLockOidc().lock(oidcAgentId);
			PassphraseAgentImpl pa = (PassphraseAgentImpl) connector.getL2pNode().getAgent(oidcAgentId);
			pa.unlock(password);

			// TODO provide OIDC user data for agent
			// if (pa instanceof UserAgent) {
			// 		((UserAgent) pa).setUserData(userInfo.toJSONString());
			// }

			return pa;
		} finally {
			connector.getLockOidc().unlock(oidcAgentId);
		}
	}

	private UserAgentImpl createNewOidcAgent(String token, String oidcProviderHeader, Credentials credentials) {
		String password = credentials.password;

		String endpoint;
		try {
			String oidcProviderURI = (oidcProviderHeader != null) ? oidcProviderHeader : connector.defaultOIDCProvider;
			JSONObject oidcProviderInfo = getOidcProviderInfo(oidcProviderURI);
			endpoint = (String) ((JSONObject) oidcProviderInfo.get("config")).get("userinfo_endpoint");
		} catch (IllegalArgumentException argErr) {
			throw new InternalServerErrorException(argErr);
		}

		JSONObject userInfo = retrieveOidcUserInfo(endpoint, token).toJSONObject();

		if (!userInfo.containsKey("sub") || !userInfo.containsKey("email") || !userInfo.containsKey("preferred_username")) {
			throw new ForbiddenException("Could not get all necessary OIDC fields. Please check your scopes.");
		}

		try {
			UserAgentImpl oidcAgent;
			if (connector.getL2pNode() instanceof EthereumNode) {
				EthereumNode ethNode = (EthereumNode) connector.getL2pNode();
				String loginName = (String) userInfo.get("preferred_username");
				oidcAgent = EthereumAgent.createEthereumAgentWithClient(loginName, password, ethNode.getRegistryClient());
			} else {
				// TODO: should we just always create an EthereumAgent?
				// should Node have a createAgent (instance? static?) method?
				// whatever, let's be conservative for now
				oidcAgent = UserAgentImpl.createUserAgent(password);
			}
			String oidcAgentId = oidcAgent.getIdentifier();
			try {
				connector.getLockOidc().lock(oidcAgentId);
				oidcAgent.unlock(password);
				oidcAgent.setEmail((String) userInfo.get("email"));
				oidcAgent.setLoginName((String) userInfo.get("preferred_username"));
				// TODO provide OIDC user data for agent
				// oidcAgent.setUserData(userInfo.toJSONString());
				connector.getL2pNode().storeAgent(oidcAgent);
				try {
					connector.getL2pNode().getUserManager().registerOIDCSub(oidcAgent, (String) userInfo.get("sub"));
				} catch (AgentAlreadyExistsException e) {
					logger.log(Level.FINE, "Could not register OIDC sub", e);
				}
				return oidcAgent;
			} finally {
				connector.getLockOidc().unlock(oidcAgentId);
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new InternalServerErrorException("OIDC agent creation failed", e);
		}
	}

	private class Credentials {
		String identifier;
		String password;
		Credentials(String identifier, String password) {
			this.identifier = identifier;
			this.password = password;
		}
	}
}
