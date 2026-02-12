/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.app;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.security.acl.redis.AclImplDeserializer;
import com.epam.pipeline.security.acl.redis.AclImplSerializer;
import com.epam.pipeline.security.acl.redis.JsonRedisSerializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.acls.domain.AclImpl;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

@EnableCaching
public class CacheConfiguration {

    public static final String PREFERENCE_CACHE = "preferences";
    public static final String ACL_CACHE = "aclCache";

    private static final String REDIS = "REDIS";
    private static final String MEMORY = "MEMORY";
    private static final String CACHE_TYPE = "cache.type";
    private static final String ACL_CACHE_TYPE = "security.acl.cache.type";

    @Value("${cache.type:}")
    private String cacheType;

    @Value("${security.acl.cache.type:}")
    private String cacheTypeAcl;

    @Value("${redis.host:}")
    private String redisHost;

    @Value("${redis.port:}")
    private Integer redisPort;

    @Value("${redis.max.connections:20}")
    private Integer redisPoolConnections;

    @Value("${redis.pool.timeout:20000}")
    private Integer poolTimeout;

    @Value("${redis.use.optimized.parsing:false}")
    private boolean useOptimizedParsing;

    @Bean
    @Primary
    public CacheManager cacheManager(@Qualifier("redisCacheManagerPref")
                                         final Optional<RedisCacheManager> redisCacheManagerPref) {
        return switch (cacheType) {
            case MEMORY -> new ConcurrentMapCacheManager(PREFERENCE_CACHE);
            case REDIS -> redisCacheManagerPref.orElseThrow(IllegalArgumentException::new);
            default -> new NoOpCacheManager();
        };
    }

    @Bean
    public CacheManager aclCacheManager(@Qualifier("redisCacheManagerAcl")
                                            final Optional<RedisCacheManager> redisCacheManagerAcl) {
        return switch (cacheTypeAcl) {
            case MEMORY -> new ConcurrentMapCacheManager(ACL_CACHE);
            case REDIS -> redisCacheManagerAcl.orElseThrow(IllegalArgumentException::new);
            default -> new NoOpCacheManager();
        };
    }

    @Bean("redisCacheManagerPref")
    @ConditionalOnProperty(value = CACHE_TYPE, havingValue = REDIS)
    public RedisCacheManager redisCacheManagerPref(final RedisConnectionFactory connectionFactory) {
        return buildRedisCacheManager(connectionFactory, Collections.singleton(ACL_CACHE), false);
    }

    @Bean("redisCacheManagerAcl")
    @ConditionalOnProperty(value = ACL_CACHE_TYPE, havingValue = REDIS)
    public RedisCacheManager redisCacheManagerAcl(final RedisConnectionFactory connectionFactory) {
        return buildRedisCacheManager(connectionFactory, Collections.singleton(ACL_CACHE), useOptimizedParsing);
    }

    @Bean("redisConnectionFactory")
    @ConditionalOnProperty(value = CACHE_TYPE, havingValue = REDIS)
    @ConditionalOnMissingBean(RedisConnectionFactory.class)
    public RedisConnectionFactory redisConnectionFactoryPref() {
        return redisConnectionFactory();
    }

    @Bean("redisConnectionFactory")
    @ConditionalOnProperty(value = ACL_CACHE_TYPE, havingValue = REDIS)
    @ConditionalOnMissingBean(RedisConnectionFactory.class)
    public RedisConnectionFactory redisConnectionFactoryAcl() {
        return redisConnectionFactory();
    }

    private JedisConnectionFactory redisConnectionFactory() {
        final var poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(redisPoolConnections);
        poolConfig.setMaxIdle(redisPoolConnections);
        final var clientConfig = JedisClientConfiguration.builder()
                .connectTimeout(Duration.ofMillis(poolTimeout))
                .usePooling().poolConfig(poolConfig)
                .build();
        final var redisConfig = new RedisStandaloneConfiguration(redisHost, redisPort);
        return new JedisConnectionFactory(redisConfig, clientConfig);
    }

    private RedisCacheManager buildRedisCacheManager(final RedisConnectionFactory connectionFactory,
                                                     final Set<String> cacheNames,
                                                     final boolean useOptimizedParsing) {
        final var cacheConfiguration = buildRedisCacheConfiguration(useOptimizedParsing);
        final var cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory);
        return RedisCacheManager.builder(cacheWriter)
                .cacheDefaults(cacheConfiguration)
                .initialCacheNames(cacheNames)
                .build();
    }

    private RedisCacheConfiguration buildRedisCacheConfiguration(final boolean useOptimizedParsing) {
        if (!useOptimizedParsing) {
            return RedisCacheConfiguration.defaultCacheConfig()
                    .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(
                            new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                            new JdkSerializationRedisSerializer()));
        }
        final var jsonMapper = new JsonMapper();
        final var module = new SimpleModule();
        module.addDeserializer(AclImpl.class, new AclImplDeserializer());
        module.addSerializer(AclImpl.class, new AclImplSerializer());
        jsonMapper.registerModule(module);

        return RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(getSerializer(jsonMapper, String.class))
                .serializeValuesWith(getSerializer(jsonMapper, AclImpl.class));
    }

    private <T> RedisSerializationContext.SerializationPair<T> getSerializer(final JsonMapper jsonMapper,
                                                                             final Class<T> type) {
        return RedisSerializationContext.SerializationPair
                .fromSerializer(new JsonRedisSerializer<>(jsonMapper, type));
    }
}
