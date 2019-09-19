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

package com.epam.pipeline.manager.user;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.PipelineUserVO;
import com.epam.pipeline.dao.user.GroupStatusDao;
import com.epam.pipeline.dao.user.RoleDao;
import com.epam.pipeline.dao.user.UserDao;
import com.epam.pipeline.entity.user.CustomControl;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.entity.user.GroupStatus;
import com.epam.pipeline.entity.utils.ControlEntry;
import com.epam.pipeline.manager.datastorage.DataStorageValidator;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.security.UserContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserManager {

    @Autowired
    private UserDao userDao;

    @Autowired
    private RoleDao roleDao;

    @Autowired
    private GroupStatusDao groupStatusDao;

    @Autowired
    private AuthManager authManager;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private RoleManager roleManager;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private DataStorageValidator storageValidator;

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineUser createUser(String name, List<Long> roles,
                                   List<String> groups, Map<String, String> attributes,
                                   Long defaultStorageId) {
        Assert.isTrue(StringUtils.isNotBlank(name),
                messageHelper.getMessage(MessageConstants.ERROR_USER_NAME_REQUIRED));
        String userName = name.trim().toUpperCase();
        PipelineUser loadedUser = userDao.loadUserByName(userName);
        Assert.isNull(loadedUser, messageHelper.getMessage(MessageConstants.ERROR_USER_NAME_EXISTS, name));
        PipelineUser user = new PipelineUser(userName);
        List<Long> userRoles = getNewUserRoles(roles);
        user.setRoles(roleDao.loadRolesList(userRoles));
        user.setGroups(groups);
        user.setAttributes(attributes);
        user.setDefaultStorageId(defaultStorageId);
        storageValidator.validate(user);
        return userDao.createUser(user, userRoles);
    }


    /**
     * Creates user with parameters defined in Cloud Pipeline (username and roles).
     * Assumes that other parameters (groups and attributes) will be filled later from AD,
     * when user will login vai SAML
     * @param userVO specifies user to create
     * @return created user
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineUser createUser(PipelineUserVO userVO) {
        return createUser(userVO.getUserName(), userVO.getRoleIds(), null, null, null);
    }

    public UserContext loadUserContext(String name) {
        PipelineUser pipelineUser = userDao.loadUserByName(name);
        Assert.notNull(pipelineUser, messageHelper.getMessage(MessageConstants.ERROR_USER_NAME_NOT_FOUND, name));
        return new UserContext(pipelineUser);
    }

    public PipelineUser loadUserByName(String name) {
        return userDao.loadUserByName(name);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<PipelineUser> loadUsersByNames(Collection<String> names) {
        if (names.isEmpty()) {
            return Collections.emptyList();
        }

        return userDao.loadUsersByNames(names);
    }

    public PipelineUser loadUserById(Long id) {
        PipelineUser user = userDao.loadUserById(id);
        Assert.notNull(user, messageHelper.getMessage(MessageConstants.ERROR_USER_ID_NOT_FOUND, id));
        return user;
    }

    public Collection<PipelineUser> loadAllUsers() {
        return userDao.loadAllUsers();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineUser deleteUser(Long id) {
        PipelineUser userContext = loadUserById(id);
        userDao.deleteUserRoles(id);
        userDao.deleteUser(id);
        return userContext;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineUser updateUser(Long id, List<Long> roles) {
        loadUserById(id);
        updateUserRoles(id, roles);
        return loadUserById(id);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineUser updateUser(Long id, PipelineUserVO userVO) {
        PipelineUser user = loadUserById(id);
        user.setDefaultStorageId(userVO.getDefaultStorageId());
        storageValidator.validate(user);
        return userDao.updateUser(user);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineUser updateUserBlockingStatus(final Long id, final boolean blockStatus) {
        final PipelineUser user = loadUserById(id);
        user.setBlocked(blockStatus);
        return userDao.updateUser(user);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public GroupStatus upsertGroupBlockingStatus(final String groupName, final boolean blockStatus,
                                                 final boolean external) {
        final GroupStatus groupStatus = new GroupStatus(groupName, blockStatus, external);
        return groupStatusDao.upsertGroupBlockingStatusQuery(groupStatus);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public GroupStatus deleteGroupBlockingStatus(final String groupName, final boolean external) {
        Assert.state(StringUtils.isNotBlank(groupName),
                messageHelper.getMessage(MessageConstants.USER_GROUP_IS_REQUIRED));
        final GroupStatus groupStatus = groupStatusDao.loadGroupBlockingStatus(groupName, external);
        Assert.notNull(groupStatus,
                messageHelper.getMessage(MessageConstants.ERROR_NO_GROUP_WAS_FOUND, groupName));
        groupStatusDao.deleteGroupBlockingStatus(groupName, external);
        return groupStatus;
    }

    public List<GroupStatus> loadGroupsBlockingStatus(final List<String> groupNames) {
        return ListUtils.emptyIfNull(CollectionUtils.isEmpty(groupNames)
                ? null
                : groupStatusDao.loadGroupsBlockingStatus(groupNames));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineUser updateUserSAMLInfo(Long id, String name, List<Long> roles, List<String> groups,
                                           Map<String, String> attributes) {
        PipelineUser user = loadUserById(id);
        if (needToUpdateUser(groups, attributes, user)) {
            user.setUserName(name);
            user.setGroups(groups);
            user.setAttributes(attributes);
            userDao.updateUser(user);
        }
        updateUserRoles(id, roles);
        return loadUserById(id);
    }

    public PipelineUser loadUserByNameOrId(String identifier) {
        PipelineUser user;
        if (NumberUtils.isDigits(identifier)) {
            user = loadUserById(Long.parseLong(identifier));
        } else {
            user = loadUserByName(identifier);
        }
        Assert.notNull(user, "User with identifier not found: " + identifier);
        return user;
    }

    /**
     * Reads file with control setting from disk and resolves settings applied to current user.
     * @return {@link List} of {@link CustomControl} that should be applied to current user
     */
    public List<CustomControl> getUserControls() {
        PipelineUser currentUser = authManager.getCurrentUser();
        if (currentUser == null) {
            return Collections.emptyList();
        }

        List<ControlEntry> settings = preferenceManager.getPreference(SystemPreferences.UI_CONTROLS_SETTINGS);
        if (settings == null) {
            return Collections.emptyList();
        }

        Set<String> authorities = currentUser.getAuthorities();
        return settings.stream()
            .map(entry -> {
                Map<String, String> userSettings = entry.getUserSettings().entrySet().stream()
                    .filter(authEntry -> authorities.contains(authEntry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                // if no setting found or settings are ambiguous (more than one are present)
                if (MapUtils.isEmpty(userSettings) || userSettings.size() > 1) {
                    return new CustomControl(entry.getKey(), entry.getDefaultValue());
                }
                return new CustomControl(entry.getKey(),
                        userSettings.values().stream().findFirst().orElse(entry.getDefaultValue()));
            })
            .collect(Collectors.toList());
    }

    /**
     * Searches for user by prefix. Search is performed in user names and all attributes values.
     * @param prefix to search for
     * @return users matching prefix
     */
    public List<PipelineUser> findUsers(String prefix) {
        Assert.isTrue(StringUtils.isNotBlank(prefix),
                messageHelper.getMessage(MessageConstants.USER_PREFIX_IS_REQUIRED));
        return new ArrayList<>(userDao.findUsers(prefix));
    }

    /**
     * Searches for a user group name by a prefix
     * @param prefix a prefix of a group name to search
     * @return a loaded {@code List} of group name that satisfy the prefix
     */
    public List<String> findGroups(String prefix) {
        if (StringUtils.isBlank(prefix)) {
            return userDao.loadAllGroups();
        }
        return userDao.findGroups(prefix);
    }

    /**
     * Loads a {@code UserContext} instances from the database specified by group
     * @param group a user group name
     * @return a loaded {@code Collection} of {@code UserContext} instances from the database
     */
    public Collection<PipelineUser> loadUsersByGroup(String group) {
        Assert.isTrue(StringUtils.isNotBlank(group),
                messageHelper.getMessage(MessageConstants.USER_GROUP_IS_REQUIRED));
        return userDao.loadUsersByGroup(group);
    }

    /**
     * Checks whether a specific user is a member of a specific group
     * @param userName a name of {@code UserContext}
     * @param group    a user group name
     * @return true if a specific user is a member of a specific group
     */
    public boolean checkUserByGroup(String userName, String group) {
        Assert.isTrue(StringUtils.isNotBlank(group),
                messageHelper.getMessage(MessageConstants.USER_GROUP_IS_REQUIRED));
        return userDao.isUserInGroup(userName, group);
    }

    public Collection<PipelineUser> loadUsersByDeafultStorage(final Long storageId) {
        return userDao.loadUsersByStorageId(storageId);
    }

    public PipelineUser getCurrentUser() {
        PipelineUser currentUser = authManager.getCurrentUser();
        if (currentUser != null && !StringUtils.isEmpty(currentUser.getUserName())) {
            PipelineUser extended = loadUserByName(currentUser.getUserName());
            if (extended != null) {
                extended.setAdmin(currentUser.isAdmin());
                return extended;
            }
        }
        return currentUser;
    }

    public boolean needToUpdateUser(List<String> groups, Map<String, String> attributes,
            PipelineUser user) {
        List<String> loadedUserGroups =
                CollectionUtils.isEmpty(user.getGroups()) ? Collections.emptyList() : user.getGroups();
        Map<String, String> loadedUserAttributes =
                MapUtils.isEmpty(user.getAttributes()) ? Collections.emptyMap() : user.getAttributes();

        return !CollectionUtils.isEqualCollection(loadedUserGroups, groups)
                || !CollectionUtils.isEqualCollection(loadedUserAttributes.entrySet(), attributes.entrySet());
    }

    private void checkAllRolesPresent(List<Long> roles) {
        if (CollectionUtils.isEmpty(roles)) {
            return;
        }
        Set<Long> presentIds = roleDao.loadRolesList(roles).stream().map(Role::getId).collect(Collectors.toSet());
        roles.forEach(roleId -> Assert.isTrue(presentIds.contains(roleId),
                messageHelper.getMessage(MessageConstants.ERROR_ROLE_ID_NOT_FOUND, roleId)));
    }

    private void updateUserRoles(Long id, List<Long> roles) {
        checkAllRolesPresent(roles);
        userDao.deleteUserRoles(id);
        if (!CollectionUtils.isEmpty(roles)) {
            userDao.insertUserRoles(id, roles);
        }
    }

    private List<Long> getNewUserRoles(List<Long> roles) {
        checkAllRolesPresent(roles);
        List<Long> userRoles = CollectionUtils.isEmpty(roles) ? roleManager.getDefaultRolesIds() : roles;
        Long roleUserId = DefaultRoles.ROLE_USER.getRole().getId();
        if (userRoles.stream().noneMatch(roleUserId::equals)) {
            userRoles.add(roleUserId);
        }
        return userRoles;
    }
}
