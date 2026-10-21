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

import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateAction;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateEvent;
import com.epam.pipeline.vo.SecuredEntityVO;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PlatformUsageCreditsEventExporterTest {

    private static final LocalDateTime DATE = LocalDateTime.of(2026, 1, 15, 10, 30, 0);
    private static final long ENTITY_ID = 42L;
    private static final String ENTITY_CLASS = "PipelineRun";
    private static final Long USER_ID = 1L;
    private static final Long RULE_ID = 2L;
    private static final int VALUE = 100;
    private static final String MESSAGE = "test message";

    private final PlatformUsageCreditsEventExporter exporter = new PlatformUsageCreditsEventExporter();

    @Test
    public void headerReturnsEightNamedColumns() {
        final String[] header = exporter.header();

        assertThat(header).containsExactly(
                "Timestamp", "User ID", "Rule ID", "Entity Class", "Entity ID", "Type", "Value", "Message");
    }

    @Test
    public void linesReturnsOneLinePerEvent() {
        final List<String[]> lines = exporter.lines(Arrays.asList(minimalEvent(), minimalEvent()));

        assertThat(lines).hasSize(2);
    }

    @Test
    public void linesReturnsEmptyListForEmptyInput() {
        assertThat(exporter.lines(Collections.emptyList())).isEmpty();
    }

    @Test
    public void linesFormatsAllFieldsForFullyPopulatedEvent() {
        final PlatformUsageCreditsUpdateEvent event = PlatformUsageCreditsUpdateEvent.builder()
                .createdDate(DATE)
                .userId(USER_ID)
                .ruleId(RULE_ID)
                .entity(new SecuredEntityVO(ENTITY_ID, ENTITY_CLASS))
                .incidentType(PlatformUsageCreditsUpdateAction.ActionType.DEDUCTION)
                .value(VALUE)
                .message(MESSAGE)
                .build();

        final String[] line = exporter.lines(Collections.singletonList(event)).get(0);

        assertThat(line).hasSize(8);
        assertThat(line[0]).isEqualTo("2026-01-15 10:30:00");
        assertThat(line[1]).isEqualTo(String.valueOf(USER_ID));
        assertThat(line[2]).isEqualTo(String.valueOf(RULE_ID));
        assertThat(line[3]).isEqualTo(ENTITY_CLASS);
        assertThat(line[4]).isEqualTo(String.valueOf(ENTITY_ID));
        assertThat(line[5]).isEqualTo(PlatformUsageCreditsUpdateAction.ActionType.DEDUCTION.name());
        assertThat(line[6]).isEqualTo(String.valueOf(VALUE));
        assertThat(line[7]).isEqualTo(MESSAGE);
    }

    @Test
    public void linesReplacesNullFieldsWithEmptyStrings() {
        final PlatformUsageCreditsUpdateEvent event = PlatformUsageCreditsUpdateEvent.builder()
                .value(VALUE)
                .build();

        final String[] line = exporter.lines(Collections.singletonList(event)).get(0);

        assertThat(line[0]).isEmpty();  // null createdDate
        assertThat(line[1]).isEmpty();  // null userId
        assertThat(line[2]).isEmpty();  // null ruleId
        assertThat(line[3]).isEmpty();  // null entity → empty entityClass
        assertThat(line[4]).isEmpty();  // null entity → empty entityId
        assertThat(line[5]).isEmpty();  // null incidentType
        assertThat(line[6]).isEqualTo(String.valueOf(VALUE));
        assertThat(line[7]).isEmpty();  // null message
    }

    private PlatformUsageCreditsUpdateEvent minimalEvent() {
        return PlatformUsageCreditsUpdateEvent.builder().value(VALUE).build();
    }
}
