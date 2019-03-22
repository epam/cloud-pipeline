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
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserContext;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;
import org.opensaml.saml2.core.NameID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.saml.SAMLCredential;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SAMLUserDetailsServiceImplTest extends AbstractSpringTest {

    private static final String USER_NAME = "user_name";
    private static final String OLD_USER_NAME = "user_name";
    private static final String SAML_ATTRIBUTE_1 = "ATTR_1";
    private static final String SAML_ATTRIBUTE_2 = "ATTR_2";
    private static final String SAML_ATTRIBUTES_STRING = "ATTR_3";
    private static final String ATTRIBUTES_KEY_1 = "email";
    private static final String ATTRIBUTES_KEY_2 = "user";
    private PipelineUser user = new PipelineUser();
    private Map<String, String> expectedAttributes = new HashMap<>();
    private List<String> groups = Stream.of(SAML_ATTRIBUTE_1, SAML_ATTRIBUTE_2).collect(Collectors.toList());
    private UserContext expectedUserContext = new UserContext(1L, USER_NAME.toUpperCase());

    @InjectMocks
    @Autowired
    private SAMLUserDetailsServiceImpl userDetailsService;

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
        user.setUserName(USER_NAME);

        Mockito.when(userManager.loadUserByName(Matchers.anyString())).thenReturn(null);
        Mockito.when(userManager.createUser(Matchers.anyString(),
                Matchers.anyListOf(Long.class), Matchers.anyListOf(String.class),
                Matchers.anyMapOf(String.class, String.class), Matchers.any())).thenReturn(user);

        UserContext actualUserContext = userDetailsService.loadUserBySAML(credential);
        Assert.assertEquals(expectedUserContext.getUsername(), actualUserContext.getUsername());
        Assert.assertEquals(expectedUserContext.getGroups(), actualUserContext.getGroups());
    }

    @Test
    public void testLoadUserBySAMLWithExistingUser() {
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

    private Map<String, String> initAttributes() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(ATTRIBUTES_KEY_1, SAML_ATTRIBUTES_STRING);
        attributes.put(ATTRIBUTES_KEY_2, SAML_ATTRIBUTES_STRING);
        return attributes;
    }
}
