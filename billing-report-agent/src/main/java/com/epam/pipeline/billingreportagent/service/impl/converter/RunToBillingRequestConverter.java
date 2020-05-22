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
import com.epam.pipeline.billingreportagent.model.EntityWithMetadata;
import com.epam.pipeline.billingreportagent.model.PipelineRunWithType;
import com.epam.pipeline.billingreportagent.model.billing.PipelineRunBillingInfo;
import com.epam.pipeline.billingreportagent.service.AbstractEntityMapper;
import com.epam.pipeline.billingreportagent.service.EntityToBillingRequestConverter;
import com.epam.pipeline.entity.cluster.NodeDisk;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
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
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@Slf4j
@RequiredArgsConstructor
public class RunToBillingRequestConverter implements EntityToBillingRequestConverter<PipelineRunWithType> {

    private static final int BILLING_PRICE_SCALE = 5;
    private static final int USER_PRICE_SCALE = 2;
    
    private final AbstractEntityMapper<PipelineRunBillingInfo> mapper;

    /**
     * Creates billing requests for given run
     *
     * @param runContainer Pipeline run to build request
     * @param indexPrefix common billing prefix for index to insert requests into
     * @param syncStart time point, where the whole synchronization process was started
     * @return list of requests to be performed (deletion index request if no billing requests created)
     */
    @Override
    public List<DocWriteRequest> convertEntityToRequests(final EntityContainer<PipelineRunWithType> runContainer,
                                                         final String indexPrefix,
                                                         final LocalDateTime previousSync,
                                                         final LocalDateTime syncStart) {
        return convertRunToBillings(runContainer, previousSync, syncStart).stream()
            .map(billingInfo -> getDocWriteRequest(indexPrefix, runContainer.getOwner(), billingInfo))
            .collect(Collectors.toList());
    }

    protected Collection<PipelineRunBillingInfo> convertRunToBillings(
        final EntityContainer<PipelineRunWithType> runContainer,
        final LocalDateTime previousSync,
        final LocalDateTime syncStart) {
        final RunPrice price = getPrice(runContainer);
        final List<RunStatus> statuses = adjustStatuses(runContainer.getEntity().getPipelineRun(),
                previousSync, syncStart);

        return createBillingsForPeriod(runContainer.getEntity(), price, statuses).stream()
            .filter(billing -> billing.getCost() > 0L || billing.getUsageMinutes() > 0L)
            .collect(Collectors.toMap(PipelineRunBillingInfo::getDate,
                                      Function.identity(),
                                      this::mergeBillings))
            .values();
    }

    private List<RunStatus> adjustStatuses(final PipelineRun run,
                                           final LocalDateTime previousSync,
                                           final LocalDateTime syncStart) {
        final List<RunStatus> statuses = run.getRunStatuses();
        if (CollectionUtils.isNotEmpty(statuses)) {
            addFirstRunningStatusIfRequired(run, statuses);
            statuses.sort(Comparator.comparing(RunStatus::getTimestamp));
            final RunStatus lastStatus = statuses.get(statuses.size() - 1);
            if (TaskStatus.RUNNING.equals(lastStatus.getStatus())) {
                final LocalDateTime lastTimestamp = Optional.ofNullable(run.getEndDate())
                        .map(DateUtils::toLocalDateTime)
                        .orElse(syncStart);
                statuses.add(new RunStatus(null, null, lastTimestamp.toLocalDate().atStartOfDay()));
            }
        } else {
            return Arrays.asList(
                new RunStatus(null, TaskStatus.RUNNING, previousSync.toLocalDate().atStartOfDay()),
                new RunStatus(null, null, syncStart.toLocalDate().atStartOfDay()));
        }
        return statuses;
    }

    private void addFirstRunningStatusIfRequired(final PipelineRun run,
                                                 final List<RunStatus> statuses) {
        final RunStatus first = statuses.get(0);
        if (!TaskStatus.RUNNING.equals(first.getStatus())) {
            final RunStatus inserted = new RunStatus(first.getRunId(), TaskStatus.RUNNING,
                    DateUtils.toLocalDateTime(run.getStartDate()));
            statuses.add(0, inserted);
        }
    }

    private RunPrice getPrice(final EntityContainer<PipelineRunWithType> runContainer) {
        final BigDecimal pricePerHour = getUserPrice(runContainer, PipelineRun::getPricePerHour);
        final BigDecimal computePricePerHour = getBillingPrice(runContainer, PipelineRun::getComputePricePerHour);
        final BigDecimal diskPricePerHour = getBillingPrice(runContainer, PipelineRun::getDiskPricePerHour);
        return new RunPrice(pricePerHour, computePricePerHour, diskPricePerHour);
    }

    private BigDecimal getUserPrice(final EntityContainer<PipelineRunWithType> runContainer, 
                                    final Function<PipelineRun, BigDecimal> priceExtractor) {
        return getScaledPrice(runContainer, priceExtractor, USER_PRICE_SCALE);
    }

    private BigDecimal getBillingPrice(final EntityContainer<PipelineRunWithType> runContainer,
                                       final Function<PipelineRun, BigDecimal> priceExtractor) {
        return getScaledPrice(runContainer, priceExtractor, BILLING_PRICE_SCALE);
    }

    private BigDecimal getScaledPrice(final EntityContainer<PipelineRunWithType> runContainer,
                                      final Function<PipelineRun, BigDecimal> priceExtractor,
                                      final int scale) {
        return Optional.of(runContainer.getEntity().getPipelineRun())
                .map(priceExtractor)
                .orElse(BigDecimal.ZERO)
                .setScale(scale, BigDecimal.ROUND_CEILING);
    }

    private List<PipelineRunBillingInfo> createBillingsForPeriod(final PipelineRunWithType run,
                                                                 final RunPrice price,
                                                                 final List<RunStatus> statuses) {
        final List<PipelineRunBillingInfo> billings = new ArrayList<>();
        for (int i = 0; i < statuses.size() - 1; i++) {
            final RunStatus previous = statuses.get(i);
            final RunStatus current = statuses.get(i + 1);
            if (TaskStatus.RUNNING.equals(previous.getStatus())) {
                billings.addAll(createRunBillingsForActivePeriod(previous.getTimestamp(), current.getTimestamp(), 
                        run, price));
            } else {
                billings.addAll(createRunBillingsForInactivePeriod(previous.getTimestamp(), current.getTimestamp(),
                        run, price));
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
                                                                          final PipelineRunWithType run,
                                                                          final RunPrice price) {
        return createRunBillingsForPeriod(start, end, run, price, true);
    }

    private List<PipelineRunBillingInfo> createRunBillingsForInactivePeriod(final LocalDateTime start, 
                                                                            final LocalDateTime end, 
                                                                            final PipelineRunWithType run, 
                                                                            final RunPrice price) {
        return createRunBillingsForPeriod(start, end, run, price, false);
    }

    private List<PipelineRunBillingInfo> createRunBillingsForPeriod(final LocalDateTime start,
                                                                    final LocalDateTime end,
                                                                    final PipelineRunWithType run,
                                                                    final RunPrice price,
                                                                    final boolean active) {
        final List<LocalDateTime> timePoints = getTimePoints(start, end, run);
        final List<PipelineRunBillingInfo> billings = new ArrayList<>();
        for (int i = 0; i < timePoints.size() - 1; i++) {
            final LocalDateTime pointStart = timePoints.get(i);
            final LocalDateTime pointEnd = timePoints.get(i + 1);
            final Duration duration = Duration.between(pointStart, pointEnd);
            final Long disk = getDisksSize(run, pointStart);
            billings.add(PipelineRunBillingInfo.builder()
                             .date(pointStart.toLocalDate())
                             .run(run)
                             .cost(getCosts(duration, disk, price, active))
                             .usageMinutes(getUsageMinutes(duration, active))
                             .build());
        }
        return billings;
    }

    private List<LocalDateTime> getTimePoints(final LocalDateTime start, final LocalDateTime end,
                                              final PipelineRunWithType run) {
        return Stream.of(Stream.of(start, end), periodTimePoints(start, end), diskAttachTimePoints(start, end, run))
                .reduce(Stream::concat)
                .orElseGet(Stream::empty)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private Stream<LocalDateTime> periodTimePoints(final LocalDateTime start, final LocalDateTime end) {
        return Stream.iterate(start.toLocalDate().plusDays(1), date -> date.plusDays(1))
                .limit(ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()))
                .map(LocalDate::atStartOfDay);
    }

    private Stream<LocalDateTime> diskAttachTimePoints(final LocalDateTime start, final LocalDateTime end, 
                                                       final PipelineRunWithType run) {
        return run.getDisks().stream()
                .filter(disk -> disk.getCreatedDate().isAfter(start) && disk.getCreatedDate().isBefore(end))
                .map(NodeDisk::getCreatedDate);
    }

    private Long getCosts(final Duration duration, final Long disk, final RunPrice price, final boolean active) {
        return price.isOldFashioned() 
                ? getOldFashionedCosts(duration, price, active)
                : getNewFashionedCosts(duration, disk, price, active);
    }

    private long getOldFashionedCosts(final Duration duration, final RunPrice price, final boolean active) {
        return active ? getOldFashionedCosts(duration, price) : 0L;
    }

    private Long getOldFashionedCosts(final Duration duration, final RunPrice price) {
        return calculateCostsForPeriod(duration.getSeconds(), price.getOldFashionedPricePerHour());
    }

    private long getNewFashionedCosts(final Duration duration, final Long disk, final RunPrice price, 
                                      final boolean active) {
        return getDiskCosts(duration, disk, price) + (active ? getComputeCosts(duration, price) : 0L);
    }

    private Long getComputeCosts(final Duration duration, final RunPrice price) {
        return calculateCostsForPeriod(duration.getSeconds(), price.getComputePricePerHour());
    }

    private Long getDiskCosts(final Duration duration, final Long disk, final RunPrice price) {
        return calculateCostsForPeriod(duration.getSeconds(), price.getDiskPricePerHour()
                .multiply(BigDecimal.valueOf(disk)));
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
            .divide(BigDecimal.valueOf(Duration.ofHours(1).getSeconds()), USER_PRICE_SCALE, RoundingMode.CEILING)
            .scaleByPowerOfTen(4)
            .longValue();
    }

    private Long getDisksSize(final PipelineRunWithType run, final LocalDateTime date) {
        return run.getDisks().stream()
                .filter(d -> d.getCreatedDate().isBefore(date) || d.getCreatedDate().isEqual(date))
                .mapToLong(NodeDisk::getSize)
                .sum();
    }

    private Long getUsageMinutes(final Duration duration, final boolean active) {
        return active ? max(duration, Duration.ofMinutes(1)).toMinutes() : 0L;
    }

    private Duration max(final Duration duration1, final Duration duration2) {
        return duration1.compareTo(duration2) > 0 ? duration1 : duration2;
    }

    private DocWriteRequest getDocWriteRequest(final String indexPrefix,
                                               final EntityWithMetadata<PipelineUser> owner,
                                               final PipelineRunBillingInfo billing) {
        final EntityContainer<PipelineRunBillingInfo> entity = EntityContainer.<PipelineRunBillingInfo>builder()
            .owner(owner)
            .entity(billing)
            .build();
        final String fullIndex = indexPrefix + parseDateToString(billing.getDate());
        final String docId = billing.getEntity().getPipelineRun().getId().toString();
        return new IndexRequest(fullIndex, INDEX_TYPE).id(docId).source(mapper.map(entity));
    }
}
