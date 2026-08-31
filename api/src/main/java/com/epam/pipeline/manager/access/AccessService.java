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

import com.epam.pipeline.entity.access.AccessCodeEntity;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.repository.access.AccessCodeRepository;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * This class provides method to obtain access token for authorized users.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccessService {
    private static final String ACCESS_DENIED_MSG = "Access is denied";

    private final AccessCodeRepository repository;
    private final AuthManager authManager;
    private final PreferenceManager preferenceManager;

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @Transactional
    public void start(final String codeChallenge, final CodeChallengeMethod codeChallengeMethod) {
        verify(codeChallenge, codeChallengeMethod);

        try {
            final PipelineUser user = authManager.getCurrentUser();
            final String userName = user.getUserName();
            log.debug("User {} initiates login process.", userName);

            final AccessCodeEntity entity = AccessCodeEntity.builder()
                    .userName(userName)
                    .code(generateAuthorizationCode())
                    .codeChallenge(codeChallenge)
                    .codeChallengeMethod(codeChallengeMethod.getValue())
                    .issued(false)
                    .created(DateUtils.nowUTC())
                    .build();

            repository.save(entity);
        } catch (Exception e) {
            log.error("An error has occurred:", e);
            throw new AccessDeniedException(ACCESS_DENIED_MSG);
        }
    }

    @Transactional
    public void deleteExpired() {
        final Integer ttl = preferenceManager.getPreference(SystemPreferences.SYSTEM_ACCESS_CODE_TTL_MINUTES);
        log.debug("Starting cleaning authorization codes.");
        repository.deleteExpired(ttl);
    }

    private void verify(final String codeChallenge, final CodeChallengeMethod codeChallengeMethod) {
        if (StringUtils.isBlank(codeChallenge) || Objects.isNull(codeChallengeMethod)) {
            log.error("Invalid input parameters.");
            throw new AccessDeniedException(ACCESS_DENIED_MSG);
        }
        final List<String> allowedMethods = ListUtils.emptyIfNull(preferenceManager.getPreference(
                SystemPreferences.SYSTEM_ACCESS_ALLOWED_CODE_CHALLENGE_METHODS));
        if (!allowedMethods.contains(codeChallengeMethod.getValue())) {
            log.error("Code challenge method {} is not allowed.", codeChallengeMethod.getValue());
            throw new AccessDeniedException(ACCESS_DENIED_MSG);
        }
    }

    private String generateAuthorizationCode() {
        final Integer accessCodeLength = preferenceManager.getPreference(SystemPreferences.SYSTEM_ACCESS_CODE_LENGTH);
        final SecureRandom secureRandom = new SecureRandom();
        final Base64.Encoder base64Encoder = Base64.getUrlEncoder();
        final byte[] randomBytes = new byte[accessCodeLength];
        secureRandom.nextBytes(randomBytes);
        return base64Encoder.encodeToString(randomBytes);
    }
}
