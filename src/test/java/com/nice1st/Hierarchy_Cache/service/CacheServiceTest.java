package com.nice1st.Hierarchy_Cache.service;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import com.nice1st.Hierarchy_Cache.domain.HierarchyGroup;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
class CacheServiceTest {

	@Autowired
	private RedisConnectionFactory redisConnectionFactory;

	@Autowired
	CacheService cacheService;

	@Autowired
	HierarchyGroupService hierarchyGroupService;

	final String TENANT_ID = "tenant1";

	final String ROOT_GROUP_ID = "e1757bb8-8568-4135-8e67-778361b3329d";

	final String DEPTH1_ID = "f28ff6e7-e556-4911-8271-587f6e9a2c8b";
	final String DEPTH1_ID2 = "ef9ae61b-eb88-473f-8c3b-3f7b5b2afa8e";

	final String DEPTH2_ID = "9b6067dd-9dbd-438a-bf73-0ae149b02e19";

	final String DEPTH3_ID = "2591d205-2f62-43d4-8b1a-a6c10289a9b6";

	@BeforeAll
	void flush() {
		try(var connection = redisConnectionFactory.getConnection()) {
			connection.serverCommands().flushDb();
		}
	}

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
	void insert_and_delete() {
		String id = insert();
		delete(id);
	}

	String insert() {
		final String ID = "test_id";
		cacheService.createGroup(TENANT_ID, DEPTH2_ID, ID);

		Set<String> parents = cacheService.getParents(TENANT_ID, ID);
		assertThat(parents).hasSizeGreaterThanOrEqualTo(1);

		Set<String> children = cacheService.getChildren(TENANT_ID, DEPTH2_ID);
		assertThat(children).hasSizeGreaterThanOrEqualTo(0);

		return ID;
	}

	void delete(String id) {
		Set<String> children;

		children = cacheService.getChildren(TENANT_ID, DEPTH1_ID);
		assertThat(children).contains(id);

		cacheService.deleteGroup(TENANT_ID, id);

		children = cacheService.getChildren(TENANT_ID, DEPTH1_ID);
		assertThat(children).doesNotContain(id);
	}

	@Test
	void move() {
		Set<String> parents;
		Set<String> children;

		parents = cacheService.getParents(TENANT_ID, DEPTH3_ID);
		assertThat(parents).contains(DEPTH1_ID);

		children = cacheService.getChildren(TENANT_ID, DEPTH1_ID);
		assertThat(children).contains(DEPTH3_ID);
		children = cacheService.getChildren(TENANT_ID, DEPTH2_ID);
		assertThat(children).contains(DEPTH3_ID);

		cacheService.moveGroup(TENANT_ID, DEPTH2_ID, DEPTH1_ID2);
		children = cacheService.getChildren(TENANT_ID, DEPTH1_ID);
		assertThat(children).doesNotContain(DEPTH3_ID);

		children = cacheService.getChildren(TENANT_ID, DEPTH2_ID);
		assertThat(children).contains(DEPTH3_ID);
		children = cacheService.getChildren(TENANT_ID, DEPTH1_ID2);
		assertThat(children).contains(DEPTH2_ID);
		assertThat(children).contains(DEPTH3_ID);

		parents = cacheService.getParents(TENANT_ID, DEPTH3_ID);
		assertThat(parents).doesNotContain(DEPTH1_ID);
		assertThat(parents).contains(DEPTH1_ID2);
	}
}