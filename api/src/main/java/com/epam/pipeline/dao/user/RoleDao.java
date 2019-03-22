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

package com.epam.pipeline.dao.user;

import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.user.ExtendedRole;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.epam.pipeline.dao.user.UserDao.UserParameters;

public class RoleDao extends NamedParameterJdbcDaoSupport {

    private String createRoleQuery;
    private String updateRoleQuery;

    private String deleteRoleQuery;
    private String loadRoleQuery;
    private String loadRoleByNameQuery;
    private String loadRoleListQuery;
    private String loadAllRolesQuery;
    private String roleSequence;
    private String loadRolesWithUsersQuery;
    private String loadRoleWithUsersQuery;
    private String loadUserDefaultRolesQuery;
    private String deleteRolesReferencesQuery;
    private String loadRolesByStorageIdQuery;

    @Autowired
    private DaoHelper daoHelper;

    @Transactional(propagation = Propagation.MANDATORY)
    public Role createRole(String name) {
        return createRole(name, false, false, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Role createRole(String name, boolean predefined, boolean userDefault, Long storageId) {
        Role role = new Role();
        role.setName(name);
        role.setId(daoHelper.createId(roleSequence));
        role.setUserDefault(userDefault);
        role.setPredefined(predefined);
        role.setDefaultStorageId(storageId);
        getNamedParameterJdbcTemplate().update(createRoleQuery, RoleParameters.getParameters(role));
        return role;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateRole(Role role) {
        getNamedParameterJdbcTemplate().update(updateRoleQuery, RoleParameters.getParameters(role));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteRole(Long id) {
        getJdbcTemplate().update(deleteRoleQuery, id);
    }

    public Collection<Role> loadAllRoles(boolean loadUsers) {
        return loadUsers ?
                new ArrayList<>(getJdbcTemplate().query(loadRolesWithUsersQuery,
                        RoleParameters.getExtendedRowExtractor())) :
                getJdbcTemplate().query(loadAllRolesQuery, RoleParameters.getRowMapper());
    }

    public List<Role> loadUserDefaultRoles() {
        return getJdbcTemplate().query(loadUserDefaultRolesQuery, RoleParameters.getRowMapper());
    }

    public List<Role> loadRolesByStorageId(Long storageId) {
        return getJdbcTemplate().query(loadRolesByStorageIdQuery, RoleParameters.getRowMapper(), storageId);
    }

    public ExtendedRole loadExtendedRole(Long roleId) {
        Collection<ExtendedRole> roles = getJdbcTemplate()
                .query(loadRoleWithUsersQuery, RoleParameters.getExtendedRowExtractor(), roleId);
        return CollectionUtils.isEmpty(roles) ? null : roles.stream().findFirst().orElse(null);
    }

    public Optional<Role> loadRole(Long id) {
        return loadRoleByParameter(id, loadRoleQuery);
    }

    public Optional<Role> loadRoleByName(String name) {
        return loadRoleByParameter(name, loadRoleByNameQuery);
    }

    private Optional<Role> loadRoleByParameter(Object parameter, String loadRoleQuery) {
        List<Role> result =
                getJdbcTemplate().query(loadRoleQuery, RoleParameters.getRowMapper(), parameter);
        return result.stream().findFirst();
    }

    public List<Role> loadRolesList(List<Long> ids) {
        return getNamedParameterJdbcTemplate().query(loadRoleListQuery,
                RoleParameters.getIdListParameters(ids), RoleParameters.getRowMapper());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteRoleReferences(Long id) {
        getJdbcTemplate().update(deleteRolesReferencesQuery, id);
    }

    enum RoleParameters {
        ROLE_ID,
        ROLE_NAME,
        ROLE_PREDEFINED,
        ROLE_USER_DEFAULT,
        IDS,
        ROLE_DEFAULT_STORAGE_ID;

        private static MapSqlParameterSource getParameters(Role role) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(ROLE_ID.name(), role.getId());
            params.addValue(ROLE_NAME.name(), role.getName());
            params.addValue(ROLE_PREDEFINED.name(), role.isPredefined());
            params.addValue(ROLE_USER_DEFAULT.name(), role.isUserDefault());
            params.addValue(ROLE_DEFAULT_STORAGE_ID.name(), role.getDefaultStorageId());
            return params;
        }

        public static MapSqlParameterSource getIdListParameters(List<Long> ids) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(IDS.name(), ids);
            return params;
        }

        private static RowMapper<Role> getRowMapper() {
            return (rs, rowNum) -> {
                Role role = new Role();
                return parseRole(rs, role);
            };
        }

        static Role parseRole(ResultSet rs, Role role) throws SQLException {
            role.setId(rs.getLong(ROLE_ID.name()));
            role.setName(rs.getString(ROLE_NAME.name()));
            role.setPredefined(rs.getBoolean(ROLE_PREDEFINED.name()));
            role.setUserDefault(rs.getBoolean(ROLE_USER_DEFAULT.name()));
            long defaultStorageId = rs.getLong(ROLE_DEFAULT_STORAGE_ID.name());
            if (!rs.wasNull()) {
                role.setDefaultStorageId(defaultStorageId);
            }
            return role;
        }

        static ResultSetExtractor<Collection<ExtendedRole>> getExtendedRowExtractor() {
            return (rs) -> {
                Map<Long, ExtendedRole> roles = new HashMap<>();
                while (rs.next()) {
                    Long roleId = rs.getLong(ROLE_ID.name());
                    ExtendedRole role = roles.get(roleId);
                    if (role == null) {
                        role = new ExtendedRole();
                        parseRole(rs, role);
                        role.setUsers(new ArrayList<>());
                        roles.put(roleId, role);
                    }
                    Long userId = rs.getLong(UserParameters.USER_ID.name());
                    if (!rs.wasNull()) {
                        PipelineUser user = UserParameters.parseUser(rs, userId);
                        role.getUsers().add(user);
                    }
                }
                return roles.values();
            };
        }
    }

    @Required
    public void setCreateRoleQuery(String createRoleQuery) {
        this.createRoleQuery = createRoleQuery;
    }

    @Required
    public void setDeleteRoleQuery(String deleteRoleQuery) {
        this.deleteRoleQuery = deleteRoleQuery;
    }

    @Required
    public void setLoadRoleQuery(String loadRoleQuery) {
        this.loadRoleQuery = loadRoleQuery;
    }

    @Required
    public void setLoadAllRolesQuery(String loadAllRolesQuery) {
        this.loadAllRolesQuery = loadAllRolesQuery;
    }

    @Required
    public void setRoleSequence(String roleSequence) {
        this.roleSequence = roleSequence;
    }

    @Required
    public void setLoadRoleListQuery(String loadRoleListQuery) {
        this.loadRoleListQuery = loadRoleListQuery;
    }

    @Required
    public void setLoadRolesWithUsersQuery(String loadRolesWithUsersQuery) {
        this.loadRolesWithUsersQuery = loadRolesWithUsersQuery;
    }

    @Required
    public void setDeleteRolesReferencesQuery(String deleteRolesReferencesQuery) {
        this.deleteRolesReferencesQuery = deleteRolesReferencesQuery;
    }

    @Required
    public void setLoadRoleWithUsersQuery(String loadRoleWithUsersQuery) {
        this.loadRoleWithUsersQuery = loadRoleWithUsersQuery;
    }

    @Required
    public void setLoadRoleByNameQuery(String loadRoleByNameQuery) {
        this.loadRoleByNameQuery = loadRoleByNameQuery;
    }

    @Required
    public void setLoadUserDefaultRolesQuery(String loadUserDefaultRolesQuery) {
        this.loadUserDefaultRolesQuery = loadUserDefaultRolesQuery;
    }

    @Required
    public void setLoadRolesByStorageIdQuery(final String loadRolesByStorageIdQuery) {
        this.loadRolesByStorageIdQuery = loadRolesByStorageIdQuery;
    }

    @Required
    public void setUpdateRoleQuery(final String updateRoleQuery) {
        this.updateRoleQuery = updateRoleQuery;
    }
}
