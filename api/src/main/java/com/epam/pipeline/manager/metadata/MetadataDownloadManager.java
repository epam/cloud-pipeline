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

package com.epam.pipeline.manager.metadata;

import com.amazonaws.util.StringInputStream;
import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.manager.metadata.writer.MetadataFileFormat;
import com.epam.pipeline.manager.metadata.writer.MetadataWriter;
import com.epam.pipeline.manager.metadata.writer.MetadataWriterException;
import com.epam.pipeline.manager.metadata.writer.MetadataWriterProvider;
import com.epam.pipeline.manager.pipeline.FolderManager;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class MetadataDownloadManager {

    private final MetadataEntityManager metadataEntityManager;
    private final FolderManager folderManager;
    private final MessageHelper messageHelper;
    private final MetadataWriterProvider metadataWriterProvider;

    public InputStream getInputStream(final Long folderId,
                                      final String entityClass,
                                      final List<Long> entityIds,
                                      final String fileExtension) {
        final MetadataFileFormat fileFormat = retrieveMetadataFileFormat(fileExtension);
        final List<MetadataEntity> entities = retrieveMetadataEntities(folderId, entityClass,
                ListUtils.emptyIfNull(entityIds));
        final StringWriter stringWriter = new StringWriter();
        final MetadataWriter metadataWriter = metadataWriterProvider.getMetadataWriter(stringWriter, fileFormat);
        metadataWriter.writeEntities(entityClass, entities);
        try {
            return new StringInputStream(stringWriter.toString());
        } catch (IOException e) {
            throw new MetadataWriterException(
                    messageHelper.getMessage(MessageConstants.ERROR_METADATA_ENTITY_WRITING_BAD_ENCODING), e);
        }
    }

    private MetadataFileFormat retrieveMetadataFileFormat(final String fileExtension) {
        return Arrays.stream(MetadataFileFormat.values())
                .filter(format -> format.name().equalsIgnoreCase(fileExtension))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(messageHelper.getMessage(
                        MessageConstants.ERROR_METADATA_ENTITY_WRITING_UNSUPPORTED_FORMAT, fileExtension)));
    }

    private List<MetadataEntity> retrieveMetadataEntities(final Long folderId, final String entityClass,
                                                          final List<Long> entityIds) {
        folderManager.load(folderId);
        final List<MetadataEntity> entities = entityIds.isEmpty()
                ? loadEntities(folderId, entityClass)
                : loadEntities(folderId, entityClass, entityIds);
        Assert.isTrue(CollectionUtils.isNotEmpty(entities), messageHelper.getMessage(
                MessageConstants.ERROR_METADATA_ENTITY_CLASS_NOT_FOUND_IN_FOLDER, folderId, entityClass));
        return entities;
    }

    private List<MetadataEntity> loadEntities(final Long folderId, final String entityClass) {
        return metadataEntityManager.loadMetadataEntityByClassNameAndFolderId(folderId, entityClass);
    }

    private List<MetadataEntity> loadEntities(final Long folderId, final String entityClass,
                                              final List<Long> entityIds) {
        return metadataEntityManager.loadEntitiesByIds(new HashSet<>(entityIds)).stream()
                .filter(entity -> matches(entity, folderId, entityClass))
                .collect(Collectors.toList());
    }

    private boolean matches(final MetadataEntity entity, final Long folderId, final String entityClass) {
        return folderId.equals(resolveFolderId(entity).orElse(null))
                && entityClass.equals(resolveEntityClass(entity).orElse(null));
    }

    private Optional<Long> resolveFolderId(final MetadataEntity entity) {
        return Optional.ofNullable(entity)
                .map(MetadataEntity::getParent)
                .map(BaseEntity::getId);
    }

    private static Optional<String> resolveEntityClass(final MetadataEntity entity) {
        return Optional.ofNullable(entity)
                .map(MetadataEntity::getClassEntity)
                .map(MetadataClass::getName);
    }
}
