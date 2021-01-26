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

package com.epam.pipeline.manager.contextual;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static com.epam.pipeline.util.CustomMatchers.isEmpty;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.ContextualPreferenceVO;
import com.epam.pipeline.dao.contextual.ContextualPreferenceDao;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.preference.PreferenceType;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.contextual.handler.ContextualPreferenceHandler;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserManager;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Test;

@SuppressWarnings("PMD.TooManyStaticImports")
public class ContextualPreferenceManagerTest {

    private static final String NAME = "name";
    private static final String ANOTHER_NAME = "anotherName";
    private static final List<String> NAMES = Arrays.asList(NAME, ANOTHER_NAME);
    private static final String VALUE = "value";
    private static final PreferenceType TYPE = PreferenceType.STRING;
    private static final PreferenceType ANOTHER_TYPE = PreferenceType.INTEGER;
    private static final ContextualPreferenceLevel LEVEL = ContextualPreferenceLevel.TOOL;
    private static final String TOOL_ID = "toolId";
    private static final String ANOTHER_TOOL_ID = "anotherToolId";
    private static final Role ROLE_1 = new Role(1L, "role1");
    private static final Role ROLE_2 = new Role(2L, "role2");
    private static final String USER_NAME = "userName";
    private static final PipelineUser USER = new PipelineUser(1L, null, Arrays.asList(ROLE_1, ROLE_2),
            Collections.emptyList(), false, false, null, null, null, Collections.emptyMap(), Collections.emptyList(), null);
    private static final PipelineUser USER_WITHOUT_ROLES = new PipelineUser(USER.getId(), null, null,
            Collections.emptyList(), false, false, null, null, null, Collections.emptyMap(), Collections.emptyList(), null);
    private static final PipelineUser USER_WITHOUT_ID = new PipelineUser(null, USER_NAME, Collections.emptyList(),
            Collections.emptyList(), false, false, null, null, null, Collections.emptyMap(), Collections.emptyList(), null);

    private final ContextualPreferenceExternalResource toolResource =
            new ContextualPreferenceExternalResource(LEVEL, TOOL_ID);
    private final ContextualPreferenceDao contextualPreferenceDao = mock(ContextualPreferenceDao.class);
    private final ContextualPreferenceHandler contextualPreferenceHandler = mock(ContextualPreferenceHandler.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final AuthManager authManager = mock(AuthManager.class);
    private final UserManager userManager = mock(UserManager.class);
    private final ContextualPreferenceManager manager =
            new ContextualPreferenceManager(contextualPreferenceDao, contextualPreferenceHandler, authManager,
                    userManager, messageHelper);

    @Test
    public void upsertShouldFailIfPreferenceHasEmptyFields() {
        when(contextualPreferenceHandler.isValid(any())).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
            () -> manager.upsert(new ContextualPreferenceVO(null, VALUE, TYPE, toolResource)));
        assertThrows(IllegalArgumentException.class,
            () -> manager.upsert(new ContextualPreferenceVO(NAME, null, TYPE, toolResource)));
        assertThrows(IllegalArgumentException.class,
            () -> manager.upsert(new ContextualPreferenceVO(NAME, VALUE, null, toolResource)));
        assertThrows(IllegalArgumentException.class,
            () -> manager.upsert(new ContextualPreferenceVO(NAME, VALUE, TYPE,
                    new ContextualPreferenceExternalResource(null, TOOL_ID))));
        assertThrows(IllegalArgumentException.class,
            () -> manager.upsert(new ContextualPreferenceVO(NAME, VALUE, TYPE,
                    new ContextualPreferenceExternalResource(LEVEL, null))));
    }

    @Test
    public void upsertShouldFailIfPreferenceLevelIsSystem() {
        final ContextualPreferenceExternalResource resource = new ContextualPreferenceExternalResource(
                ContextualPreferenceLevel.SYSTEM, TOOL_ID);
        assertThrows(IllegalArgumentException.class,
            () -> manager.upsert(new ContextualPreferenceVO(NAME, VALUE, TYPE, resource)));
    }

    @Test
    public void upsertShouldFailIfPreferenceExternalResourceDoesNotExist() {
        final ContextualPreferenceVO preferenceVO = new ContextualPreferenceVO(NAME, VALUE, TYPE, toolResource);
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, TYPE, toolResource);

        when(contextualPreferenceHandler.isValid(eq(preference))).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> manager.upsert(preferenceVO));
    }

    @Test
    public void upsertShouldSavePreference() {
        final ContextualPreferenceVO preferenceVO = new ContextualPreferenceVO(NAME, VALUE, TYPE, toolResource);
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, TYPE, toolResource);
        when(contextualPreferenceHandler.isValid(eq(preference))).thenReturn(true);

        manager.upsert(preferenceVO);

        verify(contextualPreferenceDao).upsert(eq(preference));
    }

    @Test
    public void upsertShouldFailIfPreferenceTypeDiffersWithExistingPreferencesWithTheSameName() {
        final ContextualPreferenceExternalResource anotherResource = new ContextualPreferenceExternalResource(LEVEL,
                ANOTHER_TOOL_ID);
        final ContextualPreferenceVO preferenceVO = new ContextualPreferenceVO(NAME, VALUE, TYPE, toolResource);
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, TYPE, toolResource);
        when(contextualPreferenceHandler.isValid(eq(preference))).thenReturn(true);
        when(contextualPreferenceDao.load(eq(preference.getName()))).thenReturn(Collections.singletonList(
                new ContextualPreference(NAME, VALUE, ANOTHER_TYPE, anotherResource)));

        assertThrows(IllegalArgumentException.class, () -> manager.upsert(preferenceVO));
    }

    @Test
    public void upsertShouldFailIfPreferenceValueDoesNotSuitItsType() {
        final ContextualPreferenceVO preferenceVO = new ContextualPreferenceVO(NAME, VALUE, ANOTHER_TYPE,
                toolResource);
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, ANOTHER_TYPE, toolResource);
        when(contextualPreferenceHandler.isValid(eq(preference))).thenReturn(true);
        when(contextualPreferenceDao.load(eq(preference.getName()))).thenReturn(Collections.emptyList());

        assertThrows(IllegalArgumentException.class, () -> manager.upsert(preferenceVO));
    }

    @Test
    public void loadShouldFailIfThereIsNoSuchPreference() {
        when(contextualPreferenceDao.load(eq(NAME), eq(toolResource))).thenReturn(Optional.empty());

        assertThrows(() -> manager.load(NAME, toolResource));
        verify(contextualPreferenceDao).load(eq(NAME), eq(toolResource));
    }

    @Test
    public void loadShouldFailIfSomeOfParametersAreEmpty() {
        assertThrows(IllegalArgumentException.class, () -> manager.load(null, toolResource));
        assertThrows(IllegalArgumentException.class, () -> manager.load(NAME, null));
        assertThrows(IllegalArgumentException.class, () -> manager.load(NAME,
                new ContextualPreferenceExternalResource(null, TOOL_ID)));
        assertThrows(IllegalArgumentException.class, () -> manager.load(NAME,
                new ContextualPreferenceExternalResource(LEVEL, null)));
    }

    @Test
    public void loadShouldReturnPreferenceIfItExists() {
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, toolResource);
        when(contextualPreferenceDao.load(eq(NAME), eq(toolResource))).thenReturn(Optional.of(preference));

        final ContextualPreference loadedPreference = manager.load(NAME, toolResource);

        assertThat(loadedPreference, is(preference));
        verify(contextualPreferenceDao).load(eq(NAME), eq(toolResource));
    }

    @Test
    public void loadAllShouldReturnEmptyListIfThereAreNoPreferences() {
        when(contextualPreferenceDao.loadAll()).thenReturn(Collections.emptyList());

        assertThat(manager.loadAll(), isEmpty());
        verify(contextualPreferenceDao).loadAll();
    }

    @Test
    public void loadAllShouldReturnAllPreferences() {
        final ContextualPreference preference1 = new ContextualPreference(NAME, VALUE, toolResource);
        final ContextualPreference preference2 = new ContextualPreference(ANOTHER_NAME, VALUE, toolResource);
        when(contextualPreferenceDao.loadAll()).thenReturn(Arrays.asList(preference1, preference2));

        assertThat(manager.loadAll(), containsInAnyOrder(preference1, preference2));
        verify(contextualPreferenceDao).loadAll();
    }

    @Test
    public void deleteShouldFailIfThereIsNoSuchPreference() {
        assertThrows(() -> manager.delete(NAME, toolResource));
    }

    @Test
    public void deleteShouldFailIfSomeOfParametersAreEmpty() {
        assertThrows(IllegalArgumentException.class, () -> manager.delete(null, toolResource));
        assertThrows(IllegalArgumentException.class, () -> manager.delete(NAME, null));
        assertThrows(IllegalArgumentException.class, () -> manager.delete(NAME,
                new ContextualPreferenceExternalResource(null, TOOL_ID)));
        assertThrows(IllegalArgumentException.class, () -> manager.delete(NAME,
                new ContextualPreferenceExternalResource(LEVEL, null)));
    }

    @Test
    public void deleteShouldRemovePreferenceIfItExists() {
        when(contextualPreferenceDao.load(eq(NAME), eq(toolResource)))
                .thenReturn(Optional.of(new ContextualPreference(NAME, VALUE, toolResource)));

        manager.delete(NAME, toolResource);

        verify(contextualPreferenceDao).delete(eq(NAME), eq(toolResource));
    }

    @Test
    public void deleteShouldReturnDeletedPreference() {
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, toolResource);
        when(contextualPreferenceDao.load(eq(NAME), eq(toolResource))).thenReturn(Optional.of(preference));

        final ContextualPreference deletedPreference = manager.delete(NAME, toolResource);

        assertThat(deletedPreference, is(preference));
    }

    @Test
    public void searchShouldFailIfSomeOfParametersAreEmpty() {
        assertThrows(IllegalArgumentException.class, () -> manager.search(null, toolResource));
        assertThrows(IllegalArgumentException.class, () -> manager.search(NAMES,
                new ContextualPreferenceExternalResource(null, TOOL_ID)));
        assertThrows(IllegalArgumentException.class, () -> manager.search(NAMES,
                new ContextualPreferenceExternalResource(LEVEL, null)));
    }

    @Test
    public void searchShouldFailIfPreferenceLevelIsNotTool() {
        Arrays.stream(ContextualPreferenceLevel.values())
                .filter(level -> level != ContextualPreferenceLevel.TOOL)
                .forEach(level ->
                        assertThrows(IllegalArgumentException.class,
                            () -> manager.search(NAMES, new ContextualPreferenceExternalResource(level, TOOL_ID))));
    }

    @Test
    public void searchShouldSearchPreferenceWithResourceConstructedFromTheRequestedResourceIfItIsSpecified() {
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, toolResource);
        when(contextualPreferenceHandler.search(eq(NAMES), any())).thenReturn(Optional.of(preference));
        when(authManager.getCurrentUser()).thenReturn(USER_WITHOUT_ROLES);
        when(userManager.loadUserById(eq(USER_WITHOUT_ROLES.getId()))).thenReturn(USER);

        final ContextualPreference searchedPreference = manager.search(NAMES, toolResource);

        assertThat(searchedPreference, is(preference));
        verify(contextualPreferenceHandler).search(eq(NAMES), argThat(hasItem(toolResource)));
    }

    @Test
    public void searchShouldSearchPreferenceWithoutResourceConstructedFromTheRequestedResourceIfItIsNull() {
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE);
        when(contextualPreferenceHandler.search(eq(NAMES), any())).thenReturn(Optional.of(preference));
        when(authManager.getCurrentUser()).thenReturn(USER_WITHOUT_ROLES);
        when(userManager.loadUserById(eq(USER_WITHOUT_ROLES.getId()))).thenReturn(USER);

        final ContextualPreference searchedPreference = manager.search(NAMES, null);

        assertThat(searchedPreference, is(preference));
    }

    @Test
    public void searchShouldSearchPreferenceWithResourceConstructedFromCurrentUserByAuthorizedUserId() {
        final ContextualPreferenceExternalResource userResource =
                new ContextualPreferenceExternalResource(ContextualPreferenceLevel.USER, USER.getId().toString());
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, toolResource);
        when(contextualPreferenceHandler.search(eq(NAMES), any()))
                .thenReturn(Optional.of(preference));
        when(authManager.getCurrentUser()).thenReturn(USER_WITHOUT_ROLES);
        when(userManager.loadUserById(eq(USER_WITHOUT_ROLES.getId()))).thenReturn(USER);

        final ContextualPreference searchedPreference = manager.search(NAMES, toolResource);

        assertThat(searchedPreference, is(preference));
        verify(contextualPreferenceHandler).search(eq(NAMES), argThat(hasItem(userResource)));
    }

    @Test
    public void searchShouldSearchPreferenceWithResourceConstructedFromCurrentUserByAuthorizedUserName() {
        final ContextualPreferenceExternalResource userResource =
                new ContextualPreferenceExternalResource(ContextualPreferenceLevel.USER, USER.getId().toString());
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, toolResource);
        when(contextualPreferenceHandler.search(eq(NAMES), any()))
                .thenReturn(Optional.of(preference));
        when(authManager.getCurrentUser()).thenReturn(USER_WITHOUT_ID);
        when(userManager.loadUserByName(eq(USER_WITHOUT_ID.getUserName()))).thenReturn(USER);

        final ContextualPreference searchedPreference = manager.search(NAMES, toolResource);

        assertThat(searchedPreference, is(preference));
        verify(contextualPreferenceHandler).search(eq(NAMES), argThat(hasItem(userResource)));
    }

    @Test
    public void searchShouldSearchPreferenceWithoutUserResourceIfNoUserIsAuthorized() {
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, toolResource);
        when(contextualPreferenceHandler.search(eq(NAMES), any())).thenReturn(Optional.of(preference));
        when(authManager.getCurrentUser()).thenReturn(null);

        final ContextualPreference searchedPreference = manager.search(NAMES, toolResource);

        assertThat(searchedPreference, is(preference));
        verify(contextualPreferenceHandler).search(eq(NAMES), eq(Collections.singletonList(toolResource)));
    }

    @Test
    public void searchShouldSearchPreferenceWithResourcesConstructedFromCurrentUserRoles() {
        final ContextualPreferenceExternalResource firstRoleResource =
                new ContextualPreferenceExternalResource(ContextualPreferenceLevel.ROLE, ROLE_1.getId().toString());
        final ContextualPreferenceExternalResource secondRoleResource =
                new ContextualPreferenceExternalResource(ContextualPreferenceLevel.ROLE, ROLE_2.getId().toString());
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, toolResource);
        when(contextualPreferenceHandler.search(eq(NAMES), any()))
                .thenReturn(Optional.of(preference));
        when(authManager.getCurrentUser()).thenReturn(USER_WITHOUT_ROLES);
        when(userManager.loadUserById(eq(USER_WITHOUT_ROLES.getId()))).thenReturn(USER);

        final ContextualPreference searchedPreference = manager.search(NAMES, toolResource);

        assertThat(searchedPreference, is(preference));
        verify(contextualPreferenceHandler).search(eq(NAMES), argThat(hasItems(firstRoleResource, secondRoleResource)));
    }

    @Test
    public void searchShouldSearchPreferenceWithoutUserRolesIfUserDoesNotHaveOnes() {
        final ContextualPreferenceExternalResource userResource =
                new ContextualPreferenceExternalResource(ContextualPreferenceLevel.USER, USER.getId().toString());
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, toolResource);
        when(contextualPreferenceHandler.search(eq(NAMES), any()))
                .thenReturn(Optional.of(preference));
        when(authManager.getCurrentUser()).thenReturn(USER_WITHOUT_ROLES);
        when(userManager.loadUserById(eq(USER_WITHOUT_ROLES.getId()))).thenReturn(USER_WITHOUT_ROLES);

        final ContextualPreference searchedPreference = manager.search(NAMES, toolResource);

        assertThat(searchedPreference, is(preference));
        verify(contextualPreferenceHandler).search(eq(NAMES), argThat(hasItems(toolResource, userResource)));
    }

    private <T> BaseMatcher<List<T>> hasItem(final T item) {
        return new BaseMatcher<List<T>>() {

            private final Matcher<Iterable<? super T>> matcher = Matchers.hasItem(item);

            @Override
            public boolean matches(final Object item) {
                return matcher.matches(item);
            }

            @Override
            public void describeTo(final Description description) {
                matcher.describeTo(description);
            }
        };
    }

    private <T> BaseMatcher<List<T>> hasItems(final T... items) {
        return new BaseMatcher<List<T>>() {

            private final Matcher<Iterable<T>> matcher = Matchers.hasItems(items);

            @Override
            public boolean matches(final Object item) {
                return matcher.matches(item);
            }

            @Override
            public void describeTo(final Description description) {
                matcher.describeTo(description);
            }
        };
    }
}
