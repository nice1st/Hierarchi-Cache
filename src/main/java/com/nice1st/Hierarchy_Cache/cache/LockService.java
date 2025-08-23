package com.nice1st.Hierarchy_Cache.cache;

import java.time.Duration;

public interface LockService {

    String getLockKey(String tenantId);

    boolean tryLock(String key, Duration ttl, Duration maxWait);

    void unlock(String key);
}
