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

package com.epam.pipeline.manager.datastorage;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.data.storage.UpdateDataStorageItemVO;
import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageFactory;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageItemType;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.DataStorageWithShareMount;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.datastorage.StorageServiceType;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.run.parameter.DataStorageLink;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.StorageContainer;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.SecuredEntityManager;
import com.epam.pipeline.manager.security.acl.AclSync;
import com.epam.pipeline.manager.user.RoleManager;
import com.epam.pipeline.manager.user.UserManager;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;

import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@AclSync
public class DataStorageManager implements SecuredEntityManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataStorageManager.class);

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private StorageProviderManager storageProviderManager;

    @Autowired
    private DataStorageDao dataStorageDao;

    @Autowired
    private AuthManager authManager;

    @Autowired
    private FolderManager folderManager;

    @Autowired
    private MetadataManager metadataManager;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private CloudRegionManager cloudRegionManager;

    @Autowired
    private FileShareMountManager fileShareMountManager;

    @Autowired
    private RoleManager roleManager;

    @Autowired
    private UserManager userManager;

    private AbstractDataStorageFactory dataStorageFactory =
            AbstractDataStorageFactory.getDefaultDataStorageFactory();

    public List<AbstractDataStorage> getDataStorages() {
        return dataStorageDao.loadAllDataStorages();
    }

    public List<DataStorageWithShareMount> getDataStoragesWithShareMountObject() {
        return dataStorageDao.loadAllDataStorages().stream().map(storage -> {
            if (storage.getFileShareMountId() != null) {
                return new DataStorageWithShareMount(storage,
                        fileShareMountManager.load(storage.getFileShareMountId()));
            } else {
                return new DataStorageWithShareMount(storage, null);
            }
        }).collect(Collectors.toList());
    }

    @Override
    public AbstractDataStorage load(final Long id) {
        AbstractDataStorage dbDataStorage = dataStorageDao.loadDataStorage(id);
        Assert.notNull(dbDataStorage, messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_NOT_FOUND, id));
        dbDataStorage.setHasMetadata(this.metadataManager.hasMetadata(new EntityVO(id, AclClass.DATA_STORAGE)));
        return dbDataStorage;
    }

    @Override
    public AbstractSecuredEntity changeOwner(Long id, String owner) {
        final AbstractDataStorage dataStorage = dataStorageDao.loadDataStorage(id);
        dataStorage.setOwner(owner);
        dataStorageDao.updateDataStorage(dataStorage);
        return dataStorage;
    }

    @Override public AclClass getSupportedClass() {
        return AclClass.DATA_STORAGE;
    }

    public AbstractDataStorage loadByNameOrId(final String identifier) {
        AbstractDataStorage dataStorage = null;
        if (NumberUtils.isDigits(identifier)) {
            dataStorage = dataStorageDao.loadDataStorage(Long.parseLong(identifier));
        }

        if (dataStorage == null) {
            String pathWithName = identifier;
            if (pathWithName.startsWith("/")) {
                pathWithName = pathWithName.substring(1);
            }
            if (pathWithName.endsWith("/")) {
                pathWithName = pathWithName.substring(0, pathWithName.length() - 1);
            }
            String[] pathParts = pathWithName.split("/");
            String dataStorageName = pathParts[pathParts.length - 1];
            Long parentFolderId = null;
            if (pathParts.length > 1) {
                Folder parentFolder = folderManager
                        .loadByNameOrId(
                                String.join("/", Arrays.asList(pathParts).subList(0, pathParts.length - 1)));
                if (parentFolder != null) {
                    parentFolderId = parentFolder.getId();
                }
            }
            if (parentFolderId != null) {
                dataStorage = dataStorageDao.loadDataStorageByNameAndParentId(dataStorageName, parentFolderId);
            } else {
                dataStorage = dataStorageDao.loadDataStorageByNameOrPath(dataStorageName, dataStorageName);
            }
        }
        Assert.notNull(dataStorage, messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_NOT_FOUND, identifier));
        dataStorage.setHasMetadata(metadataManager.hasMetadata(new EntityVO(dataStorage.getId(),
                AclClass.DATA_STORAGE)));
        return dataStorage;
    }

    @Override
    public Integer loadTotalCount() {
        return dataStorageDao.loadTotalCount();
    }

    @Override
    public Collection<AbstractDataStorage> loadAllWithParents(Integer page, Integer pageSize) {
        Assert.isTrue((page == null) == (pageSize == null),
                messageHelper.getMessage(MessageConstants.ERROR_INVALID_PAGE_INDEX_OR_SIZE, page, pageSize));
        Assert.isTrue(page == null || page > 0, messageHelper.getMessage(MessageConstants.ERROR_PAGE_INDEX));
        Assert.isTrue(pageSize == null || pageSize > 0, messageHelper.getMessage(MessageConstants.ERROR_PAGE_SIZE));
        return dataStorageDao.loadAllWithParents(page, pageSize);
    }

    @Override
    public AbstractSecuredEntity loadWithParents(final Long id) {
        return dataStorageDao.loadStorageWithParents(id);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public AbstractDataStorage update(DataStorageVO dataStorageVO) {
        assertDataStorageMountPoint(dataStorageVO);
        AbstractDataStorage dataStorage = load(dataStorageVO.getId());
        AbstractDataStorage updated = updateDataStorageObject(dataStorage, dataStorageVO);
        dataStorageDao.updateDataStorage(updated);
        return updated;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public AbstractDataStorage updatePolicy(DataStorageVO dataStorageVO) {
        AbstractDataStorage dataStorage = load(dataStorageVO.getId());
        AbstractDataStorage updated = updateStoragePolicy(dataStorage, dataStorageVO);
        storageProviderManager.applyStoragePolicy(dataStorage);
        dataStorageDao.updateDataStorage(updated);
        return updated;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public AbstractDataStorage create(DataStorageVO dataStorageVO, Boolean proceedOnCloud, Boolean checkExistence,
                                      boolean replaceStoragePath)
            throws DataStorageException {
        Assert.isTrue(!StringUtils.isEmpty(dataStorageVO.getName()),
                messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY, "name"));

        if (dataStorageVO.getServiceType() == StorageServiceType.FILE_SHARE) {
            Assert.notNull(dataStorageVO.getFileShareMountId(),
                    messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY,
                           "fileShareMountId"));
        }

        assertDataStorageMountPoint(dataStorageVO);

        dataStorageVO.setName(dataStorageVO.getName().trim());

        final AbstractCloudRegion storageRegion = getDatastorageCloudRegionOrDefault(dataStorageVO);
        dataStorageVO.setRegionId(storageRegion.getId());
        checkDatastorageDoesntExist(dataStorageVO.getName(), dataStorageVO.getPath());
        verifyStoragePolicy(dataStorageVO.getStoragePolicy());

        AbstractDataStorage dataStorage = dataStorageFactory.convertToDataStorage(dataStorageVO,
                storageRegion.getProvider());
        if (StringUtils.isBlank(dataStorage.getMountOptions())) {
            dataStorage.setMountOptions(
                storageProviderManager.getStorageProvider(dataStorage).getDefaultMountOptions(dataStorage));
        }

        if (proceedOnCloud) {
            if (replaceStoragePath) {
                dataStorage.setPath(adjustStoragePath(dataStorage.getPath(), dataStorage.getType()));
            }
            String created = storageProviderManager.createBucket(dataStorage);
            dataStorage.setPath(created);
        } else if (checkExistence && !storageProviderManager.checkStorage(dataStorage)) {
            throw new IllegalStateException(messageHelper
                    .getMessage(MessageConstants.ERROR_DATASTORAGE_NOT_FOUND_BY_NAME, dataStorage.getName(),
                            dataStorage.getPath()));

        }

        dataStorage.setOwner(authManager.getAuthorizedUser());
        if (dataStorage.getParentFolderId() != null) {
            Folder parent = folderManager.load(dataStorage.getParentFolderId());
            dataStorage.setParent(parent);
        }

        dataStorageDao.createDataStorage(dataStorage);

        if (dataStorage.isPolicySupported()) {
            storageProviderManager.applyStoragePolicy(dataStorage);
        }
        return dataStorage;
    }

    private AbstractCloudRegion getDatastorageCloudRegionOrDefault(DataStorageVO dataStorageVO) {
        Long dataStorageRegionId = dataStorageVO.getFileShareMountId() != null
                ? fileShareMountManager.load(dataStorageVO.getFileShareMountId()).getRegionId()
                : dataStorageVO.getRegionId();

        return cloudRegionManager.loadOrDefault(dataStorageRegionId);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public AbstractDataStorage delete(Long id, boolean proceedOnCloud) {
        AbstractDataStorage dataStorage = load(id);

        validateStorageIsNotUsedAsDefault(id, roleManager.loadRolesByDefaultStorage(id));
        validateStorageIsNotUsedAsDefault(id, userManager.loadUsersByDeafultStorage(id));

        if (proceedOnCloud) {
            try {
                storageProviderManager.deleteBucket(dataStorage);
            } catch (DataStorageException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
        dataStorageDao.deleteDataStorage(id);
        return dataStorage;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateLocks(List<Long> storageIds, boolean isLocked) {
        dataStorageDao.updateLocks(storageIds, isLocked);
    }

    public List<AbstractDataStorage> loadRootDataStorages() {
        return dataStorageDao.loadRootDataStorages();
    }

    public DataStorageListing getDataStorageItems(final Long dataStorageId,
            final String path, Boolean showVersion, Integer pageSize, String marker) {
        AbstractDataStorage dataStorage = load(dataStorageId);
        if (showVersion) {
            Assert.isTrue(dataStorage.isVersioningEnabled(), messageHelper
                    .getMessage(MessageConstants.ERROR_DATASTORAGE_VERSIONING_REQUIRED, dataStorage.getName()));
        }
        Assert.isTrue(pageSize == null || pageSize > 0,
                messageHelper.getMessage(MessageConstants.ERROR_PAGE_SIZE));
        return storageProviderManager.getItems(dataStorage, path, showVersion, pageSize, marker);
    }

    public void restoreVersion(Long id, String path, String version) throws DataStorageException {
        Assert.notNull(path, "Path is required to restore file version");
        Assert.notNull(version, "Version is required to restore file version");
        AbstractDataStorage dataStorage = load(id);
        if (!dataStorage.isVersioningEnabled()) {
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_VERSIONING_REQUIRED, dataStorage.getName()));
        }
        storageProviderManager.restoreFileVersion(dataStorage, path, version);
    }

    public DataStorageDownloadFileUrl generateDataStorageItemUrl(final Long dataStorageId,
            final String path, String version) {
        AbstractDataStorage dataStorage = load(dataStorageId);
        if (StringUtils.isNotBlank(version)) {
            Assert.isTrue(dataStorage.isVersioningEnabled(), messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_VERSIONING_REQUIRED, dataStorage.getName()));
        }
        return storageProviderManager.generateDownloadURL(dataStorage, path, version);
    }

    public List<DataStorageDownloadFileUrl> generateDataStorageItemUrl(final Long dataStorageId,
                                                                       final List<String> paths) {
        AbstractDataStorage dataStorage = load(dataStorageId);
        List<DataStorageDownloadFileUrl> urls = new ArrayList<>();
        if (paths == null) {
            return urls;
        }
        paths.forEach(path -> urls.add(storageProviderManager.generateDownloadURL(dataStorage, path, null)));
        return urls;
    }

    public DataStorageDownloadFileUrl generateDataStorageItemUploadUrl(Long id, String path) {
        AbstractDataStorage dataStorage = load(id);
        return storageProviderManager.generateDataStorageItemUploadUrl(dataStorage, path);
    }

    public List<DataStorageDownloadFileUrl> generateDataStorageItemUploadUrl(Long id, List<String> paths) {
        AbstractDataStorage dataStorage = load(id);
        List<DataStorageDownloadFileUrl> urls = new ArrayList<>();
        if (paths == null) {
            return urls;
        }
        paths.forEach(path -> urls.add(storageProviderManager.generateDataStorageItemUploadUrl(dataStorage, path)));
        return urls;
    }

    public void analyzePipelineRunsParameters(List<PipelineRun> pipelineRuns) {
        List<AbstractDataStorage> dataStorages = getDataStorages();
        pipelineRuns.forEach(pipelineRun -> {
            if (pipelineRun.getPipelineRunParameters() == null) {
                return;
            }
            pipelineRun.getPipelineRunParameters().forEach(pipelineRunParameter -> {
                List<DataStorageLink> links = new ArrayList<>();
                for (AbstractDataStorage dataStorage : dataStorages) {
                    String value = StringUtils.isNotBlank(pipelineRunParameter.getResolvedValue()) ?
                            pipelineRunParameter.getResolvedValue() : pipelineRunParameter.getValue();
                    List<DataStorageLink> dataStorageLinks = getLinks(dataStorage, value);
                    if (!dataStorageLinks.isEmpty()) {
                        links.addAll(dataStorageLinks);
                    }
                }
                if (!links.isEmpty()) {
                    pipelineRunParameter.setDataStorageLinks(links);
                }
            });
        });
    }

    public void analyzePaths(List<PipeConfValue> values) {
        if (CollectionUtils.isEmpty(values)) {
            return;
        }
        List<AbstractDataStorage> dataStorages = getDataStorages();
        values.forEach(value -> {
            List<DataStorageLink> links = new ArrayList<>();
            for (AbstractDataStorage dataStorage : dataStorages) {
                List<DataStorageLink> dataStorageLinks = getLinks(dataStorage, value.getValue());
                if (!dataStorageLinks.isEmpty()) {
                    links.addAll(dataStorageLinks);
                }
            }
            if (!links.isEmpty()) {
                value.setDataStorageLinks(links);
            }
        });
    }

    public DataStorageFile createDataStorageFile(final Long dataStorageId,
            String path,
            byte[] contents)
            throws DataStorageException {
        AbstractDataStorage dataStorage = load(dataStorageId);
        return storageProviderManager.createFile(dataStorage, path, contents);
    }

    public DataStorageFile createDataStorageFile(final Long dataStorageId,
                                                 String folder,
                                                 final String name,
                                                 byte[] contents)
            throws DataStorageException {
        AbstractDataStorage dataStorage = load(dataStorageId);
        String path = getRelativePath(folder, name, dataStorage);
        return storageProviderManager.createFile(dataStorage, path, contents);
    }

    public String buildFullStoragePath(AbstractDataStorage dataStorage, String name) {
        return storageProviderManager.buildFullStoragePath(dataStorage, name);
    }

    public boolean checkExistence(AbstractDataStorage dataStorage) {
        checkDatastorageDoesntExist(dataStorage.getName(), dataStorage.getPath());
        return storageProviderManager.checkStorage(dataStorage);
    }

    public AbstractDataStorage convertToDataStorage(DataStorageVO dataStorageVO) {
        final AbstractCloudRegion cloudRegion = cloudRegionManager.loadOrDefault(dataStorageVO.getId());
        return dataStorageFactory.convertToDataStorage(dataStorageVO, cloudRegion.getProvider());
    }

    // TODO: can be replaced with String path = StringUtils.isEmpty(folderPath) ? name : Paths.get(folderPath, name);
    private String getRelativePath(String folder, String name, AbstractDataStorage dataStorage) {
        String path = name;
        String folderPath = folder;
        if (!StringUtils.isEmpty(folderPath)) {
            if (!folderPath.endsWith(dataStorage.getDelimiter())) {
                folderPath += dataStorage.getDelimiter();
            }
            path = folderPath + name;
        }
        return path;
    }

    public DataStorageFile createDataStorageFile(final Long dataStorageId, final String path, final String name,
                                      InputStream contentStream) {
        AbstractDataStorage dataStorage = load(dataStorageId);
        String newFilePath = getRelativePath(path, name, dataStorage);
        return storageProviderManager.createFile(dataStorage, newFilePath, contentStream);
    }

    public List<AbstractDataStorageItem> updateDataStorageItems(final Long dataStorageId,
            List<UpdateDataStorageItemVO> list)
            throws DataStorageException{
        AbstractDataStorage dataStorage = load(dataStorageId);
        List<AbstractDataStorageItem> updatedItems = new ArrayList<>();
        for (UpdateDataStorageItemVO item : list) {
            updatedItems.add(updateDataStorageItem(dataStorage, item));
        }
        return updatedItems;
    }

    public int deleteDataStorageItems(final Long dataStorageId, List<UpdateDataStorageItemVO> list,
            Boolean totally)
            throws DataStorageException{
        AbstractDataStorage dataStorage = load(dataStorageId);
        if (totally || list.stream().anyMatch(item -> StringUtils.isNotBlank(item.getVersion()))) {
            Assert.isTrue(dataStorage.isVersioningEnabled(), messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_VERSIONING_REQUIRED, dataStorage.getName()));
        }
        for (UpdateDataStorageItemVO item : list) {
            deleteDataStorageItem(dataStorage, item, totally);
        }
        return list.size();
    }

    public DataStorageItemContent getDataStorageItemContent(Long id, String path, String version) {
        AbstractDataStorage dataStorage = load(id);
        checkDataStorageVersioning(dataStorage, version);
        return storageProviderManager.getFile(dataStorage, path, version);
    }

    public Map<String, String> loadDataStorageObjectTags(Long id, String path, String version) {
        AbstractDataStorage dataStorage = load(id);
        checkDataStorageVersioning(dataStorage, version);
        return storageProviderManager.listObjectTags(dataStorage, path, version);
    }

    public Map<String, String> deleteDataStorageObjectTags(Long id, String path, Set<String> tags, String version) {
        AbstractDataStorage dataStorage = load(id);
        checkDataStorageVersioning(dataStorage, version);
        return storageProviderManager.deleteObjectTags(dataStorage, path, tags, version);
    }

    public AbstractDataStorageItem getDataStorageItemWithTags(final Long dataStorageId, final String path,
            Boolean showVersion) {
        List<AbstractDataStorageItem> dataStorageItems = getDataStorageItems(dataStorageId, path, showVersion,
                null, null).getResults();
        if (CollectionUtils.isEmpty(dataStorageItems)) {
            return null;
        }
        DataStorageFile dataStorageFile = (DataStorageFile) dataStorageItems.get(0);
        if (MapUtils.isEmpty(dataStorageFile.getVersions())) {
            dataStorageFile.setTags(loadDataStorageObjectTags(dataStorageId, path, null));
        } else {
            dataStorageFile
                    .getVersions()
                    .forEach((version, item) -> item.setTags(loadDataStorageObjectTags(dataStorageId, path, version)));
        }
        return dataStorageFile;
    }

    public Map<String, String> updateDataStorageObjectTags(Long id, String path, Map<String, String> tagsToAdd,
            String version, Boolean rewrite) {
        AbstractDataStorage dataStorage = load(id);
        checkDataStorageVersioning(dataStorage, version);
        Map<String, String> resultingTags = new HashMap<>();
        if (!rewrite) {
            resultingTags = storageProviderManager.listObjectTags(dataStorage, path, version);
        }
        resultingTags.putAll(tagsToAdd);
        return storageProviderManager.updateObjectTags(dataStorage, path, resultingTags, version);
    }

    public DataStorageStreamingContent getStreamingContent(long dataStorageId, String path, String version) {
        AbstractDataStorage dataStorage = load(dataStorageId);
        return storageProviderManager.getFileStream(dataStorage, path, version);
    }

    public String generateSharedUrlForStorage(long dataStorageId) {
        Assert.isTrue(isSharedDataStorage(dataStorageId),
                      messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_IS_NOT_SHARED, dataStorageId));
        String baseShareUrl = preferenceManager.getStringPreference(SystemPreferences.BASE_API_SHARED.getKey());
        Assert.notNull(baseShareUrl, messageHelper.getMessage(MessageConstants.ERROR_SHARED_ROOT_URL_IS_NOT_SET));
        return String.format(baseShareUrl, dataStorageId);
    }

    public boolean isSharedDataStorage(long dataStorageId) {
        return load(dataStorageId).isShared();
    }

    public String adjustStoragePath(final String name, final DataStorageType storageType) {
        String prefix = preferenceManager.getPreference(SystemPreferences.STORAGE_OBJECT_PREFIX);
        if (storageType != DataStorageType.S3 || StringUtils.isBlank(prefix)) {
            return name;
        }
        return prefix + name;
    }

    private void assertDataStorageMountPoint(DataStorageVO dataStorageVO) {
        // if mount point is empty we don't need to check anything
        if (StringUtils.isBlank(dataStorageVO.getMountPoint())) {
            return;
        }

        String mountPoint = Paths.get(dataStorageVO.getMountPoint()).toString();

        AntPathMatcher matcher = new AntPathMatcher();
        boolean isPathAcceptable = Arrays.stream(
                preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_NFS_MOUNT_BLACK_LIST).split(",")
        ).noneMatch(p -> matcher.match(p, mountPoint));

        Assert.isTrue(isPathAcceptable,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_FORBIDDEN_MOUNT_POINT,
                        dataStorageVO.getPath(), dataStorageVO.getMountPoint()));
    }

    private DataStorageFolder moveDataStorageFolder(final AbstractDataStorage dataStorage,
                                                   final String oldPath,
                                                   final String newPath)
            throws DataStorageException {
        return storageProviderManager.moveFolder(dataStorage, oldPath, newPath);
    }

    private DataStorageFile moveDataStorageFile(final AbstractDataStorage dataStorage,
                                               final String oldPath,
                                               final String newPath)
            throws DataStorageException {
        return storageProviderManager.moveFile(dataStorage, oldPath, newPath);
    }

    private void deleteDataStorageFolder(final AbstractDataStorage dataStorage, final String path,
            Boolean totally)
            throws DataStorageException {
        storageProviderManager.deleteFolder(dataStorage, path, totally);
    }

    private void deleteDataStorageFile(final AbstractDataStorage dataStorage, final String path,
            String version, Boolean totally)
            throws DataStorageException {
        storageProviderManager.deleteFile(dataStorage, path, version, totally);
    }

    private void deleteDataStorageItem(final AbstractDataStorage dataStorage, UpdateDataStorageItemVO item,
            Boolean totally)
            throws DataStorageException{
        if (item.getType() == DataStorageItemType.Folder) {
            deleteDataStorageFolder(dataStorage, item.getPath(), totally);
        } else {
            deleteDataStorageFile(dataStorage, item.getPath(), item.getVersion(), totally);
        }
    }

    private AbstractDataStorage updateDataStorageObject(AbstractDataStorage dataStorage,
            DataStorageVO dataStorageVO) {
        if (!StringUtils.isEmpty(dataStorageVO.getName())) {
            String name = dataStorageVO.getName().trim();
            dataStorageVO.setName(name);

            if (!dataStorage.getName().equals(name)) {
                AbstractDataStorage loadedStorage = dataStorageDao.loadDataStorageByNameOrPath(name, name);
                // if we found a datastorage with such a name or path
                // lets check if path is equal to name and allow it
                if (loadedStorage != null && !loadedStorage.getPath().equals(name)) {
                    throw new IllegalArgumentException(
                            messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_ALREADY_EXIST, name, name));
                }
            }

            dataStorage.setName(dataStorageVO.getName());

        }

        dataStorage.setParentFolderId(dataStorageVO.getParentFolderId());
        if (dataStorageVO.getParentFolderId() != null) {
            dataStorage.setParent(folderManager.load(dataStorageVO.getParentFolderId()));
        }

        String newDescription = dataStorageVO.getDescription();
        if (newDescription != null) {
            dataStorage.setDescription(newDescription);
        }

        dataStorage.setMountPoint(dataStorageVO.getMountPoint());
        dataStorage.setMountOptions(dataStorageVO.getMountOptions());
        if (StringUtils.isBlank(dataStorage.getMountOptions())) {
            dataStorage.setMountOptions(
                storageProviderManager.getStorageProvider(dataStorage).getDefaultMountOptions(dataStorage));
        }

        return dataStorage;
    }

    private AbstractDataStorage updateStoragePolicy(AbstractDataStorage dataStorage, DataStorageVO dataStorageVO) {
        verifyStoragePolicy(dataStorageVO.getStoragePolicy());
        StoragePolicy policy = dataStorageVO.getStoragePolicy() == null ? new StoragePolicy() :
                dataStorageVO.getStoragePolicy();
        dataStorage.setStoragePolicy(policy);
        return dataStorage;
    }

    private void checkDatastorageDoesntExist(String name, String path) {
        String usePath = StringUtils.isEmpty(path) ? name : path;
        Assert.isNull(dataStorageDao.loadDataStorageByNameOrPath(name, usePath),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_ALREADY_EXIST,
                        name, usePath));
    }

    private void verifyStoragePolicy(StoragePolicy policy) {
        if (policy == null) {
            return;
        }
        Integer stsDuration = policy.getShortTermStorageDuration();
        Integer ltsDuration = policy.getLongTermStorageDuration();
        Assert.isTrue(!(stsDuration != null && ltsDuration == null),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_RULE_STS_OR_LTS_REQUIRED));
        Assert.isTrue(stsDuration == null || stsDuration > 0,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_ILLEGAL_DURATION, stsDuration));
        Assert.isTrue(ltsDuration == null || ltsDuration > 0,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_ILLEGAL_DURATION, ltsDuration));
        Assert.isTrue(!(stsDuration != null && ltsDuration < stsDuration),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_ILLEGAL_DURATION_COMBINATION,
                        stsDuration, ltsDuration));
        Assert.isTrue(policy.getBackupDuration() == null || policy.getBackupDuration() > 0,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_ILLEGAL_DURATION,
                        policy.getBackupDuration()));
    }

    private DataStorageFolder createDataStorageFolder(final AbstractDataStorage storage, final String path)
            throws DataStorageException {
        return storageProviderManager.createFolder(storage, path);
    }

    private DataStorageFile createDataStorageFile(final AbstractDataStorage storage,
            final String path,
            byte[] contents)
            throws DataStorageException {
        return storageProviderManager.createFile(storage, path, contents);
    }

    private AbstractDataStorageItem updateDataStorageItem(final AbstractDataStorage storage,
            UpdateDataStorageItemVO item)
            throws DataStorageException{
        AbstractDataStorageItem result = null;
        switch (item.getType()) {
            case Folder: result = updateFolderItem(storage, item); break;
            case File: result = updateFileItem(storage, item); break;
            default: break;
        }
        return result;
    }

    private DataStorageFolder updateFolderItem(final AbstractDataStorage storage,
            UpdateDataStorageItemVO item)
            throws DataStorageException{
        DataStorageFolder folder = null;
        switch (item.getAction()) {
            case Create: folder = createDataStorageFolder(storage, item.getPath()); break;
            case Move: folder = moveDataStorageFolder(storage, item.getOldPath(), item.getPath()); break;
            default: break;
        }
        return folder;
    }

    private DataStorageFile updateFileItem(final AbstractDataStorage storage,
            UpdateDataStorageItemVO item)
            throws DataStorageException{
        DataStorageFile file = null;
        switch (item.getAction()) {
            case Create: file = createDataStorageFile(storage, item.getPath(), item.getContents()); break;
            case Move: file = moveDataStorageFile(storage, item.getOldPath(), item.getPath()); break;
            default: break;
        }
        return file;
    }

    private void checkDataStorageVersioning(AbstractDataStorage dataStorage, String version) {
        if (!StringUtils.isEmpty(version)) {
            Assert.isTrue(dataStorage.isVersioningEnabled(), messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_VERSIONING_REQUIRED, dataStorage.getName()));
        }
    }

    private List<DataStorageLink> getLinks(AbstractDataStorage dataStorage, String paramValue) {
        if (StringUtils.isBlank(paramValue)) {
            return Collections.emptyList();
        }
        final String mask = String.format("%s%s", dataStorage.getPathMask(), ProviderUtils.DELIMITER);
        List<DataStorageLink> links = new ArrayList<>();
        for (String path : paramValue.split("[,;]")) {
            if (path.toLowerCase().trim().startsWith(mask.toLowerCase())) {
                DataStorageLink dataStorageLink = new DataStorageLink();
                dataStorageLink.setAbsolutePath(path.trim());
                dataStorageLink.setDataStorageId(dataStorage.getId());
                String relativePath = path.trim().substring(mask.length());
                if (relativePath.startsWith(ProviderUtils.DELIMITER)) {
                    relativePath = relativePath.substring(1);
                }
                String[] parts = relativePath.split(ProviderUtils.DELIMITER);
                final String lastPart = parts[parts.length - 1];
                if (lastPart.contains(".")) {
                    String newPath = "";
                    for (int i = 0; i < parts.length - 1; i++) {
                        newPath = newPath.concat(parts[i] + ProviderUtils.DELIMITER);
                    }
                    if (newPath.endsWith(ProviderUtils.DELIMITER)) {
                        newPath = newPath.substring(0, newPath.length() - 1);
                    }
                    dataStorageLink.setPath(newPath);
                } else {
                    dataStorageLink.setPath(relativePath);
                }
                links.add(dataStorageLink);
            }
        }
        return links;
    }

    private void validateStorageIsNotUsedAsDefault(final Long storageId,
                                                   final Collection<? extends StorageContainer> values) {
        Assert.isTrue(CollectionUtils.isEmpty(values),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_USED_AS_DEFAULT, storageId,
                        values.stream().findFirst().map(v -> v.getClass().getSimpleName()).orElse("CLASS"),
                        values.stream().map(v -> String.valueOf(v.getId())).collect(Collectors.joining(","))));
    }

}
