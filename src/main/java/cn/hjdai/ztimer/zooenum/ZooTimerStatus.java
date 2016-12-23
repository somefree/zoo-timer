package cn.hjdai.ztimer.zooenum;

public enum ZooTimerStatus {

	/**
	 * 未开始 - 初始值
	 */
	STATUS_0("未开始"),
	/**
	 * 执行中
	 */
	STATUS_1("执行中"),
	/**
	 * 等待中
	 */
	STATUS_2("等待中"),
	/**
	 * 已停止
	 */
	STATUS_3("已停止");

	private String remark;

	private ZooTimerStatus(String remark) {
		this.remark = remark;
	}

	public String toString() {
		return remark;
	}

}
