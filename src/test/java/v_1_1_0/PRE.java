package v_1_1_0;

import org.junit.Test;

import cn.hjdai.ztimer.ZooTask;
import cn.hjdai.ztimer.ZooTimer;
import cn.hjdai.ztimer.zooenum.DelayChoice;
import cn.hjdai.ztimer.zooenum.LoadChoice;

public class PRE {

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
		}.setTaskId("testPRE")// zooTask的唯一标识, 我对"唯一"界定是: process()实现 + 定时策略
				.setLoadChoice(LoadChoice.ROUND)// 负载策略, 可选项: 随机负载 - 默认值/轮询负载/权重负载
				.setDelayChoice(DelayChoice.PRE)// 定时策略参数之一, 可选项: 
				// 1 - 前置间隔: 方法第 n 次执行的[开始], 与第 n+1 次执行的开始, 间隔 fixedDelay 
				// 2 - 后置间隔: 方法第 n 次执行的[结束], 与第 n+1 次执行的开始, 间隔 fixedDelay - 默认值
				// 3 - 前置准点间隔: 与前置间隔基本相同, 区别是: 指定了第一次执行的准确时间点
				// 4 - 后置准点间隔: 与后置间隔基本相同, 区别是: 指定了第一次执行的准确时间点
				.setInitDelay(2000L)// 定时策略参数之二, 第一次执行延时, 单位ms, 默认5000ms
				// 说明: 分布式环境下, 该值的精确性并无太大意义, zoo-timer只确保第一次执行延时不小于该设定值
				.setFixedDelay(10000L);// 定时策略参数之三, 每隔多久, 执行一次, 单位ms, 请与 DelayChoice 参数结合理解
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
