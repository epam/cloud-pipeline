/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.controller.vo.user.RoleVO;
import com.epam.pipeline.dao.user.RoleDao;
import com.epam.pipeline.dao.user.UserDao;
import com.epam.pipeline.entity.user.ExtendedRole;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.datastorage.DataStorageValidator;
import com.epam.pipeline.manager.security.GrantPermissionHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provides methods for {@link com.epam.pipeline.entity.user.Role} entities management
 */
@Slf4j
@Service
public class RoleManager {

    @Autowired
    private GrantPermissionHandler permissionHandler;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private UserDao userDao;

    @Autowired
    private RoleDao roleDao;

    @Autowired
    private DataStorageValidator storageValidator;

    @Transactional(propagation = Propagation.REQUIRED)
    public Role createRole(final String name, final boolean predefined,
                           final boolean userDefault, final Long storageId) {
        String formattedName = getValidName(name);
        Assert.isTrue(!roleDao.loadRoleByName(name).isPresent(),
                messageHelper.getMessage(MessageConstants.ERROR_ROLE_NAME_EXISTS, name));
        storageValidator.validate(storageId);
        return roleDao.createRole(formattedName, predefined, userDefault, storageId);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Role update(final Long roleId, final RoleVO roleVO) {
        Role role = loadRole(roleId);
        role.setName(getValidName(roleVO.getName()));
        role.setUserDefault(roleVO.isUserDefault());
        role.setDefaultStorageId(roleVO.getDefaultStorageId());
        storageValidator.validate(role);
        roleDao.updateRole(role);
        return role;
    }

    public Collection<Role> loadAllRoles(boolean loadUsers) {
        return roleDao.loadAllRoles(loadUsers);
    }

    public List<Role> loadUserDefaultRoles() {
        return roleDao.loadUserDefaultRoles();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Role deleteRole(Long id) {
        Role role = loadRole(id);
        Assert.isTrue(!role.isPredefined(), "Predefined system roles cannot be deleted");
        permissionHandler.deleteGrantedAuthority(role.getName(), false);
        roleDao.deleteRoleReferences(id);
        roleDao.deleteRole(id);
        return role;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public ExtendedRole assignRole(Long roleId, List<Long> userIds) {
        loadRole(roleId);
        Assert.isTrue(CollectionUtils.isNotEmpty(userIds),
                messageHelper.getMessage(MessageConstants.ERROR_USER_LIST_EMPTY));
        Collection<PipelineUser> users = userDao.loadUsersList(userIds);
        List<Long> idsToAdd = users.stream()
                .filter(user -> user.getRoles().stream().noneMatch(role -> role.getId().equals(roleId)))
                .map(PipelineUser::getId)
                .collect(Collectors.toList());
        log.info(messageHelper.getMessage(MessageConstants.INFO_ASSIGN_ROLE, roleId,
                idsToAdd.stream().map(Object::toString).collect(Collectors.joining(", "))));
        if (CollectionUtils.isNotEmpty(idsToAdd)) {
            userDao.assignRoleToUsers(roleId, idsToAdd);
        }
        return roleDao.loadExtendedRole(roleId);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public ExtendedRole removeRole(Long roleId, List<Long> userIds) {
        loadRole(roleId);
        Assert.isTrue(CollectionUtils.isNotEmpty(userIds),
                messageHelper.getMessage(MessageConstants.ERROR_USER_LIST_EMPTY));
        Collection<PipelineUser> users = userDao.loadUsersList(userIds);
        List<Long> idsToRemove = users.stream()
                .filter(user -> user.getRoles().stream().anyMatch(role -> role.getId().equals(roleId)))
                .map(PipelineUser::getId)
                .collect(Collectors.toList());
        log.info(messageHelper.getMessage(MessageConstants.INFO_UNASSIGN_ROLE, roleId,
                idsToRemove.stream().map(Object::toString).collect(Collectors.joining(", "))));
        if (CollectionUtils.isNotEmpty(idsToRemove)) {
            userDao.removeRoleFromUsers(roleId, idsToRemove);
        }
        return roleDao.loadExtendedRole(roleId);
    }

    public Collection<Role> loadRolesByDefaultStorage(final Long storageId) {
        return roleDao.loadRolesByStorageId(storageId);
    }

    public Role loadRoleByNameOrId(String identifier) {
        if (NumberUtils.isDigits(identifier)) {
            return loadRole(Long.parseLong(identifier));
        }
        return loadRole(identifier);
    }

    public List<Long> getDefaultRolesIds() {
        return loadUserDefaultRoles()
                .stream()
                .map(Role::getId)
                .collect(Collectors.toList());
    }

    public Role loadRoleByNameWithUsers(final String name) {
        return roleDao.loadExtendedRole(loadRole(name).getId());
    }

    public Role loadRole(Long roleId) {
        return roleDao.loadRole(roleId)
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_ROLE_ID_NOT_FOUND, roleId)));
    }

    public Role loadRole(String name) {
        return roleDao.loadRoleByName(name)
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_ROLE_NAME_NOT_FOUND, name)));
    }

    public Optional<Role> findRoleByName(final String name) {
        return roleDao.loadRoleByName(name);
    }

    public ExtendedRole loadRoleWithUsers(Long roleId) {
        loadRole(roleId);
        return roleDao.loadExtendedRole(roleId);
    }

    private String getValidName(final String name) {
        Assert.isTrue(StringUtils.isNotBlank(name),
                messageHelper.getMessage(MessageConstants.ERROR_ROLE_NAME_REQUIRED));
        String formattedName = name.toUpperCase();
        if (!formattedName.startsWith(Role.ROLE_PREFIX)) {
            formattedName = Role.ROLE_PREFIX + formattedName;
        }
        return formattedName;
    }
}
