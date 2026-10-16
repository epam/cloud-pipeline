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
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.pipeline.*;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.security.jwt.JwtAuthenticationToken;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.model.Sid;
import org.springframework.security.acls.model.SidRetrievalStrategy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
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
        if (isScopedAdmin(entity)) {
            return true;
        }
        if (AclPermission.READ_NAME.equals(permissionName) && isScopedReader(entity)) {
            return true;
        }
        if (AclPermission.EXECUTE_NAME.equals(permissionName) && isScopedExecutor(entity)) {
            return true;
        }
        return permissionEvaluator
                .hasPermission(authManager.getAuthentication(), entity.getId(),
                        entity.getClass().getName(), permissionName);
    }

    /**
     * Checks the permission as {@link #isAllowed(String, AbstractSecuredEntity)} does, but without the READ and
     * EXECUTE that {@link DefaultRoles#ROLE_PIPELINE_READER} and {@link DefaultRoles#ROLE_PIPELINE_EXECUTOR}
     * grant on every pipeline: the roles give access to a pipeline itself, and not to the actions that only
     * start with it, e.g. to the runs of the pipeline, which inherit its permissions.
     * For an entity other than a pipeline, it is the same as {@link #isAllowed(String, AbstractSecuredEntity)}.
     */
    public boolean isAllowedIgnoringScopedReaderAndExecutor(final String permissionName,
                                                            final AbstractSecuredEntity entity) {
        final List<Sid> sids = getSids();
        if (!(entity instanceof Pipeline)
                || !hasAnyRole(sids, DefaultRoles.ROLE_PIPELINE_READER, DefaultRoles.ROLE_PIPELINE_EXECUTOR)) {
            return isAllowed(permissionName, entity);
        }
        if (isOwnerOrAdmin(entity.getOwner()) || isScopedAdmin(entity, sids)) {
            return true;
        }
        return permissionEvaluator.hasPermission(
                withoutRoles(authManager.getAuthentication(),
                        DefaultRoles.ROLE_PIPELINE_READER, DefaultRoles.ROLE_PIPELINE_EXECUTOR),
                entity.getId(), entity.getClass().getName(), permissionName);
    }

    public boolean isAllowed(final String permissionName, final AbstractSecuredEntity entity,
                             final String pipelineUserName) {
        if (isOwnerOrAdmin(entity.getOwner(), pipelineUserName)) {
            return true;
        }
        if (isScopedAdmin(entity)) {
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

    public boolean isScopedAdmin(final AbstractSecuredEntity entity) {
        return isScopedAdmin(entity, getSids());
    }

    public boolean isScopedAdmin(final AbstractSecuredEntity entity, final List<Sid> sids) {
        if (entity == null) {
            return false;
        }

        if (entity instanceof AbstractDataStorage) {
            return hasAnyRole(sids, DefaultRoles.ROLE_STORAGE_ADMIN);
        } else if (entity instanceof PipelineRun || entity instanceof NodeInstance) {
            return hasAnyRole(sids, DefaultRoles.ROLE_RUN_ADMIN);
        } else if (entity instanceof Pipeline || entity instanceof RunConfiguration) {
            return hasAnyRole(sids, DefaultRoles.ROLE_PIPELINE_ADMIN);
        } else if (entity instanceof Tool || entity instanceof ToolGroup || entity instanceof DockerRegistry) {
            return hasAnyRole(sids, DefaultRoles.ROLE_TOOL_ADMIN);
        } else if (entity instanceof PipelineUser || entity instanceof Role) {
            return hasAnyRole(sids, DefaultRoles.ROLE_USER_ADMIN);
        }
        return false;
    }

    public boolean isScopedAdmin(final AclClass aclClass) {
        return isScopedAdmin(aclClass, getSids());
    }

    public boolean isScopedAdmin(final AclClass aclClass, final List<Sid> sids) {
        if (aclClass == null) {
            return false;
        }

        if (aclClass == AclClass.DATA_STORAGE) {
            return hasAnyRole(sids, DefaultRoles.ROLE_STORAGE_ADMIN);
        } else if (aclClass == AclClass.PIPELINE || aclClass == AclClass.CONFIGURATION) {
            return hasAnyRole(sids, DefaultRoles.ROLE_PIPELINE_ADMIN);
        } else if (aclClass == AclClass.TOOL || aclClass == AclClass.TOOL_GROUP ||
                aclClass == AclClass.DOCKER_REGISTRY) {
            return hasAnyRole(sids, DefaultRoles.ROLE_TOOL_ADMIN);
        } else if (aclClass == AclClass.PIPELINE_USER || aclClass == AclClass.ROLE) {
            return hasAnyRole(sids, DefaultRoles.ROLE_USER_ADMIN);
        }
        return false;
    }

    /**
     * Checks whether the SIDs are granted READ on the entity by a role that grants it to every entity of
     * its type, regardless of the entity's ACL. Unlike {@link #isScopedAdmin(AbstractSecuredEntity, List)},
     * such a role grants nothing but READ.
     */
    public boolean isScopedReader(final AbstractSecuredEntity entity) {
        return isScopedReader(entity, getSids());
    }

    public boolean isScopedReader(final AbstractSecuredEntity entity, final List<Sid> sids) {
        if (entity == null) {
            return false;
        }

        if (entity instanceof AbstractDataStorage) {
            return hasAnyRole(sids, DefaultRoles.ROLE_STORAGE_READER);
        } else if (entity instanceof Pipeline) {
            return hasAnyRole(sids, DefaultRoles.ROLE_PIPELINE_READER);
        }
        return false;
    }

    public boolean isScopedReader(final AclClass aclClass) {
        return isScopedReader(aclClass, getSids());
    }

    public boolean isScopedReader(final AclClass aclClass, final List<Sid> sids) {
        if (aclClass == null) {
            return false;
        }

        if (aclClass == AclClass.DATA_STORAGE) {
            return hasAnyRole(sids, DefaultRoles.ROLE_STORAGE_READER);
        } else if (aclClass == AclClass.PIPELINE) {
            return hasAnyRole(sids, DefaultRoles.ROLE_PIPELINE_READER);
        }
        return false;
    }

    /**
     * Checks whether the SIDs are granted EXECUTE on the entity by a role that grants it to every entity of
     * its type, regardless of the entity's ACL. Such a role grants nothing but EXECUTE.
     */
    public boolean isScopedExecutor(final AbstractSecuredEntity entity) {
        return isScopedExecutor(entity, getSids());
    }

    public boolean isScopedExecutor(final AbstractSecuredEntity entity, final List<Sid> sids) {
        return entity instanceof Pipeline && hasAnyRole(sids, DefaultRoles.ROLE_PIPELINE_EXECUTOR);
    }

    public boolean hasAnyRole(final DefaultRoles... roles) {
        return hasAnyRole(Arrays.asList(roles));
    }

    public boolean hasAnyRole(final List<Sid> sids, final DefaultRoles... roles) {
        return hasAnySid(sids, Arrays.stream(roles)
                .map(DefaultRoles::getName)
                .map(GrantedAuthoritySid::new)
                .collect(Collectors.toList()));
    }

    public boolean hasAnyRole(final List<DefaultRoles> roles) {
        return hasAnySid(roles.stream()
                .map(DefaultRoles::getName)
                .map(GrantedAuthoritySid::new)
                .collect(Collectors.toList()));
    }

    public boolean hasAnySid(final List<Sid> sids) {
        return hasAnySid(getSids(), sids);
    }

    public boolean hasAnySid(final List<Sid> sids, final List<Sid> sidsToCheck) {
        return sids.stream().anyMatch(sidsToCheck::contains);
    }

    public List<Sid> getSids() {
        final Authentication authentication = authManager.getAuthentication();
        if (authentication == null) {
            return Collections.emptyList();
        }
        return sidRetrievalStrategy.getSids(authentication);
    }

    public List<Sid> getSids(final String userName) {
        return sidRetrievalStrategy.getSids(getAuthentication(userName));
    }

    private Authentication getAuthentication(final String userName) {
        final UserContext userContext = userManager.loadUserContext(userName);
        return new JwtAuthenticationToken(userContext, userContext.getAuthorities());
    }

    private Authentication withoutRoles(final Authentication authentication, final DefaultRoles... roles) {
        final List<String> roleNames = Arrays.stream(roles).map(DefaultRoles::getName).collect(Collectors.toList());
        final List<GrantedAuthority> authorities = authentication.getAuthorities().stream()
                .filter(authority -> !roleNames.contains(authority.getAuthority()))
                .collect(Collectors.toList());
        return new UsernamePasswordAuthenticationToken(authentication.getPrincipal(),
                authentication.getCredentials(), authorities);
    }
}
