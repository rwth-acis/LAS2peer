package i5.las2peer.connectors.webConnector;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import i5.las2peer.api.p2p.ServiceNameVersion;
import i5.las2peer.connectors.webConnector.client.ClientResponse;
import i5.las2peer.connectors.webConnector.client.MiniClient;
import i5.las2peer.connectors.webConnector.services.TestMissingPathService;
import i5.las2peer.connectors.webConnector.services.TestVersionService;
import i5.las2peer.p2p.LocalNode;
import i5.las2peer.p2p.LocalNodeManager;
import i5.las2peer.security.GroupAgentImpl;
import i5.las2peer.security.UserAgentImpl;
import i5.las2peer.testing.MockAgentFactory;

public class WebConnectorServiceInfoTest {

	private LocalNode node;
	private WebConnector connector;
	private ByteArrayOutputStream logStream;

	private UserAgentImpl testAgent;
	private static final String testPass = "adamspass";

	private static final String testServiceClass1 = TestVersionService.class.getName() + "@1";
	private static final String testServiceClass2 = TestVersionService.class.getName() + "@2.0";
	private static final String testServiceClass3 = TestVersionService.class.getName() + "@2.1";
	private static final String testServiceClass4 = TestVersionService.class.getName() + "@2.2.0-1";
	private static final String testServiceClass5 = TestVersionService.class.getName() + "@2.2.0-2";
	private static final String testServiceClass6 = TestMissingPathService.class.getName() + "@1";

	@Before
	public void startServer() throws Exception {
		// init agents
		UserAgentImpl eve = MockAgentFactory.getEve();
		eve.unlock("evespass");
		UserAgentImpl adam = MockAgentFactory.getAdam();
		adam.unlock("adamspass");
		UserAgentImpl abel = MockAgentFactory.getAbel();
		abel.unlock("abelspass");
		GroupAgentImpl group1 = MockAgentFactory.getGroup1();
		group1.unlock(adam);

		// start Node
		node = new LocalNodeManager().newNode();
		node.storeAgent(eve);
		node.storeAgent(adam);
		node.storeAgent(abel);
		node.storeAgent(group1);
		node.launch();

		node.startService(ServiceNameVersion.fromString(testServiceClass1), "a pass");
		node.startService(ServiceNameVersion.fromString(testServiceClass2), "a pass");
		node.startService(ServiceNameVersion.fromString(testServiceClass3), "a pass");
		node.startService(ServiceNameVersion.fromString(testServiceClass4), "a pass");
		node.startService(ServiceNameVersion.fromString(testServiceClass5), "a pass");
		node.startService(ServiceNameVersion.fromString(testServiceClass6), "a pass");

		// start connector
		logStream = new ByteArrayOutputStream();
		connector = new WebConnector(true, 0, false, 0); // Port: 0 => the system picks a port
		connector.setLogStream(new PrintStream(logStream));
		connector.start(node);

		testAgent = adam;
		testAgent.unlock("adamspass");
		node.storeAgent(testAgent);
	}

	@After
	public void shutDownServer() throws Exception {
		connector.stop();
		node.shutDown();

		connector = null;
		node = null;

		System.out.println("Connector-Log:");
		System.out.println("--------------");
		System.out.println(logStream.toString());
	}

	@Test
	public void testVersions() {
		MiniClient c = new MiniClient();
		c.setConnectorEndpoint(connector.getHttpEndpoint());

		// without version
		try {
			c.setLogin(testAgent.getIdentifier(), testPass);
			ClientResponse result = c.sendRequest("GET", "version/test", "");
			Assert.assertEquals(HttpURLConnection.HTTP_OK, result.getHttpCode());
			assertTrue(result.getResponse().trim().startsWith("2"));
		} catch (Exception e) {
			fail(e.getMessage());
		}

		// unambiguous version
		try {
			c.setLogin(testAgent.getIdentifier(), testPass);
			ClientResponse result = c.sendRequest("GET", "version/v1/test", "");
			Assert.assertEquals(HttpURLConnection.HTTP_OK, result.getHttpCode());
			assertTrue(result.getResponse().trim().equals("1"));
		} catch (Exception e) {
			fail(e.getMessage());
		}

		node.getNodeServiceCache().clear();

		// ambiguous version
		try {
			c.setLogin(testAgent.getIdentifier(), testPass);
			ClientResponse result = c.sendRequest("GET", "version/v2/test", "");
			Assert.assertEquals(HttpURLConnection.HTTP_OK, result.getHttpCode());
			assertTrue(result.getResponse().trim().startsWith("2.2"));
		} catch (Exception e) {
			fail(e.getMessage());
		}

		// exact version
		try {
			c.setLogin(testAgent.getIdentifier(), testPass);
			ClientResponse result = c.sendRequest("GET", "version/v2.2.0-1/test", "");
			Assert.assertEquals(HttpURLConnection.HTTP_OK, result.getHttpCode());
			assertTrue(result.getResponse().trim().equals("2.2.0-1"));
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

}
