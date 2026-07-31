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
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateAction.ActionType;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateEvent;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalance;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalanceFilterVO;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUserBalanceEntity;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.contextual.ContextualPreferenceManager;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.mapper.credits.PlatformUsageCreditsUserBalanceMapper;
import com.epam.pipeline.repository.credits.PlatformUsageCreditsUserBalanceRepository;
import com.epam.pipeline.repository.credits.PlatformUsageCreditsUserBalanceSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import org.apache.commons.collections4.CollectionUtils;

import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Business-logic layer for platform usage credits user balances.
 *
 * <p>A user balance tracks the current credits held by a specific pipeline user and is updated
 * whenever the monitoring service fires a credits event (increase or deduction).
 *
 */
@Slf4j
@Service
public class PlatformUsageCreditsUserBalanceService {

    private final PlatformUsageCreditsUserBalanceRepository repository;
    private final PlatformUsageCreditsUserBalanceMapper mapper;
    private final ContextualPreferenceManager contextualPreferenceManager;
    private final NotificationManager notificationManager;
    private final UserManager userManager;

    public PlatformUsageCreditsUserBalanceService(
            final PlatformUsageCreditsUserBalanceRepository repository,
            final PlatformUsageCreditsUserBalanceMapper mapper,
            final ContextualPreferenceManager contextualPreferenceManager,
            @Lazy final NotificationManager notificationManager,
            @Lazy final UserManager userManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.contextualPreferenceManager = contextualPreferenceManager;
        this.notificationManager = notificationManager;
        this.userManager = userManager;
    }

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

    /**
     * Resets the credits balance to {@code value} for each user in {@code userIds}.
     *
     * <p>When {@code userIds} is non-empty, the value is first validated against each user's
     * contextual {@code [usage.credits.min, usage.credits.max]} bounds. If the value falls outside
     * the bounds for any user, an {@link IllegalArgumentException} is thrown and no rows are
     * modified. When all validations pass, each user's balance row is upserted.
     *
     * <p>When {@code userIds} is {@code null} or empty, every existing balance row is updated in
     * a single bulk statement with no per-user validation.
     *
     * @param value   the new credits balance to set
     * @param userIds the target users, or {@code null}/empty to reset all users
     * @throws IllegalArgumentException if {@code value} is outside the allowed bounds for any user
     */
    @Transactional
    public void reset(final int value, @Nullable final List<Long> userIds) {
        final LocalDateTime now = DateUtils.nowUTC();
        if (CollectionUtils.isNotEmpty(userIds)) {
            userIds.forEach(userId -> {
                final PipelineUser user = userManager.load(userId);
                final int min = getIntPreference(SystemPreferences.USAGE_CREDITS_MIN, user);
                final int max = getIntPreference(SystemPreferences.USAGE_CREDITS_MAX, user);
                validateResetValue(value, min, max, userId);
            });
            userIds.forEach(userId -> upsertBalance(value, now, userId));
        } else {
            log.debug("Resetting credits balance to {} for all users", value);
            repository.resetAll(value);
            log.debug("Credits balance reset to {} completed for all users", value);
        }
    }

    /**
     * Applies a credits event to the user's balance and returns the event with the actual amount
     * applied (which may be less than {@code event.getValue()} when the balance would otherwise
     * exceed the configured bounds).
     *
     * <p>If the user has no balance row yet, the configured default ({@code usage.credits.default})
     * is used as the starting value. The resulting balance is always clamped to
     * [{@code usage.credits.min}, {@code usage.credits.max}].
     *
     * <p>If the actual amount applied is 0 (balance already at the boundary in the direction of the
     * event), nothing is written to the database and the returned event has {@code value = 0}.
     *
     * @param event the event to apply; {@code value} must be positive; direction is {@code incidentType}
     * @return the event with {@code value} set to the actual amount applied
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @Transactional
    public PlatformUsageCreditsUpdateEvent updateByEvent(final PlatformUsageCreditsUpdateEvent event) {
        Assert.notNull(event, "Credits event must not be null");
        Assert.notNull(event.getUserId(), "Credits event userId must not be null");
        Assert.notNull(event.getIncidentType(), "Credits event incidentType must not be null");
        final Long userId = event.getUserId();
        final PipelineUser user = userManager.load(userId);
        final int minBalance = getIntPreference(SystemPreferences.USAGE_CREDITS_MIN, user);
        final int maxBalance = getIntPreference(SystemPreferences.USAGE_CREDITS_MAX, user);
        final int defaultBalance = getIntPreference(SystemPreferences.USAGE_CREDITS_DEFAULT, user);
        final int threshold = getIntPreference(SystemPreferences.USAGE_CREDITS_NOTIFICATION_THRESHOLD, user);

        final int delta = ActionType.INCREASE.equals(event.getIncidentType())
                ? event.getValue() : -event.getValue();

        // Prevent lost-update: concurrent transactions must not read and modify the same balance row simultaneously.
        repository.lockBalance(userId);

        final List<Object[]> rows = repository.atomicUpdateBalance(
                userId, delta, defaultBalance, minBalance, maxBalance);
        if (CollectionUtils.isEmpty(rows)) {
            log.warn("atomicUpdateBalance returned no rows for user {}, skipping event", userId);
            return event;
        }
        final Object[] row = rows.get(0);
        Assert.isTrue(row.length == 2,
                "atomicUpdateBalance row must contain 2 columns but got " + row.length);
        final int newBalance = ((Number) row[0]).intValue();
        final int actualDelta = ((Number) row[1]).intValue();
        final int oldBalance = newBalance - actualDelta;
        final int actualValue = Math.abs(actualDelta);
        event.setValue(actualValue);

        if (actualValue == 0) {
            log.info("Credits event for user {} has no effect: balance already at boundary {}",
                    userId, oldBalance);
            return event;
        }

        log.debug("Credits balance updated for user {}: {} -> {} (actual delta={})",
                userId, oldBalance, newBalance, actualValue);

        if (isBelowNotificationThreshold(newBalance, minBalance, maxBalance, threshold)) {
            log.debug("Balance {} for user {} is below notification threshold, sending notification",
                    newBalance, userId);
            try {
                notificationManager.notifyLowUsageCredits(userId, newBalance);
            } catch (Exception e) {
                log.warn("Failed to send low credits notification", e);
            }
        }

        return event;
    }

    private int getIntPreference(final AbstractSystemPreference.IntPreference intPreference,
                                  final PipelineUser user) {
        return Integer.parseInt(contextualPreferenceManager.search(
                Collections.singletonList(intPreference.getKey()), user).getValue());
    }

    private void validateResetValue(final int value, final int min, final int max, final Long userId) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(String.format(
                    "Reset value %d is out of allowed range [%d, %d] for user %d", value, min, max, userId));
        }
    }

    private void upsertBalance(final int value, final LocalDateTime now, final Long userId) {
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

    private boolean isBelowNotificationThreshold(final int currentValue, final int min, final int max,
                                                  final int threshold) {
        final double absoluteValue = min + (threshold / 100.0) * (max - min);
        return currentValue < absoluteValue;
    }
}
