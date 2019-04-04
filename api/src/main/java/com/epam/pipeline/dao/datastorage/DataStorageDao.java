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

package com.epam.pipeline.dao.datastorage;

import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageFactory;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.azure.AzureBlobStorage;
import com.epam.pipeline.entity.datastorage.gcp.GSBucketStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.utils.DateUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.epam.pipeline.dao.DaoHelper.POSTGRES_LIKE_CHARACTER;

public class DataStorageDao extends NamedParameterJdbcDaoSupport {

    private Pattern limitPattern = Pattern.compile("@LIMIT@");
    private Pattern offsetPattern = Pattern.compile("@OFFSET@");

    private String dataStorageSequence;

    private String loadAllDataStoragesQuery;
    private String loadDataStorageByIdQuery;
    private String createDataStorageQuery;
    private String updateDataStorageQuery;
    private String deleteDataStorageQuery;
    private String loadRootDataStoragesQuery;
    private String loadDataStorageByNameQuery;
    private String loadDataStorageByNameAndParentIdQuery;
    private String loadDataStoragesByNFSRootPath;
    private String updateStorageLocksQuery;
    private String loadStorageCountQuery;
    private String loadAllStoragesWithParentsQuery;
    private String loadStorageWithParentsQuery;

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
                DataStorageParameters.getParameters(dataStorage));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateDataStorage(AbstractDataStorage dataStorage) {
        getNamedParameterJdbcTemplate().update(updateDataStorageQuery,
                DataStorageParameters.getParameters(dataStorage));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteDataStorage(Long id) {
        getJdbcTemplate().update(deleteDataStorageQuery, id);
    }

    public List<AbstractDataStorage> loadAllDataStorages() {
        return getNamedParameterJdbcTemplate().query(loadAllDataStoragesQuery,
                DataStorageParameters.getRowMapper());
    }

    public AbstractDataStorage loadDataStorage(Long id) {
        List<AbstractDataStorage> items = getJdbcTemplate().query(loadDataStorageByIdQuery,
                DataStorageParameters.getRowMapper(), id);
        return !items.isEmpty() ? items.get(0) : null;
    }

    public AbstractDataStorage loadDataStorageByNameOrPath(String name, String path) {
        String usePath = path == null ? name : path;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(DataStorageParameters.DATASTORAGE_NAME.name(), name);
        params.addValue(DataStorageParameters.PATH.name(), usePath);
        List<AbstractDataStorage> items = getNamedParameterJdbcTemplate()
                .query(loadDataStorageByNameQuery, params, DataStorageParameters.getRowMapper());
        return !items.isEmpty() ? items.get(0) : null;
    }

    public AbstractDataStorage loadDataStorageByNameAndParentId(String name, Long folderId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(DataStorageParameters.DATASTORAGE_NAME.name(), name);
        params.addValue(DataStorageParameters.PATH.name(), name);
        params.addValue(DataStorageParameters.FOLDER_ID.name(), folderId);
        List<AbstractDataStorage> items = getNamedParameterJdbcTemplate()
                .query(loadDataStorageByNameAndParentIdQuery, params,
                        DataStorageParameters.getRowMapper());
        return !items.isEmpty() ? items.get(0) : null;
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
        DATASTORAGE_LOCKED,
        MOUNT_POINT,
        SHARED,
        PARENT_FOLDER_ID,

        // S3 specific fields
        ALLOWED_CIDRS,

        // NFS specific fields
        MOUNT_OPTIONS,
        FILE_SHARE_MOUNT_ID,

        // cloud specific fields
        REGION_ID;

        static MapSqlParameterSource getParameters(AbstractDataStorage dataStorage) {
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

            if (dataStorage instanceof S3bucketDataStorage) {
                S3bucketDataStorage bucket = ((S3bucketDataStorage) dataStorage);
                String cidrsStr = bucket.getAllowedCidrs() != null ?
                                  bucket.getAllowedCidrs().stream().collect(Collectors.joining(",")) : null;
                params.addValue(ALLOWED_CIDRS.name(), cidrsStr);
                params.addValue(REGION_ID.name(), bucket.getRegionId());
            } else if (dataStorage instanceof AzureBlobStorage) {
                AzureBlobStorage blob = ((AzureBlobStorage) dataStorage);
                params.addValue(REGION_ID.name(), blob.getRegionId());
                params.addValue(ALLOWED_CIDRS.name(), null);
            } else if (dataStorage instanceof GSBucketStorage) {
                params.addValue(REGION_ID.name(), ((GSBucketStorage)dataStorage).getRegionId());
            } else {
                params.addValue(ALLOWED_CIDRS.name(), null);
                params.addValue(REGION_ID.name(), null);
            }

            if (dataStorage instanceof NFSDataStorage) {
                params.addValue(MOUNT_OPTIONS.name(), dataStorage.getMountOptions());
                params.addValue(FILE_SHARE_MOUNT_ID.name(), dataStorage.getFileShareMountId());
            } else {
                params.addValue(MOUNT_OPTIONS.name(), null);
                params.addValue(FILE_SHARE_MOUNT_ID.name(), null);
            }

            addPolicyParameters(dataStorage, params);
            return params;
        }

        private static void addPolicyParameters(AbstractDataStorage dataStorage,
                MapSqlParameterSource params) {
            if (dataStorage.getStoragePolicy() == null) {
                params.addValue(ENABLE_VERSIONING.name(), false);
                params.addValue(BACKUP_DURATION.name(), null);
                params.addValue(STS_DURATION.name(), null);
                params.addValue(LTS_DURATION.name(), null);
            } else {
                StoragePolicy policy = dataStorage.getStoragePolicy();
                params.addValue(ENABLE_VERSIONING.name(), policy.isVersioningEnabled());
                params.addValue(BACKUP_DURATION.name(), policy.getBackupDuration());
                params.addValue(STS_DURATION.name(), policy.getShortTermStorageDuration());
                params.addValue(LTS_DURATION.name(), policy.getLongTermStorageDuration());
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
                    fileShareMountId);

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
            return dataStorage;
        }

        static RowMapper<AbstractDataStorage> getRowMapper() {
            return (rs, rowNum) -> parseDataStorage(rs);
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
            return policy;
        }

    }
}
