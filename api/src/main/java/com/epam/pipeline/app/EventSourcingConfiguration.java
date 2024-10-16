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

import com.epam.pipeline.eventsourcing.EventEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(value = "event.sourcing.enabled", havingValue = "true")
@ComponentScan(basePackages = {"com.epam.pipeline.eventsourcing"})
public class EventSourcingConfiguration {

    @Value("${event.sourcing.redis.host:}")
    private String redisHost;

    @Value("${event.sourcing.redis.port:}")
    private Integer redisPort;

    @Value("${event.sourcing.scheduler.threads:2}")
    private Integer schedulerThreads;

    @Value("${event.sourcing.redisson.netty.threads:2}")
    private Integer redissonNettyThreads;

    @Value("${event.sourcing.redisson.threads:2}")
    private Integer redissonThreads;

    @Bean
    public EventEngine eventSourcingEngine() {
        return new EventEngine(redisHost, redisPort, schedulerThreads, redissonThreads, redissonNettyThreads);
    }

}
