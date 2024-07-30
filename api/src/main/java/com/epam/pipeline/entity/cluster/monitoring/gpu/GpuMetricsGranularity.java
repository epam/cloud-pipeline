/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.cluster.monitoring.gpu;

import java.util.List;

/**
 * Represents a levels of details for GPU usage metrics.
 */
public enum GpuMetricsGranularity {
    /**
     * Loads charts of usages by each GPU ID
     */
    DETAILS,
    /**
     * Loads charts of aggregated usages across all GPU IDs
     */
    AGGREGATIONS,
    /**
     * Loads aggregated usages for requested period of time
     */
    GLOBAL,
    /**
     * Loads all levels described above
     */
    ALL;

    public static boolean hasDetails(final List<GpuMetricsGranularity> types) {
        return types.contains(GpuMetricsGranularity.ALL) || types.contains(GpuMetricsGranularity.DETAILS);
    }

    public static boolean hasAggregations(final List<GpuMetricsGranularity> types) {
        return types.contains(GpuMetricsGranularity.ALL) || types.contains(GpuMetricsGranularity.AGGREGATIONS);
    }

    public static boolean hasGlobal(final List<GpuMetricsGranularity> types) {
        return types.contains(GpuMetricsGranularity.ALL) || types.contains(GpuMetricsGranularity.GLOBAL);
    }
}
