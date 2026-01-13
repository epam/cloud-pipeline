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

import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.elasticsearchagent.model.PipelineDoc;
import com.epam.pipeline.elasticsearchagent.service.EntityMapper;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.entity.search.SearchDocumentType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

@Component
public class PipelineMapper implements EntityMapper<PipelineDoc> {

    @Override
    public Map<String, ?> map(final EntityContainer<PipelineDoc> container) {
        final PipelineDoc pipelineDoc = container.getEntity();
        final Map<String, Object> jsonMap = new HashMap<>();
        final List<String> revisions = pipelineDoc.getRevisions()
                .stream()
                .map(Revision::getName)
                .collect(Collectors.toList());
        jsonMap.put("id", pipelineDoc.getPipeline().getId());
        jsonMap.put(DOC_TYPE_FIELD, SearchDocumentType.PIPELINE.name());
        jsonMap.put("name", pipelineDoc.getPipeline().getName());
        jsonMap.put("description", pipelineDoc.getPipeline().getDescription());
        jsonMap.put("createdDate", parseDataToString(pipelineDoc.getPipeline().getCreatedDate()));
        jsonMap.put("parentId", pipelineDoc.getPipeline().getParentFolderId());
        jsonMap.put("repository", pipelineDoc.getPipeline().getRepository());
        jsonMap.put("versions", revisions);
        jsonMap.put("templateId", pipelineDoc.getPipeline().getTemplateId());

        buildUserContent(container.getOwner(), jsonMap);
        buildMetadata(container.getMetadata(), jsonMap);
        buildPermissions(container.getPermissions(), jsonMap);
        return jsonMap;
    }
}
