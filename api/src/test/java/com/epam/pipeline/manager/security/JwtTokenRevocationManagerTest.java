/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.dao.security.JwtTokenRevocationDao;
import com.epam.pipeline.dao.security.NamedJwtTokenDao;
import com.epam.pipeline.entity.security.JwtTokenClaims;
import com.epam.pipeline.entity.security.NamedJwtToken;
import com.epam.pipeline.security.jwt.TokenVerificationException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class JwtTokenRevocationManagerTest {

    private static final String JTI = "test-jti";
    private static final Long USER_ID = 1L;
    private static final long OTHER_USER_ID = 999L;

    @Mock
    private JwtTokenRevocationDao jwtTokenRevocationDao;

    @Mock
    private NamedJwtTokenDao namedJwtTokenDao;

    @InjectMocks
    private JwtTokenRevocationManager jwtTokenRevocationManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldPassWhenTokenNotRevoked() {
        doReturn(false).when(jwtTokenRevocationDao).isRevoked(JTI);

        jwtTokenRevocationManager.assertTokenNotRevoked(JwtTokenClaims.builder()
                .jwtTokenId(JTI)
                .userName("USER")
                .build().getJwtTokenId());
    }

    @Test
    public void shouldFailWhenTokenRevoked() {
        doReturn(true).when(jwtTokenRevocationDao).isRevoked(JTI);

        assertThatThrownBy(() -> jwtTokenRevocationManager.assertTokenNotRevoked(JwtTokenClaims.builder()
                .jwtTokenId(JTI)
                .userName("USER")
                .build().getJwtTokenId()))
                .isInstanceOf(TokenVerificationException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    public void shouldFailWhenTokenAlreadyRevoked() {
        doReturn(true).when(jwtTokenRevocationDao).isRevoked(JTI);

        assertThatThrownBy(() -> jwtTokenRevocationManager.revokeTokenForUser(USER_ID, JTI))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already been revoked");
    }

    @Test
    public void shouldRevokeByJtiWhenNoNamedTokenRow() {
        doReturn(false).when(jwtTokenRevocationDao).isRevoked(JTI);
        doReturn(Optional.empty()).when(namedJwtTokenDao).loadByJti(JTI);

        jwtTokenRevocationManager.revokeTokenForUser(USER_ID, JTI);

        verify(jwtTokenRevocationDao).upsertRevocation(eq(JTI), any());
        verify(namedJwtTokenDao).deleteByJti(JTI);
    }

    @Test
    public void shouldRejectWhenNamedTokenBelongsToAnotherUser() {
        final LocalDateTime now = LocalDateTime.now();
        final NamedJwtToken otherUser = NamedJwtToken.builder()
                .jti(JTI)
                .userId(OTHER_USER_ID)
                .createdBy(OTHER_USER_ID)
                .tokenName(null)
                .issuedAt(now)
                .expiresAt(now)
                .token(null)
                .build();
        doReturn(false).when(jwtTokenRevocationDao).isRevoked(JTI);
        doReturn(Optional.of(otherUser)).when(namedJwtTokenDao).loadByJti(JTI);

        assertThatThrownBy(() -> jwtTokenRevocationManager.revokeTokenForUser(USER_ID, JTI))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
    }
}
