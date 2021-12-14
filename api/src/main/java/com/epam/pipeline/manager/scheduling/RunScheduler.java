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
import com.epam.pipeline.exception.scheduling.SchedulingException;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.text.ParseException;
import java.util.Date;

@Slf4j
@Component
public class RunScheduler {

    private static final String ID = " id: ";

    private final Scheduler quartzScheduler;
    private final ScheduleProviderManager scheduleProviderManager;

    @Autowired
    public RunScheduler(final SchedulerFactoryBean schedulerFactoryBean,
                        final ScheduleProviderManager scheduleProviderManager) {
        this.quartzScheduler = schedulerFactoryBean.getScheduler();
        this.scheduleProviderManager = scheduleProviderManager;
    }

    @PostConstruct
    public void init() {
        try {
            quartzScheduler.start();
            log.debug("Quartz scheduler is running.");
        } catch (SchedulerException e) {
            throw new SchedulingException("Error while initiating quartz scheduler", e);
        }
    }

    public void scheduleRunScheduleIfPossible(final RunSchedule schedule) {
        try {
            scheduleRunSchedule(schedule);
        } catch (SchedulingException e) {
            log.error("Error while scheduling job for run {}: {}", schedule.getSchedulableId(), e.getMessage());
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
            throw new SchedulingException(e.getMessage(), e);
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
            throw new SchedulingException(e.getMessage(), e);
        }
    }

    private JobDetail jobDetail(final RunSchedule runSchedule) {
        JobDetailFactoryBean jobDetailFactory = new JobDetailFactoryBean();
        jobDetailFactory.setJobClass(scheduleProviderManager.getProvider(runSchedule.getType()).getScheduleJobClass());
        jobDetailFactory.getJobDataMap().put("SchedulableId", runSchedule.getSchedulableId());
        jobDetailFactory.getJobDataMap().put("User", runSchedule.getUser());
        jobDetailFactory.getJobDataMap().put("Action", runSchedule.getAction().name());
        jobDetailFactory.setName(String.format("%s_%s-%s", runSchedule.getType(),
                runSchedule.getSchedulableId(), runSchedule.getId()));
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
        trigger.afterPropertiesSet();
        return trigger.getObject();
    }
}
