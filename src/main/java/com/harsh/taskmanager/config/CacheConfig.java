package com.harsh.taskmanager.config;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Caching setup shared by every cache provider. The provider itself is chosen with {@code spring.cache.type}:
 * {@code simple} (in-memory, default) or {@code redis} (see {@link RedisCacheConfig}).
 *
 * <p>The caching advice is ordered just before the transaction advice, so it wraps the transaction:
 * cache evictions run only after the write transaction has committed (no stale re-caching window),
 * and a cache hit returns without opening a database transaction at all.
 */
@Configuration
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 1)
public class CacheConfig implements CachingConfigurer {

    public static final String TASK_LIST_CACHE = "taskList";
    public static final String TASK_LIST_KEY_GENERATOR = "taskListKeyGenerator";

    @Bean(TASK_LIST_KEY_GENERATOR)
    public KeyGenerator taskListKeyGenerator() {
        return new TaskListKeyGenerator();
    }

    /** If the cache is unavailable (for example Redis is down) the error is logged and the request falls back to the database. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }
}
