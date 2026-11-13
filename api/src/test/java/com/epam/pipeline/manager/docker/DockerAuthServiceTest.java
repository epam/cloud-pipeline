/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.docker;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.security.JwtTokenClaims;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.security.jwt.JwtTokenGenerator;
import com.epam.pipeline.security.jwt.JwtTokenVerifier;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class DockerAuthServiceTest {

    private static final String USER_NAME = "CI";
    private static final Long USER_ID = 1L;
    private static final String ROLE_USER = "ROLE_USER";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String REGISTRY_HOST = "registry.local";
    private static final String TOKEN = "token";

    private JwtTokenVerifier jwtTokenVerifier;
    private DockerAuthService service;

    @Before
    public void setUp() {
        final UserManager userManager = Mockito.mock(UserManager.class);
        jwtTokenVerifier = Mockito.mock(JwtTokenVerifier.class);
        service = new DockerAuthService(userManager, jwtTokenVerifier,
                Mockito.mock(JwtTokenGenerator.class), Mockito.mock(PreferenceManager.class),
                Mockito.mock(MessageHelper.class));

        final PipelineUser stored = PipelineUser.builder()
                .id(USER_ID)
                .userName(USER_NAME)
                .roles(Arrays.asList(ROLE_ADMIN, ROLE_USER).stream().map(Role::new)
                        .collect(Collectors.toList()))
                .groups(Collections.<String>emptyList())
                .build();
        Mockito.when(userManager.loadUserContext(USER_NAME)).thenReturn(new UserContext(stored));
    }

    @Test
    public void shouldAcceptAnOrdinaryToken() {
        Mockito.when(jwtTokenVerifier.readClaims(TOKEN)).thenReturn(claims(false));

        final UserContext context = service.verifyTokenForDocker(USER_NAME, TOKEN, REGISTRY_HOST);

        assertThat(context.getUsername()).isEqualTo(USER_NAME);
    }

    /**
     * A token whose roles were narrowed is accepted here too. Registry access is authorized by
     * user name, which resolves the roles from the stored user, so the narrowing does not reach
     * it — see the limitation noted on the token exchange manual page.
     */
    @Test
    public void shouldAcceptATokenWhoseRolesWereNarrowed() {
        Mockito.when(jwtTokenVerifier.readClaims(TOKEN)).thenReturn(claims(true));

        final UserContext context = service.verifyTokenForDocker(USER_NAME, TOKEN, REGISTRY_HOST);

        assertThat(context.getUsername()).isEqualTo(USER_NAME);
        // the token carries ROLE_USER alone, and the context comes back with the stored user's
        // roles. This is the limitation the manual page states, so it is pinned here: were a
        // change to start honouring the token's roles, that page would have to change with it
        assertThat(context.getRoles()).extracting(Role::getName).contains(ROLE_ADMIN);
    }

    private static JwtTokenClaims claims(final boolean authoritiesScoped) {
        return JwtTokenClaims.builder()
                .jwtTokenId(UUID.randomUUID().toString())
                .userName(USER_NAME)
                .userId(String.valueOf(USER_ID))
                .orgUnitId("")
                .roles(Collections.singletonList(ROLE_USER))
                .groups(Collections.<String>emptyList())
                .expiresAt(LocalDateTime.now().plusHours(1))
                .authoritiesScoped(authoritiesScoped)
                .build();
    }
}
