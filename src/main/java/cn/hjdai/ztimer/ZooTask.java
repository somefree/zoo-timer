package cn.hjdai.ztimer;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.I0Itec.zkclient.ZkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.hjdai.ztimer.utils.IpUtil;
import cn.hjdai.ztimer.utils.StringUtil;
import cn.hjdai.ztimer.zooenum.DelayChoice;
import cn.hjdai.ztimer.zooenum.LoadChoice;
import cn.hjdai.ztimer.zooenum.ZooTimerStatus;

public abstract class ZooTask implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(ZooTask.class);

	private String taskId;

	private LoadChoice loadChoice = LoadChoice.RANDOM;

	private Map<String, Integer> weightConfig;

	private DelayChoice delayChoice = DelayChoice.PRE;

	private long initDelay = 5000L;

	private Date firstDate;

	private Long fixedDelay;

	private ZooTimerStatus zooTimerStatus = ZooTimerStatus.STATUS_0;

	void setZooTimerStatus(ZooTimerStatus zooTimerStatus) {
		this.zooTimerStatus = zooTimerStatus;
	}

//----------------setter
	public ZooTask setTaskId(String taskId) {
		this.taskId = taskId;
		return this;
	}

	public ZooTask setLoadChoice(LoadChoice loadChoice) {
		this.loadChoice = loadChoice;
		return this;
	}

	public ZooTask setWeightConfig(Map<String, Integer> weightConfig) {
		this.weightConfig = weightConfig;
		return this;
	}

	public ZooTask setDelayChoice(DelayChoice delayChoice) {
		this.delayChoice = delayChoice;
		return this;
	}

	public ZooTask setInitDelay(long initDelay) {
		this.initDelay = initDelay;
		return this;
	}

	public ZooTask setFirstDate(Date firstDate) {
		this.firstDate = firstDate;
		return this;
	}

	public ZooTask setFixedDelay(Long fixedDelay) {
		this.fixedDelay = fixedDelay;
		return this;
	}

//----------------getter
	public String getTaskId() {
		return taskId;
	}

	public LoadChoice getLoadChoice() {
		return loadChoice;
	}

	public Map<String, Integer> getWeightConfig() {
		return weightConfig;
	}

	public DelayChoice getDelayChoice() {
		return delayChoice;
	}

	public long getInitDelay() {
		return initDelay;
	}

	public Date getFirstDate() {
		return firstDate;
	}

	public Long getFixedDelay() {
		return fixedDelay;
	}

	public ZooTimerStatus getZooTimerStatus() {
		return zooTimerStatus;
	}

	@Override
	public String toString() {
		return "ZooTask [taskId=" + taskId + ", loadChoice=" + loadChoice + ", weightConfig=" + weightConfig + ", delayChoice=" + delayChoice
				+ ", initDelay=" + initDelay + ", firstDate=" + firstDate + ", fixedDelay=" + fixedDelay + ", zooTimerStatus=" + zooTimerStatus
				+ ", localIP=" + localIP + "]";
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

	private int groupCount;// 均衡负载所需的计数器

	void setGroupCount(int groupCount) {
		this.groupCount = groupCount;
	}

	private int totalCount;// 均衡负载所需的计数器

	private String localIP = IpUtil.getLocalIP();// 本机IP

	/**
	 * zooTask对象初始化时, 内部工具类已经读取本地IP对 localIP 赋值
	 * 该方法仅供单机模拟集群测试所用, 生产环境不建议手动设置节点IP
	 * 
	 * @param localIP
	 */
	public ZooTask setLocalIP(String localIP) {
		this.localIP = localIP;
		return this;
	}

	public String getLocalIP() {
		return localIP;
	}

	/**
	 * 主业务方法
	 */
	public abstract void process();

	/**
	 * 主业务方法发生异常时, 执行该方法
	 * 用例: 主业务方法异常时, 通知管理员
	 */
	public abstract void exceptionHandle();

	/**
	 * 当zooTask的集群节点发生变化时的处理方法 (有节点加入或移出集群)
	 * 用例: 在集群中节点意外下线时, 通知管理员
	 */
	public abstract void aliveNodesChange();

	@Override
	public void run() {
		if (StringUtil.isBlank(competePath) || StringUtil.isBlank(alivePath) || StringUtil.isBlank(taskId) || null == fixedDelay
				|| fixedDelay < 2 * sleepMills + 1000L || null == zkClient) {
			logger.error("zooTask init error ! " + this.toString());
		}
		setZooTimerStatus(ZooTimerStatus.STATUS_2);
		long start = System.currentTimeMillis();
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
			setZooTimerStatus(ZooTimerStatus.STATUS_1);
			groupCount++;
			totalCount++;
			List<String> aliveNodes = zkClient.getChildren(alivePath);
			logger.info("zooTask-[{}] process() {}-begin, {}-taskGroup, nodes: {}", new Object[] { taskId, totalCount, groupCount, aliveNodes });
			try {
				process();
			} catch (Exception e) {
				logger.error("zooTask-[" + taskId + "] process() error ! exceptionHandle() begin...", e);
				try {
					exceptionHandle();
				} catch (Exception e1) {
					logger.error("zooTask-[" + taskId + "] exceptionHandle() error !", e1);
				}
			}
			logger.info("zooTask-[{}] process() {}-end, {}-taskGroup", new Object[] { taskId, totalCount, groupCount });
			if (delayChoice.equals(DelayChoice.POST) || delayChoice.equals(DelayChoice.POST_MOMENT)) {
				zkClient.writeData(competePath, System.currentTimeMillis() + fixedDelay - (2 * sleepMills));
			}
			setZooTimerStatus(ZooTimerStatus.STATUS_2);
		}

	}

	private String randChild(LoadChoice loadChoice) {
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
