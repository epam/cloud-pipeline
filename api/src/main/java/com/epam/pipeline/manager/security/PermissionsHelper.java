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
import com.epam.pipeline.entity.user.DefaultRoles;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.model.Sid;
import org.springframework.security.acls.model.SidRetrievalStrategy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionsHelper {
    private final PermissionEvaluator permissionEvaluator;
    private final AuthManager authManager;
    private final SidRetrievalStrategy sidRetrievalStrategy;

    public boolean isAllowed(final String permissionName, final AbstractSecuredEntity entity) {
        if (isOwner(entity)) {
            return true;
        }
        return permissionEvaluator
                .hasPermission(SecurityContextHolder.getContext().getAuthentication(), entity,
                        permissionName);
    }

    public boolean isOwner(final AbstractSecuredEntity entity) {
        return isOwner(entity.getOwner());
    }

    public boolean isOwnerOrAdmin(final String owner) {
        return isOwner(owner) || isAdmin();
    }

    public boolean isAdmin() {
        final GrantedAuthoritySid admin = new GrantedAuthoritySid(DefaultRoles.ROLE_ADMIN.getName());
        return getSids().stream().anyMatch(sid -> sid.equals(admin));
    }

    private List<Sid> getSids() {
        final Authentication authentication = authManager.getAuthentication();
        return sidRetrievalStrategy.getSids(authentication);
    }

    private boolean isOwner(final String owner) {
        if (StringUtils.isBlank(owner)) {
            return false;
        }
        final String currentUser = authManager.getAuthorizedUser();
        if (currentUser == null || currentUser.equals(AuthManager.UNAUTHORIZED_USER)) {
            return false;
        }
        return owner.equalsIgnoreCase(currentUser);
    }
}
