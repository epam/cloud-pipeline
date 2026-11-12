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

package com.epam.pipeline.security;

import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.entity.security.JwtTokenClaims;
import com.epam.pipeline.entity.user.GroupStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.quota.QuotaService;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.jwt.TokenVerificationException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UserAccessServiceTest {

    private static final String USER_NAME = "JANE";
    private static final Long USER_ID = 1L;
    private static final String ROLE_USER = "ROLE_USER";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String READER = "READER";
    private static final String GROUP = "BIOINFORMATICS";
    private static final int LOGIN_THRESHOLD = 1;

    private UserManager userManager;
    private UserAccessService service;

    @Before
    public void setUp() {
        userManager = Mockito.mock(UserManager.class);
        final QuotaService quotaService = Mockito.mock(QuotaService.class);
        final PreferenceManager preferenceManager = Mockito.mock(PreferenceManager.class);

        service = new UserAccessService();
        ReflectionTestUtils.setField(service, "userManager", userManager);
        ReflectionTestUtils.setField(service, "quotaService", quotaService);
        ReflectionTestUtils.setField(service, "preferenceManager", preferenceManager);
        // the whole point of the flag is the deployments that switch this on
        ReflectionTestUtils.setField(service, "validateUser", true);

        Mockito.when(preferenceManager.getPreference(SystemPreferences.SYSTEM_USER_JWT_LAST_LOGIN_THRESHOLD))
                .thenReturn(LOGIN_THRESHOLD);
        Mockito.when(quotaService.findActiveActionForUser(Mockito.any(), Mockito.any()))
                .thenReturn(Optional.empty());
        Mockito.when(userManager.loadGroupBlockingStatus(Mockito.anyList()))
                .thenReturn(Collections.emptyList());
    }

    /**
     * The restriction a narrowed token carries only holds if the authorities are not refreshed from
     * the stored user on the next request.
     */
    @Test
    public void shouldKeepTheTokenAuthoritiesWhenTheyAreFinal() {
        storedUser(ROLE_ADMIN, ROLE_USER, READER);

        final UserContext context = service.getJwtUser(rawToken(), claims(true, ROLE_USER));

        assertThat(authoritiesOf(context)).containsExactly(ROLE_USER);
    }

    /**
     * Narrowing is an intersection, not a substitution, so withdrawing a role in the database
     * still takes effect — which is the reason a deployment turns this validation on at all.
     */
    @Test
    public void shouldDropAnAuthorityThatHasBeenWithdrawnFromTheStoredUser() {
        storedUser(ROLE_USER);

        final UserContext context = service.getJwtUser(rawToken(), claims(true, ROLE_USER, READER));

        assertThat(authoritiesOf(context)).containsExactly(ROLE_USER);
    }

    @Test
    public void shouldNotGrantAnAuthorityTheStoredUserDoesNotHold() {
        storedUser(ROLE_USER);

        final UserContext context = service.getJwtUser(rawToken(), claims(true, ROLE_ADMIN));

        assertThat(authoritiesOf(context)).isEmpty();
    }

    @Test
    public void shouldDropAGroupThatHasBeenWithdrawnFromTheStoredUser() {
        final PipelineUser stored = storedUser(ROLE_USER);
        stored.setGroups(Collections.<String>emptyList());

        final UserContext context = service.getJwtUser(rawToken(),
                claimsWithGroups(true, Collections.singletonList(GROUP), ROLE_USER));

        assertThat(authoritiesOf(context)).containsExactly(ROLE_USER);
    }

    @Test
    public void shouldRefreshAuthoritiesFromTheStoredUserWhenTheyAreNotFinal() {
        storedUser(ROLE_ADMIN, READER);

        final UserContext context = service.getJwtUser(rawToken(), claims(false, ROLE_USER));

        assertThat(authoritiesOf(context)).containsExactlyInAnyOrder(ROLE_ADMIN, READER, GROUP);
    }

    @Test
    public void shouldStillRejectABlockedUserWhenAuthoritiesAreFinal() {
        storedUser(ROLE_USER).setBlocked(true);

        assertThatThrownBy(() -> service.getJwtUser(rawToken(), claims(true, ROLE_USER)))
                .isInstanceOf(LockedException.class);
    }

    /**
     * Group blocking is checked against the groups the stored user really has, not the ones left in
     * the token, so narrowing cannot be used to slip past it.
     */
    @Test
    public void shouldStillRejectABlockedGroupWhenAuthoritiesAreFinal() {
        storedUser(ROLE_USER);
        Mockito.when(userManager.loadGroupBlockingStatus(Mockito.anyList()))
                .thenReturn(Collections.singletonList(new GroupStatus(GROUP, true, null)));

        assertThatThrownBy(() -> service.getJwtUser(rawToken(), claims(true, ROLE_USER)))
                .isInstanceOf(LockedException.class);
    }

    @Test
    public void shouldRejectATokenWhoseUserIdDoesNotMatchTheStoredUser() {
        final PipelineUser stored = storedUser(ROLE_USER);
        stored.setId(USER_ID + 1);

        assertThatThrownBy(() -> service.getJwtUser(rawToken(), claims(true, ROLE_USER)))
                .isInstanceOf(TokenVerificationException.class);
    }

    @Test
    public void shouldKeepTheTokenAuthoritiesWhenTheStoredUserIsGone() {
        Mockito.when(userManager.loadUserByName(USER_NAME)).thenReturn(null);

        final UserContext context = service.getJwtUser(rawToken(), claims(true, ROLE_USER));

        assertThat(authoritiesOf(context)).containsExactly(ROLE_USER);
    }

    private PipelineUser storedUser(final String... roles) {
        final PipelineUser user = PipelineUser.builder()
                .id(USER_ID)
                .userName(USER_NAME)
                .roles(Arrays.stream(roles).map(Role::new).collect(Collectors.toList()))
                .groups(Collections.singletonList(GROUP))
                .build();
        Mockito.when(userManager.loadUserByName(USER_NAME)).thenReturn(user);
        return user;
    }

    private static JwtTokenClaims claims(final boolean authoritiesScoped, final String... roles) {
        return claimsWithGroups(authoritiesScoped, Collections.<String>emptyList(), roles);
    }

    private static JwtTokenClaims claimsWithGroups(final boolean authoritiesScoped,
                                                   final List<String> groups,
                                                   final String... roles) {
        return JwtTokenClaims.builder()
                .jwtTokenId(UUID.randomUUID().toString())
                .userName(USER_NAME)
                .userId(String.valueOf(USER_ID))
                .orgUnitId("")
                .roles(Arrays.asList(roles))
                .groups(groups)
                .authoritiesScoped(authoritiesScoped)
                .build();
    }

    private static JwtRawToken rawToken() {
        return new JwtRawToken("token");
    }

    private static List<String> authoritiesOf(final UserContext context) {
        return context.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
    }
}
