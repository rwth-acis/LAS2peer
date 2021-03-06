package i5.las2peer.classLoaders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.Test;

import i5.las2peer.api.p2p.ServiceNameVersion;
import i5.las2peer.classLoaders.libraries.FileSystemRepository;
import i5.las2peer.classLoaders.policies.DefaultPolicy;

public class ClassManagerTest {

	@Test
	public void testPackageName() {
		assertEquals("my.package", ClassManager.getPackageName("my.package.Class"));

		try {
			ClassManager.getPackageName("teststring");
			fail("IllegalArgumentException should have been thrown");
		} catch (IllegalArgumentException e) {
		}
	}

	@Test
	public void testServiceClassLoading() throws ClassLoaderException, SecurityException, NoSuchMethodException,
			IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		ClassManager testee = new ClassManager(new FileSystemRepository("export/jars/"),
				ClassLoader.getSystemClassLoader(), new DefaultPolicy());

		Class<?> cl = testee.getServiceClass(ServiceNameVersion.fromString("i5.las2peer.classLoaders.testPackage2.UsingCounter@1.0"));

		assertFalse(cl.getClassLoader().equals(ClassLoader.getSystemClassLoader()));

		Method m = cl.getDeclaredMethod("countCalls");
		Object result = m.invoke(null);
		result = m.invoke(null);

		assertEquals(-2, ((Integer) result).intValue());

		Class<?> cl1 = testee.getServiceClass(new ServiceNameVersion("i5.las2peer.testServices.testPackage1.TestService", "1.0"));
		Class<?> cl2 = testee.getServiceClass(new ServiceNameVersion("i5.las2peer.testServices.testPackage1.TestService", "1.1"));
		Method m1 = cl1.getDeclaredMethod("getVersionStatic");
		Method m2 = cl2.getDeclaredMethod("getVersionStatic");
		assertEquals(m1.invoke(null), 100);
		assertEquals(m2.invoke(null), 110);
	}

	@Test
	public void testJarBehaviour() throws IllegalArgumentException, ClassLoaderException {
		ClassManager testee = new ClassManager(new FileSystemRepository("export/jars/"),
				ClassLoader.getSystemClassLoader(), new DefaultPolicy());
		testee.getServiceClass(new ServiceNameVersion("i5.las2peer.classLoaders.testPackage2.UsingCounter", "1.0"));

		assertEquals(1, testee.numberOfRegisteredServices());

		testee.unregisterService(new ServiceNameVersion("i5.las2peer.classLoaders.testPackage2.UsingCounter", "1.0"));

		assertEquals(0, testee.numberOfRegisteredServices());

		testee.getServiceClass(new ServiceNameVersion("i5.las2peer.classLoaders.testPackage2.UsingCounter", "1.0"));
		testee.getServiceClass(new ServiceNameVersion("i5.las2peer.classLoaders.testPackage1.CounterClass", "1.0"));
		testee.getServiceClass(new ServiceNameVersion("i5.las2peer.classLoaders.testPackage1.CounterClass", "1.1"));

		assertEquals(3, testee.numberOfRegisteredServices());
	}

	@Test
	public void testMultipleServiceClassLoading() throws ClassLoaderException, SecurityException, NoSuchMethodException,
			IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		ClassManager testee = new ClassManager(new FileSystemRepository("export/jars/"),
				ClassLoader.getSystemClassLoader(), new DefaultPolicy());

		Class<?> cl1 = testee.getServiceClass(new ServiceNameVersion("i5.las2peer.classLoaders.testPackage2.UsingCounter", "1.0"));
		Class<?> cl2 = testee.getServiceClass(new ServiceNameVersion("i5.las2peer.classLoaders.testPackage2.UsingCounter", "1.0"));

		assertFalse(cl1.getClassLoader().equals(ClassLoader.getSystemClassLoader()));
		assertFalse(cl2.getClassLoader().equals(ClassLoader.getSystemClassLoader()));

		// check that CounterClass is the same
		assertSame(cl1, cl2);
	}

}
