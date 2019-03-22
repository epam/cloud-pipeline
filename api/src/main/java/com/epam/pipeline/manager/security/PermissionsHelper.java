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

package com.epam.pipeline.manager.security;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PermissionsHelper {
    private final PermissionEvaluator permissionEvaluator;
    private final AuthManager authManager;

    public boolean isAllowed(String permissionName, AbstractSecuredEntity entity) {
        if (isOwner(entity)) {
            return true;
        }
        return permissionEvaluator
                .hasPermission(SecurityContextHolder.getContext().getAuthentication(), entity,
                        permissionName);
    }

    public boolean isOwner(AbstractSecuredEntity entity) {
        String owner = entity.getOwner();
        return StringUtils.isNotBlank(owner) && owner.equalsIgnoreCase(authManager.getAuthorizedUser());
    }
}
