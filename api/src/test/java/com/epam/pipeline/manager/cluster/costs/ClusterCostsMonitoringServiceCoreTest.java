/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.manager.cluster.costs;

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ClusterCostsMonitoringServiceCoreTest {
    private static final Long MASTER_ID_1 = 1L;
    private static final Long WORKER_ID_11 = 11L;
    private static final Long WORKER_ID_12 = 12L;
    private static final Long WORKER_ID_13 = 13L;
    private static final Long MASTER_ID_2 = 2L;
    private static final Long WORKER_ID_21 = 21L;
    private static final double PRICE_1 = 1.95;
    private static final double PRICE_2 = 20.25;
    private static final double EXPECTED_PRICE_1 = 0.0975;
    private static final double EXPECTED_PRICE_2 = 0.675;

    private final PipelineRunCRUDService pipelineRunCRUDService = mock(PipelineRunCRUDService.class);
    private final PipelineRunManager pipelineRunManager = mock(PipelineRunManager.class);
    private final ClusterCostsMonitoringServiceCore clusterCostsMonitoringService =
            new ClusterCostsMonitoringServiceCore(pipelineRunCRUDService, pipelineRunManager);

    @Test
    public void shouldUpdateClusterPrices() {
        final PipelineRun master1 = masterRun(MASTER_ID_1, 2);
        final PipelineRun master2 = masterRun(MASTER_ID_2, 1);
        when(pipelineRunManager.loadRunningPipelineRuns()).thenReturn(Arrays.asList(master1, master2));

        final PipelineRun worker11 = workerRun(WORKER_ID_11, MASTER_ID_1, PRICE_1); // no start times
        final PipelineRun worker12 = workerRun(WORKER_ID_12, MASTER_ID_1, PRICE_1); // no instance start time
        final PipelineRun worker13 = workerRun(WORKER_ID_13, MASTER_ID_1, PRICE_1); // 3 min stopped
        worker12.setStartDate(buildDate(10));
        worker13.setStartDate(buildDate(10));
        worker13.getInstance().setStartDate(buildDate(5));
        worker13.setEndDate(buildDate(2));

        final PipelineRun worker21 = workerRun(WORKER_ID_21, MASTER_ID_2, PRICE_2); // 2 min running
        worker21.setStartDate(buildDate(10));
        worker21.getInstance().setStartDate(buildDate(2));

        when(pipelineRunCRUDService.loadRunsByParentRuns(any())).thenReturn(new HashMap<Long, List<PipelineRun>>() {
            {
                put(MASTER_ID_1, Arrays.asList(worker11, worker12, worker13));
                put(MASTER_ID_2, Collections.singletonList(worker21));
            }
        });

        clusterCostsMonitoringService.monitor();

        final ArgumentCaptor<Collection<PipelineRun>> runsCaptor = ArgumentCaptor.forClass((Class) Collection.class);
        verify(pipelineRunCRUDService).updateClusterPrices(runsCaptor.capture());
        assertThat(runsCaptor.getValue().size(), is(2));
        final Map<Long, PipelineRun> actualMastersById = runsCaptor.getValue().stream()
                .collect(Collectors.toMap(PipelineRun::getId, Function.identity()));
        final PipelineRun actualMaster1 = actualMastersById.get(MASTER_ID_1);
        final PipelineRun actualMaster2 = actualMastersById.get(MASTER_ID_2);
        assertThat(actualMaster1, notNullValue());
        assertThat(actualMaster2, notNullValue());
        assertThat(actualMaster1.getWorkersPrice().doubleValue(), is(EXPECTED_PRICE_1));
        assertThat(actualMaster2.getWorkersPrice().doubleValue(), is(EXPECTED_PRICE_2));
    }

    private PipelineRun masterRun(final Long id, final int nodeCount) {
        final PipelineRun pipelineRun = new PipelineRun();
        pipelineRun.setId(id);
        pipelineRun.setNodeCount(nodeCount);
        return pipelineRun;
    }

    private PipelineRun workerRun(final Long id, final Long parentRunId, final double price) {
        final PipelineRun pipelineRun = new PipelineRun();
        pipelineRun.setId(id);
        pipelineRun.setParentRunId(parentRunId);
        pipelineRun.setPricePerHour(BigDecimal.valueOf(price));
        pipelineRun.setInstance(new RunInstance());
        return pipelineRun;
    }

    private Date buildDate(final int minutesFromNow) {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(DateUtils.now());
        calendar.add(Calendar.MINUTE, -minutesFromNow);
        return calendar.getTime();
    }
}
