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
import com.epam.pipeline.entity.quota.QuotaFilterExpression;

import java.time.LocalDateTime;

/**
 * Strategy for evaluating a single LOGICAL {@link QuotaFilterExpression} leaf node against a run.
 *
 * Each implementation encapsulates the field-specific extraction, operator validation,
 * and (where applicable) temporal duration check. The evaluator builds a registry of
 * strategies at startup — adding support for a new field requires only a new implementation
 * and a registration entry, with no changes to the evaluator itself.
 */
@FunctionalInterface
interface LeafEvaluationStrategy {

    /**
     * @param node the LOGICAL leaf node to evaluate
     * @param run  the run being tested
     * @param now  reference instant used for duration calculations
     * @return {@code true} when the run satisfies the leaf condition
     * @throws IllegalArgumentException when the field or operator is invalid
     */
    boolean evaluate(QuotaFilterExpression node, PipelineRun run, LocalDateTime now);
}
