package com.nice1st.Hierarchy_Cache.service;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.nice1st.Hierarchy_Cache.domain.HierarchyGroup;

@SpringBootTest
class CacheServiceTest {

	@Autowired
	CacheService cacheService;

	@Autowired
	HierarchyGroupService hierarchyGroupService;

	final String TENANT_ID = "tenant1";

	final String ROOT_GROUP_ID = "e1757bb8-8568-4135-8e67-778361b3329d";

	final String DEPTH1_ID = "f28ff6e7-e556-4911-8271-587f6e9a2c8b";

	final String DEPTH2_ID = "9b6067dd-9dbd-438a-bf73-0ae149b02e19";

	void initialize() {
		Map<String, List<HierarchyGroup>> groupedByParent = hierarchyGroupService.getGroupedByParent(TENANT_ID);
		cacheService.initialize(TENANT_ID, groupedByParent);
	}

	boolean hasCached() {
		List<HierarchyGroup> byTenantId = hierarchyGroupService.findByTenantId(TENANT_ID);
		Set<String> children = cacheService.getChildren(TENANT_ID, ROOT_GROUP_ID);

		return children != null && children.size() == (byTenantId.size() - 1);
	}

	@BeforeEach
	void setUp() {
		if (!hasCached()) {
			initialize();

			Set<String> children = cacheService.getChildren(TENANT_ID, ROOT_GROUP_ID);
			assertThat(children).hasSizeGreaterThanOrEqualTo(9999);
		}
	}

	@Test
	void getChildren() {
		// do nothing
	}

	@Test
	@Disabled
	void insertGroup() {
		final String ID = "test_id";
		cacheService.insertGroup(TENANT_ID, DEPTH2_ID, ID);

		Set<String> parents = cacheService.getParents(TENANT_ID, ID);
		assertThat(parents).hasSizeGreaterThanOrEqualTo(1);

		Set<String> children = cacheService.getChildren(TENANT_ID, DEPTH2_ID);
		assertThat(children).hasSizeGreaterThanOrEqualTo(0);
	}

	@Test
	@Disabled
	void deleteGroup() {
		Set<String> children;

		children = cacheService.getChildren(TENANT_ID, DEPTH1_ID);
		assertThat(children).contains(DEPTH2_ID);

		cacheService.deleteGroup(TENANT_ID, DEPTH2_ID);

		children = cacheService.getChildren(TENANT_ID, DEPTH1_ID);
		assertThat(children).doesNotContain(DEPTH2_ID);
	}
}