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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ToolVersionScanResult {

    private Long toolId;
    private String version;
    private ToolScanStatus status;
    private Date scanDate;
    private Date successScanDate;
    private List<Vulnerability> vulnerabilities;
    private List<ToolDependency> dependencies;
    private boolean isAllowedToExecute;

    @JsonIgnore
    private String lastLayerRef;

    public ToolVersionScanResult(ToolScanStatus status, Date scanDate, List<Vulnerability> vulnerabilities,
                                 List<ToolDependency> dependencies) {
        this.status = status;
        this.scanDate = scanDate;
        this.vulnerabilities = vulnerabilities;
        this.dependencies = dependencies;
        if (status == ToolScanStatus.COMPLETED) {
            this.successScanDate = scanDate;
        }
    }

    public ToolVersionScanResult(String version) {
        this.version = version;
        this.status = ToolScanStatus.NOT_SCANNED;
        this.vulnerabilities = Collections.emptyList();
        this.dependencies = Collections.emptyList();
    }

    public ToolVersionScanResult(String version, List<Vulnerability> vulnerabilities, List<ToolDependency> dependencies,
                                 ToolScanStatus status, String lastLayerRef) {
        this.version = version;
        this.vulnerabilities = vulnerabilities;
        this.lastLayerRef = lastLayerRef;
        this.status = status;
        this.dependencies = dependencies;
        this.scanDate = new Date();
        if (status == ToolScanStatus.COMPLETED) {
            this.successScanDate = scanDate;
        }
    }
}
