package cn.hjdai.ztimer;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.hjdai.ztimer.utils.CollectionUtil;
import cn.hjdai.ztimer.utils.StringUtil;
import cn.hjdai.ztimer.zooenum.DelayChoice;
import cn.hjdai.ztimer.zooenum.ZooTimerStatus;

public class ZooTimer {

	private static final Logger logger = LoggerFactory.getLogger(ZooTimer.class);

	private static Set<String> taskVaildSet = new TreeSet<String>();

	private String zkServer;// zookeeper 连接地址

	public String getZkServer() {
		return zkServer;
	}

	private final List<ZooTask> ZooTaskList;// ZooTask 集合

	public List<ZooTask> getZooTaskList() {
		return ZooTaskList;
	}

	/**
	 * 单任务
	 * 
	 * @param zkServer zookeeper连接地址, 例如:
	 *           127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183
	 * @param ZooTask 单个任务, 会放入任务集合 ZooTaskList
	 */
	public ZooTimer(String zkServer, ZooTask zooTask) {
		if (null == zooTask || StringUtil.isBlank(zkServer)) {
			throw new NullPointerException("[zooTask] or [zkServer] is null");
		}
		this.ZooTaskList = new ArrayList<ZooTask>(1);
		this.ZooTaskList.add(zooTask);
		this.zkServer = zkServer;
	}

	/**
	 * 多任务
	 * 
	 * @param zkServer zkServer zookeeper连接地址, 例如:
	 *           127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183
	 * @param ZooTaskList 任务集合
	 */
	public ZooTimer(String zkServer, List<ZooTask> zooTaskList) {
		if (CollectionUtil.isEmpty(zooTaskList) || StringUtil.isBlank(zkServer)) {
			throw new NullPointerException("[ZooTaskList] or [zkServer] is null");
		}
		this.ZooTaskList = zooTaskList;
		this.zkServer = zkServer;
	}

	private ZkClient zkClient;// zookeeper 客户端连接

	private ScheduledExecutorService scheduledThreadPool;// jdk timer 线程池

	private boolean startFlag;// 启动标志, true: 已启动, false: 未启动 或者 已停止

	public boolean getStartFlag() {
		return startFlag;
	}

	/**
	 * 启动 zooTimer
	 */
	public void start() {
		if (startFlag) {
			return;
		}
		// 建立zookeeper连接
		this.zkClient = new ZkClient(zkServer, 5000, 30000);
		if (!zkClient.waitUntilConnected(30, TimeUnit.SECONDS)) {
			throw new RuntimeException("zookeeper connect timeout within " + 30 + " s");
		}
		// timer初始化
		this.scheduledThreadPool = Executors.newScheduledThreadPool(ZooTaskList.size());

		// 启动所有 ZooTask
		for (ZooTask zooTask : ZooTaskList) {
			if (null == zooTask) {
				continue;
			}
			logger.info("starting ZooTask: {}", zooTask);
			String taskId = zooTask.getTaskId();
			if (StringUtil.isBlank(taskId)) {
				logger.error("init ZooTask error, [taskId] is null");
				continue;
			}
			String taskValid = taskId + "@" + zooTask.getLocalIP();
			if (taskVaildSet.contains(taskValid)) {// 同一IP, taskId不允许重复
				logger.error("init ZooTask error, duplicate [taskId]@[localIP]: {}", taskValid);
				continue;
			}
			taskVaildSet.add(taskValid);
			Long fixedDelay = zooTask.getFixedDelay();
			long sleepmills = zooTask.getSleepmills();
			if (null == fixedDelay || fixedDelay < (2 * sleepmills + 1000L)) {
				logger.error("init ZooTask error, [fixedDelay] is null or too small");
				continue;
			}
			DelayChoice delayChoice = zooTask.getDelayChoice();
			if (null == delayChoice) {
				logger.error("init ZooTask error, [delayChoice] is null");
				continue;
			}

			String competePath = "/ZooTimer/" + taskId + "-compete";
			if (!zkClient.exists(competePath)) {
				zkClient.createPersistent(competePath, true);
			}
			zooTask.setCompetePath(competePath);
			String alivePath = "/ZooTimer/" + taskId + "-alive";
			if (!zkClient.exists(alivePath)) {
				zkClient.createPersistent(alivePath, true);
			}
			zooTask.setAlivePath(alivePath);
			String localAlivePath = alivePath + "/" + zooTask.getLocalIP();
			if (zkClient.exists(localAlivePath)) {
				zkClient.delete(localAlivePath);
			}
			zkClient.createEphemeral(localAlivePath);

			zkClient.subscribeChildChanges(alivePath, new IZkChildListener() {
				@Override
				public void handleChildChange(String parentPath, List<String> currentChilds) throws Exception {
					logger.info("zooTask-[{}] alive nodes change to: {}", new Object[] { zooTask.getTaskId(), currentChilds });
					zooTask.setGroupCount(0);
					try {
						zooTask.aliveNodesChange();
					} catch (Exception e) {
						logger.error("zooTask-[" + taskId + "] aliveNodesChange() error !", e);
					}
				}
			});

			zooTask.setZkClient(zkClient);
			long initDelay = zooTask.getInitDelay();
			Date firstDate = zooTask.getFirstDate();
			if (DelayChoice.PRE.equals(delayChoice) || DelayChoice.PRE_MOMENT.equals(delayChoice)) {// 固定周期

				long delay = 0L;
				if (DelayChoice.PRE.equals(delayChoice)) {// 首次执行时刻无严格需求
					long creationTime = zkClient.getCreationTime(competePath);
					delay = initDelay + fixedDelay - (Math.abs(System.currentTimeMillis() - creationTime - initDelay) % fixedDelay);
				} else if (DelayChoice.PRE_MOMENT.equals(delayChoice)) {// 首次执行时刻有严格需求
					long firstTimeMillis = null == firstDate ? 0L : firstDate.getTime();
					long nowTimeMillis = System.currentTimeMillis() + (2 * sleepmills);
					delay = firstTimeMillis > nowTimeMillis ? (firstTimeMillis - nowTimeMillis)
							: (fixedDelay - ((nowTimeMillis - firstTimeMillis) % fixedDelay));
				}

				scheduledThreadPool.scheduleAtFixedRate(zooTask, delay, fixedDelay, TimeUnit.MILLISECONDS);
			} else if (DelayChoice.POST.equals(delayChoice) || DelayChoice.POST_MOMENT.equals(delayChoice)) {// 固定间隔

				zkClient.subscribeDataChanges(competePath, new IZkDataListener() {
					@Override
					public void handleDataDeleted(String dataPath) throws Exception {
						//ignore
					}

					@Override
					public void handleDataChange(String dataPath, Object data) throws Exception {
						long currentTimeMillis = System.currentTimeMillis();
						if ((Long) data > currentTimeMillis) {
							scheduledThreadPool.schedule(zooTask, (Long) data - currentTimeMillis, TimeUnit.MILLISECONDS);
						} else {
							logger.error("Unexpected error ! zooTask stoped: {}", zooTask);
						}
					}
				});

				long nowTimeMillis = System.currentTimeMillis();
				if (zkClient.getChildren(alivePath).size() == 1) {
					if (DelayChoice.POST.equals(delayChoice)) {
						zkClient.writeData(competePath, nowTimeMillis + initDelay);
					} else if (DelayChoice.POST_MOMENT.equals(delayChoice)) {
						long firstTimeMillis = null == firstDate ? 0L : firstDate.getTime();
						zkClient.writeData(competePath, Math.max(firstTimeMillis - (2 * sleepmills), nowTimeMillis + fixedDelay - (2 * sleepmills)));
					}
				}
			}
		}
		this.startFlag = true;
	}

	/**
	 * 停止 zooTimer, 释放资源, 但仍然保留 ZooTaskList, 如果process()正在执行, 则阻塞至执行结束 或 1小时超时
	 */
	public void stop() {
		zkClient.unsubscribeAll();
		this.scheduledThreadPool.shutdown();
		try {
			this.scheduledThreadPool.awaitTermination(1, TimeUnit.HOURS);
		} catch (InterruptedException e) {
			logger.warn("zooTimer 1小时内停止异常,可能某个process()方法被强行中断了", e);
		}
		this.scheduledThreadPool = null;
		for (ZooTask zooTask : ZooTaskList) {
			zooTask.setZkClient(null);
			zooTask.setCompetePath(null);
			zooTask.setAlivePath(null);
			zooTask.setZooTimerStatus(ZooTimerStatus.STATUS_3);
			logger.info("stopped ZooTask: {}", zooTask);
		}
		this.zkClient.close();
		this.zkClient = null;
		this.startFlag = false;
	}

}
