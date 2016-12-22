package cn.hjdai.ztimer.utils;

import java.util.Collection;

@SuppressWarnings("rawtypes")
public class CollectionUtil {

	public static boolean isEmpty(Collection coll) {
		return (coll == null || coll.isEmpty());
	}

}
