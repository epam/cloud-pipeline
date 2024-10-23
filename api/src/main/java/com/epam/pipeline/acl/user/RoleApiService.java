/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.acl.user;

import com.epam.pipeline.controller.vo.user.RoleVO;
import com.epam.pipeline.entity.user.ExtendedRole;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.security.acl.AclMask;
import com.epam.pipeline.manager.security.acl.AclMaskList;
import com.epam.pipeline.manager.user.RoleManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_ONLY;
import static com.epam.pipeline.security.acl.AclExpressions.OR;
import static com.epam.pipeline.security.acl.AclExpressions.OR_USER_READER;
import static com.epam.pipeline.security.acl.AclExpressions.USER_READ_FILTER;

@Service
public class RoleApiService {

    @Autowired
    private RoleManager roleManager;

    @PostFilter(USER_READ_FILTER)
    @AclMaskList
    public List<Role> loadRolesWithUsers() {
        return roleManager.loadAllRoles(true);
    }

    @PostFilter(USER_READ_FILTER)
    @AclMaskList
    public List<Role> loadRoles() {
        return roleManager.loadAllRoles(false);
    }

    @PostAuthorize(ADMIN_ONLY + OR_USER_READER + OR + "hasPermission(returnObject, 'READ')")
    @AclMask
    public Role loadRole(final Long id) {
        return roleManager.loadRoleWithUsers(id);
    }

    @PostAuthorize(ADMIN_ONLY + OR_USER_READER + OR + "hasPermission(returnObject, 'READ')")
    @AclMask
    public Role loadRoleByName(final String name) {
        return roleManager.loadRoleByNameWithUsers(name);
    }

    @PreAuthorize(ADMIN_ONLY)
    @AclMask
    public Role createRole(String name, boolean userDefault, Long storageId) {
        return roleManager.create(name, false, userDefault, storageId);
    }

    @PostAuthorize(ADMIN_ONLY + OR + "hasPermission(returnObject, 'WRITE')")
    @AclMask
    public Role updateRole(final Long roleId, final RoleVO roleVO) {
        return roleManager.update(roleId, roleVO);
    }

    @PreAuthorize(ADMIN_ONLY)
    public Role deleteRole(Long id) {
        return roleManager.delete(id);
    }

    @PreAuthorize(ADMIN_ONLY)
    @AclMask
    public ExtendedRole assignRole(Long roleId, List<Long> userIds) {
        return roleManager.assignRole(roleId, userIds);
    }

    @PreAuthorize(ADMIN_ONLY)
    public ExtendedRole removeRole(Long roleId, List<Long> userIds) {
        return roleManager.removeRole(roleId, userIds);
    }
}
