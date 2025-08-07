package com.nice1st.Hierarchy_Cache.cache.redis;

import java.time.Duration;
import java.util.Objects;

import org.springframework.data.redis.core.RedisTemplate;

import com.nice1st.Hierarchy_Cache.cache.LockService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RedisLockService implements LockService {

	private final RedisTemplate<String, String> redisTemplate;

	@Override
	public String getLockKey(String tenantId) {
		return tenantId + ":group:lock";
	}

	@Override
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

	@Override
	public void unlock(String key) {
		String currentValue = redisTemplate.opsForValue().get(key);
		if (Objects.equals(currentValue, Thread.currentThread().getName())) {
			redisTemplate.delete(key);
		}
	}
}
