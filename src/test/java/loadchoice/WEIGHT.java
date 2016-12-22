package loadchoice;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import cn.hjdai.ztimer.ZooTask;
import cn.hjdai.ztimer.ZooTimer;
import cn.hjdai.ztimer.zooenum.DelayChoice;
import cn.hjdai.ztimer.zooenum.LoadChoice;

public class WEIGHT {

	private ZooTask getZooTask() {
		return new ZooTask() {
			public void process() {
				System.out.println("7777777777777777777777777777777");
				try {
					Thread.sleep(1000L);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			public void exceptionHandle() {
			}

			public void aliveNodesChange() {
			}
		}.setTaskId("testPRE")//
				.setDelayChoice(DelayChoice.PRE)//
				.setLoadChoice(LoadChoice.WEIGHT)//
				.setInitDelay(2000L)//
				.setFixedDelay(10000L);//
	}

	@Test
	public void test() throws Exception {
		Map<String, Integer> map = new HashMap<String, Integer>(2);
		map.put("127.0.0.1", 9);
		ZooTask zooTask = getZooTask().setWeightConfig(map);

		ZooTimer zooTimer = new ZooTimer("127.0.0.1:2181", zooTask);
		zooTimer.start(); // 启动
		Thread.sleep(120000L);
		zooTimer.stop(); // 停止
		System.out.println("over......");
	}

	@Test
	public void test1() throws Exception {
		Map<String, Integer> map = new HashMap<String, Integer>(2);
		map.put("127.0.0.1", 9);
		ZooTask zooTask = getZooTask().setWeightConfig(map).setLocalIP("127.0.0.1");

		ZooTimer zooTimer = new ZooTimer("127.0.0.1:2181", zooTask);
		zooTimer.start(); // 启动
		Thread.sleep(120000L);
		zooTimer.stop(); // 停止
		System.out.println("over......");
	}

}
