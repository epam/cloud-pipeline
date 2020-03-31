/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ToolVersionScanResult {

    private Long toolId;
    private String version;
    private ToolOSVersion toolOSVersion;
    private ToolScanStatus status;
    private Date scanDate;
    private Date successScanDate;
    private List<Vulnerability> vulnerabilities;
    private List<ToolDependency> dependencies;
    private boolean isAllowedToExecute;
    private boolean fromWhiteList;
    private Date gracePeriod;

    @JsonIgnore
    private String lastLayerRef;

    @JsonIgnore
    private String digest;

    public ToolVersionScanResult(ToolScanStatus status, Date scanDate, List<Vulnerability> vulnerabilities,
                                 List<ToolDependency> dependencies, ToolOSVersion toolOSVersion) {
        this.status = status;
        this.scanDate = new Date(scanDate.getTime());
        this.vulnerabilities = new ArrayList<>(vulnerabilities);
        this.dependencies = new ArrayList<>(dependencies);
        this.toolOSVersion = toolOSVersion;
        if (status == ToolScanStatus.COMPLETED) {
            this.successScanDate = new Date(scanDate.getTime());
        }
    }

    public ToolVersionScanResult(String version) {
        this.version = version;
        this.status = ToolScanStatus.NOT_SCANNED;
        this.vulnerabilities = Collections.emptyList();
        this.dependencies = Collections.emptyList();
    }

    public ToolVersionScanResult(String version,  ToolOSVersion toolOSVersion, List<Vulnerability> vulnerabilities,
                                 List<ToolDependency> dependencies, ToolScanStatus status,
                                 String lastLayerRef, String digest) {
        this.version = version;
        this.vulnerabilities = new ArrayList<>(vulnerabilities);
        this.lastLayerRef = lastLayerRef;
        this.digest = digest;
        this.status = status;
        this.dependencies = new ArrayList<>(dependencies);
        this.scanDate = new Date();
        this.toolOSVersion = toolOSVersion;
        if (status == ToolScanStatus.COMPLETED) {
            this.successScanDate = scanDate;
        }
    }
}
