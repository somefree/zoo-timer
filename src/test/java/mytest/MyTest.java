package mytest;

import java.util.List;

import org.I0Itec.zkclient.ZkClient;
import org.junit.Test;

public class MyTest {

	@Test
	public void test() throws Exception {
		ZkClient zkClient = new ZkClient("127.0.0.1:2181", 5000, 5000);

		zkClient.delete("/adfasdf/adfasdf");

		System.out.println("deleted~~~~~~~~");

		Thread.sleep(10000L);
		zkClient.close();
	}

	@Test
	public void test1() throws Exception {
		ZkClient zkClient = new ZkClient("127.0.0.1:2181", 5000, 5000);

		Object readData = zkClient.readData("/asdfdfs/asdfsf", true);

		System.out.println("read - " + readData);

		Thread.sleep(10000L);
		zkClient.close();
	}

	@Test
	public void test2() throws Exception {
		ZkClient zkClient = new ZkClient("127.0.0.1:2181", 5000, 5000);

		List<String> children = zkClient.getChildren("/sdfasdf/asdfsd");

		System.out.println("children - " + children);

		Thread.sleep(10000L);
		zkClient.close();
	}

}
