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

import java.io.InputStream;
import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.jdbc.core.support.SqlLobValue;
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.pipeline.ToolWithIssuesCount;

public class ToolDao extends NamedParameterJdbcDaoSupport {

    private String toolSequence;
    private String toolIconSequence;

    private String createToolQuery;
    private String updateToolQuery;
    private String loadToolQuery;
    private String loadAllToolsQuery;
    private String loadToolsByGroupQuery;
    private String loadToolsWithIssueCountByGroupQuery;
    private String deleteToolQuery;
    private String loadToolByRegistryAndImageQuery;
    private String loadToolsFromOtherRegistriesByImageQuery;
    private String loadToolByGroupAndImageQuery;

    private String deleteToolIconQuery;
    private String updateToolIconQuery;
    private String updateToolIconIdQuery;
    private String loadToolIconQuery;

    @Autowired
    private DaoHelper daoHelper;

    @Autowired
    private MessageHelper messageHelper;

    /**
     * Saves provided tool to a database
     * @param tool tool to be saved
     * @throws IllegalArgumentException if tool with the same name already exist
     *         in a registry with the same registry id
     **/
    @Transactional(propagation = Propagation.MANDATORY)
    public void createTool(Tool tool) {
        Assert.isNull(loadTool(tool.getRegistryId(), tool.getImage()),
                messageHelper.getMessage(MessageConstants.ERROR_TOOL_ALREADY_EXIST, tool.getRegistry(), tool.getImage())
        );
        tool.setId(daoHelper.createId(toolSequence));
        getNamedParameterJdbcTemplate().update(createToolQuery, getParameters(tool, getConnection()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateTool(Tool tool) {
        getNamedParameterJdbcTemplate().update(updateToolQuery, getParameters(tool, getConnection()));
    }

    public List<Tool> loadAllTools(Long registryId) {
        return loadAllTools(registryId, null);
    }

    public List<Tool> loadAllTools(List<String> labels) {
        return loadAllTools(null, labels);
    }

    public List<Tool> loadAllTools() {
        return loadAllTools(null, null);
    }

    /**
     * Loads all tools with specified registryId and labels
     * @param registryId tool's registry id, optional, if it isn't present load tools from all registries
     * @param labels labels that tools should contain (at least one lof labels), optional, if isn't present
     *               load tools without filtering by labels
     * @return list of matched {@link Tool}
     **/
    @Transactional(propagation = Propagation.SUPPORTS)
    public List<Tool> loadAllTools(Long registryId, List<String> labels) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        Array labelsSqlArray = DaoHelper.mapListToSqlArray(labels, getConnection());
        params.addValue(ToolParameters.LABELS_TO_SEARCH.name(), labelsSqlArray);
        params.addValue(ToolParameters.REGISTRY_ID.name(), registryId, Types.BIGINT);
        return getNamedParameterJdbcTemplate().query(loadAllToolsQuery, params, getRowMapper());
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public Tool loadTool(Long registryId, String image) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(ToolParameters.IMAGE.name(), image);
        params.addValue(ToolParameters.REGISTRY_ID.name(), registryId, Types.BIGINT);
        List<Tool> items = getNamedParameterJdbcTemplate()
                .query(loadToolByRegistryAndImageQuery, params, getRowMapper());
        Assert.isTrue(items.size() <= 1,
                messageHelper.getMessage(MessageConstants.ERROR_NUMBER_OF_TOOLS_WITH_IMAGE_GREATER_THEN_ONE,
                        items.stream().map(Tool::getRegistry).collect(Collectors.toList()))
        );
        return !items.isEmpty() ? items.get(0) : null;
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public Optional<Tool> loadToolByGroupAndImage(Long groupId, String image) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(ToolParameters.IMAGE.name(), image);
        params.addValue(ToolParameters.TOOL_GROUP_ID.name(), groupId);

        return getNamedParameterJdbcTemplate().query(loadToolByGroupAndImageQuery, params, getRowMapper())
            .stream().findFirst();
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<Tool> loadToolsByGroup(Long groupId) {
        return getJdbcTemplate().query(loadToolsByGroupQuery, getRowMapper(), groupId);
    }

    public List<ToolWithIssuesCount> loadToolsWithIssuesCountByGroup(Long groupId) {
        return getJdbcTemplate().query(loadToolsWithIssueCountByGroupQuery, getRowMapperWithIssuesCount(), groupId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteTool(Long toolId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(ToolParameters.ID.name(), toolId);
        getNamedParameterJdbcTemplate().update(deleteToolQuery, params);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long updateIcon(long toolId, String fileName, byte[] image) {
        long iconId = daoHelper.createId(toolIconSequence);

        getJdbcTemplate().update(deleteToolIconQuery, toolId);
        getJdbcTemplate().update(updateToolIconQuery, ps -> {
            ps.setLong(1, iconId);
            ps.setLong(2, toolId);
            ps.setString(3, fileName);
            ps.setBytes(4, image);
        });
        getJdbcTemplate().update(updateToolIconIdQuery, iconId, toolId);

        return iconId;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteToolIcon(long toolId) {
        getJdbcTemplate().update(deleteToolIconQuery, toolId);
    }

    public Optional<Pair<String, InputStream>> loadIcon(long toolId) {
        LobHandler lobHandler = new DefaultLobHandler();

        List<Pair<String, InputStream>> res = getJdbcTemplate().query(loadToolIconQuery, (rs, i) -> new ImmutablePair<>(
            rs.getString(ToolIconParameters.FILE_NAME.name()),
            lobHandler.getBlobAsBinaryStream(rs, ToolIconParameters.ICON.name())
        ), toolId);

        return res.isEmpty() ? Optional.empty() : Optional.ofNullable(res.get(0));
    }

    public Tool loadTool(Long id) {
        List<Tool> result = getJdbcTemplate().query(loadToolQuery, getRowMapper(), id);
        if (CollectionUtils.isEmpty(result)) {
            return null;
        } else {
            return result.get(0);
        }
    }

    public List<Tool> loadWithSameImageNameFromOtherRegistries(String image, String registry) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(ToolParameters.IMAGE.name(), image);
        params.addValue(ToolParameters.REGISTRY.name(), registry);
        return getNamedParameterJdbcTemplate().query(loadToolsFromOtherRegistriesByImageQuery, params, getRowMapper());
    }

    enum ToolParameters {
        ID,
        IMAGE,
        CPU,
        RAM,
        REGISTRY_ID,
        REGISTRY,
        TOOL_GROUP_ID,
        SECRET_NAME,
        LABELS,
        ENDPOINTS,
        DESCRIPTION,
        SHORT_DESCRIPTION,
        DEFAULT_COMMAND,
        LABELS_TO_SEARCH,
        OWNER,
        DISK,
        INSTANCE_TYPE,
        LINK,
        ICON_ID
    }

    enum ToolIconParameters {
        ICON_ID,
        ICON,
        FILE_NAME,
        TOOL_ID;

        private static MapSqlParameterSource getParameters(long toolId, long iconId, String fileName, byte[] image) {
            MapSqlParameterSource params = new MapSqlParameterSource();

            params.addValue(ICON_ID.name(), iconId);
            params.addValue(TOOL_ID.name(), toolId);
            params.addValue(ICON.name(), new SqlLobValue(image));
            params.addValue(FILE_NAME.name(), fileName);

            return params;
        }
    }

    private static MapSqlParameterSource getParameters(Tool tool, Connection connection) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(ToolParameters.ID.name(), tool.getId());
        params.addValue(ToolParameters.IMAGE.name(), tool.getImage());
        params.addValue(ToolParameters.CPU.name(), tool.getCpu());
        params.addValue(ToolParameters.RAM.name(), tool.getRam());
        params.addValue(ToolParameters.REGISTRY_ID.name(), tool.getRegistryId());
        params.addValue(ToolParameters.TOOL_GROUP_ID.name(), tool.getToolGroupId());
        params.addValue(ToolParameters.DESCRIPTION.name(), tool.getDescription());
        params.addValue(ToolParameters.SHORT_DESCRIPTION.name(), tool.getShortDescription());
        params.addValue(ToolParameters.DEFAULT_COMMAND.name(), tool.getDefaultCommand());
        params.addValue(ToolParameters.OWNER.name(), tool.getOwner());
        params.addValue(ToolParameters.DISK.name(), tool.getDisk());
        params.addValue(ToolParameters.INSTANCE_TYPE.name(), tool.getInstanceType());
        params.addValue(ToolParameters.LINK.name(), tool.getLink());
        Array labelsSqlArray = DaoHelper.mapListToSqlArray(tool.getLabels(), connection);
        params.addValue(ToolParameters.LABELS.name(), labelsSqlArray);

        Array endpointsSqlArray = DaoHelper.mapListToSqlArray(tool.getEndpoints(), connection);
        params.addValue(ToolParameters.ENDPOINTS.name(), endpointsSqlArray);

        return params;
    }

    private static RowMapper<Tool> getRowMapper() {
        return (rs, rowNum) -> {
            Tool tool = new Tool();
            return basicInitTool(rs, tool);
        };
    }

    private static RowMapper<ToolWithIssuesCount> getRowMapperWithIssuesCount() {
        return (rs, rowNum) -> {
            ToolWithIssuesCount tool = new ToolWithIssuesCount();
            basicInitTool(rs, tool);
            tool.setIssuesCount(rs.getLong("issues_count"));
            return tool;
        };
    }

    private static Tool basicInitTool(ResultSet rs, Tool tool) throws SQLException {
        tool.setId(rs.getLong(ToolParameters.ID.name()));
        tool.setImage(rs.getString(ToolParameters.IMAGE.name()));
        tool.setCpu(rs.getString(ToolParameters.CPU.name()));
        tool.setRam(rs.getString(ToolParameters.RAM.name()));
        tool.setRegistryId(rs.getLong(ToolParameters.REGISTRY_ID.name()));
        tool.setToolGroupId(rs.getLong(ToolParameters.TOOL_GROUP_ID.name()));
        tool.setRegistry(rs.getString(ToolParameters.REGISTRY.name()));
        tool.setSecretName(rs.getString(ToolParameters.SECRET_NAME.name()));
        tool.setDescription(rs.getString(ToolParameters.DESCRIPTION.name()));
        tool.setShortDescription(rs.getString(ToolParameters.SHORT_DESCRIPTION.name()));
        tool.setDefaultCommand(rs.getString(ToolParameters.DEFAULT_COMMAND.name()));
        tool.setOwner(rs.getString(ToolParameters.OWNER.name()));
        tool.setDisk(rs.getInt(ToolParameters.DISK.name()));
        tool.setInstanceType(rs.getString(ToolParameters.INSTANCE_TYPE.name()));
        long link = rs.getLong(ToolParameters.LINK.name());
        if (!rs.wasNull()) {
            tool.setLink(link);
        }

        long longVal = rs.getLong(ToolParameters.ICON_ID.name());
        tool.setHasIcon(!rs.wasNull());
        tool.setIconId(longVal);

        Array labelsSqlArray = rs.getArray(ToolParameters.LABELS.name());
        if (labelsSqlArray != null) {
            List<String> labels = Arrays.asList((String[]) labelsSqlArray.getArray());
            tool.setLabels(labels);
        }

        Array endpointsSqlArray = rs.getArray(ToolParameters.ENDPOINTS.name());
        if (endpointsSqlArray != null) {
            List<String> endpoints = Arrays.asList((String[]) endpointsSqlArray.getArray());
            tool.setEndpoints(endpoints);
        }
        //restore parent hierarchy
        if (tool.getToolGroupId() != null) {
            tool.setParent(new ToolGroup(tool.getToolGroupId()));
            if (tool.getRegistryId() != null) {
                tool.getParent().setParent(new DockerRegistry(tool.getRegistryId()));
            }
        }
        return tool;
    }

    @Required
    public void setToolSequence(String toolSequence) {
        this.toolSequence = toolSequence;
    }

    @Required
    public void setToolIconSequence(String toolIconSequence) {
        this.toolIconSequence = toolIconSequence;
    }

    @Required
    public void setCreateToolQuery(String createToolQuery) {
        this.createToolQuery = createToolQuery;
    }

    @Required
    public void setLoadAllToolsQuery(String loadAllToolsQuery) {
        this.loadAllToolsQuery = loadAllToolsQuery;
    }

    @Required
    public void setDeleteToolQuery(String deleteToolQuery) {
        this.deleteToolQuery = deleteToolQuery;
    }

    @Required
    public void setLoadToolByRegistryAndImageQuery(String loadToolByRegistryAndImageQuery) {
        this.loadToolByRegistryAndImageQuery = loadToolByRegistryAndImageQuery;
    }

    @Required
    public void setUpdateToolQuery(String updateToolQuery) {
        this.updateToolQuery = updateToolQuery;
    }

    @Required
    public void setLoadToolQuery(String loadToolQuery) {
        this.loadToolQuery = loadToolQuery;
    }

    @Required
    public void setLoadToolsByGroupQuery(String loadToolsByGroupQuery) {
        this.loadToolsByGroupQuery = loadToolsByGroupQuery;
    }

    @Required
    public void setLoadToolByGroupAndImageQuery(String loadToolByGroupAndImageQuery) {
        this.loadToolByGroupAndImageQuery = loadToolByGroupAndImageQuery;
    }

    @Required
    public void setLoadToolsFromOtherRegistriesByImageQuery(String loadToolsFromOtherRegistriesByImageQuery) {
        this.loadToolsFromOtherRegistriesByImageQuery = loadToolsFromOtherRegistriesByImageQuery;
    }

    @Required
    public void setLoadToolsWithIssueCountByGroupQuery(String loadToolsWithIssueCountByGroupQuery) {
        this.loadToolsWithIssueCountByGroupQuery = loadToolsWithIssueCountByGroupQuery;
    }

    @Required
    public void setUpdateToolIconQuery(String updateToolIconQuery) {
        this.updateToolIconQuery = updateToolIconQuery;
    }

    @Required
    public void setUpdateToolIconIdQuery(String updateToolIconIdQuery) {
        this.updateToolIconIdQuery = updateToolIconIdQuery;
    }

    @Required
    public void setLoadToolIconQuery(String loadToolIconQuery) {
        this.loadToolIconQuery = loadToolIconQuery;
    }

    @Required
    public void setDeleteToolIconQuery(String deleteToolIconQuery) {
        this.deleteToolIconQuery = deleteToolIconQuery;
    }
}
