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

import java.sql.Types;
import java.util.List;
import java.util.Optional;

import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class ToolGroupDao extends NamedParameterJdbcDaoSupport {
    private String toolGroupSequenceName;
    private String createToolGroupQuery;
    private String loadToolGroupQuery;
    private String loadToolGroupByNameAndRegistryIdQuery;
    private String loadToolGroupsByNameAndRegistryNameQuery;
    private String loadAllToolGroupsQuery;
    private String loadToolGroupsByRegistryIdQuery;
    private String updateToolGroupQuery;
    private String deleteToolGroupQuery;

    @Autowired
    private DaoHelper daoHelper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void createToolGroup(ToolGroup group) {
        group.setId(daoHelper.createId(toolGroupSequenceName));
        getNamedParameterJdbcTemplate().update(createToolGroupQuery, ToolGroupParameters.getParameters(group));
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<ToolGroup> loadToolGroups() {
        return getJdbcTemplate().query(loadAllToolGroupsQuery, ToolGroupParameters.getRowMapper());
    }

    /**
     * Loads tool groups by name and registry name. If registry name is null, ignores it
     * @param groupName
     * @param registryName
     * @return list of tools
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public List<ToolGroup> loadToolGroupsByNameAndRegistryName(String groupName, String registryName) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(ToolGroupParameters.GROUP_NAME.name(), groupName);
        params.addValue("REGISTRY_NAME", registryName, Types.VARCHAR);

        return getNamedParameterJdbcTemplate().query(loadToolGroupsByNameAndRegistryNameQuery, params,
                                                     ToolGroupParameters.getRowMapper());
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<ToolGroup> loadToolGroups(Long registryId) {
        return getJdbcTemplate().query(loadToolGroupsByRegistryIdQuery, ToolGroupParameters.getRowMapper(), registryId);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public Optional<ToolGroup> loadToolGroup(Long id) {
        return getJdbcTemplate().query(loadToolGroupQuery, ToolGroupParameters.getRowMapper(), id).stream().findFirst();
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public Optional<ToolGroup> loadToolGroup(String name, Long registryId) {
        return getJdbcTemplate()
            .query(loadToolGroupByNameAndRegistryIdQuery, ToolGroupParameters.getRowMapper(), name, registryId)
            .stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteToolGroup(Long id) {
        getJdbcTemplate().update(deleteToolGroupQuery, id);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateToolGroup(ToolGroup toolGroup) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(ToolGroupParameters.ID.name(), toolGroup.getId());
        params.addValue(ToolGroupParameters.OWNER.name(), toolGroup.getOwner());
        params.addValue(ToolDao.ToolParameters.DESCRIPTION.name(), toolGroup.getDescription());

        getNamedParameterJdbcTemplate().update(updateToolGroupQuery, params);
    }

    private enum ToolGroupParameters {
        ID,
        GROUP_NAME,
        REGISTRY_ID,
        DESCRIPTION,
        OWNER;

        private static MapSqlParameterSource getParameters(ToolGroup group) {
            MapSqlParameterSource params = new MapSqlParameterSource();

            params.addValue(ID.name(), group.getId());
            params.addValue(GROUP_NAME.name(), group.getName());
            params.addValue(REGISTRY_ID.name(), group.getRegistryId());
            params.addValue(OWNER.name(), group.getOwner());
            params.addValue(DESCRIPTION.name(), group.getDescription());

            return params;
        }

        private static RowMapper<ToolGroup> getRowMapper() {
            return (rs, i) -> {
                ToolGroup group = new ToolGroup();

                group.setId(rs.getLong(ID.name()));
                group.setName(rs.getString(GROUP_NAME.name()));
                group.setRegistryId(rs.getLong(REGISTRY_ID.name()));
                group.setOwner(rs.getString(OWNER.name()));
                group.setDescription(rs.getString(DESCRIPTION.name()));

                return group;
            };
        }
    }

    @Required
    public void setToolGroupSequenceName(String toolGroupSequenceName) {
        this.toolGroupSequenceName = toolGroupSequenceName;
    }

    @Required
    public void setCreateToolGroupQuery(String createToolGroupQuery) {
        this.createToolGroupQuery = createToolGroupQuery;
    }

    @Required
    public void setLoadToolGroupQuery(String loadToolGroupQuery) {
        this.loadToolGroupQuery = loadToolGroupQuery;
    }

    @Required
    public void setLoadAllToolGroupsQuery(String loadAllToolGroupsQuery) {
        this.loadAllToolGroupsQuery = loadAllToolGroupsQuery;
    }

    @Required
    public void setLoadToolGroupsByRegistryIdQuery(String loadToolGroupsByRegistryIdQuery) {
        this.loadToolGroupsByRegistryIdQuery = loadToolGroupsByRegistryIdQuery;
    }

    @Required
    public void setDeleteToolGroupQuery(String deleteToolGroupQuery) {
        this.deleteToolGroupQuery = deleteToolGroupQuery;
    }

    @Required
    public void setLoadToolGroupByNameAndRegistryIdQuery(String loadToolGroupByNameAndRegistryIdQuery) {
        this.loadToolGroupByNameAndRegistryIdQuery = loadToolGroupByNameAndRegistryIdQuery;
    }

    @Required
    public void setUpdateToolGroupQuery(String updateToolGroupQuery) {
        this.updateToolGroupQuery = updateToolGroupQuery;
    }

    @Required
    public void setLoadToolGroupsByNameAndRegistryNameQuery(String loadToolGroupsByNameAndRegistryNameQuery) {
        this.loadToolGroupsByNameAndRegistryNameQuery = loadToolGroupsByNameAndRegistryNameQuery;
    }
}
