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

package com.epam.pipeline.manager.scheduling;

import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import com.epam.pipeline.manager.preference.AbstractSystemPreference.StringPreference;
import com.epam.pipeline.manager.preference.AbstractSystemPreference.IntPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;

/**
 * A base class for application components, that have a scheduled task
 */
@Slf4j
public abstract class AbstractSchedulingManager {

    @Autowired
    protected TaskScheduler scheduler;

    @Autowired
    protected PreferenceManager preferenceManager;

    @Autowired
    protected AuthManager authManager;

    protected final AtomicReference<ScheduledFuture<?>> scheduledFuture = new AtomicReference<>();

    /**
     * Schedule a task with a fixed delay, that is being fetched from a AbstractSystemPreference
     * @param task a task to schedule
     * @param delayPreference a preference, that contains execution rate
     * @param taskName a name of the task
     */
    protected void scheduleFixedDelay(Runnable task, IntPreference delayPreference, String taskName) {
        Integer statusUpdateRate = preferenceManager.getPreference(delayPreference);
        log.info("Scheduled {} with a rate of {} ms", taskName, statusUpdateRate);

        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(task, statusUpdateRate);
        scheduledFuture.set(future);

        preferenceManager.getObservablePreference(delayPreference)
            .subscribe(rate -> scheduledFuture.updateAndGet(f -> {
                log.debug("Rescheduling {} with a new rate of {} ms", taskName, rate);
                f.cancel(false);
                return scheduler.scheduleWithFixedDelay(task, rate.longValue());
            }));
    }

    /**
     * Schedule a task with a fixed delay, that is being fetched from an AbstractSystemPreference.
     *
     * A task will be executed in a security context of a default admin.
     *
     * @param task            a task to schedule.
     * @param delayPreference a preference containing execution rate.
     * @param taskName        a name of the task.
     */
    protected void scheduleFixedDelaySecured(final Runnable task,
                                             final IntPreference delayPreference,
                                             final String taskName) {
        final DelegatingSecurityContextRunnable secureRunnable = new DelegatingSecurityContextRunnable(task,
                authManager.createSchedulerSecurityContext());
        final Integer rate = Optional.ofNullable(preferenceManager.getPreference(delayPreference)).orElse(0);

        log.info("Scheduled {} at {}", taskName, rate);
        scheduledFuture.set(scheduler.scheduleWithFixedDelay(secureRunnable, rate));
        preferenceManager.getObservablePreference(delayPreference)
                .subscribe(newRate -> scheduledFuture.updateAndGet(f -> {
                    log.info("Rescheduling {} at {}", taskName, newRate);
                    f.cancel(false);
                    return scheduler.scheduleWithFixedDelay(secureRunnable, newRate);
                }));
    }

    /**
     * Schedule a task with a cron delay, that is being fetched from an AbstractSystemPreference.
     *
     * A task will be executed in a security context of a default admin.
     *
     * @param task           a task to schedule.
     * @param cronPreference a preference containing cron expression.
     * @param taskName       a name of the task.
     */
    protected void scheduleSecured(final Runnable task,
                                   final StringPreference cronPreference,
                                   final String taskName) {
        final DelegatingSecurityContextRunnable secureRunnable = new DelegatingSecurityContextRunnable(task,
                authManager.createSchedulerSecurityContext());
        final String cron = Optional.ofNullable(preferenceManager.getPreference(cronPreference)).orElse("");

        log.info("Scheduled {} at {}", taskName, cron);
        scheduledFuture.set(scheduler.schedule(secureRunnable, new CronTrigger(cron)));
        preferenceManager.getObservablePreference(cronPreference)
                .subscribe(newCron -> scheduledFuture.updateAndGet(f -> {
                    log.info("Rescheduling {} at {}", taskName, newCron);
                    f.cancel(false);
                    return scheduler.schedule(secureRunnable, new CronTrigger(newCron));
                }));
    }
}
