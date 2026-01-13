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
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

@Component
public class DockerRegistryMapper implements EntityMapper<DockerRegistry> {

    @Override
    public Map<String, ?> map(final EntityContainer<DockerRegistry> container) {
        final DockerRegistry dockerRegistry = container.getEntity();
        final Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put(DOC_TYPE_FIELD, SearchDocumentType.DOCKER_REGISTRY.name());
        jsonMap.put("id", dockerRegistry.getId());
        jsonMap.put("name", dockerRegistry.getName());
        jsonMap.put("path", dockerRegistry.getPath());
        jsonMap.put("createdDate", parseDataToString(dockerRegistry.getCreatedDate()));
        jsonMap.put("description", dockerRegistry.getDescription());
        jsonMap.put("userName", dockerRegistry.getUserName());

        buildUserContent(container.getOwner(), jsonMap);
        buildMetadata(container.getMetadata(), jsonMap);
        buildPermissions(container.getPermissions(), jsonMap);

        return jsonMap;
    }
}
