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
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.security.jwt.TokenVerificationException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Persists and checks JWT revocation by {@code jti} in {@code jwt_token_revocation}.
 * Revocation does not require the token to be listed in the named-token registry; when a named row
 * exists, {@link #revokeTokenForUser} enforces that it belongs to the given user.
 */
@Service
@RequiredArgsConstructor
public class JwtTokenRevocationManager {

    private final JwtTokenRevocationDao jwtTokenRevocationDao;
    private final NamedJwtTokenDao namedJwtTokenDao;

    @Transactional(readOnly = true)
    public void assertTokenNotRevoked(final JwtTokenClaims claims) {
        final String jti = claims.getJwtTokenId();
        if (StringUtils.isBlank(jti)) {
            return;
        }
        if (jwtTokenRevocationDao.isRevoked(jti)) {
            throw new TokenVerificationException("Token has been revoked");
        }
    }

    /**
     * Records revocation for {@code jti}. If the token is registered in the named-token table,
     * {@code userId} must match that row's owner. If it is not registered, revocation is still
     * recorded (e.g. session JWTs without a registry entry).
     */
    @Transactional
    public void revokeTokenForUser(final Long userId, final String jti) {
        if (StringUtils.isBlank(jti)) {
            throw new IllegalArgumentException("JWT id (jti) is required");
        }
        final Optional<NamedJwtToken> row = namedJwtTokenDao.loadByJti(jti);
        if (row.isPresent() && !userId.equals(row.get().getUserId())) {
            throw new IllegalArgumentException("JWT id does not belong to the specified user");
        }
        final LocalDateTime revokedAt = DateUtils.nowUTC();
        jwtTokenRevocationDao.upsertRevocation(jti, revokedAt);
        namedJwtTokenDao.deleteByJti(jti);
    }

    @Transactional
    public void revokeAllNamedTokensForUser(final Long userId) {
        final LocalDateTime revokedAt = DateUtils.nowUTC();
        jwtTokenRevocationDao.insertRevocationsForAllNamedTokensOfUser(userId, revokedAt);
        namedJwtTokenDao.deleteByUserId(userId);
    }
}
