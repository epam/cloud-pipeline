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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public abstract class AbstractContextualPreferenceHandlerTest {

    static final String NAME = "name";
    static final String ANOTHER_NAME = "anotherName";
    static final List<String> SINGLE_NAME = Collections.singletonList(NAME);
    static final List<String> SEVERAL_NAMES = Arrays.asList(NAME, ANOTHER_NAME);
    static final String VALUE = "value";
    static final String RESOURCE_ID = "1";
    static final String ANOTHER_RESOURCE_ID = "2";
    final ContextualPreferenceExternalResource resource =
            new ContextualPreferenceExternalResource(level(), RESOURCE_ID);
    final ContextualPreferenceExternalResource anotherResource =
            new ContextualPreferenceExternalResource(level(), ANOTHER_RESOURCE_ID);
    final ContextualPreferenceExternalResource notSuitableResource =
            new ContextualPreferenceExternalResource(anotherLevel(), RESOURCE_ID);

    final ContextualPreferenceHandler nextHandler = mock(ContextualPreferenceHandler.class);

    abstract ContextualPreferenceHandler handler();

    abstract ContextualPreferenceHandler lastHandler();

    abstract ContextualPreferenceLevel level();

    ContextualPreferenceLevel anotherLevel() {
        return Arrays.stream(ContextualPreferenceLevel.values())
                .filter(level -> !level().equals(level))
                .findFirst()
                .orElse(null);
    }

    @Test
    public void isValidShouldDelegateExecutionToTheNextHandlerIfPreferenceLevelDoesNotSuitCurrentHandler() {
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, notSuitableResource);
        when(nextHandler.isValid(eq(preference))).thenReturn(true);

        assertTrue(handler().isValid(preference));
        verify(nextHandler).isValid(eq(preference));
    }

    @Test
    public void isValidShouldReturnFalseIfPreferenceLevelDoesNotSuitCurrentHandlerAndThereIsNoNextHandler() {
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, notSuitableResource);

        assertFalse(lastHandler().isValid(preference));
    }

}
