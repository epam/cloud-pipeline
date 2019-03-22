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

package com.epam.pipeline.dao.configuration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.configuration.AbstractRunConfigurationEntry;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.pipeline.Folder;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class RunConfigurationDao extends NamedParameterJdbcDaoSupport {

    @Autowired
    private DaoHelper daoHelper;

    private String configSequence;
    private String createConfigQuery;
    private String updateConfigQuery;
    private String deleteConfigQuery;
    private String loadConfigQuery;
    private String loadAllConfigsQuery;
    private String loadAllRootConfigsQuery;
    private String updateConfigLocksQuery;
    private String loadConfigurationWithParentsQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public Long createId() {
        return daoHelper.createId(configSequence);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public RunConfiguration create(RunConfiguration configuration) {
        configuration.setId(createId());
        getNamedParameterJdbcTemplate()
                .update(createConfigQuery, ConfigurationParameters.getParameters(configuration));
        return configuration;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public RunConfiguration update(RunConfiguration configuration) {
        getNamedParameterJdbcTemplate()
                .update(updateConfigQuery, ConfigurationParameters.getParameters(configuration));
        return configuration;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(Long id) {
        getJdbcTemplate().update(deleteConfigQuery, id);
    }

    public RunConfiguration load(Long id) {
        List<RunConfiguration> items = getJdbcTemplate().query(loadConfigQuery,
                ConfigurationParameters.getRowMapper(), id);
        return CollectionUtils.isEmpty(items) ? null : items.get(0);
    }

    public List<RunConfiguration> loadAll() {
        return getJdbcTemplate().query(loadAllConfigsQuery, ConfigurationParameters.getRowMapper());
    }

    public List<RunConfiguration> loadRootEntities() {
        return getJdbcTemplate().query(loadAllRootConfigsQuery, ConfigurationParameters.getRowMapper());
    }

    public RunConfiguration loadConfigurationWithParents(final Long id) {
        return getJdbcTemplate().query(loadConfigurationWithParentsQuery,
                ConfigurationParameters.getConfigurationWithFolderTreeExtractor(), id)
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateLocks(List<Long> configIds, boolean isLocked) {
        daoHelper.updateLocks(updateConfigLocksQuery, configIds, isLocked);
    }

    public enum ConfigurationParameters {
        CONFIG_ID,
        CONFIG_NAME, CONFIG_FOLDER_ID,
        CONFIG_CREATED_DATE,
        CONFIG_DESCRIPTION,
        CONFIG_ENTRIES,
        CONFIG_LOCKED,
        CONFIG_OWNER,
        FOLDER_ID,
        PARENT_FOLDER_ID;

        static MapSqlParameterSource getParameters(RunConfiguration configuration) {
            MapSqlParameterSource params = new MapSqlParameterSource();

            params.addValue(CONFIG_ID.name(), configuration.getId());
            params.addValue(CONFIG_NAME.name(), configuration.getName());
            if (configuration.getParent() != null) {
                params.addValue(CONFIG_FOLDER_ID.name(), configuration.getParent().getId());
            } else {
                params.addValue(CONFIG_FOLDER_ID.name(), null);
            }
            params.addValue(CONFIG_CREATED_DATE.name(), configuration.getCreatedDate());
            params.addValue(CONFIG_OWNER.name(), configuration.getOwner());
            params.addValue(CONFIG_DESCRIPTION.name(), configuration.getDescription());
            params.addValue(CONFIG_LOCKED.name(), configuration.isLocked());
            params.addValue(CONFIG_ENTRIES.name(), entriesToJson(configuration.getEntries()));
            return params;
        }

        static RowMapper<RunConfiguration> getRowMapper() {
            return (rs, rowNum) -> getRunConfiguration(rs);
        }

        static ResultSetExtractor<Collection<RunConfiguration>> getConfigurationWithFolderTreeExtractor() {
            return DaoHelper.getFolderTreeExtractor(CONFIG_ID.name(), FOLDER_ID.name(), PARENT_FOLDER_ID.name(),
                    ConfigurationParameters::getRunConfiguration, ConfigurationParameters::fillFolders);
        }

        public static RunConfiguration getRunConfiguration(ResultSet rs) throws SQLException {
            RunConfiguration configuration = new RunConfiguration();
            configuration.setId(rs.getLong(CONFIG_ID.name()));
            configuration.setName(rs.getString(CONFIG_NAME.name()));
            configuration.setCreatedDate(new Date(rs.getTimestamp(CONFIG_CREATED_DATE.name()).getTime()));
            configuration.setDescription(rs.getString(CONFIG_DESCRIPTION.name()));
            configuration.setEntries(jsonToEntries(rs.getString(CONFIG_ENTRIES.name())));
            configuration.setOwner(rs.getString(CONFIG_OWNER.name()));
            configuration.setLocked(rs.getBoolean(CONFIG_LOCKED.name()));
            Long parentId = rs.getLong(CONFIG_FOLDER_ID.name());
            if (!rs.wasNull()) {
                configuration.setParent(new Folder(parentId));
            }
            return configuration;
        }

        private static void fillFolders(final RunConfiguration runConfiguration, final Map<Long, Folder> folders) {
            runConfiguration.setParent(DaoHelper.fillFolder(folders, runConfiguration.getParent()));
        }

        private static RunConfiguration getRunConfiguration(final ResultSet rs, final Folder folder) {
            try {
                RunConfiguration runConfiguration = getRunConfiguration(rs);
                if (folder != null) {
                    runConfiguration.setParent(folder);
                }
                return runConfiguration;
            } catch (SQLException e) {
                throw new IllegalArgumentException();
            }
        }

        private static List<AbstractRunConfigurationEntry> jsonToEntries(String data) {
            return JsonMapper.parseData(data, new TypeReference<List<AbstractRunConfigurationEntry>>() {});
        }

        private static String entriesToJson(List<AbstractRunConfigurationEntry> entries) {
            return JsonMapper.convertDataToJsonStringForQuery(entries);
        }

    }

    @Required
    public void setConfigSequence(String configSequence) {
        this.configSequence = configSequence;
    }

    @Required
    public void setCreateConfigQuery(String createConfigQuery) {
        this.createConfigQuery = createConfigQuery;
    }

    @Required
    public void setUpdateConfigQuery(String updateConfigQuery) {
        this.updateConfigQuery = updateConfigQuery;
    }

    @Required
    public void setDeleteConfigQuery(String deleteConfigQuery) {
        this.deleteConfigQuery = deleteConfigQuery;
    }

    @Required
    public void setLoadConfigQuery(String loadConfigQuery) {
        this.loadConfigQuery = loadConfigQuery;
    }

    @Required
    public void setLoadAllConfigsQuery(String loadAllConfigsQuery) {
        this.loadAllConfigsQuery = loadAllConfigsQuery;
    }

    @Required
    public void setLoadAllRootConfigsQuery(String loadAllRootConfigsQuery) {
        this.loadAllRootConfigsQuery = loadAllRootConfigsQuery;
    }

    @Required
    public void setUpdateConfigLocksQuery(String updateConfigLocksQuery) {
        this.updateConfigLocksQuery = updateConfigLocksQuery;
    }

    @Required
    public void setLoadConfigurationWithParentsQuery(String loadConfigurationWithParentsQuery) {
        this.loadConfigurationWithParentsQuery = loadConfigurationWithParentsQuery;
    }
}
