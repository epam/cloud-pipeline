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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.epam.pipeline.dao.contextual.ContextualPreferenceDao;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

@SuppressWarnings("PMD.TooManyStaticImports")
public abstract class AbstractDaoContextualPreferenceHandlerTest extends AbstractContextualPreferenceHandlerTest {

    final ContextualPreferenceDao contextualPreferenceDao = mock(ContextualPreferenceDao.class);

    @Test
    public void searchShouldDelegateExecutionToTheNextHandlerIfNoResourceSuitsCurrentHandler() {
        final List<ContextualPreferenceExternalResource> resources = Collections.singletonList(notSuitableResource);
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, resource);
        when(nextHandler.search(eq(SINGLE_NAME), eq(resources))).thenReturn(Optional.of(preference));

        final Optional<ContextualPreference> searchedPreference = handler().search(SINGLE_NAME, resources);

        assertTrue(searchedPreference.isPresent());
        assertThat(searchedPreference.get(), is(preference));
        verify(nextHandler).search(eq(SINGLE_NAME), eq(resources));
    }

    @Test
    public void searchShouldDelegateExecutionToTheNextHandlerIfThereIsSuitableResourceButPreferenceDoesNotExists() {
        final List<ContextualPreferenceExternalResource> resources = Arrays.asList(resource, notSuitableResource);
        when(contextualPreferenceDao.load(eq(NAME), eq(resource))).thenReturn(Optional.empty());
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, resource);
        when(nextHandler.search(eq(SINGLE_NAME), eq(resources))).thenReturn(Optional.of(preference));

        final Optional<ContextualPreference> searchedPreference = handler().search(SINGLE_NAME, resources);

        assertTrue(searchedPreference.isPresent());
        assertThat(searchedPreference.get(), is(preference));
        verify(contextualPreferenceDao).load(eq(NAME), eq(resource));
        verify(nextHandler).search(eq(SINGLE_NAME), eq(resources));
    }

    @Test
    public void searchShouldReturnPreferenceIfThereIsSuitableResourceAndPreferenceExists() {
        final List<ContextualPreferenceExternalResource> resources = Arrays.asList(resource, notSuitableResource);
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, resource);
        when(contextualPreferenceDao.load(eq(NAME), eq(resource))).thenReturn(Optional.of(preference));

        final Optional<ContextualPreference> searchedPreference = handler().search(SINGLE_NAME, resources);

        assertTrue(searchedPreference.isPresent());
        assertThat(searchedPreference.get(), is(preference));
        verify(contextualPreferenceDao).load(eq(NAME), eq(resource));
    }

    @Test
    public void searchShouldLoadPreferenceByAnotherNameIfThereIsNoPreferenceWithTheFirstName() {
        final List<ContextualPreferenceExternalResource> resources = Collections.singletonList(resource);
        final ContextualPreference anotherPreference = new ContextualPreference(ANOTHER_NAME, VALUE, resource);
        when(contextualPreferenceDao.load(eq(NAME), eq(resource))).thenReturn(Optional.empty());
        when(contextualPreferenceDao.load(eq(ANOTHER_NAME), eq(resource))).thenReturn(Optional.of(anotherPreference));

        final Optional<ContextualPreference> searchedPreference = handler().search(SEVERAL_NAMES, resources);

        assertTrue(searchedPreference.isPresent());
        assertThat(searchedPreference.get(), is(anotherPreference));
        verify(contextualPreferenceDao).load(eq(NAME), eq(resource));
        verify(contextualPreferenceDao).load(eq(ANOTHER_NAME), eq(resource));
    }

    @Test
    public void searchShouldDelegateExecutionToTheNextHandlerIfNoneOfThePreferencesExist() {
        final List<ContextualPreferenceExternalResource> resources = Collections.singletonList(resource);
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, resource);
        when(contextualPreferenceDao.load(eq(NAME), eq(resource))).thenReturn(Optional.empty());
        when(contextualPreferenceDao.load(eq(ANOTHER_NAME), eq(resource))).thenReturn(Optional.empty());
        when(nextHandler.search(eq(SEVERAL_NAMES), eq(resources))).thenReturn(Optional.of(preference));

        final Optional<ContextualPreference> searchedPreference = handler().search(SEVERAL_NAMES, resources);

        assertTrue(searchedPreference.isPresent());
        assertThat(searchedPreference.get(), is(preference));
        verify(contextualPreferenceDao).load(eq(NAME), eq(resource));
        verify(contextualPreferenceDao).load(eq(ANOTHER_NAME), eq(resource));
        verify(nextHandler).search(eq(SEVERAL_NAMES), eq(resources));
    }
}
