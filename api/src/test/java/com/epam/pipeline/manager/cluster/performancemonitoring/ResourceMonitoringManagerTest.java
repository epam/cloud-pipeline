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

package com.epam.pipeline.manager.cluster.performancemonitoring;

import com.epam.pipeline.dao.monitoring.MonitoringESDao;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.cluster.performancemonitoring.monitor.RunMonitor;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ResourceMonitoringManagerTest {

    @Mock private PipelineRunManager pipelineRunManager;
    @Mock private MonitoringESDao monitoringESDao;
    @Mock private PreferenceManager preferenceManager;
    @Mock private RunMonitor firstMonitor;
    @Mock private RunMonitor secondMonitor;

    private ResourceMonitoringManager resourceMonitoringManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(firstMonitor.order()).thenReturn(0);
        when(secondMonitor.order()).thenReturn(1);
        resourceMonitoringManager = new ResourceMonitoringManager(
                pipelineRunManager, monitoringESDao, preferenceManager,
                Arrays.asList(secondMonitor, firstMonitor));
    }

    @Test
    public void testMonitorsCalledInOrderDefinedByOrderMethod() {
        final List<PipelineRun> runs = Collections.singletonList(new PipelineRun());
        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(runs);

        resourceMonitoringManager.monitorResourceUsage();

        // setUp passes secondMonitor(order=1) before firstMonitor(order=0) — after sorting, first must precede second
        final InOrder ordered = inOrder(firstMonitor, secondMonitor);
        ordered.verify(firstMonitor).monitor(runs);
        ordered.verify(secondMonitor).monitor(runs);
    }

    @Test
    public void testAllMonitorsCalledWithLoadedRuns() {
        final List<PipelineRun> runs = Collections.singletonList(new PipelineRun());
        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(runs);

        resourceMonitoringManager.monitorResourceUsage();

        verify(firstMonitor, times(1)).monitor(runs);
        verify(secondMonitor, times(1)).monitor(runs);
    }
}
