/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.tool.ToolDao;
import com.epam.pipeline.dao.tool.ToolVulnerabilityDao;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.pipeline.ToolScanStatus;
import com.epam.pipeline.entity.scan.ToolVersionScanResult;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.docker.DockerClient;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.test.creator.CommonCreatorConstants;
import com.epam.pipeline.test.creator.docker.DockerCreatorUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.hamcrest.MatcherAssert.assertThat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ToolManagerUnitTest {

    private static final String COLON = ":";
    private static final String SLASH = "/";
    private static final Long REGISTRY_ID = CommonCreatorConstants.ID;
    private static final String REGISTRY = "registry:443";
    private static final Long TOOL_ID = CommonCreatorConstants.ID;
    private static final String TOOL_IMAGE = "library/image";
    private static final Long SYMLINK_ID = CommonCreatorConstants.ID_2;
    private static final String SYMLINK_IMAGE = "personal/symlink";
    private static final String LATEST_TAG = "latest";
    private static final String SOME_TAG = "tag";
    private static final Long GROUP_ID = CommonCreatorConstants.ID;
    private static final String TEST_DIGEST = "sha256:abc123";
    private static final Long DOES_NOT_EXIST_ID = 999L;

    private final ToolManager manager = new ToolManager();
    private final ToolDao toolDao = mock(ToolDao.class);
    private final DockerRegistryManager dockerRegistryManager = mock(DockerRegistryManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final ToolGroupManager toolGroupManager = mock(ToolGroupManager.class);
    private final AuthManager authManager = mock(AuthManager.class);
    private final InstanceOfferManager instanceOfferManager = mock(InstanceOfferManager.class);
    private final ToolVersionManager toolVersionManager = mock(ToolVersionManager.class);
    private final ToolVulnerabilityDao toolVulnerabilityDao = mock(ToolVulnerabilityDao.class);

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(manager, "toolDao", toolDao);
        ReflectionTestUtils.setField(manager, "dockerRegistryManager", dockerRegistryManager);
        ReflectionTestUtils.setField(manager, "messageHelper", messageHelper);
        ReflectionTestUtils.setField(manager, "toolGroupManager", toolGroupManager);
        ReflectionTestUtils.setField(manager, "authManager", authManager);
        ReflectionTestUtils.setField(manager, "instanceOfferManager", instanceOfferManager);
        ReflectionTestUtils.setField(manager, "toolVersionManager", toolVersionManager);
        ReflectionTestUtils.setField(manager, "toolVulnerabilityDao", toolVulnerabilityDao);
    }

    @Test
    public void resolveSymlinksShouldReturnToolWithFullDockerImageWithLatestTag() {
        mockTool(getTool());
        mockRegistry(getRegistry());

        final Tool resolvedTool = manager.resolveSymlinks(REGISTRY + SLASH + TOOL_IMAGE + COLON + LATEST_TAG);
        assertThat(resolvedTool.getImage(), is(REGISTRY + SLASH + TOOL_IMAGE + COLON + LATEST_TAG));
    }

    @Test
    public void resolveSymlinksShouldReturnToolWithFullDockerImageWithSomeTag() {
        mockRegistry(getRegistry());
        mockTool(getTool());

        final Tool resolvedTool = manager.resolveSymlinks(REGISTRY + SLASH + TOOL_IMAGE + COLON + SOME_TAG);
        assertThat(resolvedTool.getImage(), is(REGISTRY + SLASH + TOOL_IMAGE + COLON + SOME_TAG));
    }

    @Test
    public void resolveSymlinksShouldReturnToolWithFullDockerImageWithLatestTagByDefault() {
        mockTool(getTool());
        mockRegistry(getRegistry());

        final Tool resolvedTool = manager.resolveSymlinks(REGISTRY + SLASH + TOOL_IMAGE);
        assertThat(resolvedTool.getImage(), is(REGISTRY + SLASH + TOOL_IMAGE + COLON + LATEST_TAG));
    }

    @Test
    public void resolveSymlinksShouldReturnSymlinkedToolWithFullDockerImageWithLatestTag() {
        mockRegistry(getRegistry());
        mockTool(getTool());
        mockTool(getSymlink());

        final Tool resolvedTool = manager.resolveSymlinks(REGISTRY + SLASH + SYMLINK_IMAGE + COLON + LATEST_TAG);
        assertThat(resolvedTool.getImage(), is(REGISTRY + SLASH + TOOL_IMAGE + COLON + LATEST_TAG));
    }

    @Test
    public void resolveSymlinksShouldReturnSymlinkedToolWithFullDockerImageWithSomeTag() {
        mockRegistry(getRegistry());
        mockTool(getTool());
        mockTool(getSymlink());

        final Tool resolvedTool = manager.resolveSymlinks(REGISTRY + SLASH + SYMLINK_IMAGE + COLON + SOME_TAG);
        assertThat(resolvedTool.getImage(), is(REGISTRY + SLASH + TOOL_IMAGE + COLON + SOME_TAG));
    }

    @Test
    public void resolveSymlinksShouldReturnSymlinkedToolWithFullDockerImageWithLatestTagByDefault() {
        mockRegistry(getRegistry());
        mockTool(getTool());
        mockTool(getSymlink());

        final Tool resolvedTool = manager.resolveSymlinks(REGISTRY + SLASH + SYMLINK_IMAGE);
        assertThat(resolvedTool.getImage(), is(REGISTRY + SLASH + TOOL_IMAGE + COLON + LATEST_TAG));
    }

    @Test
    public void createShouldRegisterToolVersionForEachTag() {
        final DockerRegistry registry = getRegistry();
        final ToolGroup group = getToolGroup();
        final Tool tool = createToolForCreate();
        final DockerClient dockerClient = mock(DockerClient.class);
        final ToolVersion toolVersion = ToolVersion.builder().digest(TEST_DIGEST).version(LATEST_TAG).build();

        doReturn(group).when(toolGroupManager).load(GROUP_ID);
        doReturn(Optional.empty()).when(toolDao).loadToolByGroupAndImage(eq(GROUP_ID), anyString());
        doReturn(true).when(instanceOfferManager).isToolInstanceAllowedInAnyRegion(any(), any());
        doReturn(registry).when(dockerRegistryManager).load(REGISTRY_ID);
        doReturn(Collections.singletonList(LATEST_TAG)).when(dockerRegistryManager)
                .loadImageTags(registry, TOOL_IMAGE);
        doReturn(dockerClient).when(dockerRegistryManager).getDockerClient(registry, TOOL_IMAGE);
        doReturn(toolVersion).when(dockerClient).getVersionAttributes(registry, TOOL_IMAGE, LATEST_TAG);
        doReturn(tool).when(toolDao).loadTool(TOOL_ID);
        doReturn(Optional.empty()).when(toolVulnerabilityDao).loadToolVersionScan(eq(TOOL_ID), anyString());

        manager.create(tool, false);

        verify(toolVersionManager).updateOrCreateToolVersion(TOOL_ID, LATEST_TAG, TOOL_IMAGE, registry, dockerClient);
    }

    @Test
    public void createShouldRegisterToolVersionForMultipleTags() {
        final DockerRegistry registry = getRegistry();
        final ToolGroup group = getToolGroup();
        final Tool tool = createToolForCreate();
        final DockerClient dockerClient = mock(DockerClient.class);
        final ToolVersion latestVersion = ToolVersion.builder().digest(TEST_DIGEST).version(LATEST_TAG).build();
        final ToolVersion someVersion = ToolVersion.builder().digest(TEST_DIGEST).version(SOME_TAG).build();

        doReturn(group).when(toolGroupManager).load(GROUP_ID);
        doReturn(Optional.empty()).when(toolDao).loadToolByGroupAndImage(eq(GROUP_ID), anyString());
        doReturn(true).when(instanceOfferManager).isToolInstanceAllowedInAnyRegion(any(), any());
        doReturn(registry).when(dockerRegistryManager).load(REGISTRY_ID);
        doReturn(Arrays.asList(LATEST_TAG, SOME_TAG)).when(dockerRegistryManager)
                .loadImageTags(registry, TOOL_IMAGE);
        doReturn(dockerClient).when(dockerRegistryManager).getDockerClient(registry, TOOL_IMAGE);
        doReturn(latestVersion).when(dockerClient).getVersionAttributes(registry, TOOL_IMAGE, LATEST_TAG);
        doReturn(someVersion).when(dockerClient).getVersionAttributes(registry, TOOL_IMAGE, SOME_TAG);
        doReturn(tool).when(toolDao).loadTool(TOOL_ID);
        doReturn(Optional.empty()).when(toolVulnerabilityDao).loadToolVersionScan(eq(TOOL_ID), anyString());

        manager.create(tool, false);

        verify(toolVersionManager).updateOrCreateToolVersion(TOOL_ID, LATEST_TAG, TOOL_IMAGE, registry, dockerClient);
        verify(toolVersionManager).updateOrCreateToolVersion(TOOL_ID, SOME_TAG, TOOL_IMAGE, registry, dockerClient);
        verify(toolVersionManager, times(2))
                .updateOrCreateToolVersion(anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    public void shouldEnableBlackListWithToolVersion() {
        final Tool tool = getTool();
        mockTool(tool);
        doReturn(Optional.empty()).when(toolVulnerabilityDao).loadToolVersionScan(TOOL_ID, LATEST_TAG);

        manager.updateBlackListWithToolVersionStatus(TOOL_ID, LATEST_TAG, true);

        verify(toolVulnerabilityDao).insertToolVersionScan(eq(TOOL_ID), eq(LATEST_TAG), eq(null), eq(null),
                eq(null), eq(ToolScanStatus.NOT_SCANNED), any(Date.class), any(Map.class), eq(null), eq(null),
                eq(false));
        verify(toolVulnerabilityDao).updateWhiteAndBlackListWithToolVersion(TOOL_ID, LATEST_TAG, false, true);
    }

    @Test
    public void shouldUpdateWhiteListWithToolVersion() {
        final Tool tool = getTool();
        mockTool(tool);
        doReturn(Optional.empty()).when(toolVulnerabilityDao).loadToolVersionScan(TOOL_ID, LATEST_TAG);

        manager.updateWhiteListWithToolVersionStatus(TOOL_ID, LATEST_TAG, true);

        verify(toolVulnerabilityDao).insertToolVersionScan(eq(TOOL_ID), eq(LATEST_TAG), eq(null), eq(null),
                eq(null), eq(ToolScanStatus.NOT_SCANNED), any(Date.class), any(Map.class), eq(null), eq(null),
                eq(false));
        verify(toolVulnerabilityDao).updateWhiteAndBlackListWithToolVersion(TOOL_ID, LATEST_TAG, true, false);
    }

    @Test
    public void shouldDisableBlackListWithToolVersion() {
        final Tool tool = getTool();
        final ToolVersionScanResult scanResult = new ToolVersionScanResult(LATEST_TAG);
        scanResult.setFromBlackList(true);
        mockTool(tool);
        doReturn(Optional.of(scanResult)).when(toolVulnerabilityDao).loadToolVersionScan(TOOL_ID, LATEST_TAG);

        manager.updateBlackListWithToolVersionStatus(TOOL_ID, LATEST_TAG, false);

        verify(toolVulnerabilityDao).updateWhiteAndBlackListWithToolVersion(TOOL_ID, LATEST_TAG, false, false);
    }

    @Test
    public void shouldDisableWhiteListForToolVersion() {
        final Tool tool = getTool();
        final ToolVersionScanResult scanResult = new ToolVersionScanResult(LATEST_TAG);
        scanResult.setFromWhiteList(true);
        mockTool(tool);
        doReturn(Optional.of(scanResult)).when(toolVulnerabilityDao).loadToolVersionScan(TOOL_ID, LATEST_TAG);

        manager.updateWhiteListWithToolVersionStatus(TOOL_ID, LATEST_TAG, false);

        verify(toolVulnerabilityDao).updateWhiteAndBlackListWithToolVersion(TOOL_ID, LATEST_TAG, false, false);
    }

    @Test
    public void shouldDisableWhiteListWhenEnablingBlackList() {
        final Tool tool = getTool();
        final ToolVersionScanResult scanResult = new ToolVersionScanResult(LATEST_TAG);
        scanResult.setFromWhiteList(true);
        scanResult.setFromBlackList(false);
        mockTool(tool);
        doReturn(Optional.of(scanResult)).when(toolVulnerabilityDao).loadToolVersionScan(TOOL_ID, LATEST_TAG);

        manager.updateBlackListWithToolVersionStatus(TOOL_ID, LATEST_TAG, true);

        verify(toolVulnerabilityDao).updateWhiteAndBlackListWithToolVersion(TOOL_ID, LATEST_TAG, false, true);
    }

    @Test
    public void shouldDisableBlackListWhenEnablingWhiteList() {
        final Tool tool = getTool();
        final ToolVersionScanResult scanResult = new ToolVersionScanResult(LATEST_TAG);
        scanResult.setFromWhiteList(false);
        scanResult.setFromBlackList(true);
        mockTool(tool);
        doReturn(Optional.of(scanResult)).when(toolVulnerabilityDao).loadToolVersionScan(TOOL_ID, LATEST_TAG);

        manager.updateWhiteListWithToolVersionStatus(TOOL_ID, LATEST_TAG, true);

        verify(toolVulnerabilityDao).updateWhiteAndBlackListWithToolVersion(TOOL_ID, LATEST_TAG, true, false);
    }

    @Test
    public void shouldPreserveWhiteListWhenDisablingBlackList() {
        final Tool tool = getTool();
        final ToolVersionScanResult scanResult = new ToolVersionScanResult(LATEST_TAG);
        scanResult.setFromWhiteList(true);
        scanResult.setFromBlackList(true);
        mockTool(tool);
        doReturn(Optional.of(scanResult)).when(toolVulnerabilityDao).loadToolVersionScan(TOOL_ID, LATEST_TAG);

        manager.updateBlackListWithToolVersionStatus(TOOL_ID, LATEST_TAG, false);

        verify(toolVulnerabilityDao).updateWhiteAndBlackListWithToolVersion(TOOL_ID, LATEST_TAG, true, false);
    }

    @Test
    public void shouldPreserveBlackListWhenDisablingWhiteList() {
        final Tool tool = getTool();
        final ToolVersionScanResult scanResult = new ToolVersionScanResult(LATEST_TAG);
        scanResult.setFromWhiteList(true);
        scanResult.setFromBlackList(true);
        mockTool(tool);
        doReturn(Optional.of(scanResult)).when(toolVulnerabilityDao).loadToolVersionScan(TOOL_ID, LATEST_TAG);

        manager.updateWhiteListWithToolVersionStatus(TOOL_ID, LATEST_TAG, false);

        verify(toolVulnerabilityDao).updateWhiteAndBlackListWithToolVersion(TOOL_ID, LATEST_TAG, false, true);
    }

    @Test
    public void shouldCreateToolVersionScanWhenEnablingBlackListForNonExistentScan() {
        final Tool tool = getTool();
        mockTool(tool);
        doReturn(Optional.empty()).when(toolVulnerabilityDao).loadToolVersionScan(TOOL_ID, LATEST_TAG);

        manager.updateBlackListWithToolVersionStatus(TOOL_ID, LATEST_TAG, true);

        verify(toolVulnerabilityDao).insertToolVersionScan(eq(TOOL_ID), eq(LATEST_TAG), eq(null), eq(null),
                eq(null), eq(ToolScanStatus.NOT_SCANNED), any(Date.class), any(Map.class), eq(null), eq(null),
                eq(false));
        verify(toolVulnerabilityDao).updateWhiteAndBlackListWithToolVersion(TOOL_ID, LATEST_TAG, false, true);
    }

    @Test
    public void shouldCreateToolVersionScanWhenEnablingWhiteListForNonExistentScan() {
        final Tool tool = getTool();
        mockTool(tool);
        doReturn(Optional.empty()).when(toolVulnerabilityDao).loadToolVersionScan(TOOL_ID, LATEST_TAG);

        manager.updateWhiteListWithToolVersionStatus(TOOL_ID, LATEST_TAG, true);

        verify(toolVulnerabilityDao).insertToolVersionScan(eq(TOOL_ID), eq(LATEST_TAG), eq(null), eq(null),
                eq(null), eq(ToolScanStatus.NOT_SCANNED), any(Date.class), any(Map.class), eq(null), eq(null),
                eq(false));
        verify(toolVulnerabilityDao).updateWhiteAndBlackListWithToolVersion(TOOL_ID, LATEST_TAG, true, false);
    }

    @Test
    public void shouldFailBlackListUpdateForSymlinkTool() {
        final Tool symlink = getSymlink();
        mockTool(symlink);

        assertThrows(IllegalArgumentException.class, () ->
                manager.updateBlackListWithToolVersionStatus(SYMLINK_ID, LATEST_TAG, true));
    }

    @Test
    public void shouldFailUpdateWhiteListWhenToolIsSymlink() {
        final Tool symlink = getSymlink();
        mockTool(symlink);

        assertThrows(IllegalArgumentException.class, () ->
                manager.updateWhiteListWithToolVersionStatus(SYMLINK_ID, LATEST_TAG, true));
    }

    @Test
    public void shouldFailBlackListUpdateForNonExistentTool() {
        doReturn(null).when(toolDao).loadTool(DOES_NOT_EXIST_ID);

        assertThrows(IllegalArgumentException.class, () ->
                manager.updateBlackListWithToolVersionStatus(DOES_NOT_EXIST_ID, LATEST_TAG, true));
    }

    @Test
    public void shouldFailUpdateWhiteListWhenToolDoesNotExist() {
        doReturn(null).when(toolDao).loadTool(DOES_NOT_EXIST_ID);

        assertThrows(IllegalArgumentException.class, () ->
                manager.updateWhiteListWithToolVersionStatus(DOES_NOT_EXIST_ID, LATEST_TAG, true));
    }

    private Tool createToolForCreate() {
        final Tool tool = new Tool();
        tool.setId(TOOL_ID);
        tool.setImage(TOOL_IMAGE);
        tool.setCpu(CommonCreatorConstants.TEST_STRING);
        tool.setRam(CommonCreatorConstants.TEST_STRING);
        tool.setToolGroupId(GROUP_ID);
        tool.setOwner("test_user");
        return tool;
    }

    private ToolGroup getToolGroup() {
        final ToolGroup group = new ToolGroup();
        group.setId(GROUP_ID);
        group.setRegistryId(REGISTRY_ID);
        group.setName("library");
        return group;
    }

    private Tool getTool() {
        final Tool tool = DockerCreatorUtils.getTool();
        tool.setId(TOOL_ID);
        tool.setImage(TOOL_IMAGE);
        tool.setRegistry(REGISTRY);
        tool.setRegistryId(REGISTRY_ID);
        return tool;
    }

    private Tool getSymlink() {
        final Tool symlink = DockerCreatorUtils.getTool();
        symlink.setId(SYMLINK_ID);
        symlink.setImage(SYMLINK_IMAGE);
        symlink.setRegistry(REGISTRY);
        symlink.setRegistryId(REGISTRY_ID);
        symlink.setLink(TOOL_ID);
        return symlink;
    }

    private DockerRegistry getRegistry() {
        final DockerRegistry registry = DockerCreatorUtils.getDockerRegistry();
        registry.setId(REGISTRY_ID);
        return registry;
    }

    private void mockTool(final Tool tool) {
        doReturn(tool).when(toolDao).loadTool(tool.getRegistryId(), tool.getImage());
        doReturn(tool).when(toolDao).loadTool(tool.getId());
    }

    private void mockRegistry(final DockerRegistry registry) {
        doReturn(registry).when(dockerRegistryManager).loadByNameOrId(REGISTRY);
    }
}
