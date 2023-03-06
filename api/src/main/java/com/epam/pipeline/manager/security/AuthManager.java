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


import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.ImpersonationStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.security.jwt.JwtAuthenticationToken;
import com.epam.pipeline.security.jwt.JwtTokenGenerator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.switchuser.SwitchUserGrantedAuthority;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

@Service
public class AuthManager {

    public static final String UNAUTHORIZED_USER = "Unauthorized";

    @Autowired
    private JwtTokenGenerator jwtTokenGenerator;

    @Value("${flyway.placeholders.default.admin}")
    private String defaultAdmin;

    @Value("${flyway.placeholders.default.admin.id:1}")
    private Long defaultAdminId;

    public Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    public SecurityContext createContext(final Authentication authentication) {
        final SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        return context;
    }

    /**
     * @return user name of currently logged in user
     */
    public String getAuthorizedUser() {
        Object principal = getPrincipal();
        if (principal.equals(UNAUTHORIZED_USER)) {
            return UNAUTHORIZED_USER;
        }
        String user;
        if (principal instanceof UserContext) {
            user = ((UserContext) principal).getUsername();
        } else if (principal instanceof String) {
            user = (String) principal;
        } else if (principal instanceof User){
            user = ((User) principal).getUsername();
        } else {
            user = UNAUTHORIZED_USER;
        }
        return user;
    }

    /**
     * @return true if current logged in user is an admin user
     */
    public boolean isAdmin() {
        Object principal = getPrincipal();
        if (principal instanceof UserContext) {
            UserContext user = (UserContext)principal;
            return user.getRoles().stream()
                    .anyMatch(role -> role.getName().equals(DefaultRoles.ROLE_ADMIN.getName()));
        } else if (principal instanceof User) {
            return ((User) principal).getAuthorities().stream()
                .anyMatch(role -> role.getAuthority().equals(DefaultRoles.ROLE_ADMIN.getName()));
        } else {
            return false;
        }
    }

    public JwtRawToken issueTokenForCurrentUser() {
        return issueTokenForCurrentUser(null);
    }

    public JwtRawToken issueTokenForCurrentUser(Long expiration) {
        Object principal = getPrincipal();
        if (principal instanceof UserContext) {
            return issueToken((UserContext) principal, expiration);
        } else {
            throw new IllegalArgumentException("Unexpected authorization type: " + principal);
        }
    }

    /**
     * Wraps a current principal into a pipeline user. Therefore some user or its roles fields may be omitted.
     */
    public PipelineUser getCurrentUser() {
        Object principal = getPrincipal();
        return mapToPipelineUser(principal);
    }

    private PipelineUser mapToPipelineUser(final Object principal) {
        if (principal instanceof UserContext) {
            return ((UserContext)principal).toPipelineUser();
        } else if (principal instanceof User) {
            User user = (User) principal;
            return PipelineUser.builder()
                .userName(user.getUsername())
                .roles(user.getAuthorities().stream().map(a -> new Role(a.getAuthority())).collect(Collectors.toList()))
                .admin(user.getAuthorities().stream()
                           .anyMatch(a -> a.getAuthority().equals(DefaultRoles.ROLE_ADMIN.getName())))
                .build();
        } else {
            return null;
        }
    }

    public void setCurrentUser(final PipelineUser pipelineUser) {
        final UserContext user = new UserContext(pipelineUser);
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(user, user.getAuthorities()));
    }

    public ImpersonationStatus getImpersonationStatus() {
        final PipelineUser activeUser = getCurrentUser();
        return getAuthentication().getAuthorities().stream()
            .filter(SwitchUserGrantedAuthority.class::isInstance)
            .map(SwitchUserGrantedAuthority.class::cast)
            .findAny()
            .map(SwitchUserGrantedAuthority::getSource)
            .map(Authentication::getPrincipal)
            .map(this::mapToPipelineUser)
            .map(originalUser -> new ImpersonationStatus(originalUser, activeUser))
            .orElseGet(() -> new ImpersonationStatus(activeUser, null));
    }

    public UserContext getUserContext() {
        Object principal = getPrincipal();
        if (principal instanceof UserContext) {
            return (UserContext)principal;
        } else {
            return null;
        }
    }

    /**
     * Creates a JWT token for current user.
     * @param user user to create token for
     * @param expiration token expiration time
     * @return a JwtRawToken, that contains a string representation of JWT token
     */
    public JwtRawToken issueToken(UserContext user, @Nullable Long expiration) {
        return new JwtRawToken(jwtTokenGenerator.encodeToken(user.toClaims(), expiration));
    }

    public JwtRawToken issueAdminToken(@Nullable Long expiration) {
        return new JwtRawToken(jwtTokenGenerator.encodeToken(getAdminContext().toClaims(), expiration));
    }

    /**
     * @return A default UserContext for scheduled operations
     */
    public SecurityContext createSchedulerSecurityContext() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        UserContext userContext = getAdminContext();
        Collection<GrantedAuthority> authorities = AuthorityUtils.createAuthorityList("ROLE_ADMIN");
        context.setAuthentication(new JwtAuthenticationToken(userContext, authorities));

        return context;
    }

    public boolean isServiceUser(@Nullable String user) {
        return StringUtils.equalsIgnoreCase(user, defaultAdmin);
    }

    private UserContext getAdminContext() {
        UserContext userContext = new UserContext(defaultAdminId, defaultAdmin);
        userContext.setRoles(Collections.singletonList(DefaultRoles.ROLE_ADMIN.getRole()));
        return userContext;
    }

    private Object getPrincipal() {
        Authentication authentication = getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return UNAUTHORIZED_USER;
        }
        return authentication.getPrincipal();
    }
}
