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

	private final List<ZooTask> zooTaskList;// ZooTask 集合

	private final List<ZooTaskExecutor> zooTaskExecutorList;// ZooTask 集合

	public List<ZooTask> getZooTaskList() {
		return zooTaskList;
	}

	/**
	 * 单个任务
	 * 
	 * @param zkServer zookeeper连接地址, 例如:
	 *           127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183
	 * @param zooTask 单个任务, 会放入任务集合 ZooTaskList
	 */
	public ZooTimer(String zkServer, ZooTask zooTask) {
		if (null == zooTask || StringUtil.isBlank(zkServer)) {
			throw new NullPointerException("[zooTask] or [zkServer] is null");
		}
		this.zkServer = zkServer;
		this.zooTaskList = new ArrayList<ZooTask>(1);
		this.zooTaskList.add(zooTask);
		this.zooTaskExecutorList = new ArrayList<ZooTaskExecutor>(1);
	}

	/**
	 * 多任务
	 * 
	 * @param zkServer zkServer zookeeper连接地址, 例如:
	 *           127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183
	 * @param zooTaskList 任务集合
	 */
	public ZooTimer(String zkServer, List<ZooTask> zooTaskList) {
		if (CollectionUtil.isEmpty(zooTaskList) || StringUtil.isBlank(zkServer)) {
			throw new NullPointerException("[ZooTaskList] or [zkServer] is null");
		}
		this.zkServer = zkServer;
		this.zooTaskList = zooTaskList;
		this.zooTaskExecutorList = new ArrayList<ZooTaskExecutor>(zooTaskList.size());
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
		this.scheduledThreadPool = Executors.newScheduledThreadPool(zooTaskList.size());

		// 启动所有 ZooTask
		for (final ZooTask zooTask : zooTaskList) {

			if (null == zooTask) {
				continue;
			}
			logger.info("starting ZooTask: {}", zooTask);
			final String taskId = zooTask.getTaskId();
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

			final ZooTaskExecutor zooTaskExecutor = new ZooTaskExecutor(zooTask);
			Long fixedDelay = zooTask.getFixedDelay();
			long sleepmills = zooTaskExecutor.getSleepmills();
			if (null == fixedDelay || fixedDelay < (2 * sleepmills + 1000L)) {
				logger.error("init ZooTask error, [fixedDelay] is null or too small");
				continue;
			}
			DelayChoice delayChoice = zooTask.getDelayChoice();
			if (null == delayChoice) {
				logger.error("init ZooTask error, [delayChoice] is null");
				continue;
			}
			this.zooTaskExecutorList.add(zooTaskExecutor);

			String competePath = "/ZooTimer/" + taskId + "-compete";
			if (!zkClient.exists(competePath)) {
				zkClient.createPersistent(competePath, true);
			}
			zooTaskExecutor.setCompetePath(competePath);
			String alivePath = "/ZooTimer/" + taskId + "-alive";
			if (!zkClient.exists(alivePath)) {
				zkClient.createPersistent(alivePath, true);
			}
			zooTaskExecutor.setAlivePath(alivePath);
			String localAlivePath = alivePath + "/" + zooTask.getLocalIP();
			if (zkClient.exists(localAlivePath)) {
				zkClient.delete(localAlivePath);
			}
			zkClient.createEphemeral(localAlivePath);

			IZkChildListener aliveChildListener = new IZkChildListener() {
				@Override
				public void handleChildChange(String parentPath, List<String> currentNodes) throws Exception {
					logger.info("zooTask-[{}] alive nodes change to: {}", new Object[] { zooTask.getTaskId(), currentNodes });
					zooTaskExecutor.setGroupCount(0);
					try {
						if (zooTask.getLocalIP().equals(currentNodes.get(0))) {
							zooTask.aliveNodesChange(zooTask, currentNodes);
						}
					} catch (Exception e) {
						logger.error("zooTask-[" + taskId + "] aliveNodesChange() error !", e);
					}
				}
			};
			zkClient.subscribeChildChanges(alivePath, aliveChildListener);
			zooTaskExecutor.setAliveChildListener(aliveChildListener);

			zooTaskExecutor.setZkClient(zkClient);
			long initDelay = zooTask.getInitDelay();
			Date firstDate = zooTask.getFirstDate();
			if (DelayChoice.PRE.equals(delayChoice) || DelayChoice.PRE_MOMENT.equals(delayChoice)) {// 固定周期

				long delay = 0L;
				if (DelayChoice.PRE.equals(delayChoice)) {// 首次执行时刻无严格需求
					long creationTime = zkClient.getCreationTime(competePath);
					delay = initDelay + fixedDelay - (Math.abs(System.currentTimeMillis() - creationTime - initDelay) % fixedDelay);
				} else if (DelayChoice.PRE_MOMENT.equals(delayChoice)) {// 首次执行时刻有严格需求
					long firstTimeMillis = null == firstDate ? 0L : firstDate.getTime();
					long nowTimeMillis = System.currentTimeMillis() + 2 * sleepmills;
					delay = firstTimeMillis > nowTimeMillis ? (firstTimeMillis - nowTimeMillis)
							: (fixedDelay - ((nowTimeMillis - firstTimeMillis) % fixedDelay));
				}

				scheduledThreadPool.scheduleAtFixedRate(zooTaskExecutor, delay, fixedDelay, TimeUnit.MILLISECONDS);
			} else if (DelayChoice.POST.equals(delayChoice) || DelayChoice.POST_MOMENT.equals(delayChoice)) {// 固定间隔

				IZkDataListener postDataListener = new IZkDataListener() {
					@Override
					public void handleDataDeleted(String dataPath) throws Exception {
						//ignore
					}

					@Override
					public void handleDataChange(String dataPath, Object data) throws Exception {
						long currentTimeMillis = System.currentTimeMillis();
						if ((Long) data > currentTimeMillis) {
							scheduledThreadPool.schedule(zooTaskExecutor, (Long) data - currentTimeMillis, TimeUnit.MILLISECONDS);
						} else {
							logger.error("Unexpected error ! zooTask stoped: {}", zooTask);
						}
					}
				};

				zkClient.subscribeDataChanges(competePath, postDataListener);
				zooTaskExecutor.setPostPostDataListener(postDataListener);

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
		stop(3600);
	}

	/**
	 * 停止 zooTimer, 释放资源, 但仍然保留 ZooTaskList, 如果process()正在执行, 则阻塞至执行结束 或 超出等待时间
	 * 
	 * @param waitSeconds 超时时间
	 */
	public void stop(long waitSeconds) {
		zkClient.unsubscribeAll();
		this.scheduledThreadPool.shutdown();
		try {
			this.scheduledThreadPool.awaitTermination(waitSeconds, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			logger.warn("zooTimer " + waitSeconds + "s内停止异常,可能某个process()方法被强行中断了", e);
		}
		this.scheduledThreadPool = null;
		for (ZooTaskExecutor zooTaskExecutor : zooTaskExecutorList) {
			zooTaskExecutor.setZkClient(null);
			zooTaskExecutor.setCompetePath(null);
			zooTaskExecutor.setAlivePath(null);
			zooTaskExecutor.getZooTask().setZooTimerStatus(ZooTimerStatus.STATUS_3);
			logger.info("stopped ZooTask: {}", zooTaskExecutor.getZooTask());
		}
		this.zkClient.close();
		this.zkClient = null;
		this.startFlag = false;
	}

}
