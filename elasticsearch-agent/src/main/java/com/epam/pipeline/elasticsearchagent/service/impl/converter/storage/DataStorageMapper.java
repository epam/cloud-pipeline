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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.storage;

import com.epam.pipeline.elasticsearchagent.model.DataStorageDoc;
import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.elasticsearchagent.service.EntityMapper;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.search.SearchDocumentType;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

@RequiredArgsConstructor
public class DataStorageMapper implements EntityMapper<DataStorageDoc> {

    private final SearchDocumentType documentType;

    @Override
    public Map<String, ?> map(final EntityContainer<DataStorageDoc> doc) {
        final AbstractDataStorage storage = doc.getEntity().getStorage();
        final Map<String, Object> jsonMap = new HashMap<>();

        jsonMap.put(DOC_TYPE_FIELD, documentType.name());
        jsonMap.put("id", storage.getId());
        jsonMap.put("parentId", storage.getParentFolderId());
        jsonMap.put("name", storage.getName());
        jsonMap.put("path", storage.getPath());
        jsonMap.put("createdDate", parseDataToString(storage.getCreatedDate()));
        jsonMap.put("description", storage.getDescription());
        jsonMap.put("storageType", storage.getType());
        jsonMap.put("regionCode", doc.getEntity().getRegionName());

        StoragePolicy storagePolicy = storage.getStoragePolicy();
        if (storagePolicy != null) {
            jsonMap.put("storagePolicyBackupDuration", storagePolicy.getBackupDuration());
            jsonMap.put("storagePolicyLongTermStorageDuration", storagePolicy.getLongTermStorageDuration());
            jsonMap.put("storagePolicyShortTermStorageDuration", storagePolicy.getShortTermStorageDuration());
            jsonMap.put("storagePolicyVersioningEnabled", storagePolicy.getVersioningEnabled());
        }

        buildUserContent(doc.getOwner(), jsonMap);
        buildMetadata(doc.getMetadata(), jsonMap);
        buildPermissions(doc.getPermissions(), jsonMap);

        return jsonMap;
    }
}
