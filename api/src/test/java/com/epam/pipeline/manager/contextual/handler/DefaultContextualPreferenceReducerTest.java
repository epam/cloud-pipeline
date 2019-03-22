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
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.preference.PreferenceType;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;

@SuppressWarnings("PMD.TooManyStaticImports")
public class DefaultContextualPreferenceReducerTest {

    private static final String PREFERENCE_1 = "cluster.allowed.instance.types";
    private static final String PREFERENCE_2 = "cluster.allowed.instance.types.docker";
    private static final String ANOTHER_PREFERENCE = "anotherPreference";
    private static final String INSTANCE_TYPES = "r4.*,t2.*";
    private static final String VALUE_1 = "r4.*,t2.*";
    private static final String VALUE_2 = "m5.*,t2.*,c5.*";
    private static final String MERGED_VALUE = "m5.*,t2.*,c5.*";
    private static final ContextualPreferenceLevel LEVEL = ContextualPreferenceLevel.ROLE;
    private static final ContextualPreferenceLevel ANOTHER_LEVEL = ContextualPreferenceLevel.USER;
    private static final String RESOURCE_ID = "resourceId";

    private final ContextualPreferenceReducer innerReducer = mock(ContextualPreferenceReducer.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final ContextualPreferenceReducer reducer;

    {
        final Map<String, ContextualPreferenceReducer> preferenceReducerMap = new HashMap<>();
        preferenceReducerMap.put(PREFERENCE_1, innerReducer);
        reducer = new DefaultContextualPreferenceReducer(messageHelper, preferenceReducerMap);
    }

    @Test
    public void reduceShouldReturnEmptyOptionalIfGivenListIsEmpty() {
        final Optional<ContextualPreference> reducedPreference = reducer.reduce(Collections.emptyList());

        assertFalse(reducedPreference.isPresent());
    }

    @Test
    public void reduceShouldReturnThePreferenceIfSingletonListIsGiven() {
        final ContextualPreferenceExternalResource resource = new ContextualPreferenceExternalResource(LEVEL,
                RESOURCE_ID);
        final ContextualPreference preference = new ContextualPreference(PREFERENCE_1, INSTANCE_TYPES, resource);
        final List<ContextualPreference> preferences = Collections.singletonList(preference);

        final Optional<ContextualPreference> reducedPreference = reducer.reduce(preferences);

        assertTrue(reducedPreference.isPresent());
        assertThat(reducedPreference.get(), is(preference));
    }

    @Test
    public void reduceShouldReturnEmptyOptionalIfThereArePreferencesWithDifferentNames() {
        final ContextualPreferenceExternalResource resource = new ContextualPreferenceExternalResource(LEVEL,
                RESOURCE_ID);
        final ContextualPreference preference1 = new ContextualPreference(PREFERENCE_1, VALUE_1, resource);
        final ContextualPreference preference2 = new ContextualPreference(PREFERENCE_2, VALUE_2, resource);
        final List<ContextualPreference> preferences = Arrays.asList(preference1, preference2);

        final Optional<ContextualPreference> reducedPreference = reducer.reduce(preferences);

        assertFalse(reducedPreference.isPresent());
    }

    @Test
    public void reduceShouldReturnEmptyOptionalIfThereArePreferencesWithDifferentTypes() {
        final ContextualPreferenceExternalResource resource = new ContextualPreferenceExternalResource(LEVEL,
                RESOURCE_ID);
        final ContextualPreference preference1 = new ContextualPreference(PREFERENCE_1, VALUE_1, PreferenceType.INTEGER,
                resource);
        final ContextualPreference preference2 = new ContextualPreference(PREFERENCE_1, VALUE_2, PreferenceType.STRING,
                resource);
        final List<ContextualPreference> preferences = Arrays.asList(preference1, preference2);

        final Optional<ContextualPreference> reducedPreference = reducer.reduce(preferences);

        assertFalse(reducedPreference.isPresent());
    }

    @Test
    public void reduceShouldReturnEmptyOptionalIfThereArePreferencesWithDifferentLevels() {
        final ContextualPreferenceExternalResource resource = new ContextualPreferenceExternalResource(LEVEL,
                RESOURCE_ID);
        final ContextualPreferenceExternalResource anotherResource = new ContextualPreferenceExternalResource(
                ANOTHER_LEVEL, RESOURCE_ID);
        final ContextualPreference preference1 = new ContextualPreference(PREFERENCE_1, VALUE_1, resource);
        final ContextualPreference preference2 = new ContextualPreference(PREFERENCE_1, VALUE_2, anotherResource);
        final List<ContextualPreference> preferences = Arrays.asList(preference1, preference2);

        final Optional<ContextualPreference> reducedPreference = reducer.reduce(preferences);

        assertFalse(reducedPreference.isPresent());
    }

    @Test
    public void reduceShouldReturnEmptyOptionalIfThereIsNoReducerForTheGivenPreference() {
        final ContextualPreferenceExternalResource resource = new ContextualPreferenceExternalResource(LEVEL,
                RESOURCE_ID);
        final ContextualPreference preference1 = new ContextualPreference(ANOTHER_PREFERENCE, VALUE_1, resource);
        final ContextualPreference preference2 = new ContextualPreference(ANOTHER_PREFERENCE, VALUE_2, resource);
        final List<ContextualPreference> preferences = Arrays.asList(preference1, preference2);

        final Optional<ContextualPreference> reducedPreference = reducer.reduce(preferences);

        assertFalse(reducedPreference.isPresent());
    }

    @Test
    public void reduceShouldReturnReducedPreferenceIfThereIsReducerForTheGivenPreference() {
        final ContextualPreferenceExternalResource resource = new ContextualPreferenceExternalResource(LEVEL,
                RESOURCE_ID);
        final ContextualPreference preference1 = new ContextualPreference(PREFERENCE_1, VALUE_1, resource);
        final ContextualPreference preference2 = new ContextualPreference(PREFERENCE_1, VALUE_2, resource);
        final ContextualPreference expectedPreference = new ContextualPreference(PREFERENCE_1, MERGED_VALUE, resource);
        final List<ContextualPreference> preferences = Arrays.asList(preference1, preference2);
        when(innerReducer.reduce(eq(preferences))).thenReturn(Optional.of(expectedPreference));

        final Optional<ContextualPreference> reducedPreference = reducer.reduce(preferences);

        assertTrue(reducedPreference.isPresent());
        assertThat(reducedPreference.get(), is(expectedPreference));
        verify(innerReducer).reduce(eq(preferences));
    }
}
