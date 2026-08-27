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

package com.epam.pipeline.notifier.app;

import com.epam.pipeline.notifier.service.task.MicrosoftGraphAPINotificationManager;
import com.epam.pipeline.notifier.service.task.NotificationDLQAwareService;
import com.epam.pipeline.notifier.service.task.SMTPNotificationManager;
import com.epam.pipeline.notifier.service.task.UserNotificationManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableScheduling
@EnableAsync
@ComponentScan(basePackages = {"com.epam.pipeline.notifier"})
public class AppConfiguration {

    @Value("${submit.threads:1}")
    private int submitThreads;

    @Bean(name = "notificationThreadPool")
    public ExecutorService notificationThreadPool() {
        ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(submitThreads);
        pool.prestartAllCoreThreads();
        return pool;
    }

    @Bean(name = "userNotificationDlqAwareService")
    @ConditionalOnProperty(name = "notification.enable.ui", havingValue = "true")
    public NotificationDLQAwareService userNotificationDlqAwareService(
            final UserNotificationManager userNotificationManager) {
        return new NotificationDLQAwareService(userNotificationManager);
    }

    @Bean(name = "smtpNotificationDlqAwareService")
    @ConditionalOnProperty(name = "notification.enable.smtp", havingValue = "true")
    public NotificationDLQAwareService smtpNotificationDlqAwareService(
            final SMTPNotificationManager smtpNotificationManager) {
        return new NotificationDLQAwareService(smtpNotificationManager);
    }

    @Bean(name = "microsoftGraphAPINotificationDlqAwareService")
    @ConditionalOnProperty(name = "notification.enable.azure", havingValue = "true")
    public NotificationDLQAwareService microsoftGraphAPINotificationDlqAwareService(
            final MicrosoftGraphAPINotificationManager microsoftGraphAPINotificationManager) {
        return new NotificationDLQAwareService(microsoftGraphAPINotificationManager);
    }

}
