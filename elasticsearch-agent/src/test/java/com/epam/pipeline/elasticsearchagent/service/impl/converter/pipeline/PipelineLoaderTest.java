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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.pipeline;

import com.epam.pipeline.elasticsearchagent.exception.EntityNotFoundException;
import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.elasticsearchagent.model.PipelineDoc;
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.vo.EntityPermissionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
class PipelineLoaderTest {

    @Mock
    private CloudPipelineAPIClient apiClient;

    @BeforeEach
    void setup() {
        EntityPermissionVO entityPermissionVO =
                buildEntityPermissionVO(USER_NAME, ALLOWED_USERS, DENIED_USERS, ALLOWED_GROUPS, DENIED_GROUPS);

        MetadataEntry metadataEntry =
                buildMetadataEntry(AclClass.PIPELINE, 1L, TEST_KEY + " " + TEST_VALUE);

        when(apiClient.loadUserByName(anyString())).thenReturn(USER);
        when(apiClient.loadPermissionsForEntity(anyLong(), any())).thenReturn(entityPermissionVO);
        when(apiClient.loadMetadataEntry(any())).thenReturn(Collections.singletonList(metadataEntry));
    }

    @Test
    void shouldLoadPipeline() throws EntityNotFoundException {
        Pipeline expectedPipeline = new Pipeline(1L);
        expectedPipeline.setName(TEST_NAME);
        expectedPipeline.setCreatedDate(DateUtils.now());
        expectedPipeline.setParentFolderId(2L);
        expectedPipeline.setDescription(TEST_DESCRIPTION);
        expectedPipeline.setRepository(TEST_REPO);
        expectedPipeline.setTemplateId(TEST_TEMPLATE);
        expectedPipeline.setOwner(TEST_NAME);

        Revision expectedRevision = new Revision();
        expectedRevision.setName(TEST_NAME);

        PipelineLoader pipelineLoader = new PipelineLoader(apiClient);

        when(apiClient.loadPipeline(anyString())).thenReturn(expectedPipeline);
        when(apiClient.loadPipelineVersions(anyLong())).thenReturn(Collections.singletonList(expectedRevision));

        Optional<EntityContainer<PipelineDoc>> container = pipelineLoader.loadEntity(1L);
        EntityContainer<PipelineDoc> pipelineDocEntityContainer = container.orElseThrow(AssertionError::new);
        PipelineDoc actualPipelineDoc = pipelineDocEntityContainer.getEntity();

        assertNotNull(actualPipelineDoc);

        Pipeline actualPipeline = actualPipelineDoc.getPipeline();
        assertNotNull(actualPipeline);

        List<Revision> revisions = actualPipelineDoc.getRevisions();
        assertNotNull(revisions);

        assertEquals(expectedPipeline.getId(), actualPipeline.getId());
        assertEquals(expectedPipeline.getName(), actualPipeline.getName());
        assertEquals(expectedPipeline.getCreatedDate(), actualPipeline.getCreatedDate());
        assertEquals(expectedPipeline.getParentFolderId(), actualPipeline.getParentFolderId());
        assertEquals(expectedPipeline.getDescription(), actualPipeline.getDescription());
        assertEquals(expectedPipeline.getRepository(), actualPipeline.getRepository());
        assertEquals(expectedPipeline.getTemplateId(), actualPipeline.getTemplateId());

        verifyPipelineUser(pipelineDocEntityContainer.getOwner());
        verifyPermissions(PERMISSIONS_CONTAINER_WITH_OWNER, pipelineDocEntityContainer.getPermissions());
        verifyMetadata(EXPECTED_METADATA, new ArrayList<>(pipelineDocEntityContainer.getMetadata().values()));
    }
}
