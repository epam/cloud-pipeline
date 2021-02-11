/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dao.contextual;

import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.preference.PreferenceType;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static com.epam.pipeline.util.CustomMatchers.isEmpty;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("PMD.TooManyStaticImports")
public class ContextualPreferenceDaoTest extends AbstractJdbcTest {

    private static final String NAME = "name";
    private static final String ANOTHER_NAME = "anotherName";
    private static final String VALUE = "value";
    private static final String ANOTHER_VALUE = "anotherValue";
    private static final PreferenceType TYPE = PreferenceType.INTEGER;
    private static final ContextualPreferenceLevel LEVEL = ContextualPreferenceLevel.USER;
    private static final ContextualPreferenceLevel ANOTHER_LEVEL = ContextualPreferenceLevel.TOOL;
    private static final String RESOURCE_ID = "resourceId";
    private static final String ANOTHER_RESOURCE_ID = "anotherResourceId";

    @Autowired
    private ContextualPreferenceDao contextualPreferenceDao;

    @After
    public void tearDown() {
        contextualPreferenceDao.loadAll().forEach(pref ->
                contextualPreferenceDao.delete(pref.getName(), pref.getResource()));
    }

    @Test
    public void upsertShouldFailIfPreferenceHasEmptyFields() {
        assertThrows(() -> {
            final ContextualPreference preferenceWithoutName = new ContextualPreference(null, VALUE,
                    new ContextualPreferenceExternalResource(LEVEL, RESOURCE_ID));
            contextualPreferenceDao.upsert(preferenceWithoutName);
        });
        assertThrows(() -> {
            final ContextualPreference preferenceWithoutValue = new ContextualPreference(NAME, null,
                    new ContextualPreferenceExternalResource(LEVEL, RESOURCE_ID));
            contextualPreferenceDao.upsert(preferenceWithoutValue);
        });
        assertThrows(() -> {
            final ContextualPreference preferenceWithoutType = new ContextualPreference(NAME, VALUE, null,
                    new ContextualPreferenceExternalResource(LEVEL, RESOURCE_ID));
            contextualPreferenceDao.upsert(preferenceWithoutType);
        });
        assertThrows(() -> {
            final ContextualPreference preferenceWithoutType = new ContextualPreference(NAME, VALUE,
                    PreferenceType.STRING, null);
            contextualPreferenceDao.upsert(preferenceWithoutType);
        });
        assertThrows(() -> {
            final ContextualPreference preferenceWithoutResourceLevel = new ContextualPreference(NAME, VALUE,
                    new ContextualPreferenceExternalResource(null, RESOURCE_ID));
            contextualPreferenceDao.upsert(preferenceWithoutResourceLevel);
        });
        assertThrows(() -> {
            final ContextualPreference preferenceWithoutResourceId = new ContextualPreference(NAME, VALUE,
                    new ContextualPreferenceExternalResource(LEVEL, null));
            contextualPreferenceDao.upsert(preferenceWithoutResourceId);
        });
    }

    @Test
    public void upsertShouldCreatePreference() {
        final ContextualPreferenceExternalResource resource = new ContextualPreferenceExternalResource(LEVEL,
                RESOURCE_ID);
        final ContextualPreference preference = contextualPreferenceDao.upsert(new ContextualPreference(NAME, VALUE,
                resource));

        final Optional<ContextualPreference> loadedPreference = contextualPreferenceDao.load(NAME, resource);

        assertTrue(loadedPreference.isPresent());
        assertThat(loadedPreference.get(), is(preference));
    }

    @Test
    public void upsertShouldUpdatePreferenceIfItAlreadyExists() {
        final ContextualPreferenceExternalResource resource = new ContextualPreferenceExternalResource(LEVEL,
                RESOURCE_ID);
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, resource);
        contextualPreferenceDao.upsert(preference);
        contextualPreferenceDao.upsert(preference.withValue(ANOTHER_VALUE));

        final Optional<ContextualPreference> loadedPreference = contextualPreferenceDao.load(NAME, resource);

        assertTrue(loadedPreference.isPresent());
        assertThat(loadedPreference.get().getValue(), is(ANOTHER_VALUE));
    }

    @Test
    public void upsertShouldSetCreatedDateWhileCreatingPreference() {
        final ContextualPreferenceExternalResource resource = new ContextualPreferenceExternalResource(LEVEL,
                RESOURCE_ID);
        contextualPreferenceDao.upsert(new ContextualPreference(NAME, VALUE, resource));

        final Optional<ContextualPreference> loadedPreference = contextualPreferenceDao.load(NAME, resource);

        assertTrue(loadedPreference.isPresent());
        assertNotNull(loadedPreference.get().getCreatedDate());
    }

    @Test
    public void loadShouldReturnEmptyOptionalIfPreferenceDoesNotExist() {
        final ContextualPreferenceExternalResource resource = new ContextualPreferenceExternalResource(LEVEL,
                RESOURCE_ID);

        final Optional<ContextualPreference> loadedPreference = contextualPreferenceDao.load(NAME, resource);

        assertFalse(loadedPreference.isPresent());
    }

    @Test
    public void loadShouldReturnAllPreferencesWithTheGivenName() {
        final ContextualPreference preference1 = contextualPreferenceDao.upsert(new ContextualPreference(NAME, VALUE,
                new ContextualPreferenceExternalResource(LEVEL, RESOURCE_ID)));
        final ContextualPreference preference2 = contextualPreferenceDao.upsert(new ContextualPreference(NAME, VALUE,
                new ContextualPreferenceExternalResource(LEVEL, ANOTHER_RESOURCE_ID)));
        final ContextualPreference preference3 = contextualPreferenceDao.upsert(new ContextualPreference(NAME, VALUE,
                new ContextualPreferenceExternalResource(ANOTHER_LEVEL, ANOTHER_RESOURCE_ID)));
        contextualPreferenceDao.upsert(new ContextualPreference(ANOTHER_NAME, VALUE,
                new ContextualPreferenceExternalResource(ANOTHER_LEVEL, ANOTHER_RESOURCE_ID)));

        final List<ContextualPreference> loadedPreferences = contextualPreferenceDao.load(NAME);

        assertThat(loadedPreferences, containsInAnyOrder(preference1, preference2, preference3));
    }

    @Test
    public void loadShouldReturnPreference() {
        final ContextualPreferenceExternalResource resource = new ContextualPreferenceExternalResource(LEVEL,
                RESOURCE_ID);
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, TYPE, resource);
        final ContextualPreference storedPreference = contextualPreferenceDao.upsert(preference);

        final Optional<ContextualPreference> loadedPreference = contextualPreferenceDao.load(NAME, resource);

        assertTrue(loadedPreference.isPresent());
        loadedPreference.ifPresent(pref -> {
            assertThat(pref.getName(), is(storedPreference.getName()));
            assertThat(pref.getValue(), is(storedPreference.getValue()));
            assertThat(pref.getType(), is(storedPreference.getType()));
            assertThat(pref.getCreatedDate(), is(storedPreference.getCreatedDate()));
            assertThat(pref.getResource(), is(storedPreference.getResource()));
        });
    }

    @Test
    public void loadAllShouldReturnEmptyListIfThereAreNoPreferences() {
        assertThat(contextualPreferenceDao.loadAll(), isEmpty());
    }

    @Test
    public void loadAllShouldReturnListOfAllPreferences() {
        final ContextualPreferenceExternalResource resource = new ContextualPreferenceExternalResource(LEVEL,
                RESOURCE_ID);
        final ContextualPreference storedPreference1 = contextualPreferenceDao.upsert(new ContextualPreference(NAME,
                VALUE, resource));
        final ContextualPreference storedPreference2 = contextualPreferenceDao.upsert(new ContextualPreference(
                ANOTHER_NAME, ANOTHER_VALUE, resource));

        assertThat(contextualPreferenceDao.loadAll(), containsInAnyOrder(storedPreference1, storedPreference2));
    }

    @Test
    public void deleteShouldRemovePreference() {
        final ContextualPreferenceExternalResource resource = new ContextualPreferenceExternalResource(LEVEL,
                RESOURCE_ID);
        contextualPreferenceDao.upsert(new ContextualPreference(NAME, VALUE, resource));
        contextualPreferenceDao.delete(NAME, resource);

        final Optional<ContextualPreference> loadedPreference = contextualPreferenceDao.load(NAME, resource);

        assertFalse(loadedPreference.isPresent());
    }
}
