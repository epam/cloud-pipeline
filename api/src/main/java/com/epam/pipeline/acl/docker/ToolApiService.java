/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.acl.docker;

import com.epam.pipeline.entity.configuration.ConfigurationEntry;
import com.epam.pipeline.entity.docker.ImageDescription;
import com.epam.pipeline.entity.docker.ImageHistoryLayer;
import com.epam.pipeline.entity.docker.ToolDescription;
import com.epam.pipeline.entity.docker.ToolImageDockerfile;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.docker.ToolVersionAttributes;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.scan.ToolScanPolicy;
import com.epam.pipeline.entity.scan.ToolScanResult;
import com.epam.pipeline.entity.scan.ToolScanResultView;
import com.epam.pipeline.entity.scan.ToolVersionScanResult;
import com.epam.pipeline.entity.scan.ToolVersionScanResultView;
import com.epam.pipeline.entity.tool.ToolSymlinkRequest;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import com.epam.pipeline.manager.docker.scan.ToolScanManager;
import com.epam.pipeline.manager.docker.scan.ToolScanScheduler;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.pipeline.ToolScanInfoManager;
import com.epam.pipeline.manager.security.acl.AclMask;
import com.epam.pipeline.security.acl.AclExpressions;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ToolApiService {

    private final ToolManager toolManager;
    private final ToolScanScheduler toolScanScheduler;
    private final ToolScanManager toolScanManager;
    private final ToolVersionManager toolVersionManager;
    private final ToolScanInfoManager toolScanInfoManager;

    @PreAuthorize(AclExpressions.ADMIN_ONLY +
            "OR hasPermission(#tool.toolGroupId, 'com.epam.pipeline.entity.pipeline.ToolGroup', 'WRITE')")
    public Tool create(final Tool tool) { // tool.registryId was tested, but only tool.registry field is required
        return toolManager.create(tool, true);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY +
            "OR @grantPermissionManager.toolPermission(#tool.registry, #tool.image, 'WRITE')")
    @AclMask
    public Tool updateTool(Tool tool) {
        return toolManager.updateTool(tool);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    @AclMask
    public ToolVersionScanResult updateWhiteListWithToolVersion(final long toolId, final String version,
                                                                          final boolean fromWhiteList) {
        return toolManager.updateWhiteListWithToolVersionStatus(toolId, version, fromWhiteList);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY +
            "OR @grantPermissionManager.toolPermission(#registry, #image, 'READ')")
    @AclMask
    public Tool loadTool(String registry, final String image) {
        if (!StringUtils.hasText(registry)){
            return toolManager.loadByNameOrId(image);
        } else {
            return toolManager.loadTool(registry, image);
        }
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY +
            "OR @grantPermissionManager.toolPermission(#registry, #image, 'WRITE')")
    public Tool delete(String registry, final String image, boolean hard) {
        return toolManager.delete(registry, image, hard);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY +
            "OR @grantPermissionManager.toolPermission(#registry, #image, 'WRITE')")
    public Tool deleteToolVersion(String registry, final String image, String version) {
        return toolManager.deleteToolVersion(registry, image, version);
    }

    @PreAuthorize(AclExpressions.TOOL_READ)
    public List<String> loadImageTags(Long id) {
        return toolManager.loadTags(id);
    }

    @PreAuthorize(AclExpressions.TOOL_READ)
    public ImageDescription getImageDescription(Long id, String tag) {
        return toolManager.loadToolDescription(id, tag);
    }

    @PreAuthorize(AclExpressions.TOOL_READ)
    public List<ImageHistoryLayer> getImageHistory(final Long id, final String tag) {
        return toolManager.loadToolHistory(id, tag);
    }

    @PreAuthorize(AclExpressions.TOOL_READ)
    public ToolImageDockerfile loadDockerFile(final Long id, final String tag, final String from) {
        return toolManager.loadDockerFile(id, tag, from);
    }

    @PreAuthorize(AclExpressions.TOOL_READ)
    public String getImageDefaultCommand(final Long id, final String tag) {
        return toolManager.loadToolDefaultCommand(id, tag);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY + AclExpressions.OR +
            "@grantPermissionManager.toolPermission(#registry, #image, 'OWNER')")
    public void forceScanTool(final String registry, final String image, final String version, final Boolean rescan) {
        toolScanScheduler.forceScheduleScanTool(registry, image, version, rescan);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public void clearToolScan(final String registry, final String image, final String version) {
        toolManager.clearToolScan(registry, image, version);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY +
            "OR @grantPermissionManager.toolPermission(#registry, #image, 'READ')")
    public ToolScanResultView loadToolScanResult(String registry, String image) {
        ToolScanResult toolScanResult = toolManager.loadToolScanResult(registry, image);
        if (toolScanResult != null) {
            return new ToolScanResultView(toolScanResult.getToolId(),
                    toolScanResult.getToolVersionScanResults().values().stream().map(vsr ->
                            ToolVersionScanResultView.from(vsr,
                                                           toolManager.isToolOSVersionAllowed(vsr.getToolOSVersion()))
                    ).collect(Collectors.toMap(ToolVersionScanResultView::getVersion, vsrv -> vsrv)));
        } else {
            return null;
        }
    }

    public ToolScanPolicy loadSecurityPolicy() {
        return toolScanManager.getPolicy();
    }

    @PreAuthorize(AclExpressions.TOOL_WRITE)
    public long updateToolIcon(long id, String fileName, byte[] image) {
        return toolManager.updateToolIcon(id, fileName, image);
    }

    @PreAuthorize(AclExpressions.TOOL_WRITE)
    public void deleteToolIcon(long id) {
        toolManager.deleteToolIcon(id);
    }

    @PreAuthorize(AclExpressions.TOOL_READ)
    public Pair<String, InputStream> loadToolIcon(long id) {
        return toolManager.loadToolIcon(id);
    }

    @PreAuthorize(AclExpressions.TOOL_READ)
    public ToolDescription loadToolAttributes(Long id) {
        return toolManager.loadToolAttributes(id);
    }

    @PreAuthorize(AclExpressions.TOOL_READ)
    public ToolDescription loadToolInfo(Long id) {
        return toolScanInfoManager.loadToolInfo(id);
    }

    @PreAuthorize(AclExpressions.TOOL_READ)
    public ToolVersionAttributes loadToolVersionAttributes(final Long id, final String version) {
        return toolManager.loadToolVersionAttributes(id, version);
    }

    @PreAuthorize(AclExpressions.TOOL_WRITE)
    public ToolVersion createToolVersionSettings(final Long id, final String version, final boolean allowCommit,
                                                 final List<ConfigurationEntry> settings) {
        return toolVersionManager.createToolVersionSettings(id, version, allowCommit, settings);
    }

    @PreAuthorize(AclExpressions.TOOL_READ)
    public List<ToolVersion> loadToolVersionSettings(final Long id, final String version) {
        return toolVersionManager.loadToolVersionSettings(id, version);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY +
            "OR hasPermission(#request.toolId, 'com.epam.pipeline.entity.pipeline.Tool', 'READ') " +
            "AND hasRole('TOOL_GROUP_MANAGER') " +
            "AND @grantPermissionManager.toolGroupPermission(#request.groupId, 'WRITE')")
    public Tool symlink(final ToolSymlinkRequest request) {
        return toolManager.symlink(request);
    }
}
