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
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.contextual.ContextualPreferenceManager;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.mapper.credits.PlatformUsageCreditsUserBalanceMapper;
import com.epam.pipeline.repository.credits.PlatformUsageCreditsUserBalanceRepository;
import com.epam.pipeline.repository.credits.PlatformUsageCreditsUserBalanceSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.apache.commons.collections4.CollectionUtils;

import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Business-logic layer for platform usage credits user balances.
 *
 * <p>A user balance tracks the current credits held by a specific pipeline user and is updated
 * whenever the monitoring service fires a credits event (increase or deduction).
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformUsageCreditsUserBalanceService {

    private final PlatformUsageCreditsUserBalanceRepository repository;
    private final PlatformUsageCreditsUserBalanceMapper mapper;
    private final ContextualPreferenceManager contextualPreferenceManager;

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
        final Page<PlatformUsageCreditsUserBalanceEntity> page =
                repository.findAll(PlatformUsageCreditsUserBalanceSpecification.build(filter),
                        new PageRequest(filter.getPage(), filter.getPageSize()));
        final List<PlatformUsageCreditsUserBalance> elements = page.getContent().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
        return new PagedResult<>(elements, (int) page.getTotalElements());
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
            userIds.forEach(userId -> validateResetValue(value, userId));
            userIds.forEach(userId -> upsertBalance(value, now, userId));
        } else {
            repository.resetAll(value);
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
     * @return a copy of the event with {@code value} set to the actual amount applied
     */
    @Transactional
    public PlatformUsageCreditsUpdateEvent updateByEvent(final PlatformUsageCreditsUpdateEvent event) {
        final Optional<PlatformUsageCreditsUserBalanceEntity> existing =
                repository.findByUserId(event.getUserId());
        final int currentBalance = existing
                .map(PlatformUsageCreditsUserBalanceEntity::getCurrentValue)
                .orElseGet(() -> getIntPreference(SystemPreferences.USAGE_CREDITS_DEFAULT, event.getUserId()));

        final int minBalance = getIntPreference(SystemPreferences.USAGE_CREDITS_MIN, event.getUserId());
        final int maxBalance = getIntPreference(SystemPreferences.USAGE_CREDITS_MAX, event.getUserId());

        final int rawNewBalance = ActionType.INCREASE.equals(event.getIncidentType())
                ? currentBalance + event.getValue()
                : currentBalance - event.getValue();

        final int clampedBalance = Math.max(minBalance, Math.min(maxBalance, rawNewBalance));
        final int actualValue = Math.abs(clampedBalance - currentBalance);
        event.setValue(actualValue);

        if (actualValue == 0) {
            log.info("Credits event for user {} has no effect: balance already at boundary {}",
                    event.getUserId(), currentBalance);
            return event;
        }

        final PlatformUsageCreditsUserBalanceEntity entity = existing
                .orElseGet(() -> PlatformUsageCreditsUserBalanceEntity.builder()
                        .userId(event.getUserId())
                        .build());
        entity.setCurrentValue(clampedBalance);
        entity.setModifiedDate(DateUtils.nowUTC());
        repository.save(entity);

        return event;
    }

    private int getIntPreference(final AbstractSystemPreference.IntPreference intPreference, final Long userId) {
        return Integer.parseInt(contextualPreferenceManager.search(
                Collections.singletonList(intPreference.getKey()), userId).getValue());
    }

    private void validateResetValue(final int value, final Long userId) {
        final int min = getIntPreference(SystemPreferences.USAGE_CREDITS_MIN, userId);
        final int max = getIntPreference(SystemPreferences.USAGE_CREDITS_MAX, userId);
        if (value < min || value > max) {
            throw new IllegalArgumentException(String.format(
                    "Reset value %d is out of allowed range [%d, %d] for user %d", value, min, max, userId));
        }
    }

    private void upsertBalance(final int value, final LocalDateTime now, final Long userId) {
        final PlatformUsageCreditsUserBalanceEntity entity = repository.findByUserId(userId)
                .orElseGet(() -> PlatformUsageCreditsUserBalanceEntity.builder().userId(userId).build());
        entity.setCurrentValue(value);
        entity.setModifiedDate(now);
        repository.save(entity);
    }
}
