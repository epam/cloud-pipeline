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

import com.epam.pipeline.utils.condition.ConditionType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.entity.platformusage.PlatformUsageCreditsUpdateAction;
import com.epam.pipeline.entity.platformusage.PlatformUsageCreditsUpdateRule;
import com.epam.pipeline.entity.platformusage.PlatformUsageCreditsUpdateRuleType;
import com.epam.pipeline.utils.condition.ConditionExpression;
import com.epam.pipeline.utils.condition.evaluation.TagFieldEvaluationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformUsageCreditsUpdateRuleEvaluatorTest {

    private static final String EQ  = "=";
    private static final String NEQ = "!=";
    private static final String GT  = ">";
    private static final String LTE = "<=";

    private static final String FIELD_RUN_TAG       = "run.tag";
    private static final String FIELD_RUN_STATUS    = "run.status";
    private static final String FIELD_STATUS_ALIAS  = "status";
    private static final String FIELD_NODE_TYPE     = "node.type";
    private static final String FIELD_RUN_SPOT      = "run.spot";
    private static final String FIELD_RUN_REGION_ID = "run.region_id";
    private static final String FIELD_NODE_DISK     = "node.disk";
    private static final String FIELD_RUN_OWNER     = "run.owner";
    private static final String FIELD_OWNER_ALIAS   = "owner";
    private static final String FIELD_OWNER_GROUP   = "run.owner.authorities";
    private static final String FIELD_UNKNOWN       = "run.no_such_field";

    private static final String TAG_IDLE         = "IDLE";
    private static final String TAG_IDLE_LOWER   = "idle";
    private static final String TAG_LONG_RUNNING = "LONG-RUNNING";

    private static final String NODE_TYPE_M5_XLARGE      = "m5.xlarge";
    private static final String NODE_TYPE_M5_XLARGE_CAPS = "M5.XLARGE";
    private static final String NODE_TYPE_C5_XLARGE      = "c5.xlarge";
    private static final String PATTERN_M5_WILDCARD      = "m5.*";
    private static final String PATTERN_C5_WILDCARD      = "c5.*";

    private static final long   REGION_1  = 1L;
    private static final long   REGION_2  = 2L;
    private static final long   REGION_3  = 3L;
    private static final long   REGION_5  = 5L;
    private static final String REGION_1S = "1";
    private static final String REGION_3S = "3";
    private static final int    DISK_50   = 50;
    private static final int    DISK_200  = 200;
    private static final String DISK_100S = "100";

    private static final String OWNER_USER1           = "user1";
    private static final String OWNER_JOHN_DOE        = "john.doe";
    private static final String PATTERN_JOHN_WILDCARD = "john.*";
    private static final String GROUP_TEAM_A          = "team-a";
    private static final String GROUP_TEAM_B          = "team-b";

    private static final String STATUS_RUNNING       = "RUNNING";
    private static final String STATUS_RUNNING_LOWER = "running";
    private static final String SPOT_TRUE            = "true";

    private static final int DURATION_48H     = 48;
    private static final int DURATION_72H     = 72;
    private static final int HOURS_50_ELAPSED = 50;
    private static final int HOURS_80_ELAPSED = 80;
    private static final int ACTION_VALUE     = 100;

    private Map<String, Set<String>> authorities;
    private PlatformUsageCreditsUpdateRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        authorities = new HashMap<>();
        evaluator = new PlatformUsageCreditsUpdateRuleEvaluator(
                PlatformUsageCreditsUpdateRuleEvaluator.buildRegistry(
                    username -> authorities.getOrDefault(username, Collections.emptySet())));
    }

    // ARRAY

    @Test
    void shouldMatchRunWhenTagKeyPresent() {
        final PipelineRun run = runWithTags(Collections.singletonMap(TAG_IDLE, SPOT_TRUE));
        assertTrue(evaluator.matches(rule(logical(FIELD_RUN_TAG, EQ, TAG_IDLE)), run));
    }

    @Test
    void shouldNotMatchRunWhenTagKeyAbsent() {
        final PipelineRun run = runWithTags(Collections.singletonMap(TAG_LONG_RUNNING, SPOT_TRUE));
        assertFalse(evaluator.matches(rule(logical(FIELD_RUN_TAG, EQ, TAG_IDLE)), run));
    }

    @Test
    void shouldMatchRunOnNotEqualsWhenTagKeyAbsent() {
        final PipelineRun run = runWithTags(Collections.singletonMap(TAG_LONG_RUNNING, SPOT_TRUE));
        assertTrue(evaluator.matches(rule(logical(FIELD_RUN_TAG, NEQ, TAG_IDLE)), run));
    }

    @Test
    void shouldNotMatchRunOnNotEqualsWhenTagKeyPresent() {
        final PipelineRun run = runWithTags(Collections.singletonMap(TAG_IDLE, SPOT_TRUE));
        assertFalse(evaluator.matches(rule(logical(FIELD_RUN_TAG, NEQ, TAG_IDLE)), run));
    }

    @Test
    void shouldMatchRunTagCaseInsensitively() {
        final PipelineRun run = runWithTags(Collections.singletonMap(TAG_IDLE_LOWER, SPOT_TRUE));
        assertTrue(evaluator.matches(rule(logical(FIELD_RUN_TAG, EQ, TAG_IDLE)), run));
    }

    @Test
    void shouldNotMatchRunTagWhenTagsMapIsNull() {
        final PipelineRun run = mock(PipelineRun.class);
        when(run.getTags()).thenReturn(null);
        assertFalse(evaluator.matches(rule(logical(FIELD_RUN_TAG, EQ, TAG_IDLE)), run));
    }

    // ENUM

    @Test
    void shouldMatchRunWhenStatusEquals() {
        final PipelineRun run = runWithStatus(TaskStatus.RUNNING);
        assertTrue(evaluator.matches(rule(logical(FIELD_RUN_STATUS, EQ, STATUS_RUNNING)), run));
    }

    @Test
    void shouldNotMatchRunWhenStatusDiffers() {
        final PipelineRun run = runWithStatus(TaskStatus.STOPPED);
        assertFalse(evaluator.matches(rule(logical(FIELD_RUN_STATUS, EQ, STATUS_RUNNING)), run));
    }

    @Test
    void shouldMatchRunOnNotEqualsWhenStatusDiffers() {
        final PipelineRun run = runWithStatus(TaskStatus.STOPPED);
        assertTrue(evaluator.matches(rule(logical(FIELD_RUN_STATUS, NEQ, STATUS_RUNNING)), run));
    }

    @Test
    void shouldMatchRunStatusCaseInsensitively() {
        final PipelineRun run = runWithStatus(TaskStatus.RUNNING);
        assertTrue(evaluator.matches(rule(logical(FIELD_RUN_STATUS, EQ, STATUS_RUNNING_LOWER)), run));
    }

    @Test
    void shouldMatchRunStatusViaAlias() {
        final PipelineRun run = runWithStatus(TaskStatus.RUNNING);
        assertTrue(evaluator.matches(rule(logical(FIELD_STATUS_ALIAS, EQ, STATUS_RUNNING)), run));
    }

    @Test
    void shouldNotMatchRunWhenStatusIsNull() {
        final PipelineRun run = mock(PipelineRun.class);
        when(run.getStatus()).thenReturn(null);
        when(run.getTags()).thenReturn(Collections.emptyMap());
        assertFalse(evaluator.matches(rule(logical(FIELD_RUN_STATUS, EQ, STATUS_RUNNING)), run));
    }

    // STRING + wildcard (node.type)

    @Test
    void shouldMatchRunOnExactNodeType() {
        final PipelineRun run = runWithInstance(instance(NODE_TYPE_M5_XLARGE, false, REGION_1));
        assertTrue(evaluator.matches(rule(logical(FIELD_NODE_TYPE, EQ, NODE_TYPE_M5_XLARGE)), run));
    }

    @Test
    void shouldMatchRunNodeTypeWithWildcard() {
        final PipelineRun run = runWithInstance(instance(NODE_TYPE_M5_XLARGE, false, REGION_1));
        assertTrue(evaluator.matches(rule(logical(FIELD_NODE_TYPE, EQ, PATTERN_M5_WILDCARD)), run));
    }

    @Test
    void shouldNotMatchRunNodeTypeWildcardOnDifferentFamily() {
        final PipelineRun run = runWithInstance(instance(NODE_TYPE_C5_XLARGE, false, REGION_1));
        assertFalse(evaluator.matches(rule(logical(FIELD_NODE_TYPE, EQ, PATTERN_M5_WILDCARD)), run));
    }

    @Test
    void shouldMatchRunOnNotEqualsWildcardNodeTypeForOtherFamily() {
        final PipelineRun run = runWithInstance(instance(NODE_TYPE_C5_XLARGE, false, REGION_1));
        assertTrue(evaluator.matches(rule(logical(FIELD_NODE_TYPE, NEQ, PATTERN_M5_WILDCARD)), run));
    }

    @Test
    void shouldMatchRunNodeTypeCaseInsensitively() {
        final PipelineRun run = runWithInstance(instance(NODE_TYPE_M5_XLARGE_CAPS, false, REGION_1));
        assertTrue(evaluator.matches(rule(logical(FIELD_NODE_TYPE, EQ, PATTERN_M5_WILDCARD)), run));
    }

    // BOOLEAN (run.spot)

    @Test
    void shouldMatchRunWhenSpotIsTrue() {
        final PipelineRun run = runWithInstance(instance(NODE_TYPE_M5_XLARGE, true, REGION_1));
        assertTrue(evaluator.matches(rule(logical(FIELD_RUN_SPOT, EQ, SPOT_TRUE)), run));
    }

    @Test
    void shouldNotMatchRunWhenSpotIsFalse() {
        final PipelineRun run = runWithInstance(instance(NODE_TYPE_M5_XLARGE, false, REGION_1));
        assertFalse(evaluator.matches(rule(logical(FIELD_RUN_SPOT, EQ, SPOT_TRUE)), run));
    }

    @Test
    void shouldMatchRunOnNotEqualsWhenSpotIsFalse() {
        final PipelineRun run = runWithInstance(instance(NODE_TYPE_M5_XLARGE, false, REGION_1));
        assertTrue(evaluator.matches(rule(logical(FIELD_RUN_SPOT, NEQ, SPOT_TRUE)), run));
    }

    @Test
    void shouldNotMatchRunSpotWhenInstanceIsNull() {
        final PipelineRun run = mock(PipelineRun.class);
        when(run.getInstance()).thenReturn(null);
        when(run.getTags()).thenReturn(Collections.emptyMap());
        assertFalse(evaluator.matches(rule(logical(FIELD_RUN_SPOT, EQ, SPOT_TRUE)), run));
    }

    // NUMERIC

    @Test
    void shouldMatchRunWhenRegionIdEquals() {
        final PipelineRun run = runWithInstance(instance(NODE_TYPE_M5_XLARGE, false, REGION_1));
        assertTrue(evaluator.matches(rule(logical(FIELD_RUN_REGION_ID, EQ, REGION_1S)), run));
    }

    @Test
    void shouldMatchRunOnNotEqualsWhenRegionIdDiffers() {
        final PipelineRun run = runWithInstance(instance(NODE_TYPE_M5_XLARGE, false, REGION_2));
        assertTrue(evaluator.matches(rule(logical(FIELD_RUN_REGION_ID, NEQ, REGION_1S)), run));
    }

    @Test
    void shouldMatchRunWhenRegionIdGreaterThan() {
        final PipelineRun run = runWithInstance(instance(NODE_TYPE_M5_XLARGE, false, REGION_5));
        assertTrue(evaluator.matches(rule(logical(FIELD_RUN_REGION_ID, GT, REGION_3S)), run));
    }

    @Test
    void shouldMatchRunWhenRegionIdLessThanOrEquals() {
        final PipelineRun run = runWithInstance(instance(NODE_TYPE_M5_XLARGE, false, REGION_3));
        assertTrue(evaluator.matches(rule(logical(FIELD_RUN_REGION_ID, LTE, REGION_3S)), run));
    }

    @Test
    void shouldNotMatchRunWhenRegionIdIsNull() {
        final PipelineRun run = runWithInstance(instance(NODE_TYPE_M5_XLARGE, false, null));
        assertFalse(evaluator.matches(rule(logical(FIELD_RUN_REGION_ID, EQ, REGION_1S)), run));
    }

    @Test
    void shouldMatchRunWhenNodeDiskExceedsThreshold() {
        final RunInstance i = new RunInstance();
        i.setNodeDisk(DISK_200);
        assertTrue(evaluator.matches(rule(logical(FIELD_NODE_DISK, GT, DISK_100S)), runWithInstance(i)));
    }

    @Test
    void shouldNotMatchRunWhenNodeDiskBelowThreshold() {
        final RunInstance i = new RunInstance();
        i.setNodeDisk(DISK_50);
        assertFalse(evaluator.matches(rule(logical(FIELD_NODE_DISK, GT, DISK_100S)), runWithInstance(i)));
    }

    // STRING (run.owner)

    @Test
    void shouldMatchRunWhenOwnerEqualsViaFullFieldName() {
        final PipelineRun run = runWithOwner(OWNER_USER1);
        assertTrue(evaluator.matches(rule(logical(FIELD_RUN_OWNER, EQ, OWNER_USER1)), run));
    }

    @Test
    void shouldMatchRunWhenOwnerEqualsViaAlias() {
        final PipelineRun run = runWithOwner(OWNER_USER1);
        assertTrue(evaluator.matches(rule(logical(FIELD_OWNER_ALIAS, EQ, OWNER_USER1)), run));
    }

    @Test
    void shouldMatchRunOwnerWithWildcard() {
        final PipelineRun run = runWithOwner(OWNER_JOHN_DOE);
        assertTrue(evaluator.matches(rule(logical(FIELD_RUN_OWNER, EQ, PATTERN_JOHN_WILDCARD)), run));
    }

    // AND / OR composition

    @Test
    void shouldMatchRunWhenAllAndChildrenMatch() {
        final PipelineRun run = mock(PipelineRun.class);
        when(run.getTags()).thenReturn(Collections.singletonMap(TAG_IDLE, SPOT_TRUE));
        when(run.getInstance()).thenReturn(instance(NODE_TYPE_M5_XLARGE, true, REGION_1));
        assertTrue(evaluator.matches(rule(and(
                logical(FIELD_RUN_TAG, EQ, TAG_IDLE),
                logical(FIELD_RUN_SPOT, EQ, SPOT_TRUE)
        )), run));
    }

    @Test
    void shouldNotMatchRunWhenOneAndChildFails() {
        final PipelineRun run = mock(PipelineRun.class);
        when(run.getTags()).thenReturn(Collections.singletonMap(TAG_IDLE, SPOT_TRUE));
        when(run.getInstance()).thenReturn(instance(NODE_TYPE_M5_XLARGE, false, REGION_1));
        assertFalse(evaluator.matches(rule(and(
                logical(FIELD_RUN_TAG, EQ, TAG_IDLE),
                logical(FIELD_RUN_SPOT, EQ, SPOT_TRUE)
        )), run));
    }

    @Test
    void shouldMatchRunWhenOnlyOneOrChildMatches() {
        final PipelineRun run = mock(PipelineRun.class);
        when(run.getTags()).thenReturn(Collections.singletonMap(TAG_IDLE, SPOT_TRUE));
        when(run.getInstance()).thenReturn(instance(NODE_TYPE_M5_XLARGE, false, REGION_1));
        assertTrue(evaluator.matches(rule(orExpr(
                logical(FIELD_RUN_TAG, EQ, TAG_IDLE),
                logical(FIELD_RUN_SPOT, EQ, SPOT_TRUE)
        )), run));
    }

    @Test
    void shouldNotMatchRunWhenNoOrChildMatches() {
        final PipelineRun run = mock(PipelineRun.class);
        when(run.getTags()).thenReturn(Collections.emptyMap());
        when(run.getInstance()).thenReturn(instance(NODE_TYPE_M5_XLARGE, false, REGION_1));
        assertFalse(evaluator.matches(rule(orExpr(
                logical(FIELD_RUN_TAG, EQ, TAG_IDLE),
                logical(FIELD_RUN_SPOT, EQ, SPOT_TRUE)
        )), run));
    }

    @Test
    void shouldMatchRunWhenAllThreeAndChildrenMatch() {
        final PipelineRun run = mock(PipelineRun.class);
        when(run.getTags()).thenReturn(Collections.singletonMap(TAG_IDLE, SPOT_TRUE));
        when(run.getStatus()).thenReturn(TaskStatus.RUNNING);
        when(run.getInstance()).thenReturn(instance(NODE_TYPE_M5_XLARGE, true, REGION_1));
        assertTrue(evaluator.matches(rule(and(
                logical(FIELD_RUN_TAG, EQ, TAG_IDLE),
                logical(FIELD_RUN_STATUS, EQ, STATUS_RUNNING),
                logical(FIELD_RUN_SPOT, EQ, SPOT_TRUE)
        )), run));
    }

    /**
     * node.type in [m5.*, c5.*] is an OR-based exclude expression.
     * A c5.xlarge spot run matches the exclude (c5.xlarge matches c5.*) and is therefore not matched.
     */
    @Test
    void shouldNotMatchRunWhenExcludeOrExpressionMatchesNodeType() {
        final PipelineRun run = runWithInstance(instance(NODE_TYPE_C5_XLARGE, true, REGION_1));
        final PlatformUsageCreditsUpdateRule rule = rule(
                logical(FIELD_RUN_SPOT, EQ, SPOT_TRUE),
                orExpr(logical(FIELD_NODE_TYPE, EQ, PATTERN_M5_WILDCARD),
                        logical(FIELD_NODE_TYPE, EQ, PATTERN_C5_WILDCARD))
        );
        assertFalse(evaluator.matches(rule, run));
    }

    /**
     * node.type = m5.* exclude does not match c5.xlarge, so the run is evaluated against the statement.
     * spot=true matches → the run is matched.
     */
    @Test
    void shouldMatchRunWhenExcludeExpressionDoesNotMatchNodeType() {
        final PipelineRun run = runWithInstance(instance(NODE_TYPE_C5_XLARGE, true, REGION_1));
        final PlatformUsageCreditsUpdateRule rule = rule(
                logical(FIELD_RUN_SPOT, EQ, SPOT_TRUE),
                logical(FIELD_NODE_TYPE, EQ, PATTERN_M5_WILDCARD)
        );
        assertTrue(evaluator.matches(rule, run));
    }

    // Exclude expression (exclusion post-condition)

    @Test
    void shouldMatchRunWhenExcludeExpressionIsNull() {
        final PipelineRun run = runWithStatus(TaskStatus.RUNNING);
        assertTrue(evaluator.matches(rule(logical(FIELD_RUN_STATUS, EQ, STATUS_RUNNING), null), run));
    }

    @Test
    void shouldNotMatchRunWhenExcludeExpressionMatches() {
        final PipelineRun run = runWithInstance(instance(NODE_TYPE_M5_XLARGE, true, REGION_1));
        assertFalse(evaluator.matches(rule(
                logical(FIELD_RUN_SPOT, EQ, SPOT_TRUE),
                logical(FIELD_RUN_REGION_ID, EQ, REGION_1S)
        ), run));
    }

    @Test
    void shouldMatchRunWhenExcludeExpressionDoesNotMatch() {
        final PipelineRun run = runWithInstance(instance(NODE_TYPE_M5_XLARGE, true, REGION_2));
        assertTrue(evaluator.matches(rule(
                logical(FIELD_RUN_SPOT, EQ, SPOT_TRUE),
                logical(FIELD_RUN_REGION_ID, EQ, REGION_1S)
        ), run));
    }

    @Test
    void shouldNotMatchRunWhenStatementFailsEvenThoughExcludeDoesNotMatch() {
        final PipelineRun run = runWithInstance(instance(NODE_TYPE_M5_XLARGE, false, REGION_2));
        assertFalse(evaluator.matches(rule(
                logical(FIELD_RUN_SPOT, EQ, SPOT_TRUE),
                logical(FIELD_RUN_REGION_ID, EQ, REGION_1S)
        ), run));
    }

    // Invalid / unknown expressions

    @Test
    void shouldReturnFalseWhenFieldNameIsUnknown() {
        final PipelineRun run = mock(PipelineRun.class);
        when(run.getTags()).thenReturn(Collections.emptyMap());
        assertFalse(evaluator.matches(rule(logical(FIELD_UNKNOWN, EQ, "value")), run));
    }

    @Test
    void shouldReturnFalseWhenOperatorNotSupportedForFieldType() {
        // ">" is a NUMERIC operator; ARRAY only supports "=" and "!="
        final PipelineRun run = runWithTags(Collections.singletonMap(TAG_IDLE, SPOT_TRUE));
        assertFalse(evaluator.matches(rule(logical(FIELD_RUN_TAG, GT, TAG_IDLE)), run));
    }

    @Test
    void shouldReturnFalseWhenOperatorSymbolIsUnknown() {
        final PipelineRun run = runWithStatus(TaskStatus.RUNNING);
        assertFalse(evaluator.matches(rule(logical(FIELD_RUN_STATUS, "<>", STATUS_RUNNING)), run));
    }

    @Test
    void shouldReturnFalseWhenOneAndChildHasUnknownField() {
        // valid child passes but invalid child returns false → AND collapses to false
        final PipelineRun run = runWithStatus(TaskStatus.RUNNING);
        assertFalse(evaluator.matches(rule(and(
                logical(FIELD_RUN_STATUS, EQ, STATUS_RUNNING),
                logical(FIELD_UNKNOWN, EQ, "x")
        )), run));
    }

    // owner.group

    @Test
    void shouldMatchRunWhenOwnerBelongsToGroup() {
        authorities.put(OWNER_USER1, Collections.singleton(GROUP_TEAM_A));
        assertTrue(evaluator.matches(rule(logical(FIELD_OWNER_GROUP, EQ, GROUP_TEAM_A)), runWithOwner(OWNER_USER1)));
    }

    @Test
    void shouldNotMatchRunWhenOwnerDoesNotBelongToGroup() {
        authorities.put(OWNER_USER1, Collections.singleton(GROUP_TEAM_B));
        assertFalse(evaluator.matches(rule(logical(FIELD_OWNER_GROUP, EQ, GROUP_TEAM_A)), runWithOwner(OWNER_USER1)));
    }

    @Test
    void shouldMatchRunOnNotEqualsWhenOwnerNotInGroup() {
        authorities.put(OWNER_USER1, Collections.singleton(GROUP_TEAM_B));
        assertTrue(evaluator.matches(rule(logical(FIELD_OWNER_GROUP, NEQ, GROUP_TEAM_A)), runWithOwner(OWNER_USER1)));
    }

    @Test
    void shouldMatchRunOwnerGroupCaseInsensitively() {
        authorities.put(OWNER_USER1, Collections.singleton("Team-A"));
        assertTrue(evaluator.matches(rule(logical(FIELD_OWNER_GROUP, EQ, GROUP_TEAM_A)), runWithOwner(OWNER_USER1)));
    }

    @Test
    void shouldMatchRunWhenOwnerBelongsToAnyGroupViaOrTree() {
        authorities.put(OWNER_USER1, Collections.singleton(GROUP_TEAM_B));
        assertTrue(evaluator.matches(rule(orExpr(
                logical(FIELD_OWNER_GROUP, EQ, GROUP_TEAM_A),
                logical(FIELD_OWNER_GROUP, EQ, GROUP_TEAM_B)
        )), runWithOwner(OWNER_USER1)));
    }

    @Test
    void shouldNotMatchRunWhenOwnerBelongsToNeitherGroupInOrTree() {
        assertFalse(evaluator.matches(rule(orExpr(
                logical(FIELD_OWNER_GROUP, EQ, GROUP_TEAM_A),
                logical(FIELD_OWNER_GROUP, EQ, GROUP_TEAM_B)
        )), runWithOwner(OWNER_USER1)));
    }

    @Test
    void shouldNotMatchRunOwnerGroupWhenOwnerIsNull() {
        final PipelineRun run = mock(PipelineRun.class);
        when(run.getOwner()).thenReturn(null);
        when(run.getTags()).thenReturn(Collections.emptyMap());
        assertFalse(evaluator.matches(rule(logical(FIELD_OWNER_GROUP, EQ, GROUP_TEAM_A)), run));
    }

    // duration gate

    @Test
    void shouldMatchRunWhenTagPresentAndDurationExceeded() {
        final LocalDateTime now = LocalDateTime.of(2026, 7, 13, 12, 0, 0);
        final LocalDateTime tagApplied = now.minusHours(HOURS_50_ELAPSED);
        final PipelineRun run = runWithTagAndDate(TAG_IDLE, tagApplied);
        assertTrue(evaluator.matches(rule(logicalWithDuration(FIELD_RUN_TAG, EQ, TAG_IDLE, DURATION_48H)), run, now));
    }

    @Test
    void shouldNotMatchRunWhenTagPresentButDurationNotYetExceeded() {
        final LocalDateTime now = LocalDateTime.of(2026, 7, 13, 12, 0, 0);
        final LocalDateTime tagApplied = now.minusHours(24);
        final PipelineRun run = runWithTagAndDate(TAG_IDLE, tagApplied);
        assertFalse(evaluator.matches(rule(logicalWithDuration(FIELD_RUN_TAG, EQ, TAG_IDLE, DURATION_48H)), run, now));
    }

    @Test
    void shouldMatchRunWhenDurationExactlyMet() {
        final LocalDateTime now = LocalDateTime.of(2026, 7, 13, 12, 0, 0);
        final LocalDateTime tagApplied = now.minusHours(48);
        final PipelineRun run = runWithTagAndDate(TAG_IDLE, tagApplied);
        assertTrue(evaluator.matches(rule(logicalWithDuration(FIELD_RUN_TAG, EQ, TAG_IDLE, DURATION_48H)), run, now));
    }

    @Test
    void shouldNotMatchRunWhenTagAbsentAndDurationSet() {
        final LocalDateTime now = LocalDateTime.of(2026, 7, 13, 12, 0, 0);
        final PipelineRun run = runWithTags(Collections.singletonMap(TAG_LONG_RUNNING, SPOT_TRUE));
        assertFalse(evaluator.matches(rule(logicalWithDuration(FIELD_RUN_TAG, EQ, TAG_IDLE, DURATION_48H)), run, now));
    }

    @Test
    void shouldNotMatchRunWhenTagPresentButDateTagAbsent() {
        final LocalDateTime now = LocalDateTime.of(2026, 7, 13, 12, 0, 0);
        // IDLE tag present but no IDLE_date companion tag
        final PipelineRun run = runWithTags(Collections.singletonMap(TAG_IDLE, SPOT_TRUE));
        assertFalse(evaluator.matches(rule(logicalWithDuration(FIELD_RUN_TAG, EQ, TAG_IDLE, DURATION_48H)), run, now));
    }

    @Test
    void shouldNotMatchRunWhenDateTagValueIsUnparseable() {
        final LocalDateTime now = LocalDateTime.of(2026, 7, 13, 12, 0, 0);
        final Map<String, String> tags = new HashMap<>();
        tags.put(TAG_IDLE, SPOT_TRUE);
        tags.put(TAG_IDLE + TagFieldEvaluationStrategy.DATE_SUFFIX, "not-a-date");
        final PipelineRun run = runWithTags(tags);
        assertFalse(evaluator.matches(rule(logicalWithDuration(FIELD_RUN_TAG, EQ, TAG_IDLE, DURATION_48H)), run, now));
    }

    @Test
    void shouldMatchRunWithZeroDurationWhenTagJustApplied() {
        final LocalDateTime now = LocalDateTime.of(2026, 7, 13, 12, 0, 0);
        final PipelineRun run = runWithTagAndDate(TAG_IDLE, now);
        assertTrue(evaluator.matches(rule(logicalWithDuration(FIELD_RUN_TAG, EQ, TAG_IDLE, 0)), run, now));
    }

    @Test
    void shouldIgnoreDurationOnNonTagField() {
        // run.spot = true with duration set: boolean check passes, duration gate is skipped for non-TAG fields
        final LocalDateTime now = LocalDateTime.of(2026, 7, 13, 12, 0, 0);
        final PipelineRun run = runWithInstance(instance(NODE_TYPE_M5_XLARGE, true, REGION_1));
        final ConditionExpression leaf = logicalWithDuration(FIELD_RUN_SPOT, EQ, SPOT_TRUE, DURATION_48H);
        assertTrue(evaluator.matches(rule(leaf), run, now));
    }

    @Test
    void shouldMatchMultipleTagsWithDurationViaAndExpression() {
        final LocalDateTime now = LocalDateTime.of(2026, 7, 13, 12, 0, 0);
        final Map<String, String> tags = new HashMap<>();
        tags.put(TAG_IDLE, SPOT_TRUE);
        tags.put(TAG_IDLE + TagFieldEvaluationStrategy.DATE_SUFFIX,
                formatDate(now.minusHours(HOURS_50_ELAPSED)));
        tags.put(TAG_LONG_RUNNING, SPOT_TRUE);
        tags.put(TAG_LONG_RUNNING + TagFieldEvaluationStrategy.DATE_SUFFIX,
                formatDate(now.minusHours(HOURS_80_ELAPSED)));
        final PipelineRun run = runWithTags(tags);
        assertTrue(evaluator.matches(rule(and(
                logicalWithDuration(FIELD_RUN_TAG, EQ, TAG_IDLE, DURATION_48H),
                logicalWithDuration(FIELD_RUN_TAG, EQ, TAG_LONG_RUNNING, DURATION_72H)
        )), run, now));
    }

    @Test
    void shouldNotMatchAndExpressionWhenOnlyOneTagMeetsDuration() {
        final LocalDateTime now = LocalDateTime.of(2026, 7, 13, 12, 0, 0);
        final Map<String, String> tags = new HashMap<>();
        tags.put(TAG_IDLE, SPOT_TRUE);
        tags.put(TAG_IDLE + TagFieldEvaluationStrategy.DATE_SUFFIX,
                formatDate(now.minusHours(HOURS_50_ELAPSED)));
        tags.put(TAG_LONG_RUNNING, SPOT_TRUE);
        tags.put(TAG_LONG_RUNNING + TagFieldEvaluationStrategy.DATE_SUFFIX,
                formatDate(now.minusHours(10)));
        final PipelineRun run = runWithTags(tags);
        assertFalse(evaluator.matches(rule(and(
                logicalWithDuration(FIELD_RUN_TAG, EQ, TAG_IDLE, DURATION_48H),
                logicalWithDuration(FIELD_RUN_TAG, EQ, TAG_LONG_RUNNING, DURATION_72H)
        )), run, now));
    }

    private static ConditionExpression logical(final String field, final String operand, final String value) {
        final ConditionExpression expr = new ConditionExpression();
        expr.setType(ConditionType.LOGICAL);
        expr.setField(field);
        expr.setOperand(operand);
        expr.setValue(value);
        return expr;
    }

    private static ConditionExpression and(final ConditionExpression... children) {
        final ConditionExpression expr = new ConditionExpression();
        expr.setType(ConditionType.AND);
        expr.setExpressions(Arrays.asList(children));
        return expr;
    }

    private static ConditionExpression orExpr(final ConditionExpression... children) {
        final ConditionExpression expr = new ConditionExpression();
        expr.setType(ConditionType.OR);
        expr.setExpressions(Arrays.asList(children));
        return expr;
    }

    private static PlatformUsageCreditsUpdateRule rule(final ConditionExpression filter) {
        return rule(filter, null);
    }

    private static PlatformUsageCreditsUpdateRule rule(final ConditionExpression filter,
                                                      final ConditionExpression exclude) {
        return PlatformUsageCreditsUpdateRule.builder()
                .id(1L)
                .name("test-rule")
                .strategyType(PlatformUsageCreditsUpdateRuleType.RUN_STATE)
                .statement(filter)
                .exclude(exclude)
                .action(PlatformUsageCreditsUpdateAction.builder()
                        .type(PlatformUsageCreditsUpdateAction.ActionType.DEDUCTION)
                        .value(ACTION_VALUE)
                        .build())
                .build();
    }

    private static PipelineRun runWithInstance(final RunInstance instance) {
        final PipelineRun run = new PipelineRun();
        run.setInstance(instance);
        return run;
    }

    private static PipelineRun runWithTags(final Map<String, String> tags) {
        final PipelineRun run = mock(PipelineRun.class);
        when(run.getTags()).thenReturn(tags);
        return run;
    }

    private static PipelineRun runWithStatus(final TaskStatus status) {
        final PipelineRun run = mock(PipelineRun.class);
        when(run.getStatus()).thenReturn(status);
        when(run.getTags()).thenReturn(Collections.emptyMap());
        return run;
    }

    private static PipelineRun runWithOwner(final String owner) {
        final PipelineRun run = mock(PipelineRun.class);
        when(run.getOwner()).thenReturn(owner);
        when(run.getTags()).thenReturn(Collections.emptyMap());
        return run;
    }

    private static RunInstance instance(final String nodeType, final Boolean spot, final Long regionId) {
        final RunInstance i = new RunInstance();
        i.setNodeType(nodeType);
        i.setSpot(spot);
        i.setCloudRegionId(regionId);
        return i;
    }

    private static ConditionExpression logicalWithDuration(final String field, final String operand,
                                                        final String value, final int duration) {
        final ConditionExpression expr = logical(field, operand, value);
        expr.setDuration(duration);
        return expr;
    }

    private static PipelineRun runWithTagAndDate(final String tagName, final LocalDateTime tagApplied) {
        final Map<String, String> tags = new HashMap<>();
        tags.put(tagName, SPOT_TRUE);
        tags.put(tagName + TagFieldEvaluationStrategy.DATE_SUFFIX, formatDate(tagApplied));
        return runWithTags(tags);
    }

    private static String formatDate(final LocalDateTime dateTime) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").format(dateTime);
    }

    // run.parameter

    private static final String FIELD_RUN_PARAMETER = "run.parameter";
    private static final String PARAM_NAME_DATASET  = "dataset";
    private static final String PARAM_NAME_MISSING  = "missing_param";
    private static final String PARAM_VALUE_PROD    = "prod-dataset";
    private static final String PARAM_VALUE_OTHER   = "other-dataset";
    private static final String PARAM_PATTERN_PROD  = "prod-*";

    @Test
    void shouldMatchRunWhenParameterNameExists() {
        final PipelineRun run = runWithParameters(new PipelineRunParameter(PARAM_NAME_DATASET, PARAM_VALUE_PROD));
        assertTrue(evaluator.matches(rule(logical(FIELD_RUN_PARAMETER, EQ, PARAM_NAME_DATASET)), run));
    }

    @Test
    void shouldNotMatchRunWhenParameterNameAbsent() {
        final PipelineRun run = runWithParameters(new PipelineRunParameter(PARAM_NAME_DATASET, PARAM_VALUE_PROD));
        assertFalse(evaluator.matches(rule(logical(FIELD_RUN_PARAMETER, EQ, PARAM_NAME_MISSING)), run));
    }

    @Test
    void shouldMatchRunOnNotEqualsWhenParameterAbsent() {
        final PipelineRun run = runWithParameters(new PipelineRunParameter(PARAM_NAME_DATASET, PARAM_VALUE_PROD));
        assertTrue(evaluator.matches(rule(logical(FIELD_RUN_PARAMETER, NEQ, PARAM_NAME_MISSING)), run));
    }

    @Test
    void shouldMatchRunWhenParameterNameAndValueMatch() {
        final PipelineRun run = runWithParameters(new PipelineRunParameter(PARAM_NAME_DATASET, PARAM_VALUE_PROD));
        assertTrue(evaluator.matches(
                rule(logical(FIELD_RUN_PARAMETER, EQ, PARAM_NAME_DATASET + "=" + PARAM_VALUE_PROD)), run));
    }

    @Test
    void shouldNotMatchRunWhenParameterValueDoesNotMatch() {
        final PipelineRun run = runWithParameters(new PipelineRunParameter(PARAM_NAME_DATASET, PARAM_VALUE_PROD));
        assertFalse(evaluator.matches(
                rule(logical(FIELD_RUN_PARAMETER, EQ, PARAM_NAME_DATASET + "=" + PARAM_VALUE_OTHER)), run));
    }

    @Test
    void shouldMatchRunWhenParameterValueMatchesWildcard() {
        final PipelineRun run = runWithParameters(new PipelineRunParameter(PARAM_NAME_DATASET, PARAM_VALUE_PROD));
        assertTrue(evaluator.matches(
                rule(logical(FIELD_RUN_PARAMETER, EQ, PARAM_NAME_DATASET + "=" + PARAM_PATTERN_PROD)), run));
    }

    @Test
    void shouldMatchRunParameterNameCaseInsensitively() {
        final PipelineRun run = runWithParameters(new PipelineRunParameter("Dataset", PARAM_VALUE_PROD));
        assertTrue(evaluator.matches(rule(logical(FIELD_RUN_PARAMETER, EQ, PARAM_NAME_DATASET)), run));
    }

    @Test
    void shouldNotMatchRunWhenParameterListIsNull() {
        final PipelineRun run = runWithParameters();
        assertFalse(evaluator.matches(rule(logical(FIELD_RUN_PARAMETER, EQ, PARAM_NAME_DATASET)), run));
    }

    // run.env_var

    private static final String FIELD_RUN_ENV_VAR  = "run.env_var";
    private static final String ENV_NAME_MODE       = "PIPELINE_MODE";
    private static final String ENV_NAME_MISSING    = "MISSING_VAR";
    private static final String ENV_VALUE_BATCH     = "batch";
    private static final String ENV_VALUE_OTHER     = "interactive";
    private static final String ENV_PATTERN_BATCH   = "bat*";

    @Test
    void shouldMatchRunWhenEnvVarNameExists() {
        final PipelineRun run = runWithEnvVars(Collections.singletonMap(ENV_NAME_MODE, ENV_VALUE_BATCH));
        assertTrue(evaluator.matches(rule(logical(FIELD_RUN_ENV_VAR, EQ, ENV_NAME_MODE)), run));
    }

    @Test
    void shouldNotMatchRunWhenEnvVarNameAbsent() {
        final PipelineRun run = runWithEnvVars(Collections.singletonMap(ENV_NAME_MODE, ENV_VALUE_BATCH));
        assertFalse(evaluator.matches(rule(logical(FIELD_RUN_ENV_VAR, EQ, ENV_NAME_MISSING)), run));
    }

    @Test
    void shouldMatchRunOnNotEqualsWhenEnvVarAbsent() {
        final PipelineRun run = runWithEnvVars(Collections.singletonMap(ENV_NAME_MODE, ENV_VALUE_BATCH));
        assertTrue(evaluator.matches(rule(logical(FIELD_RUN_ENV_VAR, NEQ, ENV_NAME_MISSING)), run));
    }

    @Test
    void shouldMatchRunWhenEnvVarNameAndValueMatch() {
        final PipelineRun run = runWithEnvVars(Collections.singletonMap(ENV_NAME_MODE, ENV_VALUE_BATCH));
        assertTrue(evaluator.matches(
                rule(logical(FIELD_RUN_ENV_VAR, EQ, ENV_NAME_MODE + "=" + ENV_VALUE_BATCH)), run));
    }

    @Test
    void shouldNotMatchRunWhenEnvVarValueDoesNotMatch() {
        final PipelineRun run = runWithEnvVars(Collections.singletonMap(ENV_NAME_MODE, ENV_VALUE_BATCH));
        assertFalse(evaluator.matches(
                rule(logical(FIELD_RUN_ENV_VAR, EQ, ENV_NAME_MODE + "=" + ENV_VALUE_OTHER)), run));
    }

    @Test
    void shouldMatchRunWhenEnvVarValueMatchesWildcard() {
        final PipelineRun run = runWithEnvVars(Collections.singletonMap(ENV_NAME_MODE, ENV_VALUE_BATCH));
        assertTrue(evaluator.matches(
                rule(logical(FIELD_RUN_ENV_VAR, EQ, ENV_NAME_MODE + "=" + ENV_PATTERN_BATCH)), run));
    }

    @Test
    void shouldMatchRunEnvVarNameCaseInsensitively() {
        final PipelineRun run = runWithEnvVars(Collections.singletonMap("pipeline_mode", ENV_VALUE_BATCH));
        assertTrue(evaluator.matches(rule(logical(FIELD_RUN_ENV_VAR, EQ, ENV_NAME_MODE)), run));
    }

    @Test
    void shouldNotMatchRunWhenEnvVarMapIsNull() {
        final PipelineRun run = runWithEnvVars(null);
        assertFalse(evaluator.matches(rule(logical(FIELD_RUN_ENV_VAR, EQ, ENV_NAME_MODE)), run));
    }

    private static PipelineRun runWithParameters(final PipelineRunParameter... params) {
        final PipelineRun run = mock(PipelineRun.class);
        when(run.getPipelineRunParameters()).thenReturn(params.length == 0 ? null : Arrays.asList(params));
        when(run.getTags()).thenReturn(Collections.emptyMap());
        return run;
    }

    private static PipelineRun runWithEnvVars(final Map<String, String> envVars) {
        final PipelineRun run = mock(PipelineRun.class);
        when(run.getEnvVars()).thenReturn(envVars);
        when(run.getTags()).thenReturn(Collections.emptyMap());
        return run;
    }
}
