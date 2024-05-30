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
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.metadata.CategoricalAttribute;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.MetadataEntryWithIssuesCount;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.metadata.CommonInstanceTagsType;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.metadata.parser.MetadataLineProcessor;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.RoleManager;
import com.epam.pipeline.manager.utils.MetadataParsingUtils;
import com.epam.pipeline.mapper.MetadataEntryMapper;
import com.epam.pipeline.utils.CommonUtils;
import com.epam.pipeline.utils.PipelineStringUtils;
import com.google.common.io.CharStreams;
import com.google.common.io.LineProcessor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.ListUtils;
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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private RoleManager roleManager;

    @Autowired
    private MetadataEntryMapper metadataEntryMapper;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private CategoricalAttributeManager categoricalAttributeManager;

    @Autowired
    private AuthManager authManager;

    @Autowired
    private ToolManager toolManager;

    @Transactional(propagation = Propagation.REQUIRED)
    public MetadataEntry updateMetadataItemKey(MetadataVO metadataVO) {
        validateMetadata(metadataVO);
        EntityVO entity = metadataVO.getEntity();
        checkEntityExistsAndCanBeModified(entity.getEntityId(), entity.getEntityClass());

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
        checkEntityExistsAndCanBeModified(entity.getEntityId(), entity.getEntityClass());

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
        checkEntityExistsAndCanBeModified(entity.getEntityId(), entity.getEntityClass());

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
        checkEntityExistsAndCanBeModified(entityVO.getEntityId(), entityVO.getEntityClass());

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
        checkEntityExistsAndCanBeModified(entity.getEntityId(), entity.getEntityClass());

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
        checkEntityExistsAndCanBeModified(entityVO.getEntityId(), entityVO.getEntityClass());

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
        } else {
            return entityManager.loadByNameOrId(entityClass, identifier).getId();
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public MetadataEntry uploadMetadataFromFile(final EntityVO entityVO,
                                                final MultipartFile file,
                                                final boolean mergeWithExistingMetadata) {
        checkEntityExistsAndCanBeModified(entityVO.getEntityId(), entityVO.getEntityClass());

        final MetadataVO metadataVO = new MetadataVO();
        metadataVO.setEntity(entityVO);
        metadataVO.setData(convertFileContentToMetadata(file));
        if (mergeWithExistingMetadata) {
            return updateMetadataItemKeys(metadataVO);
        } else {
            return updateMetadataItem(metadataVO);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateEntityMetadata(final Map<String, PipeConfValue> data, final Long entityId,
                                     final AclClass entityClass) {
        if (!MapUtils.isEmpty(data)) {
            MetadataVO metadataVO = new MetadataVO();
            metadataVO.setData(data);
            metadataVO.setEntity(new EntityVO(entityId, entityClass));
            updateMetadataItemKeys(metadataVO);
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
        if (value == null) {
            return metadataDao.searchMetadataByClassAndKey(entityClass, key);
        } else {
            final Map<String, PipeConfValue> indicator = Collections.singletonMap(key, new PipeConfValue(null, value));
            return metadataDao.searchMetadataByClassAndKeyValue(entityClass, indicator);
        }
    }

    public List<MetadataEntry> searchMetadataEntriesByClassAndKeyValue(final AclClass entityClass, final String key,
                                                           final String value) {
        if (value == null) {
            return metadataDao.searchMetadataEntriesByClassAndKey(entityClass, key);
        } else {
            final Map<String, PipeConfValue> indicator = Collections.singletonMap(key, new PipeConfValue(null, value));
            return metadataDao.searchMetadataEntriesByClassAndKeyValue(entityClass, indicator);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void syncWithCategoricalAttributes() {
        final List<CategoricalAttribute> fullMetadataDict = buildFullMetadataDict();
        final Map<String, CategoricalAttribute> existingAttributes = categoricalAttributeManager.loadAll().stream()
            .collect(Collectors.toMap(BaseEntity::getName, Function.identity()));
        fullMetadataDict.forEach(attributeFromMetadata -> {
            final String name = attributeFromMetadata.getName();
            if (existingAttributes.containsKey(name)) {
                final CategoricalAttribute existingAttribute = existingAttributes.get(name);
                attributeFromMetadata.setId(existingAttribute.getId());
                attributeFromMetadata.setOwner(existingAttribute.getOwner());
                categoricalAttributeManager.update(attributeFromMetadata);
            } else {
                categoricalAttributeManager.create(attributeFromMetadata);
            }
        });
    }

    public List<CategoricalAttribute> buildFullMetadataDict() {
        final List<String> sensitiveKeys = preferenceManager.getPreference(
                SystemPreferences.MISC_METADATA_SENSITIVE_KEYS);
        return metadataDao.buildFullMetadataDict(sensitiveKeys);
    }

    public Set<String> getMetadataKeys(final AclClass entityClass) {
        final List<String> sensitiveKeys = authManager.isAdmin() ? Collections.emptyList() :
                preferenceManager.getPreference(SystemPreferences.MISC_METADATA_SENSITIVE_KEYS);
        Set<String> keys = metadataDao.loadMetadataKeys(entityClass);
        keys.removeAll(ListUtils.emptyIfNull(sensitiveKeys));
        return keys;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public Map<String, String> prepareCloudResourceTags(final PipelineRun run) {
        try {
            if (!CloudProvider.AWS.equals(run.getInstance().getCloudProvider())) {
                return Collections.emptyMap();
            }
            final Map<String, String> implicitTags = resolveInstanceTagsFromPreference(run);
            final Map<String, String> explicitTags = resolveInstanceTagsFromMetadata(run.getDockerImage());
            return CommonUtils.mergeMaps(explicitTags, implicitTags);
        } catch (Exception e) {
            LOGGER.error("An error occurred during cloud resource tags preparation for run '{}'.", run.getId(), e);
            return Collections.emptyMap();
        }
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

    private void checkEntityExistsAndCanBeModified(final Long entityId, final AclClass entityClass) {
        final Object entity = loadEntity(entityId, entityClass);
        checkEntityExists(entity, entityId, entityClass);
        checkEntityCanBeModified(entity);
    }

    private Object loadEntity(final Long entityId, final AclClass entityClass) {
        Object entity;
        if (entityClass.equals(AclClass.ROLE)) {
            entity = roleManager.loadRole(entityId);
        } else {
            entity = entityManager.load(entityClass, entityId);
        }
        return entity;
    }

    private void checkEntityExists(final Object entity, final Long entityId, final AclClass entityClass) {
        Assert.notNull(entity, messageHelper.getMessage(
                MessageConstants.ERROR_ENTITY_FOR_METADATA_NOT_FOUND, entityId, entityClass));
    }

    private void checkEntityCanBeModified(final Object entity) {
        Optional.of(entity)
                .filter(Tool.class::isInstance)
                .map(Tool.class::cast)
                .ifPresent(tool -> Assert.isTrue(tool.isNotSymlink(), messageHelper.getMessage(
                        MessageConstants.ERROR_TOOL_SYMLINK_MODIFICATION_NOT_SUPPORTED)));
    }

    private Map<String, String> resolveInstanceTagsFromMetadata(final String dockerImage) {
        final Set<String> instanceTagsKeys = PipelineStringUtils.parseCommaSeparatedSet(
                preferenceManager.findPreference(SystemPreferences.CLUSTER_INSTANCE_ALLOWED_TAGS));
        if (CollectionUtils.isEmpty(instanceTagsKeys)) {
            return Collections.emptyMap();
        }

        final Tool tool = toolManager.loadByNameOrId(dockerImage);
        final MetadataEntry toolMetadata = loadMetadataItem(tool.getId(), AclClass.TOOL);

        return Optional.ofNullable(toolMetadata)
                .map(MetadataEntry::getData)
                .orElseGet(Collections::emptyMap)
                .entrySet().stream()
                .filter(entry -> instanceTagsKeys.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getValue()));
    }

    private Map<String, String> resolveInstanceTagsFromPreference(final PipelineRun run) {
        return MapUtils.emptyIfNull(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_TAGS))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, entry -> getInstanceTagValue(entry.getKey(), run)));
    }

    private String getInstanceTagValue(final CommonInstanceTagsType tagType, final PipelineRun run) {
        switch (tagType) {
            case tool:
                return run.getDockerImage();
            case run_id:
                return run.getId().toString();
            case owner:
                return run.getOwner();
            default:
                throw new IllegalArgumentException(String.format("Failed to resolve instance tag type '%s'", tagType));
        }
    }
}
