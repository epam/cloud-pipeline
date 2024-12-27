/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.scan.clair;

import com.epam.pipeline.entity.pipeline.ToolScanStatus;
import com.epam.pipeline.entity.scan.ToolDependency;
import com.epam.pipeline.entity.scan.Vulnerability;
import com.epam.pipeline.entity.scan.VulnerabilitySeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Builder
@AllArgsConstructor
@Data
public class ClairVulnerabilities {
    private Map<VulnerabilitySeverity, Integer> vulnerabilitiesCount;
    private List<Vulnerability> vulnerabilities;
    private ToolScanStatus toolScanStatus;
    private List<ToolDependency> dependencies;
}
