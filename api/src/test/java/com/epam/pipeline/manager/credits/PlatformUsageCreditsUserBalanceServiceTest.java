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

import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateEvent;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.contextual.ContextualPreferenceManager;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.user.UserManager;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.BALANCE_ABOVE_THRESHOLD;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.BALANCE_BELOW_THRESHOLD;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.BALANCE_NEAR_MAX;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.BALANCE_NEAR_MIN;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.CURRENT_BALANCE;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.EVENT_VALUE;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.DEFAULT_BALANCE;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.MAX_BALANCE;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.MIN_BALANCE;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.NOTIFICATION_THRESHOLD;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.RESET_VALUE;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.USER_ID;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.pipelineUser;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.deductionEvent;
import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.increaseEvent;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class PlatformUsageCreditsUserBalanceServiceTest {

    private final ContextualPreferenceManager contextualPreferenceManager =
            mock(ContextualPreferenceManager.class);
    private final NotificationManager notificationManager =
            mock(NotificationManager.class);
    private final UserManager userManager =
            mock(UserManager.class);
    private final PlatformUsageCreditsUserBalanceCRUDService crudService =
            mock(PlatformUsageCreditsUserBalanceCRUDService.class);
    private final PlatformUsageCreditsLaunchService launchService =
            mock(PlatformUsageCreditsLaunchService.class);
    private final PipelineRunManager pipelineRunManager =
            mock(PipelineRunManager.class);
    private final PlatformUsageCreditsUserBalanceService service =
            new PlatformUsageCreditsUserBalanceService(
                    contextualPreferenceManager, notificationManager, userManager,
                    crudService, launchService, pipelineRunManager);

    @Test
    public void shouldUpsertBalanceOnResetForUser() {
        mockPreferences(MIN_BALANCE, MAX_BALANCE, DEFAULT_BALANCE);

        service.reset(RESET_VALUE, Collections.singletonList(USER_ID));

        verify(crudService).upsertBalance(eq(RESET_VALUE), any(LocalDateTime.class), eq(USER_ID));
    }

    @Test
    public void shouldBulkResetAllWhenUserIdIsNull() {
        service.reset(RESET_VALUE, null);

        verify(crudService).resetAll(RESET_VALUE);
        verify(crudService, never()).upsertBalance(anyInt(), any(), any());
    }

    @Test
    public void shouldBulkResetAllWhenUserIdsIsEmpty() {
        service.reset(RESET_VALUE, Collections.emptyList());

        verify(crudService).resetAll(RESET_VALUE);
        verify(crudService, never()).upsertBalance(anyInt(), any(), any());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailResetWhenValueBelowMin() {
        mockPreferences(MIN_BALANCE, MAX_BALANCE, DEFAULT_BALANCE);

        service.reset(MIN_BALANCE - 1, Collections.singletonList(USER_ID));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailResetWhenValueAboveMax() {
        mockPreferences(MIN_BALANCE, MAX_BALANCE, DEFAULT_BALANCE);

        service.reset(MAX_BALANCE + 1, Collections.singletonList(USER_ID));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailResetOnFirstInvalidUserAndSkipRemaining() {
        final Long secondUserId = USER_ID + 1;
        mockPreferences(MIN_BALANCE, MAX_BALANCE, DEFAULT_BALANCE);

        service.reset(MAX_BALANCE + 1, Arrays.asList(USER_ID, secondUserId));
    }

    @Test
    public void shouldIncreaseBalanceWithinBounds() {
        mockPreferences(MIN_BALANCE, MAX_BALANCE, DEFAULT_BALANCE);
        mockCrudUpdate(CURRENT_BALANCE + EVENT_VALUE, EVENT_VALUE);

        final PlatformUsageCreditsUpdateEvent result = service.updateByEvent(increaseEvent(EVENT_VALUE));

        assertThat(result.getValue(), is(EVENT_VALUE));
    }

    @Test
    public void shouldClampIncreaseToMax() {
        mockPreferences(MIN_BALANCE, MAX_BALANCE, DEFAULT_BALANCE);
        mockCrudUpdate(MAX_BALANCE, MAX_BALANCE - BALANCE_NEAR_MAX);

        final PlatformUsageCreditsUpdateEvent result = service.updateByEvent(increaseEvent(200));

        assertThat(result.getValue(), is(MAX_BALANCE - BALANCE_NEAR_MAX));
    }

    @Test
    public void shouldReturnZeroValueWhenAlreadyAtMax() {
        mockPreferences(MIN_BALANCE, MAX_BALANCE, DEFAULT_BALANCE);
        mockCrudUpdate(MAX_BALANCE, 0);

        final PlatformUsageCreditsUpdateEvent result = service.updateByEvent(increaseEvent(200));

        assertThat(result.getValue(), is(0));
    }

    @Test
    public void shouldDeductBalanceWithinBounds() {
        mockPreferences(MIN_BALANCE, MAX_BALANCE, DEFAULT_BALANCE);
        mockCrudUpdate(CURRENT_BALANCE - EVENT_VALUE, -EVENT_VALUE);

        final PlatformUsageCreditsUpdateEvent result = service.updateByEvent(deductionEvent(EVENT_VALUE));

        assertThat(result.getValue(), is(EVENT_VALUE));
    }

    @Test
    public void shouldClampDeductionToMin() {
        mockPreferences(MIN_BALANCE, MAX_BALANCE, DEFAULT_BALANCE);
        mockCrudUpdate(MIN_BALANCE, MIN_BALANCE - BALANCE_NEAR_MIN);

        final PlatformUsageCreditsUpdateEvent result = service.updateByEvent(deductionEvent(EVENT_VALUE));

        assertThat(result.getValue(), is(BALANCE_NEAR_MIN - MIN_BALANCE));
    }

    @Test
    public void shouldReturnZeroValueWhenAlreadyAtMin() {
        mockPreferences(MIN_BALANCE, MAX_BALANCE, DEFAULT_BALANCE);
        mockCrudUpdate(MIN_BALANCE, 0);

        final PlatformUsageCreditsUpdateEvent result = service.updateByEvent(deductionEvent(100));

        assertThat(result.getValue(), is(0));
    }

    @Test
    public void shouldUseDefaultBalanceWhenNoRowExists() {
        mockPreferences(MIN_BALANCE, MAX_BALANCE, DEFAULT_BALANCE);
        mockCrudUpdate(DEFAULT_BALANCE + EVENT_VALUE, EVENT_VALUE);

        final PlatformUsageCreditsUpdateEvent result = service.updateByEvent(increaseEvent(EVENT_VALUE));

        assertThat(result.getValue(), is(EVENT_VALUE));
    }

    @Test
    public void shouldDelegateToUpdateByEventOnCrudService() {
        mockPreferences(MIN_BALANCE, MAX_BALANCE, DEFAULT_BALANCE);
        mockCrudUpdate(CURRENT_BALANCE + EVENT_VALUE, EVENT_VALUE);

        service.updateByEvent(increaseEvent(EVENT_VALUE));

        verify(crudService).updateByEvent(any(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    public void shouldNotifyWhenBalanceDropsBelowThreshold() {
        // BALANCE_BELOW_THRESHOLD=700 < absoluteValue=768 → notify
        mockPreferences(MIN_BALANCE, MAX_BALANCE, DEFAULT_BALANCE);
        mockCrudUpdate(BALANCE_BELOW_THRESHOLD, -EVENT_VALUE);

        service.updateByEvent(deductionEvent(EVENT_VALUE));

        verify(notificationManager).notifyLowUsageCredits(USER_ID, BALANCE_BELOW_THRESHOLD);
    }

    @Test
    public void shouldNotNotifyWhenBalanceIsAboveThreshold() {
        // BALANCE_ABOVE_THRESHOLD=800 >= absoluteValue=768 → no notify
        mockPreferences(MIN_BALANCE, MAX_BALANCE, DEFAULT_BALANCE);
        mockCrudUpdate(BALANCE_ABOVE_THRESHOLD, EVENT_VALUE);

        service.updateByEvent(increaseEvent(EVENT_VALUE));

        verify(notificationManager, never()).notifyLowUsageCredits(any(), anyInt());
    }

    @Test
    public void shouldNotNotifyWhenEventHasNoEffect() {
        // balance already at MAX, INCREASE → actualValue=0, no notify
        mockPreferences(MIN_BALANCE, MAX_BALANCE, DEFAULT_BALANCE);
        mockCrudUpdate(MAX_BALANCE, 0);

        service.updateByEvent(increaseEvent(EVENT_VALUE));

        verify(notificationManager, never()).notifyLowUsageCredits(any(), anyInt());
    }

    private void mockPreferences(final int min, final int max, final int defaultValue) {
        final PipelineUser user = pipelineUser();
        doReturn(user).when(userManager).load(USER_ID);
        doReturn(new ContextualPreference(SystemPreferences.USAGE_CREDITS_MIN.getKey(), String.valueOf(min)))
                .when(contextualPreferenceManager)
                .search(Collections.singletonList(SystemPreferences.USAGE_CREDITS_MIN.getKey()), user);
        doReturn(new ContextualPreference(SystemPreferences.USAGE_CREDITS_MAX.getKey(), String.valueOf(max)))
                .when(contextualPreferenceManager)
                .search(Collections.singletonList(SystemPreferences.USAGE_CREDITS_MAX.getKey()), user);
        doReturn(new ContextualPreference(SystemPreferences.USAGE_CREDITS_DEFAULT.getKey(),
                        String.valueOf(defaultValue)))
                .when(contextualPreferenceManager)
                .search(Collections.singletonList(SystemPreferences.USAGE_CREDITS_DEFAULT.getKey()), user);
        doReturn(new ContextualPreference(SystemPreferences.USAGE_CREDITS_NOTIFICATION_THRESHOLD.getKey(),
                        String.valueOf(NOTIFICATION_THRESHOLD)))
                .when(contextualPreferenceManager)
                .search(Collections.singletonList(
                        SystemPreferences.USAGE_CREDITS_NOTIFICATION_THRESHOLD.getKey()), user);
    }

    private void mockCrudUpdate(final int newBalance, final int actualDelta) {
        doReturn(Pair.of(newBalance, actualDelta)).when(crudService)
                .updateByEvent(any(), anyInt(), anyInt(), anyInt(), anyInt());
    }
}
