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
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Data
@Slf4j
@RequiredArgsConstructor
public class RunToBillingRequestConverter implements EntityToBillingRequestConverter<PipelineRunWithType> {

    private static final int PRICE_SCALE = 5;
    private static final int USER_PRICE_SCALE = 2;
    private static final int MAX_PERIOD = 700;

    private final AbstractEntityMapper<PipelineRunBillingInfo> mapper;

    /**
     * Creates billing requests for given run
     *
     * @param runContainer Pipeline run to build request
     * @param indexPrefix common billing prefix for index to insert requests into
     * @param previousSync nullable time point, where the previous synchronization was started
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
        final PipelineRun pipelineRun = runContainer.getEntity().getPipelineRun();
        if (previousSync != null && pipelineRun.getStatus().isFinal() &&
                pipelineRun.getEndDate() != null &&
                getEnd(pipelineRun).isBefore(previousSync)) {
            log.debug("Run {} [{} - {}] was not active in period {} - {}.",
                    pipelineRun.getId(), pipelineRun.getStartDate(),
                    pipelineRun.getEndDate(), previousSync, syncStart);
            return Collections.emptyList();
        }
        final RunPrice price = getPrice(runContainer);
        final List<RunStatus> statuses = adjustStatuses(pipelineRun,
                previousSync, syncStart);

        return createBillingsForPeriod(runContainer.getEntity(), price, statuses).stream()
            .filter(this::isNotEmpty)
            .collect(Collectors.toMap(PipelineRunBillingInfo::getDate,
                                      Function.identity(),
                                      this::mergeBillings))
            .values();
    }

    private boolean isNotEmpty(final PipelineRunBillingInfo billing) {
        return billing.getCost() > 0L || billing.getUsageMinutes() > 0L || billing.getPausedMinutes() > 0L;
    }

    protected List<RunStatus> adjustStatuses(final PipelineRun run,
                                             final LocalDateTime previousSync,
                                             final LocalDateTime syncStart) {
        final List<RunStatus> statuses = run.getRunStatuses();
        final LocalDateTime start = getBillingPeriodStart(previousSync);
        final LocalDateTime end = getBillingPeriodEnd(syncStart);
        return CollectionUtils.isNotEmpty(statuses) 
                ? getAdjustedStatuses(run, statuses, start, end) 
                : getDefaultStatuses(run, start, end);
    }

    private LocalDateTime getBillingPeriodStart(final LocalDateTime periodStart) {
        return toStartOfDay(periodStart).orElse(LocalDateTime.MIN);
    }

    private LocalDateTime getBillingPeriodEnd(final LocalDateTime periodEnd) {
        return toStartOfDay(periodEnd)
                .orElseThrow(() -> new IllegalArgumentException("Billing period end date should be provided."));
    }

    private Optional<LocalDateTime> toStartOfDay(final LocalDateTime previousSync) {
        return Optional.ofNullable(previousSync)
                .map(LocalDateTime::toLocalDate)
                .map(LocalDate::atStartOfDay);
    }

    private List<RunStatus> getAdjustedStatuses(final PipelineRun run, final List<RunStatus> statuses, 
                                                final LocalDateTime start, final LocalDateTime end) {
        final List<RunStatus> sortedStatuses = statuses.stream()
                .sorted(Comparator.comparing(RunStatus::getTimestamp))
                .collect(Collectors.toList());
        final int firstStatusIndex = getFirstOverlappingStatusIndex(start, sortedStatuses);
        final int lastStatusIndex = getLastOverlappingStatusIndex(end, sortedStatuses);
        final RunStatus firstStatus = sortedStatuses.get(firstStatusIndex);
        final RunStatus lastStatus = sortedStatuses.get(lastStatusIndex);

        final List<RunStatus> resultingStatuses = 
                new ArrayList<>(sortedStatuses.subList(firstStatusIndex, lastStatusIndex + 1));
        if (firstStatus.getTimestamp().isAfter(start)) {
            if (firstStatus.getStatus() != TaskStatus.RUNNING) {
                resultingStatuses.add(0, getSyntheticFirstRunningStatus(run, start));
            }
        } else {
            resultingStatuses.set(0, getAdjustedStatus(firstStatus, start));
        }
        if (lastStatus.getTimestamp().isBefore(end)) {
            if (!lastStatus.getStatus().isFinal()) {
                resultingStatuses.add(getSyntheticLastStoppedStatus(run, end));
            }
        } else {
            resultingStatuses.set(resultingStatuses.size() - 1, getAdjustedStatus(lastStatus, end));
        }
        return resultingStatuses;
    }

    private int getFirstOverlappingStatusIndex(final LocalDateTime start, final List<RunStatus> sortedStatuses) {
        return IntStream.range(0, sortedStatuses.size() - 1)
                .filter(i -> sortedStatuses.get(i + 1).getTimestamp().isAfter(start))
                .findFirst()
                .orElse(sortedStatuses.size() - 1);
    }

    private int getLastOverlappingStatusIndex(final LocalDateTime end, final List<RunStatus> sortedStatuses) {
        return IntStream.range(0, sortedStatuses.size())
                .filter(i -> sortedStatuses.get(i).getTimestamp().isAfter(end))
                .findFirst()
                .orElse(sortedStatuses.size() - 1);
    }

    private RunStatus getSyntheticFirstRunningStatus(final PipelineRun run, final LocalDateTime start) {
        return new RunStatus(run.getId(), TaskStatus.RUNNING, max(start, getStart(run)));
    }

    private RunStatus getSyntheticLastStoppedStatus(final PipelineRun run, final LocalDateTime end) {
        return new RunStatus(run.getId(), TaskStatus.STOPPED, min(end, getEnd(run)));
    }

    private RunStatus getAdjustedStatus(final RunStatus status, final LocalDateTime date) {
        return new RunStatus(status.getRunId(), status.getStatus(), date);
    }

    private LocalDateTime getStart(final PipelineRun run) {
        return getDate(run, PipelineRun::getStartDate).orElse(LocalDateTime.MIN);
    }

    private LocalDateTime getEnd(final PipelineRun run) {
        return getDate(run, PipelineRun::getEndDate).orElse(LocalDateTime.MAX);
    }

    private Optional<LocalDateTime> getDate(final PipelineRun run, final Function<PipelineRun, Date> function) {
        return Optional.ofNullable(run).map(function).map(DateUtils::toLocalDateTime);
    }

    private LocalDateTime min(final LocalDateTime first, final LocalDateTime second) {
        return second.isBefore(first) ? second : first;
    }

    private LocalDateTime max(final LocalDateTime first, final LocalDateTime second) {
        return second.isAfter(first) ? second : first;
    }

    private List<RunStatus> getDefaultStatuses(final PipelineRun run, final LocalDateTime start, 
                                               final LocalDateTime end) {
        final LocalDateTime runStart = getStart(run);
        final LocalDateTime runEnd = getEnd(run);
        return runEnd.isBefore(start) || runStart.isAfter(end)
                ? Collections.singletonList(new RunStatus(run.getId(), TaskStatus.STOPPED, start))
                : Arrays.asList(
                new RunStatus(run.getId(), TaskStatus.RUNNING, max(start, runStart)),
                new RunStatus(run.getId(), TaskStatus.STOPPED, min(end, runEnd)));
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
        return getScaledPrice(runContainer, priceExtractor, PRICE_SCALE);
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
        billing1.setPausedMinutes(billing1.getPausedMinutes() + billing2.getPausedMinutes());
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
            final Long durationMinutes = minutesOf(duration);
            billings.add(PipelineRunBillingInfo.builder()
                             .date(pointStart.toLocalDate())
                             .run(run)
                             .cost(getCosts(duration, disk, price, active))
                             .usageMinutes(active ? durationMinutes : 0L)
                             .pausedMinutes(active ? 0L : durationMinutes)
                             .build());
        }
        return billings;
    }

    private List<LocalDateTime> getTimePoints(final LocalDateTime start, final LocalDateTime end,
                                              final PipelineRunWithType run) {
        if (isPeriodTooLong(start, end)) {
            log.warn("Billing period for run #{} is too big. It will be skipped.", run.getPipelineRun().getId());
            return Collections.emptyList();
        }
        return Stream.of(Stream.of(start, end), periodTimePoints(start, end), diskAttachTimePoints(start, end, run))
                .reduce(Stream::concat)
                .orElseGet(Stream::empty)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private boolean isPeriodTooLong(final LocalDateTime start, final LocalDateTime end) {
        return Period.between(start.toLocalDate(), end.toLocalDate()).getYears() > MAX_PERIOD;
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

    private Long minutesOf(final Duration duration) {
        return max(duration, Duration.ofMinutes(1)).toMinutes();
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
