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

package com.epam.pipeline.test.creator.compute.quota;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.dto.compute.quota.ComputeQuotaAction;
import com.epam.pipeline.dto.compute.quota.ComputeQuotaActionType;
import com.epam.pipeline.dto.compute.quota.ComputeQuotaRule;
import com.epam.pipeline.dto.compute.quota.ComputeQuotaStrategyType;
import com.epam.pipeline.dto.compute.quota.FilterExpressionType;
import com.epam.pipeline.dto.compute.quota.QuotaFilterExpression;
import com.epam.pipeline.entity.compute.quota.ComputeQuotaRuleEntity;
import com.fasterxml.jackson.core.type.TypeReference;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public interface ComputeQuotaRuleCreatorsUtils {

    TypeReference<Result<ComputeQuotaRule>> COMPUTE_QUOTA_RULE_TYPE =
            new TypeReference<Result<ComputeQuotaRule>>() {};
    TypeReference<Result<List<ComputeQuotaRule>>> COMPUTE_QUOTA_RULE_LIST_TYPE =
            new TypeReference<Result<List<ComputeQuotaRule>>>() {};

    Long ID = 1L;
    String RULE_NAME = "Test Rule";
    String RULE_DESCRIPTION = "Test rule description";
    int ACTION_VALUE = 100;
    String ACTION_MESSAGE = "Test action message";
    String FIELD_NAME = "run.tag";
    String FIELD_VALUE = "IDLE";
    String FIELD_OPERAND = "=";

    static QuotaFilterExpression filterExpression() {
        final QuotaFilterExpression expression = new QuotaFilterExpression();
        expression.setField(FIELD_NAME);
        expression.setValue(FIELD_VALUE);
        expression.setOperand(FIELD_OPERAND);
        expression.setFilterExpressionType(FilterExpressionType.LOGICAL);
        expression.setDuration(48);
        return expression;
    }

    static ComputeQuotaAction computeQuotaAction() {
        return ComputeQuotaAction.builder()
                .type(ComputeQuotaActionType.DEDUCTION)
                .value(ACTION_VALUE)
                .message(ACTION_MESSAGE)
                .perIncident(true)
                .build();
    }

    static ComputeQuotaRule computeQuotaRule() {
        return ComputeQuotaRule.builder()
                .id(ID)
                .name(RULE_NAME)
                .description(RULE_DESCRIPTION)
                .strategyType(ComputeQuotaStrategyType.RUN_STATE)
                .filterExpression(filterExpression())
                .action(computeQuotaAction())
                .build();
    }

    static ComputeQuotaRuleEntity computeQuotaRuleEntity() {
        return ComputeQuotaRuleEntity.builder()
                .id(ID)
                .name(RULE_NAME)
                .description(RULE_DESCRIPTION)
                .strategyType(ComputeQuotaStrategyType.RUN_STATE)
                .filterExpression(filterExpression())
                .actionType(ComputeQuotaActionType.DEDUCTION)
                .actionValue(ACTION_VALUE)
                .actionMessage(ACTION_MESSAGE)
                .perIncident(true)
                .createdDate(LocalDateTime.now())
                .modifiedDate(LocalDateTime.now())
                .build();
    }

    static List<ComputeQuotaRule> computeQuotaRuleList() {
        return Collections.singletonList(computeQuotaRule());
    }
}
