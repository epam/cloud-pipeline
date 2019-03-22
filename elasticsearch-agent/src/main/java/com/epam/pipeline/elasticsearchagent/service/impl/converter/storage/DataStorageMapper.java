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
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

@RequiredArgsConstructor
public class DataStorageMapper implements EntityMapper<DataStorageDoc> {

    private final SearchDocumentType documentType;

    @Override
    public XContentBuilder map(final EntityContainer<DataStorageDoc> doc) {
        try (XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()) {
            AbstractDataStorage storage = doc.getEntity().getStorage();
            jsonBuilder
                    .startObject()
                    .field(DOC_TYPE_FIELD, documentType.name())
                    .field("id", storage.getId())
                    .field("parentId", storage.getParentFolderId())
                    .field("name", storage.getName())
                    .field("path", storage.getPath())
                    .field("createdDate", parseDataToString(storage.getCreatedDate()))
                    .field("description", storage.getDescription())
                    .field("storageType", storage.getType())
                    .field("regionCode", doc.getEntity().getRegionName());

            StoragePolicy storagePolicy = storage.getStoragePolicy();
            if (storagePolicy != null) {
                jsonBuilder
                        .field("storagePolicyBackupDuration", storagePolicy.getBackupDuration())
                        .field("storagePolicyLongTermStorageDuration",
                                storagePolicy.getLongTermStorageDuration())
                        .field("storagePolicyShortTermStorageDuration",
                                storagePolicy.getShortTermStorageDuration())
                        .field("storagePolicyVersioningEnabled", storagePolicy.getVersioningEnabled());
            }

            buildUserContent(doc.getOwner(), jsonBuilder);
            buildMetadata(doc.getMetadata(), jsonBuilder);
            buildPermissions(doc.getPermissions(), jsonBuilder);

            jsonBuilder.endObject();
            return jsonBuilder;
        } catch (IOException e) {
            throw new IllegalArgumentException("An error occurred while creating document: ", e);
        }
    }
}
