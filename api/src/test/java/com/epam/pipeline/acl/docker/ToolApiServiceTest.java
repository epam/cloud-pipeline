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

package com.epam.pipeline.acl.docker;

import com.epam.pipeline.entity.configuration.ConfigurationEntry;
import com.epam.pipeline.entity.docker.ImageDescription;
import com.epam.pipeline.entity.docker.ImageHistoryLayer;
import com.epam.pipeline.entity.docker.ToolDescription;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.scan.ToolScanPolicy;
import com.epam.pipeline.entity.scan.ToolScanResult;
import com.epam.pipeline.entity.scan.ToolScanResultView;
import com.epam.pipeline.entity.scan.ToolVersionScanResult;
import com.epam.pipeline.entity.scan.ToolVersionScanResultView;
import com.epam.pipeline.entity.tool.ToolSymlinkRequest;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import com.epam.pipeline.manager.docker.scan.ToolScanManager;
import com.epam.pipeline.manager.docker.scan.ToolScanScheduler;
import com.epam.pipeline.manager.pipeline.ToolGroupManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.docker.ToolCreatorUtils;
import com.epam.pipeline.test.creator.docker.ToolGroupCreatorUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING_LIST;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

public class ToolApiServiceTest extends AbstractAclTest {

    private static final String TOOL_GROUP_MANAGER = "TOOL_GROUP_MANAGER";
    private static final int READ_PERMISSION = 1;
    private static final int WRITE_PERMISSION = 2;
    private static final int EXECUTE_PERMISSION = 4;
    private static final List<ConfigurationEntry> CONFIG_LIST = Collections.singletonList(new ConfigurationEntry());
    private final Tool tool = ToolCreatorUtils.getTool(ANOTHER_SIMPLE_USER);
    private final ToolVersionScanResult toolVersionScanResult = ToolCreatorUtils.getToolVersionScanResult();
    private final ImageDescription imageDescription = ToolCreatorUtils.getImageDescription();
    private final List<ImageHistoryLayer> imageHistoryLayers =
            Collections.singletonList(ToolCreatorUtils.getImageHistoryLayer());
    private final Pair<String, ByteArrayInputStream> pair =
            Pair.of(TEST_STRING, new ByteArrayInputStream(TEST_STRING.getBytes()));
    private final ToolDescription toolDescription = ToolCreatorUtils.getToolDescription();
    private final ToolVersion toolVersion = ToolCreatorUtils.getToolVersion();
    private final ToolScanPolicy toolScanPolicy = new ToolScanPolicy();
    private final List<ToolVersion> toolVersionList = Collections.singletonList(toolVersion);
    private final ToolSymlinkRequest toolSymlinkRequest = ToolCreatorUtils.getToolSymlinkRequest();
    private final ToolScanResult toolScanResult = ToolCreatorUtils.getToolScanResult();
    private final ToolScanResultView toolScanResultView = new ToolScanResultView(toolScanResult.getToolId(),
            Collections.singletonMap(TEST_STRING, ToolVersionScanResultView.builder().version(TEST_STRING).build()));
    private final ToolGroup toolGroup = ToolGroupCreatorUtils.getToolGroup(ANOTHER_SIMPLE_USER);

    @Autowired
    private ToolApiService toolApiService;

    @Autowired
    private ToolManager mockToolManager;

    @Autowired
    private ToolScanManager mockToolScanManager;

    @Autowired
    private ToolScanScheduler mockToolScanScheduler;

    @Autowired
    private ToolVersionManager mockToolVersionManager;

    @Autowired
    private ToolGroupManager mockToolGroupManager;

    @Autowired
    private AuthManager mockAuthManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateToolForAdmin() {
        doReturn(tool).when(mockToolManager).create(tool, true);

        assertThat(toolApiService.create(tool)).isEqualTo(tool);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldCreateToolWhenPermissionIsGranted() {
        doReturn(tool).when(mockToolManager).create(tool, true);
        initAclEntity(toolGroup, AclPermission.WRITE);

        assertThat(toolApiService.create(tool)).isEqualTo(tool);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyCreateToolWhenPermissionIsNotGranted() {
        initAclEntity(toolGroup);

        assertThrows(AccessDeniedException.class, () -> toolApiService.create(tool));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateToolForAdmin() {
        doReturn(tool).when(mockToolManager).updateTool(tool);

        assertThat(toolApiService.updateTool(tool)).isEqualTo(tool);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateToolWhenPermissionIsGranted() {
        doReturn(tool).when(mockToolManager).loadTool(TEST_STRING, TEST_STRING);
        doReturn(tool).when(mockToolManager).updateTool(tool);
        initAclEntity(tool, AclPermission.WRITE);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();
        final Tool returnedTool = toolApiService.updateTool(tool);

        assertThat(returnedTool.getMask()).isEqualTo(WRITE_PERMISSION);
        assertThat(returnedTool).isEqualTo(tool);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyUpdateToolWhenPermissionIsNotGranted() {
        doReturn(tool).when(mockToolManager).loadTool(TEST_STRING, TEST_STRING);
        initAclEntity(tool);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThrows(AccessDeniedException.class, () -> toolApiService.updateTool(tool));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void updateWhiteListWithToolVersion() {
        doReturn(toolVersionScanResult).when(mockToolManager)
                .updateWhiteListWithToolVersionStatus(ID, TEST_STRING, true);

        assertThat(toolApiService.updateWhiteListWithToolVersion(ID, TEST_STRING, true))
                .isEqualTo(toolVersionScanResult);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateWhiteListWithToolVersion() {
        doReturn(toolVersionScanResult).when(mockToolManager)
                .updateWhiteListWithToolVersionStatus(ID, TEST_STRING, true);

        assertThrows(AccessDeniedException.class,
            () -> toolApiService.updateWhiteListWithToolVersion(ID, TEST_STRING, true));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadToolWithRegistryForAdmin() {
        doReturn(tool).when(mockToolManager).loadTool(TEST_STRING, TEST_STRING);

        assertThat(toolApiService.loadTool(TEST_STRING, TEST_STRING)).isEqualTo(tool);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadToolWithoutRegistryForAdmin() {
        doReturn(tool).when(mockToolManager).loadByNameOrId(TEST_STRING);

        assertThat(toolApiService.loadTool("", TEST_STRING)).isEqualTo(tool);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadToolWithRegistryWhenPermissionIsGranted() {
        initAclEntity(tool, AclPermission.READ);
        doReturn(tool).when(mockToolManager).loadTool(TEST_STRING, TEST_STRING);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();
        final Tool returnedTool = toolApiService.loadTool(TEST_STRING, TEST_STRING);

        assertThat(returnedTool.getMask()).isEqualTo(READ_PERMISSION);
        assertThat(returnedTool).isEqualTo(tool);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadToolWithRegistryWhenPermissionIsNotGranted() {
        initAclEntity(tool);
        doReturn(tool).when(mockToolManager).loadTool(TEST_STRING, TEST_STRING);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThrows(AccessDeniedException.class, () -> toolApiService.loadTool(TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadToolWithoutRegistryWhenPermissionIsGranted() {
        initAclEntity(tool, AclPermission.READ);
        doReturn(tool).when(mockToolManager).loadTool("", TEST_STRING);
        doReturn(tool).when(mockToolManager).loadByNameOrId(TEST_STRING);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();
        final Tool returnedTool = toolApiService.loadTool("", TEST_STRING);

        assertThat(returnedTool.getMask()).isEqualTo(READ_PERMISSION);
        assertThat(returnedTool).isEqualTo(tool);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadToolWithoutRegistryWhenPermissionIsNotGranted() {
        initAclEntity(tool);
        doReturn(tool).when(mockToolManager).loadTool("", TEST_STRING);
        doReturn(tool).when(mockToolManager).loadByNameOrId(TEST_STRING);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThrows(AccessDeniedException.class, () -> toolApiService.loadTool("", TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadToolForExecutionForAdmin() {
        doReturn(tool).when(mockToolManager).loadByNameOrId(TEST_STRING);

        assertThat(toolApiService.loadToolForExecution(TEST_STRING)).isEqualTo(tool);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadToolForExecutionWhenPermissionIsGranted() {
        initAclEntity(tool, AclPermission.EXECUTE);
        doReturn(tool).when(mockToolManager).loadByNameOrId(TEST_STRING);

        final Tool returnedTool = toolApiService.loadToolForExecution(TEST_STRING);

        assertThat(returnedTool.getMask()).isEqualTo(EXECUTE_PERMISSION);
        assertThat(returnedTool).isEqualTo(tool);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadToolForExecutionWhenPermissionIsNotGranted() {
        initAclEntity(tool);
        doReturn(tool).when(mockToolManager).loadByNameOrId(TEST_STRING);

        assertThrows(AccessDeniedException.class, () -> toolApiService.loadToolForExecution(TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteToolForAdmin() {
        doReturn(tool).when(mockToolManager).delete(TEST_STRING, TEST_STRING, true);

        assertThat(toolApiService.delete(TEST_STRING, TEST_STRING, true)).isEqualTo(tool);
    }

    @Test
    @WithMockUser(SIMPLE_USER)
    public void shouldDeleteToolWhenPermissionIsGranted() {
        doReturn(tool).when(mockToolManager).delete(TEST_STRING, TEST_STRING, true);
        doReturn(tool).when(mockToolManager).loadTool(TEST_STRING, TEST_STRING);
        initAclEntity(tool, AclPermission.WRITE);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThat(toolApiService.delete(TEST_STRING, TEST_STRING, true)).isEqualTo(tool);
    }

    @Test
    @WithMockUser(SIMPLE_USER)
    public void shouldDenyDeleteToolWhenPermissionIsNotGranted() {
        doReturn(tool).when(mockToolManager).loadTool(TEST_STRING, TEST_STRING);
        initAclEntity(tool);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThrows(AccessDeniedException.class, () -> toolApiService.delete(TEST_STRING, TEST_STRING, true));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteToolVersionForAdmin() {
        doReturn(tool).when(mockToolManager).deleteToolVersion(TEST_STRING, TEST_STRING, TEST_STRING);

        assertThat(toolApiService.deleteToolVersion(TEST_STRING, TEST_STRING, TEST_STRING)).isEqualTo(tool);
    }

    @Test
    @WithMockUser(SIMPLE_USER)
    public void shouldDeleteToolVersionWhenPermissionIsGranted() {
        doReturn(tool).when(mockToolManager).deleteToolVersion(TEST_STRING, TEST_STRING, TEST_STRING);
        doReturn(tool).when(mockToolManager).loadTool(TEST_STRING, TEST_STRING);
        initAclEntity(tool, AclPermission.WRITE);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThat(toolApiService.deleteToolVersion(TEST_STRING, TEST_STRING, TEST_STRING)).isEqualTo(tool);
    }

    @Test
    @WithMockUser(SIMPLE_USER)
    public void shouldDenyDeleteToolVersionWhenPermissionIsNotGranted() {
        doReturn(tool).when(mockToolManager).loadTool(TEST_STRING, TEST_STRING);
        initAclEntity(tool);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThrows(AccessDeniedException.class,
            () -> toolApiService.deleteToolVersion(TEST_STRING, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadImageTagsForAdmin() {
        doReturn(TEST_STRING_LIST).when(mockToolManager).loadTags(ID);

        assertThat(toolApiService.loadImageTags(ID)).isEqualTo(TEST_STRING_LIST);
    }

    @Test
    @WithMockUser(SIMPLE_USER)
    public void shouldLoadImageTagsWhenPermissionIsGranted() {
        doReturn(TEST_STRING_LIST).when(mockToolManager).loadTags(ID);
        initAclEntity(tool, AclPermission.READ);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThat(toolApiService.loadImageTags(ID)).isEqualTo(TEST_STRING_LIST);
    }

    @Test
    @WithMockUser(SIMPLE_USER)
    public void shouldLoadImageTagsWhenPermissionIsNotGranted() {
        initAclEntity(tool);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThrows(AccessDeniedException.class, () -> toolApiService.loadImageTags(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetImageDescriptionForAdmin() {
        doReturn(imageDescription).when(mockToolManager).loadToolDescription(ID, TEST_STRING);

        assertThat(toolApiService.getImageDescription(ID, TEST_STRING)).isEqualTo(imageDescription);
    }

    @Test
    @WithMockUser(SIMPLE_USER)
    public void shouldLGetImageDescriptionWhenPermissionIsGranted() {
        doReturn(imageDescription).when(mockToolManager).loadToolDescription(ID, TEST_STRING);
        initAclEntity(tool, AclPermission.READ);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThat(toolApiService.getImageDescription(ID, TEST_STRING)).isEqualTo(imageDescription);
    }

    @Test
    @WithMockUser(SIMPLE_USER)
    public void shouldGetImageDescriptionWhenPermissionIsNotGranted() {
        initAclEntity(tool);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThrows(AccessDeniedException.class, () -> toolApiService.getImageDescription(ID, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetImageHistoryForAdmin() {
        doReturn(imageHistoryLayers).when(mockToolManager).loadToolHistory(ID, TEST_STRING);

        assertThat(toolApiService.getImageHistory(ID, TEST_STRING)).isEqualTo(imageHistoryLayers);
    }

    @Test
    @WithMockUser(SIMPLE_USER)
    public void shouldLGetImageHistoryWhenPermissionIsGranted() {
        doReturn(imageHistoryLayers).when(mockToolManager).loadToolHistory(ID, TEST_STRING);
        initAclEntity(tool, AclPermission.READ);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThat(toolApiService.getImageHistory(ID, TEST_STRING)).isEqualTo(imageHistoryLayers);
    }

    @Test
    @WithMockUser(SIMPLE_USER)
    public void shouldGetImageHistoryWhenPermissionIsNotGranted() {
        initAclEntity(tool);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThrows(AccessDeniedException.class, () -> toolApiService.getImageHistory(ID, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetImageDefaultCommandForAdmin() {
        doReturn(TEST_STRING).when(mockToolManager).loadToolDefaultCommand(ID, TEST_STRING);

        assertThat(toolApiService.getImageDefaultCommand(ID, TEST_STRING)).isEqualTo(TEST_STRING);
    }

    @Test
    @WithMockUser(SIMPLE_USER)
    public void shouldLGetImageDefaultCommandWhenPermissionIsGranted() {
        doReturn(TEST_STRING).when(mockToolManager).loadToolDefaultCommand(ID, TEST_STRING);
        initAclEntity(tool, AclPermission.READ);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThat(toolApiService.getImageDefaultCommand(ID, TEST_STRING)).isEqualTo(TEST_STRING);
    }

    @Test
    @WithMockUser(SIMPLE_USER)
    public void shouldGetImageDefaultCommandWhenPermissionIsNotGranted() {
        initAclEntity(tool);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThrows(AccessDeniedException.class, () -> toolApiService.getImageHistory(ID, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldForceScanToolForAdmin() {
        toolApiService.forceScanTool(TEST_STRING, TEST_STRING, TEST_STRING, true);

        verify(mockToolScanScheduler).forceScheduleScanTool(TEST_STRING, TEST_STRING, TEST_STRING, true);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyForceScanTool() {
        assertThrows(AccessDeniedException.class,
            () -> toolApiService.forceScanTool(TEST_STRING, TEST_STRING, TEST_STRING, true));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldClearToolScanForAdmin() {
        toolApiService.clearToolScan(TEST_STRING, TEST_STRING, TEST_STRING);

        verify(mockToolManager).clearToolScan(TEST_STRING, TEST_STRING, TEST_STRING);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyClearToolScan() {
        assertThrows(AccessDeniedException.class,
            () -> toolApiService.clearToolScan(TEST_STRING, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadToolScanResult() {
        doReturn(toolScanResult).when(mockToolManager).loadToolScanResult(TEST_STRING, TEST_STRING);

        assertThat(toolApiService.loadToolScanResult(TEST_STRING, TEST_STRING))
                .isEqualToComparingFieldByFieldRecursively(toolScanResultView);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadToolScanResultWhenPermissionIsGranted() {
        doReturn(tool).when(mockToolManager).loadTool(TEST_STRING, TEST_STRING);
        doReturn(toolScanResult).when(mockToolManager).loadToolScanResult(TEST_STRING, TEST_STRING);
        initAclEntity(tool, AclPermission.READ);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThat(toolApiService.loadToolScanResult(TEST_STRING, TEST_STRING))
                .isEqualToComparingFieldByFieldRecursively(toolScanResultView);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadToolScanResultWhenPermissionIsNotGranted() {
        doReturn(tool).when(mockToolManager).loadTool(TEST_STRING, TEST_STRING);
        doReturn(toolScanResult).when(mockToolManager).loadToolScanResult(TEST_STRING, TEST_STRING);
        initAclEntity(tool);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThrows(AccessDeniedException.class, () -> toolApiService.loadToolScanResult(TEST_STRING, TEST_STRING));
    }

    @Test
    public void shouldLoadSecurityPolicy() {
        doReturn(toolScanPolicy).when(mockToolScanManager).getPolicy();

        assertThat(toolApiService.loadSecurityPolicy()).isEqualTo(toolScanPolicy);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateToolIconForAdmin() {
        doReturn(ID).when(mockToolManager).updateToolIcon(ID, TEST_STRING, TEST_STRING.getBytes());

        assertThat(toolApiService.updateToolIcon(ID, TEST_STRING, TEST_STRING.getBytes())).isEqualTo(ID);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateToolIconWhenPermissionIsGranted() {
        doReturn(ID).when(mockToolManager).updateToolIcon(ID, TEST_STRING, TEST_STRING.getBytes());
        initAclEntity(tool, AclPermission.WRITE);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThat(toolApiService.updateToolIcon(ID, TEST_STRING, TEST_STRING.getBytes())).isEqualTo(ID);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyUpdateToolIconWhenPermissionIsNotGranted() {
        initAclEntity(tool);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThrows(AccessDeniedException.class,
            () -> toolApiService.updateToolIcon(ID, TEST_STRING, TEST_STRING.getBytes()));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteToolIconForAdmin() {
        toolApiService.deleteToolIcon(ID);

        verify(mockToolManager).deleteToolIcon(ID);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDeleteToolIconWhenPermissionIsGranted() {
        initAclEntity(tool, AclPermission.WRITE);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        toolApiService.deleteToolIcon(ID);
        verify(mockToolManager).deleteToolIcon(ID);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyDeleteToolIconWhenPermissionIsNotGranted() {
        initAclEntity(tool);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThrows(AccessDeniedException.class,
            () -> toolApiService.deleteToolIcon(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadToolIconForAdmin() {
        doReturn(pair).when(mockToolManager).loadToolIcon(ID);

        assertThat(toolApiService.loadToolIcon(ID)).isEqualTo(pair);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadToolIconWhenPermissionIsGranted() {
        doReturn(pair).when(mockToolManager).loadToolIcon(ID);
        initAclEntity(tool, AclPermission.READ);

        assertThat(toolApiService.loadToolIcon(ID)).isEqualTo(pair);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadToolIconWhenPermissionIsNotGranted() {
        initAclEntity(tool);

        assertThrows(AccessDeniedException.class, () -> toolApiService.loadToolIcon(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadToolAttributesForAdmin() {
        doReturn(toolDescription).when(mockToolManager).loadToolAttributes(ID);

        assertThat(toolApiService.loadToolAttributes(ID)).isEqualTo(toolDescription);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadToolAttributesWhenPermissionIsGranted() {
        doReturn(toolDescription).when(mockToolManager).loadToolAttributes(ID);
        initAclEntity(tool, AclPermission.READ);

        assertThat(toolApiService.loadToolAttributes(ID)).isEqualTo(toolDescription);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadToolAttributesWhenPermissionIsNotGranted() {
        initAclEntity(tool);

        assertThrows(AccessDeniedException.class, () -> toolApiService.loadToolAttributes(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateToolVersionSettingsForAdmin() {
        doReturn(toolVersion).when(mockToolVersionManager)
                .createToolVersionSettings(ID, TEST_STRING, CONFIG_LIST);

        assertThat(toolApiService.createToolVersionSettings(ID, TEST_STRING, CONFIG_LIST)).isEqualTo(toolVersion);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldCreateToolVersionSettingsWhenPermissionIsGranted() {
        doReturn(toolVersion).when(mockToolVersionManager)
                .createToolVersionSettings(ID, TEST_STRING, CONFIG_LIST);
        initAclEntity(tool, AclPermission.WRITE);

        assertThat(toolApiService.createToolVersionSettings(ID, TEST_STRING, CONFIG_LIST)).isEqualTo(toolVersion);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyCreateToolVersionSettingsWhenPermissionIsNotGranted() {
        initAclEntity(tool);

        assertThrows(AccessDeniedException.class,
            () -> toolApiService.createToolVersionSettings(ID, TEST_STRING, CONFIG_LIST));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadToolVersionSettingsForAdmin() {
        doReturn(toolVersionList).when(mockToolVersionManager).loadToolVersionSettings(ID, TEST_STRING);

        assertThat(toolApiService.loadToolVersionSettings(ID, TEST_STRING)).isEqualTo(toolVersionList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadToolVersionSettingsWhenPermissionIsGranted() {
        doReturn(toolVersionList).when(mockToolVersionManager).loadToolVersionSettings(ID, TEST_STRING);

        initAclEntity(tool, AclPermission.READ);

        assertThat(toolApiService.loadToolVersionSettings(ID, TEST_STRING)).isEqualTo(toolVersionList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadToolVersionSettingsWhenPermissionIsNotGranted() {
        initAclEntity(tool);

        assertThrows(AccessDeniedException.class,
            () -> toolApiService.loadToolVersionSettings(ID, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldSymlinkForAdmin() {
        doReturn(tool).when(mockToolManager).symlink(toolSymlinkRequest);

        assertThat(toolApiService.symlink(toolSymlinkRequest)).isEqualTo(tool);
    }

    @Test
    @WithMockUser(roles = TOOL_GROUP_MANAGER, username = SIMPLE_USER)
    public void shouldSymlinkForManagerWhenPermissionIsGranted() {
        doReturn(tool).when(mockToolManager).symlink(toolSymlinkRequest);
        doReturn(toolGroup).when(mockToolGroupManager).loadByNameOrId(String.valueOf(ID));
        initAclEntity(tool, AclPermission.READ);
        initAclEntity(toolGroup, AclPermission.WRITE);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThat(toolApiService.symlink(toolSymlinkRequest)).isEqualTo(tool);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenySymlinkWhenInvalidRole() {
        doReturn(tool).when(mockToolManager).symlink(toolSymlinkRequest);
        doReturn(toolGroup).when(mockToolGroupManager).loadByNameOrId(String.valueOf(ID));
        initAclEntity(tool, AclPermission.READ);
        initAclEntity(toolGroup, AclPermission.WRITE);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThrows(AccessDeniedException.class, () -> toolApiService.symlink(toolSymlinkRequest));
    }

    @Test
    @WithMockUser(roles = TOOL_GROUP_MANAGER, username = SIMPLE_USER)
    public void shouldDenySymlinkForManagerWhenToolPermissionIsNotGranted() {
        doReturn(tool).when(mockToolManager).symlink(toolSymlinkRequest);
        doReturn(toolGroup).when(mockToolGroupManager).loadByNameOrId(String.valueOf(ID));
        initAclEntity(tool);
        initAclEntity(toolGroup, AclPermission.WRITE);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThrows(AccessDeniedException.class, () -> toolApiService.symlink(toolSymlinkRequest));
    }

    @Test
    @WithMockUser(roles = TOOL_GROUP_MANAGER, username = SIMPLE_USER)
    public void shouldDenySymlinkForManagerWhenToolGroupPermissionIsNotGranted() {
        doReturn(tool).when(mockToolManager).symlink(toolSymlinkRequest);
        doReturn(toolGroup).when(mockToolGroupManager).loadByNameOrId(String.valueOf(ID));
        initAclEntity(tool, AclPermission.READ);
        initAclEntity(toolGroup);

        doReturn(SecurityContextHolder.getContext().getAuthentication()).when(mockAuthManager).getAuthentication();

        assertThrows(AccessDeniedException.class, () -> toolApiService.symlink(toolSymlinkRequest));
    }
}
