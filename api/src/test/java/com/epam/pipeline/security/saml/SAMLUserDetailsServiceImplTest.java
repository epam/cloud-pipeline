/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.security.saml;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.user.GroupStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.user.RoleManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserAccessService;
import com.epam.pipeline.security.UserContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.opensaml.saml2.core.NameID;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.saml.SAMLCredential;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.security.saml.SamlUserRegisterStrategy.AUTO;
import static com.epam.pipeline.security.saml.SamlUserRegisterStrategy.EXPLICIT;
import static com.epam.pipeline.security.saml.SamlUserRegisterStrategy.EXPLICIT_GROUP;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

public class SAMLUserDetailsServiceImplTest {

    private static final String USER_NAME = "TEST_USER";
    private static final String TEST_STRING = "TEST";
    private static final String OLD_USER_NAME = "user_name";
    private static final String SAML_ATTRIBUTE_1 = "ATTR_1";
    private static final String SAML_ATTRIBUTE_2 = "ATTR_2";
    private static final String SAML_ATTRIBUTES_STRING = "ATTR_3";
    private static final String ATTRIBUTES_KEY_1 = "email";
    private static final String ATTRIBUTES_KEY_2 = "user";
    private static final String SAML_ATTRIBUTE_BLOCKED_USER_VALUE_1 = "true";
    private static final String SAML_ATTRIBUTE_BLOCKED_USER_VALUE_2 = "True";
    private static final String SAML_ATTRIBUTE_BLOCKED_USER_VALUE_3 = "TRUE";
    private static final String SAML_ATTRIBUTE_BLOCKED_USER_VALUE_4 = "tRUe";
    private static final String SAML_ATTRIBUTE_NOT_BLOCKED_USER_VALUE = "false";

    private final PipelineUser user = new PipelineUser();
    private final List<String> groups = Stream.of(SAML_ATTRIBUTE_1, SAML_ATTRIBUTE_2).collect(Collectors.toList());
    private final UserContext expectedUserContext = new UserContext(1L, USER_NAME.toUpperCase());
    private Map<String, String> expectedAttributes = new HashMap<>();

    @Mock
    private GrantPermissionManager mockPermissionManager;

    @Mock
    private SAMLCredential mockCredential;

    @Mock
    private NameID mockNameID;

    @Mock
    private UserManager mockUserManager;

    @SuppressWarnings("PMD.UnusedPrivateField")
    @Mock
    private MessageHelper mockMessageHelper;

    @Mock
    private RoleManager mockRoleManager;

    @Spy
    private final UserAccessService spyAccessService = new UserAccessService(mockUserManager, true);

    @InjectMocks
    private SAMLUserDetailsServiceImpl userDetailsService;

    @Before
    public void setUp() {
        expectedAttributes = initAttributes();
        user.setAttributes(expectedAttributes);
        user.setGroups(groups);
        expectedUserContext.setGroups(groups);

        MockitoAnnotations.initMocks(this);
        when(mockNameID.getValue()).thenReturn(USER_NAME);
        when(mockCredential.getNameID()).thenReturn(mockNameID);
        final String[] mockAttributesArray = {SAML_ATTRIBUTE_1, SAML_ATTRIBUTE_2};
        when(mockCredential.getAttributeAsStringArray(anyString())).thenReturn(mockAttributesArray);
        when(mockCredential.getAttributeAsString(anyString())).thenReturn(SAML_ATTRIBUTES_STRING);
    }

    @Test
    public void testLoadUserBySAMLWithCreation() {
        switchRegisterStrategyTo(AUTO);
        mockUserDoesNotExistSituation();
        user.setUserName(USER_NAME);
        when(mockRoleManager.getDefaultRolesIds()).thenReturn(Collections.singletonList(1L));
        doNothing().when(spyAccessService).validateUserGroupsBlockStatus(any());

        assertUserNameAndGroups(userDetailsService.loadUserBySAML(mockCredential));
    }

    @Test
    public void testLoadUserBySAMLWithExistingUser() {
        user.setUserName(OLD_USER_NAME);
        user.setGroups(Stream.of(SAML_ATTRIBUTE_1, SAML_ATTRIBUTE_2).collect(Collectors.toList()));
        when(mockUserManager.loadUserByName(anyString())).thenReturn(user);
        doNothing().when(spyAccessService).validateUserGroupsBlockStatus(any());
        when(mockUserManager.updateUserSAMLInfo(anyLong(), anyString(),
                                                anyListOf(Long.class), anyListOf(String.class),
                                                anyMapOf(String.class, String.class))).thenReturn(user);

        assertUserNameAndGroups(userDetailsService.loadUserBySAML(mockCredential));
    }

    @Test
    public void testReadAuthorities() {
        setAuthorities();

        final List<String> actualAuthorities = userDetailsService.readAuthorities(mockCredential);

        Assert.assertTrue(CollectionUtils.isEqualCollection(groups, actualAuthorities));
    }

    @Test
    public void testReadAttributes() {
        setSamlAttributes();

        final Map<String, String> readAttributes = userDetailsService.readAttributes(mockCredential);

        Assert.assertTrue(CollectionUtils.isEqualCollection(expectedAttributes.entrySet(), readAttributes.entrySet()));
    }

    @Test
    public void shouldRegisterUserIfGroupPresentsAndEntityExistsWithExplicitGroupMode() {
        switchRegisterStrategyTo(EXPLICIT_GROUP);
        mockUserDoesNotExistSituation();
        setAuthorities();
        setSamlAttributes();
        user.setUserName(USER_NAME);
        when(mockPermissionManager.isGroupRegistered(any())).thenReturn(true);
        when(mockRoleManager.getDefaultRolesIds()).thenReturn(Collections.singletonList(1L));
        doNothing().when(spyAccessService).validateUserGroupsBlockStatus(any());

        assertUserNameAndGroups(userDetailsService.loadUserBySAML(mockCredential));
    }

    @Test
    public void shouldAuthorizeRegisteredUserIfHisGroupsHaveValidGroupStatus() {
        setValidGroupsStatusForUser();
        doNothing().when(spyAccessService).validateUserGroupsBlockStatus(any());

        assertUserNameAndGroups(userDetailsService.loadUserBySAML(mockCredential));
    }

    @Test
    public void shouldAuthorizeRegisteredUserIfHisGroupsAreNotAtGroupStatus() {
        setEmptyGroupsStatusListForUser();
        doNothing().when(spyAccessService).validateUserGroupsBlockStatus(any());

        assertUserNameAndGroups(userDetailsService.loadUserBySAML(mockCredential));
    }

    @Test
    public void shouldNotRegisterUserIfGroupPresentsButNoEntityReferWithExplicitGroupMode() {
        switchRegisterStrategyTo(EXPLICIT_GROUP);
        mockUserDoesNotExistSituation();
        setAuthorities();
        setSamlAttributes();

        assertThrows(UsernameNotFoundException.class, () -> userDetailsService.loadUserBySAML(mockCredential));
    }

    @Test
    public void shouldNotRegisterUserIfGroupNotPresentsWithExplicitGroupMode() {
        switchRegisterStrategyTo(EXPLICIT_GROUP);
        mockUserDoesNotExistSituation();
        final String[] mockAttributesArray = {"unknown"};
        when(mockCredential.getAttributeAsStringArray(anyString())).thenReturn(mockAttributesArray);

        assertThrows(UsernameNotFoundException.class, () -> userDetailsService.loadUserBySAML(mockCredential));
    }

    @Test
    public void shouldNotRegisterUserWithExplicitMode() {
        switchRegisterStrategyTo(EXPLICIT);
        mockUserDoesNotExistSituation();

        assertThrows(UsernameNotFoundException.class, () -> userDetailsService.loadUserBySAML(mockCredential));
    }

    @Test
    public void shouldThrowAuthorizationExceptionForBlockedUser() {
        blockCurrentUser();

        assertThrows(LockedException.class, () -> userDetailsService.loadUserBySAML(mockCredential));
    }

    @Test
    public void shouldThrowAuthorizationExceptionForUserWithBlockedApplicationProperty1() {
        doNothing().when(spyAccessService).validateUserGroupsBlockStatus(any());
        setBlockedAttributeValue(SAML_ATTRIBUTE_BLOCKED_USER_VALUE_1);

        assertThrows(LockedException.class, () -> userDetailsService.loadUserBySAML(mockCredential));
    }

    @Test
    public void shouldThrowAuthorizationExceptionForUserWithBlockedApplicationProperty2() {
        doNothing().when(spyAccessService).validateUserGroupsBlockStatus(any());
        setBlockedAttributeValue(SAML_ATTRIBUTE_BLOCKED_USER_VALUE_2);

        assertThrows(LockedException.class, () -> userDetailsService.loadUserBySAML(mockCredential));
    }

    @Test
    public void shouldThrowAuthorizationExceptionForUserWithBlockedApplicationProperty3() {
        doNothing().when(spyAccessService).validateUserGroupsBlockStatus(any());
        setBlockedAttributeValue(SAML_ATTRIBUTE_BLOCKED_USER_VALUE_3);

        assertThrows(LockedException.class, () -> userDetailsService.loadUserBySAML(mockCredential));
    }

    @Test
    public void shouldAcceptUserWithValidNotBlockedApplicationProperty() {
        doNothing().when(spyAccessService).validateUserGroupsBlockStatus(any());
        setBlockedAttributeValue(SAML_ATTRIBUTE_NOT_BLOCKED_USER_VALUE);

        final UserContext actualUserContext = userDetailsService.loadUserBySAML(mockCredential);

        Assert.assertEquals(expectedUserContext.getUsername(), actualUserContext.getUsername());
    }

    @Test
    public void shouldAcceptUserWithValidEmptyApplicationProperty() {
        setBlockedAttributeValue(StringUtils.EMPTY);
        doNothing().when(spyAccessService).validateUserGroupsBlockStatus(any());

        final UserContext actualUserContext = userDetailsService.loadUserBySAML(mockCredential);

        Assert.assertEquals(expectedUserContext.getUsername(), actualUserContext.getUsername());
    }

    @Test
    public void shouldThrowAuthorizationExceptionForUserFromBlockedGroup() {
        blockOneGroupForCurrentUser();

        assertThrows(LockedException.class, () -> userDetailsService.loadUserBySAML(mockCredential));
    }

    @Test
    public void shouldThrowAuthorizationExceptionForUserWithBlockedRole() {
        final Role role = new Role();
        role.setName(SAML_ATTRIBUTE_1);
        role.setPredefined(false);
        user.setRoles(Collections.singletonList(role));
        blockOneGroupForCurrentUser();

        assertThrows(LockedException.class, () -> userDetailsService.loadUserBySAML(mockCredential));
    }

    private void mockUserDoesNotExistSituation() {
        when(mockUserManager.loadUserByName(anyString())).thenReturn(null);
        when(mockUserManager.createUser(anyString(),
                anyListOf(Long.class), anyListOf(String.class),
                anyMapOf(String.class, String.class), any()))
                .thenReturn(user);
    }

    private void setBlockedAttributeValue(final String value) {
        ReflectionTestUtils.setField(userDetailsService, "blockedAttribute", value);
        ReflectionTestUtils.setField(userDetailsService, "blockedAttributeTrueValue",
                                     SAML_ATTRIBUTE_BLOCKED_USER_VALUE_4);
        user.setUserName(USER_NAME);
        when(mockUserManager.loadUserByName(eq(USER_NAME))).thenReturn(user);
        when(mockCredential.getAttributeAsString(value)).thenReturn(value);
    }

    private void blockOneGroupForCurrentUser() {
        ReflectionTestUtils.setField(spyAccessService, "userManager", mockUserManager);
        user.setUserName(USER_NAME);
        when(mockUserManager.loadUserByName(anyString())).thenReturn(user);
        final GroupStatus blockedGroupStatus = new GroupStatus(SAML_ATTRIBUTE_1, true);
        doReturn(Collections.singletonList(blockedGroupStatus)).when(mockUserManager).loadGroupBlockingStatus(groups);
    }

    private void blockCurrentUser() {
        user.setUserName(USER_NAME);
        user.setBlocked(true);
        when(mockUserManager.loadUserByName(anyString())).thenReturn(user);
    }

    private void setValidGroupsStatusForUser() {
        user.setUserName(USER_NAME);
        when(mockUserManager.loadUserByName(anyString())).thenReturn(user);
        final GroupStatus validGroupStatus = new GroupStatus(SAML_ATTRIBUTE_1, false);
        when(mockUserManager.loadGroupBlockingStatus(groups)).thenReturn(Collections.singletonList(validGroupStatus));
    }

    private void setEmptyGroupsStatusListForUser() {
        user.setUserName(USER_NAME);
        when(mockUserManager.loadUserByName(anyString())).thenReturn(user);
        when(mockUserManager.loadGroupBlockingStatus(groups)).thenReturn(Collections.emptyList());
    }

    private void switchRegisterStrategyTo(final SamlUserRegisterStrategy samlUserRegisterStrategy) {
        ReflectionTestUtils.setField(userDetailsService, "autoCreateUsers", samlUserRegisterStrategy);
    }

    private void setAuthorities() {
        ReflectionTestUtils.setField(userDetailsService, "authorities", Collections.singletonList(USER_NAME));
    }

    private void setSamlAttributes() {
        ReflectionTestUtils.setField(userDetailsService, "samlAttributes",
                new HashSet<>(Arrays.asList(ATTRIBUTES_KEY_1 + "=" + TEST_STRING,
                                            ATTRIBUTES_KEY_2 + "=" + TEST_STRING)));
    }

    private Map<String, String> initAttributes() {
        final Map<String, String> attributes = new HashMap<>();
        attributes.put(ATTRIBUTES_KEY_1, SAML_ATTRIBUTES_STRING);
        attributes.put(ATTRIBUTES_KEY_2, SAML_ATTRIBUTES_STRING);
        return attributes;
    }

    private void assertUserNameAndGroups(final UserContext actualUserContext) {
        Assert.assertEquals(expectedUserContext.getUsername(), actualUserContext.getUsername());
        Assert.assertEquals(expectedUserContext.getGroups(), actualUserContext.getGroups());
    }
}
