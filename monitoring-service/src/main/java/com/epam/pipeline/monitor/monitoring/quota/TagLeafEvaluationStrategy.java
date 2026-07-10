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
import com.epam.pipeline.entity.quota.ConditionOperator;
import com.epam.pipeline.entity.quota.FieldType;
import com.epam.pipeline.entity.quota.QuotaFilterExpression;
import com.epam.pipeline.entity.quota.RunField;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;

/**
 * Evaluates {@link FieldType#TAGS} leaf nodes, optionally applying a duration gate.
 *
 * <p><b>Boolean check</b> ({@link #doEvaluate}): {@code =} matches when the tag key is
 * present in the run's tag map; {@code !=} matches when absent. Comparison is
 * case-insensitive. Supports {@code =} and {@code !=}.
 *
 * <p><b>Duration gate</b> (when {@link QuotaFilterExpression#getDuration()} is non-null):
 * After the boolean check passes, the companion tag {@code <tagName>_date}
 * (e.g. {@code IDLE_date}) is read. Its value is parsed as
 * {@code yyyy-MM-dd HH:mm:ss.SSS} UTC. The leaf only matches if the elapsed time
 * {@code now − tagDate ≥ duration} hours. A missing or unparseable date tag returns
 * {@code false}.
 */
@Slf4j
class TagLeafEvaluationStrategy extends StandardLeafEvaluationStrategy {

    /** Suffix appended to a tag name to form the companion timestamp key (e.g. {@code IDLE_date}). */
    static final String DATE_SUFFIX = "_date";

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    TagLeafEvaluationStrategy(final RunField field) {
        super(field);
    }

    @Override
    protected boolean doEvaluate(final ConditionOperator op, final String runValue, final String expressionValue) {
        return ConditionOperator.EQUALS.equals(op) == containsKey(runValue, expressionValue);
    }

    /** Adds the duration gate on top of the base boolean check. */
    @Override
    public boolean evaluate(final QuotaFilterExpression node, final PipelineRun run, final LocalDateTime now) {
        final boolean boolResult = super.evaluate(node, run, now);
        if (!boolResult || node.getDuration() == null) {
            return boolResult;
        }
        return checkDuration(node, run, now);
    }

    private boolean checkDuration(final QuotaFilterExpression node, final PipelineRun run,
                                  final LocalDateTime now) {
        final String dateTagKey = node.getValue() + DATE_SUFFIX;
        final Map<String, String> tags = run.getTags();
        if (MapUtils.isEmpty(tags)) {
            return false;
        }
        final String dateStr = tags.get(dateTagKey);
        if (dateStr == null) {
            log.debug("Run {} has tag '{}' but companion date tag '{}' is absent — skipping duration check",
                    run.getId(), node.getValue(), dateTagKey);
            return false;
        }
        try {
            final LocalDateTime tagSince = LocalDateTime.parse(dateStr, DATE_FORMATTER);
            return ChronoUnit.HOURS.between(tagSince, now) >= node.getDuration();
        } catch (DateTimeParseException e) {
            log.warn("Cannot parse date tag '{}' value '{}' on run {}: {}",
                    dateTagKey, dateStr, run.getId(), e.getMessage());
            return false;
        }
    }

    private static boolean containsKey(final String commaSeparatedKeys, final String tag) {
        return Arrays.stream(commaSeparatedKeys.split(",", -1))
                .anyMatch(k -> k.equalsIgnoreCase(tag));
    }
}
