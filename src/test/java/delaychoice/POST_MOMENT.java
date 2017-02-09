package delaychoice;

import java.text.SimpleDateFormat;

import org.junit.Test;

import cn.hjdai.ztimer.ZooTask;
import cn.hjdai.ztimer.ZooTimer;
import cn.hjdai.ztimer.zooenum.DelayChoice;
import cn.hjdai.ztimer.zooenum.LoadChoice;

public class POST_MOMENT {

	private ZooTask getZooTask() throws Exception {
		return new ZooTask() {
			public void process() {
				System.out.println("7777777777777777777777777777777");
				try {
					Thread.sleep(1000L);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}.setTaskId("testPOST_MOMENT")//
				.setDelayChoice(DelayChoice.POST_MOMENT)//
				.setLoadChoice(LoadChoice.ROUND)//
				.setFirstDate(new SimpleDateFormat("yyyyMMddHHmmss").parse("20161220194600"))//
				.setFixedDelay(10000L);//
	}

	@Test
	public void test() throws Exception {
		ZooTimer zooTimer = new ZooTimer("127.0.0.1:2181", getZooTask());
		zooTimer.start(); // 启动
		Thread.sleep(60000L);
		zooTimer.stop(); // 停止
		System.out.println("over......");
	}

	@Test
	public void test1() throws Exception {
		ZooTimer zooTimer = new ZooTimer("127.0.0.1:2181", getZooTask().setLocalIP("127.0.0.1"));
		zooTimer.start(); // 启动
		Thread.sleep(60000L);
		zooTimer.stop(); // 停止
		System.out.println("over......");
	}

}
