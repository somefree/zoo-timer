package cn.hjdai.ztimer.zooenum;

public enum LoadChoice {

	/**
	 * 随机负载 - 默认值
	 */
	RANDOM("随机负载"),

	/**
	 * 轮询负载
	 */
	ROUND("轮询负载"),

	/**
	 * 权重负载
	 */
	WEIGHT("权重负载");

	private String remark;

	private LoadChoice(String remark) {
		this.remark = remark;
	}

	public String toString() {
		return remark;
	}

}
