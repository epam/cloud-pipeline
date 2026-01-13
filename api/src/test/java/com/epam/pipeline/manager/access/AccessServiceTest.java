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
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.repository.access.AccessCodeRepository;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import joptsimple.internal.Strings;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static com.epam.pipeline.util.CustomAssertions.notInvoked;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AccessServiceTest {
    private static final String USER_NAME = "TEST_USER";
    private static final String CODE_CHALLENGE = RandomStringUtils.randomAlphanumeric(45);
    private static final CodeChallengeMethod VALID_CODE_CHALLENGE_METHOD = CodeChallengeMethod.PLAIN;
    private static final CodeChallengeMethod INVALID_CODE_CHALLENGE_METHOD = CodeChallengeMethod.S256;
    private static final int ACCESS_CODE_LENGTH = 15;

    private final AccessCodeRepository repository = mock(AccessCodeRepository.class);
    private final AuthManager authManager = mock(AuthManager.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final AccessService accessService = new AccessService(repository, authManager, preferenceManager);

    @Test
    public void shouldGenerateAuthorizationCode() {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_ACCESS_CODE_LENGTH))
                .thenReturn(ACCESS_CODE_LENGTH);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_ACCESS_ALLOWED_CODE_CHALLENGE_METHODS))
                .thenReturn(Collections.singletonList(VALID_CODE_CHALLENGE_METHOD.getValue()));
        when(authManager.getCurrentUser()).thenReturn(new PipelineUser(USER_NAME));

        accessService.start(CODE_CHALLENGE, VALID_CODE_CHALLENGE_METHOD);

        verify(repository).save((AccessCodeEntity) any());
    }

    @Test
    public void shouldDenyIfCodeChallengeMethodNotAllowed() {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_ACCESS_CODE_LENGTH))
                .thenReturn(ACCESS_CODE_LENGTH);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_ACCESS_ALLOWED_CODE_CHALLENGE_METHODS))
                .thenReturn(Collections.singletonList(VALID_CODE_CHALLENGE_METHOD.getValue()));
        when(authManager.getCurrentUser()).thenReturn(new PipelineUser(USER_NAME));

        assertThrows(AccessDeniedException.class, () ->
                accessService.start(CODE_CHALLENGE, INVALID_CODE_CHALLENGE_METHOD));

        notInvoked(repository).save((AccessCodeEntity) any());
    }

    @Test
    public void shouldDenyIfCodeChallengeWasNotProvided() {
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_ACCESS_CODE_LENGTH))
                .thenReturn(ACCESS_CODE_LENGTH);
        when(preferenceManager.getPreference(SystemPreferences.SYSTEM_ACCESS_ALLOWED_CODE_CHALLENGE_METHODS))
                .thenReturn(Collections.singletonList(VALID_CODE_CHALLENGE_METHOD.getValue()));
        when(authManager.getCurrentUser()).thenReturn(new PipelineUser(USER_NAME));

        assertThrows(AccessDeniedException.class, () ->
                accessService.start(Strings.EMPTY, VALID_CODE_CHALLENGE_METHOD));

        notInvoked(repository).save((AccessCodeEntity) any());
    }
}
