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

package com.epam.pipeline.dts.security.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class UserContext implements UserDetails {
    private JwtRawToken jwtRawToken;
    private Long userId;
    private String userName;
    private String orgUnitId;
    private List<String> roles = new ArrayList<>();
    private List<String> groups = new ArrayList<>();

    public UserContext(JwtRawToken jwtRawToken, JwtTokenClaims claims) {
        this.jwtRawToken = jwtRawToken;
        if (NumberUtils.isDigits(claims.getUserId())) {
            this.userId = Long.parseLong(claims.getUserId());
        } else {
            //TODO: tmp solution only for integration period
            this.userId = 1L;
        }
        this.userName = claims.getUserName().toUpperCase();
        this.orgUnitId = claims.getOrgUnitId();
        this.roles = new ArrayList<>(claims.getRoles());
        this.groups = new ArrayList<>(claims.getGroups());
    }

    public UserContext(String userName) {
        this.userName = userName;
        this.orgUnitId = "";
        //TODO: tmp solution only for integration period
        this.userId = 1L;
    }

    public UserContext(String userName, Long id) {
        this(userName);
        this.userId = id;
    }

    public JwtTokenClaims toClaims() {
        return JwtTokenClaims.builder()
                .userId(userId.toString())
                .userName(userName)
                .orgUnitId(orgUnitId)
                .roles(roles)
                .groups(groups)
                .build();
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> result = new ArrayList<>();
        if (!CollectionUtils.isEmpty(roles)) {
            roles.stream()
                    .forEach(role -> result.add(new SimpleGrantedAuthority(role)));
        }
        if (!CollectionUtils.isEmpty(groups)) {
            groups.stream()
                    .forEach(group -> result.add(new SimpleGrantedAuthority(group)));
        }
        return result;
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
