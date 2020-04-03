/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.scan;

import com.epam.pipeline.entity.pipeline.ToolScanStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.Date;
import java.util.List;

@Getter
@Builder
public class ToolVersionScanResultView {

    private Long toolId;
    private String version;
    private ToolOSVersionView toolOSVersion;
    private ToolScanStatus status;
    private Date scanDate;
    private Date successScanDate;
    private List<Vulnerability> vulnerabilities;
    private List<ToolDependency> dependencies;
    private boolean isAllowedToExecute;
    private boolean fromWhiteList;
    private Date gracePeriod;

    public static ToolVersionScanResultView from(final ToolVersionScanResult scanResult, final boolean isOSAllowed) {
        return scanResult != null
                ? ToolVersionScanResultView.builder()
                    .toolId(scanResult.getToolId())
                    .version(scanResult.getVersion())
                    .toolOSVersion(ToolOSVersionView.from(scanResult.getToolOSVersion(), isOSAllowed))
                    .status(scanResult.getStatus())
                    .scanDate(scanResult.getScanDate())
                    .successScanDate(scanResult.getSuccessScanDate())
                    .vulnerabilities(scanResult.getVulnerabilities())
                    .dependencies(scanResult.getDependencies())
                    .isAllowedToExecute(scanResult.isAllowedToExecute())
                    .fromWhiteList(scanResult.isFromWhiteList())
                    .gracePeriod(scanResult.getGracePeriod()).build()
                : null;
    }

}
