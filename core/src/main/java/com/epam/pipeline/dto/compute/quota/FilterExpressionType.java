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

package com.epam.pipeline.dto.compute.quota;

/**
 * Structural type of a filter expression node.
 *
 * <ul>
 *   <li>{@link #LOGICAL} — a leaf node that carries a single field/operator/value comparison.</li>
 *   <li>{@link #AND}     — a composite node whose children must all evaluate to {@code true}.</li>
 *   <li>{@link #OR}      — a composite node where at least one child must evaluate to {@code true}.</li>
 * </ul>
 */
public enum FilterExpressionType {

    /** Leaf: a single field/operator/value predicate. */
    LOGICAL,

    /** Composite: all child expressions must be {@code true}. */
    AND,

    /** Composite: at least one child expression must be {@code true}. */
    OR
}
