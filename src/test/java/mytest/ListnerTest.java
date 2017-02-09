package mytest;

import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;
import org.junit.Test;

public class ListnerTest {

	@Test
	public void test() throws Exception {
		ZkClient zkClient = new ZkClient("127.0.0.1:2181", 5000, 5000);
		String path = "/ListnerTest";
		zkClient.createEphemeral(path);
		zkClient.subscribeDataChanges(path, new IZkDataListener() {
			public void handleDataDeleted(String dataPath) throws Exception {
				// ignore
			}

			public void handleDataChange(String dataPath, Object data) throws Exception {
				System.out.println("********************");
			}
		});

		zkClient.subscribeDataChanges(path, new IZkDataListener() {
			public void handleDataDeleted(String dataPath) throws Exception {
				// ignore
			}

			public void handleDataChange(String dataPath, Object data) throws Exception {
				System.out.println("#######################");
			}
		});

		for (int i = 0; i < 5; i++) {
			Thread.sleep(1000L);
			zkClient.writeData(path, System.currentTimeMillis());
			Thread.sleep(1000L);
		}

		zkClient.unsubscribeAll();

		zkClient.close();
	}

}
