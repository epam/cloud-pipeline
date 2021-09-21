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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.exception.MetadataReadingException;
import com.epam.pipeline.manager.metadata.parser.EntityTypeField;
import com.epam.pipeline.manager.metadata.parser.MetadataEntityHeaderParser;
import com.epam.pipeline.manager.metadata.parser.MetadataEntityReader;
import com.epam.pipeline.manager.metadata.parser.MetadataHeader;
import com.epam.pipeline.manager.metadata.parser.MetadataParsingResult;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.utils.MetadataParsingUtils;
import com.google.common.io.ByteSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

@Service
public class MetadataUploadManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataUploadManager.class);

    @Autowired
    private MetadataEntityManager metadataEntityManager;

    @Autowired
    private FolderManager folderManager;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private AuthManager authManager;


    public List<MetadataEntity> uploadFromFile(Long parentId, MultipartFile file) {
        Assert.notNull(parentId,
                messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY));
        MetadataParsingResult parsedData = readFile(parentId, file);
        return metadataEntityManager.createAndUpdateEntities(parentId, parsedData);
    }

    private MetadataParsingResult readFile(Long parentId, MultipartFile file) {
        try {
            final Folder parent = folderManager.load(parentId);
            String delimiter = MetadataParsingUtils.getDelimiterFromFileExtension(file.getOriginalFilename());
            String fallbackMetadataClass = 
                    MetadataParsingUtils.getMetadataClassFromFileName(file.getOriginalFilename());
            byte[] inputData = file.getBytes();
            MetadataHeader header = new MetadataEntityHeaderParser(delimiter, fallbackMetadataClass)
                    .readHeader(ByteSource.wrap(inputData).openStream());
            validateTypes(header, parentId);
            MetadataClass metadataClass = getOrCreateClass(header.getClassName());
            return new MetadataEntityReader(delimiter, parent, metadataClass)
                    .readData(ByteSource.wrap(inputData).openStream(), header.getFields(), 
                            header.isClassColumnPresent());
        } catch (IOException e) {
            throw new MetadataReadingException(e.getMessage(), e);
        }
    }

    private void validateTypes(MetadataHeader header, Long parentId) {
        final String className = header.getClassName();
        final Map<String, String> existingTypes = getExistingTypes(className, parentId);
        header.getFields()
                .values()
                .forEach(field -> {
                    final String fieldName = field.getName();
                    final String fieldType = field.getType();
                    if(existingTypes.containsKey(fieldName) && !fieldType.equals(existingTypes.get(fieldName))) {
                        throw new MetadataReadingException(
                                messageHelper.getMessage(
                                        MessageConstants.ERROR_METADATA_UPLOAD_CHANGED_TYPE,
                                        fieldType, fieldName, existingTypes.get(fieldName))
                        );
                    }
                });
    }

    private Map<String, String> getExistingTypes(String className, Long parentId) {
        return metadataEntityManager.getMetadataFields(parentId)
                .stream()
                .filter(description -> description.getMetadataClass().getName().equals(className))
                .flatMap(d -> d.getFields().stream())
                .collect(toMap(EntityTypeField::getName, EntityTypeField::getType));
    }

    private MetadataClass getOrCreateClass(String className) {
        MetadataClass metadataClass;
        try {
            metadataClass = metadataEntityManager.loadClass(className);
        } catch (IllegalArgumentException e) {
            LOGGER.trace(e.getMessage(), e);
            if (authManager.isAdmin()) {
                metadataClass = metadataEntityManager.createMetadataClass(className);
            } else {
                throw new MetadataReadingException("Only users with admin role are allowed to create new entity types");
            }
        }
        return metadataClass;
    }
}
