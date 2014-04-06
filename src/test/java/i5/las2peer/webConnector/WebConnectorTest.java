package i5.las2peer.webConnector;



import i5.las2peer.restMapper.MediaType;
import i5.las2peer.restMapper.data.Pair;
import i5.las2peer.webConnector.client.ClientResponse;

import i5.las2peer.webConnector.client.MiniClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

import i5.las2peer.p2p.LocalNode;
import i5.las2peer.security.ServiceAgent;
import i5.las2peer.security.UserAgent;
import i5.las2peer.testing.MockAgentFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class WebConnectorTest {

	private static final String HTTP_ADDRESS = "http://127.0.0.1";
	private static final int HTTP_PORT = WebConnector.DEFAULT_HTTP_PORT;
	
	private static LocalNode node;
	private static WebConnector connector;
	private static ByteArrayOutputStream logStream;
	
	private static UserAgent testAgent;
	private static final String testPass = "adamspass";
	
	private static final String testServiceClass = "i5.las2peer.webConnector.TestService";
    private static final String testServiceClass2 = "i5.las2peer.webConnector.TestService2";
    private static final String testServiceClass3 = "i5.las2peer.webConnector.TestService3";
    private static final String testServiceClass4 = "i5.las2peer.webConnector.TestService4";

	
	@BeforeClass
	public static void startServer () throws Exception {
		// start Node
		node = LocalNode.newNode();
		node.storeAgent(MockAgentFactory.getEve());
		node.storeAgent(MockAgentFactory.getAdam());
		node.storeAgent(MockAgentFactory.getAbel());
		node.storeAgent( MockAgentFactory.getGroup1());
		node.launch();
		
		ServiceAgent testService = ServiceAgent.generateNewAgent(testServiceClass, "a pass");
		ServiceAgent testService2 = ServiceAgent.generateNewAgent(testServiceClass2, "a pass");
        ServiceAgent testService3 = ServiceAgent.generateNewAgent(testServiceClass3, "a pass");
        ServiceAgent testService4 = ServiceAgent.generateNewAgent(testServiceClass4, "a pass");
		testService.unlockPrivateKey("a pass");
		testService2.unlockPrivateKey("a pass");
        testService3.unlockPrivateKey("a pass");
        testService4.unlockPrivateKey("a pass");
		
		node.registerReceiver(testService);
		node.registerReceiver(testService2);
        node.registerReceiver(testService3);
        node.registerReceiver(testService4);
		
		// start connector
		
		logStream = new ByteArrayOutputStream ();
		
		//String xml=RESTMapper.mergeXMLs(new String[]{RESTMapper.getMethodsAsXML(TestService.class),RESTMapper.getMethodsAsXML(TestService2.class)});
		//System.out.println(xml);
        /*System.out.println(RESTMapper.getMethodsAsXML(TestService.class));
        System.out.println(RESTMapper.getMethodsAsXML(TestService2.class));*/

		connector = new WebConnector(true,HTTP_PORT,false,1000, "./XMLCompatibility");
		connector.setSocketTimeout(10000);
		connector.setLogStream(new PrintStream ( logStream));
		connector.start ( node );
        Thread.sleep(1000);
		// eve is the anonymous agent!
		testAgent = MockAgentFactory.getAdam();
        //avoid timing errors: wait for the repository manager to get all services, before invoking them

	}

	@AfterClass
	public static void shutDownServer () throws Exception {
		//connector.interrupt();
		
		connector.stop();
		node.shutDown();

        connector = null;
        node = null;

        LocalNode.reset();
		
		System.out.println("Connector-Log:");
		System.out.println("--------------");
		
		System.out.println(logStream.toString());
        //System.out.println(connector.sslKeystore);


    }
	
	@Test
	public void testNotMethodService() {
		
		
		connector.updateServiceList();
		MiniClient c = new MiniClient();
		c.setAddressPort(HTTP_ADDRESS, HTTP_PORT);		
		
		try
		{
			c.setLogin(Long.toString(testAgent.getId()), testPass);
			
			ClientResponse response = c.sendRequest("GET", "asdag", "");
            assertEquals(404,response.getHttpCode());

		}
		catch(Exception e)
		{
			fail ("Not existing service caused wrong exception");
		}
		
	}
	
	/*@Test
	public void testMapping() {
		TestService service = new TestService();
		String output=service.getMapping();
		System.out.println(output);
	}*/

	@Test
	public void testLogin() {


        connector.updateServiceList();
		MiniClient c = new MiniClient();
		c.setAddressPort(HTTP_ADDRESS, HTTP_PORT);
		
		//correct, id based
		try
		{
			c.setLogin(Long.toString(testAgent.getId()), testPass);
			ClientResponse result=c.sendRequest("get", "", "");
			assertEquals("OK",result.getResponse().trim());
		}
		catch(Exception e)
		{
			e.printStackTrace();
			fail ( "Exception: " + e );
		}	
		
		//correct, name based
		try
		{
			c.setLogin("adam", testPass);

            ClientResponse result=c.sendRequest("GET", "", "");
			assertEquals("OK",result.getResponse().trim());
		}
		catch(Exception e)
		{
			e.printStackTrace();
			fail ( "Exception: " + e );
		}	
		
		//invalid password
		try
		{
			c.setLogin(Long.toString(testAgent.getId()), "aaaaaaaaaaaaa");
			
			ClientResponse result= c.sendRequest("GET", "", "");
            assertEquals(401, result.getHttpCode());
		}
		catch(Exception e)
		{
			e.printStackTrace();
			fail ( "Exception: " + e );
		}	
		//invalid user
		try
		{
			c.setLogin(Long.toString(65464), "aaaaaaaaaaaaa");

            ClientResponse result=c.sendRequest("GET", "", "");
            assertEquals(401, result.getHttpCode());
		}
		catch(Exception e)
		{
			e.printStackTrace();
			fail ( "Exception: " + e );
		}	
	}
	
	@Test
    @SuppressWarnings("unchecked")
	public void testCalls()
	{
        connector.updateServiceList();
        //avoid timing errors: wait for the repository manager to get all services, before invoking them
        try
        {
            System.out.println("waiting..");
            Thread.sleep(15000);
        }
        catch(InterruptedException e)
        {
            e.printStackTrace();
        }
		MiniClient c = new MiniClient();
		c.setAddressPort(HTTP_ADDRESS, HTTP_PORT);
		//call all methods of the testService
		
		try
		{
			c.setLogin(Long.toString(testAgent.getId()), testPass);

            ClientResponse result=c.sendRequest("PUT", "add/5/6", "");
			assertEquals("11",result.getResponse().trim());
		}
		catch(Exception e)
		{
			e.printStackTrace();
			fail ( "Exception: " + e );
		}	
		
		
		try
		{
			c.setLogin(Long.toString(testAgent.getId()), testPass);

            ClientResponse result=c.sendRequest("POST", "sub/5/6", "");
			assertEquals("-1",result.getResponse().trim());
		}
		catch(Exception e)
		{
			e.printStackTrace();
			fail ( "Exception: " + e );
		}	
		
		
		try
		{
			c.setLogin(Long.toString(testAgent.getId()), testPass);

            ClientResponse result=c.sendRequest("DELETE", "div/12/6", "");
			assertEquals("2",result.getResponse().trim());
		}
		catch(Exception e)
		{
			e.printStackTrace();
			fail ( "Exception: " + e );
		}	
		
		
		try
		{
			c.setLogin(Long.toString(testAgent.getId()), testPass);

            ClientResponse result=c.sendRequest("GET", "do/2/it/3?param1=4&param2=5", "");
			assertEquals("14",result.getResponse().trim());
		}
		catch(Exception e)
		{
			e.printStackTrace();
			fail ( "Exception: " + e );
		}	
		
		
		try
		{
			c.setLogin(Long.toString(testAgent.getId()), testPass);

            ClientResponse result=c.sendRequest("GET", "do/2/it/3/not?param1=4&param2=5", "");
			assertEquals("-10",result.getResponse().trim());
		}
		catch(Exception e)
		{
			e.printStackTrace();
			fail ( "Exception: " + e );
		}	
		
		
		try
		{
			c.setLogin(Long.toString(testAgent.getId()), testPass);

            ClientResponse result=c.sendRequest("GET", "do/2/this/3/not?param1=4&param2=5", "");
			assertEquals("-14",result.getResponse().trim());
		}
		catch(Exception e)
		{
			e.printStackTrace();
			fail ( "Exception: " + e );
		}	
		
		
		try
		{
			c.setLogin(Long.toString(testAgent.getId()), testPass);
            Thread.sleep(5000);
            ClientResponse result=c.sendRequest("POST", "do/a/b", "c");
			assertEquals("abc",result.getResponse().trim());
		}
		catch(Exception e)
		{
			e.printStackTrace();
			fail ( "Exception: " + e );
		}


        try
        {
            c.setLogin(Long.toString(testAgent.getId()), testPass);
            @SuppressWarnings("unchecked")
            ClientResponse result=c.sendRequest("GET", "test1/1/2", "",new Pair[]{new Pair<>("c","5"),new Pair<>("e","4")});
            assertEquals("125",result.getResponse().trim());
            assertEquals("ho",result.getHeader("hi"));
            assertEquals("text/plain",result.getHeader("Content-Type"));
        }
        catch(Exception e)
        {
            e.printStackTrace();
            fail ( "Exception: " + e );
        }

        try
        {
            c.setLogin(Long.toString(testAgent.getId()), testPass);

            ClientResponse result=c.sendRequest("GET", "test2/1/2", "",new Pair[]{});
            assertEquals(412,result.getHttpCode());
        }
        catch(Exception e)
        {
            e.printStackTrace();
            fail ( "Exception: " + e );
        }


        try
        {
            c.setLogin(Long.toString(testAgent.getId()), testPass);


            ClientResponse result=c.sendRequest("POST", "books/8", "", MediaType.TEXT_PLAIN, "",new Pair[]{});
            assertEquals("8",result.getResponse().trim());

            result=c.sendRequest("POST", "books/8", "", MediaType.AUDIO_MPEG, "",new Pair[]{});
            assertEquals("56",result.getResponse().trim());
        }
        catch(Exception e)
        {
            e.printStackTrace();
            fail ( "Exception: " + e );
        }

        try
        {
            c.setLogin(Long.toString(testAgent.getId()), testPass);


            ClientResponse result=c.sendRequest("POST", "books/8", "", MediaType.TEXT_PLAIN, "",new Pair[]{});
            assertEquals("8",result.getResponse().trim());

            result=c.sendRequest("POST", "books/8", "", MediaType.AUDIO_MPEG, "",new Pair[]{});
            assertEquals("56",result.getResponse().trim());
        }
        catch(Exception e)
        {
            e.printStackTrace();
            fail ( "Exception: " + e );
        }

        try
        {
            c.setLogin(Long.toString(testAgent.getId()), testPass);


            ClientResponse result=c.sendRequest("GET", "books/8", "", MediaType.AUDIO_MPEG, "audio/*,audio/ogg",new Pair[]{});
            assertEquals("16",result.getResponse().trim());

            assertEquals("audio/ogg", result.getHeader("content-type"));

            result=c.sendRequest("GET", "books/8", "", MediaType.AUDIO_MPEG, "video/mp4,text/*",new Pair[]{});
            assertEquals("8",result.getResponse().trim());

            assertEquals("text/plain",result.getHeader("content-type"));
        }
        catch(Exception e)
        {
            e.printStackTrace();
            fail ( "Exception: " + e );
        }
    }
}
