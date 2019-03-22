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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.dockerregistry;

import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.elasticsearchagent.service.EntityMapper;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.search.SearchDocumentType;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

@Component
public class DockerRegistryMapper implements EntityMapper<DockerRegistry> {

    @Override
    public XContentBuilder map(final EntityContainer<DockerRegistry> container) {
        DockerRegistry dockerRegistry = container.getEntity();
        try (XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()) {
            jsonBuilder
                    .startObject()
                    .field(DOC_TYPE_FIELD, SearchDocumentType.DOCKER_REGISTRY.name())
                    .field("id", dockerRegistry.getId())
                    .field("name", dockerRegistry.getName())
                    .field("path", dockerRegistry.getPath())
                    .field("createdDate", parseDataToString(dockerRegistry.getCreatedDate()))
                    .field("description", dockerRegistry.getDescription())
                    .field("userName", dockerRegistry.getUserName());

            buildUserContent(container.getOwner(), jsonBuilder);
            buildMetadata(container.getMetadata(), jsonBuilder);
            buildPermissions(container.getPermissions(), jsonBuilder);

            jsonBuilder.endObject();
            return jsonBuilder;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to create elasticsearch document for docker registry: ", e);
        }
    }
}
