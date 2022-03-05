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

package com.epam.pipeline.acl.report;

import com.epam.pipeline.dto.report.ReportFilter;
import com.epam.pipeline.dto.report.NodePoolUsageReport;
import com.epam.pipeline.dto.report.NodePoolReportType;
import com.epam.pipeline.dto.report.UsersUsageInfo;
import com.epam.pipeline.dto.report.UsersUsageReportFilterVO;
import com.epam.pipeline.manager.report.pool.NodePoolReportService;
import com.epam.pipeline.manager.report.user.UsersUsageReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_ONLY;

@Service
@RequiredArgsConstructor
public class ReportApiService {
    private final UsersUsageReportService usersUsageReportService;
    private final NodePoolReportService nodePoolReportService;

    @PreAuthorize(ADMIN_ONLY)
    public List<UsersUsageInfo> loadUsersUsage(final UsersUsageReportFilterVO filter) {
        return usersUsageReportService.loadUsersUsage(filter);
    }

    @PreAuthorize(ADMIN_ONLY)
    public List<NodePoolUsageReport> loadNodePoolReport(final ReportFilter filter) {
        return nodePoolReportService.getReport(filter);
    }

    @PreAuthorize(ADMIN_ONLY)
    public InputStream loadNodePoolUsageReportFile(final ReportFilter filter, final Long targetPool,
                                                   final NodePoolReportType type) {
        return nodePoolReportService.getReportFile(filter, targetPool, type);
    }
}
