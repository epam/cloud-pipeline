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

import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.entity.issue.Attachment;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.issue.IssueComment;
import com.epam.pipeline.entity.issue.IssueStatus;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.vo.EntityVO;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyAttachments;
import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyComments;
import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyIssue;
import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyPermissions;
import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyPipelineUser;
import static com.epam.pipeline.elasticsearchagent.TestConstants.PERMISSIONS_CONTAINER;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_DESCRIPTION;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_LABEL;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_NAME;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_PATH;
import static com.epam.pipeline.elasticsearchagent.TestConstants.USER;
import static com.epam.pipeline.elasticsearchagent.TestConstants.USER_NAME;

@SuppressWarnings({"PMD.TooManyStaticImports"})
class IssueMapperTest {

    @Test
    void shouldMapIssue() throws IOException {
        IssueMapper mapper = new IssueMapper();

        Attachment attachment = new Attachment();
        attachment.setPath(TEST_PATH);
        attachment.setOwner(USER_NAME);

        IssueComment comment = IssueComment
                .builder()
                .author(USER_NAME)
                .text(TEST_DESCRIPTION)
                .build();

        EntityVO entity = new EntityVO(1L, AclClass.TOOL);

        Issue issue = Issue
                .builder()
                .id(1L)
                .name(TEST_NAME)
                .text(TEST_DESCRIPTION)
                .status(IssueStatus.OPEN)
                .labels(Collections.singletonList(TEST_LABEL))
                .attachments(Collections.singletonList(attachment))
                .comments(Collections.singletonList(comment))
                .entity(entity)
                .build();

        EntityContainer<Issue> container = EntityContainer.<Issue>builder()
                .entity(issue)
                .owner(USER)
                .permissions(PERMISSIONS_CONTAINER)
                .build();
        XContentBuilder contentBuilder = mapper.map(container);

        verifyIssue(issue, contentBuilder);
        verifyAttachments(Collections.singletonList(attachment.getPath()), contentBuilder);
        verifyComments(Collections.singletonList(comment.getAuthor() + " : " + comment.getText()), contentBuilder);
        verifyPipelineUser(USER, contentBuilder);
        verifyPermissions(PERMISSIONS_CONTAINER, contentBuilder);
    }
}
