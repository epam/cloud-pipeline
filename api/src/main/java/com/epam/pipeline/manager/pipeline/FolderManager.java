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

package com.epam.pipeline.manager.pipeline;

import static com.epam.pipeline.utils.PasswordGenerator.generateRandomString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.dao.pipeline.FolderDao;
import com.epam.pipeline.entity.AbstractHierarchicalEntity;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.metadata.FolderWithMetadata;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.configuration.RunConfigurationManager;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.metadata.MetadataEntityManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.mapper.AbstractRunConfigurationMapper;
import com.epam.pipeline.mapper.AbstractDataStorageMapper;
import com.epam.pipeline.mapper.MetadataEntryMapper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FolderManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(FolderManager.class);
    private static final String PROJECT_INDICATOR_DELIMITER = "=";

    @Autowired
    private FolderCrudManager crudManager;

    @Autowired
    private FolderDao folderDao;

    @Autowired
    private PipelineManager pipelineManager;

    @Autowired
    private DataStorageManager dataStorageManager;

    @Autowired
    private RunConfigurationManager configurationManager;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private MetadataManager metadataManager;

    @Autowired
    private MetadataEntityManager metadataEntityManager;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private GrantPermissionManager permissionManager;

    @Autowired
    private AbstractRunConfigurationMapper runConfigurationMapper;

    @Autowired
    private MetadataEntryMapper metadataEntryMapper;

    @Autowired
    private AbstractDataStorageMapper dataStorageMapper;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private FolderTemplateManager folderTemplateManager;

    @Value("${storage.clone.name.suffix:}")
    private String storageSuffix;

    @Transactional(propagation = Propagation.REQUIRED)
    public Folder create(Folder folder) {
        return crudManager.create(folder);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Folder update(Folder folder) {
        return crudManager.update(folder);
    }

    public Folder load(Long id) {
        return crudManager.load(id);
    }

    public Folder loadByNameOrId(String pathOrIdentifier) {
        return crudManager.loadByNameOrId(pathOrIdentifier);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Folder delete(Long id) {
        return crudManager.delete(id);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Folder createFromTemplate(final Folder folder, final String templateName) {
        return folderTemplateManager.create(folder, templateName);
    }

    public Folder loadTree() {
        List<Folder> result = folderDao.loadAllFolders();
        List<Pipeline> rootPipelines = pipelineManager.loadRootPipelines();
        List<AbstractDataStorage> rootDataStorages = dataStorageManager.loadRootDataStorages();
        List<RunConfiguration> rootRunConfigurations = configurationManager.loadRootConfigurations();
        Map<String, Integer> rootMetadataEntityCount = metadataEntityManager.loadRootMetadataEntities();

        Folder root = new Folder();
        root.setChildFolders(result);
        if (!CollectionUtils.isEmpty(rootPipelines)) {
            root.setPipelines(rootPipelines);
        }
        if (!CollectionUtils.isEmpty(rootDataStorages)) {
            root.setStorages(rootDataStorages);
        }
        if (!CollectionUtils.isEmpty(rootRunConfigurations)){
            root.setConfigurations(rootRunConfigurations);
        }
        if (!CollectionUtils.sizeIsEmpty(rootMetadataEntityCount)) {
            root.setMetadata(rootMetadataEntityCount);
        }
        return root;
    }

    public Folder loadAllProjects() {
        Folder root = new Folder();
        Set<Pair<String, String>> indicator = parseProjectIndicator();
        Map<String, PipeConfValue> projectAttributes = indicator.stream()
                .collect(Collectors.toMap(Pair::getLeft, pair -> new PipeConfValue(null, pair.getRight())));
        if (MapUtils.isEmpty(projectAttributes)) {
            return root;
        }
        List<Folder> projects = folderDao.loadAllProjects(projectAttributes)
                .stream()
                .filter(folder -> {
                    if (folder instanceof FolderWithMetadata) {
                        FolderWithMetadata folderWithMetadata = (FolderWithMetadata) folder;
                        Map<String, PipeConfValue> attributes = folderWithMetadata.getData();
                        return containsProjectIndicator(indicator, attributes);
                    }
                    return false;
                })
                .collect(Collectors.toList());
        root.setChildFolders(projects);
        return root;
    }

    /**
     * Clones folder specified by ID. The following content will be copied: data storages, configurations,
     * folder metadata, metadata entities and child folders.
     * Note: pipelines will not be cloned.
     * @param id ID of {@link Folder} to be cloned
     * @param destinationFolderId ID of parent {@link Folder} for storing clone
     * @param name {@link Folder} clone name
     * @return resulting {@link Folder} instance
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Folder cloneFolder(Long id, Long destinationFolderId, String name) {
        Folder folderToClone = crudManager.load(id);
        Folder destinationFolder = crudManager.load(destinationFolderId);
        verifyFolderNames(destinationFolder.getChildFolders(), name);
        prepareStoragesForClone(folderToClone, name + storageSuffix, countDataStorages(folderToClone) > 1);
        folderToClone.setName(name);
        Folder clonedFolder = createCloneFolder(folderToClone, destinationFolderId);
        clonedFolder.setChildFolders(Collections.emptyList());
        return clonedFolder;
    }

    public FolderWithMetadata getProject(Long entityId, AclClass entityClass) {
        validateAclClass(entityClass);
        Set<Pair<String, String>> projectIndicators = parseProjectIndicator();
        if (CollectionUtils.isEmpty(projectIndicators)) {
            throw new IllegalArgumentException("Can not detect project: project indicator not found.");
        }
        AbstractSecuredEntity entity = entityManager.load(entityClass, entityId);
        AbstractSecuredEntity folderToStartSearch = entity.getAclClass().equals(AclClass.FOLDER)
                ? entity
                : entity.getParent();
        if (folderToStartSearch == null) {
            LOGGER.debug("Current entity doesn't have a Folder parent");
            return null;
        }
        if (!folderToStartSearch.getAclClass().equals(AclClass.FOLDER)) {
            throw new IllegalArgumentException("Parent must be a FOLDER.");
        }
        Map<Long, FolderWithMetadata> folders = convertListToMap(folderDao
                .loadParentFolders(folderToStartSearch.getId()));
        return getProjectFolder(folders, folderToStartSearch.getId(), projectIndicators);
    }

    /**
     * Prohibits any changes to a folder
     * Sets NO_WRITE & NO_EXECUTE permissions for an entity and all it's children
     * @param id specifies entity
     * @return
     */

    @Transactional(propagation = Propagation.REQUIRED)
    public Folder lockFolder(Long id) {
        Folder folder = crudManager.load(id);
        updateTreeLocks(folder, true);
        permissionManager.lockEntity(folder);
        return crudManager.load(id);
    }

    /**
     * Removes lock from a folder
     * @param id
     * @return
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Folder unlockFolder(Long id) {
        Folder folder = crudManager.load(id);
        updateTreeLocks(folder, false);
        permissionManager.unlockEntity(folder);
        return crudManager.load(id);
    }

    /**
     * Sets lock parameter for a list of {@link Folder} specified by Ids
     * @param folderIds
     * @param isLocked
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void updateLocks(List<Long> folderIds, boolean isLocked) {
        folderDao.updateLocks(folderIds, isLocked);
    }

    FolderWithMetadata getProjectFolder(Map<Long, FolderWithMetadata> folders, Long id,
                            Set<Pair<String, String>> projectIndicators) {
        FolderWithMetadata folder = folders.get(id);
        if (folder == null) {
            return null;
        }
        Map<String, PipeConfValue> metadata = folder.getData();
        if (MapUtils.isEmpty(metadata)) {
            LOGGER.debug("Can not detect project folder: metadata not found.");
            return continueSearch(folders, projectIndicators, folder);
        }
        if (containsProjectIndicator(projectIndicators, metadata)) {
            return folder;
        }
        return continueSearch(folders, projectIndicators, folder);
    }

    /**
     * Deletes a folder with all contents, specified by ID.
     * @param id of {@link Folder} to delete
     * @return deleted {@link Folder} instance
     */
    public Folder deleteForce(Long id) {
        Folder folder = crudManager.load(id);
        if (!CollectionUtils.isEmpty(folder.getChildren())) {
            for (AbstractHierarchicalEntity hierarchicalEntity : folder.getChildren()) {
                deleteForce(hierarchicalEntity.getId());
            }
        }
        deleteChildren(folder);
        return folder;
    }

    private void deleteChildren(Folder folder) {
        if (!CollectionUtils.isEmpty(folder.getPipelines())) {
            folder.getPipelines().forEach(pipeline -> pipelineManager.delete(pipeline.getId(), false));
        }
        if (!CollectionUtils.isEmpty(folder.getConfigurations())) {
            folder.getConfigurations().forEach(configuration -> configurationManager.delete(configuration.getId()));
        }
        if (!CollectionUtils.isEmpty(folder.getStorages())) {
            folder.getStorages().forEach(storage -> dataStorageManager.delete(storage.getId(), true));
        }
        delete(folder.getId());
    }

    private FolderWithMetadata continueSearch(Map<Long, FolderWithMetadata> folders,
            Set<Pair<String, String>> projectIndicators, FolderWithMetadata folder) {
        if (folder.getParentId() == null || folder.getParentId() == 0) {
            return null;
        }
        return getProjectFolder(folders, folder.getParentId(), projectIndicators);
    }


    private static Map<Long, FolderWithMetadata> convertListToMap(List<FolderWithMetadata> folders) {
        return folders.stream().collect(Collectors.toMap(Folder::getId, Function.identity()));
    }

    private void validateAclClass(AclClass entityClass) {
        if (entityClass.equals(AclClass.DOCKER_REGISTRY) || entityClass.equals(AclClass.TOOL)) {
            throw new IllegalArgumentException(
                    "Invalid ACL class: supports only classes with Folder parents.");
        }
    }

    private Set<Pair<String, String>> parseProjectIndicator() {
        List<String> projectIndicator = Arrays.asList(preferenceManager.getPreference(
                SystemPreferences.UI_PROJECT_INDICATOR).split(","));
        if (CollectionUtils.isEmpty(projectIndicator)) {
            return Collections.emptySet();
        }
        return projectIndicator
                .stream()
                .filter(indicator -> indicator.contains(PROJECT_INDICATOR_DELIMITER))
                .map(indicator -> {
                    String[] splittedProjectIndicator = indicator.split(PROJECT_INDICATOR_DELIMITER);
                    String key = splittedProjectIndicator[0];
                    String value = splittedProjectIndicator[1];
                    if (StringUtils.isEmpty(key) || StringUtils.isEmpty(value)) {
                        throw new IllegalArgumentException("Invalid project indicator pair.");
                    }
                    return new ImmutablePair<>(key, value);
                }).collect(Collectors.toSet());
    }

    private Folder createCloneFolder(Folder folderToClone, Long parentId) {
        Long sourceFolderId = folderToClone.getId();
        folderToClone.setParentId(parentId);
        Folder clonedFolder = crudManager.create(folderToClone);
        if (!CollectionUtils.isEmpty(folderToClone.getStorages())) {
            folderToClone.getStorages().forEach(storage -> {
                storage.setParentFolderId(clonedFolder.getId());
                dataStorageManager.create(dataStorageMapper.toDataStorageVO(storage), true, true, false);
            });
        }
        if (!CollectionUtils.isEmpty(folderToClone.getConfigurations())) {
            folderToClone.getConfigurations().forEach(runConfiguration -> {
                runConfiguration.setParent(clonedFolder);
                configurationManager.create(runConfigurationMapper.toRunConfigurationVO(runConfiguration));
            });
        }
        MetadataEntry metadataEntry = metadataManager.loadMetadataItem(sourceFolderId, AclClass.FOLDER);
        if (metadataEntry != null && !MapUtils.isEmpty(metadataEntry.getData())) {
            metadataEntry.setEntity(new EntityVO(clonedFolder.getId(), AclClass.FOLDER));
            metadataManager.updateMetadataItem(metadataEntryMapper.toMetadataVO(metadataEntry));
        }
        metadataEntityManager.insertCopiesOfExistentMetadataEntities(sourceFolderId, clonedFolder.getId());
        if (!CollectionUtils.isEmpty(folderToClone.getChildFolders())) {
            folderToClone.getChildFolders()
                    .forEach(child -> createCloneFolder(child, clonedFolder.getId()));
        }
        return clonedFolder;
    }

    private void prepareStoragesForClone(Folder folderHierarchy, String storageName, boolean generateRandomUID) {
        if (CollectionUtils.isNotEmpty(folderHierarchy.getStorages())) {
            folderHierarchy.getStorages().forEach(storage -> {
                String name = generateRandomUID ? storageName + generateRandomString(10) : storageName;
                storage.setName(name);
                String path = dataStorageManager.adjustStoragePath(name, storage.getType());
                storage.setPath(dataStorageManager.buildFullStoragePath(storage, path));
                verifyDataStorageNonExistence(storage);
            });
        }
        if (CollectionUtils.isNotEmpty(folderHierarchy.getChildFolders())) {
            folderHierarchy.getChildFolders()
                    .forEach(child -> prepareStoragesForClone(child, storageName, generateRandomUID));
        }
    }

    private void updateTreeLocks(Folder folder, boolean isLocked) {
        List<Long> folderIds = new ArrayList<>();
        List<Long> pipelineIds = new ArrayList<>();
        List<Long> storageIds = new ArrayList<>();
        List<Long> configurationIds = new ArrayList<>();
        collectChildIds(folder, folderIds, pipelineIds, storageIds, configurationIds);
        updateLocks(folderIds, isLocked);
        if (CollectionUtils.isNotEmpty(pipelineIds)) {
            pipelineManager.updateLocks(pipelineIds, isLocked);
        }
        if (CollectionUtils.isNotEmpty(storageIds)) {
            dataStorageManager.updateLocks(storageIds, isLocked);
        }
        if (CollectionUtils.isNotEmpty(configurationIds)) {
            configurationManager.updateLocks(configurationIds, isLocked);
        }
    }

    private void collectChildIds(Folder folder, List<Long> folderIds, List<Long> pipelineIds,
            List<Long> storageIds, List<Long> configurationIds) {
        folderIds.add(folder.getId());
        if (CollectionUtils.isNotEmpty(folder.getChildFolders())) {
            folder.getChildFolders().forEach(f ->
                    collectChildIds(f, folderIds, pipelineIds, storageIds, configurationIds));
        }
        addChildIds(folder.getPipelines(), pipelineIds);
        addChildIds(folder.getConfigurations(), configurationIds);
        addChildIds(folder.getStorages(), storageIds);
    }

    private void addChildIds(List<? extends BaseEntity> children, List<Long> childrenIds) {
        if (CollectionUtils.isNotEmpty(children)) {
            childrenIds.addAll(children.stream().map(BaseEntity::getId).collect(
                    Collectors.toList()));
        }
    }

    private void verifyFolderNames(List<Folder> folders, String name) {
        folders.forEach(folder -> {
            if (name.equalsIgnoreCase(folder.getName())) {
                throw new IllegalStateException(messageHelper
                        .getMessage(MessageConstants.ERROR_FOLDER_NAME_EXISTS, name, folder.getParentId()));
            }
        });
    }

    int countDataStorages(Folder folder) {
        return countDataStorages(folder, 0);
    }

    private int countDataStorages(Folder folder, int storagesCount) {
        int currentStorageCount = folder.getStorages() == null ? 0 : folder.getStorages().size();
        if (CollectionUtils.isNotEmpty(folder.getChildFolders())) {
            for (Folder child : folder.getChildFolders()) {
                currentStorageCount = countDataStorages(child, currentStorageCount);
            }
        }
        return storagesCount + currentStorageCount;
    }

    private void verifyDataStorageNonExistence(AbstractDataStorage storage) {
        if (dataStorageManager.checkExistence(storage)) {
            throw new IllegalStateException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_ALREADY_EXIST, storage.getName(), storage.getPath()));
        }
    }

    private boolean containsProjectIndicator(Set<Pair<String, String>> projectIndicator,
                                             Map<String, PipeConfValue> attributes) {
        return attributes != null &&
                projectIndicator.stream().anyMatch(indicator ->
                        attributes.containsKey(indicator.getLeft()) &&
                                indicator.getRight().equals(
                                        attributes.get(indicator.getKey()).getValue()));
    }
}
