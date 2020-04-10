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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    public UserContext(JwtRawToken jwtRawToken, JwtTokenClaims claims) {
        this.jwtRawToken = jwtRawToken;
        if (NumberUtils.isDigits(claims.getUserId())) {
            this.userId = Long.parseLong(claims.getUserId());
        } else {
            throw new IllegalArgumentException("Invalid user ID: " + claims.getUserId());
        }
        this.userName = claims.getUserName().toUpperCase();
        this.orgUnitId = claims.getOrgUnitId();
        this.roles = claims.getRoles().stream().map(Role::new).collect(Collectors.toList());
        this.groups = claims.getGroups();
        this.external = claims.isExternal();
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
                .build();
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return Stream.concat(
                ListUtils.emptyIfNull(roles).stream().map(role -> new SimpleGrantedAuthority(role.getName())),
                ListUtils.emptyIfNull(groups).stream().map(SimpleGrantedAuthority::new))
                .collect(Collectors.toList());
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

