/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster.performancemonitoring.monitor;

import com.epam.pipeline.entity.pipeline.StopServerlessRun;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.StopServerlessRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
public class ServerlessRunMonitorTest {

    private static final long RUN_ID = 42L;
    private static final int GLOBAL_TIMEOUT_MIN = 30;

    @Mock private PipelineRunManager pipelineRunManager;
    @Mock private StopServerlessRunManager stopServerlessRunManager;
    @Mock private PreferenceManager preferenceManager;

    private ServerlessRunMonitor monitor;

    @BeforeEach
    public void setUp() {
        when(preferenceManager.getPreference(SystemPreferences.LAUNCH_SERVERLESS_STOP_TIMEOUT))
                .thenReturn(GLOBAL_TIMEOUT_MIN);
        monitor = new ServerlessRunMonitor(pipelineRunManager, stopServerlessRunManager, preferenceManager);
    }

    @Test
    public void testNoActiveServerlessRunsDoesNothing() {
        when(stopServerlessRunManager.loadActiveServerlessRuns()).thenReturn(Collections.emptyList());

        monitor.monitor(Collections.emptyList());

        verify(pipelineRunManager, never()).stopServerlessRun(any());
    }

    @Test
    public void testRunNotExpiredNotStopped() {
        final StopServerlessRun run = StopServerlessRun.builder()
                .runId(RUN_ID)
                .lastUpdate(LocalDateTime.now().minusMinutes(GLOBAL_TIMEOUT_MIN - 1))
                .build();
        when(stopServerlessRunManager.loadActiveServerlessRuns()).thenReturn(Collections.singletonList(run));

        monitor.monitor(Collections.emptyList());

        verify(pipelineRunManager, never()).stopServerlessRun(any());
    }

    @Test
    public void testExpiredRunStoppedWithGlobalTimeout() {
        final StopServerlessRun run = StopServerlessRun.builder()
                .runId(RUN_ID)
                .lastUpdate(LocalDateTime.now().minusMinutes(GLOBAL_TIMEOUT_MIN + 1))
                .build();
        when(stopServerlessRunManager.loadActiveServerlessRuns()).thenReturn(Collections.singletonList(run));

        monitor.monitor(Collections.emptyList());

        verify(pipelineRunManager).stopServerlessRun(RUN_ID);
    }

    @Test
    public void testExpiredRunStoppedWithPerRunTimeout() {
        final long perRunTimeout = 10L;
        final StopServerlessRun run = StopServerlessRun.builder()
                .runId(RUN_ID)
                .stopAfter(perRunTimeout)
                .lastUpdate(LocalDateTime.now().minusMinutes(perRunTimeout + 1))
                .build();
        when(stopServerlessRunManager.loadActiveServerlessRuns()).thenReturn(Collections.singletonList(run));

        monitor.monitor(Collections.emptyList());

        verify(pipelineRunManager).stopServerlessRun(RUN_ID);
    }

    @Test
    public void testPerRunTimeoutNotYetElapsed() {
        final long perRunTimeout = GLOBAL_TIMEOUT_MIN * 2L;
        final StopServerlessRun run = StopServerlessRun.builder()
                .runId(RUN_ID)
                .stopAfter(perRunTimeout)
                .lastUpdate(LocalDateTime.now().minusMinutes(GLOBAL_TIMEOUT_MIN + 1))
                .build();
        when(stopServerlessRunManager.loadActiveServerlessRuns()).thenReturn(Collections.singletonList(run));

        monitor.monitor(Collections.emptyList());

        verify(pipelineRunManager, never()).stopServerlessRun(any());
    }
}
