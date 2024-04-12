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

package com.epam.pipeline.dao.datastorage;

import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageFactory;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.NFSStorageMountStatus;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.azure.AzureBlobStorage;
import com.epam.pipeline.entity.datastorage.gcp.GSBucketStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.ToolFingerprint;
import com.epam.pipeline.entity.pipeline.ToolVersionFingerprint;
import com.epam.pipeline.entity.utils.DateUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.data.util.Pair;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.dao.DaoHelper.POSTGRES_LIKE_CHARACTER;

public class DataStorageDao extends NamedParameterJdbcDaoSupport {

    private Pattern limitPattern = Pattern.compile("@LIMIT@");
    private Pattern offsetPattern = Pattern.compile("@OFFSET@");

    private String dataStorageSequence;

    private String loadAllDataStoragesQuery;
    private String loadDataStorageByIdQuery;
    private String createDataStorageQuery;
    private String updateDataStorageQuery;
    private String updateDataStorageMountStatusQuery;
    private String deleteDataStorageQuery;
    private String loadRootDataStoragesQuery;
    private String loadDataStorageByNameQuery;
    private String loadDataStorageByNameAndParentIdQuery;
    private String loadDataStoragesByNFSRootPath;
    private String updateStorageLocksQuery;
    private String loadStorageCountQuery;
    private String loadAllStoragesWithParentsQuery;
    private String loadStorageWithParentsQuery;
    private String loadDataStorageByPrefixesQuery;
    private String loadDataStoragesByIdsQuery;
    private String loadDataStoragesByPathsQuery;
    private String loadDataStoragesFileShareId;
    private String loadToolsToMountQuery;
    private String loadToolsToMountsForAllStoragesQuery;
    private String deleteToolsToMountQuery;
    private String addToolVersionToMountQuery;

    @Autowired
    private DaoHelper daoHelper;

    private static AbstractDataStorageFactory dataStorageFactory =
            AbstractDataStorageFactory.getDefaultDataStorageFactory();

    @Transactional(propagation = Propagation.MANDATORY)
    public Long createDataStorageId() {
        return daoHelper.createId(dataStorageSequence);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createDataStorage(AbstractDataStorage dataStorage) {
        dataStorage.setId(createDataStorageId());
        if (dataStorage.getCreatedDate() == null) {
            dataStorage.setCreatedDate(DateUtils.now());
        }
        getNamedParameterJdbcTemplate().update(createDataStorageQuery,
                DataStorageParameters.getParameters(dataStorage, true));
        updateToolsToMountForDataStorage(dataStorage);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateDataStorage(AbstractDataStorage dataStorage) {
        getNamedParameterJdbcTemplate().update(updateDataStorageQuery,
                DataStorageParameters.getParameters(dataStorage, false));
        updateToolsToMountForDataStorage(dataStorage);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateDataStorageMountStatus(final Long storageId, final NFSStorageMountStatus mountStatus) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(DataStorageParameters.DATASTORAGE_ID.name(), storageId);
        params.addValue(DataStorageParameters.MOUNT_STATUS.name(), mountStatus.name());
        getNamedParameterJdbcTemplate().update(updateDataStorageMountStatusQuery, params);
    }

    private void updateToolsToMountForDataStorage(final AbstractDataStorage dataStorage) {
        if (dataStorage.getToolsToMount() != null) {
            removeToolsToMountForDataStorage(dataStorage.getId());
            final MapSqlParameterSource[] params = ListUtils.emptyIfNull(dataStorage.getToolsToMount()).stream()
                    .flatMap(toolFingerprint -> {
                        if (CollectionUtils.isEmpty(toolFingerprint.getVersions())) {
                            return Stream.of(Pair.of(toolFingerprint, ToolVersionFingerprint.builder().build()));
                        } else {
                            return toolFingerprint.getVersions().stream().map(tv -> Pair.of(toolFingerprint, tv));
                        }
                    }).map(toolAndVersion -> {
                        final MapSqlParameterSource p = new MapSqlParameterSource();
                        p.addValue(DataStorageParameters.TOOL_ID.name(), toolAndVersion.getFirst().getId());
                        p.addValue(DataStorageParameters.DATASTORAGE_ID.name(), dataStorage.getId());
                        if (CollectionUtils.isNotEmpty(toolAndVersion.getFirst().getVersions())) {
                            p.addValue(DataStorageParameters.TOOL_VERSION_ID.name(),
                                    toolAndVersion.getSecond().getId());
                        } else {
                            p.addValue(DataStorageParameters.TOOL_VERSION_ID.name(), null);
                        }
                        return p;
                    })
                    .toArray(MapSqlParameterSource[]::new);
            getNamedParameterJdbcTemplate().batchUpdate(addToolVersionToMountQuery, params);

        }
    }

    private void removeToolsToMountForDataStorage(Long id) {
        getJdbcTemplate().update(deleteToolsToMountQuery, id);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteDataStorage(Long id) {
        getJdbcTemplate().update(deleteDataStorageQuery, id);
    }

    public List<AbstractDataStorage> loadAllDataStorages() {
        return getNamedParameterJdbcTemplate().query(loadAllDataStoragesQuery,
                DataStorageParameters.getRowMapper());
    }

    public List<AbstractDataStorage> loadAllDataStoragesWithToolsToMount() {
        final List<AbstractDataStorage> storages = getNamedParameterJdbcTemplate().query(loadAllDataStoragesQuery,
                DataStorageParameters.getRowMapper());
        final Map<Long, List<ToolFingerprint>> toolsToMountForStorages = loadToolsToMountForStorages();
        storages.forEach(storage -> storage.setToolsToMount(toolsToMountForStorages.get(storage.getId())));
        return storages;
    }

    public List<ToolFingerprint> loadToolsToMountForStorage(Long id) {
        return getJdbcTemplate().query(loadToolsToMountQuery, DataStorageParameters.getToolsToMountRowMapper(), id);
    }

    public Map<Long, List<ToolFingerprint>> loadToolsToMountForStorages() {
        return getJdbcTemplate().query(loadToolsToMountsForAllStoragesQuery,
                DataStorageParameters.getToolsToMountForAllStorageRowMapper());
    }

    public List<AbstractDataStorage> loadDataStoragesByIds(final List<Long> ids) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(DataStorageParameters.DATASTORAGE_IDS.name(), ids);
        return getNamedParameterJdbcTemplate().query(loadDataStoragesByIdsQuery,
                params, DataStorageParameters.getRowMapper());

    }

    public List<AbstractDataStorage> loadDataStoragesByPaths(final List<String> paths) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(DataStorageParameters.DATASTORAGE_PATHS.name(), paths);
        return getNamedParameterJdbcTemplate().query(loadDataStoragesByPathsQuery,
                params, DataStorageParameters.getRowMapper());
    }

    public AbstractDataStorage loadDataStorage(Long id) {
        List<AbstractDataStorage> items = getJdbcTemplate().query(loadDataStorageByIdQuery,
                DataStorageParameters.getRowMapper(), id);
        AbstractDataStorage storage = !items.isEmpty() ? items.get(0) : null;
        if (storage != null) {
            storage.setToolsToMount(loadToolsToMountForStorage(storage.getId()));
        }
        return storage;
    }

    public AbstractDataStorage loadDataStorageByNameOrPath(String name, String path) {
        String usePath = path == null ? name : path;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(DataStorageParameters.DATASTORAGE_NAME.name(), name);
        params.addValue(DataStorageParameters.PATH.name(), usePath);
        List<AbstractDataStorage> items = getNamedParameterJdbcTemplate()
                .query(loadDataStorageByNameQuery, params, DataStorageParameters.getRowMapper());
        AbstractDataStorage storage = !items.isEmpty() ? items.get(0) : null;
        if (storage != null) {
            storage.setToolsToMount(loadToolsToMountForStorage(storage.getId()));
        }
        return storage;
    }

    public AbstractDataStorage loadDataStorageByNameAndParentId(String name, Long folderId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(DataStorageParameters.DATASTORAGE_NAME.name(), name);
        params.addValue(DataStorageParameters.PATH.name(), name);
        params.addValue(DataStorageParameters.FOLDER_ID.name(), folderId);
        List<AbstractDataStorage> items = getNamedParameterJdbcTemplate()
                .query(loadDataStorageByNameAndParentIdQuery, params,
                        DataStorageParameters.getRowMapper());
        AbstractDataStorage storage = !items.isEmpty() ? items.get(0) : null;
        if (storage != null) {
            storage.setToolsToMount(loadToolsToMountForStorage(storage.getId()));
        }
        return storage;
    }

    public List<AbstractDataStorage> loadDataStoragesByPrefixes(final Collection<String> prefixes) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(DataStorageParameters.PATH.name(), prefixes);
        return getNamedParameterJdbcTemplate().query(loadDataStorageByPrefixesQuery, params,
                DataStorageParameters.getRowMapper());
    }

    public List<AbstractDataStorage> loadRootDataStorages() {
        return getNamedParameterJdbcTemplate().query(loadRootDataStoragesQuery,
                DataStorageParameters.getRowMapper());
    }

    public Integer loadTotalCount() {
        return getJdbcTemplate().queryForObject(loadStorageCountQuery, Integer.class);
    }

    public Collection<AbstractDataStorage> loadAllWithParents(Integer page, Integer pageSize) {
        String query = limitPattern.matcher(loadAllStoragesWithParentsQuery)
                .replaceFirst(pageSize == null ? "ALL" : pageSize.toString());
        int size = pageSize == null ? 0 : pageSize;
        int offset = page == null ? 0 : (page - 1) * size;
        query = offsetPattern.matcher(query).replaceFirst(String.valueOf(offset));
        return getJdbcTemplate().query(query, DataStorageParameters.getDataStorageWithFolderTreeExtractor());
    }

    public AbstractDataStorage loadStorageWithParents(final Long id) {
        return getJdbcTemplate().query(
                loadStorageWithParentsQuery,
                DataStorageParameters.getDataStorageWithFolderTreeExtractor(),
                id)
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateLocks(List<Long> storageIds, boolean isLocked) {
        daoHelper.updateLocks(updateStorageLocksQuery, storageIds, isLocked);
    }

    public List<AbstractDataStorage> loadDataStoragesByNFSPath(String nfsRootPath) {
        return getJdbcTemplate().query(loadDataStoragesByNFSRootPath, DataStorageParameters
                .getRowMapper(), nfsRootPath + POSTGRES_LIKE_CHARACTER);
    }

    public List<AbstractDataStorage> loadDataStoragesByFileShareMountID(Long fileShareId) {
        return getJdbcTemplate().query(loadDataStoragesFileShareId, DataStorageParameters
                .getRowMapper(), fileShareId);
    }

    @Required
    public void setLoadDataStoragesByNFSRootPath(String loadDataStoragesByNFSRootPath) {
        this.loadDataStoragesByNFSRootPath = loadDataStoragesByNFSRootPath;
    }
    @Required
    public void setDataStorageSequence(String dataStorageSequence) {
        this.dataStorageSequence = dataStorageSequence;
    }

    @Required
    public void setLoadAllDataStoragesQuery(String loadAllDataStoragesQuery) {
        this.loadAllDataStoragesQuery = loadAllDataStoragesQuery;
    }

    @Required
    public void setLoadDataStorageByIdQuery(String loadDataStorageByIdQuery) {
        this.loadDataStorageByIdQuery = loadDataStorageByIdQuery;
    }

    @Required
    public void setLoadRootDataStoragesQuery(String loadRootDataStoragesQuery) {
        this.loadRootDataStoragesQuery = loadRootDataStoragesQuery;
    }

    @Required
    public void setCreateDataStorageQuery(String createDataStorageQuery) {
        this.createDataStorageQuery = createDataStorageQuery;
    }

    @Required
    public void setDeleteDataStorageQuery(String deleteDataStorageQuery) {
        this.deleteDataStorageQuery = deleteDataStorageQuery;
    }

    @Required
    public void setUpdateDataStorageQuery(String updateDataStorageQuery) {
        this.updateDataStorageQuery = updateDataStorageQuery;
    }

    @Required
    public void setUpdateDataStorageMountStatusQuery(String updateDataStorageMountStatusQuery) {
        this.updateDataStorageMountStatusQuery = updateDataStorageMountStatusQuery;
    }

    @Required
    public void setLoadDataStorageByNameQuery(String loadDataStorageByNameQuery) {
        this.loadDataStorageByNameQuery = loadDataStorageByNameQuery;
    }

    @Required
    public void setLoadDataStorageByNameAndParentIdQuery(String loadDataStorageByNameAndParentIdQuery) {
        this.loadDataStorageByNameAndParentIdQuery = loadDataStorageByNameAndParentIdQuery;
    }

    @Required
    public void setUpdateStorageLocksQuery(String updateStorageLocksQuery) {
        this.updateStorageLocksQuery = updateStorageLocksQuery;
    }

    @Required
    public void setLoadStorageCountQuery(String loadStorageCountQuery) {
        this.loadStorageCountQuery = loadStorageCountQuery;
    }

    @Required
    public void setLoadAllStoragesWithParentsQuery(String loadAllStoragesWithParentsQuery) {
        this.loadAllStoragesWithParentsQuery = loadAllStoragesWithParentsQuery;
    }

    @Required
    public void setLoadStorageWithParentsQuery(String loadStorageWithParentsQuery) {
        this.loadStorageWithParentsQuery = loadStorageWithParentsQuery;
    }

    @Required
    public void setLoadDataStorageByPrefixesQuery(String loadDataStorageByPrefixesQuery) {
        this.loadDataStorageByPrefixesQuery = loadDataStorageByPrefixesQuery;
    }

    public void setLoadDataStoragesByIdsQuery(String loadDataStoragesByIdsQuery) {
        this.loadDataStoragesByIdsQuery = loadDataStoragesByIdsQuery;
    }

    public void setLoadDataStoragesByPathsQuery(final String loadDataStoragesByPathsQuery) {
        this.loadDataStoragesByPathsQuery = loadDataStoragesByPathsQuery;
    }

    public void setLoadDataStoragesFileShareId(String loadDataStoragesFileShareId) {
        this.loadDataStoragesFileShareId = loadDataStoragesFileShareId;
    }

    public void setLoadToolsToMountQuery(String loadToolsToMountQuery) {
        this.loadToolsToMountQuery = loadToolsToMountQuery;
    }

    public void setDeleteToolsToMountQuery(String deleteToolsToMountQuery) {
        this.deleteToolsToMountQuery = deleteToolsToMountQuery;
    }

    public void setAddToolVersionToMountQuery(String addToolVersionToMountQuery) {
        this.addToolVersionToMountQuery = addToolVersionToMountQuery;
    }

    public void setLoadToolsToMountsForAllStoragesQuery(String loadToolsToMountsForAllStoragesQuery) {
        this.loadToolsToMountsForAllStoragesQuery = loadToolsToMountsForAllStoragesQuery;
    }

    public enum DataStorageParameters {
        DATASTORAGE_ID,
        DATASTORAGE_NAME,
        DESCRIPTION,
        DATASTORAGE_TYPE,
        PATH,
        FOLDER_ID,
        CREATED_DATE,
        OWNER,
        ENABLE_VERSIONING,
        BACKUP_DURATION,
        STS_DURATION,
        LTS_DURATION,
        INCOMPLETE_UPLOAD_CLEANUP_DAYS,
        DATASTORAGE_LOCKED,
        MOUNT_POINT,
        SHARED,
        PARENT_FOLDER_ID,

        // S3 specific fields
        ALLOWED_CIDRS,
        S3_KMS_KEY_ARN,
        S3_USE_ASSUMED_CREDS,
        S3_TEMP_CREDS_ROLE,

        // NFS specific fields
        MOUNT_OPTIONS,
        FILE_SHARE_MOUNT_ID,

        // cloud specific fields
        REGION_ID,

        SENSITIVE,

        DATASTORAGE_IDS,
        DATASTORAGE_PATHS,
        
        // tools to mount
        TOOL_ID,
        TOOL_IMAGE,
        TOOL_REGISTRY,
        ALL_TOOL_VERSIONS,
        TOOL_VERSION_ID,
        TOOL_VERSION,
        MOUNT_STATUS;

        static MapSqlParameterSource getParameters(final AbstractDataStorage dataStorage,
                                                   final boolean setStorageMountStatus) {
            MapSqlParameterSource params = new MapSqlParameterSource();

            params.addValue(DATASTORAGE_ID.name(), dataStorage.getId());
            params.addValue(DATASTORAGE_NAME.name(), dataStorage.getName());
            params.addValue(DESCRIPTION.name(), dataStorage.getDescription());
            params.addValue(DATASTORAGE_TYPE.name(), dataStorage.getType().name());
            params.addValue(PATH.name(), dataStorage.getPath());
            params.addValue(FOLDER_ID.name(), dataStorage.getParentFolderId());
            params.addValue(CREATED_DATE.name(), dataStorage.getCreatedDate());
            params.addValue(OWNER.name(), dataStorage.getOwner());
            params.addValue(DATASTORAGE_LOCKED.name(), dataStorage.isLocked());
            params.addValue(MOUNT_POINT.name(), dataStorage.getMountPoint());
            params.addValue(SHARED.name(), dataStorage.isShared());
            params.addValue(MOUNT_OPTIONS.name(), dataStorage.getMountOptions());
            params.addValue(FILE_SHARE_MOUNT_ID.name(), dataStorage.getFileShareMountId());
            params.addValue(SENSITIVE.name(), dataStorage.isSensitive());

            if (dataStorage instanceof S3bucketDataStorage) {
                S3bucketDataStorage bucket = ((S3bucketDataStorage) dataStorage);
                String cidrsStr = bucket.getAllowedCidrs() != null ?
                                  bucket.getAllowedCidrs().stream().collect(Collectors.joining(",")) : null;
                params.addValue(ALLOWED_CIDRS.name(), cidrsStr);
                params.addValue(REGION_ID.name(), bucket.getRegionId());
                params.addValue(S3_KMS_KEY_ARN.name(), bucket.getKmsKeyArn());
                params.addValue(S3_TEMP_CREDS_ROLE.name(), bucket.getTempCredentialsRole());
                params.addValue(S3_USE_ASSUMED_CREDS.name(), bucket.isUseAssumedCredentials());
            } else if (dataStorage instanceof AzureBlobStorage) {
                AzureBlobStorage blob = ((AzureBlobStorage) dataStorage);
                params.addValue(REGION_ID.name(), blob.getRegionId());
            } else if (dataStorage instanceof GSBucketStorage) {
                params.addValue(REGION_ID.name(), ((GSBucketStorage)dataStorage).getRegionId());
            } else if (dataStorage instanceof NFSDataStorage && setStorageMountStatus) {
                params.addValue(MOUNT_STATUS.name(), ((NFSDataStorage) dataStorage).getMountStatus().name());
            }

            addPolicyParameters(dataStorage, params);
            Arrays.stream(DataStorageParameters.values())
                    .map(DataStorageParameters::name)
                    .filter(param -> !params.hasValue(param))
                    .forEach(param -> params.addValue(param, null));
            return params;
        }

        private static void addPolicyParameters(AbstractDataStorage dataStorage,
                MapSqlParameterSource params) {
            if (dataStorage.getStoragePolicy() != null) {
                StoragePolicy policy = dataStorage.getStoragePolicy();
                params.addValue(ENABLE_VERSIONING.name(), policy.isVersioningEnabled());
                params.addValue(BACKUP_DURATION.name(), policy.getBackupDuration());
                params.addValue(STS_DURATION.name(), policy.getShortTermStorageDuration());
                params.addValue(LTS_DURATION.name(), policy.getLongTermStorageDuration());
                params.addValue(INCOMPLETE_UPLOAD_CLEANUP_DAYS.name(), policy.getIncompleteUploadCleanupDays());
            }
        }

        public static ResultSetExtractor<Collection<AbstractDataStorage>> getDataStorageWithFolderTreeExtractor() {

            return DaoHelper.getFolderTreeExtractor(DATASTORAGE_ID.name(), FOLDER_ID.name(), PARENT_FOLDER_ID.name(),
                    DataStorageParameters::getDataStorage, DataStorageParameters::fillFolders);
        }

        private static void fillFolders(final AbstractDataStorage dataStorage, final Map<Long, Folder> folders) {
            dataStorage.setParent(DaoHelper.fillFolder(folders, dataStorage.getParent()));
        }

        private static AbstractDataStorage getDataStorage(final ResultSet rs, final Folder folder) {
            try {
                AbstractDataStorage dataStorage = parseDataStorage(rs);
                if (folder != null) {
                    dataStorage.setParent(folder);
                    dataStorage.setParentFolderId(folder.getId());
                }
                return dataStorage;
            } catch (SQLException e) {
                throw new IllegalArgumentException();
            }
        }

        private static AbstractDataStorage parseDataStorage(ResultSet rs) throws SQLException {
            String allowedCidrsStr = rs.getString(ALLOWED_CIDRS.name());
            List<String> allowedCidrs = null;
            if (StringUtils.isNotBlank(allowedCidrsStr)) {
                allowedCidrs = Arrays.asList(allowedCidrsStr.split(","));
            }

            Long regionId = rs.getLong(REGION_ID.name());
            if (rs.wasNull()) {
                regionId = null;
            }

            Long fileShareMountId = rs.getLong(FILE_SHARE_MOUNT_ID.name());
            if (rs.wasNull()) {
                fileShareMountId = null;
            }

            AbstractDataStorage dataStorage = dataStorageFactory.convertToDataStorage(
                    rs.getLong(DATASTORAGE_ID.name()),
                    rs.getString(DATASTORAGE_NAME.name()),
                    rs.getString(PATH.name()),
                    DataStorageType.getByName(rs.getString(DATASTORAGE_TYPE.name())),
                    null,
                    rs.getString(MOUNT_OPTIONS.name()),
                    rs.getString(MOUNT_POINT.name()),
                    allowedCidrs,
                    regionId,
                    fileShareMountId,
                    rs.getString(S3_KMS_KEY_ARN.name()),
                    rs.getString(S3_TEMP_CREDS_ROLE.name()),
                    rs.getBoolean(S3_USE_ASSUMED_CREDS.name()),
                    rs.getString(MOUNT_STATUS.name()));

            dataStorage.setShared(rs.getBoolean(SHARED.name()));
            dataStorage.setDescription(rs.getString(DESCRIPTION.name()));
            dataStorage.setOwner(rs.getString(OWNER.name()));
            dataStorage.setCreatedDate(new Date(rs.getTimestamp(CREATED_DATE.name()).getTime()));
            dataStorage.setLocked(rs.getBoolean(DATASTORAGE_LOCKED.name()));
            Long parentFolderId = rs.getLong(FOLDER_ID.name());
            if (!rs.wasNull()) {
                dataStorage.setParentFolderId(parentFolderId);
            }
            StoragePolicy policy = getStoragePolicy(rs);
            dataStorage.setStoragePolicy(policy);
            dataStorage.setSensitive(rs.getBoolean(SENSITIVE.name()));
            return dataStorage;
        }

        static RowMapper<AbstractDataStorage> getRowMapper() {
            return (rs, rowNum) -> parseDataStorage(rs);
        }

        public static ResultSetExtractor<Map<Long, List<ToolFingerprint>>> getToolsToMountForAllStorageRowMapper() {
            return (rs) -> {
                final Map<Long, Map<Long, ToolFingerprint>> toolsToStorage = new HashMap<>();
                while (rs.next()) {
                    final Long datastorageId = rs.getLong(DATASTORAGE_ID.name());
                    final Map<Long, ToolFingerprint> tools = toolsToStorage.computeIfAbsent(
                            datastorageId, id -> new HashMap<>());
                    final Long toolId = rs.getLong(TOOL_ID.name());
                    ToolFingerprint toolFingerprint = tools.get(toolId);
                    if (toolFingerprint == null) {
                        toolFingerprint = ToolFingerprint.builder()
                                .id(toolId)
                                .image(rs.getString(TOOL_IMAGE.name()))
                                .registry(rs.getString(TOOL_REGISTRY.name()))
                                .versions(new ArrayList<>())
                                .build();
                        tools.put(toolId, toolFingerprint);
                    }
                    final Long toolVersionId = rs.getLong(TOOL_VERSION_ID.name());
                    if (!rs.wasNull()) {
                        toolFingerprint.getVersions()
                                .add(ToolVersionFingerprint.builder()
                                        .id(toolVersionId)
                                        .version(rs.getString(TOOL_VERSION.name()))
                                        .build());
                    }
                }
                return toolsToStorage.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> new ArrayList<>(e.getValue().values())));

            };
        }

        public static ResultSetExtractor<List<ToolFingerprint>> getToolsToMountRowMapper() {
            return (rs) -> {
                final Map<Long, ToolFingerprint> tools = new HashMap<>();
                while (rs.next()) {
                    final Long toolId = rs.getLong(TOOL_ID.name());
                    ToolFingerprint toolFingerprint = tools.get(toolId);
                    if (toolFingerprint == null) {
                        toolFingerprint = ToolFingerprint.builder()
                                .id(toolId)
                                .image(rs.getString(TOOL_IMAGE.name()))
                                .registry(rs.getString(TOOL_REGISTRY.name()))
                                .versions(new ArrayList<>())
                                .build();
                        tools.put(toolId, toolFingerprint);
                    }
                    final Long toolVersionId = rs.getLong(TOOL_VERSION_ID.name());
                    if (!rs.wasNull()) {
                        toolFingerprint.getVersions()
                                .add(ToolVersionFingerprint.builder()
                                        .id(toolVersionId)
                                        .version(rs.getString(TOOL_VERSION.name()))
                                        .build());
                    }
                }
                return new ArrayList<>(tools.values());
            };
        }

        public static StoragePolicy getStoragePolicy(ResultSet rs) throws SQLException {
            StoragePolicy policy = new StoragePolicy();
            policy.setVersioningEnabled(rs.getBoolean(ENABLE_VERSIONING.name()));
            int backupDuration = rs.getInt(BACKUP_DURATION.name());
            if (!rs.wasNull()) {
                policy.setBackupDuration(backupDuration);
            }
            int stsDuration = rs.getInt(STS_DURATION.name());
            if (!rs.wasNull()) {
                policy.setShortTermStorageDuration(stsDuration);
            }
            int ltsDuration = rs.getInt(LTS_DURATION.name());
            if (!rs.wasNull()) {
                policy.setLongTermStorageDuration(ltsDuration);
            }
            int incompleteUploadCleanupDays = rs.getInt(INCOMPLETE_UPLOAD_CLEANUP_DAYS.name());
            if (!rs.wasNull()) {
                policy.setIncompleteUploadCleanupDays(incompleteUploadCleanupDays);
            }
            return policy;
        }

    }
}
