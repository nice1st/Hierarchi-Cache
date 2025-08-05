package com.nice1st.Hierarchy_Cache.helper;

import java.time.Duration;
import java.util.Objects;

import org.springframework.data.redis.core.RedisTemplate;

public class RedisLockHelper {

	private final RedisTemplate<String, String> redisTemplate;

	public static String getLockKey(String tenantId) {
		return tenantId + ":group:lock";
	}

	public RedisLockHelper(RedisTemplate<String, String> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	public boolean tryLock(String key, Duration ttl, Duration maxWait) {
		long startTime = System.currentTimeMillis();
		while (System.currentTimeMillis() - startTime < maxWait.toMillis()) {
			Boolean success = redisTemplate.opsForValue().setIfAbsent(key, Thread.currentThread().getName(), ttl);
			if (Boolean.TRUE.equals(success)) {
				return true;
			}
			try {
				Thread.sleep(100); // 재시도 간격
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return false;
	}

	public void unlock(String key) {
		String currentValue = redisTemplate.opsForValue().get(key);
		if (Objects.equals(currentValue, Thread.currentThread().getName())) {
			redisTemplate.delete(key);
		}
	}
}
