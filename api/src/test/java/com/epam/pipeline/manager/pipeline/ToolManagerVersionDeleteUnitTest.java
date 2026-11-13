/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.tool.ToolDao;
import com.epam.pipeline.dao.tool.ToolVulnerabilityDao;
import com.epam.pipeline.entity.docker.ManifestV2;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.exception.docker.DockerConnectionException;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Optional;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ToolManagerVersionDeleteUnitTest {

    private static final String REGISTRY_PATH = "registry:443";
    private static final String IMAGE = "library/image";
    private static final String VERSION = "1.0";
    private static final String DIGEST = "sha256:8d2c8e0aa6bd6ae1c8e0fd93b1a6e2d9d0d5c8f9";
    private static final String ERROR_MESSAGE = "Registry is not available";
    private static final long TOOL_ID = 1L;
    private static final long REGISTRY_ID = 2L;

    @Mock
    private ToolDao toolDao;

    @Mock
    private ToolVulnerabilityDao toolVulnerabilityDao;

    @Mock
    private MessageHelper messageHelper;

    @Mock
    private DockerRegistryManager dockerRegistryManager;

    @Mock
    private ToolVersionManager toolVersionManager;

    @InjectMocks
    private ToolManager toolManager;

    private final DockerRegistry registry = buildRegistry();
    private final Tool tool = buildTool();

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        doReturn(registry).when(dockerRegistryManager).loadByNameOrId(REGISTRY_PATH);
        doReturn(registry).when(dockerRegistryManager).load(REGISTRY_ID);
        doReturn(tool).when(toolDao).loadTool(REGISTRY_ID, IMAGE);
        doReturn(Optional.of(manifest())).when(dockerRegistryManager).deleteImage(registry, IMAGE, VERSION);
    }

    @Test
    public void shouldDeleteVersionFromRegistryBeforeItIsDeletedFromDatabase() {
        doReturn(Collections.emptyList()).when(dockerRegistryManager).findImageTags(registry, IMAGE);

        toolManager.deleteToolVersion(REGISTRY_PATH, IMAGE, VERSION);

        final InOrder inOrder = Mockito.inOrder(dockerRegistryManager, toolVulnerabilityDao, toolVersionManager);
        inOrder.verify(dockerRegistryManager).deleteImage(registry, IMAGE, VERSION);
        inOrder.verify(toolVulnerabilityDao).deleteToolVersionScan(TOOL_ID, VERSION);
        inOrder.verify(toolVersionManager).deleteToolVersion(TOOL_ID, VERSION);
        verify(dockerRegistryManager, never()).untagImage(any(DockerRegistry.class), anyString(), anyString());
    }

    @Test
    public void shouldNotDeleteVersionFromDatabaseIfRegistryDeletionFails() {
        doThrow(new DockerConnectionException(REGISTRY_PATH, ERROR_MESSAGE))
                .when(dockerRegistryManager).deleteImage(registry, IMAGE, VERSION);

        assertThrows(DockerConnectionException.class,
            () -> toolManager.deleteToolVersion(REGISTRY_PATH, IMAGE, VERSION));

        verifyVersionIsKeptInDatabase();
    }

    @Test
    public void shouldRemoveTagLeftBehindAfterManifestDeletion() {
        // a registry may delete a manifest and fail to remove a tag pointing to it afterwards
        doReturn(Collections.singletonList(VERSION)).when(dockerRegistryManager).findImageTags(registry, IMAGE);
        doReturn(true).when(dockerRegistryManager).untagImage(registry, IMAGE, VERSION);

        toolManager.deleteToolVersion(REGISTRY_PATH, IMAGE, VERSION);

        verify(dockerRegistryManager).deleteImage(registry, IMAGE, VERSION);
        verify(dockerRegistryManager).untagImage(registry, IMAGE, VERSION);
        verify(toolVersionManager).deleteToolVersion(TOOL_ID, VERSION);
    }

    @Test
    public void shouldTrustTagRemovalRatherThanRepeatedTagListing() {
        // a tag listing may lag behind the deletion, hence it shall not be used to verify a removed tag:
        // the registry responses of the removal itself are the only reliable outcome of the operation
        doReturn(Collections.singletonList(VERSION)).when(dockerRegistryManager).findImageTags(registry, IMAGE);
        doReturn(true).when(dockerRegistryManager).untagImage(registry, IMAGE, VERSION);

        toolManager.deleteToolVersion(REGISTRY_PATH, IMAGE, VERSION);

        verify(dockerRegistryManager, times(1)).findImageTags(registry, IMAGE);
        verify(toolVersionManager).deleteToolVersion(TOOL_ID, VERSION);
    }

    @Test
    public void shouldRemoveDanglingTagIfItsManifestCannotBeResolved() {
        doReturn(Optional.empty()).when(dockerRegistryManager).deleteImage(registry, IMAGE, VERSION);
        doReturn(Collections.singletonList(VERSION)).when(dockerRegistryManager).findImageTags(registry, IMAGE);
        doReturn(true).when(dockerRegistryManager).untagImage(registry, IMAGE, VERSION);

        toolManager.deleteToolVersion(REGISTRY_PATH, IMAGE, VERSION);

        verify(dockerRegistryManager).untagImage(registry, IMAGE, VERSION);
        verify(toolVersionManager).deleteToolVersion(TOOL_ID, VERSION);
    }

    @Test
    public void shouldNotDeleteVersionFromDatabaseIfDanglingTagCannotBeRemoved() {
        doReturn(Optional.empty()).when(dockerRegistryManager).deleteImage(registry, IMAGE, VERSION);
        doReturn(Collections.singletonList(VERSION)).when(dockerRegistryManager).findImageTags(registry, IMAGE);
        doReturn(false).when(dockerRegistryManager).untagImage(registry, IMAGE, VERSION);

        assertThrows(IllegalArgumentException.class,
            () -> toolManager.deleteToolVersion(REGISTRY_PATH, IMAGE, VERSION));

        verify(dockerRegistryManager).untagImage(registry, IMAGE, VERSION);
        verify(messageHelper).getMessage(MessageConstants.ERROR_TOOL_VERSION_DELETE_FAILED, VERSION, IMAGE);
        verifyVersionIsKeptInDatabase();
    }

    @Test
    public void shouldNotDeleteBlobsOfManifestListDuringHardToolDeletion() {
        // a manifest list references no blobs itself, its image manifests have them
        doReturn(Collections.singletonList(VERSION)).when(dockerRegistryManager).loadImageTags(registry, IMAGE);
        doReturn(Optional.of(manifestList())).when(dockerRegistryManager).deleteImage(registry, IMAGE, VERSION);

        toolManager.delete(REGISTRY_PATH, IMAGE, true);

        verify(dockerRegistryManager).deleteImage(registry, IMAGE, VERSION);
        verify(dockerRegistryManager, never()).deleteLayer(any(DockerRegistry.class), anyString(), anyString());
    }

    private void verifyVersionIsKeptInDatabase() {
        verify(toolVulnerabilityDao, never()).deleteToolVersionScan(anyLong(), anyString());
        verify(toolVersionManager, never()).deleteToolVersion(anyLong(), anyString());
    }

    private DockerRegistry buildRegistry() {
        final DockerRegistry dockerRegistry = new DockerRegistry();
        dockerRegistry.setId(REGISTRY_ID);
        dockerRegistry.setPath(REGISTRY_PATH);
        return dockerRegistry;
    }

    private Tool buildTool() {
        final Tool result = new Tool();
        result.setId(TOOL_ID);
        result.setImage(IMAGE);
        result.setRegistry(REGISTRY_PATH);
        result.setRegistryId(REGISTRY_ID);
        return result;
    }

    private ManifestV2 manifest() {
        final ManifestV2 manifest = new ManifestV2();
        manifest.setDigest(DIGEST);
        manifest.setConfig(new ManifestV2.Config(DIGEST, 0L));
        manifest.setLayers(Collections.singletonList(new ManifestV2.Config(DIGEST, 0L)));
        return manifest;
    }

    private ManifestV2 manifestList() {
        final ManifestV2 manifest = new ManifestV2();
        manifest.setDigest(DIGEST);
        manifest.setManifests(Collections.singletonList(
                new ManifestV2.ManifestReference(DIGEST, 0L, null, new ManifestV2.Platform("amd64", "linux"))));
        return manifest;
    }
}
