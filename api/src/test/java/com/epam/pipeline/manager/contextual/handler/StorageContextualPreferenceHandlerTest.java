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

import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StorageContextualPreferenceHandlerTest extends AbstractDaoContextualPreferenceHandlerTest {

    private final DataStorageDao storageDao = mock(DataStorageDao.class);

    @Override
    public ContextualPreferenceHandler handler() {
        return new StorageContextualPreferenceHandler(storageDao, contextualPreferenceDao, nextHandler);
    }

    @Override
    public ContextualPreferenceHandler lastHandler() {
        return new StorageContextualPreferenceHandler(storageDao, contextualPreferenceDao);
    }

    @Override
    public ContextualPreferenceLevel level() {
        return ContextualPreferenceLevel.STORAGE;
    }

    @Test
    public void isValidShouldReturnFalseIfStorageDoesNotExist() {
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, resource);
        when(storageDao.loadDataStorage(eq(Long.valueOf(RESOURCE_ID)))).thenReturn(null);

        assertFalse(handler().isValid(preference));
    }

    @Test
    public void isValidShouldReturnTrueIfStorageExists() {
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, resource);
        when(storageDao.loadDataStorage(eq(Long.valueOf(RESOURCE_ID)))).thenReturn(new S3bucketDataStorage());

        assertTrue(handler().isValid(preference));
    }
}
