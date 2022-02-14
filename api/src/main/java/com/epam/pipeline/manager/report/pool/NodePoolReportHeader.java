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

import com.epam.pipeline.dto.report.NodePoolUsageReport;
import com.epam.pipeline.dto.report.NodePoolUsageReportRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NodePoolReportHeader {
    private final NodePoolReportHeaderHelper helper;
    private final List<String> header;

    public NodePoolReportHeader(final NodePoolReportHeaderHelper helper) {
        this.helper = helper;
        this.header = new ArrayList<>();
        this.header.add(helper.timestamp());
    }

    public NodePoolReportHeader withPoolData(final Long poolId) {
        final String poolName = helper.poolName(poolId);
        header.add(helper.totalNodes(poolName));
        header.add(helper.occupiedNodes(poolName));
        return this;
    }

    public NodePoolReportHeader withUtilizationData(
            final List<NodePoolUsageReport> report,
            final Map<Integer, List<NodePoolUsageReportRecord>> reportByHeaderIndex) {
        report.forEach(poolReport -> {
            header.add(helper.utilization(poolReport.getPoolId()));
            reportByHeaderIndex.put(header.size() - 1, poolReport.getRecords());
        });
        return this;
    }

    public String[] toHeader() {
        return header.toArray(new String[]{});
    }
}
