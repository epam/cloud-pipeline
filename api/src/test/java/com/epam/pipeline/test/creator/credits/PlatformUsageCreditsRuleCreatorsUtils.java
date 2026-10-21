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

package com.epam.pipeline.test.creator.credits;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.FilterFieldVO;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateRuleType;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateAction;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateRule;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUpdateRuleEntity;
import com.epam.pipeline.utils.condition.ConditionExpression;
import com.epam.pipeline.utils.condition.ConditionType;
import com.fasterxml.jackson.core.type.TypeReference;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface PlatformUsageCreditsRuleCreatorsUtils {

    TypeReference<Result<PlatformUsageCreditsUpdateRule>> PLATFORM_USAGE_CREDITS_RULE_TYPE =
            new TypeReference<Result<PlatformUsageCreditsUpdateRule>>() {};
    TypeReference<Result<List<PlatformUsageCreditsUpdateRule>>> PLATFORM_USAGE_CREDITS_RULE_LIST_TYPE =
            new TypeReference<Result<List<PlatformUsageCreditsUpdateRule>>>() {};

    Long ID = 1L;
    String RULE_NAME = "Test Rule";
    String RULE_DESCRIPTION = "Test rule description";
    int ACTION_VALUE = 100;
    String ACTION_MESSAGE = "Test action message";
    String FIELD_NAME = "run.tag";
    String FIELD_VALUE = "IDLE";
    String FIELD_OPERAND = "=";
    int TIME_WINDOW = 24;

    static ConditionExpression filterExpression() {
        final ConditionExpression expression = new ConditionExpression();
        expression.setField(FIELD_NAME);
        expression.setValue(FIELD_VALUE);
        expression.setOperand(FIELD_OPERAND);
        expression.setType(ConditionType.LOGICAL);
        return expression;
    }

    static PlatformUsageCreditsUpdateAction platformUsageCreditsAction() {
        return PlatformUsageCreditsUpdateAction.builder()
                .type(PlatformUsageCreditsUpdateAction.ActionType.DEDUCTION)
                .value(ACTION_VALUE)
                .message(ACTION_MESSAGE)
                .build();
    }

    static PlatformUsageCreditsUpdateRule platformUsageCreditsRule() {
        return PlatformUsageCreditsUpdateRule.builder()
                .id(ID)
                .name(RULE_NAME)
                .description(RULE_DESCRIPTION)
                .ruleType(PlatformUsageCreditsUpdateRuleType.RUN_STATE)
                .statement(filterExpression())
                .action(platformUsageCreditsAction())
                .timeWindow(TIME_WINDOW)
                .build();
    }

    static PlatformUsageCreditsUpdateRuleEntity platformUsageCreditsRuleEntity() {
        return PlatformUsageCreditsUpdateRuleEntity.builder()
                .id(ID)
                .name(RULE_NAME)
                .description(RULE_DESCRIPTION)
                .ruleType(PlatformUsageCreditsUpdateRuleType.RUN_STATE)
                .statement(filterExpression())
                .actionType(PlatformUsageCreditsUpdateAction.ActionType.DEDUCTION)
                .actionValue(ACTION_VALUE)
                .actionMessage(ACTION_MESSAGE)
                .timeWindow(TIME_WINDOW)
                .createdDate(LocalDateTime.now())
                .modifiedDate(LocalDateTime.now())
                .build();
    }

    static List<PlatformUsageCreditsUpdateRule> platformUsageCreditsRuleList() {
        return Collections.singletonList(platformUsageCreditsRule());
    }

    static FilterFieldVO filterFieldVO() {
        final FilterFieldVO vo = new FilterFieldVO();
        vo.setFieldName(FIELD_NAME);
        vo.setSupportedOperands(Arrays.asList("=", "!="));
        return vo;
    }

    static List<FilterFieldVO> filterFieldVOList() {
        return Collections.singletonList(filterFieldVO());
    }

    static Map<String, List<FilterFieldVO>> filterFieldVOMap() {
        return Collections.singletonMap(
                PlatformUsageCreditsUpdateRuleType.RUN_STATE.name(), filterFieldVOList());
    }
}
