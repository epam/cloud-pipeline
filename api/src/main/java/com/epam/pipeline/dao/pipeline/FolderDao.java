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

package com.epam.pipeline.dao.pipeline;

import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.dao.configuration.RunConfigurationDao;
import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.dao.metadata.MetadataDao;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageFactory;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.metadata.FolderWithMetadata;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.RepositoryType;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.epam.pipeline.dao.metadata.MetadataDao.MetadataParameters.parseData;

public class FolderDao extends NamedParameterJdbcDaoSupport {

    @Autowired
    private DaoHelper daoHelper;

    private String folderSequence;
    private String createFolderQuery;
    private String updateFolderQuery;
    private String loadAllFoldersQuery;
    private String deleteFolderQuery;
    private String loadFolderByIdQuery;
    private String loadFolderByNameAndParentIdQuery;
    private String loadFolderByNameQuery;
    private String loadParentFoldersQuery;
    private String loadAllProjectsQuery;
    private String updateFolderLocksQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public Long createFolderId() {
        return daoHelper.createId(folderSequence);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createFolder(Folder folder) {
        folder.setId(createFolderId());
        getNamedParameterJdbcTemplate()
                .update(createFolderQuery, FolderParameters.getParameters(folder));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateFolder(Folder folder) {
        getNamedParameterJdbcTemplate()
                .update(updateFolderQuery, FolderParameters.getParameters(folder));
    }

    public List<Folder> loadAllFolders() {
        Collection<Folder> items =
                getJdbcTemplate().query(loadAllFoldersQuery, FolderParameters.getFolderExtractor(false));
        return items.stream().filter(folder -> folder.getParentId() == null).collect(Collectors.toList());
    }

    public Folder loadFolder(Long id) {
        Collection<Folder> items =
                getJdbcTemplate().query(loadFolderByIdQuery, FolderParameters.getFolderExtractor(false), id);
        if (CollectionUtils.isEmpty(items)) {
            return null;
        } else {
            return items.stream().filter(folder -> folder.getId().equals(id)).findAny().orElse(null);
        }
    }

    public List<Folder> loadAllProjects(Map<String, PipeConfValue> projectIndicator) {
        return new ArrayList<>(getNamedParameterJdbcTemplate()
                .query(loadAllProjectsQuery, Collections.singletonMap("PROJECT_INDICATOR",
                        MetadataDao.convertDataToJsonStringForQuery(projectIndicator)),
                        FolderParameters.getFolderExtractor(true)));
    }

    public Folder loadFolderByName(String name) {
        List<Folder> items =
                getJdbcTemplate().query(loadFolderByNameQuery, FolderParameters.getRowMapper(), name.toLowerCase());
        return !items.isEmpty() ? items.get(0) : null;
    }

    public Folder loadFolderByNameAndParentId(String name, Long parentId) {
        List<Folder> items =
                getJdbcTemplate().query(
                        loadFolderByNameAndParentIdQuery,
                        FolderParameters.getRowMapper(),
                        name.toLowerCase(),
                        parentId);
        return !items.isEmpty() ? items.get(0) : null;
    }

    public List<FolderWithMetadata> loadParentFolders(Long folderId) {
        List<FolderWithMetadata> items =
                getJdbcTemplate().query(loadParentFoldersQuery, FolderParameters.getRowMapperWithMetadata(), folderId);
        return CollectionUtils.isEmpty(items) ? null : items;
    }

    public List<Folder> loadFolderWithParents(final Long id) {
        return ListUtils.emptyIfNull(
                getJdbcTemplate().query(loadParentFoldersQuery, FolderParameters.getRowMapper(), id));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteFolder(Long id) {
        getJdbcTemplate().update(deleteFolderQuery, id);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateLocks(List<Long> folderIds, boolean isLocked) {
        daoHelper.updateLocks(updateFolderLocksQuery, folderIds, isLocked);
    }

    enum FolderParameters {
        FOLDER_ID,
        FOLDER_NAME,
        PARENT_ID,
        CREATED_DATE,
        LOCKED,
        PIPELINE_ID,
        PIPELINE_NAME,
        PIPELINE_REPO,
        PIPELINE_DESCRIPTION,
        PIPELINE_CREATED_DATE,
        PIPELINE_REPOSITORY_TOKEN,
        PIPELINE_REPOSITORY_TYPE,
        PIPELINE_LOCKED,
        DATASTORAGE_ID,
        DATASTORAGE_NAME,
        DATASTORAGE_DESCRIPTION,
        DATASTORAGE_CREATED_DATE,
        DATASTORAGE_PATH,
        DATASTORAGE_TYPE,
        DATASTORAGE_LOCKED,
        DATASTORAGE_MOUNT_POINT,
        DATASTORAGE_MOUNT_OPTIONS,
        DATASTORAGE_SHARED,
        DATASTORAGE_ALLOWED_CIDRS,
        DATASTORAGE_REGION_ID,
        DATASTORAGE_FILE_SHARE_MOUNT_ID,
        ENABLE_VERSIONING,
        BACKUP_DURATION,
        STS_DURATION,
        LTS_DURATION,
        OWNER,
        ENTITY_ID,
        CLASS_NAME,
        CONFIG_ID,
        DATA;

        static MapSqlParameterSource getParameters(Folder folder) {
            MapSqlParameterSource params = new MapSqlParameterSource();

            params.addValue(FOLDER_ID.name(), folder.getId());
            params.addValue(FOLDER_NAME.name(), folder.getName());
            params.addValue(PARENT_ID.name(), folder.getParentId());
            params.addValue(CREATED_DATE.name(), folder.getCreatedDate());
            params.addValue(OWNER.name(), folder.getOwner());
            params.addValue(LOCKED.name(), folder.isLocked());
            return params;
        }

        static RowMapper<Folder> getRowMapper() {
            return (rs, rowNum) -> {
                Folder folder = new Folder();
                basicInitFolder(folder, rs);
                return folder;
            };
        }

        static RowMapper<FolderWithMetadata> getRowMapperWithMetadata() {
            return (rs, rowNum) -> {
                FolderWithMetadata folder = new FolderWithMetadata();
                folder = (FolderWithMetadata) basicInitFolder(folder, rs);
                folder.setData(parseData(rs.getString(DATA.name())));
                return folder;
            };
        }

        static ResultSetExtractor<Collection<Folder>> getFolderExtractor(boolean withMetadata) {
            return (rs) -> {
                Map<Long, Folder> folders = new HashMap<>();
                Map<Long, Set<Long>> folderToChildren = new HashMap<>();
                while (rs.next()) {
                    Long folderId = rs.getLong(FOLDER_ID.name());
                    Folder folder = folders.get(folderId);
                    if (folder == null) {
                        folder = withMetadata ? new FolderWithMetadata() : new Folder();
                        basicInitFolder(folder, rs);
                        if (withMetadata) {
                            ((FolderWithMetadata) folder).setData(parseData(rs.getString(DATA.name())));
                        }
                        Long parentId = rs.getLong(PARENT_ID.name());
                        if (!rs.wasNull()) {
                            folder.setParentId(parentId);
                            folderToChildren.putIfAbsent(parentId, new HashSet<>());
                            folderToChildren.get(parentId).add(folderId);
                        }
                    }
                    Long pipelineId = rs.getLong(PIPELINE_ID.name());
                    if (!rs.wasNull()) {
                        Pipeline pipeline = new Pipeline();
                        pipeline.setId(pipelineId);
                        pipeline.setName(rs.getString(PIPELINE_NAME.name()));
                        pipeline.setDescription(rs.getString(PIPELINE_DESCRIPTION.name()));
                        pipeline.setRepository(rs.getString(PIPELINE_REPO.name()));
                        pipeline.setRepositoryToken(rs.getString(PIPELINE_REPOSITORY_TOKEN.name()));
                        pipeline.setRepositoryType(RepositoryType.getById(rs.getLong(PIPELINE_REPOSITORY_TYPE.name())));
                        pipeline.setCreatedDate(
                                new Date(rs.getTimestamp(PIPELINE_CREATED_DATE.name()).getTime()));
                        pipeline.setLocked(rs.getBoolean(PIPELINE_LOCKED.name()));
                        pipeline.setParentFolderId(folderId);
                        folder.getPipelines().add(pipeline);
                    }
                    Long dataStorageId = rs.getLong(DATASTORAGE_ID.name());
                    if (!rs.wasNull()) {
                        String allowedCidrsStr = rs.getString(DATASTORAGE_ALLOWED_CIDRS.name());
                        List<String> allowedCidrs = null;
                        if (StringUtils.isNotBlank(allowedCidrsStr)) {
                            allowedCidrs = Arrays.asList(allowedCidrsStr.split(","));
                        }
                        Long regionId = rs.getLong(DATASTORAGE_REGION_ID.name());
                        if (rs.wasNull()) {
                            regionId = null;
                        }

                        Long fileShareMountId = rs.getLong(DATASTORAGE_FILE_SHARE_MOUNT_ID.name());
                        if (rs.wasNull()) {
                            fileShareMountId = null;
                        }

                        AbstractDataStorage dataStorage = AbstractDataStorageFactory
                                .getDefaultDataStorageFactory().convertToDataStorage(
                                        dataStorageId,
                                        rs.getString(DATASTORAGE_NAME.name()),
                                        rs.getString(DATASTORAGE_PATH.name()),
                                        DataStorageType.getByName(rs.getString(DATASTORAGE_TYPE.name())),
                                        null,
                                        rs.getString(DATASTORAGE_MOUNT_OPTIONS.name()),
                                        rs.getString(DATASTORAGE_MOUNT_POINT.name()),
                                        allowedCidrs,
                                        regionId,
                                        fileShareMountId);
                        dataStorage.setDescription(rs.getString(DATASTORAGE_DESCRIPTION.name()));
                        dataStorage.setCreatedDate(
                                new Date(rs.getTimestamp(DATASTORAGE_CREATED_DATE.name()).getTime())
                        );
                        StoragePolicy policy = DataStorageDao.DataStorageParameters.getStoragePolicy(rs);
                        dataStorage.setStoragePolicy(policy);
                        dataStorage.setParentFolderId(folderId);
                        dataStorage.setLocked(rs.getBoolean(DATASTORAGE_LOCKED.name()));
                        dataStorage.setShared(rs.getBoolean(DATASTORAGE_SHARED.name()));
                        folder.getStorages().add(dataStorage);
                    }
                    rs.getLong(CONFIG_ID.name());
                    if (!rs.wasNull()) {
                        RunConfiguration configuration =
                                RunConfigurationDao.ConfigurationParameters.getRunConfiguration(rs);
                        folder.getConfigurations().add(configuration);
                    }
                    Integer entityId = rs.getInt(ENTITY_ID.name());
                    if (!rs.wasNull()) {
                        folder.getMetadata().put(rs.getString(CLASS_NAME.name()), entityId);
                    }
                    folders.putIfAbsent(folderId, folder);
                }
                folderToChildren.forEach((parentId, children) -> {
                    Folder parent = folders.get(parentId);
                    if (parent != null && !CollectionUtils.isEmpty(children)) {
                        children.forEach(child -> parent.getChildFolders().add(folders.get(child)));
                    }
                });
                return folders.values();
            };
        }

        private static Folder basicInitFolder(Folder folder, ResultSet rs) throws SQLException {
            folder.setId(rs.getLong(FOLDER_ID.name()));
            folder.setName(rs.getString(FOLDER_NAME.name()));
            long parentId = rs.getLong(PARENT_ID.name());
            if (!rs.wasNull()) {
                folder.setParentId(parentId);
            }
            folder.setCreatedDate(new Date(rs.getTimestamp(CREATED_DATE.name()).getTime()));
            folder.setOwner(rs.getString(OWNER.name()));
            folder.setLocked(rs.getBoolean(LOCKED.name()));
            return folder;
        }

    }
    @Required
    public void setFolderSequence(String folderSequence) {
        this.folderSequence = folderSequence;
    }

    @Required
    public void setCreateFolderQuery(String createFolderQuery) {
        this.createFolderQuery = createFolderQuery;
    }

    @Required
    public void setUpdateFolderQuery(String updateFolderQuery) {
        this.updateFolderQuery = updateFolderQuery;
    }

    @Required
    public void setLoadAllFoldersQuery(String loadAllFoldersQuery) {
        this.loadAllFoldersQuery = loadAllFoldersQuery;
    }

    @Required
    public void setDeleteFolderQuery(String deleteFolderQuery) {
        this.deleteFolderQuery = deleteFolderQuery;
    }

    @Required
    public void setLoadFolderByIdQuery(String loadFolderByIdQuery) {
        this.loadFolderByIdQuery = loadFolderByIdQuery;
    }

    @Required
    public void setLoadFolderByNameQuery(String loadFolderByNameQuery) {
        this.loadFolderByNameQuery = loadFolderByNameQuery;
    }

    @Required
    public void setLoadFolderByNameAndParentIdQuery(String loadFolderByNameAndParentIdQuery) {
        this.loadFolderByNameAndParentIdQuery = loadFolderByNameAndParentIdQuery;
    }

    @Required
    public void setLoadParentFoldersQuery(String loadParentFoldersQuery) {
        this.loadParentFoldersQuery = loadParentFoldersQuery;
    }

    @Required
    public void setUpdateFolderLocksQuery(String updateFolderLocksQuery) {
        this.updateFolderLocksQuery = updateFolderLocksQuery;
    }

    @Required
    public void setLoadAllProjectsQuery(String loadAllProjectsQuery) {
        this.loadAllProjectsQuery = loadAllProjectsQuery;
    }
}
