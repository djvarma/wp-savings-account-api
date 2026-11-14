package com.example.savings.config;

import com.example.savings.account.AccountResponse;
import com.example.savings.account.SavingsAccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

	@Bean
	RedisCacheManagerBuilderCustomizer accountsCacheCustomizer(@Value("${app.cache.accounts-ttl:10m}") Duration ttl) {
		// Store accounts as JSON rather than Java serialization, so the cache is readable
		// and doesn't break when the class changes shape.
		RedisCacheConfiguration accountsCache = RedisCacheConfiguration.defaultCacheConfig()
				.entryTtl(ttl)
				.disableCachingNullValues()
				.serializeValuesWith(SerializationPair.fromSerializer(
						new JacksonJsonRedisSerializer<>(AccountResponse.class)));

		return builder -> builder.withCacheConfiguration(SavingsAccountService.ACCOUNTS_CACHE, accountsCache);
	}

	/**
	 * If Redis is down, log it and carry on as if there was a cache miss. The cache is an
	 * optimisation, it shouldn't take the API down with it.
	 * <p>
	 * TODO: add a circuit breaker so we stop paying the Redis timeout on every request during an outage.
	 */
	@Override
	public CacheErrorHandler errorHandler() {
		return new LoggingCacheErrorHandler();
	}
}
