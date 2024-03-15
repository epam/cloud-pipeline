/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.MetadataVO;
import com.epam.pipeline.controller.vo.data.storage.UpdateDataStorageItemVO;
import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreAction;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestorePathType;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.SecuredEntityWithAction;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageFactory;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.ActionStatus;
import com.epam.pipeline.entity.datastorage.ContentDisposition;
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
import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.datastorage.NFSStorageMountStatus;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.datastorage.StorageServiceType;
import com.epam.pipeline.entity.datastorage.StorageUsage;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.azure.AzureBlobStorage;
import com.epam.pipeline.entity.datastorage.gcp.GSBucketStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.datastorage.tag.DataStorageObject;
import com.epam.pipeline.entity.datastorage.tag.DataStorageObjectSearchByTagRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTag;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagSearchResult;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.ToolFingerprint;
import com.epam.pipeline.entity.pipeline.ToolVersionFingerprint;
import com.epam.pipeline.entity.pipeline.run.parameter.DataStorageLink;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.MountStorageRule;
import com.epam.pipeline.entity.search.StorageFileSearchMask;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.templates.DataStorageTemplate;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.StorageContainer;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.exception.ObjectNotFoundException;
import com.epam.pipeline.manager.datastorage.lifecycle.DataStorageLifecycleRestoredListingContainer;
import com.epam.pipeline.manager.datastorage.lifecycle.DataStorageLifecycleManager;
import com.epam.pipeline.manager.datastorage.lifecycle.DataStorageLifecycleRestoreManager;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.datastorage.tag.DataStorageTagProviderManager;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.metadata.parser.EntityTypeField;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.pipeline.FolderTemplateManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.search.SearchManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.security.SecuredEntityManager;
import com.epam.pipeline.manager.security.acl.AclSync;
import com.epam.pipeline.manager.security.storage.StoragePermissionManager;
import com.epam.pipeline.manager.user.RoleManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.utils.PipelineStringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
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
import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;


@Service
@AclSync
@Slf4j
public class DataStorageManager implements SecuredEntityManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataStorageManager.class);
    private static final String DEFAULT_USER_STORAGE_NAME_TEMPLATE = "@@-home";
    private static final String DEFAULT_USER_STORAGE_DESCRIPTION_TEMPLATE = "Home folder for user @@";
    public static final String DAV_MOUNT_TAG = "dav-mount";
    private static final String SHARED_STORAGE_SUFFIX = "share";
    private static final String READ_PERMISSION = "READ";

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

    @Autowired
    private Executor dataStoragePathExecutor;

    @Autowired
    private SearchManager searchManager;

    @Autowired
    private DataStoragePathLoader pathLoader;

    @Autowired
    private GrantPermissionManager permissionManager;

    @Autowired
    private DataStorageTagProviderManager tagProviderManager;

    @Autowired
    private ToolVersionManager toolVersionManager;

    @Autowired
    private ToolManager toolManager;

    @Autowired
    private DataStorageLifecycleManager dataStorageLifecycleManager;

    @Autowired
    private DataStorageLifecycleRestoreManager storageLifecycleRestoreManager;

    @Autowired
    private StoragePermissionManager storagePermissionManager;

    private AbstractDataStorageFactory dataStorageFactory =
            AbstractDataStorageFactory.getDefaultDataStorageFactory();

    public List<AbstractDataStorage> getDataStorages() {
        return dataStorageDao.loadAllDataStorages();
    }

    public List<AbstractDataStorage> getDataStoragesWithToolsToMount() {
        return dataStorageDao.loadAllDataStoragesWithToolsToMount();
    }

    public List<DataStorageWithShareMount> getDataStoragesWithShareMountObject(final Long fromRegionId) {
        final List<AbstractDataStorage> storages = getDataStoragesWithToolsToMount();
        storagePermissionManager.filterStorage(storages, Arrays.asList("READ", "WRITE"), false);
        return getDataStoragesWithShareMountObject(fromRegionId, storages);
    }

    public List<DataStorageWithShareMount> getDataStoragesWithShareMountObject(
            final Long fromRegionId, final List<AbstractDataStorage> storages) {
        if (CollectionUtils.isEmpty(storages)) {
            return Collections.emptyList();
        }
        final AbstractCloudRegion fromRegion = Optional.ofNullable(fromRegionId)
                .map(cloudRegionManager::load).orElse(null);
        final Map<Long, ? extends AbstractCloudRegion> regions = ListUtils.emptyIfNull(cloudRegionManager.loadAll())
                .stream()
                .collect(Collectors.toMap(BaseEntity::getId, Function.identity()));
        final Map<Long, FileShareMount> fsMounts = ListUtils.emptyIfNull(fileShareMountManager.loadAll())
                .stream().collect(Collectors.toMap(FileShareMount::getId, Function.identity()));
        return storages
                .stream()
                .filter(storage -> !storage.isSensitive())
                .map(storage -> new DataStorageWithShareMount(storage, findFileShareMount(storage, fsMounts)
                        .orElse(null)))
                .filter(storage -> Objects.isNull(fromRegion) || isStorageMountAllowed(storage, fromRegion, regions))
                .collect(Collectors.toList());
    }

    @Override
    public AbstractDataStorage load(final Long id) {
        AbstractDataStorage dbDataStorage = dataStorageDao.loadDataStorage(id);
        Assert.notNull(dbDataStorage, messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_NOT_FOUND, id));
        dbDataStorage.setHasMetadata(this.metadataManager.hasMetadata(new EntityVO(id, AclClass.DATA_STORAGE)));
        return dbDataStorage;
    }

    public boolean exists(final Long id) {
        return dataStorageDao.loadDataStorage(id) != null;
    }

    public List<AbstractDataStorage> getDatastoragesByIds(final List<Long> ids) {
        if(CollectionUtils.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return dataStorageDao.loadDataStoragesByIds(ids);
    }

    public List<AbstractDataStorage> getDatastoragesByPaths(final List<String> paths) {
        if (CollectionUtils.isEmpty(paths)) {
            return Collections.emptyList();
        }
        return dataStorageDao.loadDataStoragesByPaths(paths);
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

    public AbstractDataStorage loadByPathOrId(final String identifier) {
        return pathLoader.loadDataStorageByPathOrId(identifier);
    }

    public List<AbstractDataStorage> loadAllByPath(final String identifier) {
        Assert.isTrue(StringUtils.isNotBlank(identifier),
                      messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_IS_EMPTY));
        return dataStorageDao.loadDataStorageByNameOrPath(identifier, identifier, true);
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
            dataStorage = dataStorageDao.loadDataStorageByNameOrPath(pathWithName, pathWithName);
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
        assertToolsToMount(dataStorageVO);
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
    public AbstractDataStorage updateMountStatus(final AbstractDataStorage storage,
                                                 final NFSStorageMountStatus status) {
        final NFSDataStorage existingStorage = Optional.of(load(storage.getId()))
            .filter(dataStorage -> DataStorageType.NFS.equals(dataStorage.getType()))
            .map(NFSDataStorage.class::cast)
            .orElseThrow(() -> new IllegalArgumentException(
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_NOT_SUPPORTED,
                                         storage.getId(), storage.getType())));
        if (!existingStorage.getMountStatus().equals(status)) {
            dataStorageDao.updateDataStorageMountStatus(existingStorage.getId(), status);
            existingStorage.setMountStatus(status);
        }
        return existingStorage;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public SecuredEntityWithAction<AbstractDataStorage> create(final DataStorageVO dataStorageVO,
                                                               final Boolean proceedOnCloud,
                                                               final Boolean checkExistence,
                                                               final boolean replaceStoragePath) {
        return create(dataStorageVO, proceedOnCloud, checkExistence, replaceStoragePath, false);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public SecuredEntityWithAction<AbstractDataStorage> create(final DataStorageVO dataStorageVO,
                                                               final Boolean proceedOnCloud,
                                                               final Boolean checkExistence,
                                                               final boolean replaceStoragePath,
                                                               final boolean skipPolicy)
            throws DataStorageException {
        dataStorageVO.setName(createStorageNameIfRequired(dataStorageVO));
        Assert.isTrue(!StringUtils.isEmpty(dataStorageVO.getName()),
                messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY, "name"));

        if (dataStorageVO.getServiceType() == StorageServiceType.FILE_SHARE) {
            Assert.notNull(dataStorageVO.getFileShareMountId(),
                    messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY,
                           "fileShareMountId"));
        }

        assertDataStorageMountPoint(dataStorageVO);
        assertToolsToMount(dataStorageVO);


        final AbstractCloudRegion storageRegion = getDatastorageCloudRegionOrDefault(dataStorageVO);
        dataStorageVO.setRegionId(storageRegion.getId());
        final DataStorageType dataStorageType =
                Optional.ofNullable(dataStorageVO.getType()).orElseGet(() ->
                        DataStorageType.fromServiceType(
                                storageRegion.getProvider(), dataStorageVO.getServiceType()
                        )
                );
        checkDatastorageDoesntExist(dataStorageVO.getName(), dataStorageVO.getPath(),
                                    dataStorageVO.getSourceStorageId() != null, dataStorageType);
        verifyStoragePolicy(dataStorageVO.getStoragePolicy());
        validateMirroringParameters(dataStorageVO);

        AbstractDataStorage dataStorage = dataStorageFactory.convertToDataStorage(dataStorageVO,
                storageRegion.getProvider());
        final SecuredEntityWithAction<AbstractDataStorage> createdStorage = new SecuredEntityWithAction<>();
        createdStorage.setEntity(dataStorage);
        if (StringUtils.isBlank(dataStorage.getMountOptions())) {
            dataStorage.setMountOptions(
                storageProviderManager.getStorageProvider(dataStorage)
                    .getDefaultMountOptions(getLinkOrStorage(dataStorage)));
        }

        if (proceedOnCloud) {
            if (replaceStoragePath) {
                dataStorage.setPath(adjustStoragePath(dataStorage.getPath(), dataStorage.getType()));
            }
            String created = storageProviderManager.createBucket(dataStorage);
            dataStorage.setPath(created);
            if (skipPolicy) {
                createdStorage.setActionStatus(ActionStatus.success());
            } else {
                createdStorage.setActionStatus(storageProviderManager.postCreationProcessing(dataStorage));
            }
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

        if (!skipPolicy && dataStorage.isPolicySupported()) {
            storageProviderManager.applyStoragePolicy(dataStorage);
        }

        dataStorageDao.createDataStorage(dataStorage);

        return createdStorage;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Long> tryInitUserDefaultStorage(final PipelineUser user) {
        final boolean shouldCreateDefaultHome =
            preferenceManager.getPreference(SystemPreferences.DEFAULT_USER_DATA_STORAGE_ENABLED);
        return shouldCreateDefaultHome
               ? createDefaultStorageForUser(user.getUserName()).map(AbstractDataStorage::getId)
               : Optional.empty();
    }

    public Optional<AbstractDataStorage> createDefaultStorageForUser(final String userName) {
        final DataStorageTemplate dataStorageTemplate =
            Optional
                .ofNullable(preferenceManager.getSystemPreference(SystemPreferences.DEFAULT_USER_DATA_STORAGE_TEMPLATE))
                .map(preference -> preference.get(Function.identity()))
                .map(templateJson -> JsonMapper.<DataStorageTemplate>
                    parseData(replaceInTemplate(templateJson, userName), new TypeReference<DataStorageTemplate>() {}))
                .orElseGet(() -> {
                    final DataStorageVO storageVO = new DataStorageVO();
                    storageVO.setName(replaceInTemplate(DEFAULT_USER_STORAGE_NAME_TEMPLATE, userName));
                    storageVO.setDescription(replaceInTemplate(DEFAULT_USER_STORAGE_DESCRIPTION_TEMPLATE, userName));
                    return new DataStorageTemplate(storageVO, Collections.emptyList(), Collections.emptyMap());
                });
        final DataStorageVO dataStorageDetails = dataStorageTemplate.getDatastorage();
        if (dataStorageDetails.getPath() == null) {
            dataStorageDetails.setPath(adjustStoragePath(dataStorageDetails.getName(), null));
        }
        final AbstractDataStorage correspondingExistingStorage =
            dataStorageDao.loadDataStorageByNameOrPath(dataStorageDetails.getName(), dataStorageDetails.getPath());
        if (correspondingExistingStorage != null) {
            log.warn(messageHelper.getMessage(MessageConstants.DEFAULT_STORAGE_CREATION_CORRESPONDING_EXISTS,
                                              dataStorageDetails.getPath(),
                                              userName,
                                              correspondingExistingStorage.getId()));
            return Optional.empty();
        }
        if (!folderManager.exists(dataStorageDetails.getParentFolderId())) {
            dataStorageDetails.setParentFolderId(null);
        }
        if (dataStorageDetails.getServiceType() == null) {
            dataStorageDetails.setServiceType(StorageServiceType.OBJECT_STORAGE);
        }
        final AbstractDataStorage dataStorage = create(dataStorageDetails, true, true, true).getEntity();
        metadataManager
            .updateEntityMetadata(dataStorageTemplate.getMetadata(), dataStorage.getId(), AclClass.DATA_STORAGE);
        permissionManager
            .setPermissionsToEntity(dataStorageTemplate.getPermissions(), dataStorage.getId(), AclClass.DATA_STORAGE);
        return Optional.of(dataStorage);
    }

    private AbstractCloudRegion getDatastorageCloudRegionOrDefault(DataStorageVO dataStorageVO) {
        Long dataStorageRegionId = dataStorageVO.getFileShareMountId() != null
                ? fileShareMountManager.load(dataStorageVO.getFileShareMountId()).getRegionId()
                : dataStorageVO.getRegionId();

        return cloudRegionManager.loadOrDefault(dataStorageRegionId);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public AbstractDataStorage delete(Long id, boolean proceedOnCloud) {
        final AbstractDataStorage dataStorage = load(id);

        Assert.isTrue(Objects.isNull(dataStorage.getSourceStorageId()) || !proceedOnCloud,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_MIRROR_DELETION));

        validateStorageIsNotUsedAsDefault(id, roleManager.loadRolesByDefaultStorage(id));
        validateStorageIsNotUsedAsDefault(id, userManager.loadUsersByDeafultStorage(id));

        if (proceedOnCloud) {
            try {
                storageProviderManager.deleteBucket(dataStorage);
            } catch (DataStorageException e) {
                LOGGER.error(e.getMessage(), e);
            }
            tagProviderManager.deleteStorageTags(dataStorage);
        }
        dataStorageLifecycleManager.deleteStorageLifecyclePolicyRules(id);
        storageLifecycleRestoreManager.deleteRestoreActions(id);
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

    public DataStorageListing getDataStorageItems(final Long dataStorageId, final String path,
                                                  final Boolean showVersion, final Integer pageSize,
                                                  final String marker, final boolean showArchived) {
        AbstractDataStorage dataStorage = load(dataStorageId);
        if (showVersion) {
            Assert.isTrue(dataStorage.isVersioningEnabled(), messageHelper
                    .getMessage(MessageConstants.ERROR_DATASTORAGE_VERSIONING_REQUIRED, dataStorage.getName()));
        }
        Assert.isTrue(pageSize == null || pageSize > 0,
                messageHelper.getMessage(MessageConstants.ERROR_PAGE_SIZE));
        if (!showArchived && DataStorageType.S3.equals(dataStorage.getType())) {
            final DataStorageLifecycleRestoredListingContainer restoredListing = loadRestoredPaths(dataStorage, path);
            return storageProviderManager.getRestoredItems(dataStorage, path, showVersion, pageSize, marker,
                    restoredListing);
        }
        return storageProviderManager.getItems(dataStorage, path, showVersion, pageSize, marker);
    }

    @Transactional
    public void restoreVersion(Long id, String path, String version) throws DataStorageException {
        Assert.notNull(path, "Path is required to restore file version");
        Assert.notNull(version, "Version is required to restore file version");
        final AbstractDataStorage dataStorage = load(id);
        if (!dataStorage.isVersioningEnabled()) {
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_VERSIONING_REQUIRED, dataStorage.getName()));
        }
        storageProviderManager.restoreFileVersion(dataStorage, path, version);
        tagProviderManager.restoreFileTags(dataStorage, path, version);
    }

    public DataStorageDownloadFileUrl generateDataStorageItemUrl(final Long dataStorageId,
            final String path, String version, ContentDisposition contentDisposition) {
        AbstractDataStorage dataStorage = load(dataStorageId);
        if (StringUtils.isNotBlank(version)) {
            Assert.isTrue(dataStorage.isVersioningEnabled(), messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_VERSIONING_REQUIRED, dataStorage.getName()));
        }
        return storageProviderManager.generateDownloadURL(dataStorage, path, version, contentDisposition);
    }

    public List<DataStorageDownloadFileUrl> generateDataStorageItemUrl(final Long dataStorageId,
                                                                       final List<String> paths,
                                                                       final List<String> permissions,
                                                                       final long hours) {
        final AbstractDataStorage dataStorage = load(dataStorageId);
        final List<String> adjustedPermissions = adjustPermissions(permissions);
        final Duration duration = resolveDuration(hours);
        return CollectionUtils.emptyIfNull(paths)
                .stream()
                .map(path -> storageProviderManager.generateUrl(dataStorage, path, adjustedPermissions, duration))
                .collect(Collectors.toList());
    }

    private List<String> adjustPermissions(final List<String> permissions) {
        return Optional.ofNullable(permissions)
                .filter(CollectionUtils::isNotEmpty)
                .orElseGet(() -> Collections.singletonList(READ_PERMISSION));
    }

    private Duration resolveDuration(final long hours) {
        return hours > 0 ? Duration.ofHours(hours) : Duration.ZERO;
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
                    List<DataStorageLink> dataStorageLinks = PathAnalyzer.getLinks(dataStorage, value);
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



    @Transactional
    public DataStorageFile createDataStorageFile(final Long dataStorageId,
                                                 final String path,
                                                 final byte[] contents) throws DataStorageException {
        AbstractDataStorage dataStorage = load(dataStorageId);
        return createDataStorageFile(dataStorage, path, contents);
    }

    @Transactional
    public DataStorageFile createDataStorageFile(final Long dataStorageId,
                                                 final String folder,
                                                 final String name,
                                                 final byte[] contents) throws DataStorageException {
        AbstractDataStorage dataStorage = load(dataStorageId);
        String path = getRelativePath(folder, name, dataStorage);
        return createDataStorageFile(dataStorage, path, contents);
    }

    @Transactional
    public DataStorageFile createDataStorageFile(final Long dataStorageId,
                                                 final String folder,
                                                 final String name,
                                                 final InputStream contentStream) {
        AbstractDataStorage dataStorage = load(dataStorageId);
        String path = getRelativePath(folder, name, dataStorage);
        return createDataStorageFile(dataStorage, path, contentStream);
    }

    public String buildFullStoragePath(AbstractDataStorage dataStorage, String name) {
        return storageProviderManager.buildFullStoragePath(dataStorage, name);
    }

    public boolean checkExistence(AbstractDataStorage dataStorage) {
        checkDatastorageDoesntExist(dataStorage.getName(), dataStorage.getPath(),
                                    dataStorage.getSourceStorageId() != null, dataStorage.getType());
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

    @Transactional
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

    @Transactional
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

    @Transactional
    public Map<String, String> loadDataStorageObjectTags(Long id, String path, String version) {
        final AbstractDataStorage dataStorage = load(id);
        checkDataStorageVersioning(dataStorage, version);
        checkDataStorageObjectExists(dataStorage, path, version);
        return tagProviderManager.loadFileTags(dataStorage, path, version);
    }

    @Transactional
    public Map<String, String> deleteDataStorageObjectTags(Long id, String path, String version,
                                                           Set<String> tags) {
        final AbstractDataStorage dataStorage = load(id);
        checkDataStorageVersioning(dataStorage, version);
        checkDataStorageObjectExists(dataStorage, path, version);
        tagProviderManager.deleteFileTags(dataStorage, path, version, tags);
        return tagProviderManager.loadFileTags(dataStorage, path, version);
    }

    @Transactional
    public AbstractDataStorageItem getDataStorageItemWithTags(final Long dataStorageId,
                                                              final String path,
                                                              final Boolean showVersion,
                                                              final boolean showArchived) {
        final List<AbstractDataStorageItem> dataStorageItems = getDataStorageItems(dataStorageId, path, showVersion,
                null, null, showArchived).getResults();
        if (CollectionUtils.isEmpty(dataStorageItems)) {
            return null;
        }
        final DataStorageFile dataStorageFile = (DataStorageFile) dataStorageItems.get(0);
        final AbstractDataStorage dataStorage = load(dataStorageId);
        if (MapUtils.isEmpty(dataStorageFile.getVersions())) {
            dataStorageFile.setTags(tagProviderManager.loadFileTags(dataStorage, path, null));
        } else {
            dataStorageFile
                    .getVersions()
                    .forEach((version, item) ->
                            item.setTags(tagProviderManager.loadFileTags(dataStorage, path, version)));
        }
        return dataStorageFile;
    }

    public List<DataStorageTagSearchResult> searchDataStorageItemByTag(final DataStorageObjectSearchByTagRequest req) {
        final Set<Long> requestedStorageIds = new HashSet<>(CollectionUtils.emptyIfNull(req.getDatastorageIds()));
        final Map<Long, List<DataStorageTag>> searchTagsResult =
                tagProviderManager.search(getDatastoragesByIds(req.getDatastorageIds()), req.getTags());

        if (MapUtils.isNotEmpty(searchTagsResult)) {
            // by tagProviderManager.search we found tags for datastorage_root, and now we need to map it on
            // actual storages
            final Map<Long, List<AbstractDataStorage>> storagesByRootId = dataStorageDao
                .loadDataStoragesByRootIds(searchTagsResult.keySet()).stream()
                .filter(dataStorage -> checkStoragePermissions(dataStorage, READ_PERMISSION))
                .filter(ds -> CollectionUtils.isEmpty(requestedStorageIds) || requestedStorageIds.contains(ds.getId()))
                .collect(Collectors.groupingBy(AbstractDataStorage::getRootId));

            final Map<Long, List<DataStorageTag>> results = new HashMap<>();
            searchTagsResult.forEach(
                (rootId, tags) -> tags.forEach(
                    rootTag -> storagesByRootId.getOrDefault(rootId, Collections.emptyList())
                        .stream()
                        .filter(dataStorage -> rootTag.getObject().getPath().startsWith(dataStorage.getPrefix()))
                        .max(Comparator.comparingInt(s -> s.getPrefix().length()))
                        .ifPresent(dataStorage -> {
                            final DataStorageTag storageTag = new DataStorageTag(
                                new DataStorageObject(
                                        dataStorage.resolveRelativePath(rootTag.getObject().getPath()),
                                        rootTag.getObject().getVersion()
                                ),
                                rootTag.getKey(), rootTag.getValue()
                            );
                            results.computeIfAbsent(dataStorage.getId(), (id) -> new ArrayList<>()).add(storageTag);
                        })
                )
            );
            return results.entrySet().stream()
                    .map(e -> new DataStorageTagSearchResult(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    @Transactional
    public Map<String, String> updateDataStorageObjectTags(Long id, String path,
                                                           Map<String, String> tagsToAdd, String version,
                                                           Boolean rewrite) {
        final AbstractDataStorage dataStorage = load(id);
        checkDataStorageVersioning(dataStorage, version);
        checkDataStorageObjectExists(dataStorage, path, version);
        return tagProviderManager.updateFileTags(dataStorage, path, version, tagsToAdd, rewrite);
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
        final String prefix = preferenceManager.getPreference(SystemPreferences.STORAGE_OBJECT_PREFIX);
        if (storageType == DataStorageType.NFS || StringUtils.isBlank(prefix)) {
            return name;
        }
        return prefix + name;
    }

    public List<PathDescription> getDataSizes(final List<String> paths) {
        if (CollectionUtils.isEmpty(paths)) {
            return Collections.emptyList();
        }
        final Long timeout = preferenceManager.getPreference(SystemPreferences.STORAGE_LISTING_TIME_LIMIT);
        final Map<String, PathDescription> container = new ConcurrentHashMap<>();
        try {
            CompletableFuture.runAsync(
                () -> getRootPaths(paths)
                        .forEach(path -> computeDataSize(path, container)),
                    dataStoragePathExecutor)
                    .get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return new ArrayList<>(container.values());
    }

    public StorageUsage getStorageUsage(final String id, final String path) {
        final AbstractDataStorage dataStorage = loadByNameOrId(id);
        final Set<String> storageSizeMasks = resolveSizeMasks(loadSizeCalculationMasksMapping(), dataStorage);
        final Set<String> storageClasses = storagePermissionManager.storageArchiveReadPermissions(dataStorage)
                ? dataStorage.getType().getStorageClasses()
                : Collections.singleton(DataStorageType.Constants.STANDARD_STORAGE_CLASS);
        final boolean allowVersions = permissionManager.isOwnerOrAdmin(dataStorage);
        return searchManager.getStorageUsage(dataStorage, path, storageSizeMasks, storageClasses, allowVersions);
    }

    public Map<String, Set<String>> loadSizeCalculationMasksMapping() {
        return Optional.ofNullable(preferenceManager.getPreference(SystemPreferences.STORAGE_QUOTAS_SKIPPED_PATHS))
            .orElse(Collections.emptyList())
            .stream()
            .collect(Collectors.groupingBy(StorageFileSearchMask::getStorageName,
                                           Collector.of(HashSet::new,
                                               (set, mask) -> set.addAll(mask.getHiddenFilePathGlobs()),
                                               (left, right) -> {
                                                   left.addAll(right);
                                                   return left;
                                               })));
    }

    public Set<String> resolveSizeMasks(final Map<String, Set<String>> masksMapping,
                                        final AbstractDataStorage storage) {
        final String storageName = storage.getName();
        final AntPathMatcher matcher = new AntPathMatcher();
        return MapUtils.emptyIfNull(masksMapping).entrySet()
            .stream()
            .filter(e -> matcher.match(e.getKey(), storageName))
            .map(Map.Entry::getValue)
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    public void requestDataStorageDavMount(final Long id, final Long time) {
        log.debug(messageHelper.getMessage(MessageConstants.INFO_DATASTORAGE_DAV_MOUNT_REQUEST, id, time));
        // check that storage exists
        final AbstractDataStorage storage = load(id);
        Assert.isTrue(0 < time,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_DAV_MOUNT_ILLEGAL_TIME));
        final Map<String, PipeConfValue> metadata = Optional.ofNullable(
                metadataManager.loadMetadataItem(storage.getId(), AclClass.DATA_STORAGE)
        ).map(MetadataEntry::getData).orElse(Collections.emptyMap());
        final PipeConfValue davMountTimestamp = metadata.get(DAV_MOUNT_TAG);
        final long now = DateUtils.nowUTC().toEpochSecond(ZoneOffset.UTC);
        if (davMountTimestamp != null) {
            final long timestamp = NumberUtils.isDigits(davMountTimestamp.getValue())
                    ? Long.parseLong(davMountTimestamp.getValue()) : 0;
            final long remain = timestamp - now;
            if (remain > time) {
                throw new IllegalStateException(
                        messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_DAV_MOUNT_ALREADY_MOUNTED,
                        DateUtils.convertSecsToHours(remain), DateUtils.convertSecsToMinOfHour(remain),
                        DateUtils.convertSecsToSecsOfMin(remain)));
            }
        }
        checkDavMountQuotas();
        log.info(messageHelper.getMessage(
                MessageConstants.INFO_DATASTORAGE_DAV_MOUNT_REQUEST_ALLOWED, storage.getId(), time));
        metadataManager.updateMetadataItemKey(
                MetadataVO.builder()
                        .entity(new EntityVO(storage.getId(), AclClass.DATA_STORAGE))
                        .data(Collections.singletonMap(DAV_MOUNT_TAG,
                                new PipeConfValue(EntityTypeField.DEFAULT_TYPE, String.valueOf(now + time))))
                        .build()
        );
    }

    public void callOffDataStorageDavMount(final Long id) {
        // check that storage exists
        final AbstractDataStorage storage = load(id);
        log.info(messageHelper.getMessage(
                MessageConstants.INFO_DATASTORAGE_DAV_MOUNT_REQUEST_CALLED_OFF, storage.getId()));
        metadataManager.deleteMetadataItemKey(new EntityVO(storage.getId(), AclClass.DATA_STORAGE), DAV_MOUNT_TAG);
    }

    private Optional<FileShareMount> findFileShareMount(final AbstractDataStorage storage,
                                                        final Map<Long, FileShareMount> fsMounts) {
        return Optional.ofNullable(storage.getFileShareMountId()).map(fsMounts::get);
    }

    private boolean isStorageMountAllowed(final DataStorageWithShareMount storage,
                                          final AbstractCloudRegion region,
                                          final Map<Long, ? extends AbstractCloudRegion> regions) {
        switch (storage.getStorage().getType().getServiceType()) {
            case OBJECT_STORAGE:
                return isObjectStorageMountAllowed(storage, region, regions);
            case FILE_SHARE:
            default:
                return isFileStorageMountAllowed(storage, region, regions);
        }
    }

    private boolean isObjectStorageMountAllowed(final DataStorageWithShareMount storage,
                                                final AbstractCloudRegion region,
                                                final Map<Long, ? extends AbstractCloudRegion> regions) {
        final AbstractCloudRegion storageRegion = regions.get(getCloudRegionId(storage.getStorage()));
        return isRegionStorageMountAllowed(region, storageRegion, storageRegion.getMountObjectStorageRule());
    }

    private boolean isFileStorageMountAllowed(final DataStorageWithShareMount storage,
                                              final AbstractCloudRegion region,
                                              final Map<Long, ? extends AbstractCloudRegion> regions) {
        final AbstractCloudRegion storageRegion = regions.get(storage.getShareMount().getRegionId());
        return isRegionStorageMountAllowed(region, storageRegion, storageRegion.getMountFileStorageRule());
    }

    private boolean isRegionStorageMountAllowed(final AbstractCloudRegion source,
                                                final AbstractCloudRegion target,
                                                final MountStorageRule rule) {
        switch (rule) {
            case CLOUD:
                return source.getProvider().equals(target.getProvider());
            case ALL:
                return true;
            case NONE:
            default:
                return false;
        }
    }

    private boolean checkStoragePermissions(final AbstractDataStorage dataStorage, final String permission) {
        return permissionManager.storagePermission(dataStorage, permission);
    }

    private Long getCloudRegionId(final AbstractDataStorage dataStorage) {
        if (dataStorage instanceof S3bucketDataStorage) {
            return ((S3bucketDataStorage) dataStorage).getRegionId();
        } else if (dataStorage instanceof GSBucketStorage) {
            return ((GSBucketStorage) dataStorage).getRegionId();
        } else if (dataStorage instanceof AzureBlobStorage) {
            return ((AzureBlobStorage) dataStorage).getRegionId();
        }
        throw new IllegalArgumentException("Unsupported type of DataStorage!");
    }

    private Collection<String> getRootPaths(final List<String> paths) {
        final Set<String> initialPaths = new HashSet<>(paths);
        final List<String> childPaths = initialPaths.stream()
                .map(path -> initialPaths.stream().filter(p -> !p.equals(path) &&
                        p.startsWith(ProviderUtils.withTrailingDelimiter(path))))
                .flatMap(Function.identity())
                .collect(Collectors.toList());
        return CollectionUtils.subtract(initialPaths, childPaths);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void computeDataSize(final String path, final Map<String, PathDescription> container) {
        try {
            final PathDescription pathDescription = PathDescription.builder()
                    .path(path)
                    .completed(false)
                    .size(-1L)
                    .build();
            container.put(path, pathDescription);

            Assert.state(StringUtils.isNotBlank(path), messageHelper
                    .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_IS_EMPTY));
            final URI pathUri = new URI(path);
            final String bucketName = pathUri.getHost();
            Assert.state(StringUtils.isNotBlank(bucketName), messageHelper
                    .getMessage(MessageConstants.ERROR_DATASTORAGE_NAME_IS_EMPTY));
            final String relativePath = ProviderUtils.withoutLeadingDelimiter(pathUri.getPath());

            final AbstractDataStorage dataStorage = loadByNameOrId(bucketName);
            Assert.state(StringUtils.startsWithIgnoreCase(path, dataStorage.getPathMask()),
                    messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_INVALID_SCHEMA, path,
                            dataStorage.getPathMask()));

            pathDescription.setDataStorageId(dataStorage.getId());
            pathDescription.setSize(0L);
            storageProviderManager.getDataSize(dataStorage, relativePath, pathDescription);
        } catch (Exception e) {
            LOGGER.error(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_PATH_PROCCESSING, path, e.getMessage()));
            LOGGER.error(e.getMessage(), e);
        }
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
                                                    final String newPath) throws DataStorageException {
        final DataStorageFolder folder = storageProviderManager.moveFolder(dataStorage, oldPath, newPath);
        tagProviderManager.moveFolderTags(dataStorage, oldPath, newPath);
        return folder;
    }

    private DataStorageFile moveDataStorageFile(final AbstractDataStorage dataStorage,
                                                final String oldPath,
                                                final String newPath) throws DataStorageException {
        final DataStorageFile file = storageProviderManager.moveFile(dataStorage, oldPath, newPath);
        tagProviderManager.moveFileTags(dataStorage, oldPath, newPath, file.getVersion());
        return file;
    }

    private DataStorageFolder copyDataStorageFolder(final AbstractDataStorage dataStorage,
                                                    final String oldPath,
                                                    final String newPath) {
        final DataStorageFolder folder = storageProviderManager.copyFolder(dataStorage, oldPath, newPath);
        tagProviderManager.copyFolderTags(dataStorage, oldPath, newPath);
        return folder;
    }

    private DataStorageFile copyDataStorageFile(final AbstractDataStorage dataStorage,
                                                final String oldPath,
                                                final String newPath) {
        final DataStorageFile file = storageProviderManager.copyFile(dataStorage, oldPath, newPath);
        tagProviderManager.copyFileTags(dataStorage, oldPath, newPath, file.getVersion());
        return file;
    }

    private void deleteDataStorageFolder(final AbstractDataStorage dataStorage,
                                         final String path,
                                         final Boolean totally) throws DataStorageException {
        storageProviderManager.deleteFolder(dataStorage, path, totally);
        tagProviderManager.deleteFolderTags(dataStorage, path, totally);
    }

    private void deleteDataStorageFile(final AbstractDataStorage dataStorage,
                                       final String path,
                                       final String version,
                                       final Boolean totally) throws DataStorageException {
        storageProviderManager.deleteFile(dataStorage, path, version, totally);
        tagProviderManager.deleteFileTags(dataStorage, path, version, totally);
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

        if (dataStorageVO.getMountDisabled() != null) {
            dataStorage.setMountDisabled(dataStorageVO.getMountDisabled());
        }

        if (dataStorageVO.getToolsToMount() != null) {
            dataStorage.setToolsToMount(dataStorageVO.getToolsToMount());
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

    private void checkDatastorageDoesntExist(final String name, final String path,
                                             final boolean isMirror, final DataStorageType storageType) {
        final String usePath = StringUtils.isEmpty(path) ? name : path;
        final List<AbstractDataStorage> matchingStorage =
            dataStorageDao.loadDataStorageByNameOrPath(name, isMirror ? null : usePath, true);
        Assert.isTrue(CollectionUtils.isEmpty(matchingStorage),
                      messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_ALREADY_EXIST, name, path));
        if (storageType.equals(DataStorageType.AWS_OMICS_REF)) {
            final String nameOfExisting = dataStorageDao.loadDataStorageByType(storageType)
                    .stream().findFirst().map(BaseEntity::getName).orElse(null);
            Assert.isNull(
                    nameOfExisting,
                    messageHelper.getMessage(MessageConstants.AWS_OMICS_REFERENCE_STORE_ALREADY_EXISTS, nameOfExisting)
            );
        }
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
                                                  final byte[] contents) throws DataStorageException {
        final DataStorageFile file = storageProviderManager.createFile(storage, path, contents);
        tagProviderManager.createFileTags(storage, path, file.getVersion());
        return file;
    }

    private DataStorageFile createDataStorageFile(final AbstractDataStorage storage, 
                                                  final String path, 
                                                  final InputStream contentStream) {
        final DataStorageFile file = storageProviderManager.createFile(storage, path, contentStream);
        tagProviderManager.createFileTags(storage, path, file.getVersion());
        return file;
    }

    private AbstractDataStorageItem updateDataStorageItem(final AbstractDataStorage storage,
                                                          final UpdateDataStorageItemVO item) {
        switch (item.getType()) {
            case Folder: return updateFolderItem(storage, item);
            case File: return updateFileItem(storage, item);
            default: return null;
        }
    }

    private DataStorageFolder updateFolderItem(final AbstractDataStorage storage, final UpdateDataStorageItemVO item) {
        switch (item.getAction()) {
            case Create: return createDataStorageFolder(storage, item.getPath());
            case Move: return moveDataStorageFolder(storage, item.getOldPath(), item.getPath());
            case Copy: return copyDataStorageFolder(storage, item.getOldPath(), item.getPath());
            default: return null;
        }
    }

    private DataStorageFile updateFileItem(final AbstractDataStorage storage, final UpdateDataStorageItemVO item) {
        switch (item.getAction()) {
            case Create: return createDataStorageFile(storage, item.getPath(), item.getContents());
            case Move: return moveDataStorageFile(storage, item.getOldPath(), item.getPath());
            case Copy: return copyDataStorageFile(storage, item.getOldPath(), item.getPath());
            default: return null;
        }
    }

    private void checkDataStorageVersioning(AbstractDataStorage dataStorage, String version) {
        if (!StringUtils.isEmpty(version)) {
            Assert.isTrue(dataStorage.isVersioningEnabled(), messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_VERSIONING_REQUIRED, dataStorage.getName()));
        }
    }

    public void checkDataStorageObjectExists(final AbstractDataStorage dataStorage,
                                             final String path,
                                             final String version) {
        storageProviderManager.findFile(dataStorage, path, version)
                .orElseThrow(() ->
                        new ObjectNotFoundException(
                                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_NOT_FOUND,
                                        path, dataStorage.getRoot())));
    }

    public DataStorageItemType getItemType(final Long id,
                                           final String path,
                                           final String version) {
        return storageProviderManager.getItemType(load(id), path, version);
    }

    public DataStorageItemType getItemType(final AbstractDataStorage dataStorage,
                                             final String path,
                                             final String version) {
        return storageProviderManager.getItemType(dataStorage, path, version);
    }

    private void assertToolsToMount(final DataStorageVO dataStorageVO) {
        if (!CollectionUtils.isEmpty(dataStorageVO.getToolsToMount())) {
            for (ToolFingerprint tool : dataStorageVO.getToolsToMount()) {
                Assert.notNull(tool.getId(),
                        "Tool id is not provided when specifying to which tools storage should be mounted");
                Assert.notNull(toolManager.load(tool.getId()),
                        messageHelper.getMessage(MessageConstants.ERROR_TOOL_NOT_FOUND, tool.getId()));
                if (CollectionUtils.isNotEmpty(tool.getVersions())) {
                    Assert.isTrue(tool.getVersions().stream().allMatch(tv -> StringUtils.isNotBlank(tv.getVersion())),
                            "Version could not be empty when configure tools to mount");
                    final Map<String, ToolVersion> tagsToToolVersions = toolVersionManager
                            .loadToolVersions(tool.getId(), tool.getVersions().stream()
                                    .map(ToolVersionFingerprint::getVersion)
                                    .filter(StringUtils::isNotBlank)
                                    .collect(Collectors.toList()));
                    for (ToolVersionFingerprint version : tool.getVersions()) {
                        Assert.isTrue(tagsToToolVersions.containsKey(version.getVersion()),
                                "There is no version: " + version.getVersion());
                        if (version.getId() == null) {
                            version.setId(tagsToToolVersions.get(version.getVersion()).getId());
                        }
                    }
                }
            }
        }
    }

    private void validateStorageIsNotUsedAsDefault(final Long storageId,
                                                   final Collection<? extends StorageContainer> values) {
        Assert.isTrue(CollectionUtils.isEmpty(values),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_USED_AS_DEFAULT, storageId,
                        values.stream().findFirst().map(v -> v.getClass().getSimpleName()).orElse("CLASS"),
                        values.stream().map(v -> String.valueOf(v.getId())).collect(Collectors.joining(","))));
    }

    private String replaceInTemplate(final String template, final String replacement) {
        return template.replaceAll(FolderTemplateManager.TEMPLATE_REPLACE_MARK, replacement);
    }

    private void checkDavMountQuotas() {
        final int davMountedStoragesMaxValue = preferenceManager.getIntPreference(
                SystemPreferences.DATA_STORAGE_DAV_MOUNT_MAX_STORAGES.getKey());
        final List<EntityVO> davMountedStorages = ListUtils.emptyIfNull(
                metadataManager.searchMetadataByClassAndKeyValue(AclClass.DATA_STORAGE, DAV_MOUNT_TAG, null));
        Assert.state(davMountedStorages.size() < davMountedStoragesMaxValue,
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_DAV_MOUNT_QUOTA_EXCEEDED));
    }

    /**
     * This method compares linking masks of new storage and its source one.
     * If any masks are assigned to the source entity - new masks must be the subset of them,
     * otherwise, they might be used to bypass the source storage restrictions.
     *
     * @param newStorageVO new storage VO
     */
    private void validateMirroringParameters(final DataStorageVO newStorageVO) {
        final Long sourceStorageId = newStorageVO.getSourceStorageId();
        if (sourceStorageId == null) {
            return;
        }
        final AbstractDataStorage sourceStorage = dataStorageDao.loadDataStorage(sourceStorageId);
        if (sourceStorage != null) {
            final Set<String> sourceStorageMasks = sourceStorage.getLinkingMasks();
            if (CollectionUtils.isNotEmpty(sourceStorageMasks)) {
                final Set<String> newStorageMasks = newStorageVO.getLinkingMasks();
                if (CollectionUtils.isEmpty(newStorageMasks)) {
                    newStorageVO.setLinkingMasks(sourceStorageMasks);
                } else if (!sourceStorageMasks.containsAll(newStorageMasks)) {
                    throw new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_NEW_LINKING_MASKS_ILLEGAL_STATE));
                }
            }
        } else {
            throw new IllegalArgumentException(messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_NOT_FOUND,
                                                                        sourceStorageId));
        }
        final DataStorageType newStorageType = newStorageVO.getType();
        final DataStorageType sourceStorageType = sourceStorage.getType();
        if (newStorageType == null) {
            newStorageVO.setType(sourceStorageType);
        } else {
            Assert.isTrue(newStorageType.equals(sourceStorageType),
                          messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_MIRROR_ILLEGAL_TYPE,
                                                   newStorageType, sourceStorageType));
        }
    }

    private AbstractDataStorage getLinkOrStorage(final AbstractDataStorage storage) {
        return Optional.of(storage)
            .map(AbstractDataStorage::getSourceStorageId)
            .map(dataStorageDao::loadDataStorage)
            .orElse(storage);
    }

    private String createStorageNameIfRequired(final DataStorageVO dataStorageVO) {
        final String nameSpecified = dataStorageVO.getName();
        final String path = dataStorageVO.getPath();
        if (dataStorageVO.getSourceStorageId() == null || dataStorageVO.getName() != null || path == null) {
            return Optional.ofNullable(nameSpecified).map(String::trim).orElse(null);
        } else {
            final String sharedPathNamePrefix = PipelineStringUtils.convertToAlphanumericWithDashes(path.trim());
            final Long latestMirrorNumber = dataStorageDao.loadDataStorageByNameOrPath(path, path, true).stream()
                .map(AbstractDataStorage::getName)
                .filter(existingName -> existingName.startsWith(sharedPathNamePrefix))
                .map(existingName -> existingName.split(PipelineStringUtils.DASH))
                .map(parts -> parts[parts.length - 1])
                .map(StringUtils::trim)
                .filter(NumberUtils::isDigits)
                .map(Long::valueOf)
                .max(Comparator.naturalOrder())
                .orElse(0L);
            return String.join(PipelineStringUtils.DASH,
                               sharedPathNamePrefix, SHARED_STORAGE_SUFFIX, Long.toString(latestMirrorNumber + 1));
        }
    }

    private DataStorageLifecycleRestoredListingContainer loadRestoredPaths(final AbstractDataStorage storage,
                                                                           final String path) {
        final String normalizedPath = ProviderUtils.delimiterIfEmpty(ProviderUtils.withLeadingDelimiter(path));
        final List<StorageRestoreAction> restoredItems = storageLifecycleRestoreManager
                .loadSucceededRestoreActions(storage, path);

        if (CollectionUtils.isEmpty(restoredItems)) {
            return DataStorageLifecycleRestoredListingContainer.builder()
                    .folderRestored(false)
                    .restoredFiles(Collections.emptyList())
                    .build();
        }
        final boolean parentFolderRestored = restoredItems.stream()
                .filter(action -> StorageRestorePathType.FOLDER.equals(action.getType()))
                .map(StorageRestoreAction::getPath)
                .map(restoredPath -> ProviderUtils.DELIMITER.equals(restoredPath)
                        ? restoredPath : ProviderUtils.withoutTrailingDelimiter(restoredPath))
                .anyMatch(normalizedPath::startsWith);
        if (parentFolderRestored) {
            return DataStorageLifecycleRestoredListingContainer.builder()
                    .folderRestored(true)
                    .restoredFiles(Collections.emptyList())
                    .build();
        }
        return DataStorageLifecycleRestoredListingContainer.builder()
                .folderRestored(false)
                .restoredFiles(getRestoredFilePaths(restoredItems))
                .build();
    }

    private List<String> getRestoredFilePaths(final List<StorageRestoreAction> restoredItems) {
        return ListUtils.emptyIfNull(restoredItems).stream()
                .filter(action -> StorageRestorePathType.FILE.equals(action.getType()))
                .map(StorageRestoreAction::getPath)
                .collect(Collectors.toList());
    }
}
