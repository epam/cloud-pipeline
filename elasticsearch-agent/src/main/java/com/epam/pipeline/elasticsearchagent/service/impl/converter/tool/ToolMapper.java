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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.tool;

import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.elasticsearchagent.service.EntityMapper;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.search.SearchDocumentType;
import org.apache.commons.collections.CollectionUtils;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

@Component
public class ToolMapper implements EntityMapper<Tool> {

    @Override
    public XContentBuilder map(final EntityContainer<Tool> doc) {
        try (XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()) {
            Tool tool = doc.getEntity();
            jsonBuilder
                    .startObject()
                    .field(DOC_TYPE_FIELD, SearchDocumentType.TOOL.name())
                    .field("id", tool.getId())
                    .field("registry", tool.getRegistry())
                    .field("registryId", tool.getRegistryId())
                    .field("image", tool.getImage())
                    .field("createdDate", parseDataToString(tool.getCreatedDate()))
                    .field("description", tool.getDescription())
                    .field("shortDescription", tool.getShortDescription())
                    .field("defaultCommand", tool.getDefaultCommand())
                    .field("toolGroupId", tool.getToolGroupId());

            buildLabels(tool.getLabels(), jsonBuilder);

            buildUserContent(doc.getOwner(), jsonBuilder);
            buildMetadata(doc.getMetadata(), jsonBuilder);
            buildPermissions(doc.getPermissions(), jsonBuilder);

            jsonBuilder.endObject();
            return jsonBuilder;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to create elasticsearch document for tool: ", e);
        }
    }

    private void buildLabels(final List<String> labels, final XContentBuilder jsonBuilder) throws IOException {
        if (!CollectionUtils.isEmpty(labels)) {
            jsonBuilder.array("labels", labels.toArray());
        }
    }
}
