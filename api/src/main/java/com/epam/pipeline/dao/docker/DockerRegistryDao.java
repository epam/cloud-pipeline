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

package com.epam.pipeline.dao.docker;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class DockerRegistryDao extends NamedParameterJdbcDaoSupport {

    @Autowired
    private DaoHelper daoHelper;

    private String dockerRegistrySequence;
    private String createDockerRegistryQuery;
    private String loadAllDockerRegistriesQuery;
    private String loadDockerRegistriesWithSecurityScanEnabledQuery;
    private String loadDockerRegistriesWithCertsQuery;
    private String loadDockerRegistryByIdQuery;
    private String loadDockerRegistryByNameQuery;
    private String loadDockerRegistryByExtUrlQuery;
    private String deleteDockerRegistryQuery;
    private String updateDockerRegistryQuery;
    private String loadDockerRegistriesWithContentQuery;
    private String loadDockerRegistryWithContentQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public void createDockerRegistry(final DockerRegistry registry) {
        registry.setId(daoHelper.createId(dockerRegistrySequence));
        getNamedParameterJdbcTemplate().update(createDockerRegistryQuery, RepositoryProperty.getParameters(registry));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateDockerRegistry(final DockerRegistry registry) {
        getNamedParameterJdbcTemplate().update(updateDockerRegistryQuery, RepositoryProperty.getParameters(registry));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteDockerRegistry(final long id) {
        getJdbcTemplate().update(deleteDockerRegistryQuery, id);
    }

    public DockerRegistry loadDockerRegistry(String registryPath) {
        Collection<DockerRegistry> repositories = getJdbcTemplate()
                .query(loadDockerRegistryByNameQuery, RepositoryProperty.getDockerRegistryExtractor(), registryPath);
        return repositories.isEmpty() ? null : repositories.iterator().next();
    }

    /**
     * Loads {@link DockerRegistry} from database
     * @param registryExtUrl docker registry external url
     * @return {@link DockerRegistry} matched by external URL or null if there is no matched registry
     * */
    public DockerRegistry loadDockerRegistryByExternalUrl(String registryExtUrl) {
        Collection<DockerRegistry> repositories = getJdbcTemplate()
                .query(loadDockerRegistryByExtUrlQuery, RepositoryProperty.getDockerRegistryExtractor(),
                        registryExtUrl);
        return repositories.isEmpty() ? null : repositories.iterator().next();
    }

    public DockerRegistry loadDockerRegistry(long id) {
        Collection<DockerRegistry> repositories = getJdbcTemplate()
                .query(loadDockerRegistryByIdQuery, RepositoryProperty.getDockerRegistryExtractor(), id);
        return repositories.isEmpty() ? null : repositories.iterator().next();
    }

    public List<DockerRegistry> loadAllDockerRegistry() {
        return new ArrayList<>(
                getJdbcTemplate().query(loadAllDockerRegistriesQuery, RepositoryProperty.getDockerRegistryExtractor())
        );
    }

    public List<DockerRegistry> loadDockerRegistriesWithSecurityScanEnabled() {
        return new ArrayList<>(
            getJdbcTemplate().query(loadDockerRegistriesWithSecurityScanEnabledQuery,
                                    RepositoryProperty.getDockerRegistryExtractor())
        );
    }

    public List<DockerRegistry> listAllDockerRegistriesWithCerts() {
        return new ArrayList<>(
                getJdbcTemplate().query(loadDockerRegistriesWithCertsQuery,
                        RepositoryProperty.getDockerRegistryFullExtractor())
        );
    }

    public List<DockerRegistry> loadAllRegistriesContent() {
        return new ArrayList<>(
                getJdbcTemplate().query(loadDockerRegistriesWithContentQuery,
                        RepositoryProperty.getDockerRegistryFullExtractor())
        );
    }

    public DockerRegistry loadDockerRegistryTree(Long id) {
        Collection<DockerRegistry> repositories = getJdbcTemplate()
                .query(loadDockerRegistryWithContentQuery, RepositoryProperty.getDockerRegistryFullExtractor(), id);
        return repositories.isEmpty() ? null : repositories.iterator().next();
    }

    enum RepositoryProperty {
        REGISTRY_ID,
        REGISTRY_PATH,
        REGISTRY_DESCRIPTION,
        SECRET_NAME,
        CA_CERT,
        USER_NAME,
        PASSWORD,
        PIPELINE_AUTH,
        EXTERNAL_URL,
        OWNER,
        SECURITY_SCAN_ENABLED,
        TOOL_ID,
        TOOL_IMAGE,
        TOOL_LINK,
        TOOL_CPU,
        TOOL_RAM,
        TOOL_DESCRIPTION,
        TOOL_SHORT_DESCRIPTION,
        TOOL_LABELS,
        TOOL_ENDPOINTS,
        TOOL_DEFAULT_COMMAND,
        TOOL_OWNER,
        TOOL_ICON_ID,
        TOOL_ALLOW_SENSITIVE,
        GROUP_ID,
        GROUP_NAME,
        GROUP_DESCRIPTION,
        GROUP_OWNER;

        static MapSqlParameterSource getParameters(DockerRegistry repository) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(REGISTRY_ID.name(), repository.getId());
            params.addValue(REGISTRY_PATH.name(), repository.getPath());
            params.addValue(REGISTRY_DESCRIPTION.name(), repository.getDescription());
            params.addValue(SECRET_NAME.name(), repository.getSecretName());
            params.addValue(CA_CERT.name(), repository.getCaCert());
            params.addValue(USER_NAME.name(), repository.getUserName());
            params.addValue(PASSWORD.name(), repository.getPassword());
            params.addValue(OWNER.name(), repository.getOwner());
            params.addValue(PIPELINE_AUTH.name(), repository.isPipelineAuth());
            params.addValue(EXTERNAL_URL.name(), repository.getExternalUrl());
            params.addValue(SECURITY_SCAN_ENABLED.name(), repository.isSecurityScanEnabled());
            return params;
        }

        static RowMapper<DockerRegistry> getRowMapper() {
            return (rs, rowNum) -> {
                Long registryId = rs.getLong(REGISTRY_ID.name());
                return initDockerRegistry(rs, registryId);
            };
        }

        static ResultSetExtractor<Collection<DockerRegistry>> getDockerRegistryExtractor() {
            return rs -> {
                Map<Long, DockerRegistry> registries = new HashMap<>();
                while (rs.next()) {
                    Long registryId = rs.getLong(REGISTRY_ID.name());
                    DockerRegistry registry = registries.get(registryId);
                    if (registry == null) {
                        registry = initDockerRegistry(rs, registryId);
                    }
                    Long toolId = rs.getLong(TOOL_ID.name());
                    if (!rs.wasNull()) {
                        Tool tool = new Tool();
                        initTool(rs, registryId, registry, toolId, tool);
                        registry.getTools().add(tool);
                    }
                    registries.putIfAbsent(registryId, registry);
                }
                return registries.values();
            };
        }

        //TODO: rewrite to load only docker registries, leave tools and groups for manager layers
        static ResultSetExtractor<Collection<DockerRegistry>> getDockerRegistryFullExtractor() {
            return rs -> {
                Map<Long, DockerRegistry> registries = new HashMap<>();
                Map<Long, ToolGroup> groups = new HashMap<>();
                while (rs.next()) {
                    Long registryId = rs.getLong(REGISTRY_ID.name());
                    DockerRegistry registry = registries.get(registryId);
                    if (registry == null) {
                        registry = initDockerRegistry(rs, registryId);
                    }
                    Long groupId = rs.getLong(GROUP_ID.name());
                    if (!rs.wasNull()) {
                        ToolGroup group = groups.get(groupId);
                        if (group == null) {
                            group = new ToolGroup();
                            group.setId(groupId);
                            group.setName(rs.getString(GROUP_NAME.name()));
                            group.setDescription(rs.getString(GROUP_DESCRIPTION.name()));
                            group.setOwner(rs.getString(GROUP_OWNER.name()));
                            group.setTools(new ArrayList<>());
                            groups.put(groupId, group);
                            registry.getGroups().add(group);
                        }
                        Long toolId = rs.getLong(TOOL_ID.name());
                        if (!rs.wasNull()) {
                            Tool tool = new Tool();
                            initTool(rs, registryId, registry, toolId, tool);
                            tool.setOwner(rs.getString(TOOL_OWNER.name()));
                            group.getTools().add(tool);
                        }
                    }
                    registries.putIfAbsent(registryId, registry);
                }
                return registries.values();
            };
        }

        private static void initTool(ResultSet rs, Long registryId, DockerRegistry registry,
                Long toolId, Tool tool) throws SQLException {
            tool.setId(toolId);
            tool.setImage(rs.getString(TOOL_IMAGE.name()));
            tool.setCpu(rs.getString(TOOL_CPU.name()));
            tool.setRam(rs.getString(TOOL_RAM.name()));
            tool.setRegistryId(registryId);
            tool.setRegistry(registry.getPath());
            tool.setDescription(rs.getString(TOOL_DESCRIPTION.name()));
            tool.setShortDescription(rs.getString(TOOL_SHORT_DESCRIPTION.name()));
            tool.setAllowSensitive(rs.getBoolean(TOOL_ALLOW_SENSITIVE.name()));
            long toolLink = rs.getLong(TOOL_LINK.name());
            if (!rs.wasNull()) {
                tool.setLink(toolLink);
            }

            long longVal = rs.getLong(TOOL_ICON_ID.name());
            if (!rs.wasNull()) {
                tool.setIconId(longVal);
            }

            Array labelsSqlArray = rs.getArray(TOOL_LABELS.name());
            if (labelsSqlArray != null) {
                List<String> labels = Arrays.asList((String[]) labelsSqlArray.getArray());
                tool.setLabels(labels);
            }

            Array endpointsSqlArray = rs.getArray(TOOL_ENDPOINTS.name());
            if (endpointsSqlArray != null) {
                List<String> endpoints = Arrays.asList((String[]) endpointsSqlArray.getArray());
                tool.setEndpoints(endpoints);
            }

            tool.setDefaultCommand(rs.getString(TOOL_DEFAULT_COMMAND.name()));
        }

        private static DockerRegistry initDockerRegistry(ResultSet rs, Long registryId)
                throws SQLException {
            DockerRegistry registry;
            registry = new DockerRegistry();
            registry.setId(registryId);
            registry.setPath(rs.getString(REGISTRY_PATH.name()));
            registry.setDescription(rs.getString(REGISTRY_DESCRIPTION.name()));
            registry.setOwner(rs.getString(OWNER.name()));
            registry.setSecretName(rs.getString(SECRET_NAME.name()));
            registry.setCaCert(rs.getString(CA_CERT.name()));
            registry.setUserName(rs.getString(USER_NAME.name()));
            registry.setPipelineAuth(rs.getBoolean(PIPELINE_AUTH.name()));
            registry.setExternalUrl(rs.getString(EXTERNAL_URL.name()));
            registry.setPassword(rs.getString(PASSWORD.name()));
            registry.setSecurityScanEnabled(rs.getBoolean(SECURITY_SCAN_ENABLED.name()));
            return registry;
        }
    }
    @Required
    public void setLoadAllDockerRegistriesQuery(String loadAllDockerRegistriesQuery) {
        this.loadAllDockerRegistriesQuery = loadAllDockerRegistriesQuery;
    }

    @Required
    public void setLoadDockerRegistryByIdQuery(String loadDockerRegistryByIdQuery) {
        this.loadDockerRegistryByIdQuery = loadDockerRegistryByIdQuery;
    }

    @Required
    public void setDockerRegistrySequence(String dockerRegistrySequence) {
        this.dockerRegistrySequence = dockerRegistrySequence;
    }

    @Required
    public void setCreateDockerRegistryQuery(String createDockerRegistryQuery) {
        this.createDockerRegistryQuery = createDockerRegistryQuery;
    }

    @Required
    public void setDeleteDockerRegistryQuery(String deleteDockerRegistryQuery) {
        this.deleteDockerRegistryQuery = deleteDockerRegistryQuery;
    }

    @Required
    public void setUpdateDockerRegistryQuery(String updateDockerRegistryQuery) {
        this.updateDockerRegistryQuery = updateDockerRegistryQuery;
    }

    @Required
    public void setLoadDockerRegistryByNameQuery(String loadDockerRegistryByNameQuery) {
        this.loadDockerRegistryByNameQuery = loadDockerRegistryByNameQuery;
    }

    @Required
    public void setLoadDockerRegistriesWithCertsQuery(String loadDockerRegistriesWithCertsQuery) {
        this.loadDockerRegistriesWithCertsQuery = loadDockerRegistriesWithCertsQuery;
    }

    @Required
    public void setLoadDockerRegistryByExtUrlQuery(String loadDockerRegistryByExtUrlQuery) {
        this.loadDockerRegistryByExtUrlQuery = loadDockerRegistryByExtUrlQuery;
    }

    @Required
    public void setLoadDockerRegistriesWithContentQuery(
            String loadDockerRegistriesWithContentQuery) {
        this.loadDockerRegistriesWithContentQuery = loadDockerRegistriesWithContentQuery;
    }

    @Required
    public void setLoadDockerRegistriesWithSecurityScanEnabledQuery(
        String loadDockerRegistriesWithSecurityScanEnabledQuery) {
        this.loadDockerRegistriesWithSecurityScanEnabledQuery = loadDockerRegistriesWithSecurityScanEnabledQuery;
    }

    @Required
    public void setLoadDockerRegistryWithContentQuery(String loadDockerRegistryWithContentQuery) {
        this.loadDockerRegistryWithContentQuery = loadDockerRegistryWithContentQuery;
    }
}
