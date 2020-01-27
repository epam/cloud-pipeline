/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.billingreportagent.model.billing.PipelineRunBillingInfo;
import com.epam.pipeline.billingreportagent.service.EntityMapper;
import com.epam.pipeline.billingreportagent.service.EntityToBillingRequestConverter;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.index.IndexRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
@Slf4j
@RequiredArgsConstructor
public class RunToBillingRequestConverter implements EntityToBillingRequestConverter<PipelineRun> {

    private final EntityMapper<PipelineRunBillingInfo> mapper;

    /**
     * Creates billing requests for given run
     *
     * @param runContainer Pipeline run to build request
     * @param indexPrefix common billing prefix for index to insert requests into
     * @param syncStart time point, where the whole synchronization process was started
     * @return list of requests to be performed (deletion index request if no billing requests created)
     */
    @Override
    public List<DocWriteRequest> convertEntityToRequests(final EntityContainer<PipelineRun> runContainer,
                                                         final String indexPrefix,
                                                         final LocalDateTime previousSync,
                                                         final LocalDateTime syncStart) {
        return convertRunToBillings(runContainer, previousSync, syncStart).stream()
            .map(billingInfo -> getDocWriteRequest(indexPrefix, runContainer.getOwner(), billingInfo))
            .collect(Collectors.toList());
    }

    protected Collection<PipelineRunBillingInfo> convertRunToBillings(final EntityContainer<PipelineRun> runContainer,
                                                                      final LocalDateTime previousSync,
                                                                      final LocalDateTime syncStart) {
        final BigDecimal pricePerHour = runContainer.getEntity().getPricePerHour();
        final List<RunStatus> statuses = adjustStatuses(runContainer.getEntity().getRunStatuses(),
                                                        previousSync,
                                                        syncStart);

        return createBillingsForPeriod(runContainer.getEntity(), pricePerHour, statuses).stream()
            .filter(billing -> !billing.getCost().equals(0L))
            .collect(Collectors.toMap(PipelineRunBillingInfo::getDate,
                                      Function.identity(),
                                      this::mergeBillings))
            .values();
    }

    private List<RunStatus> adjustStatuses(final List<RunStatus> statuses,
                                           final LocalDateTime previousSync,
                                           final LocalDateTime syncStart) {
        if (CollectionUtils.isNotEmpty(statuses)) {
            statuses.sort(Comparator.comparing(RunStatus::getTimestamp));
            final RunStatus lastStatus = statuses.get(statuses.size() - 1);
            if (TaskStatus.RUNNING.equals(lastStatus.getStatus())) {
                statuses
                    .add(new RunStatus(null, null, lastStatus.getTimestamp().plusDays(1).toLocalDate().atStartOfDay()));
            }
        } else {
            return Arrays.asList(
                new RunStatus(null, TaskStatus.RUNNING, previousSync.toLocalDate().atStartOfDay()),
                new RunStatus(null, null, syncStart.toLocalDate().atStartOfDay()));
        }
        return statuses;
    }

    private List<PipelineRunBillingInfo> createBillingsForPeriod(final PipelineRun run,
                                                                 final BigDecimal pricePerHour,
                                                                 final List<RunStatus> statuses) {
        final List<PipelineRunBillingInfo> billings = new ArrayList<>();
        boolean isPreviousActive = false;
        LocalDateTime periodStart = null;
        for (final RunStatus current : statuses) {
            final boolean isCurrentActive = TaskStatus.RUNNING.equals(current.getStatus());
            if (isCurrentActive
                && !isPreviousActive) {
                periodStart = current.getTimestamp();
                isPreviousActive = true;
            } else if (!isCurrentActive
                       && isPreviousActive) {
                isPreviousActive = false;
                billings.addAll(createRunBillingsForActivePeriod(periodStart, current.getTimestamp(),
                                                                 run, pricePerHour));
            }
        }
        return billings;
    }

    private PipelineRunBillingInfo mergeBillings(final PipelineRunBillingInfo billing1,
                                                 final PipelineRunBillingInfo billing2) {
        billing1.setCost(billing1.getCost() + billing2.getCost());
        billing1.setUsageMinutes(billing1.getUsageMinutes() + billing2.getUsageMinutes());
        return billing1;
    }

    private List<PipelineRunBillingInfo> createRunBillingsForActivePeriod(final LocalDateTime start,
                                                                          final LocalDateTime end,
                                                                          final PipelineRun run,
                                                                          final BigDecimal pricePerHour) {
        final List<LocalDateTime> timePoints = new ArrayList<>();
        final List<PipelineRunBillingInfo> billings = new ArrayList<>();
        timePoints.add(start);
        final LocalDate startDate = start.toLocalDate();
        for (long i = 0; i < ChronoUnit.DAYS.between(startDate, end.toLocalDate()); i++) {
            timePoints.add(startDate.plusDays(1).atStartOfDay());
        }
        timePoints.add(end);
        for (int i = 0; i < timePoints.size() - 1; i++) {
            final Duration durationSeconds = Duration.between(timePoints.get(i), timePoints.get(i + 1));
            final Long cost = calculateCostsForPeriod(durationSeconds.getSeconds(), pricePerHour);
            billings.add(PipelineRunBillingInfo.builder()
                             .date(timePoints.get(i).toLocalDate())
                             .run(run)
                             .cost(cost)
                             .usageMinutes(durationSeconds.plusMinutes(1).toMinutes())
                             .build());
        }
        return billings;
    }

    /**
     * Calculate cost, based on duration of period and hourly price
     *
     * @param durationSecs duration in seconds
     * @param hourlyPrice  price for evaluating resource
     * @return cost for the given period in hundredths of cents
     */
    private Long calculateCostsForPeriod(final Long durationSecs, final BigDecimal hourlyPrice) {
        final BigDecimal duration = BigDecimal.valueOf(durationSecs);
        return duration.multiply(hourlyPrice)
            .divide(BigDecimal.valueOf(Duration.ofHours(1).getSeconds()), RoundingMode.CEILING)
            .scaleByPowerOfTen(4)
            .longValue();
    }

    private DocWriteRequest getDocWriteRequest(final String indexPrefix,
                                               final PipelineUser owner,
                                               final PipelineRunBillingInfo billing) {
        final EntityContainer<PipelineRunBillingInfo> entity = EntityContainer.<PipelineRunBillingInfo>builder()
            .owner(owner)
            .entity(billing)
            .build();
        final String fullIndex = indexPrefix + parseDateToString(billing.getDate());
        return new IndexRequest(fullIndex, INDEX_TYPE).source(mapper.map(entity));
    }
}
