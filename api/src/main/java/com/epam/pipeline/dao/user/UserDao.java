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

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.entity.utils.DateUtils;
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

import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.epam.pipeline.dao.DaoHelper.mapListToSqlArray;
import static com.epam.pipeline.dao.user.RoleDao.RoleParameters;

public class UserDao extends NamedParameterJdbcDaoSupport {

    private String createUserQuery;
    private String updateUserQuery;
    private String loadAllUsersQuery;
    private String loadUserByNameQuery;
    private String loadUsersByNamesQuery;
    private String loadUserByIdQuery;
    private String deleteUserQuery;
    private String findUsersByPrefixQuery;
    private String loadUserListQuery;
    private String deleteUserRolesQuery;
    private String userSequence;
    private String loadUsersByGroupQuery;
    private String loadUserByGroupQuery;
    private String findGroupsByPrefixQuery;
    private String loadAllGroupsQuery;
    private String loadUsersByStorageIdQuery;
    private String addRoleToUserQuery;
    private String deleteRoleFromUserQuery;

    @Autowired
    private DaoHelper daoHelper;

    @Transactional(propagation = Propagation.MANDATORY)
    public PipelineUser createUser(PipelineUser user, List<Long> roles) {
        user.setId(daoHelper.createId(userSequence));
        user.setRegistrationDate(DateUtils.nowUTC());
        getNamedParameterJdbcTemplate().update(createUserQuery, UserParameters.getParameters(user, getConnection()));
        List<Long> appliedRoles = new ArrayList<>();
        if (CollectionUtils.isEmpty(roles)) {
            appliedRoles.add(DefaultRoles.ROLE_USER.getId());
        } else {
            appliedRoles.addAll(roles);
        }
        insertUserRoles(user.getId(), appliedRoles);
        return user;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insertUserRoles(Long userId, List<Long> authorities) {
        MapSqlParameterSource[] batchParameters = authorities.stream()
                .map(id -> UserParameters.getParameterSource(userId, id))
                .toArray(MapSqlParameterSource[]::new);

        getNamedParameterJdbcTemplate().batchUpdate(addRoleToUserQuery, batchParameters);
    }

    public Collection<PipelineUser> loadAllUsers() {
        return getJdbcTemplate().query(loadAllUsersQuery, UserParameters.getUserExtractor());
    }

    public PipelineUser loadUserByName(String name) {
        Collection<PipelineUser> items =
                getJdbcTemplate().query(loadUserByNameQuery,
                        UserParameters.getUserExtractor(), name.toLowerCase());
        if (CollectionUtils.isEmpty(items)) {
            return null;
        } else {
            return items.stream().filter(user -> user.getUserName()
                    .equalsIgnoreCase(name)).findAny().orElse(null);
        }
    }

    public List<PipelineUser> loadUsersByNames(Collection<String> userNames) {
        Collection<PipelineUser> users = getJdbcTemplate().query(
                DaoHelper.replaceInClause(loadUsersByNamesQuery, userNames.size()), UserParameters.getUserExtractor(),
                (Object[]) userNames.stream().map(String::toLowerCase).toArray(String[]::new));
        return new ArrayList<>(users);
    }

    public Collection<PipelineUser> loadUsersByStorageId(Long storageId) {
        return getJdbcTemplate()
                .query(loadUsersByStorageIdQuery, UserParameters.getUserExtractor(), storageId);
    }

    public PipelineUser loadUserById(Long id) {
        Collection<PipelineUser> items =
                getJdbcTemplate().query(loadUserByIdQuery, UserParameters.getUserExtractor(), id);
        return items.stream().findFirst().orElse(null);
    }

    /**
     * Loads a {@code UserContext} instances from the database specified by group
     *
     * @param group a user group name
     * @return a loaded {@code Collection} of {@code UserContext} instances from the database
     */
    public Collection<PipelineUser> loadUsersByGroup(String group) {
        return getJdbcTemplate().query(loadUsersByGroupQuery, UserParameters.getUserExtractor(), group);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PipelineUser updateUser(PipelineUser user) {
        getNamedParameterJdbcTemplate().update(updateUserQuery, UserParameters.getParameters(user, getConnection()));
        return user;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PipelineUser updateUserRoles(PipelineUser user, List<Long> roles) {
        deleteUserRoles(user.getId());
        if (!CollectionUtils.isEmpty(roles)) {
            insertUserRoles(user.getId(), roles);
        }
        return user;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteUser(Long id) {
        getJdbcTemplate().update(deleteUserQuery, id);
    }

    public Collection<PipelineUser> findUsers(String prefix) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(UserParameters.PREFIX.name(), daoHelper.escapeUnderscore(prefix.toLowerCase() + "%"));
        return getNamedParameterJdbcTemplate().query(findUsersByPrefixQuery, params,
                UserParameters.getUserExtractor());
    }

    public Collection<PipelineUser> loadUsersList(List<Long> userIds) {
        return getNamedParameterJdbcTemplate().query(loadUserListQuery,
                RoleParameters.getIdListParameters(userIds), UserParameters.getUserExtractor());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteUserRoles(Long id) {
        getJdbcTemplate().update(deleteUserRolesQuery, id);
    }


    @Transactional(propagation = Propagation.MANDATORY)
    public void assignRoleToUsers(Long roleId, List<Long> userIds) {
        processBatchQuery(addRoleToUserQuery, roleId, userIds);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void removeRoleFromUsers(Long roleId, List<Long> userIds) {
        processBatchQuery(deleteRoleFromUserQuery, roleId, userIds);
    }

    private void processBatchQuery(String query, Long roleId, List<Long> userIds) {
        MapSqlParameterSource[] batchParameters = userIds.stream()
                .map(id -> UserParameters.getParameterSource(id, roleId))
                .toArray(MapSqlParameterSource[]::new);
        getNamedParameterJdbcTemplate().batchUpdate(query, batchParameters);
    }

    /**
     * Searches for a user group name by a prefix in the database
     *
     * @param prefix a prefix of a group name to search
     * @return a loaded {@code List} of group name from the database that satisfy the prefix
     */
    public List<String> findGroups(String prefix) {
        return getJdbcTemplate().query(findGroupsByPrefixQuery,
                UserParameters.getGroupExtractor(), prefix.toLowerCase(), prefix.toLowerCase());
    }

    /**
     * Checks whether a specific user is a member of a specific group
     *
     * @param userName a name of {@code UserContext}
     * @param group    a user group name
     * @return true if a specific user user was found in the database and is a member of a specific group
     */
    public boolean isUserInGroup(String userName, String group) {
        List<PipelineUser> user = getJdbcTemplate().query(loadUserByGroupQuery,
                UserParameters.getUserRowMapper(), userName, group);
        return !CollectionUtils.isEmpty(user);
    }

    /**
     * Loads all user groups in the the database
     *
     * @return a loaded {@code List} of all group names from the database
     */
    public List<String> loadAllGroups() {
        return getJdbcTemplate().query(loadAllGroupsQuery, UserParameters.getGroupExtractor());
    }

    enum UserParameters {
        USER_ID,
        USER_NAME,
        USER_GROUPS,
        ATTRIBUTES,
        PREFIX,
        USER_DEFAULT_STORAGE_ID,
        REGISTRATION_DATE,
        FIRST_LOGIN_DATE;

        private static MapSqlParameterSource getParameterSource(Long userId, Long roleId) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(UserParameters.USER_ID.name(), userId);
            params.addValue(RoleParameters.ROLE_ID.name(), roleId);
            return params;
        }

        private static MapSqlParameterSource getParameters(PipelineUser user, Connection connection) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(USER_ID.name(), user.getId());
            params.addValue(USER_NAME.name(), user.getUserName());
            Array groupsSqlArray = mapListToSqlArray(user.getGroups(), connection);
            params.addValue(USER_GROUPS.name(), groupsSqlArray);
            params.addValue(ATTRIBUTES.name(), convertDataToJsonStringForQuery(user.getAttributes()));
            params.addValue(USER_DEFAULT_STORAGE_ID.name(), user.getDefaultStorageId());
            params.addValue(REGISTRATION_DATE.name(), user.getRegistrationDate());
            params.addValue(FIRST_LOGIN_DATE.name(), user.getFirstLoginDate());
            return params;
        }

        private static RowMapper<PipelineUser> getUserRowMapper() {
            return (rs, rowNum) -> parseUser(rs, rs.getLong(USER_ID.name()));
        }

        static ResultSetExtractor<Collection<PipelineUser>> getUserExtractor() {
            return (ResultSet rs) -> {
                Map<Long, PipelineUser> users = new HashMap<>();
                while (rs.next()) {
                    Long userId = rs.getLong(USER_ID.name());
                    PipelineUser user = users.get(userId);
                    if (user == null) {
                        user = parseUser(rs, userId);
                        users.put(userId, user);
                    }
                    rs.getLong(RoleParameters.ROLE_ID.name());
                    if (!rs.wasNull()) {
                        Role role = new Role();
                        RoleParameters.parseRole(rs, role);
                        user.getRoles().add(role);
                    }
                }
                return users.values();
            };
        }

        static PipelineUser parseUser(ResultSet rs, Long userId) throws SQLException {
            PipelineUser user = new PipelineUser();
            user.setId(userId);
            user.setUserName(rs.getString(USER_NAME.name()));
            user.setRegistrationDate(rs.getTimestamp(REGISTRATION_DATE.name()).toLocalDateTime());
            Array groupsSqlArray = rs.getArray(USER_GROUPS.name());
            if (groupsSqlArray != null) {
                List<String> groups = Arrays.asList((String[]) groupsSqlArray.getArray());
                user.setGroups(groups);
            }
            Map<String, String> data = parseData(rs.getString(ATTRIBUTES.name()));
            if (!rs.wasNull()) {
                user.setAttributes(data);
            }
            Timestamp firstLoginDate = rs.getTimestamp(FIRST_LOGIN_DATE.name());
            if (!rs.wasNull()) {
                user.setFirstLoginDate(firstLoginDate.toLocalDateTime());
            }
            long defaultStorageId = rs.getLong(USER_DEFAULT_STORAGE_ID.name());
            if (!rs.wasNull()) {
                user.setDefaultStorageId(defaultStorageId);
            }
            return user;
        }

        private static ResultSetExtractor<List<String>> getGroupExtractor() {
            return (ResultSet rs) -> {
                List<String> groups = new ArrayList<>();
                while (rs.next()) {
                    Array groupsSqlArray = rs.getArray(USER_GROUPS.name());
                    if (groupsSqlArray != null) {
                        groups = Arrays.asList((String[]) groupsSqlArray.getArray());
                    }
                }
                return groups;
            };
        }

        public static Map<String, String> parseData(String data) {
            return JsonMapper.parseData(data, new TypeReference<Map<String, String>>() {
            });
        }

        private static String convertDataToJsonStringForQuery(Map<String, String> data) {
            return JsonMapper.convertDataToJsonStringForQuery(data);
        }
    }

    @Required
    public void setCreateUserQuery(String createUserQuery) {
        this.createUserQuery = createUserQuery;
    }

    @Required
    public void setAddRoleToUserQuery(String addRoleToUserQuery) {
        this.addRoleToUserQuery = addRoleToUserQuery;
    }

    @Required
    public void setLoadUserByNameQuery(String loadUserByNameQuery) {
        this.loadUserByNameQuery = loadUserByNameQuery;
    }

    @Required
    public void setLoadUserByIdQuery(String loadUserByIdQuery) {
        this.loadUserByIdQuery = loadUserByIdQuery;
    }

    @Required
    public void setDeleteUserRolesQuery(String deleteUserRolesQuery) {
        this.deleteUserRolesQuery = deleteUserRolesQuery;
    }

    @Required
    public void setDeleteUserQuery(String deleteUserQuery) {
        this.deleteUserQuery = deleteUserQuery;
    }

    @Required
    public void setUserSequence(String userSequence) {
        this.userSequence = userSequence;
    }

    @Required
    public void setUpdateUserQuery(String updateUserQuery) {
        this.updateUserQuery = updateUserQuery;
    }

    @Required
    public void setLoadAllUsersQuery(String loadAllUsersQuery) {
        this.loadAllUsersQuery = loadAllUsersQuery;
    }


    @Required
    public void setFindUsersByPrefixQuery(String findUsersByPrefixQuery) {
        this.findUsersByPrefixQuery = findUsersByPrefixQuery;
    }

    @Required
    public void setLoadUserListQuery(String loadUserListQuery) {
        this.loadUserListQuery = loadUserListQuery;
    }

    @Required
    public void setDeleteRoleFromUserQuery(String deleteRoleFromUserQuery) {
        this.deleteRoleFromUserQuery = deleteRoleFromUserQuery;
    }

    @Required
    public void setLoadUsersByGroupQuery(String loadUsersByGroupQuery) {
        this.loadUsersByGroupQuery = loadUsersByGroupQuery;
    }

    @Required
    public void setLoadUserByGroupQuery(String loadUserByGroupQuery) {
        this.loadUserByGroupQuery = loadUserByGroupQuery;
    }

    @Required
    public void setFindGroupsByPrefixQuery(String findGroupsByPrefixQuery) {
        this.findGroupsByPrefixQuery = findGroupsByPrefixQuery;
    }

    @Required
    public void setLoadAllGroupsQuery(String loadAllGroupsQuery) {
        this.loadAllGroupsQuery = loadAllGroupsQuery;
    }


    @Required
    public void setLoadUsersByNamesQuery(String loadUsersByNamesQuery) {
        this.loadUsersByNamesQuery = loadUsersByNamesQuery;
    }

    @Required
    public void setLoadUsersByStorageIdQuery(final String loadUsersByStorageIdQuery) {
        this.loadUsersByStorageIdQuery = loadUsersByStorageIdQuery;
    }
}
