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
import com.epam.pipeline.dto.credits.PlatformUsageCreditsMode;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalance;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalanceFilterVO;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUserBalanceEntity;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Low-level CRUD service for {@link PlatformUsageCreditsUserBalanceEntity}.
 *
 * <p>This service owns all direct reads and writes to the user-balance table and is the
 * single place that enforces the {@link PlatformUsageCreditsMode#OFF} short-circuit: read
 * methods return empty results and write methods are no-ops when the feature is disabled,
 * so callers do not need to repeat that check.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformUsageCreditsUserBalanceCRUDService {

    private final PlatformUsageCreditsUserBalanceRepository repository;
    private final PlatformUsageCreditsUserBalanceMapper mapper;
    private final PreferenceManager preferenceManager;

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
        return isOffMode() ? Optional.empty() : repository.findByUserId(userId).map(mapper::toDto);
    }

    /**
     * Returns all recorded balances keyed by {@code userId}.
     * Intended for bulk enrichment to avoid N+1 queries.
     *
     * @return map from userId to the corresponding balance DTO
     */
    public Map<Long, PlatformUsageCreditsUserBalance> findAllAsMap() {
        return isOffMode()
                ? Collections.emptyMap()
                : StreamSupport.stream(repository.findAll().spliterator(), false)
                  .map(mapper::toDto)
                  .collect(Collectors.toMap(PlatformUsageCreditsUserBalance::getUserId, Function.identity()));
    }

    /**
     * Sets every user's balance to {@code value} in a single bulk UPDATE.
     * Intended for periodic resets (e.g. monthly quota refresh or initial set).
     *
     * @param value the new balance to assign to all users
     */
    @Transactional
    public void resetAll(final int value) {
        log.debug("Resetting credits balance to {} for all users", value);
        repository.resetAll(value);
        log.debug("Credits balance reset to {} completed for all users", value);
    }

    /**
     * Applies a signed {@code delta} to the user's balance atomically, clamping the result
     * to [{@code minBalance}, {@code maxBalance}] and defaulting to {@code defaultBalance} if
     * no row exists yet. Uses a pessimistic row-level lock to prevent lost updates under
     * concurrent transactions.
     *
     * @param userId         the user whose balance to update
     * @param minBalance     the lower bound the balance must not fall below
     * @param maxBalance     the upper bound the balance must not exceed
     * @param defaultBalance the starting balance if no row exists for the user
     * @param delta          signed amount to add (negative for deductions)
     * @return a {@link BalanceUpdateResult} containing the new balance and the signed delta
     *         actually applied, or {@code null} if the atomic update produced no rows
     */
    @Transactional
    public BalanceUpdateResult updateByEvent(final Long userId,
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

        return new BalanceUpdateResult(newBalance, actualDelta);
    }

    /**
     * Creates or updates the balance row for {@code userId}, setting its value to {@code value}
     * and its modification timestamp to {@code now}. If no row exists, a new one is inserted.
     *
     * @param value  the balance value to store
     * @param now    the modification timestamp to record
     * @param userId the user whose balance to create or update
     */
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

    /**
     * Inserts a default balance row for a newly created user using the value from the
     * {@code usage.credits.default} system preference.
     *
     * <p>The method is a no-op when:
     * <ul>
     *   <li>the credits feature mode is {@link PlatformUsageCreditsMode#OFF}, or</li>
     *   <li>a balance row already exists for {@code userId} (idempotent).</li>
     * </ul>
     *
     * @param userId the ID of the newly created user
     */
    @Transactional
    public void createDefaultBalance(final Long userId) {
        if (isOffMode()) {
            return;
        }
        if (repository.findByUserId(userId).isPresent()) {
            return;
        }
        final int defaultValue = preferenceManager.getPreference(SystemPreferences.USAGE_CREDITS_DEFAULT);
        repository.save(PlatformUsageCreditsUserBalanceEntity.builder()
                .userId(userId)
                .currentValue(defaultValue)
                .modifiedDate(DateUtils.nowUTC())
                .build());
    }

    private boolean isOffMode() {
        return PlatformUsageCreditsMode.OFF.equals(
                preferenceManager.getPreference(SystemPreferences.USAGE_CREDITS_MODE));
    }
}
