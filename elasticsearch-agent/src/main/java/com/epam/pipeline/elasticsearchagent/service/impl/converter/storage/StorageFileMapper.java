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
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.elasticsearchagent.utils.ESConstants;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.entity.search.StorageFileSearchMask;
import com.epam.pipeline.utils.FileContentUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.SetUtils;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.slf4j.Logger;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer.DOC_TYPE_FIELD;

public class StorageFileMapper {

    private static final String DELIMITER = "/";
    private static final String DEFAULT_MOUNT_POINT = "/cloud-data";

    private final Map<String, Set<String>> hiddenMasks = new HashMap<>();
    private final Map<String, Set<String>> indexContentMasks = new HashMap<>();

    public XContentBuilder fileToDocument(final DataStorageFile dataStorageFile,
                                          final AbstractDataStorage dataStorage,
                                          final String region,
                                          final PermissionsContainer permissions,
                                          final SearchDocumentType type,
                                          final String tagDelimiter,
                                          final String content) {
        try (XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()) {
            final Map<String, String> tags = MapUtils.emptyIfNull(dataStorageFile.getTags());
            final Map<String, String> labels = MapUtils.emptyIfNull(dataStorageFile.getLabels());
            jsonBuilder
                    .startObject()
                    .field("lastModified", dataStorageFile.getChanged())
                    .field("size", dataStorageFile.getSize())
                    .field("path", dataStorageFile.getPath())
                    .field("cloud_path", constructCloudPath(dataStorage, dataStorageFile))
                    .field("mount_path", constructMountPath(dataStorage, dataStorageFile))
                    .field("id", dataStorageFile.getPath())
                    .field("name", dataStorageFile.getName())
                    .field("ownerUserName", tags.get("CP_OWNER"))
                    .field("parentId", dataStorage.getId())
                    .field("storage_id", dataStorage.getId())
                    .field("storage_name", dataStorage.getName())
                    .field("storage_region", region)
                    .field("is_hidden", isHidden(dataStorage, dataStorageFile))
                    .field(DOC_TYPE_FIELD, type.name())
                    .array("metadata", tags.entrySet().stream()
                            .map(entry -> entry.getKey() + " " + entry.getValue())
                            .toArray(String[]::new))
                    .array("allowed_users", permissions.getAllowedUsers().toArray())
                    .array("denied_users", permissions.getDeniedUsers().toArray())
                    .array("allowed_groups", permissions.getAllowedGroups().toArray())
                    .array("denied_groups", permissions.getDeniedGroups().toArray())
                    .field("content", content);
            if (labels.containsKey(ESConstants.STORAGE_CLASS_LABEL)) {
                 jsonBuilder.field("storage_class", labels.get(ESConstants.STORAGE_CLASS_LABEL));
            }
            for (final Map.Entry<String, String> entry : tags.entrySet()) {
                if (StringUtils.hasText(tagDelimiter) && StringUtils.hasText(entry.getValue())
                        && entry.getValue().contains(tagDelimiter)) {
                    final String[] chunks = entry.getValue().split(tagDelimiter);
                    jsonBuilder.array(entry.getKey(), chunks);
                } else {
                    jsonBuilder.field(entry.getKey(), entry.getValue());
                }
            }
            return jsonBuilder.endObject();
        } catch (IOException e) {
            throw new AmazonS3Exception("An error occurred while creating document: ", e);
        }
    }

    // This method construct mount path assuming that default mount point is /cloud-data/,
    // if f.e. CP_STORAGE_MOUNT_ROOT_DIR is defined in launch.env.properties or somewhere else,
    // this method will return wrong results
    private static String constructMountPath(AbstractDataStorage dataStorage, DataStorageFile dataStorageFile) {
        String mountPoint = dataStorage.getMountPoint();
        if (StringUtils.isEmpty(mountPoint)) {
            final String storageMountName = DataStorageType.NFS == dataStorage.getType()
                    ? dataStorage.getPath().replaceFirst(":", "")
                    : dataStorage.getPath();
            mountPoint = String.join(DELIMITER, DEFAULT_MOUNT_POINT, storageMountName);
        }
        return String.join(DELIMITER, mountPoint, dataStorageFile.getPath());
    }

    private static String constructCloudPath(AbstractDataStorage dataStorage, DataStorageFile dataStorageFile) {
        return String.join(dataStorage.getDelimiter(), dataStorage.getPathMask(), dataStorageFile.getPath());
    }

    public void updateSearchMasks(final CloudPipelineAPIClient cloudPipelineAPIClient, final Logger logger) {
        final List<StorageFileSearchMask> storageSearchMasks = cloudPipelineAPIClient.getStorageSearchMasks();
        final Map<String, Set<String>> newHiddenMasks = getSearchMasks(storageSearchMasks,
                StorageFileSearchMask::getHiddenFilePathGlobs);
        hiddenMasks.clear();
        logger.info("Updating search hidden masks: {}", newHiddenMasks);
        hiddenMasks.putAll(newHiddenMasks);

        final Map<String, Set<String>> newIndexContentMasks = getSearchMasks(storageSearchMasks,
                StorageFileSearchMask::getIndexedContentPathGlobs);
        indexContentMasks.clear();
        logger.info("Updating search content masks: {}", newIndexContentMasks);
        indexContentMasks.putAll(newIndexContentMasks);
    }

    public boolean isSkipContent(final String storageName, final String filePath) {
        return !isMaskMatch(storageName, filePath, indexContentMasks);
    }

    public String getFileContent(final byte[] byteContent, final String filePath, final Logger logger) {
        if (Objects.isNull(byteContent)) {
            return null;
        }
        if (!FileContentUtils.isBinaryContent(byteContent)) {
            return new String(byteContent, StandardCharsets.UTF_8);
        }
        logger.debug("Skipping binary file '{}'", filePath);
        return null;
    }

    private boolean isHidden(final AbstractDataStorage dataStorage, final DataStorageFile file) {
        return isMaskMatch(dataStorage.getName(), file.getPath(), hiddenMasks);
    }

    private Map<String, Set<String>> getSearchMasks(final List<StorageFileSearchMask> storageSearchMasks,
                                                    final Function<StorageFileSearchMask, Set<String>> getMask) {
        return ListUtils.emptyIfNull(storageSearchMasks).stream()
                .filter(mask -> CollectionUtils.isNotEmpty(getMask.apply(mask)))
                .collect(Collectors.toMap(StorageFileSearchMask::getStorageName, getMask, SetUtils::union));
    }

    private boolean isMaskMatch(final String storageName, final String filePath, final Map<String, Set<String>> masks) {
        if (masks.containsKey(storageName)) {
            final AntPathMatcher pathMatcher = new AntPathMatcher();
            return CollectionUtils.emptyIfNull(masks.get(storageName)).stream()
                    .anyMatch(mask -> pathMatcher.match(mask, filePath));
        }
        return false;
    }
}
