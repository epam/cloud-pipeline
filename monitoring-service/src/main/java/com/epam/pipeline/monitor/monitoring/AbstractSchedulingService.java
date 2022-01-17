/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.monitor.monitoring;

import com.epam.pipeline.monitor.rest.CloudPipelineAPIClient;
import com.epam.pipeline.monitor.service.preference.PreferencesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;

import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A base class for application components, that have a scheduled task
 */
@Slf4j
public abstract class AbstractSchedulingService {
    protected final TaskScheduler scheduler;
    protected final CloudPipelineAPIClient client;
    protected final PreferencesService preferencesService;

    protected final AtomicReference<ScheduledFuture<?>> scheduledFuture = new AtomicReference<>();

    public AbstractSchedulingService(final TaskScheduler scheduler, final CloudPipelineAPIClient client,
                                     final PreferencesService preferencesService) {
        this.scheduler = scheduler;
        this.client = client;
        this.preferencesService = preferencesService;
    }

    /**
     * Schedule a task with a fixed delay
     * @param task a task to schedule
     * @param rate the execution rate
     * @param taskName a name of the task
     */
    protected void scheduleFixedDelay(final Runnable task, final Integer rate, final String taskName) {
        if (Objects.isNull(rate)) {
            log.debug("Scheduling rate cannot be found for task '{}'", taskName);
            return;
        }
        log.info("Scheduled {} with a rate of {} ms", taskName, rate);
        scheduledFuture.set(scheduler.scheduleWithFixedDelay(task, rate));
    }

    /**
     * Schedule a task with a fixed delay
     * @param task a task to schedule
     * @param ratePreferenceName a preference name that contains execution rate
     * @param taskName a name of the task
     */
    protected void scheduleFixedDelay(final Runnable task, final String ratePreferenceName, final String taskName) {
        scheduleFixedDelay(task, client.getIntPreference(ratePreferenceName), taskName);
        preferencesService.getObservablePreference(ratePreferenceName)
                .subscribe(newRate -> rescheduleFixedDelay(task, taskName, Integer.parseInt(newRate)));
    }

    private void rescheduleFixedDelay(final Runnable task, final String taskName, final Integer rate) {
        scheduledFuture.updateAndGet(future -> {
            log.debug("Rescheduling {} with a new rate of {} ms", taskName, rate);
            future.cancel(false);
            return scheduler.scheduleWithFixedDelay(task, rate.longValue());
        });
    }
}
