/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.security.jwt.JwtAuthenticationToken;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.model.Sid;
import org.springframework.security.acls.model.SidRetrievalStrategy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code PermissionsHelper} provides methods for ACL permissions check.
 */
// TODO: 24-10-2019
// All common code (not entity specific) methods shall be moved to this class from GrantPermissionManager.class
// For now some of methods are duplicated, this shall be removed as soon we'll refactor GrantPermissionManager.class
// and provide integration tests for ACL permissions
@Service
@RequiredArgsConstructor
public class CheckPermissionHelper {
    private final PermissionEvaluator permissionEvaluator;
    private final AuthManager authManager;
    private final SidRetrievalStrategy sidRetrievalStrategy;
    private final UserManager userManager;

    public SecurityContext createContext(final String userName) {
        return authManager.createContext(getAuthentication(userName.toUpperCase()));
    }

    public boolean isAllowed(final String permissionName, final AbstractSecuredEntity entity) {
        if (isOwnerOrAdmin(entity.getOwner())) {
            return true;
        }
        return permissionEvaluator
                .hasPermission(authManager.getAuthentication(), entity.getId(),
                        entity.getClass().getName(), permissionName);
    }

    public boolean isAllowed(final String permissionName, final AbstractSecuredEntity entity,
                             final String pipelineUserName) {
        if (isOwnerOrAdmin(entity.getOwner(), pipelineUserName)) {
            return true;
        }
        return permissionEvaluator.hasPermission(getAuthentication(pipelineUserName), entity, permissionName);
    }

    public boolean isOwnerOrAdmin(String owner) {
        final String user = authManager.getAuthorizedUser();
        if (user == null || user.equals(AuthManager.UNAUTHORIZED_USER)) {
            return false;
        }
        return user.equalsIgnoreCase(owner) || isAdmin();
    }

    private boolean isOwnerOrAdmin(final String owner, final String userName) {
        return isOwner(owner, userName) || isAdmin(userName);
    }

    public boolean isOwner(final AbstractSecuredEntity entity) {
        return isOwner(entity.getOwner());
    }

    public boolean isOwner(final String owner) {
        return isOwner(owner, authManager.getAuthorizedUser());
    }

    private boolean isOwner(final String owner, final String userName) {
        if (StringUtils.isBlank(owner)) {
            return false;
        }
        if (userName == null || userName.equals(AuthManager.UNAUTHORIZED_USER)) {
            return false;
        }
        return owner.equalsIgnoreCase(userName);
    }

    public boolean isAdmin() {
        final GrantedAuthoritySid admin = new GrantedAuthoritySid(DefaultRoles.ROLE_ADMIN.getName());
        return getSids().stream().anyMatch(sid -> sid.equals(admin));
    }

    public boolean isAdmin(final String userName) {
        final GrantedAuthoritySid admin = new GrantedAuthoritySid(DefaultRoles.ROLE_ADMIN.getName());
        return getSids(userName).stream().anyMatch(sid -> sid.equals(admin));
    }

    public boolean hasAnyRole(final DefaultRoles... roles) {
        return hasAnyRole(Arrays.asList(roles));
    }

    public boolean hasAnyRole(final List<DefaultRoles> roles) {
        return hasAnySid(roles.stream()
                .map(DefaultRoles::getName)
                .map(GrantedAuthoritySid::new)
                .collect(Collectors.toList()));
    }

    public boolean hasAnySid(final List<Sid> sids) {
        return getSids().stream().anyMatch(sids::contains);
    }

    public List<Sid> getSids() {
        final Authentication authentication = authManager.getAuthentication();
        return sidRetrievalStrategy.getSids(authentication);
    }

    public List<Sid> getSids(final String userName) {
        return sidRetrievalStrategy.getSids(getAuthentication(userName));
    }

    private Authentication getAuthentication(final String userName) {
        final UserContext userContext = userManager.loadUserContext(userName);
        return new JwtAuthenticationToken(userContext, userContext.getAuthorities());
    }
}
