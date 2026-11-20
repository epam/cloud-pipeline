/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.security.user;

import com.epam.pipeline.controller.vo.user.RoleVO;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.user.RoleManager;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class RolePermissionManager {

    private static final String ROLE_ADMIN_SUFFIX = "ADMIN";

    private final RoleManager roleManager;

    public boolean isAdminRole(final Long roleId) {
        final Role loaded = roleManager.load(roleId);
        return Arrays.stream(DefaultRoles.values())
                .map(DefaultRoles::getName)
                .filter(name -> name.contains(ROLE_ADMIN_SUFFIX))
                .anyMatch(roleName -> roleName.equals(loaded.getName()));
    }

    public boolean isPredefinedRole(final Long roleId) {
        return roleManager.load(roleId).isPredefined();
    }

    /**
     * Checks an update of a group by a user, who has WRITE permission on it, but is neither ADMIN nor USER_ADMIN.
     * Such a user may not update a predefined role, rename a group or change its "default" flag:
     * permissions are granted to a group by its name, and a default group is assigned to all new users.
     * @param roleId an ID of a group to update
     * @param roleVO the requested group state
     * @return true if the update is allowed
     */
    public boolean isDelegatedUpdateAllowed(final Long roleId, final RoleVO roleVO) {
        if (roleVO == null) {
            return false;
        }
        final Role loaded = roleManager.load(roleId);
        return !loaded.isPredefined()
                && loaded.isUserDefault() == roleVO.isUserDefault()
                && StringUtils.isNotBlank(roleVO.getName())
                && loaded.getName().equals(RoleManager.formatName(roleVO.getName()));
    }
}
