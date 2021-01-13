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

package com.epam.pipeline.dao.metadata;

import com.epam.pipeline.entity.metadata.CategoricalAttribute;
import com.epam.pipeline.entity.metadata.CategoricalAttributeValue;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.epam.pipeline.util.CategoricalAttributeTestUtils.assertValuesPresentedForKeyInMap;
import static com.epam.pipeline.util.CategoricalAttributeTestUtils.convertToMap;
import static com.epam.pipeline.util.CategoricalAttributeTestUtils.fromStrings;
import static org.hamcrest.Matchers.containsInAnyOrder;


public class CategoricalAttributeDaoTest extends AbstractJdbcTest {

    private static final String ATTRIBUTE_KEY_1 = "key1";
    private static final String ATTRIBUTE_KEY_2 = "key2";
    private static final String INCORRECT_KEY = "incorrect_key";
    private static final String ATTRIBUTE_VALUE_1 = "value1";
    private static final String ATTRIBUTE_VALUE_2 = "value2";
    private static final String ATTRIBUTE_VALUE_3 = "value3";

    @Autowired
    private CategoricalAttributeDao categoricalAttributeDao;

    @Test
    public void testPairsToCategoricalAttributesConversion() {
        final List<Pair<String, String>> pairs = Arrays.asList(Pair.of(ATTRIBUTE_KEY_1, ATTRIBUTE_VALUE_1),
                                                               Pair.of(ATTRIBUTE_KEY_1, ATTRIBUTE_VALUE_2),
                                                               Pair.of(ATTRIBUTE_KEY_2, ATTRIBUTE_VALUE_2),
                                                               Pair.of(ATTRIBUTE_KEY_2, ATTRIBUTE_VALUE_3));
        final List<CategoricalAttribute> attributes = CategoricalAttributeDao.convertPairsToAttributesList(pairs);
        Assert.assertEquals(2, attributes.size());
        final Map<String, List<String>> attributesAsMap = convertToMap(attributes);
        assertValuesPresentedForKeyInMap(attributesAsMap, ATTRIBUTE_KEY_1, ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2);
        assertValuesPresentedForKeyInMap(attributesAsMap, ATTRIBUTE_KEY_2, ATTRIBUTE_VALUE_2, ATTRIBUTE_VALUE_3);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testInsertAttributesValues() {
        final List<CategoricalAttribute> values = new ArrayList<>();
        values.add(new CategoricalAttribute(ATTRIBUTE_KEY_1,
                                            fromStrings(ATTRIBUTE_KEY_1,
                                                        Arrays.asList(ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2))));
        values.add(new CategoricalAttribute(ATTRIBUTE_KEY_2,
                                            fromStrings(ATTRIBUTE_KEY_2,
                                                        Arrays.asList(ATTRIBUTE_VALUE_2, ATTRIBUTE_VALUE_3))));
        Assert.assertTrue(categoricalAttributeDao.insertAttributesValues(values));
        values.add(new CategoricalAttribute(ATTRIBUTE_KEY_1,
                                            fromStrings(ATTRIBUTE_KEY_1,
                                                        Collections.singletonList(ATTRIBUTE_VALUE_1))));
        Assert.assertFalse(categoricalAttributeDao.insertAttributesValues(values));
        values.add(new CategoricalAttribute(ATTRIBUTE_KEY_1,
                                            fromStrings(ATTRIBUTE_KEY_1,
                                                        Collections.singletonList(ATTRIBUTE_VALUE_3))));
        Assert.assertTrue(categoricalAttributeDao.insertAttributesValues(values));
    }



    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testLoadAll() {
        final List<CategoricalAttribute> values = Arrays.asList(
            new CategoricalAttribute(ATTRIBUTE_KEY_1, fromStrings(ATTRIBUTE_KEY_1,
                                                                  Arrays.asList(ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2))),
            new CategoricalAttribute(ATTRIBUTE_KEY_2, fromStrings(ATTRIBUTE_KEY_2,
                                                                  Collections.singletonList(ATTRIBUTE_VALUE_3))));
        categoricalAttributeDao.insertAttributesValues(values);
        final Map<String, List<String>> attributesWithValues = convertToMap(categoricalAttributeDao.loadAll());
        Assert.assertEquals(2, attributesWithValues.size());
        assertValuesPresentedForKeyInMap(attributesWithValues, ATTRIBUTE_KEY_1, ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2);
        assertValuesPresentedForKeyInMap(attributesWithValues, ATTRIBUTE_KEY_2, ATTRIBUTE_VALUE_3);
    }

    @Test
    public void testLoadAllWhenNoAttributesArePresent() {
        Assert.assertTrue(CollectionUtils.isEmpty(categoricalAttributeDao.loadAll()));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testLoadAllValuesForKeys() {
        final List<CategoricalAttributeValue> key1Values = fromStrings(ATTRIBUTE_KEY_1,
                Arrays.asList(ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2));
        final List<CategoricalAttribute> values = Arrays.asList(
            new CategoricalAttribute(ATTRIBUTE_KEY_1, key1Values),
            new CategoricalAttribute(ATTRIBUTE_KEY_2, fromStrings(ATTRIBUTE_KEY_2,
                                                                  Collections.singletonList(ATTRIBUTE_VALUE_3))));
        categoricalAttributeDao.insertAttributesValues(values);
        final CategoricalAttribute attributeWithValues = categoricalAttributeDao.loadAllValuesForKey(ATTRIBUTE_KEY_1);
        Assert.assertNotNull(attributeWithValues);
        Assert.assertEquals(ATTRIBUTE_KEY_1, attributeWithValues.getKey());
        Assert.assertThat(attributeWithValues.getValues(), containsInAnyOrder(key1Values.toArray()));
    }

    @Test
    public void testLoadAllValuesForNonExistentKey() {
        Assert.assertNull(categoricalAttributeDao.loadAllValuesForKey(INCORRECT_KEY));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testDeleteAttributeValuesQuery() {
        final List<CategoricalAttribute> values = Arrays.asList(
            new CategoricalAttribute(ATTRIBUTE_KEY_1, fromStrings(ATTRIBUTE_KEY_1,
                                                                  Arrays.asList(ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2))),
            new CategoricalAttribute(ATTRIBUTE_KEY_2, fromStrings(ATTRIBUTE_KEY_2,
                                                                  Collections.singletonList(ATTRIBUTE_VALUE_3))));
        categoricalAttributeDao.insertAttributesValues(values);
        Assert.assertTrue(categoricalAttributeDao.deleteAttributeValues(ATTRIBUTE_KEY_2));
        final Map<String, List<String>> attributesWithValues = convertToMap(categoricalAttributeDao.loadAll());
        Assert.assertEquals(1, attributesWithValues.size());
        assertValuesPresentedForKeyInMap(attributesWithValues, ATTRIBUTE_KEY_1, ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testDeleteAttributeValueQuery() {
        final List<CategoricalAttribute> values = Arrays.asList(
            new CategoricalAttribute(ATTRIBUTE_KEY_1, fromStrings(ATTRIBUTE_KEY_1,
                                                                  Arrays.asList(ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2))),
            new CategoricalAttribute(ATTRIBUTE_KEY_2, fromStrings(ATTRIBUTE_KEY_2,
                                                                  Collections.singletonList(ATTRIBUTE_VALUE_3))));
        categoricalAttributeDao.insertAttributesValues(values);
        Assert.assertTrue(categoricalAttributeDao.deleteAttributeValue(ATTRIBUTE_KEY_1, ATTRIBUTE_VALUE_1));
        final Map<String, List<String>> attributesWithValues = convertToMap(categoricalAttributeDao.loadAll());
        Assert.assertEquals(2, attributesWithValues.size());
        assertValuesPresentedForKeyInMap(attributesWithValues, ATTRIBUTE_KEY_1, ATTRIBUTE_VALUE_2);
        assertValuesPresentedForKeyInMap(attributesWithValues, ATTRIBUTE_KEY_2, ATTRIBUTE_VALUE_3);
    }
}
