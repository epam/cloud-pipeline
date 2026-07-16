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
import com.epam.pipeline.entity.platformusage.PlatformUsageCreditEventFilterVO;
import com.epam.pipeline.entity.platformusage.PlatformUsageCreditUpdateAction;
import com.epam.pipeline.entity.platformusage.PlatformUsageCreditUpdateEvent;
import com.epam.pipeline.entity.platformusage.PlatformUsageCreditUpdateRule;
import com.epam.pipeline.entity.platformusage.PlatformUsageCreditUpdateRuleType;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.monitor.rest.CloudPipelineAPIClient;
import com.epam.pipeline.vo.PagingRunFilterVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

class PlatformUsageCreditMonitoringServiceTest {

    private static final String ENABLE_PREF = "enable.pref";
    private static final Long RULE_ID = 1L;
    private static final Long RUN_ID_1 = 10L;
    private static final Long RUN_ID_2 = 20L;
    private static final Long USER_ID = 100L;
    private static final String OWNER = "testUser";
    private static final int ACTION_VALUE = 50;
    private static final String ACTION_MESSAGE = "Test deduction";

    private Path tempDir;
    private Path lastExecFile;

    private final CloudPipelineAPIClient client = mock(CloudPipelineAPIClient.class);
    private final PlatformUsageCreditUpdateRuleEvaluator evaluator =
            mock(PlatformUsageCreditUpdateRuleEvaluator.class);

    private PlatformUsageCreditMonitoringService monitor;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("credit-monitor-test");
        lastExecFile = tempDir.resolve("last_exec.txt");
        monitor = new PlatformUsageCreditMonitoringService(
                ENABLE_PREF, lastExecFile.toString(), client, evaluator);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(lastExecFile);
        Files.deleteIfExists(tempDir);
    }

    @Test
    void monitorDisabled() {
        when(client.getBooleanPreference(ENABLE_PREF)).thenReturn(false);

        monitor.monitor();

        verify(client, never()).loadAllPlatformUsageCreditRules();
        verifyZeroInteractions(evaluator);
    }

    @Test
    void noRulesFound() {
        when(client.getBooleanPreference(ENABLE_PREF)).thenReturn(true);
        when(client.loadAllPlatformUsageCreditRules()).thenReturn(Collections.emptyList());

        monitor.monitor();

        verify(client, never()).filterRuns(any());
        verify(client, never()).savePlatformUsageCreditEvents(any());
    }

    @Test
    void noRunsFound() {
        when(client.getBooleanPreference(ENABLE_PREF)).thenReturn(true);
        when(client.loadAllPlatformUsageCreditRules())
                .thenReturn(Collections.singletonList(runStateRule()));
        when(client.filterRuns(any()))
                .thenReturn(Collections.emptyList());

        monitor.monitor();

        verify(client, never()).filterPlatformUsageCreditEvents(any());
        verify(client, never()).savePlatformUsageCreditEvents(any());
    }

    @Test
    void createsEventsForMatchingRuns() {
        final PlatformUsageCreditUpdateRule rule = runStateRule();
        final PipelineRun run = run(RUN_ID_1, OWNER);
        final PipelineUser user = user(USER_ID);

        when(client.getBooleanPreference(ENABLE_PREF)).thenReturn(true);
        when(client.loadAllPlatformUsageCreditRules())
                .thenReturn(Collections.singletonList(rule));
        when(client.filterRuns(any()))
                .thenReturn(Collections.singletonList(run));
        when(client.loadUserByName(OWNER)).thenReturn(user);
        when(evaluator.matches(any(), any(), any())).thenReturn(true);
        when(client.filterPlatformUsageCreditEvents(any()))
                .thenReturn(Collections.emptyList());

        monitor.monitor();

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<PlatformUsageCreditUpdateEvent>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(client).savePlatformUsageCreditEvents(captor.capture());

        final List<PlatformUsageCreditUpdateEvent> saved = captor.getValue();
        assertEquals(1, saved.size());
        final PlatformUsageCreditUpdateEvent event = saved.get(0);
        assertEquals(USER_ID, event.getUserId());
        assertEquals(RULE_ID, event.getRuleId());
        assertEquals(RUN_ID_1, event.getRunId());
        assertEquals(PlatformUsageCreditUpdateAction.ActionType.DEDUCTION, event.getIncidentType());
        assertEquals(ACTION_VALUE, event.getValue());
        assertEquals(ACTION_MESSAGE, event.getMessage());
    }

    @Test
    void deduplicatesAlreadyProcessedRuns() {
        final PlatformUsageCreditUpdateRule rule = runStateRule();
        final PipelineRun run1 = run(RUN_ID_1, OWNER);
        final PipelineRun run2 = run(RUN_ID_2, OWNER);

        when(client.getBooleanPreference(ENABLE_PREF)).thenReturn(true);
        when(client.loadAllPlatformUsageCreditRules())
                .thenReturn(Collections.singletonList(rule));
        when(client.filterRuns(any()))
                .thenReturn(Arrays.asList(run1, run2));
        when(client.loadUserByName(OWNER)).thenReturn(user(USER_ID));
        when(evaluator.matches(any(), any(), any())).thenReturn(true);
        // RUN_ID_1 already has an event for this rule
        when(client.filterPlatformUsageCreditEvents(any()))
                .thenReturn(Collections.singletonList(
                        PlatformUsageCreditUpdateEvent.builder()
                                .runId(RUN_ID_1).ruleId(RULE_ID).build()));

        monitor.monitor();

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<PlatformUsageCreditUpdateEvent>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(client).savePlatformUsageCreditEvents(captor.capture());

        final List<PlatformUsageCreditUpdateEvent> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals(RUN_ID_2, saved.get(0).getRunId());
    }

    @Test
    void filterEventsCalledWithCorrectRunIdsAndRuleId() {
        final PlatformUsageCreditUpdateRule rule = runStateRule();
        final PipelineRun run1 = run(RUN_ID_1, OWNER);
        final PipelineRun run2 = run(RUN_ID_2, OWNER);

        when(client.getBooleanPreference(ENABLE_PREF)).thenReturn(true);
        when(client.loadAllPlatformUsageCreditRules())
                .thenReturn(Collections.singletonList(rule));
        when(client.filterRuns(any()))
                .thenReturn(Arrays.asList(run1, run2));
        when(client.loadUserByName(OWNER)).thenReturn(user(USER_ID));
        when(evaluator.matches(any(), any(), any())).thenReturn(true);
        when(client.filterPlatformUsageCreditEvents(any()))
                .thenReturn(Collections.emptyList());

        monitor.monitor();

        verify(client).filterPlatformUsageCreditEvents(
                PlatformUsageCreditEventFilterVO.builder()
                        .runIds(Arrays.asList(RUN_ID_1, RUN_ID_2))
                        .ruleId(RULE_ID)
                        .build());
    }

    @Test
    void skipsRunsNotMatchingRule() {
        when(client.getBooleanPreference(ENABLE_PREF)).thenReturn(true);
        when(client.loadAllPlatformUsageCreditRules())
                .thenReturn(Collections.singletonList(runStateRule()));
        when(client.filterRuns(any()))
                .thenReturn(Collections.singletonList(run(RUN_ID_1, OWNER)));
        when(client.loadUserByName(OWNER)).thenReturn(user(USER_ID));
        when(evaluator.matches(any(), any(), any())).thenReturn(false);

        monitor.monitor();

        verify(client, never()).filterPlatformUsageCreditEvents(any());
        verify(client, never()).savePlatformUsageCreditEvents(any());
    }

    @Test
    void skipsUnknownUser() {
        when(client.getBooleanPreference(ENABLE_PREF)).thenReturn(true);
        when(client.loadAllPlatformUsageCreditRules())
                .thenReturn(Collections.singletonList(runStateRule()));
        when(client.filterRuns(any()))
                .thenReturn(Collections.singletonList(run(RUN_ID_1, OWNER)));
        when(client.loadUserByName(OWNER)).thenReturn(null);

        monitor.monitor();

        verifyZeroInteractions(evaluator);
        verify(client, never()).savePlatformUsageCreditEvents(any());
    }

    @Test
    void firstRunQueriesOnlyActiveStatuses() {
        when(client.getBooleanPreference(ENABLE_PREF)).thenReturn(true);
        when(client.loadAllPlatformUsageCreditRules())
                .thenReturn(Collections.singletonList(runStateRule()));
        when(client.filterRuns(any())).thenReturn(Collections.emptyList());

        monitor.monitor();

        // Only one filterRuns call (active statuses); no completed-runs call when there is no prior timestamp.
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<PagingRunFilterVO> captor = ArgumentCaptor.forClass(PagingRunFilterVO.class);
        verify(client, times(1)).filterRuns(captor.capture());
        assertEquals(
                Arrays.asList(TaskStatus.RUNNING, TaskStatus.PAUSING, TaskStatus.PAUSED, TaskStatus.RESUMING),
                captor.getValue().getStatuses());
        assertNull(captor.getValue().getStartDateFrom());
    }

    @Test
    void subsequentRunAlsoQueriesCompletedRunsWithTimestamp() {
        when(client.getBooleanPreference(ENABLE_PREF)).thenReturn(true);
        when(client.loadAllPlatformUsageCreditRules())
                .thenReturn(Collections.singletonList(runStateRule()));
        when(client.filterRuns(any())).thenReturn(Collections.emptyList());

        monitor.monitor(); // first call — no file, only active query, writes timestamp
        monitor.monitor(); // second call — active query + completed query with startDateFrom

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<PagingRunFilterVO> captor = ArgumentCaptor.forClass(PagingRunFilterVO.class);
        // first call: 1 filterRuns; second call: 2 filterRuns → 3 total
        verify(client, times(3)).filterRuns(captor.capture());
        final PagingRunFilterVO completedFilter = captor.getAllValues().get(2);
        assertEquals(
                Arrays.asList(TaskStatus.SUCCESS, TaskStatus.FAILURE, TaskStatus.STOPPED),
                completedFilter.getStatuses());
        assertNotNull(completedFilter.getStartDateFrom());
    }

    @Test
    void multipleRunsAcrossDifferentOwners() {
        final PlatformUsageCreditUpdateRule rule = runStateRule();
        final String owner2 = "anotherUser";
        final Long userId2 = 200L;
        final PipelineRun run1 = run(RUN_ID_1, OWNER);
        final PipelineRun run2 = run(RUN_ID_2, owner2);

        when(client.getBooleanPreference(ENABLE_PREF)).thenReturn(true);
        when(client.loadAllPlatformUsageCreditRules())
                .thenReturn(Collections.singletonList(rule));
        when(client.filterRuns(any()))
                .thenReturn(Arrays.asList(run1, run2));
        when(client.loadUserByName(OWNER)).thenReturn(user(USER_ID));
        when(client.loadUserByName(owner2)).thenReturn(user(userId2));
        when(evaluator.matches(any(), any(), any())).thenReturn(true);
        when(client.filterPlatformUsageCreditEvents(any()))
                .thenReturn(Collections.emptyList());

        monitor.monitor();

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<PlatformUsageCreditUpdateEvent>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(client).savePlatformUsageCreditEvents(captor.capture());

        final List<PlatformUsageCreditUpdateEvent> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertEquals(USER_ID, saved.stream()
                .filter(e -> RUN_ID_1.equals(e.getRunId())).findFirst()
                .map(PlatformUsageCreditUpdateEvent::getUserId).orElse(null));
        assertEquals(userId2, saved.stream()
                .filter(e -> RUN_ID_2.equals(e.getRunId())).findFirst()
                .map(PlatformUsageCreditUpdateEvent::getUserId).orElse(null));
    }

    private static PlatformUsageCreditUpdateRule runStateRule() {
        return PlatformUsageCreditUpdateRule.builder()
                .id(RULE_ID)
                .strategyType(PlatformUsageCreditUpdateRuleType.RUN_STATE)
                .action(PlatformUsageCreditUpdateAction.builder()
                        .type(PlatformUsageCreditUpdateAction.ActionType.DEDUCTION)
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
