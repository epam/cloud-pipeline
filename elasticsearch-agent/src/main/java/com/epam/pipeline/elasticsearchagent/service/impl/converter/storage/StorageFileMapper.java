/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.elasticsearchagent.service.impl.converter.storage;

import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.epam.pipeline.elasticsearchagent.model.PermissionsContainer;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.search.SearchDocumentType;
import org.apache.commons.collections4.MapUtils;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;
import java.util.Map;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

public class StorageFileMapper {

    public XContentBuilder fileToDocument(final DataStorageFile dataStorageFile,
                                          final AbstractDataStorage dataStorage,
                                          final String region,
                                          final PermissionsContainer permissions,
                                          final SearchDocumentType type) {
        try (XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()) {
            final Map<String, String> tags = MapUtils.emptyIfNull(dataStorageFile.getTags());
            jsonBuilder
                    .startObject()
                    .field("lastModified", dataStorageFile.getChanged())
                    .field("size", dataStorageFile.getSize())
                    .field("path", dataStorageFile.getPath())
                    .field("id", dataStorageFile.getPath())
                    .field("name", dataStorageFile.getPath())
                    .field("ownerUserName", tags.get("CP_OWNER"))
                    .field("parentId", dataStorage.getId())
                    .field("storage_id", dataStorage.getId())
                    .field("storage_name", dataStorage.getName())
                    .field("storage_region", region)
                    .field("is_hidden", dataStorageFile.getIsHidden())
                    .field(DOC_TYPE_FIELD, type.name())
                    .array("metadata", tags.entrySet().stream()
                            .map(entry -> entry.getKey() + " " + entry.getValue())
                            .toArray(String[]::new))
                    .array("allowed_users", permissions.getAllowedUsers().toArray())
                    .array("denied_users", permissions.getDeniedUsers().toArray())
                    .array("allowed_groups", permissions.getAllowedGroups().toArray())
                    .array("denied_groups", permissions.getDeniedGroups().toArray());
            for (final Map.Entry<String, String> entry : tags.entrySet()) {
                jsonBuilder.field(entry.getKey(), entry.getValue());
            }
            return jsonBuilder.endObject();
        } catch (IOException e) {
            throw new AmazonS3Exception("An error occurred while creating document: ", e);
        }
    }
}
