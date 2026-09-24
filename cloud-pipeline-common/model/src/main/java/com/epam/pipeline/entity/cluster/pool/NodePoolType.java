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

package com.epam.pipeline.entity.cluster.pool;

/**
 * How a pool's nodes are shared between runs. Independent of whether the pool is backed by a capacity
 * reservation - the two are orthogonal.
 *
 * <p>This is the {@code cloud-pipeline-common:model} copy, consumed by {@code monitoring-service}. It
 * mirrors the {@code core} enum of the same name; the two modules declare the same packages and no module
 * depends on both.
 */
public enum NodePoolType {

    /**
     * One node hosts one run. How every node pool behaves today.
     */
    STANDARD,

    /**
     * One node hosts several concurrent runs, each limited to a portion of its CPU/GPU/RAM. Not supported
     * yet, and rejected at validation time.
     */
    SHARABLE_NODE
}
