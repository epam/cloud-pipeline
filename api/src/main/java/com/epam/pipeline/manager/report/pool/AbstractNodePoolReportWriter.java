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

import com.epam.pipeline.dto.report.NodePoolReportType;
import com.epam.pipeline.dto.report.NodePoolUsageReport;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.manager.cluster.pool.NodePoolManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public abstract class AbstractNodePoolReportWriter {
    private static final String DEFAULT_POOL_NAME_FORMAT = "pool%d";

    protected final PreferenceManager preferenceManager;
    protected final NodePoolManager nodePoolManager;

    abstract NodePoolReportType getType();

    abstract InputStream writeToStream(List<NodePoolUsageReport> report, Long targetPool, ChronoUnit interval);

    protected NodePoolReportHeaderHelper getHeaderHelper() {
        return NodePoolReportHeaderHelper.builder()
                .timestampColumnName(preferenceManager
                        .getPreference(SystemPreferences.REPORTS_TIMESTAMP_COLUMN_NAME))
                .totalNodesColumnFormat(preferenceManager
                        .getPreference(SystemPreferences.POOL_REPORTS_TOTAL_NODES_COLUMN_FORMAT))
                .occupiedNodesColumnFormat(preferenceManager
                        .getPreference(SystemPreferences.POOL_REPORTS_OCCUPIED_NODES_COLUMN_FORMAT))
                .utilizationColumnsFormat(preferenceManager
                        .getPreference(SystemPreferences.POOL_REPORTS_UTILIZATION_COLUMNS_FORMAT))
                .pools(ListUtils.emptyIfNull(nodePoolManager.loadAll()).stream()
                        .collect(Collectors.toMap(NodePool::getId, this::findNodePoolName)))
                .build();
    }

    private String findNodePoolName(final NodePool nodePool) {
        return StringUtils.isBlank(nodePool.getName())
                ? String.format(DEFAULT_POOL_NAME_FORMAT, nodePool.getId())
                : nodePool.getName();
    }
}
