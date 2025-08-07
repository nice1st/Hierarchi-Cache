package com.nice1st.Hierarchy_Cache.cache;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.nice1st.Hierarchy_Cache.domain.HierarchyGroup;

public interface CacheService {

	String getRootGroup();

	String getCursor(String tenantId);

	void updateCursor(String tenantId, Long tsid);

	void initialize(String tenantId, Map<String, List<HierarchyGroup>> groupByParentId);

	void deleteGroup(String tenantId, String groupId);

	void createGroup(String tenantId, String parentId, String id);

	void moveGroup(String tenantId, String targetId, String newParentId);

	Set<String> getParents(String tenantId, String groupId);

	Set<String> getChildren(String tenantId, String groupId);
}
