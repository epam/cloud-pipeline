/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import static org.quartz.impl.StdSchedulerFactory.PROP_JOB_STORE_PREFIX;

import com.epam.pipeline.common.MessageHelper;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import com.epam.pipeline.manager.scheduling.AutowiringSpringBeanJobFactory;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.sql.DataSource;

@Configuration
@EnableScheduling
@EnableAsync
@ComponentScan(basePackages = {"com.epam.pipeline.dao",
        "com.epam.pipeline.manager",
        "com.epam.pipeline.security",
        "com.epam.pipeline.aspect",
        "com.epam.pipeline.event"})
@EnableSchedulerLock(interceptMode = EnableSchedulerLock.InterceptMode.PROXY_METHOD, defaultLockAtMostFor = "PT30S")
public class AppConfiguration implements SchedulingConfigurer {

    private static final int MAX_LOG_PAYLOAD_LENGTH = 1000;
    private static final String JOB_STORE_IS_CLUSTERED = ".isClustered";
    private static final String JOB_STORE_CLUSTER_CHECKIN_INTERVAL = ".clusterCheckinInterval";
    private static final String JOB_STORE_CLUSTER_MISFIRE_THRESHOLD = ".misfireThreshold";
    private static final String JOB_STORE_DRIVER_DELEGATE_CLASS = ".driverDelegateClass";

    @Value("${scheduled.pool.size:5}")
    private int scheduledPoolSize;

    @Value("${pause.pool.size:10}")
    private int pausePoolSize;

    @Value("${run.as.pool.size:5}")
    private int runAsPoolSize;

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
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setPoolSize(scheduledPoolSize); // For AbstractSchedulingManager's subclasses' tasks
        return scheduler;
    }

    @Bean
    @ConditionalOnProperty(name = "ha.deploy.enabled", havingValue = "false", matchIfMissing = true)
    public SchedulerFactoryBean schedulerFactoryBeanInMemory(final ApplicationContext applicationContext) {
        final SchedulerFactoryBean schedulerFactory = new SchedulerFactoryBean();
        final AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        schedulerFactory.setJobFactory(jobFactory);
        return schedulerFactory;
    }

    @Bean
    @ConditionalOnProperty(name = "ha.deploy.enabled", havingValue = "true")
    public SchedulerFactoryBean schedulerFactoryBeanPersistent(final ApplicationContext applicationContext,
                                                               final DataSource dataSource,
                                                               @Value("${scheduled.ha.checkin.interval:25000}")
                                                                   final String checkInInterval,
                                                               @Value("${scheduled.ha.misfire.threshold:60000}")
                                                                   final String misfireThreshold,
                                                               @Value("${scheduled.ha.db.driverDelegateClass}")
                                                                   final String driverDelegateClass) {
        final SchedulerFactoryBean schedulerFactory = new SchedulerFactoryBean();
        final AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        schedulerFactory.setJobFactory(jobFactory);
        schedulerFactory.setDataSource(dataSource);
        final Properties quartzProperties = new Properties();
        quartzProperties.setProperty(PROP_JOB_STORE_PREFIX + JOB_STORE_IS_CLUSTERED, "true");
        quartzProperties.setProperty(PROP_JOB_STORE_PREFIX + JOB_STORE_CLUSTER_CHECKIN_INTERVAL, checkInInterval);
        quartzProperties.setProperty(PROP_JOB_STORE_PREFIX + JOB_STORE_DRIVER_DELEGATE_CLASS, driverDelegateClass);
        quartzProperties.setProperty(PROP_JOB_STORE_PREFIX + JOB_STORE_CLUSTER_MISFIRE_THRESHOLD, misfireThreshold);
        quartzProperties.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_ID,
                                     StdSchedulerFactory.AUTO_GENERATE_INSTANCE_ID);
        schedulerFactory.setQuartzProperties(quartzProperties);
        return schedulerFactory;
    }

    @Bean
    public CommonsRequestLoggingFilter logFilter() {
        CommonsRequestLoggingFilter filter
                = new CommonsRequestLoggingFilter();
        filter.setIncludeClientInfo(true);
        filter.setIncludeQueryString(true);
        filter.setIncludePayload(true);
        filter.setMaxPayloadLength(MAX_LOG_PAYLOAD_LENGTH);
        filter.setIncludeHeaders(false);
        filter.setAfterMessagePrefix("REQUEST DATA : ");
        return filter;
    }

    @Bean
    public Executor notificationsExecutor() {
        return getSingleThreadExecutor("Notifications");
    }

    @Bean
    public Executor pauseRunExecutor() {
        return new DelegatingSecurityContextExecutor(getThreadPoolTaskExecutor("PauseRun", pausePoolSize));
    }

    @Bean
    public Executor dataStoragePathExecutor() {
        return getSingleThreadExecutor("PathExecutor");
    }

    @Bean(name = "lockProvider")
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource);
    }

    @Bean
    public Executor runAsExecutor() {
        return getThreadPoolTaskExecutor("runAsExecutor", runAsPoolSize);
    }

    private Executor getThreadPoolTaskExecutor(final String name, final int poolSize) {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setThreadNamePrefix(name);
        executor.initialize();
        return executor;
    }

    private Executor getSingleThreadExecutor(String name) {
        return Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName(name);
            return thread;
        });
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(taskScheduler());
    }
}
