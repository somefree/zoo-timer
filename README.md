# zootimer
##分布式timer插件, 轻量, 易用, 去中心化, 均衡负载, 集群节点通过zookeeper同步作业, java编写 

###1-为什么会写这个?
    <br><br>如今的环境下, 几乎没有成熟的公司会把应用只部署一台机器, 多机部署使得应用面对扩容需求, 越来越简单, 但同时, 也失去了一些便捷性, 比如: 有一个小功能, 需要定时执行, 但是又不必要甚至不允许, 每个应用在特定时刻, 同时去执行相同代码, 而是只需在特定时刻, 任选集群中的某一台节点执行...
  <br>  <br>原本单机部署时, 一个jdk timer随手解决的问题, 在分布式环境下, 变得很不友好... 
   <br> <br>针对这种情形, 开源社区涌现出一批新的思路实现分布式的任务调度, 本人也关注过1-2个优秀的开源项目, 总结下来, 模型大致是: 建立一个调度中心, 各个业务功能在调度中心进行注册(执行周期, 接口方法, 集群节点, 负载策略等必要信息), 然后由调度中心针对每个业务功能, 建立timer, 远程调度集群某一节点的接口方法
   <br> <br>这种模型好处多多: 调度中心对各个业务功能有绝对掌控, 方便扩展周边的监控/统计等功能, 调度和功能代码分离, 等等...
   <br> <br>但在不同的场景下, 优点也是缺点: 中心化略显笨重和冗余, 随着任务数的增多, 调度中心这个应用本身也需要相当的资源去部署和维护, 调度与功能代码分离会使得监控和报警功能复杂化, 等等...

    <br><br>我的小目标是: 在分布式部署环境下, 像从前单机部署一样愉快的书写timer代码, 同时, 又能享受分布式部署带来的易扩展性, 于是有了: zoo-timer
    
###2-zoo-timer 的模型
<br>    <br>集群节点的通讯依赖zookeeper中间件, 每一个zookeeper集群, 抽象为一个 ZooTimer 对象, 每一个需要既定周期去定时执行的业务, 抽象为一个 ZooTask 对象, 一个 Zootimer 可以管理多个 ZooTask
 <br>   <br>Zootimer 的构造方法: 
 ```
 /**
 	 * 单任务
 	 * 
 	 * @param zkServer zookeeper连接地址, 例如:
 	 *           127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183
 	 * @param ZooTask 单个任务, 会放入任务集合 ZooTaskList
 	 */
 	public ZooTimer(String zkServer, ZooTask zooTask) {}
 /**
 	 * 多任务
 	 * 
 	 * @param zkServer zkServer zookeeper连接地址, 例如:
 	 *           127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183
 	 * @param ZooTaskList 任务集合
 	 */
 	public ZooTimer(String zkServer, List<ZooTask> zooTaskList) {}
 ```
     <br>ZooTask 是个抽象类, 使用时需要继承它, 实现三个抽象方法:
     
 ```
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
```
<br>  <br> 同时, 需要初始化设置一些必须的参数
```
zooTaskChild
.setTaskId("testPRE")// zooTask的唯一标识, 我对"唯一"界定是: process()实现 + 定时策略
.setLoadChoice(LoadChoice.ROUND)// 负载策略, 可选项: 随机负载 - 默认值/轮询负载/权重负载
.setDelayChoice(DelayChoice.PRE)// 定时策略参数之一, 可选项: 
// 1 - 前置间隔: 方法第 n 次执行的[开始], 与第 n+1 次执行的开始, 间隔 fixedDelay 
// 2 - 后置间隔: 方法第 n 次执行的[结束], 与第 n+1 次执行的开始, 间隔 fixedDelay - 默认值
// 3 - 前置准点间隔: 与前置间隔基本相同, 区别是: 指定了第一次执行的准确时间点
// 4 - 后置准点间隔: 与后置间隔基本相同, 区别是: 指定了第一次执行的准确时间点
.setInitDelay(2000L)// 定时策略参数之二, 第一次执行延时, 单位ms, 默认5000ms
// 说明: 分布式环境下, 该值的精确性并无太大意义, zoo-timer只确保第一次执行延时不小于该设定值
.setFixedDelay(10000L);// 定时策略参数之三, 每隔多久, 执行一次, 单位ms, 请与 DelayChoice 参数结合理解
 
// 其它可能需要的参数: 
.setFirstDate(Date date)// 准点模式下, 第一次执行的准确时间
.setWeightConfig(Map<String,Integer> map)// 权重负载时, 权重的节点分配, key是IP地址, 权重是大于1的整数, 建议不超过10
```  
 <br>3-如何将 zoo-timer 引入你的项目
  <br>   下载源码, mvn install, 或者直接在/build目录下载打包好的jar, JDK 1.6+, zookeeper版本: 3.4.8 第三方依赖
```
     <dependency>
       <groupId>com.101tec</groupId>
       <artifactId>zkclient</artifactId>
       <version>0.10</version>
     </dependency>
```
 <br>结束语
<br><br>一个人花了一周在闲暇时间码出来的东西, 难免有闭门造车之嫌, 奈何测试环境案例有限, 所以只做了一些基本的功能测试, 但覆盖了我所能想到的所有情形, 欢迎广大小伙伴参与测试, 使用, 并与我交流反馈, 顺便指点一二, 个人QQ: 305015319, 备注:zootimer
 <br>
