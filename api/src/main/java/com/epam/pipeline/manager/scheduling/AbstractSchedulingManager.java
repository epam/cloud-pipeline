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

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import com.epam.pipeline.manager.preference.AbstractSystemPreference.IntPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;

/**
 * A base class for application components, that have a scheduled task
 */
public abstract class AbstractSchedulingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractSchedulingManager.class);

    @Autowired
    protected TaskScheduler scheduler;

    @Autowired
    protected PreferenceManager preferenceManager;

    protected AtomicReference<ScheduledFuture<?>> scheduledFuture = new AtomicReference<>();

    /**
     * Schedule a task with a fixed delay, that is being fetched from a AbstractSystemPreference
     * @param task a task to schedule
     * @param delayPreference a preference, that contains execution rate
     * @param taskName a name of the task
     */
    protected void scheduleFixedDelay(Runnable task, IntPreference delayPreference, String taskName) {
        Integer statusUpdateRate = preferenceManager.getPreference(delayPreference);
        LOGGER.info("Scheduled {} with a rate of {} ms", taskName, statusUpdateRate);

        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(task, statusUpdateRate);
        scheduledFuture.set(future);

        preferenceManager.getObservablePreference(delayPreference)
            .subscribe(rate -> scheduledFuture.updateAndGet(f -> {
                LOGGER.debug("Rescheduling {} with a new rate of {} ms", taskName, rate);
                f.cancel(false);
                return scheduler.scheduleWithFixedDelay(task, rate.longValue());
            }));
    }
}
