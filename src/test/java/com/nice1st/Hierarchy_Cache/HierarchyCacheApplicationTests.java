package com.nice1st.Hierarchy_Cache;

import static org.assertj.core.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import com.nice1st.Hierarchy_Cache.service.HierarchyGroupCommandService;
import com.nice1st.Hierarchy_Cache.service.HierarchyGroupReadService;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HierarchyCacheApplicationTests {

	@Autowired
	private RedisConnectionFactory redisConnectionFactory;

	@Autowired
	HierarchyGroupCommandService commandService;

	@Autowired
	HierarchyGroupReadService readService;


	final String TENANT_ID = "tenant1";

	final String ROOT_GROUP_ID = "e1757bb8-8568-4135-8e67-778361b3329d";

	final String DEPTH1_ID = "f28ff6e7-e556-4911-8271-587f6e9a2c8b";
	final String DEPTH1_ID2 = "ef9ae61b-eb88-473f-8c3b-3f7b5b2afa8e";

	final String DEPTH2_ID = "9b6067dd-9dbd-438a-bf73-0ae149b02e19";

	final String DEPTH3_ID = "2591d205-2f62-43d4-8b1a-a6c10289a9b6";

	@BeforeAll
	void flush() {
		try (RedisConnection connection = redisConnectionFactory.getConnection()) {
			connection.serverCommands().flushDb();
		}
	}

	@Test
	void 정상_트리_생성_조회_이벤트소싱_검증() {
		// Given
		String newDepth2Id = commandService.create(DEPTH1_ID).getId();

		// When
		Set<String> ids = readService.read(ROOT_GROUP_ID);

		// Then
		assertThat(ids).contains(DEPTH1_ID, newDepth2Id);

		// one more by cached
		String newDepth2Id2 = commandService.create(DEPTH1_ID).getId();
		ids = readService.read(ROOT_GROUP_ID);
		assertThat(ids).contains(DEPTH1_ID, newDepth2Id, newDepth2Id2);
	}

}
