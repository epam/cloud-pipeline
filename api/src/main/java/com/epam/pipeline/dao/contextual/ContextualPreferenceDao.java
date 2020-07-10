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

package com.epam.pipeline.dao.contextual;

import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.preference.PreferenceType;
import com.epam.pipeline.entity.utils.DateUtils;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;

@RequiredArgsConstructor
public class ContextualPreferenceDao extends NamedParameterJdbcDaoSupport {

    private final String insertContextualPreferenceQuery;
    private final String loadContextualPreferenceQuery;
    private final String loadContextualPreferenceByNameQuery;
    private final String loadAllContextualPreferencesQuery;
    private final String deleteContextualPreferenceQuery;

    public ContextualPreference upsert(final ContextualPreference preference) {
        final ContextualPreference upsertingPreference = preference.withCreatedDate(DateUtils.now());
        getNamedParameterJdbcTemplate().update(
                insertContextualPreferenceQuery,
                Parameters.getParameters(upsertingPreference)
        );
        return upsertingPreference;
    }

    public Optional<ContextualPreference> load(final String name,
                                               final ContextualPreferenceExternalResource externalResource) {
        return getNamedParameterJdbcTemplate()
                .query(loadContextualPreferenceQuery, 
                        Parameters.getParameters(name, externalResource), 
                        Parameters.getRowMapper())
                .stream()
                .findFirst();
    }

    public List<ContextualPreference> load(final String name) {
        return getJdbcTemplate()
                .query(loadContextualPreferenceByNameQuery, Parameters.getRowMapper(), name);
    }

    public void delete(final String name, final ContextualPreferenceExternalResource externalResource) {
        getJdbcTemplate().update(deleteContextualPreferenceQuery, name, externalResource.getLevel().getId(),
                externalResource.getResourceId());
    }

    public List<ContextualPreference> loadAll() {
        return getJdbcTemplate()
                .query(loadAllContextualPreferencesQuery, Parameters.getRowMapper());
    }

    enum Parameters {
        NAME,
        VALUE,
        TYPE,
        CREATED_DATE,
        RESOURCE_ID,
        LEVEL;

        static MapSqlParameterSource getParameters(final ContextualPreference preference) {
            final MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(NAME.name(), preference.getName());
            params.addValue(VALUE.name(), preference.getValue());
            params.addValue(TYPE.name(), preference.getType().getId());
            params.addValue(CREATED_DATE.name(), preference.getCreatedDate());
            params.addValue(RESOURCE_ID.name(), preference.getResource().getResourceId());
            params.addValue(LEVEL.name(), preference.getResource().getLevel().getId());
            return params;
        }

        static MapSqlParameterSource getParameters(final String name, 
                                                   final ContextualPreferenceExternalResource externalResource) {
            final MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(NAME.name(), name);
            params.addValue(RESOURCE_ID.name(), externalResource.getResourceId());
            params.addValue(LEVEL.name(), externalResource.getLevel().getId());
            return params;
        }

        static RowMapper<ContextualPreference> getRowMapper() {
            return (rs, rowNum) -> {
                final String name = rs.getString(NAME.name());
                final String value = rs.getString(VALUE.name());
                final PreferenceType type = PreferenceType.getById(rs.getLong(TYPE.name()));
                final Date createdDate = new Date(rs.getTimestamp(CREATED_DATE.name()).getTime());
                final String resourceId = rs.getString(RESOURCE_ID.name());
                final ContextualPreferenceLevel level =
                        ContextualPreferenceLevel.getById(rs.getLong(LEVEL.name())).orElse(null);
                final ContextualPreferenceExternalResource externalResource = new ContextualPreferenceExternalResource(
                        level, resourceId);
                return new ContextualPreference(name, value, type, createdDate, externalResource);
            };
        }
    }
}
