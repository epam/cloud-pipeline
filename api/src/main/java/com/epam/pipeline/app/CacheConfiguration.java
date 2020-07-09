/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import javax.swing.text.html.Option;
import java.util.Arrays;
import java.util.Optional;

@EnableCaching
public class CacheConfiguration {

    public static final String PREFERENCE_CACHE = "preferences";
    public static final String ACL_CACHE = "aclCache";

    @Value("${cache.type:}")
    private String cacheType;

    @Value("${redis.host:}")
    private String redisHost;

    @Value("${redis.port:0}")
    private int redisPort;

    @Bean
    @Primary
    public CacheManager cacheManager(final Optional<RedisCacheManager> redisCacheManager) {
        switch (cacheType) {
            case "MEMORY":
                return new ConcurrentMapCacheManager(PREFERENCE_CACHE, ACL_CACHE);
            case "REDIS":
                return redisCacheManager
                        .orElseThrow(IllegalArgumentException::new);
            default:
                return new NoOpCacheManager();
        }
    }

    @Bean
    @ConditionalOnProperty(value = "cache.type", havingValue = "REDIS")
    public RedisCacheManager redisCacheManager(final RedisTemplate template) {
        return new RedisCacheManager(template, Arrays.asList(PREFERENCE_CACHE, ACL_CACHE));
    }

    @Bean
    @ConditionalOnProperty(value = "cache.type", havingValue = "REDIS")
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(redisHost, redisPort);
    }

    @Bean
    @ConditionalOnProperty(value = "cache.type", havingValue = "REDIS")
    public RedisTemplate<Object, Object> redisTemplate(final RedisConnectionFactory redisConnectionFactory) {
        final RedisTemplate<Object, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        return redisTemplate;
    }
}
