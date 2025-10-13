/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.access;

import com.epam.pipeline.dto.auth.AccessCode;
import com.epam.pipeline.entity.access.AccessCodeEntity;
import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.repository.access.AccessCodeRepository;
import com.nimbusds.oauth2.sdk.pkce.CodeChallenge;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * This class provides methods to obtain access token for unauthorized users.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnsecuredAccessService {
    private static final String ACCESS_DENIED_MSG = "Access is denied";

    private final AccessCodeRepository repository;
    private final UserManager userManager;
    private final PreferenceManager preferenceManager;

    /**
     * Returns authorization code if code already generated for this user with received code_challenge.
     * Returns empty object otherwise.
     * If code already received access shall be denied (authorization code must be retrieved only once).
     * If code generated but expired (created more than 10 minutes ago) access shall be denied.
     *
     * @param codeChallenge code_challenge
     * @return code
     */
    public AccessCode findCode(final String codeChallenge) {
        log.debug("Searching for '{}' registered at the system.", codeChallenge);
        final Optional<AccessCodeEntity> rawEntity = repository.findByCodeChallenge(codeChallenge);
        if (!rawEntity.isPresent()) {
            return null;
        }
        try {
            return rawEntity
                    .map(this::checkCodeIssued)
                    .map(this::checkCodeExpired)
                    .map(this::updateCodeIssued)
                    .map(this::toDTO)
                    .orElse(null);
        } catch (AccessDeniedException e) {
            repository.delete(rawEntity.get());
            throw e;
        }
    }

    /**
     * Exchanges authorization code for JWT token.
     * If no code found (e.g. expired by timeout) access must be denied.
     * If provided code_verifier does not match previously saved code_challenge access must be denied.
     * After receiving authorization code this code must be removed.
     * If error has occurred for existing code this code must be removed.
     *
     * @param code authorization code
     * @param codeVerifier code_verifier to check
     * @return JWT token
     */
    public JwtRawToken exchangeCodeForToken(final String code, final String codeVerifier) {
        final AccessCodeEntity accessCode = repository.findByCode(code)
                .orElseThrow(() -> new AccessDeniedException(ACCESS_DENIED_MSG));
        try {
            checkCodeVerifier(accessCode, codeVerifier);
            checkCodeExpired(accessCode);
            return userManager.issueToken(accessCode.getUserName(), null);
        } finally {
            repository.delete(accessCode);
        }
    }

    private boolean isCodeVerifierValid(final String codeVerifier, final AccessCodeEntity accessCodeEntity) {
        if (StringUtils.isBlank(codeVerifier)) {
            return false;
        }
        final CodeChallengeMethod method = CodeChallengeMethod.parse(accessCodeEntity.getCodeChallengeMethod());
        final String codeChallenge = CodeChallenge.compute(method, new CodeVerifier(codeVerifier)).getValue();
        return accessCodeEntity.getCodeChallenge().equals(codeChallenge);
    }

    private void checkCodeVerifier(final AccessCodeEntity entity, final String codeVerifier) {
        if (!isCodeVerifierValid(codeVerifier, entity)) {
            log.error("Invalid code verifier.");
            throw new AccessDeniedException(ACCESS_DENIED_MSG);
        }
    }

    private AccessCodeEntity checkCodeExpired(final AccessCodeEntity entity) {
        final Integer ttl = preferenceManager.getPreference(SystemPreferences.SYSTEM_ACCESS_CODE_TTL_MINUTES);
        if (DateUtils.nowUTC().isAfter(entity.getCreated().plusMinutes(ttl))) {
            log.error("Authorization code has expired.");
            throw new AccessDeniedException(ACCESS_DENIED_MSG);
        }
        return entity;
    }

    private AccessCodeEntity updateCodeIssued(final AccessCodeEntity entity) {
        entity.setIssued(true);
        repository.save(entity);
        return entity;
    }

    private AccessCodeEntity checkCodeIssued(final AccessCodeEntity entity) {
        if (entity.isIssued()) {
            log.error("Authorization code has been already issued.");
            throw new AccessDeniedException(ACCESS_DENIED_MSG);
        }
        return entity;
    }

    private AccessCode toDTO(final AccessCodeEntity entity) {
        return AccessCode.builder()
                .code(entity.getCode())
                .build();
    }
}
