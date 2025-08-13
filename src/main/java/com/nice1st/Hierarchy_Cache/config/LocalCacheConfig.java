package com.nice1st.Hierarchy_Cache.config;

import com.nice1st.Hierarchy_Cache.cache.CacheService;
import com.nice1st.Hierarchy_Cache.cache.LockService;
import com.nice1st.Hierarchy_Cache.cache.local.InMemoryCacheService;
import com.nice1st.Hierarchy_Cache.cache.local.InMemoryLockService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local")
public class LocalCacheConfig {

    @Bean
    public CacheService cacheService() {
        return new InMemoryCacheService();
    }

    @Bean
    public LockService lockService() {
        return new InMemoryLockService();
    }
}
