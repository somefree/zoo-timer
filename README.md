# zootimer
##分布式timer插件, 轻量, 易用, 去中心化, 均衡负载, 集群节点通过zookeeper同步作业, java编写 

###1-为什么会写这个?

如今的环境下, 几乎没有成熟的公司会把应用只部署一台机器, 多机部署使得应用面对扩容需求, 越来越简单, 但同时, 也失去了一些便捷性, 比如: 有一个小功能, 需要定时执行, 但是又不必要甚至不允许, 每个应用在特定时刻, 同时去执行相同代码, 而是只需在特定时刻, 任选集群中的某一台节点执行...


原本单机部署时, 一个jdk timer随手解决的问题, 在分布式环境下, 变得很不友好... 

针对这种情形, 开源社区涌现出一批新的思路实现分布式的任务调度, 本人也关注过1-2个优秀的开源项目, 总结下来, 模型大致是: 建立一个调度中心, 各个业务功能在调度中心进行注册(执行周期, 接口方法, 集群节点, 负载策略等必要信息), 然后由调度中心针对每个业务功能, 建立timer, 远程调度集群某一节点的接口方法

这种模型好处多多: 调度中心对各个业务功能有绝对掌控, 方便扩展周边的监控/统计等功能, 调度和功能代码分离, 等等...

但在不同的场景下, 优点也是缺点: 中心化略显笨重和冗余, 随着任务数的增多, 调度中心这个应用本身也需要相当的资源去部署和维护, 调度与功能代码分离会使得监控和报警功能复杂化, 等等...


我的小目标是: 在分布式部署环境下, 像从前单机部署一样愉快地书写timer代码, 同时, 又能享受分布式部署带来的高可用, 负载均衡, 于是有了: zoo-timer
    
###2-快速开始

集群节点的通讯依赖zookeeper中间件, 每一个zookeeper客户端连接, 抽象为一个 ZooTimer 对象, 每一个需要既定周期去定时执行的业务, 抽象为一个 ZooTask 对象, 一个 Zootimer 可以管理多个 ZooTask

一个入门示例:
```
	public static void main(String[] args) throws Exception {
		ZooTask zooTask = new ZooTask() {
			public void process() throws Exception {
                                // 主要的业务方法, 每当到了执行时间, 仅有一个节点执行该方法
				System.out.println("7777777777777777777777777777777");
				Thread.sleep(1000L);
			}

			@Override
			public void exceptionHandle(ZooTask zooTask, Exception e) {
				// 可选的监控预警方法1: 如果 process() 方法抛出了异常, 执行该方法, 该方法可以不重写
			}

			@Override
			public void aliveNodesChange(ZooTask zooTask, List<String> currentNodes) {
				// 可选的监控预警方法2: 如果该zooTask的集群发生了节点变化, 执行该方法, 该方法可以不重写
			}
		}.setTaskId("testPRE")// zooTask的唯一标识, 很重要, 不可重复
		 .setTaskDescription("这只是个测试用例")// zooTask描述
		 .setLoadChoice(LoadChoice.ROUND)// 负载策略, 可选项: 随机负载/轮询负载/权重负载
		 .setDelayChoice(DelayChoice.PRE)// 定时策略参数之一, 可选项: 
		 // 前置间隔: 方法第 n 次执行的[开始], 与第 n+1 次执行的开始, 间隔 fixedDelay 
		 // 后置间隔: 方法第 n 次执行的[结束], 与第 n+1 次执行的开始, 间隔 fixedDelay
		 // 前置准点间隔: 与前置间隔基本相同, 区别是: 指定了第一次执行的准确时间点
		 // 后置准点间隔: 与后置间隔基本相同, 区别是: 指定了第一次执行的准确时间点
		 .setInitDelay(5000L)// 定时策略参数之二, 第一次执行延时, 单位ms, 默认5000ms
		 // 说明: 分布式环境下, 该值的精确性并无太大意义, zoo-timer只确保程序启动后延时不小于该设定值
		 .setFixedDelay(10000L);// 定时策略参数之三, 每隔多久, 执行一次, 单位ms
		    

		ZooTimer zooTimer = new ZooTimer("127.0.0.1:2181", zooTask);
		zooTimer.start(); // 启动
		Thread.sleep(60000L);
		zooTimer.stop(60); // 停止, 1分钟超时	
 	}
```



###3-如何将 zoo-timer 引入你的项目

下载源码, mvn install, 或者直接在/build目录下载打包好的jar, JDK 1.6+, zookeeper版本: 3.4.8 第三方依赖
```
     <dependency>
       <groupId>com.101tec</groupId>
       <artifactId>zkclient</artifactId>
       <version>0.10</version>
     </dependency>
```

结束语

一个人自嗨码出来的东西, 难免有闭门造车之嫌, 奈何测试环境案例有限, 所以只做了一些基本的功能测试, 但覆盖了我所能想到的所有情形, 欢迎广大小伙伴参与测试, 使用, 并与我交流反馈, 顺便指点一二, 邮箱:amx0728@163.com
