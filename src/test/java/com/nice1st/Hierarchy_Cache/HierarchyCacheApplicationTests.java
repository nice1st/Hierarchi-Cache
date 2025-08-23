package com.nice1st.Hierarchy_Cache;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import com.nice1st.Hierarchy_Cache.cache.CacheService;
import com.nice1st.Hierarchy_Cache.cache.LockService;
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

    @Autowired
    CacheService cacheService;

    @Autowired
    LockService lockService;

    @Autowired
    RedisTemplate<String, String> redisTemplate;

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

    @Test
    void 커서_유실_오염_상태_자동복구_검증() {
        // Given: 캐시와 커서가 정상 상태
        Set<String> before = readService.read(ROOT_GROUP_ID);
        // 커서 유실 (삭제)
        String cursor = cacheService.getCursor(TENANT_ID);
        redisTemplate.delete(cursor);
        // 캐시도 flush로 오염 가능

        // When: read() 호출 (자동 복구 트리거)
        Set<String> after = readService.read(ROOT_GROUP_ID);

        // Then: 다시 정상 트리로 복구되었는지 확인
        assertThat(after).isEqualTo(before);
    }

    @Test
    void 락_미획득_시_DB_조회_fallback_검증() {
        // 락 점유 (직접 Redis에 락 key 삽입)
        String lockKey = lockService.getLockKey(TENANT_ID);
        redisTemplate.opsForValue().setIfAbsent(lockKey, "test-lock", java.time.Duration.ofMinutes(1));
        // When: read() 호출 (락 획득 불가 상황)
        Set<String> ids = readService.read(ROOT_GROUP_ID);
        // Then: 캐시 대신 DB 트리 결과만 리턴됨
        assertThat(ids).contains(DEPTH1_ID, DEPTH2_ID);
        // 캐시 상태에는 영향 X
    }

    @Test
    void 동시성_경쟁_상황에서_단일락_보장_검증() throws Exception {
        Set<String> before = readService.read(ROOT_GROUP_ID);

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Set<String>> resultSet = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    resultSet.add(readService.read(ROOT_GROUP_ID));
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        assertThat(resultSet).isNotEmpty();
        for (Set<String> set : resultSet) {
            assertThat(set).isEqualTo(before);
        }
        executor.shutdown();
    }
}
