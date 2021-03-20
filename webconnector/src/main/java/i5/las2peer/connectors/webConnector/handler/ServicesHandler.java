package i5.las2peer.connectors.webConnector.handler;

import i5.las2peer.connectors.webConnector.util.AuthenticationManager;

import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.jar.JarInputStream;
import java.util.Base64;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.CookieParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;

import org.glassfish.jersey.media.multipart.FormDataParam;
import org.web3j.crypto.Credentials;

import i5.las2peer.api.execution.ServiceNotFoundException;
import i5.las2peer.api.p2p.ServiceNameVersion;
import i5.las2peer.api.persistency.EnvelopeAlreadyExistsException;
import i5.las2peer.api.persistency.EnvelopeException;
import i5.las2peer.api.persistency.EnvelopeNotFoundException;
import i5.las2peer.api.security.AgentException;
import i5.las2peer.api.security.AgentNotFoundException;
import i5.las2peer.api.security.UserAgent;
import i5.las2peer.classLoaders.ClassManager;
import i5.las2peer.classLoaders.libraries.SharedStorageRepository;
import i5.las2peer.connectors.webConnector.WebConnector;
import i5.las2peer.connectors.webConnector.util.AgentSession;
import i5.las2peer.connectors.webConnector.util.L2P_JSONUtil;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.p2p.AgentNotRegisteredException;
import i5.las2peer.p2p.EthereumNode;
import i5.las2peer.p2p.Node;
import i5.las2peer.p2p.NodeException;
import i5.las2peer.p2p.NodeInformation;
import i5.las2peer.p2p.NodeNotFoundException;
import i5.las2peer.p2p.PastryNodeImpl;
import i5.las2peer.persistency.EnvelopeVersion;
import i5.las2peer.registry.CredentialUtils;
import i5.las2peer.registry.ReadOnlyRegistryClient;
import i5.las2peer.registry.data.ServiceReleaseData;
import i5.las2peer.security.AgentImpl;
import i5.las2peer.security.EthereumAgent;
import i5.las2peer.security.GroupAgentImpl;
import i5.las2peer.security.GroupEthereumAgent;
import i5.las2peer.security.UserAgentImpl;
import i5.las2peer.tools.CryptoException;
import i5.las2peer.tools.PackageUploader;
import i5.las2peer.tools.PackageUploader.ServiceVersionList;
import i5.las2peer.tools.ServicePackageException;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import rice.pastry.NodeHandle;
import i5.las2peer.api.security.Agent;
import i5.las2peer.api.security.AgentAccessDeniedException;

@Path(ServicesHandler.RESOURCE_PATH)
public class ServicesHandler {

	public static final String RESOURCE_PATH = DefaultHandler.ROOT_RESOURCE_PATH + "/services";

	private final WebConnector connector;
	private final Node node;
	private final PastryNodeImpl pastryNode;
	private final EthereumNode ethereumNode;
	private final ReadOnlyRegistryClient registry;
	private AuthenticationManager authenticationManager;

	private final L2pLogger logger = L2pLogger.getInstance(ServicesHandler.class);

	private ConcurrentHashMap<String, NodeInformation> nodeInfoCache = new ConcurrentHashMap<>();

	public ServicesHandler(WebConnector connector) {
		this.connector = connector;
		this.node = connector.getL2pNode();
		pastryNode = (node instanceof PastryNodeImpl) ? (PastryNodeImpl) node : null;
		ethereumNode = (node instanceof EthereumNode) ? (EthereumNode) node : null;
		registry = (node instanceof EthereumNode) ? ethereumNode.getRegistryClient() : null;
		authenticationManager = new AuthenticationManager(connector);

	}

	@GET
	@Path("/search")
	@Produces(MediaType.APPLICATION_JSON)
	@Deprecated // no longer needed, and insecure, see #getNetworkServices below -- TODO: just
				// remove this?
	public Response handleSearchService(@HeaderParam("Host") String hostHeader,
			@QueryParam("searchname") String searchName) {
		JSONObject result = new JSONObject();
		try {
			JSONArray instances = new JSONArray();
			if (searchName == null || searchName.isEmpty()) {
				// iterate local services
				List<String> serviceNames = node.getNodeServiceCache().getLocalServiceNames();
				for (String serviceName : serviceNames) {
					// add service versions from network
					instances.addAll(getNetworkServices(node, hostHeader, serviceName));
				}
			} else {
				// search for service version in network
				instances.addAll(getNetworkServices(node, hostHeader, searchName));
			}
			result.put("instances", instances);
		} catch (EnvelopeNotFoundException | IllegalArgumentException e) {
			result.put("msg", "'" + searchName + "' not found in network");
		} catch (Exception e) {
			result.put("msg", e.toString());
		}
		return Response.ok(result.toJSONString(), MediaType.APPLICATION_JSON).build();
	}

	@Deprecated // this bypasses the Repository and Registry, replace
	private JSONArray getNetworkServices(Node node, String hostHeader, String searchName) throws Exception {
		JSONArray result = new JSONArray();
		String libName = ClassManager.getPackageName(searchName);
		String libId = SharedStorageRepository.getLibraryVersionsEnvelopeIdentifier(libName);
		EnvelopeVersion networkVersions = node.fetchEnvelope(libId);
		Serializable content = networkVersions.getContent();
		if (content instanceof ServiceVersionList) {
			ServiceVersionList serviceversions = (ServiceVersionList) content;
			for (String version : serviceversions) {
				JSONObject jsonObject = new JSONObject();
				jsonObject.put("name", searchName);
				jsonObject.put("version", version);
				result.add(jsonObject);
			}
		} else {
			throw new ServicePackageException("Invalid version envelope expected " + List.class.getCanonicalName()
					+ " but envelope contains " + content.getClass().getCanonicalName());
		}
		return result;
	}

	@POST
	@Path("/upload")
	@Produces(MediaType.APPLICATION_JSON)
	public Response handleServicePackageUpload(@CookieParam(WebConnector.COOKIE_SESSIONID_KEY) String sessionId,
			@FormDataParam("jarfile") InputStream jarfile,
			@DefaultValue("") @FormDataParam("supplement") String supplement) throws Exception {
		AgentSession session = connector.getSessionById(sessionId);
		if (session == null) {
			throw new BadRequestException("You have to be logged in to upload");
		} else if (jarfile == null) {
			throw new BadRequestException("No jar file provided");
		} else if (pastryNode == null) {
			throw new ServerErrorException(
					"Service upload only available for " + PastryNodeImpl.class.getCanonicalName() + " Nodes",
					Status.INTERNAL_SERVER_ERROR);
		}

		JarInputStream jarStream = new JarInputStream(jarfile);

		try {
			PackageUploader.uploadServicePackage(pastryNode, jarStream, session.getAgent(), supplement);
			JSONObject json = new JSONObject();
			json.put("code", Status.OK.getStatusCode());
			json.put("text", Status.OK.getStatusCode() + " - Service package upload successful");
			json.put("msg", "Service package upload successful");
			return Response.ok(json.toJSONString(), MediaType.APPLICATION_JSON).build();
		} catch (EnvelopeAlreadyExistsException e) {
			throw new BadRequestException("Version is already known in the network. To update increase version number",
					e);
		} catch (ServicePackageException e) {
			e.printStackTrace();
			throw new BadRequestException("Service package upload failed", e);
		}
	}

	@POST
	@Path("/registerClusterService")
	@Produces(MediaType.APPLICATION_JSON)
	public Response testCAE(String body, @Context HttpHeaders httpHeaders) throws Exception {
		JSONObject payload = parseJson(body);
		String groupIdOrName = payload.getAsString("groupId");
		System.out.println(body);
		if (pastryNode == null) {
			throw new ServerErrorException(
					"Service upload only available for " + PastryNodeImpl.class.getCanonicalName() + " Nodes",
					Status.INTERNAL_SERVER_ERROR);
		}
		try {
			AgentImpl agent;
			System.out.println("Trying to get agent by group name in laod group call");
			try {
				agent = getGroupByName(groupIdOrName);
			} catch (Exception e) {
				System.out.println("Exception " + e + "occured");
				System.out.println("Couldn't find agent based on group name, trying group id...");
				try {
					agent = node.getAgent(groupIdOrName);

				} catch (AgentNotFoundException f) {
					return Response.status(Status.BAD_REQUEST).entity("Agent not found").build();
				}
			}
			AgentImpl userAgent = authenticationManager.authenticateAgent(httpHeaders.getRequestHeaders(),
					"access-token");
			GroupAgentImpl groupAgent = (GroupAgentImpl) agent;
			try {
				groupAgent.unlock(userAgent);
			} catch (AgentAccessDeniedException e) {
				return Response.status(Status.BAD_REQUEST).entity("You must be a member of this group").build();
			}
			System.out.println(groupAgent.getGroupName());
			PackageUploader.registerClusterService(pastryNode, payload.getAsString("name"),
					payload.getAsString("version"), groupAgent, body);
			JSONObject json = new JSONObject();
			json.put("code", Status.OK.getStatusCode());
			json.put("text", Status.OK.getStatusCode() + " - Registering deployment successful");
			json.put("msg", "Registering deployment successful");
			return Response.ok(json.toJSONString(), MediaType.APPLICATION_JSON).build();
		} catch (EnvelopeAlreadyExistsException e) {
			throw new BadRequestException("Version is already known in the network. To update increase version number",
					e);
		} catch (ServicePackageException e) {
			e.printStackTrace();
			throw new BadRequestException("Service package upload failed", e);
		} catch (Exception e) {
			throw new BadRequestException("Login required to deploy", e);
		}
	}

	private AgentImpl getGroupByName(String groupName) throws Exception {
		try {
			String agentId = node.getAgentIdForGroupName(groupName);
			System.out.println("Agent id is " + agentId);
			if (node instanceof EthereumNode) {
				System.out.println(" ok is eth nnnnnnode");
				EthereumNode ethNode = (EthereumNode) node;
				AgentImpl agent = ethNode.getAgent(agentId);
				if(agent instanceof GroupAgentImpl){
					System.out.println(" group ag impl");
				}
				if(agent instanceof GroupEthereumAgent){
					System.out.println(" group ag eth impl");
				}
				if(agent instanceof EthereumAgent){
					System.out.println(" eth ag impl");
				}
				if(agent instanceof UserAgentImpl){
					System.out.println("user ag impl ag impl");
				}
				return agent;
			} else {
				System.out.println("oooh noo noo et noode");
				return node.getAgent(agentId);
			}
		} catch (AgentNotFoundException e) {
			throw new BadRequestException("Agent not found");
		}
	}

	@POST
	@Path("/announceDeployment")
	@Produces(MediaType.APPLICATION_JSON)
	public Response deployServiceTEST(String body) throws Exception {
		JSONObject payload = parseJson(body);
		System.out.println(body);
		if (pastryNode == null) {
			throw new ServerErrorException(
					"Service upload only available for " + PastryNodeImpl.class.getCanonicalName() + " Nodes",
					Status.INTERNAL_SERVER_ERROR);
		}
		try {
			PackageUploader.announceClusterServiceDeployment(pastryNode, payload.getAsString("name"),
					payload.getAsString("clusterName"), payload.getAsString("version"), body);

			JSONObject json = new JSONObject();
			json.put("code", Status.OK.getStatusCode());
			json.put("text", Status.OK.getStatusCode() + " - Cluster service announcement successful");
			json.put("msg", "Cluster service announcement successful");
			return Response.ok(json.toJSONString(), MediaType.APPLICATION_JSON).build();
		} catch (EnvelopeAlreadyExistsException e) {
			throw new BadRequestException("Version is already known in the network. To update increase version number",
					e);
		} catch (ServicePackageException e) {
			e.printStackTrace();
			throw new BadRequestException("Service package upload failed", e);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BadRequestException("Cluster service announcement failed", e);
		}
	}

	@POST
	@Path("/announceUndeployment")
	@Produces(MediaType.APPLICATION_JSON)
	public Response undeployClusterTESt(String body) throws Exception {
		JSONObject payload = parseJson(body);
		System.out.println(body);
		if (pastryNode == null) {
			throw new ServerErrorException(
					"Service upload only available for " + PastryNodeImpl.class.getCanonicalName() + " Nodes",
					Status.INTERNAL_SERVER_ERROR);
		}

		try {
			PackageUploader.announceUndeploymentOfClusterService(pastryNode, payload.getAsString("name"),
					payload.getAsString("clusterName"), payload.getAsString("version"));
			JSONObject json = new JSONObject();
			json.put("code", Status.OK.getStatusCode());
			json.put("text", Status.OK.getStatusCode() + " - Service package upload successful");
			json.put("msg", "Service package upload successful");
			return Response.ok(json.toJSONString(), MediaType.APPLICATION_JSON).build();
		} catch (EnvelopeAlreadyExistsException e) {
			throw new BadRequestException("Version is already known in the network. To update increase version number",
					e);
		} catch (ServicePackageException e) {
			e.printStackTrace();
			throw new BadRequestException("Cluster service  undeployment announcement failed", e);
		}
	}

	@POST
	@Path("/start")
	public Response handleStartService(@QueryParam("serviceName") String serviceName,
			@QueryParam("version") String version) throws CryptoException, AgentException {
		// TODO: uhhh, about that password -- is that relevant??
		pastryNode.startService(ServiceNameVersion.fromString(serviceName + "@" + version), "foofoo");
		return Response.ok().build();
	}

	@POST
	@Path("/stop")
	public Response handleStopService(@QueryParam("serviceName") String serviceName,
			@QueryParam("version") String version)
			throws NodeException, AgentNotRegisteredException, ServiceNotFoundException {
		pastryNode.stopService(ServiceNameVersion.fromString(serviceName + "@" + version));
		return Response.ok().build();
	}

	@GET
	@Path("/node-id")
	@Produces(MediaType.APPLICATION_JSON)
	// sort of a duplicate of the status thing, but actually not, because this is
	// the full Pastry node ID
	public JSONObject getRawNodeIdAsJson() {
		return new JSONObject().appendField("id", pastryNode.getPastryNode().getId().toStringFull());
	}

	@GET
	@Path("/node-id")
	@Produces(MediaType.TEXT_PLAIN)
	// sort of a duplicate of the status thing, but actually not, because this is
	// the full Pastry node ID
	public String getRawNodeId() {
		return getPastryNodeId();
	}

	private String getPastryNodeId() {
		return pastryNode.getPastryNode().getId().toStringFull();
	}

	private NodeInformation queryNodeInfoWithCache(String nodeID) {
		logger.fine("[NodeInfo] searching for node #" + nodeID);

		// do we know this node?
		if (nodeInfoCache.containsKey(nodeID)) {
			NodeInformation foundVal = nodeInfoCache.get(nodeID);
			logger.fine("[NodeInfo] ! found info for node #" + nodeID + " in cache");
			return foundVal;
		}

		// are we looking for this (local) node?
		String localNodeID = getPastryNodeId();
		if (nodeID.equals(localNodeID)) {
			NodeInformation localNodeInfo = new NodeInformation();
			try {
				localNodeInfo = ethereumNode.getNodeInformation();
				nodeInfoCache.put(localNodeID, localNodeInfo);
			} catch (CryptoException e) {
				logger.severe("trying to local access node info");
				e.printStackTrace();
			}
			logger.fine("[NodeInfo] ! node #" + nodeID + " is local node! ");
			return localNodeInfo;
		}

		Collection<NodeHandle> knownNodes = ethereumNode.getPastryNode().getLeafSet().getUniqueSet();

		logger.info(
				"[NodeInfo] nodeID not found in cache, query network for info on " + knownNodes.size() + " nodes...");

		for (NodeHandle nh : knownNodes) {
			String remoteNodeID = nh.getId().toStringFull();
			NodeInformation remoteNodeInfo = null;
			try {
				remoteNodeInfo = node.getNodeInformation(nh);
			} catch (NodeNotFoundException e) {
				// logger.severe("trying to access node " + remoteNodeHandle.getNodeId() + " | "
				// + remoteNodeHandle.getId());
				// ignore malformed nodeinfo / missing node
				continue;
			} finally {
				logger.fine(remoteNodeInfo.toString());
				nodeInfoCache.put(remoteNodeID, remoteNodeInfo);
			}

			if (remoteNodeInfo != null && remoteNodeID.equals(nodeID)) {
				logger.fine("[NodeInfo] ! found remote node " + nodeID);
				return remoteNodeInfo;
			}
		}

		logger.severe("[NodeInfo] NOT FOUND! ");
		return new NodeInformation();
	}

	@GET
	@Path("/services")
	@Produces(MediaType.APPLICATION_JSON)
	public String getStructuredServiceData() {
		if (registry == null)
			throw new NotFoundException("Node does not use registry.");

		JSONArray services = new JSONArray();

		registry.getServiceNames().forEach(name -> {
			Map<String, JSONObject> releasesByVersion = new HashMap<>();

			String serviceAuthor = registry.getServiceAuthors().get(name);

			registry.getServiceReleases().getOrDefault(name, Collections.emptyList()).forEach(release -> {
				// this is a bit ugly, but there's no way to handle errors in a lambda
				byte[] rawSupplement = new byte[0];
				try {
					rawSupplement = ethereumNode.fetchHashedContent(release.getSupplementHash());
				} catch (EnvelopeException e) {
					e.printStackTrace();
				}
				JSONObject supplement = parseJson(new String(rawSupplement, StandardCharsets.UTF_8));

				JSONArray deploymentsJson = new JSONArray();
				registry.getDeployments(name, release.getVersion()).forEach(deployment -> {
					if (deployment.getNodeId() != null) {
						String deploymentNodeId = deployment.getNodeId();
						NodeInformation hostedOn = queryNodeInfoWithCache(deploymentNodeId);
						deploymentsJson.add(new JSONObject().appendField("className", deployment.getServiceClassName())
								.appendField("nodeId", deploymentNodeId)
								.appendField("nodeInfo", L2P_JSONUtil.nodeInformationToJSON(hostedOn))
								.appendField("hosterReputation",
										ethereumNode.getAgentReputation(hostedOn.getAdminName(),
												hostedOn.getAdminEmail()))
								.appendField("announcementEpochSeconds", deployment.getTimestamp().getEpochSecond()));
					} else {
						byte[] rawSupplementDeployment = new byte[0];
						try {
							rawSupplementDeployment = ethereumNode.fetchHashedContent(deployment.getSupplementHash());
						} catch (EnvelopeException e) {
							e.printStackTrace();
						}
						JSONObject supplementDeployment = parseJson(
								new String(rawSupplementDeployment, StandardCharsets.UTF_8));
						System.out.println(supplementDeployment.toString());
						supplementDeployment.put("time", deployment.getTime());
						deploymentsJson.add(supplementDeployment);
					}
				});

				releasesByVersion.put(release.getVersion(),
						new JSONObject().appendField("publicationEpochSeconds", release.getTimestamp().getEpochSecond())
								.appendField("instances", deploymentsJson).appendField("supplement", supplement));
			});

			services.appendElement(new JSONObject().appendField("name", name).appendField("authorName", serviceAuthor)
					.appendField("authorReputation", ethereumNode.getAgentReputation(serviceAuthor, null))
					.appendField("releases", new JSONObject(releasesByVersion)));
		});

		return services.toJSONString();
	}

	// TODO: decide which of the below are worth keeping

	@GET
	@Path("/names")
	@Produces(MediaType.APPLICATION_JSON)
	public String getRegisteredServices() {
		if (registry == null)
			throw new NotFoundException("Node does not use registry.");
		JSONArray serviceNameList = new JSONArray();
		serviceNameList.addAll(registry.getServiceNames());
		return serviceNameList.toJSONString();
	}

	@GET
	@Path("/authors")
	@Produces(MediaType.APPLICATION_JSON)
	@Deprecated
	public String getServiceAuthors() {
		if (registry == null)
			throw new NotFoundException("Node does not use registry.");
		JSONObject jsonObject = new JSONObject();
		jsonObject.putAll(registry.getServiceAuthors());
		return jsonObject.toJSONString();
	}

	@GET
	@Path("/releases")
	@Produces(MediaType.APPLICATION_JSON)
	public String getServiceReleases() {
		if (registry == null)
			throw new NotFoundException("Node does not use registry.");
		JSONObject jsonObject = new JSONObject();
		for (ConcurrentMap.Entry<String, List<ServiceReleaseData>> service : registry.getServiceReleases().entrySet()) {
			JSONArray releaseList = new JSONArray();
			for (ServiceReleaseData release : service.getValue()) {
				JSONObject entry = new JSONObject();
				entry.put("name", release.getServiceName());
				entry.put("version", release.getVersion());
				releaseList.add(entry);
			}
			jsonObject.put(service.getKey(), releaseList);
		}
		return jsonObject.toJSONString();
	}

	@GET
	@Path("/deployments")
	@Produces(MediaType.APPLICATION_JSON)
	public String getServiceDeployments() {
		if (registry == null)
			throw new NotFoundException("Node does not use registry.");
		JSONObject jsonObject = new JSONObject();
		registry.getServiceNames().forEach(serviceName -> {
			JSONArray deploymentList = new JSONArray();
			registry.getDeployments(serviceName).forEach(deployment -> {
				if (deployment.getServiceClassName() != null) {
					JSONObject entry = new JSONObject();
					entry.put("packageName", deployment.getServicePackageName());
					entry.put("className", deployment.getServiceClassName());
					entry.put("version", deployment.getVersion());
					entry.put("time", deployment.getTime());
					entry.put("nodeId", deployment.getNodeId());
					deploymentList.add(entry);
				} else {
					byte[] rawSupplement = new byte[0];
					try {
						rawSupplement = ethereumNode.fetchHashedContent(deployment.getSupplementHash());
					} catch (EnvelopeException e) {
						e.printStackTrace();
					}
					JSONObject supplement = parseJson(new String(rawSupplement, StandardCharsets.UTF_8));
					System.out.println(supplement.toString());
					supplement.put("time", deployment.getTime());
					deploymentList.add(supplement);
				}

			});
			jsonObject.put(serviceName, deploymentList);
		});
		return jsonObject.toJSONString();
	}

	@GET
	@Path("/registry/tags")
	@Produces(MediaType.APPLICATION_JSON)
	public String getTags() {
		if (registry == null)
			throw new NotFoundException("Node does not use registry.");
		JSONObject jsonObject = new JSONObject();
		jsonObject.putAll(registry.getTags());
		return jsonObject.toJSONString();
	}

	@GET
	@Path("/registry/mnemonic")
	@Produces(MediaType.TEXT_PLAIN)
	public String generateMnemonic() {
		return CredentialUtils.createMnemonic();
	}

	@POST
	@Path("/registry/mnemonic")
	@Produces(MediaType.APPLICATION_JSON)
	public String showKeysForMnemonic(String requestBody) {
		JSONObject payload = parseJson(requestBody);
		String mnemonic = payload.getAsString("mnemonic");
		String password = payload.getAsString("password");

		Credentials credentials = CredentialUtils.fromMnemonic(mnemonic, password);

		return new JSONObject().appendField("mnemonic", mnemonic).appendField("password", password)
				.appendField("publicKey", "0x" + credentials.getEcKeyPair().getPublicKey().toString(16))
				.appendField("privateKey", "0x" + credentials.getEcKeyPair().getPrivateKey().toString(16))
				.appendField("address", credentials.getAddress()).toJSONString();
	}

	// only handles objects (not JSON arrays)
	private JSONObject parseJson(String s) {
		if (s.isEmpty()) {
			return new JSONObject();
		}
		try {
			return (JSONObject) new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE).parse(s);
		} catch (ParseException e) {
			throw new BadRequestException("Could not parse JSON");
		}
	}
}
