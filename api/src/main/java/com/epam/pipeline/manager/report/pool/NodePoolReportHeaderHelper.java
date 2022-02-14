/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.report.pool;

import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Map;

@Builder
@AllArgsConstructor
public class NodePoolReportHeaderHelper {
    private final String timestampColumnName;
    private final String totalNodesColumnFormat;
    private final String occupiedNodesColumnFormat;
    private final String utilizationColumnsFormat;
    private final Map<Long, String> pools;

    public String timestamp() {
        return timestampColumnName;
    }

    public String totalNodes(final String poolName) {
        return String.format(totalNodesColumnFormat, poolName);
    }

    public String occupiedNodes(final String poolName) {
        return String.format(occupiedNodesColumnFormat, poolName);
    }

    public String utilization(final Long poolId) {
        final String poolName = poolName(poolId);
        return String.format(utilizationColumnsFormat, poolName);
    }

    public String poolName(final Long poolId) {
        return pools.get(poolId);
    }
}
