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

package com.epam.pipeline.dao.preference;

import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.preference.PreferenceType;
import com.epam.pipeline.entity.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Slf4j
public class PreferenceDao extends NamedParameterJdbcDaoSupport {

    private static final String PREFERENCES_CACHE = "preferences";

    private String upsertPreferenceQuery;
    private String loadPreferenceByNameQuery;
    private String loadAllPreferencesQuery;
    private String loadVisiblePreferencesQuery;
    private String deletePreferenceQuery;

    @CachePut(value = PREFERENCES_CACHE, key="#preference.name")
    @Transactional(propagation = Propagation.MANDATORY)
    public Preference upsertPreference(Preference preference) {
        preference.setCreatedDate(DateUtils.now());

        getNamedParameterJdbcTemplate().update(
                upsertPreferenceQuery,
                PreferenceParameters.getParameters(preference)
        );

        return preference;
    }

    @CacheEvict(value = PREFERENCES_CACHE, key = "#name")
    @Transactional(propagation = Propagation.MANDATORY)
    public void deletePreference(String name) {
        getJdbcTemplate().update(deletePreferenceQuery, name);
    }

    @Cacheable(value = PREFERENCES_CACHE, key="#name")
    public Preference loadPreferenceByName(String name) {
        log.debug("Loading preference {} from DB.", name);
        List<Preference> items = getJdbcTemplate().query(
                loadPreferenceByNameQuery,
                PreferenceParameters.getRowMapper(),
                name.toLowerCase());
        return !items.isEmpty() ? items.get(0) : null;
    }

    public List<Preference> loadAllPreferences() {
        return getNamedParameterJdbcTemplate().query(loadAllPreferencesQuery, PreferenceParameters.getRowMapper());
    }

    /**
     * Loads only preferences, that are visible to non-admin users
     * @return a List of Preference
     */
    public List<Preference> loadVisiblePreferences() {
        return getNamedParameterJdbcTemplate().query(loadVisiblePreferencesQuery, PreferenceParameters.getRowMapper());
    }

    enum PreferenceParameters {
        PREFERENCE_NAME,
        CREATED_DATE,
        VALUE,
        PREFERENCE_GROUP,
        DEFAULT_VALUE,
        DESCRIPTION,
        VALIDATION_RULE,
        VISIBLE,
        PREFERENCE_TYPE;

        static MapSqlParameterSource getParameters(Preference preference) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(PREFERENCE_NAME.name(), preference.getName());
            params.addValue(CREATED_DATE.name(), preference.getCreatedDate());
            params.addValue(VALUE.name(), preference.getValue());
            params.addValue(PREFERENCE_GROUP.name(), preference.getPreferenceGroup());
            params.addValue(DESCRIPTION.name(), preference.getDescription());
            params.addValue(VISIBLE.name(), preference.isVisible());
            params.addValue(PREFERENCE_TYPE.name(), preference.getType().getId());
            return params;
        }

        static RowMapper<Preference> getRowMapper() {
            return (rs, rowNum) -> {
                Preference preference = new Preference();
                preference.setName(rs.getString(PREFERENCE_NAME.name()));
                preference.setCreatedDate(new Date(rs.getTimestamp(CREATED_DATE.name()).getTime()));
                preference.setValue(rs.getString(VALUE.name()));
                preference.setPreferenceGroup(rs.getString(PREFERENCE_GROUP.name()));
                preference.setDescription(rs.getString(DESCRIPTION.name()));
                preference.setVisible(rs.getBoolean(VISIBLE.name()));
                preference.setType(PreferenceType.getById(rs.getLong(PREFERENCE_TYPE.name())));
                return preference;
            };
        }
    }

    @Required
    public void setUpsertPreferenceQuery(String upsertPreferenceQuery) {
        this.upsertPreferenceQuery = upsertPreferenceQuery;
    }

    @Required
    public void setLoadPreferenceByNameQuery(String loadPreferenceByNameQuery) {
        this.loadPreferenceByNameQuery = loadPreferenceByNameQuery;
    }

    @Required
    public void setLoadAllPreferencesQuery(String loadAllPreferencesQuery) {
        this.loadAllPreferencesQuery = loadAllPreferencesQuery;
    }

    @Required
    public void setDeletePreferenceQuery(String deletePreferenceQuery) {
        this.deletePreferenceQuery = deletePreferenceQuery;
    }

    @Required
    public void setLoadVisiblePreferencesQuery(String loadVisiblePreferencesQuery) {
        this.loadVisiblePreferencesQuery = loadVisiblePreferencesQuery;
    }
}
