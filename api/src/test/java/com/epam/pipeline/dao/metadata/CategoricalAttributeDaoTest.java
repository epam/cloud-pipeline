/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dao.metadata;

import com.epam.pipeline.AbstractSpringTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

public class CategoricalAttributeDaoTest extends AbstractSpringTest {

    private static final String ATTRIBUTE_KEY_1 = "key1";
    private static final String ATTRIBUTE_KEY_2 = "key2";
    private static final String ATTRIBUTE_VALUE_1 = "value1";
    private static final String ATTRIBUTE_VALUE_2 = "value2";
    private static final String ATTRIBUTE_VALUE_3 = "value3";

    @Autowired
    private CategoricalAttributeDao categoricalAttributeDao;

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testInsertAttributesValues() {
        final Map<String, List<String>> values = new HashMap<>();
        values.put(ATTRIBUTE_KEY_1, Arrays.asList(ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2));
        values.put(ATTRIBUTE_KEY_2, Arrays.asList(ATTRIBUTE_VALUE_2, ATTRIBUTE_VALUE_3));
        assertTrue(categoricalAttributeDao.insertAttributesValues(values));
        values.put(ATTRIBUTE_KEY_1, Collections.singletonList(ATTRIBUTE_VALUE_1));
        assertFalse(categoricalAttributeDao.insertAttributesValues(values));
        values.put(ATTRIBUTE_KEY_1, Collections.singletonList(ATTRIBUTE_VALUE_3));
        assertTrue(categoricalAttributeDao.insertAttributesValues(values));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testLoadAll() {
        final Map<String, List<String>> values = new HashMap<>();
        values.put(ATTRIBUTE_KEY_1, Arrays.asList(ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2));
        values.put(ATTRIBUTE_KEY_2, Collections.singletonList(ATTRIBUTE_VALUE_3));
        categoricalAttributeDao.insertAttributesValues(values);
        final Map<String, List<String>> attributesWithValues = categoricalAttributeDao.loadAll();
        assertEquals(2, attributesWithValues.size());
        assertValuesPresentedForKeyInMap(attributesWithValues, ATTRIBUTE_KEY_1, ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2);
        assertValuesPresentedForKeyInMap(attributesWithValues, ATTRIBUTE_KEY_2, ATTRIBUTE_VALUE_3);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testLoadAllValuesForKeys() {
        final Map<String, List<String>> values = new HashMap<>();
        values.put(ATTRIBUTE_KEY_1, Arrays.asList(ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2));
        values.put(ATTRIBUTE_KEY_2, Collections.singletonList(ATTRIBUTE_VALUE_3));
        categoricalAttributeDao.insertAttributesValues(values);
        final Map<String, List<String>>
            attributesWithValues =
            categoricalAttributeDao.loadAllValuesForKeys(Collections.singletonList(ATTRIBUTE_KEY_1));
        assertEquals(1, attributesWithValues.size());
        assertValuesPresentedForKeyInMap(attributesWithValues, ATTRIBUTE_KEY_1, ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testDeleteAttributeValuesQuery() {
        final Map<String, List<String>> values = new HashMap<>();
        values.put(ATTRIBUTE_KEY_1, Arrays.asList(ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2));
        values.put(ATTRIBUTE_KEY_2, Collections.singletonList(ATTRIBUTE_VALUE_3));
        categoricalAttributeDao.insertAttributesValues(values);
        assertTrue(categoricalAttributeDao.deleteAttributeValuesQuery(ATTRIBUTE_KEY_2));
        final Map<String, List<String>> attributesWithValues = categoricalAttributeDao.loadAll();
        assertEquals(1, attributesWithValues.size());
        assertValuesPresentedForKeyInMap(attributesWithValues, ATTRIBUTE_KEY_1, ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testDeleteAttributeValueQuery() {
        final Map<String, List<String>> values = new HashMap<>();
        values.put(ATTRIBUTE_KEY_1, Arrays.asList(ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2));
        values.put(ATTRIBUTE_KEY_2, Collections.singletonList(ATTRIBUTE_VALUE_3));
        categoricalAttributeDao.insertAttributesValues(values);
        assertTrue(categoricalAttributeDao.deleteAttributeValueQuery(ATTRIBUTE_KEY_1, ATTRIBUTE_VALUE_1));
        final Map<String, List<String>> attributesWithValues = categoricalAttributeDao.loadAll();
        assertEquals(2, attributesWithValues.size());
        assertValuesPresentedForKeyInMap(attributesWithValues, ATTRIBUTE_KEY_1, ATTRIBUTE_VALUE_2);
        assertValuesPresentedForKeyInMap(attributesWithValues, ATTRIBUTE_KEY_2, ATTRIBUTE_VALUE_3);
    }

    private void assertValuesPresentedForKeyInMap(final Map<String, List<String>> attributesWithValues,
                                                  final String key,
                                                  final String... values) {
        final List<String> valuesForKey = attributesWithValues.get(key);
        assertEquals(values.length, valuesForKey.size());
        assertThat(valuesForKey, contains(values));
    }
}