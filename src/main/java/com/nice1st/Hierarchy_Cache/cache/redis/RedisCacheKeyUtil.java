package com.nice1st.Hierarchy_Cache.cache.redis;

class RedisCacheKeyUtil {

	static String getCursorKey(String tenantId) {
		return getPrefixKey(tenantId) + ":cursor";
	}

	static String getPrefixKey(String tenantId) {
		return tenantId + ":group";
	}

	static String getChildrenKey(String tenantId, String groupId) {
		return getPrefixKey(tenantId) + ":" + groupId + ":children";
	}

	static String getParentsKey(String tenantId, String groupId) {
		return getPrefixKey(tenantId) + ":" + groupId + ":parents";
	}

}
