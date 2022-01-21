/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
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

package com.epam.pipeline.monitor.config;

import com.epam.pipeline.client.pipeline.CloudPipelineApiExecutor;
import com.epam.pipeline.client.pipeline.RetryingCloudPipelineApiExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

@Configuration
@EnableScheduling
@EnableAsync
public class AppConfiguration implements SchedulingConfigurer {

    @Value("${scheduled.pool.size:5}")
    private int scheduledPoolSize;

    @Bean
    public TaskScheduler taskScheduler() {
        final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setPoolSize(scheduledPoolSize);
        return scheduler;
    }

    @Bean
    public CloudPipelineApiExecutor cloudPipelineApiExecutor(
            @Value("${cloud.pipeline.retry.attempts:3}") int retryAttempts,
            @Value("${cloud.pipeline.retry.delay:5000}") int retryDelay) {
        return new RetryingCloudPipelineApiExecutor(retryAttempts, Duration.ofMillis(retryDelay));
    }

    @Override
    public void configureTasks(final ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(taskScheduler());
    }
}
