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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.preference.PreferenceType;
import com.epam.pipeline.manager.preference.PreferenceManager;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

@SuppressWarnings("PMD.TooManyStaticImports")
public class SystemPreferenceHandlerTest extends AbstractContextualPreferenceHandlerTest {

    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);

    @Override
    public ContextualPreferenceHandler handler() {
        return new SystemPreferenceHandler(preferenceManager, nextHandler);
    }

    @Override
    public ContextualPreferenceHandler lastHandler() {
        return new SystemPreferenceHandler(preferenceManager);
    }

    @Override
    public ContextualPreferenceLevel level() {
        return ContextualPreferenceLevel.SYSTEM;
    }

    @Test
    public void isValidShouldReturnFalseForAnySuitablePreference() {
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, resource);

        assertFalse(handler().isValid(preference));
        verify(preferenceManager, times(0)).load(any());
    }

    @Test
    public void searchShouldReturnPreferenceIfItExists() {
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE);
        when(preferenceManager.load(eq(NAME))).thenReturn(Optional.of(systemPreference(NAME, VALUE)));

        final Optional<ContextualPreference> searchedPreference = handler().search(SINGLE_NAME,
                Collections.emptyList());

        assertTrue(searchedPreference.isPresent());
        assertThat(searchedPreference.get(), is(preference));
        verify(preferenceManager).load(eq(NAME));
    }

    @Test
    public void searchShouldDelegateExecutionToTheNextHandlerIfPreferenceDoesNotExist() {
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, resource);
        final List<ContextualPreferenceExternalResource> resources = Collections.emptyList();
        when(preferenceManager.load(eq(NAME))).thenReturn(Optional.empty());
        when(nextHandler.search(eq(SINGLE_NAME), eq(resources))).thenReturn(Optional.of(preference));

        final Optional<ContextualPreference> searchedPreference = handler().search(SINGLE_NAME, resources);

        assertTrue(searchedPreference.isPresent());
        assertThat(searchedPreference.get(), is(preference));
        verify(preferenceManager).load(eq(NAME));
        verify(nextHandler).search(eq(SINGLE_NAME), eq(resources));
    }

    @Test
    public void searchShouldLoadPreferenceByAnotherNameIfThereIsNoPreferenceWithTheFirstName() {
        final ContextualPreference preference = new ContextualPreference(ANOTHER_NAME, VALUE);
        when(preferenceManager.load(eq(NAME))).thenReturn(Optional.empty());
        when(preferenceManager.load(eq(ANOTHER_NAME))).thenReturn(Optional.of(systemPreference(ANOTHER_NAME, VALUE)));

        final Optional<ContextualPreference> searchedPreference = handler().search(SEVERAL_NAMES,
                Collections.emptyList());

        assertTrue(searchedPreference.isPresent());
        assertThat(searchedPreference.get(), is(preference));
        verify(preferenceManager).load(eq(NAME));
        verify(preferenceManager).load(eq(ANOTHER_NAME));
    }

    @Test
    public void searchShouldDelegateExecutionToTheNextHandlerIfNoneOfThePreferencesExist() {
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, resource);
        final List<ContextualPreferenceExternalResource> resources = Collections.emptyList();
        when(preferenceManager.load(eq(NAME))).thenReturn(Optional.empty());
        when(preferenceManager.load(eq(ANOTHER_NAME))).thenReturn(Optional.empty());
        when(nextHandler.search(eq(SEVERAL_NAMES), eq(resources))).thenReturn(Optional.of(preference));

        final Optional<ContextualPreference> searchedPreference = handler().search(SEVERAL_NAMES, resources);

        assertTrue(searchedPreference.isPresent());
        assertThat(searchedPreference.get(), is(preference));
        verify(preferenceManager).load(eq(NAME));
        verify(preferenceManager).load(eq(ANOTHER_NAME));
        verify(nextHandler).search(eq(SEVERAL_NAMES), eq(resources));
    }

    private Preference systemPreference(final String name, final String value) {
        return new Preference(name, value, null, null, PreferenceType.STRING, false);
    }
}
