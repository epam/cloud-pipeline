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

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUpdateRule;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.monitor.rest.CloudPipelineAPIClient;
import com.epam.pipeline.utils.condition.evaluation.BooleanFieldEvaluationStrategy;
import com.epam.pipeline.utils.condition.evaluation.ConditionExpressionEvaluator;
import com.epam.pipeline.utils.condition.evaluation.EntityConditionEvaluationStrategy;
import com.epam.pipeline.utils.condition.evaluation.EnumFieldEvaluationStrategy;
import com.epam.pipeline.utils.condition.evaluation.KeyValueFieldEvaluationStrategy;
import com.epam.pipeline.utils.condition.evaluation.NumericFieldEvaluationStrategy;
import com.epam.pipeline.utils.condition.evaluation.StringFieldEvaluationStrategy;
import com.epam.pipeline.utils.condition.evaluation.TagFieldEvaluationStrategy;
import com.epam.pipeline.utils.condition.evaluation.UserAuthoritiesFieldEvaluationStrategy;
import com.epam.pipeline.utils.condition.field.PipelineRunField;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Evaluates a {@link PlatformUsageCreditsUpdateRule} against a {@link PipelineRun}.
 *
 * <p>A run matches a rule when:
 * <ol>
 *   <li>the rule's {@code exclude} expression is {@code null} or evaluates to {@code false}, AND</li>
 *   <li>the rule's {@code statement} expression evaluates to {@code true}.</li>
 * </ol>
 */
@Component
public class PlatformUsageCreditsUpdateRuleEvaluator {

    private final ConditionExpressionEvaluator<PipelineRun> expressionEvaluator;

    @Autowired
    public PlatformUsageCreditsUpdateRuleEvaluator(final CloudPipelineAPIClient cloudPipelineAPIClient) {
        this.expressionEvaluator = new ConditionExpressionEvaluator<>(buildRegistry(
            username -> {
                if (username == null) {
                    return Collections.emptySet();
                }
                final PipelineUser user = cloudPipelineAPIClient.loadUserByName(username);
                if (user == null) {
                    return Collections.emptySet();
                }
                return Stream.concat(
                        user.getGroups() != null ? user.getGroups().stream() : Stream.empty(),
                        user.getRoles() != null
                                ? user.getRoles().stream().map(Role::getName) : Stream.empty()
                ).collect(Collectors.toList());
            }));
    }

    PlatformUsageCreditsUpdateRuleEvaluator(
            final Map<String, EntityConditionEvaluationStrategy<PipelineRun>> registry) {
        this.expressionEvaluator = new ConditionExpressionEvaluator<>(registry);
    }

    /**
     * Evaluates the rule against the run using the current UTC time for duration checks.
     */
    public boolean matches(final PlatformUsageCreditsUpdateRule rule, final PipelineRun run) {
        return matches(rule, run, LocalDateTime.now(ZoneOffset.UTC));
    }

    public boolean matches(final PlatformUsageCreditsUpdateRule rule, final PipelineRun run,
                           final LocalDateTime evaluationTime) {
        if (!Objects.isNull(rule.getExclude())
                && expressionEvaluator.evaluate(rule.getExclude(), run, evaluationTime)) {
            return false;
        }
        return expressionEvaluator.evaluate(rule.getStatement(), run, evaluationTime);
    }

    static Map<String, EntityConditionEvaluationStrategy<PipelineRun>> buildRegistry(
            final Function<String, Collection<String>> authoritiesResolver) {
        final Map<String, EntityConditionEvaluationStrategy<PipelineRun>> map = new HashMap<>();
        for (final PipelineRunField field : PipelineRunField.values()) {
            final EntityConditionEvaluationStrategy<PipelineRun> strategy;
            switch (field.getType()) {
                case STRING:
                    strategy = new StringFieldEvaluationStrategy<>(field);
                    break;
                case NUMERIC:
                    strategy = new NumericFieldEvaluationStrategy<>(field);
                    break;
                case BOOLEAN:
                    strategy = new BooleanFieldEvaluationStrategy<>(field);
                    break;
                case ENUM:
                    strategy = new EnumFieldEvaluationStrategy<>(field);
                    break;
                case TAGS:
                    strategy = new TagFieldEvaluationStrategy<>(field);
                    break;
                case USER_AUTHORITIES:
                    strategy = new UserAuthoritiesFieldEvaluationStrategy<>(field, authoritiesResolver);
                    break;
                case KEY_VALUE:
                    strategy = new KeyValueFieldEvaluationStrategy<>(field);
                    break;
                default:
                    throw new IllegalArgumentException(
                            "No strategy registered for type: " + field.getType());
            }
            for (final String name : field.getDisplayNames()) {
                map.put(name, strategy);
            }
        }
        return Collections.unmodifiableMap(map);
    }
}
