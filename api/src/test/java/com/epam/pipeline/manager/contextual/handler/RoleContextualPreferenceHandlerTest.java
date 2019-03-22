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

package com.epam.pipeline.manager.contextual.handler;


import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.epam.pipeline.dao.user.RoleDao;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.user.Role;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

@SuppressWarnings("PMD.TooManyStaticImports")
public class RoleContextualPreferenceHandlerTest extends AbstractDaoContextualPreferenceHandlerTest {

    private static final Role ROLE1 = new Role(1L, "firstRole");
    private static final Role ROLE2 = new Role(2L, "secondRole");
    private final ContextualPreferenceExternalResource role1Resource =
            new ContextualPreferenceExternalResource(level(), ROLE1.getId().toString());
    private final ContextualPreferenceExternalResource role2Resource =
            new ContextualPreferenceExternalResource(level(), ROLE2.getId().toString());

    private final RoleDao roleDao = mock(RoleDao.class);
    private final ContextualPreferenceReducer reducer = mock(ContextualPreferenceReducer.class);

    @Override
    public ContextualPreferenceHandler handler() {
        return new RoleContextualPreferenceHandler(roleDao, contextualPreferenceDao, nextHandler, reducer);
    }

    @Override
    public ContextualPreferenceHandler lastHandler() {
        return new RoleContextualPreferenceHandler(roleDao, contextualPreferenceDao, reducer);
    }

    @Override
    public ContextualPreferenceLevel level() {
        return ContextualPreferenceLevel.ROLE;
    }

    @Override
    @Test
    public void searchShouldReturnPreferenceIfThereIsSuitableResourceAndPreferenceExists() {
        when(reducer.reduce(any()))
                .thenAnswer(invocation -> Optional.of(invocation.getArgumentAt(0, List.class).get(0)));
        super.searchShouldReturnPreferenceIfThereIsSuitableResourceAndPreferenceExists();
    }

    @Override
    @Test
    public void searchShouldLoadPreferenceByAnotherNameIfThereIsNoPreferenceWithTheFirstName() {
        when(reducer.reduce(any()))
                .thenAnswer(invocation -> Optional.of(invocation.getArgumentAt(0, List.class).get(0)));
        super.searchShouldLoadPreferenceByAnotherNameIfThereIsNoPreferenceWithTheFirstName();
    }

    @Test
    public void isValidShouldReturnFalseIfRoleDoesNotExist() {
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, role1Resource);
        when(roleDao.loadRole(eq(Long.valueOf(role1Resource.getResourceId())))).thenReturn(Optional.empty());

        assertFalse(handler().isValid(preference));
    }

    @Test
    public void isValidShouldReturnTrueIfRoleExists() {
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, role1Resource);
        when(roleDao.loadRole(eq(Long.valueOf(role1Resource.getResourceId())))).thenReturn(Optional.of(new Role()));

        assertTrue(handler().isValid(preference));
    }

    @Test
    public void searchShouldReturnReducedPreferenceIfSeveralRolePreferencesExist() {
        final ContextualPreference preference1 = new ContextualPreference(NAME, VALUE, role1Resource);
        final ContextualPreference preference2 = new ContextualPreference(NAME, VALUE, role2Resource);
        final List<ContextualPreference> preferences = Arrays.asList(preference1, preference2);
        final ContextualPreference reducedPreference = new ContextualPreference(NAME, VALUE);
        final List<ContextualPreferenceExternalResource> resources = Arrays.asList(role1Resource, role2Resource);
        when(contextualPreferenceDao.load(eq(preference1.getName()), eq(role1Resource)))
                .thenReturn(Optional.of(preference1));
        when(contextualPreferenceDao.load(eq(preference2.getName()), eq(role2Resource)))
                .thenReturn(Optional.of(preference2));
        when(reducer.reduce(eq(preferences))).thenReturn(Optional.of(reducedPreference));

        final Optional<ContextualPreference> searchedPreference = handler().search(SINGLE_NAME, resources);

        assertTrue(searchedPreference.isPresent());
        assertThat(searchedPreference.get(), is(reducedPreference));
        verify(reducer).reduce(eq(preferences));
    }

    @Test
    public void searchShouldDelegateExecutionToTheNextHandlerIfPreferencesReducingReturnsEmptyOptional() {
        final ContextualPreference preference1 = new ContextualPreference(NAME, VALUE, role1Resource);
        final ContextualPreference preference2 = new ContextualPreference(NAME, VALUE, role2Resource);
        final List<ContextualPreference> preferences = Arrays.asList(preference1, preference2);
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, role1Resource);
        final List<ContextualPreferenceExternalResource> resources = Arrays.asList(role1Resource, role2Resource);
        when(contextualPreferenceDao.load(eq(preference1.getName()), eq(role1Resource)))
                .thenReturn(Optional.of(preference1));
        when(contextualPreferenceDao.load(eq(preference2.getName()), eq(role2Resource)))
                .thenReturn(Optional.of(preference2));
        when(reducer.reduce(eq(preferences))).thenReturn(Optional.empty());
        when(nextHandler.search(eq(SINGLE_NAME), eq(resources))).thenReturn(Optional.of(preference));

        final Optional<ContextualPreference> searchedPreference = handler().search(SINGLE_NAME, resources);

        assertTrue(searchedPreference.isPresent());
        assertThat(searchedPreference.get(), is(preference));
        verify(reducer).reduce(eq(preferences));
        verify(nextHandler).search(eq(SINGLE_NAME), eq(resources));
    }

    @Test
    public void searchShouldReturnReducedPreferenceFoundByAnotherNameIfThereIsNoPreferencesWithTheFirstName() {
        final ContextualPreference preference1 = new ContextualPreference(NAME, VALUE, role1Resource);
        final ContextualPreference preference2 = new ContextualPreference(NAME, VALUE, role2Resource);
        final List<ContextualPreference> preferences = Arrays.asList(preference1, preference2);
        final ContextualPreference reducedPreference = new ContextualPreference(NAME, VALUE, preference1.getResource());
        final List<ContextualPreferenceExternalResource> resources = Arrays.asList(role1Resource, role2Resource);
        when(contextualPreferenceDao.load(eq(NAME), eq(role1Resource))).thenReturn(Optional.empty());
        when(contextualPreferenceDao.load(eq(NAME), eq(role2Resource))).thenReturn(Optional.empty());
        when(contextualPreferenceDao.load(eq(ANOTHER_NAME), eq(role1Resource))).thenReturn(Optional.of(preference1));
        when(contextualPreferenceDao.load(eq(ANOTHER_NAME), eq(role2Resource))).thenReturn(Optional.of(preference2));
        when(reducer.reduce(eq(preferences))).thenReturn(Optional.of(reducedPreference));

        final Optional<ContextualPreference> searchedPreference = handler().search(SEVERAL_NAMES, resources);

        assertTrue(searchedPreference.isPresent());
        assertThat(searchedPreference.get(), is(reducedPreference));
        verify(reducer).reduce(eq(preferences));
        verify(contextualPreferenceDao).load(eq(NAME), eq(role1Resource));
        verify(contextualPreferenceDao).load(eq(NAME), eq(role2Resource));
        verify(contextualPreferenceDao).load(eq(ANOTHER_NAME), eq(role1Resource));
        verify(contextualPreferenceDao).load(eq(ANOTHER_NAME), eq(role2Resource));
    }

    @Test
    public void searchShouldLoadPreferencesBySingleNameEvenIfSeveralNamesAndSeveralRolesAreGiven() {
        final List<ContextualPreferenceExternalResource> resources = Arrays.asList(role1Resource, role2Resource);
        final ContextualPreference preference1 = new ContextualPreference(NAME, VALUE, role1Resource);
        final List<ContextualPreference> preferences = Collections.singletonList(preference1);
        when(contextualPreferenceDao.load(eq(NAME), eq(role1Resource))).thenReturn(Optional.of(preference1));
        when(contextualPreferenceDao.load(eq(NAME), eq(role2Resource))).thenReturn(Optional.empty());
        when(reducer.reduce(eq(preferences))).thenReturn(Optional.of(preference1));

        final Optional<ContextualPreference> searchedPreference = handler().search(SEVERAL_NAMES, resources);

        assertTrue(searchedPreference.isPresent());
        assertThat(searchedPreference.get(), is(preference1));
        verify(reducer).reduce(eq(preferences));
        verify(contextualPreferenceDao).load(eq(NAME), eq(role1Resource));
        verify(contextualPreferenceDao).load(eq(NAME), eq(role2Resource));
        verify(contextualPreferenceDao, times(0)).load(eq(ANOTHER_NAME), eq(role1Resource));
        verify(contextualPreferenceDao, times(0)).load(eq(ANOTHER_NAME), eq(role2Resource));
    }

    @Test
    public void searchShouldDelegateExecutionToTheNextHandlerIfNoneOfPreferencesExist() {
        final List<ContextualPreferenceExternalResource> resources = Arrays.asList(role1Resource, role2Resource);
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, role1Resource);
        when(contextualPreferenceDao.load(any(), any())).thenReturn(Optional.empty());
        when(nextHandler.search(eq(SEVERAL_NAMES), eq(resources))).thenReturn(Optional.of(preference));

        final Optional<ContextualPreference> searchedPreference = handler().search(SEVERAL_NAMES, resources);

        assertTrue(searchedPreference.isPresent());
        assertThat(searchedPreference.get(), is(preference));
        verify(contextualPreferenceDao).load(eq(NAME), eq(role1Resource));
        verify(contextualPreferenceDao).load(eq(NAME), eq(role2Resource));
        verify(contextualPreferenceDao).load(eq(ANOTHER_NAME), eq(role1Resource));
        verify(contextualPreferenceDao).load(eq(ANOTHER_NAME), eq(role2Resource));
    }
}
