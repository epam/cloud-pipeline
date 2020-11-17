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

import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.scan.ToolScanPolicy;
import com.epam.pipeline.entity.scan.ToolVersionScanResult;
import com.epam.pipeline.manager.docker.scan.ToolScanManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.docker.ToolCreatorUtils;
import com.epam.pipeline.test.creator.docker.ToolGroupCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.nio.file.AccessDeniedException;
import java.util.Collections;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.assertj.core.api.Assertions.assertThat;

public class ToolApiServiceTest extends AbstractAclTest {

    @Autowired
    private ToolApiService toolApiService;

    @Autowired
    private ToolManager mockToolManager;

    @Autowired
    private ToolScanManager mockToolScanManager;

    @Autowired
    private AuthManager mockAuthManager;

    private final Tool tool = ToolCreatorUtils.getTool();

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateToolForAdmin() {
        doReturn(tool).when(mockToolManager).create(tool, true);

        assertThat(toolApiService.create(tool)).isEqualTo(tool);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldCreateToolWhenPermissionIsGranted() {
        final Tool tool = ToolCreatorUtils.getTool(SIMPLE_USER);
        initAclEntity(tool, AclPermission.WRITE);
        doReturn(tool).when(mockToolManager).create(tool, true);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();

        assertThat(toolApiService.create(tool)).isEqualTo(tool);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateToolWhenPermissionIsNotGranted() {
        final Tool tool = ToolCreatorUtils.getTool(SIMPLE_USER);
        final ToolGroup toolGroup = ToolGroupCreatorUtils.getToolGroup(ANOTHER_SIMPLE_USER);
        toolGroup.setTools(Collections.singletonList(tool));
        initAclEntity(toolGroup);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        doReturn(toolGroup.getTools().get(0)).when(mockToolManager).create(toolGroup.getTools().get(0), true);

        assertThrows(AccessDeniedException.class, () -> mockToolManager.create(tool, true));
    }

    @Test
    public void updateTool() {
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void updateWhiteListWithToolVersion() {
        final ToolVersionScanResult toolVersionScanResult = new ToolVersionScanResult();
        doReturn(toolVersionScanResult).when(mockToolManager).updateWhiteListWithToolVersionStatus(ID, TEST_STRING, true);

        assertThat(toolApiService.updateWhiteListWithToolVersion(ID, TEST_STRING, true)).isEqualTo(toolVersionScanResult);
    }

    @Test
    public void loadTool() {
    }

    @Test
    public void loadToolForExecution() {
    }

    @Test
    public void delete() {
    }

    @Test
    public void deleteToolVersion() {
    }

    @Test
    public void loadImageTags() {
    }

    @Test
    public void getImageDescription() {
    }

    @Test
    public void getImageHistory() {
    }

    @Test
    public void getImageDefaultCommand() {
    }

    @Test
    public void forceScanTool() {
    }

    @Test
    public void clearToolScan() {
    }

    @Test
    public void loadToolScanResult() {
    }

    @Test
    public void loadSecurityPolicy() {
        final ToolScanPolicy toolScanPolicy = new ToolScanPolicy();
        doReturn(toolScanPolicy).when(mockToolScanManager).getPolicy();

        assertThat(toolApiService.loadSecurityPolicy()).isEqualTo(toolScanPolicy);
    }

    @Test
    public void updateToolIcon() {
    }

    @Test
    public void deleteToolIcon() {
    }

    @Test
    public void loadToolIcon() {
    }

    @Test
    public void loadToolAttributes() {
    }

    @Test
    public void createToolVersionSettings() {
    }

    @Test
    public void loadToolVersionSettings() {
    }

    @Test
    public void symlink() {
    }
}
