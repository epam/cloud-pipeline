/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.monitor.monitoring.platformusage;

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.vo.platformusage.PlatformUsageCreditsEventFilterVO;
import com.epam.pipeline.entity.platformusage.PlatformUsageCreditsUpdateAction;
import com.epam.pipeline.entity.platformusage.PlatformUsageCreditsUpdateEvent;
import com.epam.pipeline.entity.platformusage.PlatformUsageCreditsUpdateRule;
import com.epam.pipeline.entity.platformusage.PlatformUsageCreditsUpdateRuleType;
import com.epam.pipeline.vo.SecuredEntityVO;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.monitor.rest.CloudPipelineAPIClient;
import com.epam.pipeline.vo.PagingRunFilterVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class PipelineRunUsageCreditsRuleHandlerTest {

    private static final String UNCHECKED = "unchecked";
    private static final Long RULE_ID = 1L;
    private static final Long RUN_ID_1 = 10L;
    private static final Long RUN_ID_2 = 20L;
    private static final Long USER_ID = 100L;
    private static final String OWNER = "testUser";
    private static final int ACTION_VALUE = 50;
    private static final String ACTION_MESSAGE = "Test deduction";

    private final CloudPipelineAPIClient client = mock(CloudPipelineAPIClient.class);
    private final PlatformUsageCreditsUpdateRuleEvaluator evaluator =
            mock(PlatformUsageCreditsUpdateRuleEvaluator.class);

    private final PipelineRunUsageCreditsRuleHandler handler =
            new PipelineRunUsageCreditsRuleHandler(client, evaluator);

    private final LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0, 0);

    @Test
    void noRunsFound() {
        when(client.filterRuns(any())).thenReturn(Collections.emptyList());

        final List<PlatformUsageCreditsUpdateEvent> result =
                handler.process(Collections.singletonList(runStateRule()), null, now);

        assertEquals(Collections.emptyList(), result);
        verify(client, never()).filterPlatformUsageCreditsEvents(any());
    }

    @Test
    void createsEventsForMatchingRuns() {
        final PlatformUsageCreditsUpdateRule rule = runStateRule();
        final PipelineRun run = run(RUN_ID_1, OWNER);
        final PipelineUser user = user(USER_ID);

        when(client.filterRuns(any())).thenReturn(Collections.singletonList(run));
        when(client.loadUserByName(OWNER)).thenReturn(user);
        when(evaluator.matches(any(), any(), any())).thenReturn(true);
        when(client.filterPlatformUsageCreditsEvents(any())).thenReturn(Collections.emptyList());

        final List<PlatformUsageCreditsUpdateEvent> result =
                handler.process(Collections.singletonList(rule), null, now);

        assertEquals(1, result.size());
        final PlatformUsageCreditsUpdateEvent event = result.get(0);
        assertEquals(USER_ID, event.getUserId());
        assertEquals(RULE_ID, event.getRuleId());
        assertNotNull(event.getEntity());
        assertEquals(RUN_ID_1.longValue(), event.getEntity().getEntityId());
        assertEquals(PlatformUsageCreditsUpdateAction.ActionType.DEDUCTION, event.getIncidentType());
        assertEquals(ACTION_VALUE, event.getValue());
        assertEquals(ACTION_MESSAGE, event.getMessage());
    }

    @Test
    void deduplicatesAlreadyProcessedRuns() {
        final PlatformUsageCreditsUpdateRule rule = runStateRule();
        final PipelineRun run1 = run(RUN_ID_1, OWNER);
        final PipelineRun run2 = run(RUN_ID_2, OWNER);

        when(client.filterRuns(any())).thenReturn(Arrays.asList(run1, run2));
        when(client.loadUserByName(OWNER)).thenReturn(user(USER_ID));
        when(evaluator.matches(any(), any(), any())).thenReturn(true);
        when(client.filterPlatformUsageCreditsEvents(any()))
                .thenReturn(Collections.singletonList(
                        PlatformUsageCreditsUpdateEvent.builder()
                                .entity(SecuredEntityVO.from(PipelineRun.class, RUN_ID_1))
                                .ruleId(RULE_ID).build()));

        final List<PlatformUsageCreditsUpdateEvent> result =
                handler.process(Collections.singletonList(rule), null, now);

        assertEquals(1, result.size());
        assertNotNull(result.get(0).getEntity());
        assertEquals(RUN_ID_2.longValue(), result.get(0).getEntity().getEntityId());
    }

    @Test
    void filterEventsCalledWithCorrectRunIdsAndRuleId() {
        final PlatformUsageCreditsUpdateRule rule = runStateRule();
        final PipelineRun run1 = run(RUN_ID_1, OWNER);
        final PipelineRun run2 = run(RUN_ID_2, OWNER);

        when(client.filterRuns(any())).thenReturn(Arrays.asList(run1, run2));
        when(client.loadUserByName(OWNER)).thenReturn(user(USER_ID));
        when(evaluator.matches(any(), any(), any())).thenReturn(true);
        when(client.filterPlatformUsageCreditsEvents(any())).thenReturn(Collections.emptyList());

        handler.process(Collections.singletonList(rule), null, now);

        verify(client).filterPlatformUsageCreditsEvents(
                PlatformUsageCreditsEventFilterVO.builder()
                        .entities(Arrays.asList(
                                SecuredEntityVO.from(PipelineRun.class, RUN_ID_1),
                                SecuredEntityVO.from(PipelineRun.class, RUN_ID_2)))
                        .ruleId(RULE_ID)
                        .build());
    }

    @Test
    void skipsRunsNotMatchingRule() {
        when(client.filterRuns(any())).thenReturn(Collections.singletonList(run(RUN_ID_1, OWNER)));
        when(client.loadUserByName(OWNER)).thenReturn(user(USER_ID));
        when(evaluator.matches(any(), any(), any())).thenReturn(false);

        final List<PlatformUsageCreditsUpdateEvent> result =
                handler.process(Collections.singletonList(runStateRule()), null, now);

        assertEquals(Collections.emptyList(), result);
        verify(client, never()).filterPlatformUsageCreditsEvents(any());
    }

    @Test
    void skipsUnknownUser() {
        when(client.filterRuns(any())).thenReturn(Collections.singletonList(run(RUN_ID_1, OWNER)));
        when(client.loadUserByName(OWNER)).thenReturn(null);

        final List<PlatformUsageCreditsUpdateEvent> result =
                handler.process(Collections.singletonList(runStateRule()), null, now);

        assertEquals(Collections.emptyList(), result);
        verifyZeroInteractions(evaluator);
    }

    @Test
    void processWithNullFromUsesOnlyActiveStatuses() {
        when(client.filterRuns(any())).thenReturn(Collections.emptyList());

        handler.process(Collections.singletonList(runStateRule()), null, now);

        @SuppressWarnings(UNCHECKED)
        final ArgumentCaptor<PagingRunFilterVO> captor = ArgumentCaptor.forClass(PagingRunFilterVO.class);
        verify(client, times(1)).filterRuns(captor.capture());
        assertEquals(
                Arrays.asList(TaskStatus.RUNNING, TaskStatus.PAUSING, TaskStatus.PAUSED, TaskStatus.RESUMING),
                captor.getValue().getStatuses());
        assertNull(captor.getValue().getStartDateFrom());
    }

    @Test
    void processWithFromDateAlsoQueriesCompletedRuns() {
        final LocalDateTime from = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        when(client.filterRuns(any())).thenReturn(Collections.emptyList());

        handler.process(Collections.singletonList(runStateRule()), from, now);

        @SuppressWarnings(UNCHECKED)
        final ArgumentCaptor<PagingRunFilterVO> captor = ArgumentCaptor.forClass(PagingRunFilterVO.class);
        verify(client, times(2)).filterRuns(captor.capture());
        final PagingRunFilterVO completedFilter = captor.getAllValues().get(1);
        assertEquals(
                Arrays.asList(TaskStatus.SUCCESS, TaskStatus.FAILURE, TaskStatus.STOPPED),
                completedFilter.getStatuses());
        assertNotNull(completedFilter.getStartDateFrom());
    }

    @Test
    void multipleRunsAcrossDifferentOwners() {
        final String owner2 = "anotherUser";
        final Long userId2 = 200L;
        final PipelineRun run1 = run(RUN_ID_1, OWNER);
        final PipelineRun run2 = run(RUN_ID_2, owner2);

        when(client.filterRuns(any())).thenReturn(Arrays.asList(run1, run2));
        when(client.loadUserByName(OWNER)).thenReturn(user(USER_ID));
        when(client.loadUserByName(owner2)).thenReturn(user(userId2));
        when(evaluator.matches(any(), any(), any())).thenReturn(true);
        when(client.filterPlatformUsageCreditsEvents(any())).thenReturn(Collections.emptyList());

        final List<PlatformUsageCreditsUpdateEvent> result =
                handler.process(Collections.singletonList(runStateRule()), null, now);

        assertEquals(2, result.size());
        assertEquals(USER_ID, result.stream()
                .filter(e -> e.getEntity() != null && e.getEntity().getEntityId() == RUN_ID_1).findFirst()
                .map(PlatformUsageCreditsUpdateEvent::getUserId).orElse(null));
        assertEquals(userId2, result.stream()
                .filter(e -> e.getEntity() != null && e.getEntity().getEntityId() == RUN_ID_2).findFirst()
                .map(PlatformUsageCreditsUpdateEvent::getUserId).orElse(null));
    }

    private static PlatformUsageCreditsUpdateRule runStateRule() {
        return PlatformUsageCreditsUpdateRule.builder()
                .id(RULE_ID)
                .ruleType(PlatformUsageCreditsUpdateRuleType.RUN_STATE)
                .action(PlatformUsageCreditsUpdateAction.builder()
                        .type(PlatformUsageCreditsUpdateAction.ActionType.DEDUCTION)
                        .value(ACTION_VALUE)
                        .message(ACTION_MESSAGE)
                        .build())
                .build();
    }

    private static PipelineRun run(final Long id, final String owner) {
        final PipelineRun run = new PipelineRun();
        run.setId(id);
        run.setOwner(owner);
        return run;
    }

    private static PipelineUser user(final Long id) {
        final PipelineUser user = new PipelineUser();
        user.setId(id);
        return user;
    }
}
