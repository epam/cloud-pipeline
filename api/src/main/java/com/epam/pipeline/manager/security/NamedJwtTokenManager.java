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

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.epam.pipeline.dao.security.NamedJwtTokenDao;
import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.entity.security.NamedJwtToken;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Registry of named JWTs ({@code jwt_named_token}). Revocation is handled by
 * {@link JwtTokenRevocationManager}.
 */
@Service
@Slf4j
public class NamedJwtTokenManager {

    private final NamedJwtTokenDao namedJwtTokenDao;
    private final AuthManager authManager;
    private final UserManager userManager;
    private final PreferenceManager preferenceManager;

    @Autowired
    public NamedJwtTokenManager(final NamedJwtTokenDao namedJwtTokenDao,
                                @Lazy final AuthManager authManager,
                                @Lazy final UserManager userManager,
                                final PreferenceManager preferenceManager) {
        this.namedJwtTokenDao = namedJwtTokenDao;
        this.authManager = authManager;
        this.userManager = userManager;
        this.preferenceManager = preferenceManager;
    }

    @Transactional
    public void registerIssuedNamedJwtToken(final JwtRawToken rawToken, final Long userId,
                                            final Long createdByUserId, final String tokenName) {
        if (userId == null || createdByUserId == null) {
            log.debug("Skipping JWT named token registration: missing user id or created-by id.");
            return;
        }
        final NamedJwtToken entity = buildNamedJwtToken(rawToken, userId, createdByUserId, tokenName, null);
        if (entity.getJti() == null) {
            return;
        }
        assertWithinNamedTokenLimit(userId);
        namedJwtTokenDao.insert(entity);
    }

    private void assertWithinNamedTokenLimit(final Long userId) {
        final Integer limit = preferenceManager.getPreference(SystemPreferences.LAUNCH_JWT_NAMED_TOKENS_LIMIT);
        final int max = limit == null ? 0 : limit;
        if (max <= 0) {
            return;
        }
        final int current = namedJwtTokenDao.countByUserId(userId);
        if (current >= max) {
            throw new IllegalArgumentException(
                    String.format("The maximum number of JWT tokens for a user is %d.", max));
        }
    }

    /**
     * Issues a JWT for the current principal and returns registry metadata including the raw token value.
     */
    @Transactional
    public NamedJwtToken issueNamedTokenForCurrentUser(final Long expiration,
                                                       final boolean validateExpirationDuration,
                                                       @Nullable final String registryLabel) {
        final JwtRawToken raw = authManager.issueTokenForCurrentUser(expiration, validateExpirationDuration, null);
        final UserContext owner = authManager.getUserContext();
        if (owner == null || owner.getUserId() == null) {
            throw new IllegalStateException("Cannot resolve current user for named JWT response");
        }
        registerIssuedNamedJwtToken(raw, owner.getUserId(), resolveTokenCreatedByUserId(owner), registryLabel);
        return buildNamedJwtToken(raw, owner.getUserId(), resolveTokenCreatedByUserId(owner), registryLabel,
                raw.getToken());
    }

    /**
     * Issues a JWT for the given user (admin path) and returns registry metadata including the raw token value.
     */
    @Transactional
    public NamedJwtToken issueNamedTokenForUser(final String userName, final Long expiration,
                                                @Nullable final String registryLabel) {
        final UserContext userContext = userManager.loadUserContext(userName);
        final JwtRawToken raw = userManager.issueToken(userName, expiration, false, null);
        registerIssuedNamedJwtToken(raw, userContext.getUserId(), resolveTokenCreatedByUserId(userContext),
                registryLabel);
        return buildNamedJwtToken(raw, userContext.getUserId(), resolveTokenCreatedByUserId(userContext),
                registryLabel, raw.getToken());
    }

    @Transactional(readOnly = true)
    public List<NamedJwtToken> loadNamedJwtTokensForCurrentUser(final Long userId) {
        return namedJwtTokenDao.loadByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<NamedJwtToken> loadNamedJwtTokensForUserAsAdmin(final Long userId) {
        return namedJwtTokenDao.loadByUserId(userId);
    }

    private NamedJwtToken buildNamedJwtToken(final JwtRawToken rawToken, final Long userId,
                                             final Long createdByUserId, final String tokenName,
                                             @Nullable final String secretToken) {
        final DecodedJWT decoded = JWT.decode(rawToken.getToken());
        final String jti = decoded.getId();
        if (StringUtils.isBlank(jti)) {
            return NamedJwtToken.builder().build();
        }
        final LocalDateTime issuedAt = toLocalDateTime(decoded.getIssuedAt());
        final LocalDateTime expiresAt = toLocalDateTime(decoded.getExpiresAt());
        final NamedJwtToken.NamedJwtTokenBuilder builder = NamedJwtToken.builder()
                .jti(jti)
                .userId(userId)
                .createdBy(createdByUserId)
                .tokenName(StringUtils.trimToNull(tokenName))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt);
        if (secretToken != null) {
            builder.token(secretToken);
        }
        return builder.build();
    }

    private Long resolveTokenCreatedByUserId(final UserContext tokenOwner) {
        final UserContext current = authManager.getUserContext();
        if (current != null && current.getUserId() != null) {
            return current.getUserId();
        }
        return tokenOwner.getUserId();
    }

    private static LocalDateTime toLocalDateTime(final java.util.Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
