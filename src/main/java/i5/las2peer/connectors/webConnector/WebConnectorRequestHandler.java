package i5.las2peer.connectors.webConnector;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.nimbusds.oauth2.sdk.ErrorObject;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPRequest.Method;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.openid.connect.sdk.UserInfoErrorResponse;
import com.nimbusds.openid.connect.sdk.UserInfoResponse;
import com.nimbusds.openid.connect.sdk.UserInfoSuccessResponse;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsExchange;

import i5.las2peer.api.execution.ServiceInvocationException;
import i5.las2peer.api.p2p.ServiceNameVersion;
import i5.las2peer.api.p2p.ServiceVersion;
import i5.las2peer.api.security.AgentAccessDeniedException;
import i5.las2peer.api.security.AgentException;
import i5.las2peer.api.security.AgentLockedException;
import i5.las2peer.api.security.AgentNotFoundException;
import i5.las2peer.api.security.AgentOperationFailedException;
import i5.las2peer.api.security.AnonymousAgent;
import i5.las2peer.p2p.AgentAlreadyRegisteredException;
import i5.las2peer.p2p.AliasNotFoundException;
import i5.las2peer.p2p.Node;
import i5.las2peer.p2p.ServiceAliasManager.AliasResolveResponse;
import i5.las2peer.restMapper.RESTResponse;
import i5.las2peer.security.AnonymousAgentImpl;
import i5.las2peer.security.Mediator;
import i5.las2peer.security.PassphraseAgentImpl;
import i5.las2peer.security.UserAgentImpl;
import i5.las2peer.tools.CryptoException;
import i5.las2peer.tools.CryptoTools;
import i5.las2peer.tools.SimpleTools;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Swagger;
import io.swagger.models.auth.OAuth2Definition;
import io.swagger.util.Json;
import net.minidev.json.JSONObject;

/**
 * A HttpServer RequestHandler for handling requests to the las2peer Web Connector. Each request will be distributed to
 * its corresponding session.
 * 
 */
public class WebConnectorRequestHandler implements HttpHandler {

	private static final String AUTHENTICATION_FIELD = "Authorization";
	private static final String ACCESS_TOKEN_KEY = "access_token";
	private static final String OIDC_PROVIDER_KEY = "oidc_provider";
	private static final int NO_RESPONSE_BODY = -1;
	// 0 means chunked transfer encoding, see
	// https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/HttpExchange.html#sendResponseHeaders-int-long-
	private static final List<String> INGORE_HEADERS = Arrays.asList(AUTHENTICATION_FIELD.toLowerCase(),
			ACCESS_TOKEN_KEY.toLowerCase(), OIDC_PROVIDER_KEY.toLowerCase());
	private static final List<String> INGORE_QUERY_PARAMS = Arrays.asList(ACCESS_TOKEN_KEY.toLowerCase(),
			OIDC_PROVIDER_KEY.toLowerCase());

	private WebConnector connector;
	private Node l2pNode;

	public WebConnectorRequestHandler(WebConnector connector) {
		this.connector = connector;
		l2pNode = connector.getL2pNode();
	}

	@Override
	public void handle(HttpExchange exchange) throws IOException {
		exchange.getResponseHeaders().set("Server-Name", "las2peer WebConnector");

		try {
			PassphraseAgentImpl userAgent;
			if ((userAgent = authenticate(exchange)) != null) {
				invoke(userAgent, exchange);
			}
		} catch (Exception e) {
			sendUnexpectedErrorResponse(exchange, e.toString(), e);
		}

		// otherwise the client waits till the timeout for an answer
		exchange.getResponseBody().close();
	}

	private PassphraseAgentImpl authenticate(HttpExchange exchange) throws UnsupportedEncodingException {
		if (exchange.getRequestHeaders().containsKey(AUTHENTICATION_FIELD)
				&& exchange.getRequestHeaders().getFirst(AUTHENTICATION_FIELD).toLowerCase().startsWith("basic ")) {
			// basic authentication
			return authenticateBasic(exchange);
		} else if (connector.oidcProviderInfos != null && ((exchange.getRequestURI().getRawQuery() != null
				&& exchange.getRequestURI().getRawQuery().contains(ACCESS_TOKEN_KEY + "="))
				|| exchange.getRequestHeaders().containsKey(ACCESS_TOKEN_KEY)
				|| (exchange.getRequestHeaders().containsKey(AUTHENTICATION_FIELD) && exchange.getRequestHeaders()
						.getFirst(AUTHENTICATION_FIELD).toLowerCase().startsWith("bearer ")))) {
			// openid connect
			return authenticateOIDC(exchange);
		} else if (connector.defaultLoginUser.length() > 0) {
			// anonymous login
			return authenticateNamePassword(connector.defaultLoginUser, connector.defaultLoginPassword, exchange);
		} else if (exchange.getRequestMethod().equalsIgnoreCase("options")) {
			return authenticateAnonymous(exchange);
		} else {
			sendUnauthorizedResponse(exchange, null, exchange.getRemoteAddress() + ": No Authentication provided!");
			return null;
		}
	}

	private PassphraseAgentImpl authenticateBasic(HttpExchange exchange) {
		// looks like: Authentication Basic <Byte64(name:pass)>
		String userPass = exchange.getRequestHeaders().getFirst(AUTHENTICATION_FIELD).substring("BASIC ".length());
		userPass = new String(Base64.getDecoder().decode(userPass), StandardCharsets.UTF_8);
		int separatorPos = userPass.indexOf(':');

		// get username and password
		String username = userPass.substring(0, separatorPos);
		String password = userPass.substring(separatorPos + 1);

		return authenticateNamePassword(username, password, exchange);
	}

	private PassphraseAgentImpl authenticateOIDC(HttpExchange exchange) throws UnsupportedEncodingException {
		// extract token
		String token = "";
		String oidcProviderURI = connector.defaultOIDCProvider;
		if (exchange.getRequestHeaders().containsKey(ACCESS_TOKEN_KEY)) {
			// get OIDC parameters from headers
			token = exchange.getRequestHeaders().getFirst(ACCESS_TOKEN_KEY);
			if (exchange.getRequestHeaders().containsKey(OIDC_PROVIDER_KEY)) {
				oidcProviderURI = URLDecoder.decode(exchange.getRequestHeaders().getFirst(OIDC_PROVIDER_KEY),
						StandardCharsets.UTF_8.name());
			}
		} else if (exchange.getRequestHeaders().containsKey(AUTHENTICATION_FIELD)
				&& exchange.getRequestHeaders().getFirst(AUTHENTICATION_FIELD).toLowerCase().startsWith("bearer ")) {
			// get BEARER token from Authentication field
			token = exchange.getRequestHeaders().getFirst(AUTHENTICATION_FIELD).substring("BEARER ".length());
			if (exchange.getRequestHeaders().containsKey(OIDC_PROVIDER_KEY)) {
				oidcProviderURI = URLDecoder.decode(exchange.getRequestHeaders().getFirst(OIDC_PROVIDER_KEY),
						StandardCharsets.UTF_8.name());
			}
		} else { // get OIDC parameters from GET values
			String[] params = exchange.getRequestURI().getRawQuery().split("&");
			for (String param : params) {
				String[] keyval = param.split("=");
				if (keyval[0].equalsIgnoreCase(ACCESS_TOKEN_KEY)) {
					token = keyval[1];
				} else if (keyval[0].equalsIgnoreCase(OIDC_PROVIDER_KEY)) {
					oidcProviderURI = URLDecoder.decode(keyval[1], StandardCharsets.UTF_8.name());
				}
			}
		}

		// validate given OIDC provider and get provider info
		JSONObject oidcProviderInfo = null;
		if (!connector.oidcProviders.contains(oidcProviderURI)
				|| connector.oidcProviderInfos.get(oidcProviderURI) == null) {
			sendStringResponse(exchange, HttpURLConnection.HTTP_INTERNAL_ERROR, "The given OIDC provider ("
					+ oidcProviderURI
					+ ") is not whitelisted! Please make sure the complete OIDC provider URI is added to the config.");
			return null;
		} else {
			oidcProviderInfo = connector.oidcProviderInfos.get(oidcProviderURI);
		}

		// send request to OpenID Connect user info endpoint to retrieve complete user information
		// in exchange for access token.
		HTTPRequest hrq;
		HTTPResponse hrs;

		try {
			URI userinfoEndpointUri = new URI(
					(String) ((JSONObject) oidcProviderInfo.get("config")).get("userinfo_endpoint"));
			hrq = new HTTPRequest(Method.GET, userinfoEndpointUri.toURL());
			hrq.setAuthorization("Bearer " + token);

			// TODO process all error cases that can happen (in particular invalid tokens)
			hrs = hrq.send();
		} catch (IOException | URISyntaxException e) {
			sendUnexpectedErrorResponse(exchange, "Fetching OIDC user info failed", e);
			return null;
		}

		// process response from OpenID Connect user info endpoint
		UserInfoResponse userInfoResponse;
		try {
			userInfoResponse = UserInfoResponse.parse(hrs);
		} catch (ParseException e) {
			sendUnexpectedErrorResponse(exchange, "Couldn't parse UserInfo response", e);
			return null;
		}

		// failed request for OpenID Connect user info
		if (userInfoResponse instanceof UserInfoErrorResponse) {
			UserInfoErrorResponse uier = (UserInfoErrorResponse) userInfoResponse;
			ErrorObject err = uier.getErrorObject();
			String cause = "Session expired?";
			if (err != null) {
				cause = err.getDescription();
			}
			sendStringResponse(exchange, HttpURLConnection.HTTP_UNAUTHORIZED,
					"Open ID Connect UserInfo request failed! Cause: " + cause);
			return null;
		}

		// successful request
		UserInfo userInfo = ((UserInfoSuccessResponse) userInfoResponse).getUserInfo();

		JSONObject ujson = userInfo.toJSONObject();

		if (!ujson.containsKey("sub") || !ujson.containsKey("email") || !ujson.containsKey("preferred_username")) {
			sendStringResponse(exchange, HttpURLConnection.HTTP_FORBIDDEN,
					"Could not get provider information. Please check your scopes.");
			return null;
		}

		String sub = (String) ujson.get("sub");

		String oidcAgentId;
		try {
			oidcAgentId = SimpleTools.byteToHexString(CryptoTools.getSecureHash(sub.getBytes()));
		} catch (CryptoException e) {
			connector.logError("Could not derive agent id from oidc sub", e);
			sendStringResponse(exchange, HttpURLConnection.HTTP_UNAUTHORIZED, e.toString());
			return null;
		}
		// TODO choose other scheme for generating agent password
		String password = sub;

		this.connector.getLockOidc().lock(oidcAgentId + "");

		try {
			try {
				PassphraseAgentImpl pa = (PassphraseAgentImpl) l2pNode.getAgent(oidcAgentId);
				pa.unlock(password);
				if (pa instanceof UserAgentImpl) {
					UserAgentImpl ua = (UserAgentImpl) pa;
					// TODO provide OIDC user data for agent
//					ua.setUserData(ujson.toJSONString());
					return ua;
				} else {
					return pa;
				}
			} catch (AgentAccessDeniedException e) {
				connector.logError("Authentication failed!", e);
				sendStringResponse(exchange, HttpURLConnection.HTTP_UNAUTHORIZED, e.toString());
				return null;
			} catch (AgentNotFoundException e) {
				UserAgentImpl oidcAgent;
				try {
					oidcAgent = UserAgentImpl.createUserAgent(password);
					oidcAgent.unlock(password);

					oidcAgent.setEmail((String) ujson.get("email"));
					oidcAgent.setLoginName((String) ujson.get("preferred_username"));
					// TODO provide OIDC user data for agent
//					oidcAgent.setUserData(ujson.toJSONString());

					l2pNode.storeAgent(oidcAgent);
					l2pNode.getUserManager().registerOIDCSub(oidcAgent, sub);
					return oidcAgent;
				} catch (Exception e1) {
					sendUnexpectedErrorResponse(exchange, "OIDC agent creation failed", e1);
					return null;
				}
			} catch (AgentException e) {
				connector.logError("Could not retrieve and unlock agent from network", e);
				sendStringResponse(exchange, HttpURLConnection.HTTP_UNAUTHORIZED, e.toString());
				return null;
			}
		} finally {
			this.connector.getLockOidc().unlock(oidcAgentId + "");
		}
	}

	private PassphraseAgentImpl authenticateNamePassword(String username, String password, HttpExchange exchange) {
		if (username.equalsIgnoreCase(AnonymousAgent.LOGIN_NAME)) {
			return authenticateAnonymous(exchange);
		}
		try {
			String userId;
			// check if username is an agent id
			if (CryptoTools.isAgentID(username)) {
				userId = username;
			} else {
				userId = l2pNode.getAgentIdForLogin(username);
			}

			PassphraseAgentImpl userAgent = (PassphraseAgentImpl) l2pNode.getAgent(userId);
			userAgent.unlock(password);

			return userAgent;
		} catch (AgentNotFoundException e) {
			connector.logError("user " + username + " not found", e);
			sendUnauthorizedResponse(exchange, null, exchange.getRemoteAddress() + ": user " + username + " not found");
		} catch (AgentAccessDeniedException e) {
			connector.logError("passphrase invalid for user " + username, e);
			sendUnauthorizedResponse(exchange, null,
					exchange.getRemoteAddress() + ": passphrase invalid for user " + username);
		} catch (Exception e) {
			connector.logError("something went horribly wrong. Check your request for correctness.", e);
			sendUnauthorizedResponse(exchange, null, exchange.getRemoteAddress()
					+ ": something went horribly wrong. Check your request for correctness.");
		}
		return null;
	}

	private PassphraseAgentImpl authenticateAnonymous(HttpExchange exchange) {
		try {
			AnonymousAgentImpl anonymousAgent = AnonymousAgentImpl.getInstance();
			anonymousAgent.unlock(AnonymousAgent.PASSPHRASE);
			return anonymousAgent;
		} catch (CryptoException | AgentAccessDeniedException
				| AgentOperationFailedException e) {
			connector.logError("Could not authenticate as user anonymous", e);
			sendUnauthorizedResponse(exchange, null, "Could not authenticate as user anonymous");
		}
		return null;
	}

	private boolean invoke(PassphraseAgentImpl agent, HttpExchange exchange) {
		String requestPath = exchange.getRequestURI().getPath();

		// filter special pages
		if (requestPath.equalsIgnoreCase("/")) { // welcome page
			sendStringResponse(exchange, HttpURLConnection.HTTP_OK, "Welcome to las2peer!");
			return true;
		} else if (requestPath.equalsIgnoreCase("/favicon.ico")) { // favicon
			sendFaviconResponse(exchange);
			return true;
		}

		// split path
		ArrayList<String> pathSplit = new ArrayList<>(Arrays.asList(requestPath.split("/")));
		pathSplit.removeIf(item -> item == null || "".equals(item));

		// resolve service name
		String serviceName;
		int serviceAliasLength;
		try {
			AliasResolveResponse response = l2pNode.getServiceAliasManager().resolvePathToServiceName(requestPath);
			serviceName = response.getServiceName();
			serviceAliasLength = response.getNumMatchedParts();
		} catch (AliasNotFoundException e1) {
			connector.logError("Could not resolve " + requestPath + " to a service name.", e1);
			sendStringResponse(exchange, HttpURLConnection.HTTP_NOT_FOUND,
					"Could not resolve " + requestPath + " to a service name.");
			return false;
		}

		// get service version
		ServiceVersion serviceVersion;
		boolean versionSpecified = false;
		if (pathSplit.size() > serviceAliasLength && pathSplit.get(serviceAliasLength).startsWith("v")) {
			try {
				serviceVersion = new ServiceVersion(pathSplit.get(serviceAliasLength).substring(1));
				versionSpecified = true;
			} catch (IllegalArgumentException e) {
				serviceVersion = new ServiceVersion("*");
			}
		} else {
			serviceVersion = new ServiceVersion("*");
		}

		// required service
		ServiceNameVersion requiredService = new ServiceNameVersion(serviceName, serviceVersion);

		// construct base path
		String basePath = "/";
		for (int i = 0; i < serviceAliasLength; i++) {
			basePath += pathSplit.get(i) + "/";
		}
		if (versionSpecified) {
			basePath += pathSplit.get(serviceAliasLength) + "/";
		}

		// create mediator
		Mediator mediator;
		try {
			mediator = l2pNode.createMediatorForAgent(agent);
		} catch (AgentAlreadyRegisteredException | AgentLockedException e) {
			// should not occur, since agent is known and unlocked at this point
			sendUnexpectedErrorResponse(exchange, "Mediator could not be created", e);
			return false;
		}

		if (exchange.getRequestMethod().equalsIgnoreCase("get")
				&& exchange.getRequestURI().getPath().toString().equals(basePath + "swagger.json")) {
			return invokeSwagger(exchange, mediator, requiredService, basePath);
		} else {
			return invokeRestService(exchange, mediator, requiredService, basePath);
		}
	}

	private void sendFaviconResponse(HttpExchange exchange) {
		try {
			exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0);
			OutputStream os = exchange.getResponseBody();
			try {
				InputStream is = new FileInputStream("etc/favicon.ico");
				if (is != null) {
					os.write(SimpleTools.toByteArray(is));
				}
			} catch (FileNotFoundException e) {
				connector.logError(e.toString());
			}
			os.close();
		} catch (IOException e) {
			connector.logError(e.toString(), e);
		}
	}

	private boolean invokeRestService(HttpExchange exchange, Mediator mediator, ServiceNameVersion requiredService,
			String basePath) {

		// URIs
		URI exchangeUri = exchange.getRequestURI();
		UriBuilder exchangeUriBuilder = UriBuilder.fromUri(exchangeUri);
		if (!exchangeUri.getPath().endsWith("/")) { // make sure URI ends with "/"
			exchangeUriBuilder.path("/");
		}
		for (String param : INGORE_QUERY_PARAMS) { // remove auth params from uri
			exchangeUriBuilder.replaceQueryParam(param);
		}
		exchangeUri = exchangeUriBuilder.build();

		final URI baseUri = getBaseUri(exchange, basePath);
		final URI requestUri = getRequestUri(exchange, baseUri, exchangeUri);

		// content
		byte[] requestContent;
		try {
			requestContent = getRequestContent(exchange);
		} catch (IOException e1) {
			sendUnexpectedErrorResponse(exchange, "An error occurred: " + e1, e1);
			return false;
		}
		if (requestContent == null) {
			sendStringResponse(exchange, HttpURLConnection.HTTP_ENTITY_TOO_LARGE,
					"Given request body exceeds limit of " + connector.maxRequestBodySize + " bytes");
			return false;
		}

		// headers
		HashMap<String, ArrayList<String>> headers = new HashMap<>();
		for (Entry<String, List<String>> entry : exchange.getRequestHeaders().entrySet()) {
			// exclude some headers for security reasons
			if (!INGORE_HEADERS.contains(entry.getKey().toLowerCase())) {
				ArrayList<String> list = new ArrayList<>();
				list.addAll(entry.getValue());
				headers.put(entry.getKey(), list);
			}
		}

		// invoke
		Serializable[] params = new Serializable[] { baseUri, requestUri, exchange.getRequestMethod(), requestContent,
				headers };

		Serializable result = callServiceMethod(exchange, mediator, requiredService, "handle", params);
		if (result == null) {
			return false;
		}

		if (result instanceof RESTResponse) {
			sendRESTResponse(exchange, (RESTResponse) result);
			return true;
		} else {
			sendStringResponse(exchange, HttpURLConnection.HTTP_INTERNAL_ERROR, "Internal Server Error");
			return false;
		}
	}

	private URI getBaseUri(final HttpExchange exchange, final String decodedBasePath) {
		final String scheme = (exchange instanceof HttpsExchange) ? "https" : "http";

		final URI baseUri;
		try {
			final List<String> hostHeader = exchange.getRequestHeaders().get("Host");
			if (hostHeader != null) {
				baseUri = new URI(scheme + "://" + hostHeader.get(0) + decodedBasePath);
			} else {
				final InetSocketAddress addr = exchange.getLocalAddress();
				baseUri = new URI(scheme, null, addr.getHostName(), addr.getPort(), decodedBasePath, null, null);
			}
		} catch (final URISyntaxException ex) {
			throw new IllegalArgumentException(ex);
		}
		return baseUri;
	}

	private URI getRequestUri(final HttpExchange exchange, final URI baseUri, final URI exchangeUri) {
		try {
			return new URI(getServerAddress(baseUri) + exchangeUri);
		} catch (URISyntaxException ex) {
			throw new IllegalArgumentException(ex);
		}
	}

	private String getServerAddress(final URI baseUri) throws URISyntaxException {
		return new URI(baseUri.getScheme(), null, baseUri.getHost(), baseUri.getPort(), null, null, null).toString();
	}

	private byte[] getRequestContent(HttpExchange exchange) throws IOException {
		InputStream is = exchange.getRequestBody();
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int nRead;
		byte[] data = new byte[4096];
		boolean overflow = false;
		while ((nRead = is.read(data, 0, data.length)) != -1) {
			if (buffer.size() < connector.maxRequestBodySize - data.length) {
				// still space left in local buffer
				buffer.write(data, 0, nRead);
			} else {
				overflow = true;
				// no break allowed otherwise the client gets an exception (like connection closed)
				// so we have to read all given content
			}
		}
		if (overflow) {
			return null;
		}
		return buffer.toByteArray();
	}

	private boolean invokeSwagger(HttpExchange exchange, Mediator mediator, ServiceNameVersion requiredService,
			String basePath) {

		// get definitions
		Serializable result = callServiceMethod(exchange, mediator, requiredService, "getSwagger",
				new Serializable[] {});
		if (result == null) {
			return false;
		}

		if (!(result instanceof String)) {
			sendStringResponse(exchange, HttpURLConnection.HTTP_NOT_FOUND, "Swagger API declaration not available!");
			return false;
		}

		// deserialize Swagger
		Swagger swagger;
		try {
			swagger = Json.mapper().reader(Swagger.class).readValue((String) result);
		} catch (Exception e) {
			connector.logError("Swagger API declaration not available!", e);
			sendStringResponse(exchange, HttpURLConnection.HTTP_NOT_FOUND, "Swagger API declaration not available!");
			return false;
		}

		// modify Swagger
		swagger.setBasePath(basePath);

		// OpenID Connect integration
		if (connector.oidcProviderInfos != null && connector.defaultOIDCProvider != null
				&& !connector.defaultOIDCProvider.isEmpty()) {
			// add security definition for default provider
			JSONObject infos = connector.oidcProviderInfos.get(connector.defaultOIDCProvider);
			OAuth2Definition scheme = new OAuth2Definition();
			String authUrl = (String) ((JSONObject) infos.get("config")).get("authorization_endpoint");
			scheme.implicit(authUrl);
			scheme.addScope("openid", "Access Identity");
			scheme.addScope("email", "Access E-Mail-Address");
			scheme.addScope("profile", "Access Profile Data");

			swagger.addSecurityDefinition("defaultProvider", scheme);

			// add security requirements to operations
			List<String> scopes = new ArrayList<>();
			scopes.add("openid");
			scopes.add("email");
			scopes.add("profile");
			Map<String, Path> paths = swagger.getPaths();
			if (paths != null) {
				for (Path path : paths.values()) {
					for (Operation operation : path.getOperations()) {
						operation.addSecurity("defaultProvider", scopes);
					}
				}
			}
		}

		// serialize Swagger
		String json;
		try {
			json = Json.mapper().writeValueAsString(swagger);
		} catch (JsonProcessingException e) {
			sendUnexpectedErrorResponse(exchange, "Swagger documentation could not be serialized to JSON", e);
			return false;
		}

		sendStringResponse(exchange, HttpURLConnection.HTTP_OK, json);
		return true;
	}

	private Serializable callServiceMethod(HttpExchange exchange, Mediator mediator, ServiceNameVersion service,
			String method, Serializable[] params) {
		Serializable result = null;
		try {
			result = mediator.invoke(service.toString(), method, params, connector.onlyLocalServices());
			if (result == null) {
				sendUnexpectedErrorResponse(exchange, "Service method invocation returned null response", null);
			}
		} catch (AgentException e) {
			connector.logError("No service found matching " + service + ".", e);
			sendStringResponse(exchange, HttpURLConnection.HTTP_NOT_FOUND,
					"No service found matching " + service + ".");
		} catch (ServiceInvocationException e) {
			sendInvocationException(exchange, e);
		} catch (Exception e) {
			sendUnexpectedErrorResponse(exchange, "Service method invocation failed", e);
		}
		return result;
	}

	private void sendInvocationException(HttpExchange exchange, ServiceInvocationException e) {
		connector.logError("Exception while processing RMI: " + exchange.getRequestURI().getPath(), e);
		sendStringResponse(exchange, HttpURLConnection.HTTP_INTERNAL_ERROR, "Exception during RMI invocation!");
	}

	private void sendRESTResponse(HttpExchange exchange, RESTResponse result) {
		exchange.getResponseHeaders().putAll(result.getHeaders());
		try {
			sendResponseHeaders(exchange, result.getHttpCode(), getResponseLength(result.getBody().length));
			OutputStream os = exchange.getResponseBody();
			if (result.getBody().length > 0) {
				os.write(result.getBody());
			}
			os.close();
		} catch (IOException e) {
			connector.logError("Sending REST response (Code: " + result.getHttpCode() + ") failed!", e);
		}
	}

	private void sendUnauthorizedResponse(HttpExchange exchange, String answerMessage, String logMessage) {
		connector.logMessage(logMessage);
		exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"las2peer WebConnector\"");
		if (answerMessage != null) {
			sendStringResponse(exchange, HttpURLConnection.HTTP_UNAUTHORIZED, answerMessage);
		} else {
			try {
				sendResponseHeaders(exchange, HttpURLConnection.HTTP_UNAUTHORIZED, NO_RESPONSE_BODY);
				// otherwise the client waits till the timeout for an answer
				exchange.getResponseBody().close();
			} catch (IOException e) {
				connector.logError(e.toString(), e);
			}
		}
	}

	private void sendUnexpectedErrorResponse(HttpExchange exchange, String message, Throwable e) {
		connector.logError("Internal Server Error: " + message, e);
		sendStringResponse(exchange, HttpURLConnection.HTTP_INTERNAL_ERROR, message);
	}

	private void sendStringResponse(HttpExchange exchange, int responseCode, String response) {
		byte[] content = response.getBytes();
		exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "text/plain");
		try {
			sendResponseHeaders(exchange, responseCode, content.length);
			OutputStream os = exchange.getResponseBody();
			os.write(content);
			os.close();
		} catch (IOException e) {
			connector.logError(e.toString(), e);
		}
	}

	private void sendResponseHeaders(HttpExchange exchange, int responseCode, long contentLength) throws IOException {
		Headers responseHeaders = exchange.getResponseHeaders();

		// remove CORSheaders from service
		responseHeaders.remove("Access-Control-Allow-Origin");
		responseHeaders.remove("Access-Control-Max-Age");
		responseHeaders.remove("Access-Control-Allow-Headers");
		responseHeaders.remove("Access-Control-Allow-Methods");

		// add CORS
		if (connector.enableCrossOriginResourceSharing) {
			responseHeaders.add("Access-Control-Allow-Origin", connector.crossOriginResourceDomain);
			responseHeaders.add("Access-Control-Max-Age", String.valueOf(connector.crossOriginResourceMaxAge));
			// just reply all requested headers
			String requestedHeaders = exchange.getRequestHeaders().getFirst("Access-Control-Request-Headers");
			if (requestedHeaders != null) {
				if (!requestedHeaders.toLowerCase().contains("authorization")) {
					if (!requestedHeaders.trim().equals("")) {
						requestedHeaders += ", ";
					}
					requestedHeaders += "Authorization";
				}
				responseHeaders.add("Access-Control-Allow-Headers", requestedHeaders);
			}
			responseHeaders.add("Access-Control-Allow-Methods", "POST, GET, PUT, DELETE, OPTIONS");
		}
		exchange.sendResponseHeaders(responseCode, contentLength);
	}

	private long getResponseLength(final long contentLength) {
		if (contentLength == 0) {
			return -1;
		}
		if (contentLength < 0) {
			return 0;
		}
		return contentLength;
	}

}
