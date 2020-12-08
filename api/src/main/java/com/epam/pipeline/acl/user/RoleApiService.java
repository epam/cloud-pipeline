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

package com.epam.pipeline.acl.user;

import com.epam.pipeline.controller.vo.user.RoleVO;
import com.epam.pipeline.entity.user.ExtendedRole;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.user.RoleManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_ONLY;
import static com.epam.pipeline.security.acl.AclExpressions.OR_USER_READER;

@Service
public class RoleApiService {

    @Autowired
    private RoleManager roleManager;

    @PreAuthorize(ADMIN_ONLY + OR_USER_READER)
    public Collection<Role> loadRolesWithUsers() {
        return roleManager.loadAllRoles(true);
    }

    public Collection<Role> loadRoles() {
        return roleManager.loadAllRoles(false);
    }

    @PreAuthorize(ADMIN_ONLY + OR_USER_READER)
    public Role loadRole(Long id) {
        return roleManager.loadRoleWithUsers(id);
    }

    @PreAuthorize(ADMIN_ONLY)
    public Role createRole(String name, boolean userDefault, Long storageId) {
        return roleManager.createRole(name, false, userDefault, storageId);
    }

    @PreAuthorize(ADMIN_ONLY)
    public Role updateRole(final Long roleId, final RoleVO roleVO) {
        return roleManager.update(roleId, roleVO);
    }

    @PreAuthorize(ADMIN_ONLY)
    public Role deleteRole(Long id) {
        return roleManager.deleteRole(id);
    }

    @PreAuthorize(ADMIN_ONLY)
    public ExtendedRole assignRole(Long roleId, List<Long> userIds) {
        return roleManager.assignRole(roleId, userIds);
    }

    @PreAuthorize(ADMIN_ONLY)
    public ExtendedRole removeRole(Long roleId, List<Long> userIds) {
        return roleManager.removeRole(roleId, userIds);
    }

}
