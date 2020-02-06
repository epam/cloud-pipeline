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

package com.epam.pipeline.manager.scheduling;

import com.epam.pipeline.entity.pipeline.run.RunSchedule;
import com.epam.pipeline.entity.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.Date;
import java.util.Properties;

import org.quartz.Scheduler;

import javax.annotation.PostConstruct;

@Slf4j
@Component
public class RunScheduler {

    private static final String JOB_EXECUTION_THREADS = "5";
    private static final String MAX_CONCURRENT_JOB_FIRING_AT_ONCE = "2";
    private static final String ID = " id: ";
    private final Scheduler quartzScheduler;

    @Autowired
    private ScheduleProviderManager scheduleProviderManager;

    @Autowired
    RunScheduler(final SchedulerFactoryBean schedulerFactoryBean) {
        final Properties quartsProperties = new Properties();
        quartsProperties.setProperty(SchedulerFactoryBean.PROP_THREAD_COUNT, JOB_EXECUTION_THREADS);
        quartsProperties.setProperty(StdSchedulerFactory.PROP_SCHED_MAX_BATCH_SIZE, MAX_CONCURRENT_JOB_FIRING_AT_ONCE);
        schedulerFactoryBean.setQuartzProperties(quartsProperties);
        this.quartzScheduler = schedulerFactoryBean.getScheduler();
    }

    @PostConstruct
    public void init() {
        try {
            quartzScheduler.start();
            log.debug("Scheduler is running.");
        } catch (SchedulerException e) {
            log.error("SchedulerException while scheduling process: " + e.getMessage());
        }
    }

    public void scheduleRunSchedule(final RunSchedule schedule) {
        try {
            log.debug("Request received to schedule action: " + schedule.getAction() + " for "
                    + schedule.getType() + ID + schedule.getSchedulableId());
            final JobDetail jobDetail = jobDetail(schedule);

            log.debug("Creating trigger for key " + jobDetail.getKey().getName() + " at date: " + DateUtils.now());
            final Trigger cronTrigger = createCronTrigger(schedule);

            final Date scheduledDate = quartzScheduler.scheduleJob(jobDetail, cronTrigger);
            log.debug("Job for: " + schedule.getType() + ID
                    + schedule.getSchedulableId() + " scheduled successfully for date: " + scheduledDate);
        } catch (SchedulerException | ParseException e) {
            log.error("SchedulerException while scheduling job for run " + schedule.getSchedulableId() + " : " +
                      e.getMessage());
        }
    }

    public void unscheduleRunSchedule(final RunSchedule schedule) {
        try {
            log.debug("Request received to unscheduling trigger for: " + schedule.getType()
                    + ID + schedule.getSchedulableId());

            final JobKey key = jobDetail(schedule).getKey();

            quartzScheduler.deleteJob(key);

            log.debug("Schedule " + schedule.getCronExpression() + " for "  + schedule.getType()
                    + ID + schedule.getSchedulableId() + " was revoked successfully.");
        } catch (SchedulerException e) {
            log.error("SchedulerException while unscheduling trigger for "  + schedule.getType()
                    + ID + schedule.getSchedulableId() + " : " + e.getMessage());
        }
    }

    private JobDetail jobDetail(final RunSchedule runSchedule) {
        JobDetailFactoryBean jobDetailFactory = new JobDetailFactoryBean();
        jobDetailFactory.setJobClass(scheduleProviderManager.getProvider(runSchedule.getType()).getScheduleJobClass());
        jobDetailFactory.getJobDataMap().put("SchedulableId", runSchedule.getSchedulableId());
        jobDetailFactory.getJobDataMap().put("User", runSchedule.getUser());
        jobDetailFactory.getJobDataMap().put("Action", runSchedule.getAction().name());
        jobDetailFactory.setName(String.format("run_%s-%s", runSchedule.getSchedulableId(), runSchedule.getId()));
        jobDetailFactory.setDescription("Invoke run schedule job service...");
        jobDetailFactory.setDurability(true);
        jobDetailFactory.afterPropertiesSet();
        return jobDetailFactory.getObject();
    }

    private Trigger createCronTrigger(final RunSchedule runSchedule) throws ParseException {
        CronTriggerFactoryBean trigger = new CronTriggerFactoryBean();
        trigger.setName(String.valueOf(runSchedule.getId()));
        trigger.setTimeZone(runSchedule.getTimeZone());
        trigger.setCronExpression(runSchedule.getCronExpression());
        trigger.setStartTime(getFirstExecutionFromCronExpression(runSchedule.getCronExpression()));
        trigger.afterPropertiesSet();
        return trigger.getObject();
    }

    private Date getFirstExecutionFromCronExpression(final String cronExpression) {
        try {
            CronExpression cron = new CronExpression(cronExpression);
            return cron.getNextValidTimeAfter(new Date(System.currentTimeMillis()));
        } catch (ParseException e) {
            throw new IllegalArgumentException(
                "Could not get first execution time for cron expression " + cronExpression, e);
        }
    }
}
