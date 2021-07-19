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

package com.epam.pipeline.dao.dts;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.dts.DtsRegistry;
import com.epam.pipeline.entity.dts.DtsStatus;
import com.epam.pipeline.entity.utils.DateUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.epam.pipeline.dao.DaoHelper.mapListToSqlArray;

public class DtsRegistryDao extends NamedParameterJdbcDaoSupport {

    private static final String JSON_KEY_TEMPLATE = "'{%s}'";
    private static final String JSON_KEY_REMOVE_OPERATOR = " #- ";
    private static final String PREFERENCE_KEYS_PLACEHOLDER = "@PREFERENCE_KEYS_EXPRESSION@";

    @Autowired
    private DaoHelper daoHelper;

    private String dtsRegistrySequence;
    private String loadAllDtsRegistriesQuery;
    private String loadDtsRegistryByIdQuery;
    private String loadDtsRegistryByNameQuery;
    private String createDtsRegistryQuery;
    private String updateDtsRegistryQuery;
    private String deleteDtsRegistryQuery;
    private String upsertDtsRegistryPreferencesQuery;
    private String deleteDtsRegistryPreferencesQuery;
    private String updateDtsRegistryHeartbeatQuery;
    private String updateDtsRegistryStatusQuery;

    public List<DtsRegistry> loadAll() {
        return ListUtils.emptyIfNull(getJdbcTemplate().query(loadAllDtsRegistriesQuery, DtsRegistryParameters.getRowMapper()));
    }

    public Optional<DtsRegistry> loadById(Long registryId) {
        return getJdbcTemplate().query(loadDtsRegistryByIdQuery, DtsRegistryParameters.getRowMapper(), registryId)
                .stream().findFirst();
    }

    public Optional<DtsRegistry> loadByName(final String registryName) {
        return getJdbcTemplate().query(loadDtsRegistryByNameQuery, DtsRegistryParameters.getRowMapper(), registryName)
                .stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long createRegistryId() {
        return daoHelper.createId(dtsRegistrySequence);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DtsRegistry create(DtsRegistry dtsRegistry) {
        dtsRegistry.setId(createRegistryId());
        dtsRegistry.setCreatedDate(DateUtils.now());
        getNamedParameterJdbcTemplate().update(createDtsRegistryQuery,
                DtsRegistryParameters.getParameters(dtsRegistry, getConnection(), true));
        return dtsRegistry;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void update(DtsRegistry dtsRegistry) {
        getNamedParameterJdbcTemplate().update(updateDtsRegistryQuery,
                DtsRegistryParameters.getParameters(dtsRegistry, getConnection()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(Long registryId) {
        getJdbcTemplate().update(deleteDtsRegistryQuery, registryId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void upsertPreferences(final Long registryId, final Map<String, String> preferences) {
        getNamedParameterJdbcTemplate().update(upsertDtsRegistryPreferencesQuery,
                                               DtsRegistryParameters.getPreferencesParameters(registryId, preferences));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deletePreferences(final Long registryId, final List<String> preferencesKeys) {
        getNamedParameterJdbcTemplate()
            .update(buildDeletePreferencesQuery(preferencesKeys),
                    DtsRegistryParameters.getParameters(registryId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateHeartbeat(final Long registryId, final LocalDateTime heartbeat, final DtsStatus status) {
        getNamedParameterJdbcTemplate()
                .update(updateDtsRegistryHeartbeatQuery,
                        DtsRegistryParameters.getHeartbeatParameters(registryId, heartbeat, status));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateStatus(final Long registryId, final DtsStatus status) {
        getNamedParameterJdbcTemplate()
                .update(updateDtsRegistryStatusQuery,
                        DtsRegistryParameters.getStatusParameters(registryId, status));
    }

    @Required
    public void setDtsRegistrySequence(String dtsRegistrySequence) {
        this.dtsRegistrySequence = dtsRegistrySequence;
    }

    @Required
    public void setLoadAllDtsRegistriesQuery(String loadAllDtsRegistriesQuery) {
        this.loadAllDtsRegistriesQuery = loadAllDtsRegistriesQuery;
    }

    @Required
    public void setLoadDtsRegistryByIdQuery(String loadDtsRegistryByIdQuery) {
        this.loadDtsRegistryByIdQuery = loadDtsRegistryByIdQuery;
    }

    @Required
    public void setLoadDtsRegistryByNameQuery(String loadDtsRegistryByNameQuery) {
        this.loadDtsRegistryByNameQuery = loadDtsRegistryByNameQuery;
    }

    @Required
    public void setCreateDtsRegistryQuery(String createDtsRegistryQuery) {
        this.createDtsRegistryQuery = createDtsRegistryQuery;
    }

    @Required
    public void setUpdateDtsRegistryQuery(String updateDtsRegistryQuery) {
        this.updateDtsRegistryQuery = updateDtsRegistryQuery;
    }

    @Required
    public void setDeleteDtsRegistryQuery(String deleteDtsRegistryQuery) {
        this.deleteDtsRegistryQuery = deleteDtsRegistryQuery;
    }

    @Required
    public void setUpsertDtsRegistryPreferencesQuery(final String upsertDtsRegistryPreferencesQuery) {
        this.upsertDtsRegistryPreferencesQuery = upsertDtsRegistryPreferencesQuery;
    }

    @Required
    public void setDeleteDtsRegistryPreferencesQuery(final String deleteDtsRegistryPreferencesQuery) {
        this.deleteDtsRegistryPreferencesQuery = deleteDtsRegistryPreferencesQuery;
    }

    @Required
    public void setUpdateDtsRegistryHeartbeatQuery(final String updateDtsRegistryHeartbeatQuery) {
        this.updateDtsRegistryHeartbeatQuery = updateDtsRegistryHeartbeatQuery;
    }

    @Required
    public void setUpdateDtsRegistryStatusQuery(final String updateDtsRegistryStatusQuery) {
        this.updateDtsRegistryStatusQuery = updateDtsRegistryStatusQuery;
    }

    enum DtsRegistryParameters {
        ID,
        NAME,
        SCHEDULABLE,
        CREATED_DATE,
        URL,
        HEARTBEAT,
        STATUS,
        PREFIXES,
        PREFERENCES,
        PREFERENCES_KEYS;

        static MapSqlParameterSource getParameters(final DtsRegistry dtsRegistry, final Connection connection) {
            return getParameters(dtsRegistry, connection, false);
        }

        static MapSqlParameterSource getParameters(final DtsRegistry dtsRegistry, final Connection connection,
                                                   final boolean extended) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(ID.name(), dtsRegistry.getId());
            params.addValue(URL.name(), dtsRegistry.getUrl());
            params.addValue(NAME.name(), dtsRegistry.getName());
            params.addValue(CREATED_DATE.name(), dtsRegistry.getCreatedDate());
            params.addValue(SCHEDULABLE.name(), dtsRegistry.isSchedulable());
            Array prefixesArray = mapListToSqlArray(dtsRegistry.getPrefixes(), connection);
            params.addValue(PREFIXES.name(), prefixesArray);
            if (extended) {
                params.addValue(PREFERENCES.name(), JsonMapper
                    .convertDataToJsonStringForQuery(MapUtils.emptyIfNull(dtsRegistry.getPreferences())));
            }
            return params;
        }

        static MapSqlParameterSource getPreferencesParameters(final Long dtsRegistryId,
                                                              final Map<String, String> preferences) {
            return getParameters(dtsRegistryId)
                    .addValue(PREFERENCES.name(),
                            JsonMapper.convertDataToJsonStringForQuery(MapUtils.emptyIfNull(preferences)));
        }

        public static MapSqlParameterSource getHeartbeatParameters(final Long registryId, final LocalDateTime heartbeat,
                                                                   final DtsStatus status) {
            return getStatusParameters(registryId, status)
                    .addValue(HEARTBEAT.name(), Timestamp.valueOf(heartbeat));
        }

        public static MapSqlParameterSource getStatusParameters(final Long registryId, final DtsStatus status) {
            return getParameters(registryId)
                    .addValue(STATUS.name(), status.getId());
        }

        static MapSqlParameterSource getParameters(final Long registryId) {
            return new MapSqlParameterSource()
                    .addValue(ID.name(), registryId);
        }

        static RowMapper<DtsRegistry> getRowMapper() {
            return (rs, rowNum) -> {
                DtsRegistry dtsRegistry = new DtsRegistry();
                dtsRegistry.setId(rs.getLong(ID.name()));
                dtsRegistry.setUrl(rs.getString(URL.name()));
                dtsRegistry.setName(rs.getString(NAME.name()));
                dtsRegistry.setCreatedDate(new Date(rs.getTimestamp(CREATED_DATE.name()).getTime()));
                dtsRegistry.setSchedulable(rs.getBoolean(SCHEDULABLE.name()));
                dtsRegistry.setHeartbeat(rs.getTimestamp(HEARTBEAT.name()).toLocalDateTime());
                dtsRegistry.setStatus(DtsStatus.findById(rs.getLong(STATUS.name())).orElse(DtsStatus.OFFLINE));
                Array prefixesSqlArray = rs.getArray(PREFIXES.name());
                List<String> prefixesList = Arrays.asList((String[]) prefixesSqlArray.getArray());
                dtsRegistry.setPrefixes(prefixesList);
                dtsRegistry.setPreferences(getPreferencesRowMapper().mapRow(rs, rowNum));
                return dtsRegistry;
            };
        }

        static RowMapper<Map<String, String>> getPreferencesRowMapper() {
            return (rs, rowNum) -> JsonMapper.parseData(rs.getString(PREFERENCES.name()),
                                                        new TypeReference<Map<String, String>>() {});
        }
    }

    private String buildDeletePreferencesQuery(final List<String> preferencesKeys) {
        final String keysRemovingExpression = CollectionUtils.isEmpty(preferencesKeys)
                                              ? String.format(JSON_KEY_TEMPLATE, StringUtils.EMPTY)
                                              : preferencesKeys.stream()
                                                  .map(preferenceKey -> String.format(JSON_KEY_TEMPLATE, preferenceKey))
                                                  .collect(Collectors.joining(JSON_KEY_REMOVE_OPERATOR));
        return deleteDtsRegistryPreferencesQuery.replaceFirst(PREFERENCE_KEYS_PLACEHOLDER, keysRemovingExpression);
    }
}
