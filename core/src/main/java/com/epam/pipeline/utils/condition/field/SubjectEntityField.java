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

package com.epam.pipeline.utils.condition.field;

import com.epam.pipeline.utils.condition.ConditionExpression;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.utils.condition.FieldType;

import java.util.*;

/**
 * Describes a single filterable field of a domain object of type {@code T}.
 *
 * <p>Implementations register their display names, value type, and extraction logic so that
 * {@code ComputeQuotaRuleEvaluator} can evaluate filter expression trees against any subject
 * type — not just {@link PipelineRun}.
 *
 * @param <T> the subject type this field belongs to (e.g. {@code PipelineRun})
 */
public interface SubjectEntityField<T> {

    /** Value type that governs which operators are valid and how comparisons are performed. */
    FieldType getType();

    /**
     * Whether this field supports the duration gate on a filter expression leaf.
     * When {@code true} a non-null {@link ConditionExpression#getDuration()} activates a
     * time-based check in the evaluator's TAG strategy.
     */
    boolean isSupportsDuration();

    /**
     * Extracts the field's string representation from {@code subject} for comparison
     * against the rule value. Returns {@code null} when the value is absent.
     */
    String extract(T subject);

    /**
     * Extracts a string-to-string map from {@code subject} for {@link FieldType#KEY_VALUE} fields.
     * Returns an empty map by default; {@link FieldType#KEY_VALUE} fields must override this.
     */
    default Map<String, String> extractMap(T subject) {
        return Collections.emptyMap();
    }

    /** One or more names by which this field can be referenced in a rule expression. */
    List<String> getDisplayNames();

    /**
     * Returns all fields that can be evaluated against the given subject type,
     * or an empty list if the type is not recognised.
     */
    @SuppressWarnings("unchecked")
    static <T> List<SubjectEntityField<T>> forSubjectType(final Class<T> subjectType) {
        if (subjectType == PipelineRun.class) {
            return (List<SubjectEntityField<T>>) (List<?>) Arrays.asList(PipelineRunField.values());
        }
        return Collections.emptyList();
    }

    static <T> Map<String, SubjectEntityField<T>> byDisplayNames(final Class<T> subjectType) {
        final List<SubjectEntityField<T>> fields = forSubjectType(subjectType);
        final Map<String, SubjectEntityField<T>> map = new HashMap<>();
        for (final SubjectEntityField<T> f : fields) {
            for (final String name : f.getDisplayNames()) {
                map.put(name, f);
            }
        }
        return Collections.unmodifiableMap(map);
    }
}
