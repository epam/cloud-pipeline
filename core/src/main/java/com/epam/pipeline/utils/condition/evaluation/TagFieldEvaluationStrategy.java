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

package com.epam.pipeline.utils.condition.evaluation;

import com.epam.pipeline.utils.condition.ConditionExpression;
import com.epam.pipeline.utils.condition.FieldType;
import com.epam.pipeline.utils.condition.field.SubjectEntityField;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Evaluates {@link FieldType#TAGS} leaf nodes, optionally applying a duration gate.
 *
 * <p><b>Boolean check</b> ({@link #doEvaluate}): {@code =} matches when the tag key is
 * present in the subject's tag map; {@code !=} matches when absent. Comparison is
 * case-insensitive. Supports {@code =} and {@code !=}.
 *
 * <p><b>Duration gate</b> (when {@link ConditionExpression#getDuration()} is non-null):
 * After the boolean check passes, the companion tag {@code <tagName>_date}
 * (e.g. {@code IDLE_date}) is read via the injected {@code tagsExtractor}. Its value is
 * parsed as {@code yyyy-MM-dd HH:mm:ss.SSS} UTC. The leaf only matches if the elapsed time
 * {@code now − tagDate ≥ duration} hours. A missing or unparseable date tag returns
 * {@code false}.
 *
 * @param <T> the subject type being evaluated
 */
@Slf4j
public class TagFieldEvaluationStrategy<T> extends KeyValueFieldEvaluationStrategy<T> {

    /** Suffix appended to a tag name to form the companion timestamp key (e.g. {@code IDLE_date}). */
    public static final String DATE_SUFFIX = "_date";

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public TagFieldEvaluationStrategy(final SubjectEntityField<T> field) {
        super(field);
    }

    /** Adds the duration gate on top of the base boolean check. */
    @Override
    public boolean evaluate(final ConditionExpression condition, final T subject, final LocalDateTime now) {
        final boolean boolResult = super.evaluate(condition, subject, now);
        if (!boolResult || condition.getDuration() == null) {
            return boolResult;
        }
        return checkDuration(condition, subject, now);
    }

    private boolean checkDuration(final ConditionExpression condition, final T subject, final LocalDateTime now) {
        final String expressionValue = condition.getValue();
        final int sep = expressionValue.indexOf('=');
        final String name = sep < 0 ? expressionValue : expressionValue.substring(0, sep);
        final String dateTagKey = name + DATE_SUFFIX;
        final Map<String, String> tags = field.extractMap(subject);
        if (MapUtils.isEmpty(tags)) {
            return false;
        }
        final String dateStr = tags.get(dateTagKey);
        if (dateStr == null) {
            log.debug("Subject has tag '{}' but companion date tag '{}' is absent — skipping duration check",
                    condition.getValue(), dateTagKey);
            return false;
        }
        try {
            final LocalDateTime tagSince = LocalDateTime.parse(dateStr, DATE_FORMATTER);
            return ChronoUnit.HOURS.between(tagSince, now) >= condition.getDuration();
        } catch (DateTimeParseException e) {
            log.warn("Cannot parse date tag '{}' value '{}': {}", dateTagKey, dateStr, e.getMessage());
            return false;
        }
    }

}
