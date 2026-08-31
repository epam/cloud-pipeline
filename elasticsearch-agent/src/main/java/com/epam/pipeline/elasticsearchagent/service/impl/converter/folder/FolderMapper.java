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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.folder;

import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.elasticsearchagent.service.EntityMapper;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.search.SearchDocumentType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

@Component
public class FolderMapper implements EntityMapper<Folder> {

    @Override
    public Map<String, ?> map(final EntityContainer<Folder> container) {
        final Folder folder = container.getEntity();
        final Map<String, Object> jsonMap = new HashMap<>();

        jsonMap.put(DOC_TYPE_FIELD, SearchDocumentType.FOLDER.name());
        jsonMap.put("id", folder.getId());
        jsonMap.put("name", folder.getName());
        jsonMap.put("parentId", folder.getParentId());
        jsonMap.put("createdDate", parseDataToString(folder.getCreatedDate()));

        buildUserContent(container.getOwner(), jsonMap);
        buildMetadata(container.getMetadata(), jsonMap);
        buildPermissions(container.getPermissions(), jsonMap);

        return jsonMap;
    }
}
