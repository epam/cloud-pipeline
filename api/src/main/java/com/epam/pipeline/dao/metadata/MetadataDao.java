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

package com.epam.pipeline.dao.metadata;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.MetadataEntryWithIssuesCount;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MetadataDao extends NamedParameterJdbcDaoSupport {

    private static final String KEY = "KEY";
    private static final String VALUE = "VALUE";

    private Pattern dataKeyPattern = Pattern.compile("@KEY@");
    private Pattern entitiesValuePatten = Pattern.compile("@ENTITIES@");

    private String createMetadataItemQuery;
    private String uploadMetadataItemQuery;
    private String uploadMetadataItemKeyQuery;
    private String loadMetadataItemQuery;
    private String loadMetadataItemsQuery;
    private String deleteMetadataItemQuery;
    private String deleteMetadataItemKeyQuery;
    private String loadMetadataItemsWithIssuesQuery;
    private String searchMetadataByClassAndKeyValueQuery;
    private String loadUniqueValuesFromEntitiesAttributes;

    @Transactional(propagation = Propagation.MANDATORY)
    public void registerMetadataItem(MetadataEntry metadataEntry) {
        getNamedParameterJdbcTemplate().update(createMetadataItemQuery,
                MetadataParameters.getParametersWithData(metadataEntry));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void uploadMetadataItemKey(EntityVO entityVO, String key, String value, String type) {
        MapSqlParameterSource parameters = MetadataParameters.getParameters(entityVO);
        parameters.addValue(KEY, String.format("{%s}", key));
        parameters.addValue(VALUE, JsonMapper.convertDataToJsonStringForQuery(new PipeConfValue(type, value)));
        getNamedParameterJdbcTemplate().update(uploadMetadataItemKeyQuery, parameters);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void uploadMetadataItem(MetadataEntry metadataEntry) {
        getNamedParameterJdbcTemplate().update(uploadMetadataItemQuery,
                MetadataParameters.getParametersWithData(metadataEntry));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteMetadataItem(EntityVO entityVO) {
        getJdbcTemplate().update(deleteMetadataItemQuery, entityVO.getEntityId(), entityVO.getEntityClass().name());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteMetadataItemKey(EntityVO entityVO, String key) {
        String query = dataKeyPattern.matcher(deleteMetadataItemKeyQuery)
                .replaceFirst(String.format("'%s'", key));
        getNamedParameterJdbcTemplate().update(query, MetadataParameters.getParameters(entityVO));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public MetadataEntry deleteMetadataItemKeys(MetadataEntry metadataEntry, Set<String> keysToDelete) {
        metadataEntry.getData().keySet().removeAll(keysToDelete);
        uploadMetadataItem(metadataEntry);
        return metadataEntry;
    }

    public List<String> loadUniqueValuesFromEntitiesAttribute(final AclClass entityClass, final String attributeKey) {
        return getNamedParameterJdbcTemplate().query(loadUniqueValuesFromEntitiesAttributes,
                                       MetadataParameters.getParametersForEntityAndKey(entityClass, attributeKey),
                                       MetadataParameters.getUniqueAttributesExtractor());
    }

    public MetadataEntry loadMetadataItem(EntityVO entity) {
        List<MetadataEntry> items = getJdbcTemplate().query(loadMetadataItemQuery,
                MetadataParameters.getRowMapper(), entity.getEntityId(), entity.getEntityClass().name());
        return items.isEmpty() ? null : items.get(0);
    }

    public boolean hasMetadata(EntityVO entity) {
        List<MetadataEntry> items = getJdbcTemplate().query(loadMetadataItemQuery,
                MetadataParameters.getRowMapper(), entity.getEntityId(), entity.getEntityClass().name());
        return !items.isEmpty() && items.get(0).getData() != null && !items.get(0).getData().isEmpty();
    }

    public List<MetadataEntry> loadMetadataItems(List<EntityVO> entities) {
        List<MetadataEntry> items = getNamedParameterJdbcTemplate().query(
                convertEntitiesToString(loadMetadataItemsQuery, entities),
                MetadataParameters.getParametersWithArrays(entities),
                MetadataParameters.getRowMapper());
        return items.isEmpty() ? null : items;
    }

    public List<MetadataEntryWithIssuesCount> loadMetadataItemsWithIssues(List<EntityVO> entities) {
        List<MetadataEntryWithIssuesCount> items = getNamedParameterJdbcTemplate()
                .query(convertEntitiesToString(loadMetadataItemsWithIssuesQuery, entities),
                        MetadataParameters.getParametersWithArrays(entities),
                        MetadataParameters.getRowMapperWithIssues());
        return items.isEmpty() ? null : items;
    }

    public List<EntityVO> searchMetadataByClassAndKeyValue(final AclClass entityClass,
                                                           final Map<String, PipeConfValue> indicator) {
        return getJdbcTemplate().query(searchMetadataByClassAndKeyValueQuery, MetadataParameters.getEntityVORowMapper(),
                        entityClass.name(), MetadataDao.convertDataToJsonStringForQuery(indicator));
    }

    public static String convertDataToJsonStringForQuery(Map<String, PipeConfValue> data) {
        return JsonMapper.convertDataToJsonStringForQuery(data);
    }

    private String convertEntitiesToString(String query, List<EntityVO> entities) {
        return entitiesValuePatten.matcher(query)
                .replaceAll(entities.stream()
                        .map(entityVO -> String.format("(%s,'%s')", entityVO.getEntityId(), entityVO.getEntityClass()))
                        .collect(Collectors.joining(",")));
    }

    @Required
    public void setLoadMetadataItemsWithIssuesQuery(String loadMetadataItemsWithIssuesQuery) {
        this.loadMetadataItemsWithIssuesQuery = loadMetadataItemsWithIssuesQuery;
    }

    @Required
    public void setUploadMetadataItemKeyQuery(String uploadMetadataItemKeyQuery) {
        this.uploadMetadataItemKeyQuery = uploadMetadataItemKeyQuery;
    }

    @Required
    public void setLoadMetadataItemQuery(String loadMetadataItemQuery) {
        this.loadMetadataItemQuery = loadMetadataItemQuery;
    }

    @Required
    public void setLoadMetadataItemsQuery(String loadMetadataItemsQuery) {
        this.loadMetadataItemsQuery = loadMetadataItemsQuery;
    }

    @Required
    public void setCreateMetadataItemQuery(String createMetadataItemQuery) {
        this.createMetadataItemQuery = createMetadataItemQuery;
    }

    @Required
    public void setDeleteMetadataItemQuery(String deleteMetadataItemQuery) {
        this.deleteMetadataItemQuery = deleteMetadataItemQuery;
    }

    @Required
    public void setDeleteMetadataItemKeyQuery(String deleteMetadataItemKeyQuery) {
        this.deleteMetadataItemKeyQuery = deleteMetadataItemKeyQuery;
    }

    @Required
    public void setUploadMetadataItemQuery(String uploadMetadataItemQuery) {
        this.uploadMetadataItemQuery = uploadMetadataItemQuery;
    }

    @Required
    public void setSearchMetadataByClassAndKeyValueQuery(String searchMetadataByClassAndKeyValueQuery) {
        this.searchMetadataByClassAndKeyValueQuery = searchMetadataByClassAndKeyValueQuery;
    }

    @Required
    public void setLoadUniqueValuesFromEntitiesAttributes(final String loadUniqueValuesFromEntitiesAttributes) {
        this.loadUniqueValuesFromEntitiesAttributes = loadUniqueValuesFromEntitiesAttributes;
    }

    public enum MetadataParameters {
        ENTITY_ID,
        ENTITY_CLASS,
        DATA,
        IDS,
        CLASSES;

        static MapSqlParameterSource getParameters(EntityVO entityVO) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(ENTITY_ID.name(), entityVO.getEntityId());
            params.addValue(ENTITY_CLASS.name(), entityVO.getEntityClass().name());
            return params;
        }

        static MapSqlParameterSource getParametersWithData(MetadataEntry metadataEntry) {
            MapSqlParameterSource params = getParameters(metadataEntry.getEntity());
            params.addValue(DATA.name(), convertDataToJsonStringForQuery(metadataEntry.getData()));
            return params;
        }

        static MapSqlParameterSource getParametersWithArrays(List<EntityVO> entities) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(IDS.name(), entities.stream().map(EntityVO::getEntityId).collect(Collectors.toList()));
            params.addValue(CLASSES.name(), entities.stream()
                    .map(entity -> entity.getEntityClass().name())
                    .collect(Collectors.toList()));
            return params;
        }

        static MapSqlParameterSource getParametersForEntityAndKey(final AclClass entityClass,
                                                                  final String attributeKey) {
            final MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(KEY, attributeKey);
            params.addValue(VALUE, VALUE.toLowerCase());
            params.addValue(ENTITY_CLASS.name(), entityClass.name());
            return params;
        }

        static RowMapper<MetadataEntry> getRowMapper() {
            return (rs, rowNum) -> {
                Long metadataEntityId = rs.getLong(ENTITY_ID.name());
                String metadataEntityClass = rs.getString(ENTITY_CLASS.name());
                return initMetadataItem(rs, metadataEntityId, metadataEntityClass);
            };
        }

        static RowMapper<MetadataEntryWithIssuesCount> getRowMapperWithIssues() {
            return (rs, rowNum) -> {
                Long metadataEntityId = rs.getLong(ENTITY_ID.name());
                String metadataEntityClass = rs.getString(ENTITY_CLASS.name());
                MetadataEntryWithIssuesCount metadataEntry = new MetadataEntryWithIssuesCount();
                metadataEntry.setEntity(new EntityVO(metadataEntityId, AclClass.valueOf(metadataEntityClass)));
                metadataEntry.setData(parseData(rs.getString(DATA.name())));
                metadataEntry.setIssuesCount(rs.getLong("issues_count"));
                return metadataEntry;
            };
        }

        static RowMapper<EntityVO> getEntityVORowMapper() {
            return (rs, rowNum) -> {
                Long entityId = rs.getLong(ENTITY_ID.name());
                String entityClass = rs.getString(ENTITY_CLASS.name());
                return new EntityVO(entityId, AclClass.valueOf(entityClass));
            };
        }

        private static ResultSetExtractor<List<String>> getUniqueAttributesExtractor() {
            return (ResultSet rs) -> {
                final List<String> values = new ArrayList<>();
                while (rs.next()) {
                    final String value = rs.getString(DATA.name().toLowerCase());
                    if (value != null) {
                        values.add(value.replace("\"", ""));
                    }
                }
                return values;
            };
        }

        private static MetadataEntry initMetadataItem(ResultSet rs, Long metadataItemId, String metadataEntityClass)
                throws SQLException {
            MetadataEntry metadataEntry = new MetadataEntry();
            metadataEntry.setEntity(new EntityVO(metadataItemId, AclClass.valueOf(metadataEntityClass)));
            metadataEntry.setData(parseData(rs.getString(DATA.name())));
            return metadataEntry;
        }

        public static Map<String, PipeConfValue> parseData(String data) {
            return JsonMapper.parseData(data, new TypeReference<Map<String, PipeConfValue>>() {});
        }
    }
}
