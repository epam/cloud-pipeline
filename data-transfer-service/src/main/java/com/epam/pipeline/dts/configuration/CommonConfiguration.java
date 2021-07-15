/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.dts.configuration;

import com.epam.pipeline.client.pipeline.CloudPipelineApiBuilder;
import com.epam.pipeline.dts.common.service.CloudPipelineAPIClient;
import com.epam.pipeline.dts.common.service.FileService;
import com.epam.pipeline.dts.common.service.impl.FileServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class CommonConfiguration {

    @Bean
    public FileService fileService() {
        return new FileServiceImpl();
    }

    //for async methods
    @Bean
    public Executor taskExecutor(@Value("${task.pool.size:10}") int taskPoolSize) {
        return new DelegatingSecurityContextExecutor(getThreadPoolTaskExecutor("Task", taskPoolSize));
    }

    //for async execution of autonomous transfer
    @Bean
    public Executor autonomousTransferExecutor(@Value("${task.local.pool.size:3}") int taskPoolSize) {
        return new DelegatingSecurityContextExecutor(getThreadPoolTaskExecutor("LocalTask", taskPoolSize));
    }

    //for scheduled methods (SubmissionMonitor)
    @Bean
    public TaskScheduler taskScheduler(@Value("${task.scheduled.pool.size:2}") int scheduledTasksPoolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setPoolSize(scheduledTasksPoolSize);
        return scheduler;
    }

    @Bean
    public CloudPipelineAPIClient apiClient(final @Value("${dts.api.url}") String apiUrl,
                                            final @Value("${dts.api.token}") String apiToken,
                                            final @Value("${dts.api.timeout.seconds}") int apiTimeoutInSeconds) {
        return CloudPipelineAPIClient.from(new CloudPipelineApiBuilder(apiTimeoutInSeconds, apiTimeoutInSeconds, apiUrl, apiToken)
                .buildClient());
    }

    private Executor getThreadPoolTaskExecutor(String name, int taskPoolSize) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(taskPoolSize);
        executor.setMaxPoolSize(taskPoolSize);
        executor.setThreadNamePrefix(name);
        executor.initialize();
        return executor;
    }
}
