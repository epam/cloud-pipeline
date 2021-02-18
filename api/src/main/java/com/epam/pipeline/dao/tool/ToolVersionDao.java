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

package com.epam.pipeline.dao.tool;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.configuration.ConfigurationEntry;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ToolVersionDao extends NamedParameterJdbcDaoSupport {

    private String toolVersionSequenceQuery;
    private String createToolVersionQuery;
    private String updateToolVersionQuery;
    private String deleteToolVersionQuery;
    private String deleteToolVersionsQuery;
    private String loadToolVersionQuery;
    private String loadToolVersionSettingsQuery;
    private String loadToolSettingsQuery;
    private String loadToolVersionListSettingsQuery;
    private String createToolVersionWithSettingsQuery;
    private String updateToolVersionWithSettingsQuery;

    @Autowired
    private DaoHelper daoHelper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void createToolVersion(ToolVersion toolVersion) {
        toolVersion.setId(daoHelper.createId(toolVersionSequenceQuery));
        getNamedParameterJdbcTemplate()
                .update(createToolVersionQuery, ToolVersionParameters.getParameters(toolVersion));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateToolVersion(ToolVersion toolVersion) {
        getNamedParameterJdbcTemplate()
                .update(updateToolVersionQuery, ToolVersionParameters.getParameters(toolVersion));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteToolVersion(Long toolId, String version) {
        getJdbcTemplate().update(deleteToolVersionQuery, toolId, version);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteToolVersions(Long toolId) {
        getJdbcTemplate().update(deleteToolVersionsQuery, toolId);
    }

    public Optional<ToolVersion> loadToolVersion(Long toolId, String version) {
        return getJdbcTemplate()
                .query(loadToolVersionQuery, ToolVersionParameters.getRowMapper(), toolId, version)
                .stream()
                .findFirst();
    }

    public List<ToolVersion> loadToolWithSettings(Long toolId) {
        return getJdbcTemplate()
                .query(loadToolSettingsQuery, ToolVersionParameters.getRowMapperWithSettings(), toolId);
    }

    public Optional<ToolVersion> loadToolVersionWithSettings(Long toolId, String version) {
        return getJdbcTemplate()
                .query(loadToolVersionSettingsQuery, ToolVersionParameters.getRowMapperWithSettings(), toolId, version)
                .stream()
                .findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createToolVersionWithSettings(ToolVersion toolVersion) {
        toolVersion.setId(daoHelper.createId(toolVersionSequenceQuery));
        getNamedParameterJdbcTemplate().update(createToolVersionWithSettingsQuery,
                        ToolVersionParameters.getParametersWithSettings(toolVersion));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateToolVersionWithSettings(ToolVersion toolVersion) {
        getNamedParameterJdbcTemplate().update(updateToolVersionWithSettingsQuery,
                        ToolVersionParameters.getParametersWithSettings(toolVersion));
    }

    public Map<String, ToolVersion> loadToolVersions(final Long toolId, final List<String> versions) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(ToolVersionParameters.TOOL_ID.name(), toolId);
        params.addValue("VERSIONS", versions);
        return getNamedParameterJdbcTemplate()
                .query(loadToolVersionListSettingsQuery, params, ToolVersionParameters.getRowMapper())
                .stream()
                .collect(Collectors.toMap(ToolVersion::getVersion, Function.identity()));
    }

    enum ToolVersionParameters {
        ID,
        TOOL_ID,
        VERSION,
        DIGEST,
        SIZE,
        MODIFIED_DATE,
        SETTINGS;

        private static MapSqlParameterSource getParameters(ToolVersion toolVersion) {
            return getInitialParameters(toolVersion)
                    .addValue(ToolVersionParameters.DIGEST.name(), toolVersion.getDigest())
                    .addValue(ToolVersionParameters.SIZE.name(), toolVersion.getSize())
                    .addValue(ToolVersionParameters.MODIFIED_DATE.name(), toolVersion.getModificationDate());
        }

        private static RowMapper<ToolVersion> getRowMapper() {
            return (rs, rowNum) -> {
                ToolVersion toolVersion = getInitialToolVersion(rs);
                toolVersion.setDigest(rs.getString(ToolVersionParameters.DIGEST.name()));
                Timestamp timestamp = rs.getTimestamp(ToolVersionParameters.MODIFIED_DATE.name());
                if (!rs.wasNull()) {
                    toolVersion.setModificationDate(new Date(timestamp.getTime()));
                }
                return toolVersion;
            };
        }

        private static RowMapper<ToolVersion> getRowMapperWithSettings() {
            return (rs, rowNum) -> {
                ToolVersion toolVersion = getInitialToolVersion(rs);
                toolVersion.setSettings(parseData(rs.getString(SETTINGS.name())));
                return toolVersion;
            };
        }

        private static MapSqlParameterSource getParametersWithSettings(ToolVersion toolVersion) {
            return getInitialParameters(toolVersion)
                    .addValue(ToolVersionParameters.SETTINGS.name(),
                            JsonMapper.convertDataToJsonStringForQuery(toolVersion.getSettings()));
        }

        private static ToolVersion getInitialToolVersion(ResultSet rs) throws SQLException {
            return ToolVersion
                    .builder()
                    .id(rs.getLong(ToolVersionParameters.ID.name()))
                    .toolId(rs.getLong(ToolVersionParameters.TOOL_ID.name()))
                    .version(rs.getString(ToolVersionParameters.VERSION.name()))
                    .size(rs.getLong(ToolVersionParameters.SIZE.name()))
                    .build();
        }

        private static MapSqlParameterSource getInitialParameters(ToolVersion toolVersion) {
            return new MapSqlParameterSource()
                    .addValue(ToolVersionParameters.ID.name(), toolVersion.getId())
                    .addValue(ToolVersionParameters.TOOL_ID.name(), toolVersion.getToolId())
                    .addValue(ToolVersionParameters.VERSION.name(), toolVersion.getVersion());
        }

        private static List<ConfigurationEntry> parseData(String data) {
            return JsonMapper.parseData(data, new TypeReference<List<ConfigurationEntry>>() {});
        }
    }

    @Required
    public void setToolVersionSequenceQuery(String toolVersionSequenceQuery) {
        this.toolVersionSequenceQuery = toolVersionSequenceQuery;
    }

    @Required
    public void setCreateToolVersionQuery(String createToolVersionQuery) {
        this.createToolVersionQuery = createToolVersionQuery;
    }

    @Required
    public void setUpdateToolVersionQuery(String updateToolVersionQuery) {
        this.updateToolVersionQuery = updateToolVersionQuery;
    }

    @Required
    public void setDeleteToolVersionsQuery(String deleteToolVersionsQuery) {
        this.deleteToolVersionsQuery = deleteToolVersionsQuery;
    }

    @Required
    public void setDeleteToolVersionQuery(String deleteToolVersionQuery) {
        this.deleteToolVersionQuery = deleteToolVersionQuery;
    }

    @Required
    public void setLoadToolVersionQuery(String loadToolVersionQuery) {
        this.loadToolVersionQuery = loadToolVersionQuery;
    }

    @Required
    public void setLoadToolVersionSettingsQuery(String loadToolVersionSettingsQuery) {
        this.loadToolVersionSettingsQuery = loadToolVersionSettingsQuery;
    }

    @Required
    public void setCreateToolVersionWithSettingsQuery(String createToolVersionWithSettingsQuery) {
        this.createToolVersionWithSettingsQuery = createToolVersionWithSettingsQuery;
    }

    @Required
    public void setUpdateToolVersionWithSettingsQuery(String updateToolVersionWithSettingsQuery) {
        this.updateToolVersionWithSettingsQuery = updateToolVersionWithSettingsQuery;
    }

    @Required
    public void setLoadToolSettingsQuery(final String loadToolSettingsQuery) {
        this.loadToolSettingsQuery = loadToolSettingsQuery;
    }

    @Required
    public void setLoadToolVersionListSettingsQuery(final String loadToolVersionListSettingsQuery) {
        this.loadToolVersionListSettingsQuery = loadToolVersionListSettingsQuery;
    }
}
