package cn.hjdai.ztimer;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.hjdai.ztimer.utils.StringUtil;
import cn.hjdai.ztimer.zooenum.DelayChoice;
import cn.hjdai.ztimer.zooenum.LoadChoice;
import cn.hjdai.ztimer.zooenum.ZooTimerStatus;

class ZooTaskExecutor implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(ZooTask.class);

	private ZooTask zooTask;

	public ZooTask getZooTask() {
		return zooTask;
	}

	public void setZooTask(ZooTask zooTask) {
		this.zooTask = zooTask;
	}

	public ZooTaskExecutor(ZooTask zooTask) {
		super();
		this.zooTask = zooTask;
	}

	// 不同节点与zookeeper同步通信所需的一个缓冲值, 不宜过大, 也不宜过小, 推荐值: 500-2000
	private static final long sleepMills = 2000L;

	long getSleepmills() {
		return sleepMills;
	}

	private ZkClient zkClient;

	void setZkClient(ZkClient zkClient) {
		this.zkClient = zkClient;
	}

	private String competePath;

	void setCompetePath(String competePath) {
		this.competePath = competePath;
	}

	private String alivePath;

	void setAlivePath(String alivePath) {
		this.alivePath = alivePath;
	}

	private IZkChildListener aliveChildListener;

	void setAliveChildListener(IZkChildListener aliveChildListener) {
		this.aliveChildListener = aliveChildListener;
	}

	IZkChildListener getAliveChildListener() {
		return aliveChildListener;
	}

	private IZkDataListener postDataListener;

	void setPostPostDataListener(IZkDataListener postDataListener) {
		this.postDataListener = postDataListener;
	}

	IZkDataListener getPostDataListener() {
		return postDataListener;
	}

	public void setPostDataListener(IZkDataListener postDataListener) {
		this.postDataListener = postDataListener;
	}

	private int groupCount;// 均衡负载所需的计数器

	void setGroupCount(int groupCount) {
		this.groupCount = groupCount;
	}

	private int totalCount;// 均衡负载所需的计数器

	@Override
	public void run() {
		long start = System.currentTimeMillis();
		zooTask.setZooTimerStatus(ZooTimerStatus.STATUS_2);

		String taskId = zooTask.getTaskId();
		Long fixedDelay = zooTask.getFixedDelay();
		DelayChoice delayChoice = zooTask.getDelayChoice();
		LoadChoice loadChoice = zooTask.getLoadChoice();
		Map<String, Integer> weightConfig = zooTask.getWeightConfig();

		if (StringUtil.isBlank(competePath) || StringUtil.isBlank(alivePath) || StringUtil.isBlank(taskId) || null == fixedDelay
				|| fixedDelay < 2 * sleepMills + 1000L || null == zkClient) {
			logger.error("zooTask init error ! " + this.toString());
		}

		if (!zkClient.waitUntilConnected(sleepMills, TimeUnit.MILLISECONDS)) {// 确认 zkClient 连接状态
			logger.error("zooTask-[" + taskId + "] error !", new TimeoutException("zookeeper client reConnect timeout within " + sleepMills + " ms"));
			return;
		}

		String randChild = randChild(loadChoice);// 节点生成的随机key, 集群中所有节点通过随机key来竞争process()方法的执行权
		if (null == randChild) {
			logger.error("zooTask-[" + taskId + "] error ! invalid [weightConfig]: " + weightConfig);
			return;
		}
		String randPath = competePath + "/" + randChild;// 随机key对应的随机znode, 即用即删
		zkClient.createEphemeral(randPath);
		try {// 集群通讯前置栅栏
			Thread.sleep(sleepMills - System.currentTimeMillis() + start);
			start = System.currentTimeMillis();
		} catch (InterruptedException e) {
			logger.error("zooTask-[" + taskId + "] error !", e);
			return;
		}
		List<String> list = zkClient.getChildren(competePath);
		Collections.sort(list);
		try {// 集群通讯后置栅栏
			Thread.sleep(sleepMills - System.currentTimeMillis() + start);
		} catch (InterruptedException e) {
			logger.error("zooTask-[" + taskId + "] error !", e);
			return;
		}
		zkClient.delete(randPath);// 删除临时node

		if (randChild.equals(list.get(0))) {// 只有一个竞争成功的节点, 执行业务方法process()
			zooTask.setZooTimerStatus(ZooTimerStatus.STATUS_1);
			groupCount++;
			totalCount++;
			List<String> aliveNodes = zkClient.getChildren(alivePath);
			logger.info("zooTask-[{}] process() begin-{}-{}, nodes: {}", new Object[] { taskId, totalCount, groupCount, aliveNodes });
			try {
				zooTask.process();
			} catch (Exception e) {
				logger.error("zooTask-[" + taskId + "] process() error ! exceptionHandle() begin...", e);
				try {
					zooTask.exceptionHandle(this.toString(), e);
				} catch (Exception e1) {
					logger.error("zooTask-[" + taskId + "] exceptionHandle() error !", e1);
				}
			}
			logger.info("zooTask-[{}] process() end-{}-{}", new Object[] { taskId, totalCount, groupCount });
			if (delayChoice.equals(DelayChoice.POST) || delayChoice.equals(DelayChoice.POST_MOMENT)) {
				zkClient.writeData(competePath, System.currentTimeMillis() + fixedDelay - (2 * sleepMills));
			}
			zooTask.setZooTimerStatus(ZooTimerStatus.STATUS_2);
		}

	}

	private String randChild(LoadChoice loadChoice) {
		Map<String, Integer> weightConfig = zooTask.getWeightConfig();
		String localIP = zooTask.getLocalIP();

		String randChild = UUID.randomUUID().toString();
		if (LoadChoice.ROUND.equals(loadChoice)) {
			randChild = groupCount + randChild;
		} else if (LoadChoice.WEIGHT.equals(loadChoice)) {
			Integer weight = weightConfig.get(localIP);
			if (null != weight && weight > 1) {
				for (int i = 0; i < weight; i++) {
					String newTemp = UUID.randomUUID().toString();
					randChild = randChild.compareTo(newTemp) < 0 ? randChild : newTemp;
				}
			}
		}
		return randChild;
	}

}