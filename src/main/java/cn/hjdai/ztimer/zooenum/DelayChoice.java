package cn.hjdai.ztimer.zooenum;

public enum DelayChoice {

	/**
	 * 前置间隔: 方法第 n 次执行的[开始], 与第 n+1 次执行的开始, 间隔 fixedDelay
	 */
	PRE("前置间隔"),

	/**
	 * 后置间隔: 方法第 n 次执行的[结束], 与第 n+1 次执行的开始, 间隔 fixedDelay - 默认值
	 */
	POST("后置间隔"),

	/**
	 * 前置准点间隔: 与前置间隔基本相同, 区别是: 指定了第一次执行的准确时间点
	 */
	PRE_MOMENT("准点间隔"),

	/**
	 * 后置准点间隔: 与后置间隔基本相同, 区别是: 指定了第一次执行的准确时间点
	 */
	POST_MOMENT("准点间隔");

	private String remark;

	private DelayChoice(String remark) {
		this.remark = remark;
	}

	public String toString() {
		return remark;
	}

}
