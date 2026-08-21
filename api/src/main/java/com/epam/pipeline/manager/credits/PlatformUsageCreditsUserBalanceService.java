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

import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateAction.ActionType;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateEvent;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalance;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.contextual.ContextualPreferenceManager;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import com.epam.pipeline.manager.user.UserManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import org.apache.commons.collections4.CollectionUtils;

import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

    private final ContextualPreferenceManager contextualPreferenceManager;
    private final NotificationManager notificationManager;
    private final UserManager userManager;
    private final CheckPermissionHelper permissionHelper;
    private final PlatformUsageCreditsUserBalanceCRUDService crudService;
    private final PlatformUsageCreditsLaunchService launchService;

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
            userIds.forEach(userId -> crudService.upsertBalance(value, now, userId));
        } else {
            crudService.resetAll(value);
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

        final BalanceUpdateResult result = crudService.updateByEvent(userId, minBalance,
                maxBalance, defaultBalance, delta);

        if (Objects.isNull(result)) {
            return event;
        }
        final int actualValue = Math.abs(result.getActualDelta());
        final int newBalance = result.getNewBalance();
        final int oldBalance = newBalance - result.getActualDelta();
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

    /**
     * Returns the balance for the given user enriched with currently allocated credits.
     */
    public PlatformUsageCreditsUserBalance getBalanceWithAllocated(final Long userId) {
        Assert.notNull(userId, "User id must not be null");
        final PipelineUser currentUser = userManager.getCurrentUser();
        if (!currentUser.isAdmin() && !permissionHelper.isScopedAdmin(currentUser)
                && !Objects.equals(currentUser.getId(), userId)) {
            throw new AccessDeniedException("Access denied: you can only view your own credits balance.");
        }
        final PipelineUser user = Objects.equals(currentUser.getId(), userId)
                ? currentUser
                : userManager.load(userId);
        final PlatformUsageCreditsUserBalance balance = crudService.findByUserId(userId)
                .orElse(getDefaultBalance(user));
        balance.setAllocated(launchService.getAllocatedCredits(user.getUserName()));
        return balance;
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

    private boolean isBelowNotificationThreshold(final int currentValue, final int min, final int max,
                                                  final int threshold) {
        final double absoluteValue = min + (threshold / 100.0) * (max - min);
        return currentValue < absoluteValue;
    }

    private PlatformUsageCreditsUserBalance getDefaultBalance(final PipelineUser user) {
        return PlatformUsageCreditsUserBalance.builder()
                .currentValue(getIntPreference(SystemPreferences.USAGE_CREDITS_DEFAULT, user))
                .build();
    }
}
