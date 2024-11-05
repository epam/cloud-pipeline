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
import com.epam.pipeline.elasticsearchagent.utils.ESConstants;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.search.SearchDocumentType;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

public class StorageFileMapper {

    public static final String STANDARD_TIER = "STANDARD";

    public XContentBuilder fileToDocument(final DataStorageFile dataStorageFile,
                                          final AbstractDataStorage dataStorage,
                                          final String region,
                                          final PermissionsContainer permissions,
                                          final SearchDocumentType type) {
        try (XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()) {
            final Map<String, String> labels = MapUtils.emptyIfNull(dataStorageFile.getLabels());
            jsonBuilder
                    .startObject()
                    .field("lastModified", dataStorageFile.getChanged())
                    .field("size", dataStorageFile.getSize())
                    .field("path", dataStorageFile.getPath())
                    .field("id", dataStorageFile.getPath())
                    .field("name", dataStorageFile.getPath())
                    .field("parentId", dataStorage.getId())
                    .field("tags", dataStorageFile.getTags())
                    .field("storage_id", dataStorage.getId())
                    .field("storage_name", dataStorage.getName())
                    .field("storage_region", region)
                    .field("is_deleted", Boolean.TRUE.equals(dataStorageFile.getDeleteMarker()))
                    .field("storage_class", labels.getOrDefault(ESConstants.STORAGE_CLASS_LABEL, STANDARD_TIER))
                    .field(DOC_TYPE_FIELD, type.name());

            if (MapUtils.isNotEmpty(dataStorageFile.getVersions())) {
                final Map<String, ImmutablePair<Long, Integer>> versionSizesByStorageClass =
                        calculateVersionSizes(dataStorageFile.getVersions());
                for (String key : versionSizesByStorageClass.keySet()) {
                    String storageClassKey = key.toLowerCase(Locale.ROOT);
                    jsonBuilder.field(
                            String.format("ov_%s_size", storageClassKey),
                            versionSizesByStorageClass.get(key).getLeft());
                    jsonBuilder.field(
                            String.format("ov_%s_count", storageClassKey),
                            versionSizesByStorageClass.get(key).getRight());
                }
            }

            jsonBuilder.array("allowed_users", permissions.getAllowedUsers().toArray());
            jsonBuilder.array("denied_users", permissions.getDeniedUsers().toArray());
            jsonBuilder.array("allowed_groups", permissions.getAllowedGroups().toArray());
            jsonBuilder.array("denied_groups", permissions.getDeniedGroups().toArray());

            jsonBuilder.endObject();
            return jsonBuilder;
        } catch (IOException e) {
            throw new AmazonS3Exception("An error occurred while creating document: ", e);
        }
    }

    private Map<String, ImmutablePair<Long, Integer>> calculateVersionSizes(
            final Map<String, AbstractDataStorageItem> versions) {
        final HashMap<String, ImmutablePair<Long, Integer>> result = new HashMap<>();
        for (final AbstractDataStorageItem v : versions.values()) {
            final DataStorageFile version = (DataStorageFile) v;
            final String storageClass = Optional.ofNullable(
                    MapUtils.emptyIfNull(version.getLabels()).get(ESConstants.STORAGE_CLASS_LABEL)
            ).orElse(STANDARD_TIER);
            result.compute(
                storageClass,
                (tier, tierSize) -> {
                    if (tierSize == null) {
                        return ImmutablePair.of(version.getSize(), 1);
                    } else {
                        return ImmutablePair.of(tierSize.getLeft() + version.getSize(), tierSize.getRight() + 1);
                    }
                }
            );
        }
        return result;
    }
}
