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
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

@Component
public class PipelineMapper implements EntityMapper<PipelineDoc> {

    @Override
    public XContentBuilder map(final EntityContainer<PipelineDoc> container) {
        PipelineDoc pipelineDoc = container.getEntity();
        try (XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()) {
            List<String> revisions = pipelineDoc.getRevisions()
                    .stream()
                    .map(Revision::getName)
                    .collect(Collectors.toList());
            jsonBuilder
                    .startObject()
                    .field("id", pipelineDoc.getPipeline().getId())
                    .field(DOC_TYPE_FIELD, SearchDocumentType.PIPELINE.name())
                    .field("name", pipelineDoc.getPipeline().getName())
                    .field("description", pipelineDoc.getPipeline().getDescription())
                    .field("createdDate", parseDataToString(pipelineDoc.getPipeline().getCreatedDate()))
                    .field("parentId", pipelineDoc.getPipeline().getParentFolderId())
                    .field("repository", pipelineDoc.getPipeline().getRepository())
                    .field("versions", revisions)
                    .field("templateId", pipelineDoc.getPipeline().getTemplateId());

            buildUserContent(container.getOwner(), jsonBuilder);
            buildMetadata(container.getMetadata(), jsonBuilder);
            buildOntologies(container.getOntologies(), jsonBuilder);
            buildPermissions(container.getPermissions(), jsonBuilder);

            jsonBuilder.endObject();
            return jsonBuilder;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to create elasticsearch document for pipeline: ", e);
        }
    }
}
