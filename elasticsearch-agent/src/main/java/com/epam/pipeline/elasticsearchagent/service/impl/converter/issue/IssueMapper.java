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
import com.epam.pipeline.elasticsearchagent.service.EntityMapper;
import com.epam.pipeline.entity.issue.Attachment;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.issue.IssueComment;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.vo.EntityVO;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.List;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

@Component
public class IssueMapper implements EntityMapper<Issue> {

    @Override
    public XContentBuilder map(final EntityContainer<Issue> container) {
        Issue issue = container.getEntity();
        try (XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()) {
            jsonBuilder.startObject();
            jsonBuilder
                    .field(DOC_TYPE_FIELD, SearchDocumentType.ISSUE.name())
                    .field("id", issue.getId())
                    .field("name", issue.getName())
                    .field("text", issue.getText())
                    .field("status", issue.getStatus())
                    .field("createdDate", parseDataToString(issue.getCreatedDate()))
                    .field("updatedDate", parseDataToString(issue.getUpdatedDate()));

            buildLabels(issue.getLabels(), jsonBuilder);
            buildAttachments(issue.getAttachments(), jsonBuilder);
            buildEntityVO(issue.getEntity(), jsonBuilder);
            buildComments(issue.getComments(), jsonBuilder);
            buildPermissions(container.getPermissions(), jsonBuilder);
            buildUserContent(container.getOwner(), jsonBuilder);

            jsonBuilder.endObject();
            return jsonBuilder;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to create elasticsearch document for issue: ", e);
        }
    }

    private void buildComments(final List<IssueComment> comments,
                               final XContentBuilder jsonBuilder) throws IOException {
        if (!CollectionUtils.isEmpty(comments)) {
            jsonBuilder.array("comments", comments.stream()
                    .map(comment -> comment.getAuthor() + " : " + comment.getText())
                    .toArray(String[]::new));
        }
    }

    private void buildAttachments(final List<Attachment> attachments,
                                  final XContentBuilder jsonBuilder) throws IOException {
        if (!CollectionUtils.isEmpty(attachments)) {
            jsonBuilder.array("attachments", attachments
                    .stream()
                    .map(Attachment::getPath)
                    .toArray(String[]::new));
        }
    }

    private void buildLabels(final List<String> labels, final XContentBuilder jsonBuilder) throws IOException {
        if (!CollectionUtils.isEmpty(labels)) {
            jsonBuilder.array("labels", labels.toArray());
        }
    }

    private void buildEntityVO(final EntityVO entity, final XContentBuilder jsonBuilder) throws IOException {
        if (entity != null) {
            jsonBuilder
                    .field("entityId", entity.getEntityId())
                    .field("entityClass", entity.getEntityClass());
        }
    }
}
