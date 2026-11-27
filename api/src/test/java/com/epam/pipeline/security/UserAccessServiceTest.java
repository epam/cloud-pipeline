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

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.entity.security.JwtTokenClaims;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.GroupStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.quota.QuotaService;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.user.RoleManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.jwt.TokenVerificationException;
import com.epam.pipeline.security.saml.SamlUserRegisterStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UserAccessServiceTest {

    private static final String USER_NAME = "JANE";
    private static final Long USER_ID = 1L;
    private static final String ROLE_USER = "ROLE_USER";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String READER = "READER";
    private static final String GROUP = "BIOINFORMATICS";
    private static final int LOGIN_THRESHOLD = 1;

    private static final String SAML_USER_NAME = "TEST_USER";
    private static final String OLD_USER_NAME = "user_name";
    private static final String SAML_ATTRIBUTE_1 = "ATTR_1";
    private static final String SAML_ATTRIBUTE_2 = "ATTR_2";
    private static final String SAML_ATTRIBUTES_STRING = "ATTR_3";
    private static final String ATTRIBUTES_KEY_1 = "email";
    private static final String ATTRIBUTES_KEY_2 = "user";

    private final PipelineUser samlUser = new PipelineUser();
    private final List<String> samlGroups = Stream.of(SAML_ATTRIBUTE_1, SAML_ATTRIBUTE_2)
            .collect(Collectors.toList());
    private final Map<String, String> samlAttributes = initAttributes();

    private UserManager userManager;
    private PreferenceManager preferenceManager;
    private QuotaService quotaService;
    private RoleManager roleManager;
    private MessageHelper messageHelper;
    private GrantPermissionManager permissionManager;
    private UserAccessService service;

    @BeforeEach
    public void setUp() {
        userManager = Mockito.mock(UserManager.class);
        quotaService = Mockito.mock(QuotaService.class);
        preferenceManager = Mockito.mock(PreferenceManager.class);
        roleManager = Mockito.mock(RoleManager.class);
        messageHelper = Mockito.mock(MessageHelper.class);
        permissionManager = Mockito.mock(GrantPermissionManager.class);

        // validateUser is on: the whole point of the flag is the deployments that switch this on
        service = buildService(SamlUserRegisterStrategy.EXPLICIT, false);

        Mockito.when(preferenceManager.getPreference(SystemPreferences.SYSTEM_USER_JWT_LAST_LOGIN_THRESHOLD))
                .thenReturn(LOGIN_THRESHOLD);
        Mockito.when(quotaService.findActiveActionForUser(Mockito.any(), Mockito.any()))
                .thenReturn(Optional.empty());
        Mockito.when(userManager.loadGroupBlockingStatus(Mockito.anyList()))
                .thenReturn(Collections.emptyList());

        samlUser.setUserName(SAML_USER_NAME);
        samlUser.setAttributes(samlAttributes);
        samlUser.setGroups(samlGroups);
    }

    /**
     * The restriction a narrowed token carries only holds if its roles are not refreshed from the
     * stored user on the next request. Its groups are refreshed, because nothing narrowed them.
     */
    @Test
    public void shouldKeepTheTokenRolesWhenTheyAreScoped() {
        storedUser(ROLE_ADMIN, ROLE_USER, READER);

        final UserContext context = service.getJwtUser(rawToken(), claims(true, ROLE_USER));

        assertThat(authoritiesOf(context)).containsExactlyInAnyOrder(ROLE_USER, GROUP);
    }

    /**
     * Narrowing is an intersection, not a substitution, so withdrawing a role in the database
     * still takes effect — which is the reason a deployment turns this validation on at all.
     */
    @Test
    public void shouldDropARoleThatHasBeenWithdrawnFromTheStoredUser() {
        storedUser(ROLE_USER);

        final UserContext context = service.getJwtUser(rawToken(), claims(true, ROLE_USER, READER));

        assertThat(authoritiesOf(context)).containsExactlyInAnyOrder(ROLE_USER, GROUP);
    }

    @Test
    public void shouldNotGrantARoleTheStoredUserDoesNotHold() {
        storedUser(ROLE_USER);

        final UserContext context = service.getJwtUser(rawToken(), claims(true, ROLE_ADMIN));

        assertThat(authoritiesOf(context)).containsExactly(GROUP);
    }

    /**
     * Only roles are ever narrowed, so a group granted after the token was issued has to show up
     * on the next request — pinning the group set to the moment of issue would hide it, and the
     * permissions that come with it, for the rest of the token's life.
     */
    @Test
    public void shouldPickUpAGroupGrantedAfterTheTokenWasIssued() {
        final PipelineUser stored = storedUser(ROLE_USER);
        stored.setGroups(Arrays.asList(GROUP, "LATE_GROUP"));

        final UserContext context = service.getJwtUser(rawToken(),
                claimsWithGroups(true, Collections.singletonList(GROUP), ROLE_USER));

        assertThat(authoritiesOf(context)).containsExactlyInAnyOrder(ROLE_USER, GROUP, "LATE_GROUP");
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

    @Test
    public void shouldCreateAnUnregisteredUserWithAutoMode() {
        service = buildService(SamlUserRegisterStrategy.AUTO, false);
        mockUserDoesNotExistSituation();
        when(roleManager.getDefaultRolesIds()).thenReturn(Collections.singletonList(1L));

        assertSamlUser(service.parseUser(SAML_USER_NAME, samlGroups, samlAttributes));
        verify(userManager).create(SAML_USER_NAME, Collections.singletonList(1L), samlGroups, samlAttributes, null);
    }

    @Test
    public void shouldUpdateTheGroupsAndAttributesOfARegisteredUser() {
        samlUser.setUserName(OLD_USER_NAME);
        when(userManager.loadUserByName(anyString())).thenReturn(samlUser);
        when(userManager.needToUpdateUser(any(), any(), any())).thenReturn(true);
        when(userManager.updateUserSAMLInfo(any(), any(), any(), any(), any())).thenReturn(samlUser);

        assertSamlUser(service.parseUser(SAML_USER_NAME, samlGroups, samlAttributes));
        verify(userManager).updateUserSAMLInfo(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldRegisterAUserOfARegisteredGroupWithExplicitGroupMode() {
        service = buildService(SamlUserRegisterStrategy.EXPLICIT_GROUP, false);
        mockUserDoesNotExistSituation();
        when(permissionManager.isGroupRegistered(any())).thenReturn(true);
        when(roleManager.getDefaultRolesIds()).thenReturn(Collections.singletonList(1L));

        assertSamlUser(service.parseUser(SAML_USER_NAME, samlGroups, samlAttributes));
    }

    @Test
    public void shouldAuthorizeARegisteredUserWhoseGroupsAreNotBlocked() {
        when(userManager.loadUserByName(anyString())).thenReturn(samlUser);
        final GroupStatus validGroupStatus = new GroupStatus(SAML_ATTRIBUTE_1, false, null);
        when(userManager.loadGroupBlockingStatus(samlGroups))
                .thenReturn(Collections.singletonList(validGroupStatus));

        assertSamlUser(service.parseUser(SAML_USER_NAME, samlGroups, samlAttributes));
    }

    /**
     * The last login date is the caller's to update, once the whole login has passed: 0.16 has
     * always done it in the SAML login itself.
     */
    @Test
    public void shouldNotUpdateTheLastLoginDateWhenParsingAUser() {
        when(userManager.loadUserByName(anyString())).thenReturn(samlUser);

        service.parseUser(SAML_USER_NAME, samlGroups, samlAttributes);

        verify(userManager, never()).updateLastLoginDate(any());
    }

    @Test
    public void shouldRejectABlockedRegisteredUser() {
        samlUser.setBlocked(true);
        when(userManager.loadUserByName(anyString())).thenReturn(samlUser);

        assertThatThrownBy(() -> service.parseUser(SAML_USER_NAME, samlGroups, samlAttributes))
                .isInstanceOf(LockedException.class);
    }

    @Test
    public void shouldNotRegisterAUserOfAnUnregisteredGroupWithExplicitGroupMode() {
        service = buildService(SamlUserRegisterStrategy.EXPLICIT_GROUP, false);
        mockUserDoesNotExistSituation();

        assertThatThrownBy(() -> service.parseUser(SAML_USER_NAME, samlGroups, samlAttributes))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    public void shouldNotRegisterAUserWithExplicitMode() {
        mockUserDoesNotExistSituation();

        assertThatThrownBy(() -> service.parseUser(SAML_USER_NAME, samlGroups, samlAttributes))
                .isInstanceOf(UsernameNotFoundException.class);
        verify(userManager, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldAdmitAnUnregisteredUserAsAnonymousWhenAllowed() {
        service = buildService(SamlUserRegisterStrategy.EXPLICIT, true);
        mockUserDoesNotExistSituation();

        final UserContext context = service.parseUser(SAML_USER_NAME, samlGroups, samlAttributes);

        assertThat(context.getUserId()).isNull();
        assertThat(context.getUsername()).isEqualTo(SAML_USER_NAME);
        assertThat(context.getGroups()).isEqualTo(samlGroups);
        assertThat(context.getRoles()).extracting(Role::getName)
                .containsExactly(DefaultRoles.ROLE_ANONYMOUS_USER.getName());
        verify(userManager, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldAdmitAUserOfAnUnregisteredGroupAsAnonymousWhenAllowed() {
        service = buildService(SamlUserRegisterStrategy.EXPLICIT_GROUP, true);
        mockUserDoesNotExistSituation();

        final UserContext context = service.parseUser(SAML_USER_NAME, samlGroups, samlAttributes);

        assertThat(context.getUserId()).isNull();
        assertThat(context.getRoles()).extracting(Role::getName)
                .containsExactly(DefaultRoles.ROLE_ANONYMOUS_USER.getName());
    }

    @Test
    public void shouldRejectAUserFromABlockedGroup() {
        blockOneGroupForSamlUser();

        assertThatThrownBy(() -> service.validateUserGroupsBlockStatus(samlUser))
                .isInstanceOf(LockedException.class);
    }

    @Test
    public void shouldRejectAUserWithABlockedRole() {
        final Role role = new Role();
        role.setName(SAML_ATTRIBUTE_1);
        role.setPredefined(false);
        samlUser.setRoles(Collections.singletonList(role));
        blockOneGroupForSamlUser();

        assertThatThrownBy(() -> service.validateUserGroupsBlockStatus(samlUser))
                .isInstanceOf(LockedException.class);
    }

    private UserAccessService buildService(final SamlUserRegisterStrategy autoCreateUsers,
                                           final boolean allowAnonymous) {
        return new UserAccessService(userManager, true, preferenceManager, quotaService,
                roleManager, messageHelper, permissionManager, autoCreateUsers, allowAnonymous);
    }

    private void mockUserDoesNotExistSituation() {
        when(userManager.loadUserByName(anyString())).thenReturn(null);
        when(userManager.create(any(), any(), any(), any(), any())).thenReturn(samlUser);
    }

    private void blockOneGroupForSamlUser() {
        when(userManager.loadUserByName(anyString())).thenReturn(samlUser);
        final GroupStatus blockedGroupStatus = new GroupStatus(SAML_ATTRIBUTE_1, true, null);
        doReturn(Collections.singletonList(blockedGroupStatus)).when(userManager)
                .loadGroupBlockingStatus(samlGroups);
    }

    private void assertSamlUser(final UserContext actual) {
        assertThat(actual.getUsername()).isEqualTo(SAML_USER_NAME);
        assertThat(actual.getGroups()).isEqualTo(samlGroups);
    }

    private static Map<String, String> initAttributes() {
        final Map<String, String> attributes = new HashMap<>();
        attributes.put(ATTRIBUTES_KEY_1, SAML_ATTRIBUTES_STRING);
        attributes.put(ATTRIBUTES_KEY_2, SAML_ATTRIBUTES_STRING);
        return attributes;
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
