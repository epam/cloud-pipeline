/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.EmbeddedValueResolver;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UserNotificationManagerTest {

    private static final String CLEANUP_PERIOD_PROPERTY = "scheduled.notifications.cleanup.sec";
    private static final long THIRTY_DAYS = 30;

    @Test
    public void cleanUpShouldRunOnceADayByDefault() throws NoSuchMethodException {
        assertEquals(String.valueOf(TimeUnit.DAYS.toMillis(1)), resolveCleanUpDelay(new MockEnvironment()));
    }

    @Test
    public void cleanUpPeriodShouldBeTakenInSeconds() throws NoSuchMethodException {
        final MockEnvironment environment = new MockEnvironment()
                .withProperty(CLEANUP_PERIOD_PROPERTY, String.valueOf(TimeUnit.HOURS.toSeconds(1)));

        assertEquals(String.valueOf(TimeUnit.HOURS.toMillis(1)), resolveCleanUpDelay(environment));
    }

    @Test
    public void cleanUpPeriodShouldNotOverflowForLongPeriods() throws NoSuchMethodException {
        final MockEnvironment environment = new MockEnvironment()
                .withProperty(CLEANUP_PERIOD_PROPERTY, String.valueOf(TimeUnit.DAYS.toSeconds(THIRTY_DAYS)));

        assertEquals(String.valueOf(TimeUnit.DAYS.toMillis(THIRTY_DAYS)), resolveCleanUpDelay(environment));
    }

    /**
     * Resolves the delay of the cleanup's schedule as the scheduling of Spring does: the property placeholders
     * first, then the expression; Spring reads the result in the schedule's time unit, milliseconds.
     */
    private String resolveCleanUpDelay(final ConfigurableEnvironment environment) throws NoSuchMethodException {
        final Scheduled scheduled = UserNotificationManager.class.getMethod("cleanUp").getAnnotation(Scheduled.class);
        assertEquals(TimeUnit.MILLISECONDS, scheduled.timeUnit());
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.setEnvironment(environment);
            context.refresh();
            return new EmbeddedValueResolver(context.getBeanFactory()).resolveStringValue(scheduled.fixedDelayString());
        }
    }
}
