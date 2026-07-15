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

package com.epam.pipeline.monitor.monitoring.quota;

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.monitor.rest.CloudPipelineAPIClient;
import com.epam.pipeline.utils.condition.evaluation.BooleanFieldEvaluationStrategy;
import com.epam.pipeline.utils.condition.evaluation.EnumFieldEvaluationStrategy;
import com.epam.pipeline.utils.condition.evaluation.EntityConditionEvaluationStrategy;
import com.epam.pipeline.utils.condition.evaluation.NumericFieldEvaluationStrategy;
import com.epam.pipeline.utils.condition.evaluation.StringFieldEvaluationStrategy;
import com.epam.pipeline.utils.condition.evaluation.TagFieldEvaluationStrategy;
import com.epam.pipeline.utils.condition.evaluation.UserAuthoritiesFieldEvaluationStrategy;
import com.epam.pipeline.utils.condition.field.PipelineRunField;
import com.epam.pipeline.utils.condition.field.SubjectEntityField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds {@link EntityConditionEvaluationStrategy} registries for rule evaluation, wiring in
 * group and role resolvers that call the Cloud Pipeline API on demand per username.
 */
@Component
@RequiredArgsConstructor
public class CloudPipelineApiAwareEntityFieldConditionStrategyComposer {

    private final CloudPipelineAPIClient cloudPipelineAPIClient;

    /**
     * Builds a PipelineRun strategy registry whose group and role resolvers call the Cloud
     * Pipeline API on demand to load the owner of each evaluated run.
     */
    public Map<String, EntityConditionEvaluationStrategy<PipelineRun>> buildPipelineRunRegistry() {
        return buildPipelineRunRegistry(
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
                        user.getRoles() != null ? user.getRoles().stream().map(Role::getName) : Stream.empty()
                ).collect(Collectors.toList());
            });
    }

    /**
     * Builds a PipelineRun strategy registry using the provided group and role resolvers.
     * Exposed as a static utility for tests or callers that already have user data available.
     */
    public static Map<String, EntityConditionEvaluationStrategy<PipelineRun>> buildPipelineRunRegistry(
            final Function<String, Collection<String>> authoritiesResolver) {
        final Map<String, EntityConditionEvaluationStrategy<PipelineRun>> map = new HashMap<>();
        for (final PipelineRunField field : PipelineRunField.values()) {
            final EntityConditionEvaluationStrategy<PipelineRun> strategy =
                    resolveEntityFieldConditionStrategy(field, authoritiesResolver);
            for (final String name : field.getDisplayNames()) {
                map.put(name, strategy);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static EntityConditionEvaluationStrategy<PipelineRun> resolveEntityFieldConditionStrategy(
            final SubjectEntityField<PipelineRun> field,
            final Function<String, Collection<String>> authoritiesResolver) {
        switch (field.getType()) {
            case STRING:
                return new StringFieldEvaluationStrategy<>(field);
            case NUMERIC:
                return new NumericFieldEvaluationStrategy<>(field);
            case BOOLEAN:
                return new BooleanFieldEvaluationStrategy<>(field);
            case ENUM:
                return new EnumFieldEvaluationStrategy<>(field);
            case TAGS:
                return new TagFieldEvaluationStrategy<>(field, PipelineRun::getTags);
            case USER_AUTHORITIES:
                return new UserAuthoritiesFieldEvaluationStrategy<>(field, authoritiesResolver);
            default:
                throw new IllegalArgumentException(
                        "No strategy registered for type: " + field.getType());
        }
    }

}
