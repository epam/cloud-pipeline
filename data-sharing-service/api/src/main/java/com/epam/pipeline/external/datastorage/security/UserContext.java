/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.external.datastorage.security;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.auth0.jwt.JWT;
import com.epam.pipeline.external.datastorage.exception.TokenExpiredException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserContext implements UserDetails {
    private Long userId;
    private String userName;
    private String orgUnitId;
    private List<String> groups = new ArrayList<>();
    private Map<String, String> attributes;
    private String token;
    private JWT jwtToken;

    public UserContext(String userName) {
        this.userName = userName;
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return Collections.emptyList(); // No authorities needed for a proxy user
    }

    public void setToken(String token) {
        this.token = token;
        jwtToken = JWT.decode(token);
    }

    public String getToken() throws TokenExpiredException {
        if (jwtToken.getExpiresAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().isBefore(
            LocalDateTime.now())) {
            throw new TokenExpiredException();
        }

        return token;
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

