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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.folder;

import com.epam.pipeline.elasticsearchagent.exception.EntityNotFoundException;
import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.vo.EntityPermissionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

import static com.epam.pipeline.elasticsearchagent.LoaderVerificationUtils.verifyMetadata;
import static com.epam.pipeline.elasticsearchagent.LoaderVerificationUtils.verifyPermissions;
import static com.epam.pipeline.elasticsearchagent.LoaderVerificationUtils.verifyPipelineUser;
import static com.epam.pipeline.elasticsearchagent.ObjectCreationUtils.buildEntityPermissionVO;
import static com.epam.pipeline.elasticsearchagent.ObjectCreationUtils.buildMetadataEntry;
import static com.epam.pipeline.elasticsearchagent.TestConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"PMD.TooManyStaticImports"})
class FolderLoaderTest {

    @Mock
    private CloudPipelineAPIClient apiClient;

    @BeforeEach
    void setup() {
        EntityPermissionVO entityPermissionVO =
                buildEntityPermissionVO(USER_NAME, ALLOWED_USERS, DENIED_USERS, ALLOWED_GROUPS, DENIED_GROUPS);

        MetadataEntry metadataEntry =
                buildMetadataEntry(AclClass.FOLDER, 1L, TEST_KEY + " " + TEST_VALUE);

        when(apiClient.loadUserByName(anyString())).thenReturn(USER);
        when(apiClient.loadPermissionsForEntity(anyLong(), any())).thenReturn(entityPermissionVO);
        when(apiClient.loadMetadataEntry(any())).thenReturn(Collections.singletonList(metadataEntry));
    }

    @Test
    void shouldLoadFolder() throws EntityNotFoundException {
        Folder expectedFolder = new Folder(1L);
        expectedFolder.setName(TEST_NAME);
        expectedFolder.setParentId(1L);
        expectedFolder.setOwner(TEST_NAME);
        FolderLoader loader = new FolderLoader(apiClient);

        when(apiClient.loadPipelineFolder(anyLong())).thenReturn(expectedFolder);

        Optional<EntityContainer<Folder>> container = loader.loadEntity(1L);

        EntityContainer<Folder> folderEntityContainer = container.orElseThrow(AssertionError::new);

        Folder actualFolder = folderEntityContainer.getEntity();
        assertNotNull(actualFolder);
        assertEquals(expectedFolder.getName(), actualFolder.getName());
        assertEquals(expectedFolder.getId(), actualFolder.getId());
        assertEquals(expectedFolder.getParentId(), actualFolder.getParentId());

        verifyPipelineUser(folderEntityContainer.getOwner());
        verifyPermissions(PERMISSIONS_CONTAINER_WITH_OWNER, folderEntityContainer.getPermissions());
        verifyMetadata(EXPECTED_METADATA, new ArrayList<>(folderEntityContainer.getMetadata().values()));
    }
}
