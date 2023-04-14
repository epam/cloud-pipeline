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
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.utils.RunDurationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClusterCostsMonitoringServiceCore {
    private static final int DIVIDE_SCALE = 5;
    private static final int MINUTES_IN_HOUR = 60;

    private final PipelineRunCRUDService pipelineRunCRUDService;
    private final PipelineRunManager pipelineRunManager;

    @SchedulerLock(name = "ClusterCostsMonitoringService_monitor", lockAtMostForString = "PT10M")
    public void monitor() {
        log.debug("Started cluster costs monitoring");
        final Map<Long, PipelineRun> masters = ListUtils.emptyIfNull(pipelineRunManager
                .loadRunningPipelineRuns()).stream()
                .filter(PipelineRun::isMasterRun)
                .collect(Collectors.toMap(PipelineRun::getId, Function.identity()));
        log.debug("Found '{}' running master runs", masters.size());

        final Map<Long, List<PipelineRun>> workersByParent = pipelineRunCRUDService
                .loadRunsByParentRuns(masters.keySet());

        workersByParent.forEach((parentId, workers) -> estimateClusterPrice(workers, masters.get(parentId)));
        pipelineRunCRUDService.updateClusterPrices(masters.values());
        log.debug("Finished cluster costs monitoring");
    }

    private void estimateClusterPrice(final List<PipelineRun> workers, final PipelineRun master) {
        final BigDecimal workersPrice = estimateWorkersPrice(workers);
        master.setWorkersPrice(workersPrice);
    }

    private BigDecimal estimateWorkersPrice(final List<PipelineRun> workers) {
        return workers.stream()
                .map(this::estimatePriceForRun)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal estimatePriceForRun(final PipelineRun run) {
        if (Objects.isNull(run.getPricePerHour())) {
            return BigDecimal.ZERO;
        }
        return run.getPricePerHour()
                .multiply(BigDecimal.valueOf(RunDurationUtils.getBillableDuration(run).toMinutes()))
                .divide(BigDecimal.valueOf(MINUTES_IN_HOUR), DIVIDE_SCALE, RoundingMode.HALF_UP);
    }
}
