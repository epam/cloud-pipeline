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

package com.epam.pipeline.dto.credits;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import lombok.Getter;

/**
 * Identifies the type of domain object a {@link PlatformUsageCreditsUpdateRule} is evaluated
 * against, and carries the corresponding subject class so that callers can resolve the available
 * filter fields without hard-coding strategy-specific logic.
 *
 * <p>The {@code entityClass} is the bridge to
 * {@link com.epam.pipeline.utils.condition.field.SubjectEntityField}: pass it to either static
 * factory method to obtain the fields that belong to this strategy.
 *
 * <p><b>List all filterable fields for a strategy:</b>
 * <pre>{@code
 * List<SubjectEntityField<PipelineRun>> fields =
 *         SubjectEntityField.forSubjectType(RUN_STATE.getEntityClass());
 * // → all PipelineRunField enum constants
 * }</pre>
 *
 * <p><b>Look up a field by its expression display name (e.g. for validation):</b>
 * <pre>{@code
 * Map<String, SubjectEntityField<PipelineRun>> index =
 *         SubjectEntityField.byDisplayNames(RUN_STATE.getEntityClass());
 *
 * SubjectEntityField<PipelineRun> field = index.get("run.tag");  // null when name is unknown
 * }</pre>
 *
 * <p><b>Resolve fields directly from a rule's strategy type (generic, strategy-agnostic):</b>
 * <pre>{@code
 * PlatformUsageCreditsStrategyType strategyType = rule.getStrategyType();
 *
 * List<?>                 fields = SubjectEntityField.forSubjectType(strategyType.getEntityClass());
 * Map<String, ?> index = SubjectEntityField.byDisplayNames(strategyType.getEntityClass());
 * SubjectEntityField<?>   field  = index.get(leaf.getField());
 * }</pre>
 */
@Getter
public enum PlatformUsageCreditsStrategyType {
    RUN_STATE(PipelineRun.class);

    private final Class<? extends AbstractSecuredEntity> entityClass;

    PlatformUsageCreditsStrategyType(final Class<? extends AbstractSecuredEntity> entityClass) {
        this.entityClass = entityClass;
    }
}