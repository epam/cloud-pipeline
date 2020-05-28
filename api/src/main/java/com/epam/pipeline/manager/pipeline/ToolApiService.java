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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.entity.configuration.ConfigurationEntry;
import com.epam.pipeline.entity.docker.ImageDescription;
import com.epam.pipeline.entity.docker.ToolDescription;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.scan.ToolScanPolicy;
import com.epam.pipeline.entity.scan.ToolScanResult;
import com.epam.pipeline.entity.scan.ToolScanResultView;
import com.epam.pipeline.entity.scan.ToolVersionScanResult;
import com.epam.pipeline.entity.scan.ToolVersionScanResultView;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import com.epam.pipeline.manager.docker.scan.ToolScanManager;
import com.epam.pipeline.manager.docker.scan.ToolScanScheduler;
import com.epam.pipeline.manager.security.acl.AclMask;
import com.epam.pipeline.security.acl.AclExpressions;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ToolApiService {

    @Autowired
    private ToolManager toolManager;

    @Autowired
    private ToolScanScheduler toolScanScheduler;

    @Autowired
    private ToolScanManager toolScanManager;

    @Autowired
    private ToolVersionManager toolVersionManager;

    @PreAuthorize("hasRole('ADMIN') OR "
            + "hasPermission(#tool.toolGroupId, 'com.epam.pipeline.entity.pipeline.ToolGroup', 'WRITE')")
    public Tool create(final Tool tool) { // tool.registryId was tested, but only tool.registry field is required
        return toolManager.create(tool, true);
    }

    @PreAuthorize("hasRole('ADMIN') or "
            + "@grantPermissionManager.toolPermission(#tool.registry, #tool.image, 'WRITE')")
    @AclMask
    public Tool updateTool(Tool tool) {
        return toolManager.updateTool(tool);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @AclMask
    public ToolVersionScanResult updateWhiteListWithToolVersion(final long toolId, final String version,
                                                                          final boolean fromWhiteList) {
        return toolManager.updateWhiteListWithToolVersionStatus(toolId, version, fromWhiteList);
    }

    @PreAuthorize("hasRole('ADMIN') or @grantPermissionManager.toolPermission(#registry, #image, 'READ')")
    @AclMask
    public Tool loadTool(String registry, final String image) {
        if (!StringUtils.hasText(registry)){
            return toolManager.loadByNameOrId(image);
        } else {
            return toolManager.loadTool(registry, image);
        }
    }

    @PostAuthorize("hasRole('ADMIN') or hasPermission(returnObject, 'EXECUTE')")
    @AclMask
    public Tool loadToolForExecution(String image) {
        return toolManager.loadByNameOrId(image);
    }

    @PreAuthorize("hasRole('ADMIN') or @grantPermissionManager.toolPermission(#registry, #image, 'WRITE')")
    public Tool delete(String registry, final String image, boolean hard) {
        return toolManager.delete(registry, image, hard);
    }

    @PreAuthorize("hasRole('ADMIN') or @grantPermissionManager.toolPermission(#registry, #image, 'WRITE')")
    public Tool deleteToolVersion(String registry, final String image, String version) {
        return toolManager.deleteToolVersion(registry, image, version);
    }

    @PreAuthorize("hasRole('ADMIN') OR "
            + "hasPermission(#id, 'com.epam.pipeline.entity.pipeline.Tool', 'READ')")
    public List<String> loadImageTags(Long id) {
        return toolManager.loadTags(id);
    }

    @PreAuthorize("hasRole('ADMIN') OR "
            + "hasPermission(#id, 'com.epam.pipeline.entity.pipeline.Tool', 'READ')")
    public ImageDescription getImageDescription(Long id, String tag) {
        return toolManager.loadToolDescription(id, tag);
    }

    @PreAuthorize("hasRole('ADMIN') OR "
            + "hasPermission(#id, 'com.epam.pipeline.entity.pipeline.Tool', 'READ')")
    public List<String> getImageHistory(final Long id, final String tag) {
        return toolManager.loadToolHistory(id, tag);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public void forceScanTool(String registry, String image, String version, final Boolean rescan) {
        toolScanScheduler.forceScheduleScanTool(registry, image, version, rescan);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public void clearToolScan(final String registry, final String tool, final String version) {
        toolManager.clearToolScan(registry, tool, version);
    }

    @PreAuthorize("hasRole('ADMIN') or @grantPermissionManager.toolPermission(#registry, #image, 'READ')")
    public ToolScanResultView loadToolScanResult(String registry, String image) {
        ToolScanResult toolScanResult = toolManager.loadToolScanResult(registry, image);
        if (toolScanResult != null) {
            return new ToolScanResultView(toolScanResult.getToolId(),
                    toolScanResult.getToolVersionScanResults().values().stream().map(vsr ->
                            ToolVersionScanResultView.from(vsr, toolManager.isToolOSVersionAllowed(vsr.getToolOSVersion()))
                    ).collect(Collectors.toMap(ToolVersionScanResultView::getVersion, vsrv -> vsrv)));
        } else {
            return null;
        }
    }

    public ToolScanPolicy loadSecurityPolicy() {
        return toolScanManager.getPolicy();
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#toolId, 'com.epam.pipeline.entity.pipeline.Tool', 'WRITE')")
    public long updateToolIcon(long toolId, String fileName, byte[] image) {
        return toolManager.updateToolIcon(toolId, fileName, image);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#toolId, 'com.epam.pipeline.entity.pipeline.Tool', 'WRITE')")
    public void deleteToolIcon(long toolId) {
        toolManager.deleteToolIcon(toolId);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#toolId, 'com.epam.pipeline.entity.pipeline.Tool', 'READ')")
    public Pair<String, InputStream> loadToolIcon(long toolId) {
        return toolManager.loadToolIcon(toolId);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#toolId, 'com.epam.pipeline.entity.pipeline.Tool', 'READ')")
    public ToolDescription loadToolAttributes(Long toolId) {
        return toolManager.loadToolAttributes(toolId);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#toolId, 'com.epam.pipeline.entity.pipeline.Tool', 'WRITE')")
    public ToolVersion createToolVersionSettings(final Long toolId, final String version,
                                                 final List<ConfigurationEntry> settings) {
        return toolVersionManager.createToolVersionSettings(toolId, version, settings);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#toolId, 'com.epam.pipeline.entity.pipeline.Tool', 'READ')")
    public List<ToolVersion> loadToolVersionSettings(final Long toolId, final String version) {
        return toolVersionManager.loadToolVersionSettings(toolId, version);
    }
}
