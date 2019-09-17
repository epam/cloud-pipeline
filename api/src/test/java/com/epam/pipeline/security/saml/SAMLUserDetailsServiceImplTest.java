/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.app.TestApplicationWithAclSecurity;
import com.epam.pipeline.controller.vo.PermissionGrantVO;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.GroupStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserContext;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensaml.saml2.core.NameID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.saml.SAMLCredential;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@DirtiesContext
@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
public class SAMLUserDetailsServiceImplTest extends AbstractSpringTest {

    private static final String USER_NAME = "TEST_USER";
    private static final String OLD_USER_NAME = "user_name";
    private static final String SAML_ATTRIBUTE_1 = "ATTR_1";
    private static final String SAML_ATTRIBUTE_2 = "ATTR_2";
    private static final String SAML_ATTRIBUTES_STRING = "ATTR_3";
    private static final String ATTRIBUTES_KEY_1 = "email";
    private static final String ATTRIBUTES_KEY_2 = "user";
    private static final String TEST_FOLDER = "test-folder";
    private PipelineUser user = new PipelineUser();
    private Map<String, String> expectedAttributes = new HashMap<>();
    private List<String> groups = Stream.of(SAML_ATTRIBUTE_1, SAML_ATTRIBUTE_2).collect(Collectors.toList());
    private UserContext expectedUserContext = new UserContext(1L, USER_NAME.toUpperCase());

    @InjectMocks
    @Autowired
    private SAMLUserDetailsServiceImpl userDetailsService;
    @Autowired
    private GrantPermissionManager grantPermissionManager;
    @Autowired
    private FolderManager folderManager;

    @Mock
    private SAMLCredential credential;

    @Mock
    private NameID nameID;

    @MockBean
    private UserManager userManager;

    @Before
    public void setUp() {
        expectedAttributes = initAttributes();
        user.setAttributes(expectedAttributes);
        user.setGroups(groups);
        expectedUserContext.setGroups(groups);

        MockitoAnnotations.initMocks(this);
        Mockito.when(nameID.getValue()).thenReturn(USER_NAME);
        Mockito.when(credential.getNameID()).thenReturn(nameID);
        String[] mockAttributesArray = {SAML_ATTRIBUTE_1, SAML_ATTRIBUTE_2};
        Mockito.when(credential.getAttributeAsStringArray(Matchers.anyString())).thenReturn(mockAttributesArray);
        Mockito.when(credential.getAttributeAsString(Matchers.anyString())).thenReturn(SAML_ATTRIBUTES_STRING);
    }

    @Test
    public void testLoadUserBySAMLWithCreation() {
        switchToAutoMode();

        user.setUserName(USER_NAME);

        mockUserDoesNotExistSituation();

        UserContext actualUserContext = userDetailsService.loadUserBySAML(credential);
        Assert.assertEquals(expectedUserContext.getUsername(), actualUserContext.getUsername());
        Assert.assertEquals(expectedUserContext.getGroups(), actualUserContext.getGroups());
    }

    @Test
    public void testLoadUserBySAMLWithExistingUser() {
        switchToAutoMode();

        user.setUserName(OLD_USER_NAME);
        user.setGroups(Stream.of(SAML_ATTRIBUTE_1).collect(Collectors.toList()));

        Mockito.when(userManager.loadUserByName(Matchers.anyString())).thenReturn(user);
        user.setGroups(Stream.of(SAML_ATTRIBUTE_1, SAML_ATTRIBUTE_2).collect(Collectors.toList()));
        Mockito.when(userManager.updateUserSAMLInfo(Matchers.anyLong(), Matchers.anyString(),
                                                    Matchers.anyListOf(Long.class), Matchers.anyListOf(String.class),
                                                    Matchers.anyMapOf(String.class, String.class))).thenReturn(user);

        UserContext actualUserContext = userDetailsService.loadUserBySAML(credential);
        Assert.assertEquals(expectedUserContext.getUsername(), actualUserContext.getUsername());
        Assert.assertEquals(expectedUserContext.getGroups(), actualUserContext.getGroups());
    }

    @Test
    public void testReadAuthorities() {
        List<String> actualAuthorities = userDetailsService.readAuthorities(credential);
        Assert.assertTrue(CollectionUtils.isEqualCollection(groups, actualAuthorities));
    }

    @Test
    public void testReadAttributes() {
        Map<String, String> readAttributes = userDetailsService.readAttributes(credential);
        Assert.assertTrue(CollectionUtils.isEqualCollection(expectedAttributes.entrySet(), readAttributes.entrySet()));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = USER_NAME)
    public void shouldRegisterUserIfGroupPresentsAndEntityExistsWithExplicitGroupMode() {
        switchToExplicitGroupMode();

        mockUserDoesNotExistSituation();

        final Folder folder = initFolder();
        initFolderPermissions(folder);

        final UserContext actualUserContext = userDetailsService.loadUserBySAML(credential);
        Assert.assertEquals(expectedUserContext.getUsername(), actualUserContext.getUsername());
        Assert.assertEquals(expectedUserContext.getGroups(), actualUserContext.getGroups());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = USER_NAME)
    public void shouldAuthorizeRegisteredUserIfHisGroupsHaveValidGroupStatus() {
        setValidGroupsStatusForUser();
        final UserContext actualUserContext = userDetailsService.loadUserBySAML(credential);
        Assert.assertEquals(expectedUserContext.getUsername(), actualUserContext.getUsername());
        Assert.assertEquals(expectedUserContext.getGroups(), actualUserContext.getGroups());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = USER_NAME)
    public void shouldAuthorizeRegisteredUserIfHisGroupsAreNotAtGroupStatus() {
        setEmptyGroupsStatusListForUser();
        final UserContext actualUserContext = userDetailsService.loadUserBySAML(credential);
        Assert.assertEquals(expectedUserContext.getUsername(), actualUserContext.getUsername());
        Assert.assertEquals(expectedUserContext.getGroups(), actualUserContext.getGroups());
    }

    @Test(expected = UsernameNotFoundException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = USER_NAME)
    public void shouldNotRegisterUserIfGroupPresentsButNoEntityReferWithExplicitGroupMode() {
        switchToExplicitGroupMode();

        mockUserDoesNotExistSituation();

        final Folder folder = initFolder();
        initFolderPermissions(folder);
        folderManager.delete(folder.getId());

        userDetailsService.loadUserBySAML(credential);
    }

    @Test(expected = UsernameNotFoundException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = USER_NAME)
    public void shouldNotRegisterUserIfGroupNotPresentsWithExplicitGroupMode() {
        switchToExplicitGroupMode();

        mockUserDoesNotExistSituation();
        final String[] mockAttributesArray = {"unknown"};
        Mockito.when(credential.getAttributeAsStringArray(Matchers.anyString())).thenReturn(mockAttributesArray);

        final Folder folder = initFolder();
        initFolderPermissions(folder);

        userDetailsService.loadUserBySAML(credential);
    }

    @Test(expected = UsernameNotFoundException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = USER_NAME)
    public void shouldNotRegisterUserWithExplicitMode() {
        switchToExplicitMode();

        mockUserDoesNotExistSituation();

        userDetailsService.loadUserBySAML(credential);
    }

    private void mockUserDoesNotExistSituation() {
        Mockito.when(userManager.loadUserByName(Matchers.anyString())).thenReturn(null);
        Mockito.when(userManager.createUser(Matchers.anyString(),
                                            Matchers.anyListOf(Long.class), Matchers.anyListOf(String.class),
                                            Matchers.anyMapOf(String.class, String.class), Matchers.any()))
               .thenReturn(user);
    }

    @Test(expected = LockedException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void shouldThrowAuthorizationExceptionForBlockedUser() {
        blockCurrentUser();
        userDetailsService.loadUserBySAML(credential);
    }

    @Test(expected = LockedException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void shouldThrowAuthorizationExceptionForUserFromBlockedGroup() {
        blockOneGroupForCurrentUser();
        userDetailsService.loadUserBySAML(credential);
    }

    private void blockOneGroupForCurrentUser() {
        user.setUserName(USER_NAME);
        Mockito.when(userManager.loadUserByName(Matchers.anyString())).thenReturn(user);
        final GroupStatus blockedGroupStatus = new GroupStatus(SAML_ATTRIBUTE_1, true);
        Mockito.when(userManager.loadGroupBlockingStatus(groups))
                .thenReturn(Collections.singletonList(blockedGroupStatus));
    }

    private void blockCurrentUser() {
        user.setUserName(USER_NAME);
        user.setBlocked(true);
        Mockito.when(userManager.loadUserByName(Matchers.anyString())).thenReturn(user);
    }

    private void setValidGroupsStatusForUser() {
        user.setUserName(USER_NAME);
        Mockito.when(userManager.loadUserByName(Matchers.anyString())).thenReturn(user);
        final GroupStatus validGroupStatus = new GroupStatus(SAML_ATTRIBUTE_1, false);
        Mockito.when(userManager.loadGroupBlockingStatus(groups))
                .thenReturn(Collections.singletonList(validGroupStatus));
    }

    private void setEmptyGroupsStatusListForUser() {
        user.setUserName(USER_NAME);
        Mockito.when(userManager.loadUserByName(Matchers.anyString())).thenReturn(user);
        Mockito.when(userManager.loadGroupBlockingStatus(groups))
                .thenReturn(Collections.emptyList());
    }

    private void switchToExplicitGroupMode() {
        ReflectionTestUtils.setField(userDetailsService, "autoCreateUsers",
                                     SamlUserRegisterStrategy.EXPLICIT_GROUP);
    }

    private void switchToExplicitMode() {
        ReflectionTestUtils.setField(userDetailsService, "autoCreateUsers", SamlUserRegisterStrategy.EXPLICIT);
    }

    private void switchToAutoMode() {
        ReflectionTestUtils.setField(userDetailsService, "autoCreateUsers", SamlUserRegisterStrategy.AUTO);
    }

    private Folder initFolder() {
        final Folder folder = new Folder();
        folder.setName(TEST_FOLDER);
        folderManager.create(folder);
        return folder;
    }

    private PermissionGrantVO initFolderPermissions(final Folder folder) {
        final PermissionGrantVO folderPermissions = new PermissionGrantVO();
        folderPermissions.setAclClass(AclClass.FOLDER);
        folderPermissions.setId(folder.getId());
        folderPermissions.setUserName(SAML_ATTRIBUTE_1);
        folderPermissions.setPrincipal(false);
        folderPermissions.setMask(1);
        grantPermissionManager.setPermissions(folderPermissions);
        return folderPermissions;
    }

    private Map<String, String> initAttributes() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(ATTRIBUTES_KEY_1, SAML_ATTRIBUTES_STRING);
        attributes.put(ATTRIBUTES_KEY_2, SAML_ATTRIBUTES_STRING);
        return attributes;
    }
}
