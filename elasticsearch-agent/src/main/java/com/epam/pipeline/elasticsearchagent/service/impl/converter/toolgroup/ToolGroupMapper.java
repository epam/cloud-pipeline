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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.toolgroup;

import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.elasticsearchagent.service.EntityMapper;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.search.SearchDocumentType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

@Component
public class ToolGroupMapper implements EntityMapper<ToolGroup> {

    @Override
    public Map<String, ?> map(final EntityContainer<ToolGroup> container) {
        final ToolGroup toolGroup = container.getEntity();
        final Map<String, Object> jsonMap = new HashMap<>();

        jsonMap.put(DOC_TYPE_FIELD, SearchDocumentType.TOOL_GROUP.name());
        jsonMap.put("id", toolGroup.getId());
        jsonMap.put("name", toolGroup.getName());
        jsonMap.put("registryId", toolGroup.getRegistryId());
        jsonMap.put("parentId", toolGroup.getRegistryId());
        jsonMap.put("createdDate", parseDataToString(toolGroup.getCreatedDate()));
        jsonMap.put("description", toolGroup.getDescription());

        buildUserContent(container.getOwner(), jsonMap);
        buildMetadata(container.getMetadata(), jsonMap);
        buildPermissions(container.getPermissions(), jsonMap);

        return jsonMap;
    }
}
