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
import com.epam.pipeline.entity.user.GroupStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.quota.QuotaService;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.user.RoleManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.saml.SamlUserRegisterStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.security.saml.SamlUserRegisterStrategy.*;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD.UnusedPrivateField")
class UserAccessServiceTest {

    private static final String USER_NAME = "TEST_USER";
    private static final String OLD_USER_NAME = "user_name";
    private static final String SAML_ATTRIBUTE_1 = "ATTR_1";
    private static final String SAML_ATTRIBUTE_2 = "ATTR_2";
    private static final String SAML_ATTRIBUTES_STRING = "ATTR_3";
    private static final String ATTRIBUTES_KEY_1 = "email";
    private static final String ATTRIBUTES_KEY_2 = "user";

    private final PipelineUser user = new PipelineUser();
    private final List<String> groups = Stream.of(SAML_ATTRIBUTE_1, SAML_ATTRIBUTE_2).collect(Collectors.toList());
    private final UserContext expectedUserContext = new UserContext(1L, USER_NAME.toUpperCase());
    private final Map<String, String> expectedAttributes = initAttributes();

    @Mock
    private UserManager userManager;
    @Mock
    private RoleManager roleManager;
    @Mock
    private MessageHelper messageHelper;
    @Mock
    private GrantPermissionManager permissionManager;
    @Mock
    private PreferenceManager preferenceManager;
    @Mock
    private QuotaService quotaService;

    @InjectMocks
    private UserAccessService userAccessService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        user.setUserName(USER_NAME);
        user.setAttributes(expectedAttributes);
        user.setGroups(groups);

        expectedUserContext.setGroups(groups);
    }

    @Test
    void shouldLoadUserWithCreation() {
        switchRegisterStrategyTo(AUTO);
        mockUserDoesNotExistSituation();
        when(roleManager.getDefaultRolesIds()).thenReturn(Collections.singletonList(1L));

        assertUserNameAndGroups(userAccessService.parseUser(USER_NAME, groups, expectedAttributes));
    }

    @Test
    void shouldLoadUserWithExistingUser() {
        user.setUserName(OLD_USER_NAME);
        user.setGroups(Stream.of(SAML_ATTRIBUTE_1, SAML_ATTRIBUTE_2).collect(Collectors.toList()));

        when(userManager.loadUserByName(anyString())).thenReturn(user);
        when(userManager.updateUserSAMLInfo(any(), any(), any(), any(), any())).thenReturn(user);

        assertUserNameAndGroups(userAccessService.parseUser(USER_NAME, groups, expectedAttributes));
    }

    @Test
    void shouldRegisterUserIfGroupPresentsAndEntityExistsWithExplicitGroupMode() {
        switchRegisterStrategyTo(EXPLICIT_GROUP);
        mockUserDoesNotExistSituation();

        when(permissionManager.isGroupRegistered(any())).thenReturn(true);
        when(roleManager.getDefaultRolesIds()).thenReturn(Collections.singletonList(1L));

        assertUserNameAndGroups(userAccessService.parseUser(USER_NAME, groups, expectedAttributes));
    }

    @Test
    void shouldAuthorizeRegisteredUserIfGroupsHaveValidGroupStatus() {
        when(userManager.loadUserByName(anyString())).thenReturn(user);
        final GroupStatus validGroupStatus = new GroupStatus(SAML_ATTRIBUTE_1, false, null);
        when(userManager.loadGroupBlockingStatus(groups)).thenReturn(Collections.singletonList(validGroupStatus));

        assertUserNameAndGroups(userAccessService.parseUser(USER_NAME, groups, expectedAttributes));
    }

    @Test
    void shouldAuthorizeRegisteredUserIfGroupsAreNotAtGroupStatus() {
        when(userManager.loadUserByName(anyString())).thenReturn(user);
        when(userManager.loadGroupBlockingStatus(groups)).thenReturn(Collections.emptyList());

        assertUserNameAndGroups(userAccessService.parseUser(USER_NAME, groups, expectedAttributes));
    }

    @Test
    void shouldThrowAuthorizationExceptionForBlockedUser() {
        user.setBlocked(true);
        when(userManager.loadUserByName(anyString())).thenReturn(user);

        assertThrows(LockedException.class, () -> userAccessService.parseUser(USER_NAME, groups, expectedAttributes));
    }

    @Test
    void shouldNotRegisterUserIfGroupNotPresentsWithExplicitGroupMode() {
        switchRegisterStrategyTo(EXPLICIT_GROUP);
        mockUserDoesNotExistSituation();

        assertThrows(UsernameNotFoundException.class,
                () -> userAccessService.parseUser(USER_NAME, groups, expectedAttributes));
    }

    @Test
    void shouldNotRegisterUserWithExplicitMode() {
        switchRegisterStrategyTo(EXPLICIT);
        mockUserDoesNotExistSituation();

        assertThrows(UsernameNotFoundException.class,
                () -> userAccessService.parseUser(USER_NAME, groups, expectedAttributes));
    }

    @Test
    void shouldThrowAuthorizationExceptionForUserFromBlockedGroup() {
        blockOneGroupForCurrentUser();

        assertThrows(LockedException.class, () -> userAccessService.validateUserGroupsBlockStatus(user));
    }

    @Test
    void shouldThrowAuthorizationExceptionForUserWithBlockedRole() {
        final Role role = new Role();
        role.setName(SAML_ATTRIBUTE_1);
        role.setPredefined(false);
        user.setRoles(Collections.singletonList(role));
        blockOneGroupForCurrentUser();

        assertThrows(LockedException.class, () -> userAccessService.validateUserGroupsBlockStatus(user));
    }

    private void switchRegisterStrategyTo(final SamlUserRegisterStrategy samlUserRegisterStrategy) {
        ReflectionTestUtils.setField(userAccessService, "autoCreateUsers", samlUserRegisterStrategy);
    }

    private void mockUserDoesNotExistSituation() {
        when(userManager.loadUserByName(anyString())).thenReturn(null);
        when(userManager.create(any(), any(), any(), any(), any())).thenReturn(user);
    }

    private Map<String, String> initAttributes() {
        final Map<String, String> attributes = new HashMap<>();
        attributes.put(ATTRIBUTES_KEY_1, SAML_ATTRIBUTES_STRING);
        attributes.put(ATTRIBUTES_KEY_2, SAML_ATTRIBUTES_STRING);
        return attributes;
    }

    private void assertUserNameAndGroups(final UserContext actualUserContext) {
        assertEquals(expectedUserContext.getUsername(), actualUserContext.getUsername());
        assertEquals(expectedUserContext.getGroups(), actualUserContext.getGroups());
    }

    private void blockOneGroupForCurrentUser() {
        when(userManager.loadUserByName(anyString())).thenReturn(user);
        final GroupStatus blockedGroupStatus = new GroupStatus(SAML_ATTRIBUTE_1, true, null);
        doReturn(Collections.singletonList(blockedGroupStatus)).when(userManager).loadGroupBlockingStatus(groups);
    }
}
