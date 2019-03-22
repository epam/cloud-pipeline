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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.issue;

import com.epam.pipeline.elasticsearchagent.exception.EntityNotFoundException;
import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.entity.issue.Attachment;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.issue.IssueComment;
import com.epam.pipeline.entity.issue.IssueStatus;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.vo.EntityPermissionVO;
import com.epam.pipeline.vo.EntityVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

import static com.epam.pipeline.elasticsearchagent.LoaderVerificationUtils.verifyIssue;
import static com.epam.pipeline.elasticsearchagent.LoaderVerificationUtils.verifyMetadata;
import static com.epam.pipeline.elasticsearchagent.LoaderVerificationUtils.verifyPermissions;
import static com.epam.pipeline.elasticsearchagent.LoaderVerificationUtils.verifyPipelineUser;
import static com.epam.pipeline.elasticsearchagent.ObjectCreationUtils.buildEntityPermissionVO;
import static com.epam.pipeline.elasticsearchagent.ObjectCreationUtils.buildMetadataEntry;
import static com.epam.pipeline.elasticsearchagent.TestConstants.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"PMD.TooManyStaticImports"})
class IssueLoaderTest {

    @Mock
    private CloudPipelineAPIClient apiClient;

    @BeforeEach
    void setup() {
        EntityPermissionVO entityPermissionVO =
                buildEntityPermissionVO(USER_NAME, ALLOWED_USERS, DENIED_USERS, ALLOWED_GROUPS, DENIED_GROUPS);
        MetadataEntry metadataEntry =
                buildMetadataEntry(AclClass.TOOL, 1L, TEST_KEY + " " + TEST_VALUE);

        when(apiClient.loadUserByName(anyString())).thenReturn(USER);
        when(apiClient.loadPermissionsForEntity(anyLong(), any())).thenReturn(entityPermissionVO);
        when(apiClient.loadMetadataEntry(any())).thenReturn(Collections.singletonList(metadataEntry));
    }

    @Test
    void shouldLoadIssueTest() throws EntityNotFoundException {
        Attachment attachment = new Attachment();
        attachment.setPath(TEST_PATH);
        attachment.setOwner(USER_NAME);

        IssueComment comment = IssueComment
                .builder()
                .author(USER_NAME)
                .text(TEST_DESCRIPTION)
                .build();

        EntityVO entity = new EntityVO(1L, AclClass.TOOL);

        Issue expectedIssue = Issue
                .builder()
                .id(1L)
                .name(TEST_NAME)
                .text(TEST_DESCRIPTION)
                .status(IssueStatus.OPEN)
                .labels(Collections.singletonList(TEST_LABEL))
                .attachments(Collections.singletonList(attachment))
                .comments(Collections.singletonList(comment))
                .entity(entity)
                .author(TEST_NAME)
                .build();

        IssueLoader issueLoader = new IssueLoader(apiClient);

        when(apiClient.loadIssue(anyLong())).thenReturn(expectedIssue);

        Optional<EntityContainer<Issue>> container = issueLoader.loadEntity(1L);
        EntityContainer<Issue> issueEntityContainer = container.orElseThrow(AssertionError::new);
        Issue actualIssue = issueEntityContainer.getEntity();

        assertNotNull(actualIssue);

        verifyIssue(expectedIssue, actualIssue);

        verifyPipelineUser(issueEntityContainer.getOwner());
        verifyPermissions(PERMISSIONS_CONTAINER_WITH_OWNER, issueEntityContainer.getPermissions());
        verifyMetadata(EXPECTED_METADATA, new ArrayList<>(issueEntityContainer.getMetadata().values()));
    }
}