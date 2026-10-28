package com.harsh.taskmanager.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harsh.taskmanager.dto.PageResponse;
import com.harsh.taskmanager.dto.TaskResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

/**
 * Redis-specific cache settings, active when {@code spring.cache.type=redis} (the "redis" profile).
 */
@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
public class RedisCacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer taskListCacheCustomizer(RedisConnectionFactory connectionFactory,
                                                                      ObjectMapper objectMapper,
                                                                      @Value("${app.cache.task-list-ttl}") Duration taskListTtl) {
        // Values are stored as readable JSON. The serializer is bound to the exact cached type, so no class
        // names have to be embedded in the payload; the copied mapper keeps Spring's java.time support.
        ObjectMapper cacheMapper = objectMapper.copy();
        JavaType pageOfTasks = cacheMapper.getTypeFactory().constructParametricType(PageResponse.class, TaskResponse.class);

        RedisCacheConfiguration taskListConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(taskListTtl)
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(cacheMapper, pageOfTasks)));

        return builder -> builder
                // Evicting all entries uses SCAN instead of the blocking KEYS command.
                .cacheWriter(RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory, BatchStrategies.scan(500)))
                .withCacheConfiguration(CacheConfig.TASK_LIST_CACHE, taskListConfig);
    }
}
