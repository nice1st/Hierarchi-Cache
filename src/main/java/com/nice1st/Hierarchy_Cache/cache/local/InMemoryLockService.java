package com.nice1st.Hierarchy_Cache.cache.local;

import com.nice1st.Hierarchy_Cache.cache.LockService;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class InMemoryLockService implements LockService {

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public String getLockKey(String tenantId) {
        return tenantId + ":group:lock";
    }

    @Override
    public boolean tryLock(String key, Duration ttl, Duration maxWait) {
        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        try {
            return lock.tryLock(maxWait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void unlock(String key) {
        ReentrantLock lock = locks.get(key);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
