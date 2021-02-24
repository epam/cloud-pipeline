/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.dao.tool.ToolVulnerabilityDao;
import com.epam.pipeline.entity.docker.ToolDescription;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.docker.ToolVersionAttributes;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolScanStatus;
import com.epam.pipeline.entity.scan.ToolVersionScanResult;
import com.epam.pipeline.entity.scan.ToolVersionScanResultView;
import com.epam.pipeline.entity.scan.VulnerabilitySeverity;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Class returns aggregated scan info without detailed vulnerability or dependency data
 */
@Service
@RequiredArgsConstructor
public class ToolScanInfoManager {

    private final ToolManager toolManager;
    private final ToolVersionManager toolVersionManager;
    private final ToolVulnerabilityDao toolVulnerabilityDao;

    public ToolDescription loadToolInfo(final Long toolId) {
        final Tool tool = toolManager.loadExisting(toolId);
        return tool.isSymlink() ? loadToolInfo(tool.getLink()) : loadToolInfo(tool);
    }

    public Optional<ToolVersionScanResult> loadToolVersionScanInfo(final Long toolId, final String version) {
        final Tool tool = toolManager.loadExisting(toolId);
        return tool.isSymlink() ? loadToolVersionScanInfo(tool.getLink(), version) :
                loadToolVersionScanInfo(tool, version);
    }

    private ToolDescription loadToolInfo(final Tool tool) {
        final ToolDescription result = new ToolDescription(tool.getId());
        final List<String> activeVersions = toolManager.loadTags(tool);
        if (StringUtils.isEmpty(activeVersions)) {
            return result;
        }
        final Map<String, ToolVersionScanResult> scanInfo = loadScanInfo(tool, activeVersions);
        final Map<String, ToolVersion> versionInfo = toolVersionManager.loadToolVersions(tool.getId(), activeVersions);
        final List<ToolVersionAttributes> versionsAttributes = activeVersions.stream()
                .map(version -> {
                    final ToolVersionScanResult scanResult = scanInfo.get(version);
                    final ToolVersionAttributes.ToolVersionAttributesBuilder builder = ToolVersionAttributes.builder()
                            .version(version)
                            .attributes(versionInfo.get(version));
                    Optional.ofNullable(scanResult)
                            .ifPresent(scan -> {
                                builder.scanResult(ToolVersionScanResultView.from(scan,
                                        toolManager.isToolOSVersionAllowed(scan.getToolOSVersion())));
                            });
                    return builder.build();
                })
                .collect(Collectors.toList());
        result.setVersions(versionsAttributes);
        return result;
    }

    private Map<String, ToolVersionScanResult> loadScanInfo(final Tool tool, final List<String> versions) {
        final Map<String, ToolVersionScanResult> scanInfo = loadScanInfoForVersions(tool, versions);
        scanInfo.forEach((version, scan) -> toolManager.setExecutePermission(tool, version, scan));
        return scanInfo;
    }

    private Map<String, ToolVersionScanResult> loadScanInfoForVersions(final Tool tool, final List<String> versions) {
        final Map<String, ToolVersionScanResult> scanInfo =
                toolVulnerabilityDao.loadToolVersionScanInfo(tool.getId(), versions);

        //try to calculate vulnerability data from DB if precalculated info is not available
        final List<String> versionsWithoutVulnerabilityStats = scanInfo.entrySet().stream().filter(e -> {
            final ToolVersionScanResult scan = e.getValue();
            return !scan.getStatus().equals(ToolScanStatus.NOT_SCANNED) &&
                    MapUtils.isEmpty(scan.getVulnerabilitiesCount());
        }).map(Map.Entry::getKey).collect(Collectors.toList());

        if (!CollectionUtils.isEmpty(versionsWithoutVulnerabilityStats)) {
            final Map<String, Map<VulnerabilitySeverity, Integer>> missingVulnerabilityCount =
                    toolVulnerabilityDao.loadVulnerabilityCount(tool.getId(), versionsWithoutVulnerabilityStats);
            missingVulnerabilityCount.forEach(
                (version, vulnerabilities) -> scanInfo.get(version).setVulnerabilitiesCount(vulnerabilities));
        }
        return scanInfo;
    }

    private Optional<ToolVersionScanResult> loadToolVersionScanInfo(final Tool tool, final String version) {
        final Map<String, ToolVersionScanResult> result =
                loadScanInfoForVersions(tool, Collections.singletonList(version));
        return Optional.ofNullable(result.get(version));
    }
}
