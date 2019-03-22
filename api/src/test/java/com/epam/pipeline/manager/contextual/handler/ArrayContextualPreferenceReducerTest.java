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
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

public class ArrayContextualPreferenceReducerTest {

    private static final String NAME = "somePreference";
    private static final ContextualPreferenceLevel LEVEL = ContextualPreferenceLevel.USER;
    private static final String RESOURCE_ID = "resourceId";
    private static final String VALUE_1 = "r4.*,t2.*";
    private static final String VALUE_2 = "m5.*,t2.*,c5.*";
    private static final String MERGED_VALUE = "r4.*,t2.*,m5.*,c5.*";

    private final ContextualPreferenceReducer reducer = new ArrayContextualPreferenceReducer();

    @Test
    public void reduceShouldReturnPreferenceWithMergedValue() {
        final ContextualPreferenceExternalResource resource =  new ContextualPreferenceExternalResource(LEVEL,
                RESOURCE_ID);
        final ContextualPreference preference1 = new ContextualPreference(NAME, VALUE_1, resource);
        final ContextualPreference preference2 = new ContextualPreference(NAME, VALUE_2, resource);
        final List<ContextualPreference> preferences = Arrays.asList(preference1, preference2);

        final Optional<ContextualPreference> reducedPreference = reducer.reduce(preferences);

        assertTrue(reducedPreference.isPresent());
        assertThat(reducedPreference.get().getValue(), is(MERGED_VALUE));
    }

    @Test
    public void reduceShouldReturnEmptyOptionalIfTheGivenListOfPreferencesIsEmpty() {
        final Optional<ContextualPreference> reducedPreference = reducer.reduce(Collections.emptyList());

        assertFalse(reducedPreference.isPresent());
    }

    @Test
    public void reduceShouldReturnPreferenceWithoutCreatedDateAndResource() {
        final ContextualPreferenceExternalResource resource = new ContextualPreferenceExternalResource(LEVEL,
                RESOURCE_ID);
        final ContextualPreference preference1 = new ContextualPreference(NAME, VALUE_1, resource);
        final ContextualPreference preference2 = new ContextualPreference(NAME, VALUE_2, resource);
        final List<ContextualPreference> preferences = Arrays.asList(preference1, preference2);

        final Optional<ContextualPreference> reducedPreference = reducer.reduce(preferences);

        assertTrue(reducedPreference.isPresent());
        assertThat(reducedPreference.get().getCreatedDate(), is(nullValue()));
        assertThat(reducedPreference.get().getResource(), is(nullValue()));
    }
}
