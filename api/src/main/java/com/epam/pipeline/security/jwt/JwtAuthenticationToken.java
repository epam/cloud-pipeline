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

package com.epam.pipeline.security.jwt;

import java.util.Collection;
import java.util.Date;

import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.security.UserContext;
import org.joda.time.DateTime;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public class JwtAuthenticationToken extends AbstractAuthenticationToken {
    private JwtRawToken jwtRawToken;
    private UserContext userContext;
    private Date tokenExpiration;
    private static final int TOKEN_SESSION_TIMEOUT = 60;

    public JwtAuthenticationToken(JwtRawToken jwtRawToken) {
        super(null);
        this.jwtRawToken = jwtRawToken;
        this.setAuthenticated(false);
        this.tokenExpiration = DateTime.now().plusSeconds(TOKEN_SESSION_TIMEOUT).toDate();
    }

    public JwtAuthenticationToken(UserContext userContext, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        super.setAuthenticated(true);
        this.userContext = userContext;
        this.tokenExpiration = DateTime.now().plusSeconds(TOKEN_SESSION_TIMEOUT).toDate();
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        if (isAuthenticated) {
            throw new IllegalArgumentException(
                    "Cannot set this token to trusted - use constructor which takes a GrantedAuthority list instead");
        }

        super.setAuthenticated(false);
    }

    @Override
    public boolean isAuthenticated() {
        if (tokenExpiration != null && new Date().compareTo(tokenExpiration) >= 0) {
            return false;
        } else {
            return super.isAuthenticated();
        }
    }

    @Override
    public Object getCredentials() {
        return this.jwtRawToken;
    }

    @Override
    public Object getPrincipal() {
        return this.userContext;
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        this.jwtRawToken = null;
    }
}
