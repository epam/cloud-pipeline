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

import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserAccessService;
import com.epam.pipeline.security.UserContext;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.saml2.provider.service.authentication.DefaultSaml2AuthenticatedPrincipal;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static com.epam.pipeline.util.CustomAssertions.notInvoked;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SAMLUserDetailsServiceTest {

    private static final String SAML_USER_NAME = "Awesome";
    private static final String USER_NAME = "AWESOME";
    private static final List<String> GROUPS = Arrays.asList("ROLE_ADMIN", "ROLE_USER");
    private static final List<Object> SAML_GROUPS = Arrays.asList("role_admin", "role_user");

    private final List<String> authoritiesAttributes = List.of("groups");
    private final Set<String> samlAttributes = new HashSet<>(Arrays.asList("Email=email", "FirstName=firstName"));
    private final String blockedAttribute = "blocked";
    private final String blockedAttributeTrueValue = "true";

    private final UserManager userManager = mock(UserManager.class);
    private final UserAccessService accessService = mock(UserAccessService.class);

    private final SAMLUserDetailsService userDetailsService = new SAMLUserDetailsService(
            authoritiesAttributes,
            samlAttributes,
            blockedAttribute,
            blockedAttributeTrueValue,
            userManager,
            accessService);

    @Test
    void shouldLoadUserBySAML() {
        final var attributes = buildSamlAttributes("false");
        final var credentials = new DefaultSaml2AuthenticatedPrincipal(SAML_USER_NAME, attributes);
        final var expectedContext = buildExpectedContext();
        final var expectedAttributes = buildExpectedAttributes();

        doReturn(expectedContext).when(accessService)
                .parseUser(USER_NAME, GROUPS, expectedAttributes);
        doNothing().when(accessService).validateUserGroupsBlockStatus(any());

        assertUserNameAndGroups(userDetailsService.loadUserBySAML(credentials), expectedContext);
        verify(accessService).validateUserGroupsBlockStatus(any());
        notInvoked(accessService).throwUserIsBlocked(any());
    }

    @Test
    void shouldLoadUserBySAMLWhenNoSAMLAttributesProvided() {
        final var attributes = Collections.singletonMap("groups", SAML_GROUPS);
        final var credentials = new DefaultSaml2AuthenticatedPrincipal(SAML_USER_NAME, attributes);
        final var expectedContext = buildExpectedContext();

        doReturn(expectedContext).when(accessService)
                .parseUser(USER_NAME, GROUPS, Collections.emptyMap());
        doNothing().when(accessService).validateUserGroupsBlockStatus(any());

        assertUserNameAndGroups(userDetailsService.loadUserBySAML(credentials), expectedContext);
        verify(accessService).validateUserGroupsBlockStatus(any());
        notInvoked(accessService).throwUserIsBlocked(any());
    }

    @Test
    void shouldLoadUserBySAMLWhenSAMLAttributesNotConfigured() {
        final var userDetailsService = new SAMLUserDetailsService(
                authoritiesAttributes,
                null,
                blockedAttribute,
                blockedAttributeTrueValue,
                userManager,
                accessService);

        final var attributes = buildSamlAttributes("false");
        final var credentials = new DefaultSaml2AuthenticatedPrincipal(SAML_USER_NAME, attributes);
        final var expectedContext = buildExpectedContext();

        doReturn(expectedContext).when(accessService)
                .parseUser(USER_NAME, GROUPS, Collections.emptyMap());
        doNothing().when(accessService).validateUserGroupsBlockStatus(any());

        assertUserNameAndGroups(userDetailsService.loadUserBySAML(credentials), expectedContext);
        verify(accessService).validateUserGroupsBlockStatus(any());
        notInvoked(accessService).throwUserIsBlocked(any());
    }

    @Test
    void shouldThrowIfUserHasBlockedAttributeAndBlocked() {
        final Set<String> samlAttributesWithBlocked = new HashSet<>(samlAttributes);
        samlAttributesWithBlocked.add("Blocked=blocked");
        final var userDetailsService = new SAMLUserDetailsService(
            authoritiesAttributes,
            samlAttributesWithBlocked,
            blockedAttribute,
            blockedAttributeTrueValue,
            userManager,
            accessService);
        final var attributes = buildSamlAttributes("true");
        final var credentials = new DefaultSaml2AuthenticatedPrincipal(SAML_USER_NAME, attributes);
        final var expectedContext = buildExpectedContext();
        final var expectedAttributes = buildExpectedAttributes();
        expectedAttributes.put("Blocked", "true");

        doReturn(expectedContext).when(accessService)
                .parseUser(USER_NAME, GROUPS, expectedAttributes);
        doNothing().when(accessService).validateUserGroupsBlockStatus(any());
        doThrow(LockedException.class).when(accessService).throwUserIsBlocked(USER_NAME);

        assertThrows(LockedException.class, () -> userDetailsService.loadUserBySAML(credentials));
        verify(accessService).validateUserGroupsBlockStatus(any());
    }

    @Test
    void shouldLoadSAMLIfUserHasBlockedAttributeAndNotBlocked() {
        final Set<String> samlAttributesWithBlocked = new HashSet<>(samlAttributes);
        samlAttributesWithBlocked.add("Blocked=blocked");
        final var userDetailsService = new SAMLUserDetailsService(
                authoritiesAttributes,
                samlAttributesWithBlocked,
                blockedAttribute,
                blockedAttributeTrueValue,
                userManager,
                accessService);
        final var attributes = buildSamlAttributes("false");
        final var credentials = new DefaultSaml2AuthenticatedPrincipal(SAML_USER_NAME, attributes);
        final var expectedContext = buildExpectedContext();
        final var expectedAttributes = buildExpectedAttributes();
        expectedAttributes.put("Blocked", "false");

        doReturn(expectedContext).when(accessService)
                .parseUser(USER_NAME, GROUPS, expectedAttributes);
        doNothing().when(accessService).validateUserGroupsBlockStatus(any());
        doThrow(LockedException.class).when(accessService).throwUserIsBlocked(USER_NAME);

        assertUserNameAndGroups(userDetailsService.loadUserBySAML(credentials), expectedContext);
        verify(accessService).validateUserGroupsBlockStatus(any());
        notInvoked(accessService).throwUserIsBlocked(any());
    }

    private void assertUserNameAndGroups(final UserContext actualUserContext, final UserContext expectedUserContext) {
        assertEquals(expectedUserContext.getUsername(), actualUserContext.getUsername());
        assertEquals(expectedUserContext.getGroups(), actualUserContext.getGroups());
    }

    private UserContext buildExpectedContext() {
        final var userContext = new UserContext(1L, SAML_USER_NAME.toUpperCase());
        userContext.setGroups(GROUPS);
        return userContext;
    }

    private Map<String, String> buildExpectedAttributes() {
        final HashMap<String, String> attributes = new HashMap<>();
        attributes.put("Email", "test@email.com");
        attributes.put("FirstName", "Awesome");
        return attributes;
    }

    private Map<String, List<Object>> buildSamlAttributes(final String blocked) {
        final Map<String, List<Object>> attributes = new HashMap<>();
        attributes.put("groups", SAML_GROUPS);
        attributes.put("email", Collections.singletonList("test@email.com"));
        attributes.put("firstName", Collections.singletonList("Awesome"));
        attributes.put("blocked", Collections.singletonList(blocked));
        return attributes;
    }
}
