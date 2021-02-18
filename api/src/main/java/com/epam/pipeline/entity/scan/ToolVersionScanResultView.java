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
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Getter
@Builder
@EqualsAndHashCode
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
    private Map<VulnerabilitySeverity, Integer> vulnerabilitiesCount;

    public static ToolVersionScanResultView from(final ToolVersionScanResult scanResult, final boolean isOSAllowed) {
        return Optional.ofNullable(scanResult).map(scan ->
            ToolVersionScanResultView.builder()
                    .toolId(scan.getToolId())
                    .version(scan.getVersion())
                    .toolOSVersion(ToolOSVersionView.from(scan.getToolOSVersion(), isOSAllowed))
                    .status(scan.getStatus())
                    .scanDate(scan.getScanDate())
                    .successScanDate(scan.getSuccessScanDate())
                    .vulnerabilities(scan.getVulnerabilities())
                    .dependencies(scan.getDependencies())
                    .isAllowedToExecute(scan.isAllowedToExecute())
                    .fromWhiteList(scan.isFromWhiteList())
                    .gracePeriod(scan.getGracePeriod())
                    .vulnerabilitiesCount(scan.getVulnerabilitiesCount())
                    .build()
        ).orElse(null);
    }
}
