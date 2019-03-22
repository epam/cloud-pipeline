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

package com.epam.pipeline.dao.metadata;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.metadata.FireCloudClass;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataClassDescription;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.MetadataField;
import com.epam.pipeline.entity.metadata.MetadataFilter;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.manager.metadata.parser.EntityTypeField;
import org.apache.commons.collections4.CollectionUtils;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MetadataEntityDao extends NamedParameterJdbcDaoSupport {

    private Pattern dataKeyPattern = Pattern.compile("@KEY@");
    private Pattern dataValuePatten = Pattern.compile("@VALUE@");
    private Pattern wherePattern = Pattern.compile("@WHERE_CLAUSE@");
    private Pattern orderPattern = Pattern.compile("@ORDER_CLAUSE@");
    private Pattern searchPattern = Pattern.compile("@QUERY@");
    private static final String AND = " AND ";
    private static final int BATCH_SIZE = 1000;

    @Autowired
    private DaoHelper daoHelper;

    private String metadataEntitySequence;
    private String createMetadataEntityQuery;
    private String updateMetadataEntityQuery;
    private String updateMetadataEntityDataKeyQuery;
    private String loadAllMetadataEntitiesQuery;
    private String loadMetadataEntityByIdQuery;
    private String insertCopiesOfExistentMetadataEntitiesQuery;
    private String loadRootMetadataEntityQuery;
    private String loadMetadataEntityByClassNameAndFolderIdQuery;
    private String deleteMetadataEntityDataKeyQuery;
    private String deleteMetadataEntityItemQuery;
    private String recursiveFilterQuery;
    private String baseFilterQuery;
    private String searchClauseQuery;
    private String externalIdClauseQuery;
    private String recursiveFilterCountQuery;
    private String baseFilterCountQuery;
    private String loadMetadataKeysQuery;
    private String loadByExternalIdsQuery;
    private String loadBylIdsQuery;
    private String loadAllReferencesQuery;
    private String loadMetadataKeysRecursiveQuery;
    private String loadEntitiesInProjectQuery;
    private String deleteMetadataInFolderQuery;
    private String deleteMetadataEntitiesQuery;
    private String deleteMetadataClassInProjectQuery;
    private String loadMetadataEntityWithParentsQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public void createMetadataEntity(MetadataEntity metadataEntity) {
        metadataEntity.setId(daoHelper.createId(metadataEntitySequence));
        getNamedParameterJdbcTemplate().update(createMetadataEntityQuery,
                MetadataEntityParameters.getParameters(metadataEntity));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @SuppressWarnings("unchecked")
    public Collection<MetadataEntity> batchInsert(List<MetadataEntity> entitiesToCreate) {
        for (int i = 0; i < entitiesToCreate.size(); i += BATCH_SIZE) {
            final List<MetadataEntity> batchList = entitiesToCreate.subList(i,
                    i + BATCH_SIZE > entitiesToCreate.size() ? entitiesToCreate.size() : i + BATCH_SIZE);
            List<Long> ids = daoHelper.createIds(metadataEntitySequence, batchList.size());
            Map<String, Object>[] batchValues = new Map[batchList.size()];
            for (int j = 0; j < batchList.size(); j++) {
                MetadataEntity entity = batchList.get(j);
                entity.setId(ids.get(j));
                batchValues[j] = MetadataEntityParameters.getParameters(entity).getValues();
            }
            getNamedParameterJdbcTemplate().batchUpdate(this.createMetadataEntityQuery, batchValues);
        }
        return entitiesToCreate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateMetadataEntity(MetadataEntity metadataEntity) {
        getNamedParameterJdbcTemplate().update(updateMetadataEntityQuery,
                MetadataEntityParameters.getParameters(metadataEntity));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @SuppressWarnings("unchecked")
    public Collection<MetadataEntity> batchUpdate(List<MetadataEntity> entitiesToUpdate) {
        for (int i = 0; i < entitiesToUpdate.size(); i += BATCH_SIZE) {
            final List<MetadataEntity> batchList = entitiesToUpdate.subList(i,
                    i + BATCH_SIZE > entitiesToUpdate.size() ? entitiesToUpdate.size() : i + BATCH_SIZE);
            Map<String, Object>[] batchValues = new Map[batchList.size()];
            for (int j = 0; j < batchList.size(); j++) {
                MetadataEntity entity = batchList.get(j);
                batchValues[j] = MetadataEntityParameters.getParameters(entity).getValues();
            }
            getNamedParameterJdbcTemplate().batchUpdate(this.updateMetadataEntityQuery, batchValues);
        }
        return entitiesToUpdate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateMetadataEntityDataKey(MetadataEntity metadataEntity, String key, String value, String type) {
        String query = dataKeyPattern.matcher(updateMetadataEntityDataKeyQuery)
                .replaceFirst(String.format("'{%s}'", key));
        query = dataValuePatten.matcher(query)
                .replaceFirst(String.format("'{\"type\": \"%s\", \"value\": \"%s\"}'", type, value));
        getNamedParameterJdbcTemplate().update(query, MetadataEntityParameters.getParameters(metadataEntity));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insertCopiesOfExistentMetadataEntities(Long existentParentId, Long parentIdToAdd) {
        getJdbcTemplate().update(insertCopiesOfExistentMetadataEntitiesQuery, parentIdToAdd, existentParentId);
    }

    public MetadataEntity loadMetadataEntityById(Long id) {
        List<MetadataEntity> items = getJdbcTemplate().query(loadMetadataEntityByIdQuery, MetadataEntityParameters
                .getRowMapper(), id);

        return !items.isEmpty() ? items.get(0) : null;
    }

    public MetadataEntity loadMetadataEntityWithParents(final Long id) {
        return getJdbcTemplate().query(loadMetadataEntityWithParentsQuery,
                MetadataEntityParameters.getMetadataEntityWithFolderTreeExtractor(), id)
                .stream()
                .findFirst()
                .orElse(null);
    }

    public List<MetadataEntity> loadAllMetadataEntities() {
        return getNamedParameterJdbcTemplate().query(loadAllMetadataEntitiesQuery,
                MetadataEntityParameters.getRowMapper());
    }

    //TODO: update loadRootMetadataEntityQuery to return count of entityId by class entity
    public List<MetadataEntity> loadRootMetadataEntities() {
        return getNamedParameterJdbcTemplate().query(loadRootMetadataEntityQuery,
                MetadataEntityParameters.getRowMapper());
    }

    public List<MetadataEntity> loadMetadataEntityByClassNameAndFolderId(Long id, String className) {
        return getJdbcTemplate().query(loadMetadataEntityByClassNameAndFolderIdQuery,
                MetadataEntityParameters.getRowMapper(), id, className);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteMetadataEntity(Long entityId) {
        getJdbcTemplate().update(deleteMetadataEntityItemQuery, entityId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteMetadataItemKey(Long id, String key) {
        String query = dataKeyPattern.matcher(deleteMetadataEntityDataKeyQuery)
                .replaceFirst(String.format("'%s'", key));
        getJdbcTemplate().update(query, id);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteMetadataFromFolder(Long folderId) {
        getJdbcTemplate().update(deleteMetadataInFolderQuery, folderId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteMetadataEntities(Set<Long> entitiesIds) {
        getNamedParameterJdbcTemplate().update(deleteMetadataEntitiesQuery,
                Collections.singletonMap("ENTITIES_IDS", entitiesIds));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteMetadataClassFromProject(Long projectId, Long classId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(MetadataEntityParameters.PARENT_ID.name(), projectId);
        params.addValue(MetadataEntityParameters.CLASS_ID.name(), classId);
        getNamedParameterJdbcTemplate().update(deleteMetadataClassInProjectQuery, params);
    }

    public List<MetadataEntity> loadEntitiesInProject(String className, Long projectId) {
        MapSqlParameterSource params = MetadataEntityParameters.getClassFolderParameters(className, projectId);
        return getNamedParameterJdbcTemplate()
                .query(loadEntitiesInProjectQuery, params, MetadataEntityParameters.getRowMapper());
    }

    public List<MetadataEntity> filterEntities(MetadataFilter filter) {
        MapSqlParameterSource params = MetadataEntityParameters
                .getClassFolderParameters(filter.getMetadataClass(), filter.getFolderId());
        params.addValue("LIMIT", filter.getPageSize());
        params.addValue("OFFSET", (filter.getPage() - 1) * filter.getPageSize());
        String query = buildFilterQuery(filter);
        return getNamedParameterJdbcTemplate().query(query, params, MetadataEntityParameters.getRowMapper());
    }

    public Integer countEntities(MetadataFilter filter) {
        MapSqlParameterSource params = MetadataEntityParameters
                .getClassFolderParameters(filter.getMetadataClass(), filter.getFolderId());
        String query = buildCountQuery(filter);
        return getNamedParameterJdbcTemplate().queryForObject(query, params, Integer.class);
    }

    public List<MetadataField> getMetadataKeys(Long folderId, Long classId) {
        List<MetadataField> result = new ArrayList<>();
        result.addAll(MetadataEntityParameters.fieldNames.values());
        List<String> dataFields = getJdbcTemplate()
                .queryForList(loadMetadataKeysQuery, String.class, folderId, classId);
        result.addAll(dataFields
                .stream()
                .map(name -> new MetadataField(name, null, false))
                .collect(Collectors.toList()));
        return result;
    }

    public Collection<MetadataClassDescription> getMetadataFields(Long folderId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(MetadataEntityParameters.PARENT_ID.name(), folderId);
        return getNamedParameterJdbcTemplate()
                .query(loadMetadataKeysRecursiveQuery, params,
                        MetadataEntityParameters.getClassMapper());
    }


    public Set<MetadataEntity> loadExisting(Long folderId, String className, Set<String> externalIds) {
        String idClause = externalIds.stream().map(s -> String.format("('%s')", s))
                .collect(Collectors.joining(","));
        return new HashSet<>(getJdbcTemplate().query(String.format(loadByExternalIdsQuery, idClause),
                MetadataEntityParameters.getRowMapper(), folderId, className));
    }

    public List<MetadataEntity> loadAllReferences(List<Long> entitiesIds, Long parentId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(MetadataEntityParameters.PARENT_ID.name(), parentId);
        params.addValue("IDS", entitiesIds);
        return getNamedParameterJdbcTemplate().query(loadAllReferencesQuery, params,
                MetadataEntityParameters.getRowMapper());
    }

    public Set<MetadataEntity> loadByIds(Set<Long> ids) {
        String idClause = ids.stream().map(id -> String.format("(%d)", id))
                .collect(Collectors.joining(","));
        return new HashSet<>(getJdbcTemplate().query(String.format(loadBylIdsQuery, idClause),
                MetadataEntityParameters.getRowMapper()));
    }


    private String buildFilterQuery(MetadataFilter filter) {
        String baseQuery = filter.isRecursive() ? recursiveFilterQuery : baseFilterQuery;
        baseQuery = wherePattern.matcher(baseQuery).replaceFirst(makeWhereClause(filter));
        baseQuery = orderPattern.matcher(baseQuery).replaceFirst(makerOrderClause(filter));
        return daoHelper.escapeUnderscoreParam(baseQuery);
    }

    private static String convertDataToJsonStringForQuery(Map<String, PipeConfValue> data) {
        return JsonMapper.convertDataToJsonStringForQuery(data);
    }

    private String buildCountQuery(MetadataFilter filter) {
        String baseQuery = filter.isRecursive() ? recursiveFilterCountQuery : baseFilterCountQuery;
        baseQuery = wherePattern.matcher(baseQuery).replaceFirst(makeWhereClause(filter));
        return daoHelper.escapeUnderscoreParam(baseQuery);
    }

    private String makerOrderClause(MetadataFilter filter) {
        StringBuilder clause = new StringBuilder();
        if (CollectionUtils.isNotEmpty(filter.getOrderBy())) {
            filter.getOrderBy().forEach(orderBy -> {
                if (clause.length() > 0) {
                    clause.append(", ");
                } else {
                    clause.append(" ORDER BY ");
                }
                clause.append(getFieldName(orderBy.getField()));
                if (orderBy.isDesc()) {
                    clause.append(" DESC");
                }
            });
        }
        return clause.toString();
    }

    private String makeWhereClause(MetadataFilter filter) {
        StringBuilder clause = new StringBuilder();
        addFilterConditions(clause, filter.getFilters());
        addSearchConditions(clause, filter.getSearchQueries());
        addExternalIdsConditions(clause, filter.getExternalIdQueries());
        return clause.toString();
    }

    private void addExternalIdsConditions(StringBuilder clause, List<String> externalIdQueries) {
        if (CollectionUtils.isEmpty(externalIdQueries)) {
            return;
        }
        externalIdQueries.forEach(query -> {
            String formattedQuery = daoHelper.replaceUnderscoreWithParam(query.toLowerCase());
            clause.append(AND);
            clause.append(searchPattern.matcher(externalIdClauseQuery).replaceFirst(formattedQuery));
        });
    }

    private void addSearchConditions(StringBuilder clause, List<String> searchQueries) {
        if (CollectionUtils.isEmpty(searchQueries)) {
            return;
        }
        searchQueries.forEach(query -> {
            String formattedQuery = daoHelper.replaceUnderscoreWithParam(query.toLowerCase());
            clause.append(AND);
            clause.append(searchPattern.matcher(searchClauseQuery).replaceFirst(formattedQuery));
        });
    }

    private void addFilterConditions(StringBuilder clause, List<MetadataFilter.FilterQuery> filters) {
        if (CollectionUtils.isEmpty(filters)) {
            return;
        }
        filters.forEach(filter -> {
            clause.append(AND);
            MetadataField field = MetadataEntityParameters.getFieldNames().get(filter.getKey().toUpperCase());
            if (field != null) {
                clause.append(String.format("%s::text = '%s'", field.getDbName(), filter.getValue()));
            } else {
                clause.append(String.format("e.data -> '%s' @> '{\"value\":\"%s\"}'", filter.getKey(),
                        filter.getValue()));
            }
        });
    }

    private String getFieldName(String field) {
        MetadataField fieldValue = MetadataEntityParameters.getFieldNames().get(field.toUpperCase());
        if (fieldValue != null) {
            return fieldValue.getDbName();
        }
        return String.format("e.data ->> '%s'", field);
    }

    enum MetadataEntityParameters {
        ENTITY_ID,
        CLASS_ID,
        PARENT_ID,
        ENTITY_NAME,
        EXTERNAL_ID,
        CLASS_NAME,
        DATA,
        EXTERNAL_IDS,
        KEY,
        TYPE,
        EXTERNAL_CLASS_NAME,
        FOLDER_ID,
        PARENT_FOLDER_ID;

        protected static Map<String, MetadataField> fieldNames = new HashMap<>();

        static {
            fieldNames.put("ID", new MetadataField("id", "e.entity_id", true));
            fieldNames.put("NAME", new MetadataField("name", "e.entity_name", true));
            fieldNames.put("EXTERNALID", new MetadataField("externalId", "e.external_id", true));
            fieldNames.put("PARENT.ID", new MetadataField("parent.id", "e.parent_id", true));
            fieldNames.put("CLASSENTITY.ID", new MetadataField("classEntity.id", "c.class_id", true));
            fieldNames.put("CLASSENTITY.NAME", new MetadataField("classEntity.name", "c.class_name", true));
        }
        private static Map<String, MetadataField> getFieldNames() {
            return fieldNames;
        }

        private static MapSqlParameterSource getClassFolderParameters(String className, Long folderId) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(MetadataEntityParameters.CLASS_NAME.name(), className);
            params.addValue(MetadataEntityParameters.PARENT_ID.name(), folderId);
            return params;
        }

        private static MapSqlParameterSource getParameters(MetadataEntity metadataEntity) {
            MapSqlParameterSource params = new MapSqlParameterSource();

            params.addValue(ENTITY_ID.name(), metadataEntity.getId());
            params.addValue(CLASS_ID.name(), metadataEntity.getClassEntity().getId());
            params.addValue(PARENT_ID.name(), metadataEntity.getParent().getId());
            params.addValue(ENTITY_NAME.name(), metadataEntity.getName());
            params.addValue(EXTERNAL_ID.name(), metadataEntity.getExternalId());
            params.addValue(DATA.name(), convertDataToJsonStringForQuery(metadataEntity.getData()));
            return params;
        }


        static ResultSetExtractor<Collection<MetadataClassDescription>> getClassMapper() {
            return (rs) -> {
                Map<Long, MetadataClassDescription> results = new HashMap<>();
                while (rs.next()) {
                    Long classId = rs.getLong(CLASS_ID.name());
                    MetadataClassDescription description = results.get(classId);
                    if (description == null) {
                        description = new MetadataClassDescription();
                        MetadataClass metadataClass = getMetadataClass(rs);
                        description.setMetadataClass(metadataClass);
                        description.setFields(new ArrayList<>());
                        results.put(classId, description);
                    }
                    String type = rs.getString(TYPE.name());
                    String fieldName = rs.getString(KEY.name());
                    description.getFields().add(EntityTypeField.parseFromStringType(fieldName, type));
                }
                return results.values();
            };
        }


        static RowMapper<MetadataEntity> getRowMapper() {
            return (rs, rowNum) -> {
                MetadataEntity entity = getMetadataEntity(rs);
                MetadataClass metadataClass = getMetadataClass(rs);
                entity.setClassEntity(metadataClass);
                return entity;
            };
        }

        static ResultSetExtractor<Collection<MetadataEntity>> getMetadataEntityWithFolderTreeExtractor() {
            return DaoHelper.getFolderTreeExtractor(ENTITY_ID.name(), FOLDER_ID.name(), PARENT_FOLDER_ID.name(),
                    MetadataEntityParameters::getMetadataEntity, MetadataEntityParameters::fillFolders);
        }

        private static void fillFolders(final MetadataEntity metadataEntity, final Map<Long, Folder> folders) {
            metadataEntity.setParent(DaoHelper.fillFolder(folders, metadataEntity.getParent()));
        }

        private static MetadataEntity getMetadataEntity(final ResultSet rs, final Folder folder) {
            try {
                MetadataEntity metadataEntity = getMetadataEntity(rs);
                if (folder != null) {
                    metadataEntity.setParent(folder);
                }
                return metadataEntity;
            } catch (SQLException e) {
                throw new IllegalArgumentException();
            }
        }

        private static MetadataEntity getMetadataEntity(ResultSet rs) throws SQLException {
            MetadataEntity entity = new MetadataEntity();
            entity.setId(rs.getLong(ENTITY_ID.name()));
            long parentId = rs.getLong(PARENT_ID.name());
            if(!rs.wasNull()) {
                Folder folder = new Folder();
                folder.setId(parentId);
                entity.setParent(folder);
            }
            entity.setName(rs.getString(ENTITY_NAME.name()));
            entity.setExternalId(rs.getString(EXTERNAL_ID.name()));
            entity.setData(MetadataDao.MetadataParameters.parseData(rs.getString(DATA.name())));
            return entity;
        }

        private static MetadataClass getMetadataClass(ResultSet rs) throws SQLException {
            MetadataClass metadataClass = new MetadataClass();
            metadataClass.setId(rs.getLong(CLASS_ID.name()));
            metadataClass.setName(rs.getString(CLASS_NAME.name()));
            String externalClassName = rs.getString(EXTERNAL_CLASS_NAME.name());
            if (!rs.wasNull()) {
                metadataClass.setFireCloudClassName(FireCloudClass.valueOf(externalClassName));
            }
            return metadataClass;
        }
    }

    @Required
    public void setMetadataEntitySequence(String metadataEntitySequence) {
        this.metadataEntitySequence = metadataEntitySequence;
    }

    @Required
    public void setCreateMetadataEntityQuery(String createMetadataEntityQuery) {
        this.createMetadataEntityQuery = createMetadataEntityQuery;
    }

    @Required
    public void setUpdateMetadataEntityQuery(String updateMetadataEntityQuery) {
        this.updateMetadataEntityQuery = updateMetadataEntityQuery;
    }

    @Required
    public void setUpdateMetadataEntityDataKeyQuery(String updateMetadataEntityDataKeyQuery) {
        this.updateMetadataEntityDataKeyQuery = updateMetadataEntityDataKeyQuery;
    }

    @Required
    public void setLoadAllMetadataEntitiesQuery(String loadAllMetadataEntitiesQuery) {
        this.loadAllMetadataEntitiesQuery = loadAllMetadataEntitiesQuery;
    }

    @Required
    public void setLoadMetadataEntityByIdQuery(String loadMetadataEntityByIdQuery) {
        this.loadMetadataEntityByIdQuery = loadMetadataEntityByIdQuery;
    }

    @Required
    public void setLoadRootMetadataEntityQuery(String loadRootMetadataEntityQuery) {
        this.loadRootMetadataEntityQuery = loadRootMetadataEntityQuery;
    }

    @Required
    public void setLoadMetadataEntityByClassNameAndFolderIdQuery(String loadMetadataEntityByClassNameAndFolderIdQuery) {
        this.loadMetadataEntityByClassNameAndFolderIdQuery = loadMetadataEntityByClassNameAndFolderIdQuery;
    }

    @Required
    public void setDeleteMetadataEntityDataKeyQuery(String deleteMetadataEntityDataKeyQuery) {
        this.deleteMetadataEntityDataKeyQuery = deleteMetadataEntityDataKeyQuery;
    }

    @Required
    public void setDeleteMetadataEntityItemQuery(String deleteMetadataEntityItemQuery) {
        this.deleteMetadataEntityItemQuery = deleteMetadataEntityItemQuery;
    }

    @Required
    public void setRecursiveFilterQuery(String recursiveFilterQuery) {
        this.recursiveFilterQuery = recursiveFilterQuery;
    }

    @Required
    public void setBaseFilterQuery(String baseFilterQuery) {
        this.baseFilterQuery = baseFilterQuery;
    }

    @Required
    public void setSearchClauseQuery(String searchClauseQuery) {
        this.searchClauseQuery = searchClauseQuery;
    }

    @Required
    public void setRecursiveFilterCountQuery(String recursiveFilterCountQuery) {
        this.recursiveFilterCountQuery = recursiveFilterCountQuery;
    }

    @Required
    public void setBaseFilterCountQuery(String baseFilterCountQuery) {
        this.baseFilterCountQuery = baseFilterCountQuery;
    }

    @Required
    public void setLoadMetadataKeysQuery(String loadMetadataKeysQuery) {
        this.loadMetadataKeysQuery = loadMetadataKeysQuery;
    }

    @Required
    public void setLoadByExternalIdsQuery(String loadByExternalIdsQuery) {
        this.loadByExternalIdsQuery = loadByExternalIdsQuery;
    }

    @Required
    public void setLoadBylIdsQuery(String loadBylIdsQuery) {
        this.loadBylIdsQuery = loadBylIdsQuery;
    }

    @Required
    public void setLoadAllReferencesQuery(String loadAllReferencesQuery) {
        this.loadAllReferencesQuery = loadAllReferencesQuery;
    }

    @Required
    public void setLoadMetadataKeysRecursiveQuery(String loadMetadataKeysRecursiveQuery) {
        this.loadMetadataKeysRecursiveQuery = loadMetadataKeysRecursiveQuery;
    }

    @Required
    public void setLoadEntitiesInProjectQuery(String loadEntitiesInProjectQuery) {
        this.loadEntitiesInProjectQuery = loadEntitiesInProjectQuery;
    }

    @Required
    public void setDeleteMetadataInFolderQuery(String deleteMetadataInFolderQuery) {
        this.deleteMetadataInFolderQuery = deleteMetadataInFolderQuery;
    }

    @Required
    public void setExternalIdClauseQuery(String externalIdClauseQuery) {
        this.externalIdClauseQuery = externalIdClauseQuery;
    }

    @Required
    public void setInsertCopiesOfExistentMetadataEntitiesQuery(String insertCopiesOfExistentMetadataEntitiesQuery) {
        this.insertCopiesOfExistentMetadataEntitiesQuery = insertCopiesOfExistentMetadataEntitiesQuery;
    }

    @Required
    public void setDeleteMetadataEntitiesQuery(String deleteMetadataEntitiesQuery) {
        this.deleteMetadataEntitiesQuery = deleteMetadataEntitiesQuery;
    }

    @Required
    public void setDeleteMetadataClassInProjectQuery(String deleteMetadataClassInProjectQuery) {
        this.deleteMetadataClassInProjectQuery = deleteMetadataClassInProjectQuery;
    }

    @Required
    public void setLoadMetadataEntityWithParentsQuery(String loadMetadataEntityWithParentsQuery) {
        this.loadMetadataEntityWithParentsQuery = loadMetadataEntityWithParentsQuery;
    }
}
