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

package com.epam.pipeline.manager.scheduling;

import com.epam.pipeline.manager.preference.AbstractSystemPreference.IntPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import io.reactivex.subjects.PublishSubject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class AbstractSchedulingManagerTest {

    private static final IntPreference DELAY = new IntPreference("test.delay", 1000, "TEST", (v, d) -> true);
    private static final int POSITIVE_DELAY = 1000;
    private static final int NEW_POSITIVE_DELAY = 2000;
    private static final String TASK_NAME = "test task";

    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final PublishSubject<Integer> delayChanges = PublishSubject.create();
    private final ScheduledFuture<?> future = mock(ScheduledFuture.class);
    private final Runnable task = () -> { };
    private final TestSchedulingManager manager = new TestSchedulingManager();

    @BeforeEach
    public void setUp() {
        manager.scheduler = scheduler;
        manager.preferenceManager = preferenceManager;
        doReturn(delayChanges).when(preferenceManager).getObservablePreference(DELAY);
        doReturn(future).when(scheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
    }

    @Test
    public void shouldScheduleWithAPositiveDelay() {
        doReturn(POSITIVE_DELAY).when(preferenceManager).getPreference(DELAY);

        manager.scheduleFixedDelay(task, DELAY, TASK_NAME);

        verify(scheduler).scheduleWithFixedDelay(task, Duration.ofMillis(POSITIVE_DELAY));
        assertThat((Object) manager.scheduledFuture.get()).isSameAs(future);
    }

    @Test
    public void shouldNotScheduleWithANegativeOrZeroDelay() {
        doReturn(-1).when(preferenceManager).getPreference(DELAY);

        manager.scheduleFixedDelay(task, DELAY, TASK_NAME);

        verify(scheduler, never()).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        assertThat((Object) manager.scheduledFuture.get()).isNull();
    }

    @Test
    public void shouldStopTheTaskWhenTheDelayChangesToANegativeValue() {
        doReturn(POSITIVE_DELAY).when(preferenceManager).getPreference(DELAY);
        manager.scheduleFixedDelay(task, DELAY, TASK_NAME);

        delayChanges.onNext(-1);

        verify(future).cancel(false);
        assertThat((Object) manager.scheduledFuture.get()).isNull();
    }

    @Test
    public void shouldStartTheTaskWhenTheDelayChangesFromANegativeValue() {
        doReturn(-1).when(preferenceManager).getPreference(DELAY);
        manager.scheduleFixedDelay(task, DELAY, TASK_NAME);

        delayChanges.onNext(NEW_POSITIVE_DELAY);

        verify(scheduler).scheduleWithFixedDelay(eq(task), eq(Duration.ofMillis(NEW_POSITIVE_DELAY)));
        assertThat((Object) manager.scheduledFuture.get()).isSameAs(future);
    }

    private static final class TestSchedulingManager extends AbstractSchedulingManager {
    }
}
