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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.metadata;

import com.epam.pipeline.elasticsearchagent.exception.EntityNotFoundException;
import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
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
import static com.epam.pipeline.elasticsearchagent.LoaderVerificationUtils.verifyMetadataEntity;
import static com.epam.pipeline.elasticsearchagent.LoaderVerificationUtils.verifyPermissions;
import static com.epam.pipeline.elasticsearchagent.ObjectCreationUtils.buildEntityPermissionVO;
import static com.epam.pipeline.elasticsearchagent.ObjectCreationUtils.buildMetadataEntry;
import static com.epam.pipeline.elasticsearchagent.TestConstants.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"PMD.TooManyStaticImports"})
class MetadataEntityLoaderTest {

    @Mock
    private CloudPipelineAPIClient apiClient;

    @BeforeEach
    void setup() {
        EntityPermissionVO entityPermissionVO =
                buildEntityPermissionVO(USER_NAME, ALLOWED_USERS, DENIED_USERS, ALLOWED_GROUPS, DENIED_GROUPS);
        MetadataEntry metadataEntry =
                buildMetadataEntry(AclClass.METADATA_ENTITY, 1L, TEST_KEY + " " + TEST_VALUE);

        when(apiClient.loadPermissionsForEntity(anyLong(), any())).thenReturn(entityPermissionVO);
        when(apiClient.loadMetadataEntry(any())).thenReturn(Collections.singletonList(metadataEntry));
    }

    @Test
    void shouldLoadMetadataEntityTest() throws EntityNotFoundException {
        MetadataClass metadataClass = new MetadataClass(1L, "Sample");
        MetadataEntity expectedMetadataEntity = new MetadataEntity();
        expectedMetadataEntity.setClassEntity(metadataClass);
        expectedMetadataEntity.setId(1L);
        expectedMetadataEntity.setExternalId("external");
        expectedMetadataEntity.setParent(new Folder(1L));
        expectedMetadataEntity.setName(TEST_NAME);
        expectedMetadataEntity.setOwner(TEST_NAME);
        expectedMetadataEntity.setData(
                Collections.singletonMap(TEST_KEY, new PipeConfValue("string", TEST_VALUE)));

        MetadataEntityLoader metadataEntityLoader = new MetadataEntityLoader(apiClient);

        when(apiClient.loadMetadataEntity(anyLong())).thenReturn(expectedMetadataEntity);

        Optional<EntityContainer<MetadataEntity>> container = metadataEntityLoader.loadEntity(1L);
        EntityContainer<MetadataEntity> metadataEntityContainer = container.orElseThrow(AssertionError::new);
        MetadataEntity actualMetadataEntity = metadataEntityContainer.getEntity();

        assertNotNull(actualMetadataEntity);

        verifyMetadataEntity(expectedMetadataEntity, actualMetadataEntity);

        verifyPermissions(PERMISSIONS_CONTAINER_WITH_OWNER, metadataEntityContainer.getPermissions());
        verifyMetadata(EXPECTED_METADATA, new ArrayList<>(metadataEntityContainer.getMetadata().values()));
    }
}