/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.release.notes.agent;

import com.epam.pipeline.client.pipeline.CloudPipelineApiBuilder;
import com.epam.pipeline.client.pipeline.CloudPipelineApiExecutor;
import com.epam.pipeline.client.pipeline.RetryingCloudPipelineApiExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ComponentScan(basePackages = { "com.epam.release.notes.agent.service" })
public class AppConfiguration {

    @Bean
    public CloudPipelineApiExecutor cloudPipelineApiExecutor(
            @Value("${cloud.pipeline.retry.attempts:3}") int retryAttempts,
            @Value("${cloud.pipeline.retry.delay:5000}") int retryDelay) {
        return new RetryingCloudPipelineApiExecutor(retryAttempts, Duration.ofMillis(retryDelay));
    }

    @Bean
    public CloudPipelineApiBuilder cloudPipelineApiBuilder(
            @Value("${pipeline.api.url}") final String pipelineApiUrl,
            @Value("${pipeline.api.token}") final String pipelineApiToken,
            @Value("${pipeline.client.connect.timeout}") final int connectTimeout,
            @Value("${pipeline.client.read.timeout}") final int readTimeout) {
        return new CloudPipelineApiBuilder(connectTimeout, readTimeout, pipelineApiUrl, pipelineApiToken);
    }
}
