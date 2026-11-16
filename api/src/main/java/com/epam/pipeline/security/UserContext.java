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

package com.epam.pipeline.security;

import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.entity.security.JwtTokenClaims;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
public class UserContext implements UserDetails {
    private JwtRawToken jwtRawToken;
    private Long userId;
    private String userName;
    private String orgUnitId;
    private List<Role> roles = new ArrayList<>();
    private List<String> groups = new ArrayList<>();
    /**
     * Defines if user is logged in through an external service
     */
    private boolean external;

    /**
     * Defines if the roles and groups held here were scoped down deliberately, and so are the whole
     * of what the principal may do rather than a snapshot that may be refreshed from the database.
     */
    private boolean authoritiesScoped;

    /**
     * Whether the token behind this principal may be traded for another. True unless the token
     * says otherwise, so an ordinary login is unaffected.
     */
    private boolean exchangeable = true;

    public UserContext(JwtRawToken jwtRawToken, JwtTokenClaims claims) {
        this.jwtRawToken = jwtRawToken;
        this.userId = Optional.ofNullable(claims.getUserId()).filter(NumberUtils::isDigits).map(Long::parseLong)
                .orElse(null);
        this.userName = claims.getUserName().toUpperCase();
        this.orgUnitId = claims.getOrgUnitId();
        this.roles = claims.getRoles().stream().map(Role::new).collect(Collectors.toList());
        this.groups = claims.getGroups();
        this.external = claims.isExternal();
        this.authoritiesScoped = claims.isAuthoritiesScoped();
        this.exchangeable = claims.isExchangeable();
    }

    public UserContext(Long id, String userName) {
        this.userId = id;
        this.userName = userName;
        this.orgUnitId = "";
    }

    public UserContext(PipelineUser pipelineUser) {
        this.userName = pipelineUser.getUserName();
        this.userId = pipelineUser.getId();
        this.roles = pipelineUser.getRoles();
        this.groups = pipelineUser.getGroups();
    }


    public JwtTokenClaims toClaims() {
        return JwtTokenClaims.builder()
                .userId(Optional.ofNullable(userId).map(Objects::toString).orElse(null))
                .userName(userName)
                .orgUnitId(orgUnitId)
                .roles(roles.stream().map(Role::getName)
                        .collect(Collectors.toList()))
                .groups(groups)
                .external(external)
                // carried over deliberately: a token issued from this context must keep the
                // restriction, or its holder could exchange a narrowed token for a full one
                .authoritiesScoped(authoritiesScoped)
                // carried over so the restriction cannot be shed by issuing a token from this
                // context, the same way the narrowed authorities cannot
                .exchangeable(exchangeable)
                .build();
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return Stream.concat(
                ListUtils.emptyIfNull(roles).stream().map(role -> new SimpleGrantedAuthority(role.getName())),
                ListUtils.emptyIfNull(groups).stream().map(SimpleGrantedAuthority::new))
                .collect(Collectors.toList());
    }

    /**
     * Drops every role and group that is not in the given sets, keeping the intersection.
     * <p>
     * Used where a restriction has to be applied to authorities that came from somewhere else:
     * the token's authorities narrowed by what the stored user still holds, so that revoking a
     * role in the database continues to take effect, or the stored user's authorities narrowed by
     * what a token carries. Either way the result can only be smaller than both.
     * <p>
     * A null set removes everything, because "nothing is allowed" is the safe reading of a
     * restriction that failed to say what it permits.
     */
    public void retainAuthorities(final Collection<String> allowedRoles,
                                  final Collection<String> allowedGroups) {
        final Set<String> keptRoles = new HashSet<>(ListUtils.emptyIfNull(asList(allowedRoles)));
        this.roles = ListUtils.emptyIfNull(roles).stream()
                .filter(role -> keptRoles.contains(role.getName()))
                .collect(Collectors.toList());
        final Set<String> keptGroups = new HashSet<>(ListUtils.emptyIfNull(asList(allowedGroups)));
        this.groups = ListUtils.emptyIfNull(groups).stream()
                .filter(keptGroups::contains)
                .collect(Collectors.toList());
    }

    private static List<String> asList(final Collection<String> values) {
        return Objects.isNull(values) ? null : new ArrayList<>(values);
    }

    public PipelineUser toPipelineUser() {
        return PipelineUser.builder()
                .id(userId)
                .userName(userName)
                .roles(roles)
                .groups(groups)
                .admin(roles.stream()
                        .anyMatch(role -> role.getName().equals(DefaultRoles.ROLE_ADMIN.getName())))
                .build();
    }

    @Override public String getPassword() {
        return null;
    }

    @Override public String getUsername() {
        return userName;
    }

    @Override public boolean isAccountNonExpired() {
        return false;
    }

    @Override public boolean isAccountNonLocked() {
        return false;
    }

    @Override public boolean isCredentialsNonExpired() {
        return false;
    }

    @Override public boolean isEnabled() {
        return true;
    }
}

