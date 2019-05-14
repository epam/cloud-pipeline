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

package com.epam.pipeline.vmmonitor;

import com.epam.pipeline.vmmonitor.service.CloudPipelineAPIClient;
import com.epam.pipeline.vmmonitor.service.NotificationSender;
import com.epam.pipeline.vmmonitor.service.impl.ApiNotificationSender;
import com.epam.pipeline.vmmonitor.service.impl.SMTPConfiguration;
import com.epam.pipeline.vmmonitor.service.impl.SMTPNotificationSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootApplication
@EnableScheduling
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class})
public class VMMonitorApplication {

    private static final int SCHEDULED_TASKS_POOL_SIZE = 1;

    @Value(value = "${email.notification.retry.count:3}")
    private int notifyRetryCount;

    @Value(value = "${email.smtp.server.host.name:}")
    private String smtpServerHostName;

    @Value(value = "${email.smtp.port:0}")
    private int smtpPort;

    @Value(value = "${email.ssl.on.connect:false}")
    private boolean sslOnConnect;

    @Value(value = "${email.start.tls.enabled:false}")
    private boolean startTlsEnabled;

    @Value(value = "${email.from:}")
    private String emailFrom;

    @Value(value = "${email.user:}")
    private String username;

    @Value(value = "${email.password:}")
    private String password;

    @Value(value = "${email.notification.letter.delay:-1}")
    private long emailDelay;

    @Value(value = "${email.notification.retry.delay:-1}")
    private long retryDelay;


    public static void main(String[] args) {
        SpringApplication.run(VMMonitorApplication.class, args);
    }


    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setPoolSize(SCHEDULED_TASKS_POOL_SIZE);
        return scheduler;
    }

    @Bean
    public NotificationSender notificationSender(
            @Value("${notification.send-via-api:false}") final boolean useApi,
            final CloudPipelineAPIClient apiClient) {
        return useApi ? new ApiNotificationSender(apiClient) : new SMTPNotificationSender(apiClient, buildSmtpConfig());
    }

    private SMTPConfiguration buildSmtpConfig() {
        return SMTPConfiguration.builder()
                .notifyRetryCount(notifyRetryCount)
                .smtpServerHostName(smtpServerHostName)
                .smtpPort(smtpPort)
                .sslOnConnect(sslOnConnect)
                .startTlsEnabled(startTlsEnabled)
                .emailFrom(emailFrom)
                .username(username)
                .password(password)
                .emailDelay(emailDelay)
                .retryDelay(retryDelay)
                .build();
    }

}
