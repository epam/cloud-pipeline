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
import com.epam.pipeline.entity.preference.Preference;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.acls.domain.AclImpl;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Collections;
import java.util.Optional;

@EnableCaching
public class CacheConfiguration {

    public static final String PREFERENCE_CACHE = "preferences";
    public static final String ACL_CACHE = "aclCache";

    private static final String REDIS = "REDIS";
    private static final String MEMORY = "MEMORY";
    private static final String CACHE_TYPE = "cache.type";

    @Value("${cache.type:}")
    private String cacheType;

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
    public CacheManager cacheManager(final Optional<RedisCacheManager> redisCacheManagerPref) {
        switch (cacheType) {
            case MEMORY:
                return new ConcurrentMapCacheManager(PREFERENCE_CACHE);
            case REDIS:
                return redisCacheManagerPref
                        .orElseThrow(IllegalArgumentException::new);
            default:
                return new NoOpCacheManager();
        }
    }

    @Bean
    public CacheManager aclCacheManager(final Optional<RedisCacheManager> redisCacheManagerAcl) {
        switch (cacheType) {
            case MEMORY:
                return new ConcurrentMapCacheManager(ACL_CACHE);
            case REDIS:
                return redisCacheManagerAcl
                        .orElseThrow(IllegalArgumentException::new);
            default:
                return new NoOpCacheManager();
        }
    }

    @Bean
    @ConditionalOnProperty(value = CACHE_TYPE, havingValue = REDIS)
    public RedisCacheManager redisCacheManagerPref(final RedisTemplate<String, Preference> templatePreference) {
        return new RedisCacheManager(templatePreference, Collections.singleton(PREFERENCE_CACHE));
    }

    @Bean
    @ConditionalOnProperty(value = CACHE_TYPE, havingValue = REDIS)
    public RedisCacheManager redisCacheManagerAcl(final RedisTemplate<Object, AclImpl> templateACl) {
        return new RedisCacheManager(templateACl, Collections.singleton(ACL_CACHE));
    }

    @Bean
    @ConditionalOnProperty(value = CACHE_TYPE, havingValue = REDIS)
    public RedisConnectionFactory redisConnectionFactory() {
        final JedisConnectionFactory jedisConnectionFactory = new JedisConnectionFactory();
        jedisConnectionFactory.setHostName(redisHost);
        jedisConnectionFactory.setPort(redisPort);
        jedisConnectionFactory.setTimeout(poolTimeout);
        final JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(redisPoolConnections);
        poolConfig.setMaxIdle(redisPoolConnections);
        jedisConnectionFactory.setPoolConfig(poolConfig);
        return jedisConnectionFactory;
    }

    @Bean
    @ConditionalOnProperty(value = CACHE_TYPE, havingValue = REDIS)
    public RedisTemplate<String, Preference> templatePreference(final RedisConnectionFactory redisConnectionFactory) {
        final RedisTemplate<String, Preference> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        return redisTemplate;
    }

    @Bean
    @ConditionalOnProperty(value = CACHE_TYPE, havingValue = REDIS)
    public RedisTemplate<Object, AclImpl> templateACl(final RedisConnectionFactory redisConnectionFactory) {
        final RedisTemplate<Object, AclImpl> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        if (useOptimizedParsing) {
            final JsonMapper jsonMapper = new JsonMapper();
            final SimpleModule module = new SimpleModule();
            module.addDeserializer(AclImpl.class, new AclImplDeserializer());
            module.addSerializer(AclImpl.class, new AclImplSerializer());
            jsonMapper.registerModule(module);
            redisTemplate.setDefaultSerializer(new JsonRedisSerializer(jsonMapper));
        }
        return redisTemplate;
    }
}