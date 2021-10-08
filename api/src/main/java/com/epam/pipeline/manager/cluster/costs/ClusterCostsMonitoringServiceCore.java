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
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClusterCostsMonitoringServiceCore {

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

        workersByParent.forEach((parentId, workers) -> estimateClusterPrice(parentId, workers, masters));
        pipelineRunCRUDService.updateClusterPrices(masters.values());
        log.debug("Finished cluster costs monitoring");
    }

    private void estimateClusterPrice(final Long parentId, final List<PipelineRun> workers,
                                      final Map<Long, PipelineRun> masters) {
        final BigDecimal workersPrice = estimateWorkersPrice(workers);

        final PipelineRun master = masters.get(parentId);
        final BigDecimal masterPrice = estimatePriceForRun(master);

        master.setClusterPrice(masterPrice.add(workersPrice));
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
        final BigDecimal pricePerMinute = run.getPricePerHour().divide(BigDecimal.valueOf(60), RoundingMode.HALF_UP);
        return pricePerMinute.multiply(durationInMinutes(run));
    }

    private BigDecimal durationInMinutes(final PipelineRun run) {
        if (Objects.isNull(run.getStartDate())) {
            return BigDecimal.ZERO;
        }

        final Date pipelineEnd = Objects.isNull(run.getEndDate()) ? DateUtils.now() : run.getEndDate();
        final long runDurationMs = pipelineEnd.getTime() - run.getStartDate().getTime();
        return new BigDecimal(TimeUnit.MINUTES.convert(runDurationMs, TimeUnit.MILLISECONDS));
    }
}
