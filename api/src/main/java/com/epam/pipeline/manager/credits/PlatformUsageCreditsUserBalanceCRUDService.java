/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.credits;

import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalance;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalanceFilterVO;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUserBalanceEntity;
import com.epam.pipeline.mapper.credits.PlatformUsageCreditsUserBalanceMapper;
import com.epam.pipeline.repository.credits.PlatformUsageCreditsUserBalanceRepository;
import com.epam.pipeline.repository.credits.PlatformUsageCreditsUserBalanceSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.apache.commons.lang3.tuple.Pair;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformUsageCreditsUserBalanceCRUDService {

    private final PlatformUsageCreditsUserBalanceRepository repository;
    private final PlatformUsageCreditsUserBalanceMapper mapper;

    /**
     * Returns a paged, optionally filtered list of user balances.
     * This method supports three optional predicates, all
     * translated to SQL so that paging counts are always accurate:
     * <ul>
     *   <li>{@code userIds} — restrict results to a specific set of users</li>
     *   <li>{@code value} + {@code operation} ({@code "<"}, {@code ">"}, {@code "="}) — compare
     *       the stored {@code current_value} against the given threshold at the database level</li>
     * </ul>
     * @param filter filter criteria; {@code page} and {@code pageSize} are always applied
     * @return paged result containing matching balance DTOs and the total count
     */
    public PagedResult<List<PlatformUsageCreditsUserBalance>> filter(
            final PlatformUsageCreditsUserBalanceFilterVO filter) {
        Assert.isTrue(filter.getPage() >= 1, "Page index must be >= 1");
        Assert.isTrue(filter.getPageSize() > 0, "Page size must be > 0");
        final Page<PlatformUsageCreditsUserBalanceEntity> page =
                repository.findAll(PlatformUsageCreditsUserBalanceSpecification.build(filter),
                        new PageRequest(filter.getPage() - 1, filter.getPageSize()));
        final List<PlatformUsageCreditsUserBalance> elements = page.getContent().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
        return new PagedResult<>(elements, (int) page.getTotalElements());
    }

    /**
     * Returns the credits balance for a single user, or empty if no record exists yet.
     *
     * @param userId the user whose balance to look up
     * @return the balance DTO, or {@link Optional#empty()} if no row exists for the user
     */
    public Optional<PlatformUsageCreditsUserBalance> findByUserId(final Long userId) {
        return repository.findByUserId(userId).map(mapper::toDto);
    }

    /**
     * Returns all recorded balances keyed by {@code userId}.
     * Intended for bulk enrichment to avoid N+1 queries.
     *
     * @return map from userId to the corresponding balance DTO
     */
    public Map<Long, PlatformUsageCreditsUserBalance> findAllAsMap() {
        return StreamSupport.stream(repository.findAll().spliterator(), false)
                .map(mapper::toDto)
                .collect(Collectors.toMap(PlatformUsageCreditsUserBalance::getUserId, Function.identity()));
    }

    @Transactional
    public void resetAll(final int value) {
        log.debug("Resetting credits balance to {} for all users", value);
        repository.resetAll(value);
        log.debug("Credits balance reset to {} completed for all users", value);
    }

    @Transactional
    public Pair<Integer, Integer> updateByEvent(final Long userId,
                                                final int minBalance,
                                                final int maxBalance,
                                                final int defaultBalance,
                                                final int delta) {
        // Prevent lost-update: concurrent transactions must not read and modify the same balance row simultaneously.
        repository.lockBalance(userId);

        final List<Object[]> rows = repository.atomicUpdateBalance(
                userId, delta, defaultBalance, minBalance, maxBalance);
        if (CollectionUtils.isEmpty(rows)) {
            log.warn("atomicUpdateBalance returned no rows for user {}, skipping event", userId);
            return null;
        }
        final Object[] row = rows.get(0);
        Assert.isTrue(row.length == 2,
                "atomicUpdateBalance row must contain 2 columns but got " + row.length);
        final int newBalance = ((Number) row[0]).intValue();
        final int actualDelta = ((Number) row[1]).intValue();
        final int actualValue = Math.abs(actualDelta);

        return Pair.of(newBalance, actualValue);
    }

    @Transactional
    public void upsertBalance(final int value, final LocalDateTime now, final Long userId) {
        final PlatformUsageCreditsUserBalanceEntity entity = repository.findByUserId(userId)
                .orElseGet(() -> {
                    log.debug("No existing balance row for user {}, creating new", userId);
                    return PlatformUsageCreditsUserBalanceEntity.builder().userId(userId).build();
                });
        entity.setCurrentValue(value);
        entity.setModifiedDate(now);
        repository.save(entity);
        log.debug("Saved credits balance {} for user {}", value, userId);
    }
}
