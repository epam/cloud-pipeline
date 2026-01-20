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
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import joptsimple.internal.Strings;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;

import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UnsecuredAccessServiceTest {
    private static final String USER_NAME = "TEST_USER";
    private static final String TEST_TOKEN = "abc";
    private static final int TTL = 10;
    private static final int EXPIRED_MINUTES = 15;
    private static final int CODE_CHALLENGE_LENGTH = 45;

    private final AccessCodeRepository repository = mock(AccessCodeRepository.class);
    private final UserManager userManager = mock(UserManager.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final UnsecuredAccessService unsecuredAccessService = new UnsecuredAccessService(repository,
            userManager, preferenceManager);

    @Test
    public void shouldReturnAccessCode() {
        final AccessCodeEntity accessCodeEntity = entity();
        when(repository.findByCodeChallenge(any())).thenReturn(Optional.of(accessCodeEntity));
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_ACCESS_CODE_TTL_MINUTES)).thenReturn(TTL);

        final AccessCode accessCode = unsecuredAccessService.findCode(accessCodeEntity.getCodeChallenge());

        assertNotNull(accessCode);
        assertEquals(accessCodeEntity.getCode(), accessCode.getCode());
        verify(repository).findByCodeChallenge(accessCodeEntity.getCodeChallenge());
        verify(repository).save(accessCodeEntity);
    }

    @Test
    public void shouldDenyIfCodeAlreadyIssued() {
        final AccessCodeEntity issuedEntity = entity();
        issuedEntity.setIssued(true);
        when(repository.findByCodeChallenge(any())).thenReturn(Optional.of(issuedEntity));

        assertThrows(AccessDeniedException.class, () ->
                unsecuredAccessService.findCode(issuedEntity.getCodeChallenge()));
        verify(repository).findByCodeChallenge(issuedEntity.getCodeChallenge());
        verify(repository).delete(issuedEntity);
    }

    @Test
    public void shouldReturnNullIfCodeChallengeNotFound() {
        when(repository.findByCodeChallenge(any())).thenReturn(Optional.empty());

        final AccessCode accessCode = unsecuredAccessService.findCode(UUID.randomUUID().toString());

        assertNull(accessCode);
        verify(repository).findByCodeChallenge(any());
    }

    @Test
    public void shouldDenyIfCodeExpired() {
        final AccessCodeEntity expiredEntity = entity();
        expiredEntity.setCreated(DateUtils.nowUTC().minusMinutes(EXPIRED_MINUTES));
        when(repository.findByCodeChallenge(any())).thenReturn(Optional.of(expiredEntity));
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_ACCESS_CODE_TTL_MINUTES)).thenReturn(TTL);

        assertThrows(AccessDeniedException.class, () ->
                unsecuredAccessService.findCode(expiredEntity.getCodeChallenge()));
        verify(repository).findByCodeChallenge(expiredEntity.getCodeChallenge());
        verify(repository).delete(expiredEntity);
    }

    @Test
    public void shouldExchangeCodeForToken() {
        final AccessCodeEntity accessCodeEntity = entity();
        when(repository.findByCode(any())).thenReturn(Optional.of(accessCodeEntity));
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_ACCESS_CODE_TTL_MINUTES)).thenReturn(TTL);
        when(userManager.issueToken(any(), any())).thenReturn(new JwtRawToken(TEST_TOKEN));

        final JwtRawToken token = unsecuredAccessService.exchangeCodeForToken(accessCodeEntity.getCode(),
                accessCodeEntity.getCodeChallenge());

        assertNotNull(token);
        assertEquals(TEST_TOKEN, token.getToken());
        verify(repository).findByCode(accessCodeEntity.getCode());
        verify(repository).delete(accessCodeEntity);
        verify(userManager).issueToken(USER_NAME, null);
    }

    @Test
    public void shouldNotExchangeCodeForTokenIfCodeNotFound() {
        when(repository.findByCode(any())).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () ->
                unsecuredAccessService.exchangeCodeForToken(UUID.randomUUID().toString(),
                        UUID.randomUUID().toString()));
        verify(repository).findByCode(any());
    }

    @Test
    public void shouldNotExchangeCodeForTokenIfCodeExpired() {
        final AccessCodeEntity expiredEntity = entity();
        expiredEntity.setCreated(DateUtils.nowUTC().minusMinutes(EXPIRED_MINUTES));
        when(repository.findByCode(any())).thenReturn(Optional.of(expiredEntity));
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_ACCESS_CODE_TTL_MINUTES)).thenReturn(TTL);

        assertThrows(AccessDeniedException.class, () ->
                unsecuredAccessService.exchangeCodeForToken(expiredEntity.getCode(), expiredEntity.getCodeChallenge()));
        verify(repository).findByCode(expiredEntity.getCode());
        verify(repository).delete(expiredEntity);
    }

    @Test
    public void shouldNotExchangeCodeForTokenIfEmptyInputs() {
        final AccessCodeEntity accessCodeEntity = entity();
        when(repository.findByCode(any())).thenReturn(Optional.of(accessCodeEntity));

        assertThrows(AccessDeniedException.class, () ->
                unsecuredAccessService.exchangeCodeForToken(accessCodeEntity.getCode(), Strings.EMPTY));
        verify(repository).findByCode(accessCodeEntity.getCode());
        verify(repository).delete(accessCodeEntity);
    }

    @Test
    public void shouldNotExchangeCodeForTokenIfCodeVerifierNotValid() {
        final AccessCodeEntity accessCodeEntity = entity();
        when(repository.findByCode(any())).thenReturn(Optional.of(accessCodeEntity));
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_ACCESS_CODE_TTL_MINUTES)).thenReturn(TTL);

        final String invalidCodeVerifier = generateCodeChallenge();

        assertThrows(AccessDeniedException.class, () ->
                unsecuredAccessService.exchangeCodeForToken(accessCodeEntity.getCode(), invalidCodeVerifier));
        verify(repository).findByCode(accessCodeEntity.getCode());
        verify(repository).delete(accessCodeEntity);
    }

    private AccessCodeEntity entity() {
        return AccessCodeEntity.builder()
                .code(UUID.randomUUID().toString())
                .codeChallenge(generateCodeChallenge())
                .codeChallengeMethod(CodeChallengeMethod.PLAIN.getValue())
                .issued(false)
                .userName(USER_NAME)
                .created(LocalDateTime.now())
                .build();
    }

    private String generateCodeChallenge() {
        return RandomStringUtils.randomAlphanumeric(CODE_CHALLENGE_LENGTH);
    }
}
