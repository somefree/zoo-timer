package cn.hjdai.ztimer;

import java.util.Date;
import java.util.List;
import java.util.Map;

import cn.hjdai.ztimer.utils.IpUtil;
import cn.hjdai.ztimer.zooenum.DelayChoice;
import cn.hjdai.ztimer.zooenum.LoadChoice;
import cn.hjdai.ztimer.zooenum.ZooTimerStatus;

public abstract class ZooTask {

	/**
	 * 业务方法
	 * 
	 * @throws Exception
	 */
	public abstract void process() throws Exception;

	/**
	 * 业务方法发生异常时, 执行该方法
	 * 用例: 主业务方法异常时, 通知管理员
	 * 
	 * @param zooTask zooTask对象
	 * @param e 主业务方法抛出的异常
	 */
	public void exceptionHandle(ZooTask zooTask, Exception e) {
	};

	/**
	 * 当zooTask的集群节点发生变化时的处理方法 (有节点加入或移出集群)
	 * 用例: 在集群中节点意外下线时, 通知管理员
	 * 
	 * @param zooTask zooTask对象
	 * @param currentNodes 集群中仍然存活的节点
	 */
	public void aliveNodesChange(ZooTask zooTask, List<String> currentNodes) {
	};

	private String taskId;

	private String taskDescription;

	private LoadChoice loadChoice = LoadChoice.RANDOM;

	private Map<String, Integer> weightConfig;

	private DelayChoice delayChoice = DelayChoice.PRE;

	private long initDelay = 5000L;

	private Date firstDate;

	private Long fixedDelay;

	private String localIP = IpUtil.getLocalIP();// 本机IP

	private ZooTimerStatus zooTimerStatus = ZooTimerStatus.STATUS_0;

	void setZooTimerStatus(ZooTimerStatus zooTimerStatus) {
		this.zooTimerStatus = zooTimerStatus;
	}

//----------------setter
	public ZooTask setTaskId(String taskId) {
		this.taskId = taskId;
		return this;
	}

	public ZooTask setTaskDescription(String taskDescription) {
		this.taskDescription = taskDescription;
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

	/**
	 * zooTask对象初始化时, 内部工具类已经读取本地IP对 localIP 赋值
	 * 该方法仅供单机模拟集群测试所用, 生产环境不建议手动设置节点IP
	 * 
	 * @param localIP 自定义IP
	 * @return zootask
	 */
	public ZooTask setLocalIP(String localIP) {
		this.localIP = localIP;
		return this;
	}

//----------------getter
	public String getTaskId() {
		return taskId;
	}

	public String getTaskDescription() {
		return taskDescription;
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

	public String getLocalIP() {
		return localIP;
	}

	public ZooTimerStatus getZooTimerStatus() {
		return zooTimerStatus;
	}

	@Override
	public String toString() {
		return "[taskId=" + taskId + ", taskDescription=" + taskDescription + ", loadChoice=" + loadChoice + ", weightConfig=" + weightConfig
				+ ", delayChoice=" + delayChoice + ", initDelay=" + initDelay + ", firstDate=" + firstDate + ", fixedDelay=" + fixedDelay + ", localIP="
				+ localIP + ", zooTimerStatus=" + zooTimerStatus + "]";
	}

}
