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
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

@Component
public class IssueMapper implements EntityMapper<Issue> {

    @Override
    public Map<String, ?> map(final EntityContainer<Issue> container) {
        final Issue issue = container.getEntity();
        final Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put(DOC_TYPE_FIELD, SearchDocumentType.ISSUE.name());
        jsonMap.put("id", issue.getId());
        jsonMap.put("name", issue.getName());
        jsonMap.put("text", issue.getText());
        jsonMap.put("status", issue.getStatus());
        jsonMap.put("createdDate", parseDataToString(issue.getCreatedDate()));
        jsonMap.put("updatedDate", parseDataToString(issue.getUpdatedDate()));

        buildLabels(issue.getLabels(), jsonMap);
        buildAttachments(issue.getAttachments(), jsonMap);
        buildEntityVO(issue.getEntity(), jsonMap);
        buildComments(issue.getComments(), jsonMap);
        buildPermissions(container.getPermissions(), jsonMap);
        buildUserContent(container.getOwner(), jsonMap);

        return jsonMap;
    }

    private void buildComments(final List<IssueComment> comments, final Map<String, Object> jsonMap) {
        if (!CollectionUtils.isEmpty(comments)) {
            jsonMap.put("comments", comments.stream()
                    .map(comment -> comment.getAuthor() + " : " + comment.getText())
                    .toArray(String[]::new));
        }
    }

    private void buildAttachments(final List<Attachment> attachments, final Map<String, Object> jsonMap) {
        if (!CollectionUtils.isEmpty(attachments)) {
            jsonMap.put("attachments", attachments.stream()
                    .map(Attachment::getPath)
                    .toArray(String[]::new));
        }
    }

    private void buildLabels(final List<String> labels, final Map<String, Object> jsonMap) {
        if (!CollectionUtils.isEmpty(labels)) {
            jsonMap.put("labels", labels.toArray());
        }
    }

    private void buildEntityVO(final EntityVO entity, final Map<String, Object> jsonMap) {
        if (entity != null) {
            jsonMap.put("entityId", entity.getEntityId());
            jsonMap.put("parentId", entity.getEntityId());
            jsonMap.put("entityClass", entity.getEntityClass());
        }
    }
}
