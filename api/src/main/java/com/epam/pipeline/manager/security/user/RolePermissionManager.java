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

import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.user.RoleManager;
import lombok.RequiredArgsConstructor;
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
}
