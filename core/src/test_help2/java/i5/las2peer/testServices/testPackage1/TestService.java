package i5.las2peer.testServices.testPackage1;

import i5.las2peer.api.Service;

public class TestService extends Service {
	public int getVersion() {
		return 110;
	}
	
	public static int getVersionStatic() {
		return 110;
	}
}
