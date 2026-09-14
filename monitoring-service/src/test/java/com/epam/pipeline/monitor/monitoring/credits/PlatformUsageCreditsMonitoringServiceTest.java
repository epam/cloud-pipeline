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

package com.epam.pipeline.monitor.monitoring.credits;

import com.epam.pipeline.entity.credits.PlatformUsageCreditsUpdateAction;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUpdateEvent;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUpdateRule;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUpdateRuleType;
import com.epam.pipeline.monitor.monitoring.credits.handler.PlatformUsageCreditsRuleHandler;
import com.epam.pipeline.monitor.rest.CloudPipelineAPIClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformUsageCreditsMonitoringServiceTest {

    private static final String UNCHECKED = "unchecked";
    private static final String ENABLE_PREF = "enable.pref";
    private static final String MODE_PREF = "mode.pref";
    private static final Long RULE_ID = 1L;
    private static final int ACTION_VALUE = 50;
    private static final String ACTION_MESSAGE = "Test deduction";

    private Path tempDir;
    private Path lastExecFile;

    private final CloudPipelineAPIClient client = mock(CloudPipelineAPIClient.class);
    private final PlatformUsageCreditsRuleHandler handler = mock(PlatformUsageCreditsRuleHandler.class);

    private PlatformUsageCreditsMonitoringService monitor;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("credit-monitor-test");
        lastExecFile = tempDir.resolve("last_exec.txt");
        when(handler.getRuleType()).thenReturn(PlatformUsageCreditsUpdateRuleType.RUN_STATE);
        when(client.getStringPreference(MODE_PREF)).thenReturn("ON");
        monitor = new PlatformUsageCreditsMonitoringService(
                ENABLE_PREF, MODE_PREF, lastExecFile.toString(), client, Collections.singletonList(handler));
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

        verify(client, never()).loadAllPlatformUsageCreditsRules();
        verify(handler, never()).process(any(), any(), any());
    }

    @Test
    void monitorSkipsWhenCreditsModeIsOff() {
        when(client.getBooleanPreference(ENABLE_PREF)).thenReturn(true);
        when(client.getStringPreference(MODE_PREF)).thenReturn("OFF");

        monitor.monitor();

        verify(client, never()).loadAllPlatformUsageCreditsRules();
        verify(handler, never()).process(any(), any(), any());
    }

    @Test
    void noRulesFound() {
        when(client.getBooleanPreference(ENABLE_PREF)).thenReturn(true);
        when(client.loadAllPlatformUsageCreditsRules()).thenReturn(Collections.emptyList());

        monitor.monitor();

        verify(handler, never()).process(any(), any(), any());
        verify(client, never()).savePlatformUsageCreditsEvents(any());
    }

    @Test
    void delegatesRulesToHandlerByType() {
        final PlatformUsageCreditsUpdateRule rule = runStateRule();
        when(client.getBooleanPreference(ENABLE_PREF)).thenReturn(true);
        when(client.loadAllPlatformUsageCreditsRules()).thenReturn(Collections.singletonList(rule));
        when(handler.process(any(), any(), any())).thenReturn(Collections.emptyList());

        monitor.monitor();

        verify(handler).process(eq(Collections.singletonList(rule)), isNull(), any(LocalDateTime.class));
    }

    @Test
    void savesEventsReturnedByHandler() {
        final PlatformUsageCreditsUpdateEvent event = PlatformUsageCreditsUpdateEvent.builder()
                .ruleId(RULE_ID).build();
        when(client.getBooleanPreference(ENABLE_PREF)).thenReturn(true);
        when(client.loadAllPlatformUsageCreditsRules())
                .thenReturn(Collections.singletonList(runStateRule()));
        when(handler.process(any(), any(), any())).thenReturn(Collections.singletonList(event));

        monitor.monitor();

        verify(client).savePlatformUsageCreditsEvents(Collections.singletonList(event));
    }

    @Test
    void skipsRuleTypeWithNoRegisteredHandler() {
        final PlatformUsageCreditsMonitoringService monitorWithNoHandlers =
                new PlatformUsageCreditsMonitoringService(
                        ENABLE_PREF, MODE_PREF, lastExecFile.toString(), client, Collections.emptyList());
        when(client.getBooleanPreference(ENABLE_PREF)).thenReturn(true);
        when(client.loadAllPlatformUsageCreditsRules())
                .thenReturn(Collections.singletonList(runStateRule()));

        monitorWithNoHandlers.monitor();

        verify(client, never()).savePlatformUsageCreditsEvents(any());
    }

    @Test
    void passesPersistedLastExecTimeToHandler() throws IOException {
        final LocalDateTime expectedFrom = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        Files.write(lastExecFile, ("2026-01-01T10:00:00.000" + System.lineSeparator()).getBytes());

        when(client.getBooleanPreference(ENABLE_PREF)).thenReturn(true);
        when(client.loadAllPlatformUsageCreditsRules())
                .thenReturn(Collections.singletonList(runStateRule()));
        when(handler.process(any(), any(), any())).thenReturn(Collections.emptyList());

        monitor.monitor();

        @SuppressWarnings(UNCHECKED)
        final ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(handler).process(any(), fromCaptor.capture(), any());
        assertEquals(expectedFrom, fromCaptor.getValue());
    }

    @Test
    void noEventsReturnedMeansNoSaveCall() {
        when(client.getBooleanPreference(ENABLE_PREF)).thenReturn(true);
        when(client.loadAllPlatformUsageCreditsRules())
                .thenReturn(Collections.singletonList(runStateRule()));
        when(handler.process(any(), isNull(), any())).thenReturn(Collections.emptyList());

        monitor.monitor();

        verify(client, never()).savePlatformUsageCreditsEvents(any());
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
}
