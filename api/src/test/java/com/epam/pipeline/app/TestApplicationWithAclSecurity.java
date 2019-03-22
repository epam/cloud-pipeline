/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.app;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.manager.cluster.InstanceOfferScheduler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.autoconfigure.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.SecurityFilterAutoConfiguration;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.acls.domain.SidRetrievalStrategyImpl;
import org.springframework.security.acls.model.SidRetrievalStrategy;
import org.springframework.test.context.TestPropertySource;

@SpringBootConfiguration
@Import({
        AppMVCConfiguration.class,
        DBConfiguration.class,
        TestAclSecurityConfig.class
    })
@EnableAutoConfiguration(exclude = {
    SecurityAutoConfiguration.class,
    ManagementWebSecurityAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class})
@ComponentScan(
    basePackages = {"com.epam.pipeline.dao", "com.epam.pipeline.manager",  "com.epam.pipeline.security"},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern="com.epam.pipeline.manager.security.Test*"),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = InstanceOfferScheduler.class)
    })
@TestPropertySource(value={"classpath:test-application.properties"})
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class TestApplicationWithAclSecurity {
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

    @Bean
    public EmbeddedServletContainerCustomizer containerCustomizer() {

        return container -> {
            if(container instanceof TomcatEmbeddedServletContainerFactory) {
                TomcatEmbeddedServletContainerFactory containerFactory =
                    (TomcatEmbeddedServletContainerFactory) container;
                containerFactory.addConnectorCustomizers((connector -> connector.setSecure(false)));
            }
        };
    }

    @Bean
    public SidRetrievalStrategy sidRetrievalStrategy() {
        return new SidRetrievalStrategyImpl();
    }

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
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        return scheduler;
    }

}
