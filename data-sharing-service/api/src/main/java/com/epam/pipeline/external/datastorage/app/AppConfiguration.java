/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.external.datastorage.app;

import com.epam.pipeline.client.pipeline.CloudPipelineApiExecutor;
import com.epam.pipeline.client.pipeline.RetryingCloudPipelineApiExecutor;
import com.epam.pipeline.external.datastorage.manager.CloudPipelineApiBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

import com.epam.pipeline.external.datastorage.message.MessageHelper;

import java.time.Duration;

@Configuration
@ComponentScan(basePackages = { "com.epam.pipeline.external.datastorage.manager" })
public class AppConfiguration {
    @Bean
    public MessageHelper messageHelper() {
        return new MessageHelper(messageSource());
    }

    @Bean
    public ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }

    @Bean
    public CloudPipelineApiBuilder cloudPipelineApiBuilder(
        @Value("${pipeline.api.base.url}") final String pipelineBaseUrl,
        @Value("${pipeline.client.connect.timeout}") final long connectTimeout,
        @Value("${pipeline.client.read.timeout}") final long readTimeout) {
        return new CloudPipelineApiBuilder(connectTimeout, readTimeout, pipelineBaseUrl);
    }

    @Bean
    public CloudPipelineApiExecutor cloudPipelineApiExecutor(
            @Value("${cloud.pipeline.retry.attempts:3}") int retryAttempts,
            @Value("${cloud.pipeline.retry.delay:5000}") int retryDelay) {
        return new RetryingCloudPipelineApiExecutor(retryAttempts, Duration.ofMillis(retryDelay));
    }
}
