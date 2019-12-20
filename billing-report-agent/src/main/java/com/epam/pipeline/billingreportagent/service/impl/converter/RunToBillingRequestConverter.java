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

package com.epam.pipeline.billingreportagent.service.impl.converter;

import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.model.PipelineRunBillingInfo;
import com.epam.pipeline.billingreportagent.model.ResourceType;
import com.epam.pipeline.billingreportagent.service.EntityMapper;
import com.epam.pipeline.billingreportagent.service.EntityToBillingRequestConverter;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.index.IndexRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@Slf4j
public class RunToBillingRequestConverter implements EntityToBillingRequestConverter<PipelineRun> {

    private final String indexPrefix;
    private final EntityMapper<PipelineRunBillingInfo> mapper;

    public RunToBillingRequestConverter(final String indexPrefix,
                                        final EntityMapper<PipelineRunBillingInfo> mapper) {
        this.indexPrefix = indexPrefix;
        this.mapper = mapper;
    }
    /**
     * Creates billing requests for given run
     * @param container Pipeline run to build request
     * @param indexName index to insert requests into
     * @param syncStart time point, where the whole synchronization process was started
     * @return list of requests to be performed (deletion index request if no billing requests created)
     */
    @Override
    public List<DocWriteRequest> convertEntityToRequests(final EntityContainer<PipelineRun> container,
                                                         final String indexName,
                                                         final LocalDateTime syncStart) {
            return convertRunToBillings(container, syncStart).stream()
                .map(billingInfo -> getDocWriteRequest(indexName, container, billingInfo))
                .collect(Collectors.toList());
    }

    protected List<PipelineRunBillingInfo> convertRunToBillings(final EntityContainer<PipelineRun> run,
                                                                final LocalDateTime syncStart) {
        final BigDecimal pricePerHour = run.getEntity().getPricePerHour();
        final List<RunStatus> statuses = run.getEntity().getRunStatuses();
        adjustStatuses(statuses, syncStart, run.getEntity().getId());
        final List<List<RunStatus>> activePeriods = calculateActivityPeriods(statuses);

        final Map<LocalDate, PipelineRunBillingInfo> reports = new HashMap<>();
        activePeriods.forEach(period -> {
            for (int i = 0; i < period.size() - 1; i++) {
                final LocalDateTime start = period.get(i).getTimestamp();
                final LocalDateTime end = period.get(i + 1).getTimestamp();
                final Long dailyCost = calculateCostsForPeriod(start, end, pricePerHour);
                addDailyCostReport(reports, start.toLocalDate(), dailyCost, ChronoUnit.MINUTES.between(start, end), run);
            }
        });
        return new ArrayList<>(reports.values());
    }

    private void adjustStatuses(final List<RunStatus> statuses, final LocalDateTime syncStart, final Long runId) {
        statuses.sort(Comparator.comparing(RunStatus::getTimestamp));
        final RunStatus lastStatus = statuses.get(statuses.size() - 1);
        if (lastStatus.getStatus() == TaskStatus.RUNNING) {
            statuses.add(new RunStatus(null, null, syncStart));
        } else if (statuses.size() > 1) {
            final RunStatus status = statuses.get(statuses.size() - 2);
            if (status.getStatus() != TaskStatus.RUNNING) {
                statuses.remove(statuses.size() - 1);
            }
        }
        if (statuses.size() % 2 != 0) { // no RUNNING states for given run
            log.warn("Can't adjusting run {} statuses, it has no RUNNING states in history!", runId);
            statuses.clear();
        }
    }

    private List<List<RunStatus>> calculateActivityPeriods(final List<RunStatus> statuses) {
        final List<List<RunStatus>> activePeriods = new ArrayList<>();
        for (int i = 0; i < statuses.size(); i += 2) {
            final RunStatus currentStatus = statuses.get(i);
            final RunStatus nextStatus = statuses.get(i + 1);
            final List<RunStatus> activePeriod = new ArrayList<>();
            activePeriod.add(currentStatus);
            LocalDateTime nextTimePoint = currentStatus.getTimestamp()
                .toLocalDate()
                .plusDays(1)
                .atTime(LocalTime.MIDNIGHT);
            while (nextTimePoint.isBefore(nextStatus.getTimestamp())) {
                activePeriod.add(new RunStatus(null, null, nextTimePoint));
                nextTimePoint = nextTimePoint.plusDays(1);
            }
            activePeriod.add(nextStatus);
            activePeriods.add(activePeriod);
        }
        return activePeriods;
    }

    private void addDailyCostReport(final Map<LocalDate, PipelineRunBillingInfo> reports,
                                    final LocalDate date,
                                    final Long cost,
                                    final Long duration,
                                    final EntityContainer<PipelineRun> run) {
        final PipelineRunBillingInfo billing = reports.get(date);
        if (billing != null) {
            final Long currentCost = billing.getCost();
            billing.setCost(currentCost + cost);
            reports.put(date, billing);
        } else {
            reports.put(date, new PipelineRunBillingInfo(date, run.getEntity(), cost, duration, ResourceType.COMPUTE));
        }
    }

    /**
     * Calculate cost, based on duration of period and hourly price
     * @param start timepoint describing start of evaluating period
     * @param end timepoint describing end of evaluating period
     * @param hourlyPrice price for evaluating resource
     * @return cost for the given period in cents
     */
    private Long calculateCostsForPeriod(final LocalDateTime start, final LocalDateTime end,
                                         final BigDecimal hourlyPrice) {
        final BigDecimal durationMins = BigDecimal.valueOf(Duration.between(start, end).get(ChronoUnit.SECONDS));
        return durationMins.multiply(hourlyPrice)
            .divide(BigDecimal.valueOf(3600), RoundingMode.CEILING)
            .unscaledValue()
            .longValue();
    }

    private DocWriteRequest getDocWriteRequest(final String periodIndex,
                                               final EntityContainer<PipelineRun> run,
                                               final PipelineRunBillingInfo billing) {
        final EntityContainer<PipelineRunBillingInfo> entity = EntityContainer.<PipelineRunBillingInfo>builder()
            .owner(run.getOwner())
            .entity(billing)
            .build();
        final String fullIndex = String.format("%s-%s", periodIndex, parseDateToString(billing.getDate()));
        return new IndexRequest(fullIndex, INDEX_TYPE).source(mapper.map(entity));
    }
}
