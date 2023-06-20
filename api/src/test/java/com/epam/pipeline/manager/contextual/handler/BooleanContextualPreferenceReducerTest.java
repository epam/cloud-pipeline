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

import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.nimbusds.jose.util.Pair;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class BooleanContextualPreferenceReducerTest {

    private static final String NAME = "somePreference";
    private static final ContextualPreferenceLevel LEVEL = ContextualPreferenceLevel.USER;
    private static final String RESOURCE_ID = "resourceId";
    private static final String TRUE = "true";
    private static final String FALSE = "false";

    private final ContextualPreferenceReducer reducer = new BooleanContextualPreferenceReducer();

    @Test
    public void reduceShouldReturnPreferenceWithTrueValueIfTheGivenListOfPreferencesHasAtLeastOneTrueValue() {
        final ContextualPreferenceExternalResource resource = new ContextualPreferenceExternalResource(LEVEL,
                RESOURCE_ID);
        final ContextualPreference truePreference = new ContextualPreference(NAME, TRUE, resource);
        final ContextualPreference falsePreference = new ContextualPreference(NAME, FALSE, resource);
        final List<Pair<List<ContextualPreference>, String>> preferences = Arrays.asList(
                Pair.of(Arrays.asList(truePreference, truePreference), TRUE),
                Pair.of(Arrays.asList(truePreference, falsePreference), TRUE),
                Pair.of(Arrays.asList(falsePreference, truePreference), TRUE),
                Pair.of(Arrays.asList(falsePreference, falsePreference), FALSE));
        preferences.stream().collect(Collectors.toMap(Pair::getLeft, Pair::getRight))
                .forEach((inputs, expectedMergedValue) -> {
                    final Optional<ContextualPreference> reducedPreference = reducer.reduce(inputs);
                    assertTrue(reducedPreference.isPresent());
                    assertThat(reducedPreference.get().getValue(), is(expectedMergedValue));
                });
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
        final ContextualPreference preference1 = new ContextualPreference(NAME, TRUE, resource);
        final ContextualPreference preference2 = new ContextualPreference(NAME, FALSE, resource);
        final List<ContextualPreference> preferences = Arrays.asList(preference1, preference2);

        final Optional<ContextualPreference> reducedPreference = reducer.reduce(preferences);

        assertTrue(reducedPreference.isPresent());
        assertThat(reducedPreference.get().getCreatedDate(), is(nullValue()));
        assertThat(reducedPreference.get().getResource(), is(nullValue()));
    }
}
