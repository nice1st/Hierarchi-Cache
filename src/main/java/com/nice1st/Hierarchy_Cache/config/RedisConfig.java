package com.nice1st.Hierarchy_Cache.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.nice1st.Hierarchy_Cache.cache.CacheService;
import com.nice1st.Hierarchy_Cache.cache.LockService;
import com.nice1st.Hierarchy_Cache.cache.redis.RedisCacheService;
import com.nice1st.Hierarchy_Cache.cache.redis.RedisLockService;

@Configuration
public class RedisConfig {

	@Bean
	public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
		RedisTemplate<String, String> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);

		template.setKeySerializer(new StringRedisSerializer());
		template.setValueSerializer(new GenericJackson2JsonRedisSerializer());

		return template;
	}

	@Bean
	public CacheService cacheService(RedisTemplate<String, String> redisTemplate) {
		return new RedisCacheService(redisTemplate);
	}

	@Bean
	public LockService lockService(RedisTemplate<String, String> redisTemplate) {
		return new RedisLockService(redisTemplate);
	}
}
