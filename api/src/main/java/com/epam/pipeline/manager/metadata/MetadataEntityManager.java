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
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.metadata.MetadataEntityVO;
import com.epam.pipeline.dao.metadata.MetadataClassDao;
import com.epam.pipeline.dao.metadata.MetadataEntityDao;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.metadata.FireCloudClass;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataClassDescription;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.MetadataField;
import com.epam.pipeline.entity.metadata.MetadataFilter;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.metadata.parser.EntityTypeField;
import com.epam.pipeline.manager.metadata.parser.MetadataEntityConverter;
import com.epam.pipeline.manager.metadata.parser.MetadataParsingResult;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.security.SecuredEntityManager;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MetadataEntityManager implements SecuredEntityManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataEntityManager.class);

    @Autowired
    private MetadataEntityDao metadataEntityDao;

    @Autowired
    private MetadataClassDao metadataClassDao;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private FolderManager folderManager;

    @Autowired
    private DataStorageManager storageManager;

    public Map<String, Integer> loadRootMetadataEntities() {
        Map<String, Integer> countEntities = new HashMap<>();
        List<MetadataEntity> entities = metadataEntityDao.loadRootMetadataEntities();
        entities.forEach(e -> countEntities.merge(e.getClassEntity().getName(), 1, (p, i) -> p + i));
        return countEntities;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public MetadataClass createMetadataClass(final String className) {
        if (StringUtils.isEmpty(className)) {
            throw new IllegalArgumentException("User entity class name must be not empty.");
        }
        MetadataClass metadataClass = new MetadataClass();
        metadataClass.setName(className);
        metadataClassDao.createMetadataClass(metadataClass);
        return metadataClass;
    }

    public List<MetadataClass> loadAllMetadataClasses() {
        return metadataClassDao.loadAllMetadataClasses();
    }

    public MetadataClass loadClass(String name) {
        MetadataClass metadataClass = metadataClassDao.loadMetadataClass(name);
        Assert.notNull(metadataClass,
                messageHelper.getMessage(MessageConstants.ERROR_METADATA_ENTITY_CLASS_NOT_FOUND, name));
        return metadataClass;
    }

    public MetadataClass loadClass(Long id) {
        MetadataClass metadataClass = metadataClassDao.loadMetadataClass(id);
        Assert.notNull(metadataClass,
                messageHelper.getMessage(MessageConstants.ERROR_METADATA_ENTITY_CLASS_NOT_FOUND, id));
        return metadataClass;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public MetadataClass deleteMetadataClass(Long id) {
        Assert.notNull(id, messageHelper.getMessage(MessageConstants.ERROR_INVALID_METADATA_ENTITY_CLASS_ID, id));
        MetadataClass metadataClass = loadClass(id);
        metadataClassDao.deleteMetadataClass(id);
        return metadataClass;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public MetadataClass updateExternalClassName(Long id, FireCloudClass externalClassName) {
        Assert.notNull(id, messageHelper.getMessage(MessageConstants.ERROR_INVALID_METADATA_ENTITY_CLASS_ID, id));
        MetadataClass metadataClass = loadClass(id);
        Assert.notNull(metadataClass,
                messageHelper.getMessage(MessageConstants.ERROR_METADATA_ENTITY_CLASS_NOT_FOUND, id));
        metadataClass.setFireCloudClassName(externalClassName);
        metadataClassDao.updateMetadataClass(metadataClass);
        return metadataClass;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public MetadataEntity updateMetadataEntity(MetadataEntityVO metadataEntityVO) {
        Assert.notNull(metadataEntityVO.getParentId(),
                messageHelper.getMessage(MessageConstants.ERROR_PARENT_REQUIRED));
        MetadataEntity metadataEntity = metadataEntityVO.convertToMetadataEntity();
        if (metadataEntity.getParent() != null) {
            folderManager.load(metadataEntity.getParent().getId());
        }
        Long entityId = metadataEntity.getId();
        if (entityId != null) {
            MetadataEntity existingMetadataEntity = existingMetadataItem(entityId, false);
            if (existingMetadataEntity != null) {
                metadataEntityDao.updateMetadataEntity(metadataEntity);
                return metadataEntity;
            }
            LOGGER.debug("Metadata entity with id %d was not found. A new one will be created.", entityId);
        }
        metadataEntityDao.createMetadataEntity(metadataEntity);
        return metadataEntity;
    }

    @Override
    public MetadataEntity load(Long id) {
        Assert.notNull(id, messageHelper.getMessage(MessageConstants.ERROR_INVALID_METADATA_ENTITY_ID, id));
        return metadataEntityDao.loadMetadataEntityById(id);
    }

    @Override
    public Integer loadTotalCount() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<? extends AbstractSecuredEntity> loadAllWithParents(Integer page, Integer pageSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MetadataEntity loadWithParents(final Long id) {
        return metadataEntityDao.loadMetadataEntityWithParents(id);
    }

    public List<MetadataEntity> loadMetadataEntityByClassNameAndFolderId(Long id, String className) {
        Assert.notNull(className, messageHelper.getMessage(MessageConstants.ERROR_METADATA_ENTITY_CLASS_NOT_FOUND));
        return metadataEntityDao.loadMetadataEntityByClassNameAndFolderId(id, className);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public MetadataEntity updateMetadataItemKey(MetadataEntityVO metadataEntityVO) {
        MetadataEntity metadataEntity = metadataEntityVO.convertToMetadataEntity();
        Long entityId = metadataEntity.getId();
        MetadataEntity dbEntity = load(entityId);
        Assert.notNull(dbEntity,
                messageHelper.getMessage(MessageConstants.ERROR_METADATA_ENTITY_NOT_FOUND, dbEntity));
        Assert.notNull(metadataEntity.getData(),
                messageHelper.getMessage(MessageConstants.ERROR_METADATA_UPDATE_KEY_NOT_FOUND, 0));
        Assert.isTrue(metadataEntity.getData().size() == 1,
                messageHelper.getMessage(MessageConstants.ERROR_METADATA_UPDATE_KEY_NOT_FOUND,
                        metadataEntity.getData().size()));
        Map.Entry<String, PipeConfValue> metadataEntry = metadataEntity.getData().entrySet().iterator().next();
        metadataEntityDao.updateMetadataEntityDataKey(metadataEntity, metadataEntry.getKey(),
                        metadataEntry.getValue().getValue(), metadataEntry.getValue().getType());
        return metadataEntityDao.loadMetadataEntityById(metadataEntity.getId());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void insertCopiesOfExistentMetadataEntities(Long existentParentId, Long parentIdToAdd) {
        metadataEntityDao.insertCopiesOfExistentMetadataEntities(existentParentId, parentIdToAdd);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public MetadataEntity deleteMetadataEntity(Long id) {
        Assert.notNull(id, messageHelper.getMessage(MessageConstants.ERROR_INVALID_METADATA_ENTITY_ID, id));
        MetadataEntity metadataEntity = metadataEntityDao.loadMetadataEntityById(id);
        Assert.notNull(metadataEntity, messageHelper
                .getMessage(MessageConstants.ERROR_METADATA_ENTITY_NOT_FOUND, id));
        metadataEntityDao.deleteMetadataEntity(metadataEntity.getId());
        return metadataEntity;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public MetadataEntity deleteMetadataItemKey(Long id, String key) {
        MetadataEntity existingMetadataEntity = existingMetadataItem(id, true);
        existingMetadataEntity.getData().keySet().remove(key);
        metadataEntityDao.deleteMetadataItemKey(existingMetadataEntity.getId(), key);
        return existingMetadataEntity;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Set<Long> deleteMetadataEntities(Set<Long> entitiesIds) {
        if (CollectionUtils.isEmpty(entitiesIds)) {
            throw new IllegalArgumentException(
                    messageHelper.getMessage(MessageConstants.ERROR_METADATA_ENTITIES_NOT_FOUND));
        }
        metadataEntityDao.deleteMetadataEntities(entitiesIds);
        return entitiesIds;
    }

    /**
     * Deletes all metadata entities, present in project {@link com.epam.pipeline.entity.pipeline.Folder}.
     * Optionally supports deletion only of specified {@link MetadataClass}
     * @param projectId specifies {@link com.epam.pipeline.entity.pipeline.Folder} to delete metadata from
     * @param entityClassName optional name of {@link MetadataClass}, if it is specified only entities of
     *                    this class are deleted
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteMetadataEntitiesInProject(Long projectId, String entityClassName) {
        Objects.requireNonNull(projectId);
        if (StringUtils.hasText(entityClassName)) {
            MetadataClass metadataClass =  loadClass(entityClassName);
            metadataEntityDao.deleteMetadataClassFromProject(projectId, metadataClass.getId());
        } else {
            metadataEntityDao.deleteMetadataFromFolder(projectId);
        }
    }

    /**
     * Deletes all {@link MetadataEntity} instances
     * from a {@link com.epam.pipeline.entity.pipeline.Folder}
     * specified by {@param folderId}
     * @param folderId to delete metadata entities
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteMetadataFromFolder(Long folderId) {
        metadataEntityDao.deleteMetadataFromFolder(folderId);
    }

    public MetadataEntity loadByExternalId(String id, String className, Long folderId) {
        Set<MetadataEntity> entities =
                getExistingEntities(Collections.singleton(id), folderId, className);
        Assert.isTrue(CollectionUtils.isNotEmpty(entities), messageHelper
                .getMessage(MessageConstants.ERROR_METADATA_ENTITY_NOT_FOUND, id));
        return entities.iterator().next();
    }

    public PagedResult<List<MetadataEntity>> filterMetadata(MetadataFilter filter) {
        Assert.notNull(filter.getFolderId(),
                messageHelper.getMessage(MessageConstants.ERROR_INVALID_METADATA_FILTER,
                        "folderId", filter.getFolderId()));
        folderManager.load(filter.getFolderId());
        Assert.notNull(filter.getMetadataClass(),
                messageHelper.getMessage(MessageConstants.ERROR_INVALID_METADATA_FILTER,
                        "class", filter.getFolderId()));
        loadClass(filter.getMetadataClass());
        Assert.isTrue(filter.getPage() > 0,
                messageHelper.getMessage(MessageConstants.ERROR_PAGE_INDEX));
        Assert.isTrue(filter.getPageSize() > 0,
                messageHelper.getMessage(MessageConstants.ERROR_PAGE_SIZE));
        List<MetadataEntity> result = metadataEntityDao.filterEntities(filter);
        List<PipeConfValue> paths = result.stream()
                .map(entry -> entry.getData().values())
                .flatMap(Collection::stream)
                .filter(param -> param.getType() != null && param.getType().equals(EntityTypeField.PATH_TYPE))
                .collect(Collectors.toList());
        storageManager.analyzePaths(paths);
        return new PagedResult<>(result, metadataEntityDao.countEntities(filter));

    }

    public List<MetadataField> getMetadataKeys(Long folderId, String className) {
        Assert.notNull(folderId,
                messageHelper.getMessage(MessageConstants.ERROR_INVALID_METADATA_FILTER, "folderId", folderId));
        folderManager.load(folderId);
        Assert.notNull(className,
                messageHelper.getMessage(MessageConstants.ERROR_INVALID_METADATA_FILTER, "class", className));
        MetadataClass metadataClass = loadClass(className);
        return metadataEntityDao.getMetadataKeys(folderId, metadataClass.getId());
    }

    public Collection<MetadataClassDescription> getMetadataFields(Long folderId) {
        Assert.notNull(folderId,
                messageHelper.getMessage(MessageConstants.ERROR_INVALID_METADATA_FILTER, "folderId", folderId));
        folderManager.load(folderId);
        Collection<MetadataClassDescription> metadataFields =
                metadataEntityDao.getMetadataFields(folderId);
        Set<String> presentClasses = metadataFields.stream()
                .map(c -> c.getMetadataClass().getName())
                .collect(Collectors.toSet());
        metadataFields.stream()
                .map(MetadataClassDescription::getFields)
                .flatMap(Collection::stream)
                .forEach(field -> field.setReference(presentClasses.contains(field.getType())));
        return metadataFields;
    }

    public Set<MetadataEntity> getExistingEntities(Set<String> externalIds, Long folderId, String className) {
        return metadataEntityDao.loadExisting(folderId, className, externalIds);
    }

    public Set<MetadataEntity> loadEntitiesByIds(Set<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptySet();
        }
        return metadataEntityDao.loadByIds(ids);
    }

    /**
     * Converts list of list of {@link MetadataEntity}s to map that represents entities data to upload to the FireCloud
     * workspace.
     * @param ids list of {@link MetadataEntity}s to be converted
     * @return entities data content represented with the following form:
     *         key - file name to be uploaded
     *         value - data content that ready for upload
     */
    public Map<String, String> loadEntitiesData(Set<Long> ids) {
        Set<MetadataEntity> metadataEntities = loadEntitiesByIds(ids);
        Long folderId = getCommonFolderForEntities(metadataEntities);
        List<MetadataEntity> entities = loadReferencesForEntities(new ArrayList<>(ids), folderId);
        return MetadataEntityConverter.convert(entities);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<MetadataEntity> createAndUpdateEntities(Long parentId, MetadataParsingResult parsedData) {
        checkUploadIntegrity(parsedData.getReferences(), parentId);
        Map<String, MetadataEntity>
                existing = getExistingEntities(
                parsedData.getEntities().keySet(), parentId, parsedData.getMetadataClass().getName())
                .stream()
                .collect(Collectors.toMap(MetadataEntity::getExternalId, Function.identity()));
        List<MetadataEntity> entitiesToUpdate = new ArrayList<>();
        List<MetadataEntity> entitiesToCreate = new ArrayList<>();
        parsedData.getEntities().values().forEach(e -> {
            if (existing.containsKey(e.getExternalId())) {
                MetadataEntity current = existing.get(e.getExternalId());
                if (org.apache.commons.lang3.StringUtils.isNotBlank(e.getName())) {
                    current.setName(e.getName());
                }
                current.getData().putAll(e.getData());
                entitiesToUpdate.add(current);
            } else {
                entitiesToCreate.add(e);
            }
        });
        List<MetadataEntity> result = new ArrayList<>(entitiesToCreate.size() + entitiesToUpdate.size());
        result.addAll(metadataEntityDao.batchInsert(entitiesToCreate));
        result.addAll(metadataEntityDao.batchUpdate(entitiesToUpdate));
        return result;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateMetadataEntities(List<MetadataEntity> metadataEntities) {
        metadataEntityDao.batchUpdate(metadataEntities);
    }

    public List<MetadataEntity> loadReferencesForEntities(List<Long> entityIds, Long parentId) {
        return metadataEntityDao.loadAllReferences(entityIds, parentId);
    }

    /**
     * @param references Class name to external IDs map
     * @param folderId
     */
    void checkUploadIntegrity(Map<String, Set<String>> references, Long folderId) {
        references.entrySet().forEach(ref -> {
            loadClass(ref.getKey());
            Set<MetadataEntity> existing = getExistingEntities(ref.getValue(), folderId, ref.getKey());
            Assert.isTrue(existing.size() == ref.getValue().size(),
                    "Not all required references are present");
        });
    }

    private Long getCommonFolderForEntities(Set<MetadataEntity> metadataEntities) {
        if (CollectionUtils.isEmpty(metadataEntities)) {
            return null;
        }
        MetadataEntity metadataEntity = metadataEntities.stream().findFirst()
                .orElseThrow(() ->  new IllegalArgumentException("Cannot determine folder for entities."));
        Long folderId = metadataEntity.getParent().getId();
        Assert.isTrue(metadataEntities.stream()
                .allMatch(entity -> Objects.equals(folderId, entity.getParent().getId())),
                messageHelper.getMessage(MessageConstants.ERROR_FOLDER_INVALID_ID));
        return folderId;
    }

    private MetadataEntity existingMetadataItem(Long id, boolean checkExistence) {
        MetadataEntity entity = metadataEntityDao.loadMetadataEntityById(id);
        if (checkExistence) {
            Assert.notNull(entity, messageHelper
                    .getMessage(MessageConstants.ERROR_METADATA_ENTITY_NOT_FOUND, id));
        }
        return entity;
    }

    @Override
    public AbstractSecuredEntity loadByNameOrId(String identifier) {
        if (NumberUtils.isDigits(identifier)) {
            MetadataEntity metadataEntity = metadataEntityDao.loadMetadataEntityById(Long.parseLong(identifier));
            if (metadataEntity != null) {
                return metadataEntity;
            }
        }
        //Search by name is not supported for metadata
        throw new UnsupportedOperationException(messageHelper
                .getMessage(MessageConstants.ERROR_UNSUPPORTED_OPERATION, "metadata entity"));
    }

    @Override
    public AbstractSecuredEntity changeOwner(Long id, String owner) {
        throw new UnsupportedOperationException("Can not perform operation with metadata entity.");
    }

    @Override
    public AclClass getSupportedClass() {
        return AclClass.METADATA_ENTITY;
    }
}
