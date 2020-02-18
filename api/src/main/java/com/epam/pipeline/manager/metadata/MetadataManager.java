/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.MetadataVO;
import com.epam.pipeline.dao.metadata.MetadataDao;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.MetadataEntryWithIssuesCount;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.metadata.parser.MetadataLineProcessor;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.user.RoleManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.manager.utils.MetadataParsingUtils;
import com.epam.pipeline.mapper.MetadataEntryMapper;
import com.google.common.io.CharStreams;
import com.google.common.io.LineProcessor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("unchecked")
@Service
public class MetadataManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataManager.class);

    @Autowired
    private MetadataDao metadataDao;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private FolderManager folderManager;

    @Autowired
    private UserManager userManager;

    @Autowired
    private RoleManager roleManager;

    @Autowired
    private MetadataEntryMapper metadataEntryMapper;

    @Transactional(propagation = Propagation.REQUIRED)
    public MetadataEntry updateMetadataItemKey(MetadataVO metadataVO) {
        validateMetadata(metadataVO);
        EntityVO entity = metadataVO.getEntity();
        checkEntityExistence(entity.getEntityId(), entity.getEntityClass());

        MetadataEntry metadataToSave = metadataEntryMapper.toMetadataEntry(metadataVO);
        MetadataEntry existingMetadata = listMetadataItem(metadataToSave.getEntity(), false);
        if (existingMetadata == null) {
            LOGGER.debug("Could not find such metadata. A new one will be created.");
            metadataDao.registerMetadataItem(metadataToSave);
        } else {
            Map.Entry<String, PipeConfValue> metadataEntry = metadataToSave.getData().entrySet().iterator().next();
            metadataDao.uploadMetadataItemKey(metadataToSave.getEntity(), metadataEntry.getKey(),
                    metadataEntry.getValue().getValue(), metadataEntry.getValue().getType());
        }
        return metadataDao.loadMetadataItem(entity);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public MetadataEntry updateMetadataItemKeys(MetadataVO metadataVO) {
        validateMetadata(metadataVO);
        EntityVO entity = metadataVO.getEntity();
        checkEntityExistence(entity.getEntityId(), entity.getEntityClass());

        MetadataEntry metadataToSave = metadataEntryMapper.toMetadataEntry(metadataVO);
        MetadataEntry existingMetadata = listMetadataItem(metadataToSave.getEntity(), false);
        if (existingMetadata == null) {
            LOGGER.debug("Could not find such metadata. A new one will be created.");
            metadataDao.registerMetadataItem(metadataToSave);
        } else {
            existingMetadata.getData().putAll(metadataToSave.getData());
            metadataDao.uploadMetadataItem(existingMetadata);
        }
        return metadataDao.loadMetadataItem(entity);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public MetadataEntry updateMetadataItem(MetadataVO metadataVO) {
        validateMetadata(metadataVO);
        EntityVO entity = metadataVO.getEntity();
        checkEntityExistence(entity.getEntityId(), entity.getEntityClass());

        MetadataEntry metadataToUpdate = metadataEntryMapper.toMetadataEntry(metadataVO);
        MetadataEntry existingMetadata = listMetadataItem(metadataToUpdate.getEntity(), false);
        if (existingMetadata == null) {
            metadataDao.registerMetadataItem(metadataToUpdate);
        } else {
            metadataDao.uploadMetadataItem(metadataToUpdate);
        }
        return metadataToUpdate;
    }

    public List<MetadataEntry> listMetadataItems(List<EntityVO> entities) {
        if (CollectionUtils.isEmpty(entities)) {
            return Collections.emptyList();
        }
        return metadataDao.loadMetadataItems(entities);
    }

    public boolean hasMetadata(EntityVO entityVO) {
        return metadataDao.hasMetadata(entityVO);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public MetadataEntry deleteMetadataItemKey(EntityVO entityVO, String key) {
        checkEntityExistence(entityVO.getEntityId(), entityVO.getEntityClass());

        MetadataEntry metadataEntry = listMetadataItem(entityVO, true);
        if (!metadataEntry.getData().keySet().contains(key)) {
            throw new IllegalArgumentException("Could not delete non existing key.");
        }
        metadataEntry.getData().keySet().remove(key);
        metadataDao.deleteMetadataItemKey(entityVO, key);
        return metadataEntry;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public MetadataEntry deleteMetadataItemKeys(MetadataVO metadataWithKeysToDelete) {
        validateMetadata(metadataWithKeysToDelete);
        EntityVO entity = metadataWithKeysToDelete.getEntity();
        checkEntityExistence(entity.getEntityId(), entity.getEntityClass());

        MetadataEntry metadataEntry = listMetadataItem(entity, true);
        Set<String> existingKeys = metadataEntry.getData().keySet();
        Set<String> keysToDelete = metadataWithKeysToDelete.getData().keySet();
        if (!existingKeys.containsAll(keysToDelete)) {
            throw new IllegalArgumentException("Could not delete non existing key.");
        }
        return metadataDao.deleteMetadataItemKeys(metadataEntry, keysToDelete);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteMetadata(EntityVO entityVO) {
        metadataDao.deleteMetadataItem(entityVO);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public MetadataEntry deleteMetadataItem(EntityVO entityVO) {
        checkEntityExistence(entityVO.getEntityId(), entityVO.getEntityClass());

        MetadataEntry metadataEntry = listMetadataItem(entityVO, true);
        metadataDao.deleteMetadataItem(entityVO);
        return metadataEntry;
    }

    public MetadataEntry findMetadataEntryByNameOrId(String identifier, AclClass entityClass) {
        MetadataEntry metadataEntry = new MetadataEntry();
        EntityVO entity = new EntityVO(loadEntityId(identifier, entityClass), entityClass);
        metadataEntry.setEntity(entity);
        metadataEntry.setData(Collections.emptyMap());
        return metadataEntry;
    }

    private Long loadEntityId(String identifier, AclClass entityClass) {
        if (entityClass.equals(AclClass.ROLE)) {
            return roleManager.loadRoleByNameOrId(identifier).getId();
        } else if (entityClass.equals(AclClass.PIPELINE_USER)) {
            return userManager.loadUserByNameOrId(identifier).getId();
        } else {
            return entityManager.loadByNameOrId(entityClass, identifier).getId();
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public MetadataEntry uploadMetadataFromFile(final EntityVO entityVO,
                                                final MultipartFile file,
                                                final boolean mergeWithExistingMetadata) {
        checkEntityExistence(entityVO.getEntityId(), entityVO.getEntityClass());

        final MetadataVO metadataVO = new MetadataVO();
        metadataVO.setEntity(entityVO);
        metadataVO.setData(convertFileContentToMetadata(file));
        if (mergeWithExistingMetadata) {
            return updateMetadataItemKeys(metadataVO);
        } else {
            return updateMetadataItem(metadataVO);
        }
    }

    public List<String> loadUniqueValuesFromEntityClassMetadata(final AclClass entityClass, final String attributeKey) {
        if (StringUtils.isEmpty(attributeKey)) {
            throw new IllegalArgumentException(
                messageHelper.getMessage(MessageConstants.ERROR_KEY_FOR_METADATA_UNIQUE_VALUES_REQUEST_NOT_SPECIFIED));
        }
        return metadataDao.loadUniqueValuesFromEntitiesAttribute(entityClass, attributeKey);
    }

    public MetadataEntry loadMetadataItem(Long id, AclClass aclClass) {
        return listMetadataItem(new EntityVO(id, aclClass), false);
    }

    public List<MetadataEntryWithIssuesCount> loadEntitiesMetadataFromFolder(Long parentFolderId) {
        List<EntityVO> entities = new ArrayList<>();
        Folder folder;
        if (parentFolderId == null) {
            folder = folderManager.loadTree();
        } else {
            folder = folderManager.load(parentFolderId);
            entities.add(new EntityVO(parentFolderId, AclClass.FOLDER));
        }
        if (!CollectionUtils.isEmpty(folder.getPipelines())) {
            folder.getPipelines().forEach(pipeline ->
                    entities.add(new EntityVO(pipeline.getId(), AclClass.PIPELINE)));
        }
        if (!CollectionUtils.isEmpty(folder.getConfigurations())) {
            folder.getConfigurations().forEach(configuration ->
                    entities.add(new EntityVO(configuration.getId(), AclClass.CONFIGURATION)));
        }
        if (!CollectionUtils.isEmpty(folder.getStorages())) {
            folder.getStorages().forEach(storage ->
                    entities.add(new EntityVO(storage.getId(), AclClass.DATA_STORAGE)));
        }
        if (!CollectionUtils.isEmpty(folder.getChildren())) {
            folder.getChildren().forEach(childFolder ->
                    entities.add(new EntityVO(childFolder.getId(), AclClass.FOLDER)));
        }
        if (CollectionUtils.isEmpty(entities)) {
            return Collections.emptyList();
        }
        return metadataDao.loadMetadataItemsWithIssues(entities);
    }

    public List<EntityVO> searchMetadataByClassAndKeyValue(final AclClass entityClass, final String key,
                                                           final String value) {
        Map<String, PipeConfValue> indicator = Collections.singletonMap(key, new PipeConfValue(null, value));
        return metadataDao.searchMetadataByClassAndKeyValue(entityClass, indicator);
    }

    Map<String, PipeConfValue> convertFileContentToMetadata(MultipartFile file) {
        String delimiter = MetadataParsingUtils.getDelimiterFromFileExtension(file.getOriginalFilename());
        try (InputStream content = file.getInputStream()) {
            LineProcessor<Map<String, PipeConfValue>> processor = new MetadataLineProcessor(delimiter);
            return CharStreams.readLines(new InputStreamReader(content, StandardCharsets.UTF_8), processor);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read data.");
        }
    }

    private MetadataEntry listMetadataItem(EntityVO entityVO, boolean checkExistence) {
        MetadataEntry metadataEntry = metadataDao.loadMetadataItem(entityVO);
        if (checkExistence) {
            Assert.notNull(metadataEntry, messageHelper
                    .getMessage(MessageConstants.ERROR_METADATA_NOT_FOUND, entityVO.getEntityId(),
                            entityVO.getEntityClass()));
        }
        return metadataEntry;
    }

    private void validateMetadata(MetadataVO metadataVO) {
        Assert.notNull(metadataVO.getEntity(), messageHelper.getMessage(
                MessageConstants.ERROR_ENTITY_FOR_METADATA_NOT_SPECIFIED));
        Assert.notNull(metadataVO.getEntity().getEntityId(), messageHelper.getMessage(
                MessageConstants.ERROR_INVALID_METADATA_ENTITY_ID, metadataVO.getEntity().getEntityId()));
        Assert.notNull(metadataVO.getEntity().getEntityClass(), messageHelper.getMessage(
                MessageConstants.ERROR_INVALID_METADATA_ENTITY_CLASS, metadataVO.getEntity().getEntityClass()));
        Assert.notNull(metadataVO.getData(), messageHelper.getMessage(
                MessageConstants.ERROR_INVALID_METADATA, metadataVO.getData()));
    }

    private void checkEntityExistence(final Long entityId, final AclClass entityClass) {
        //just need to check that object is not null
        Object entity;
        if (entityClass.equals(AclClass.ROLE)) {
            entity = roleManager.loadRole(entityId);
        } else if (entityClass.equals(AclClass.PIPELINE_USER)) {
            entity = userManager.loadUserById(entityId);
        } else {
            entity = entityManager.load(entityClass, entityId);
        }
        Assert.notNull(entity,
                messageHelper.getMessage(MessageConstants.ERROR_ENTITY_FOR_METADATA_NOT_FOUND, entityId, entityClass));

    }
}
