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

import static com.epam.pipeline.util.CategoricalAttributeTestUtils.assertValuesPresentedForKeyInMap;
import static com.epam.pipeline.util.CategoricalAttributeTestUtils.convertToMap;
import static com.epam.pipeline.util.CategoricalAttributeTestUtils.fromStrings;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.entity.metadata.CategoricalAttribute;
import com.epam.pipeline.entity.metadata.CategoricalAttributeValue;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
<<<<<<< ours
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
||||||| base
import org.junit.Assert;
import org.junit.Test;
=======
import org.junit.jupiter.api.Test;
>>>>>>> theirs
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

<<<<<<< ours
||||||| base
import static com.epam.pipeline.util.CategoricalAttributeTestUtils.assertValuesPresentedForKeyInMap;
import static com.epam.pipeline.util.CategoricalAttributeTestUtils.convertToMap;
import static com.epam.pipeline.util.CategoricalAttributeTestUtils.fromStrings;
import static org.hamcrest.Matchers.containsInAnyOrder;
=======
import static com.epam.pipeline.util.CategoricalAttributeTestUtils.assertValuesPresentedForKeyInMap;
import static com.epam.pipeline.util.CategoricalAttributeTestUtils.convertToMap;
import static com.epam.pipeline.util.CategoricalAttributeTestUtils.fromStrings;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.*;
>>>>>>> theirs

public class CategoricalAttributeDaoTest extends AbstractSpringTest {

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
        assertEquals(2, attributes.size());
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
<<<<<<< ours
                                                        Arrays.asList(ATTRIBUTE_VALUE_2, ATTRIBUTE_VALUE_3))));
        Assert.assertTrue(categoricalAttributeDao.insertAttributesValues(values));
||||||| base
                                                        Arrays.asList(ATTRIBUTE_VALUE_2, ATTRIBUTE_VALUE_3)),
                                            OWNER));
        values.forEach(categoricalAttributeDao::createAttribute);
        Assert.assertTrue(categoricalAttributeDao.insertAttributesValues(values));
=======
                                                        Arrays.asList(ATTRIBUTE_VALUE_2, ATTRIBUTE_VALUE_3)),
                                            OWNER));
        values.forEach(categoricalAttributeDao::createAttribute);
        assertTrue(categoricalAttributeDao.insertAttributesValues(values));
>>>>>>> theirs
        values.add(new CategoricalAttribute(ATTRIBUTE_KEY_1,
                                            fromStrings(ATTRIBUTE_KEY_1,
<<<<<<< ours
                                                        Collections.singletonList(ATTRIBUTE_VALUE_1))));
        Assert.assertFalse(categoricalAttributeDao.insertAttributesValues(values));
||||||| base
                                                        Collections.singletonList(ATTRIBUTE_VALUE_1)),
                                            OWNER));
        Assert.assertTrue(categoricalAttributeDao.insertAttributesValues(values));
=======
                                                        Collections.singletonList(ATTRIBUTE_VALUE_1)),
                                            OWNER));
        assertTrue(categoricalAttributeDao.insertAttributesValues(values));
>>>>>>> theirs
        values.add(new CategoricalAttribute(ATTRIBUTE_KEY_1,
                                            fromStrings(ATTRIBUTE_KEY_1,
<<<<<<< ours
                                                        Collections.singletonList(ATTRIBUTE_VALUE_3))));
        Assert.assertTrue(categoricalAttributeDao.insertAttributesValues(values));
||||||| base
                                                        Collections.singletonList(ATTRIBUTE_VALUE_3)),
                                            OWNER));
        Assert.assertTrue(categoricalAttributeDao.insertAttributesValues(values));
=======
                                                        Collections.singletonList(ATTRIBUTE_VALUE_3)),
                                            OWNER));
        assertTrue(categoricalAttributeDao.insertAttributesValues(values));
>>>>>>> theirs
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
        assertEquals(2, attributesWithValues.size());
        assertValuesPresentedForKeyInMap(attributesWithValues, ATTRIBUTE_KEY_1, ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2);
        assertValuesPresentedForKeyInMap(attributesWithValues, ATTRIBUTE_KEY_2, ATTRIBUTE_VALUE_3);
    }

    @Test
    public void testLoadAllWhenNoAttributesArePresent() {
        assertTrue(CollectionUtils.isEmpty(categoricalAttributeDao.loadAll()));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testLoadAllValuesForKeys() {
        final List<CategoricalAttribute> values = Arrays.asList(
            new CategoricalAttribute(ATTRIBUTE_KEY_1, fromStrings(ATTRIBUTE_KEY_1,
                                                                  Arrays.asList(ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2))),
            new CategoricalAttribute(ATTRIBUTE_KEY_2, fromStrings(ATTRIBUTE_KEY_2,
                                                                  Collections.singletonList(ATTRIBUTE_VALUE_3))));
        categoricalAttributeDao.insertAttributesValues(values);
        final CategoricalAttribute attributeWithValues = categoricalAttributeDao.loadAllValuesForKey(ATTRIBUTE_KEY_1);
<<<<<<< ours
        Assert.assertNotNull(attributeWithValues);
        Assert.assertEquals(ATTRIBUTE_KEY_1, attributeWithValues.getKey());
        final List<CategoricalAttributeValue> attributeValues = Stream.of(ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2)
            .map(v -> new CategoricalAttributeValue(attributeWithValues.getKey(), v))
            .collect(Collectors.toList());
        Assert.assertThat(attributeWithValues.getValues(), CoreMatchers.is(attributeValues));
||||||| base
        Assert.assertNotNull(attributeWithValues);
        Assert.assertEquals(ATTRIBUTE_KEY_1, attributeWithValues.getName());
        Assert.assertThat(attributeWithValues.getValues(), containsInAnyOrder(key1Values.toArray()));
=======
        assertNotNull(attributeWithValues);
        assertEquals(ATTRIBUTE_KEY_1, attributeWithValues.getName());
        assertThat(attributeWithValues.getValues(), containsInAnyOrder(key1Values.toArray()));
>>>>>>> theirs
    }

    @Test
    public void testLoadAllValuesForNonExistentKey() {
        assertNull(categoricalAttributeDao.loadAllValuesForKey(INCORRECT_KEY));
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
        assertTrue(categoricalAttributeDao.deleteAttributeValues(ATTRIBUTE_KEY_2));
        final Map<String, List<String>> attributesWithValues = convertToMap(categoricalAttributeDao.loadAll());
        assertEquals(1, attributesWithValues.size());
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
        assertTrue(categoricalAttributeDao.deleteAttributeValue(ATTRIBUTE_KEY_1, ATTRIBUTE_VALUE_1));
        final Map<String, List<String>> attributesWithValues = convertToMap(categoricalAttributeDao.loadAll());
        assertEquals(2, attributesWithValues.size());
        assertValuesPresentedForKeyInMap(attributesWithValues, ATTRIBUTE_KEY_1, ATTRIBUTE_VALUE_2);
        assertValuesPresentedForKeyInMap(attributesWithValues, ATTRIBUTE_KEY_2, ATTRIBUTE_VALUE_3);
    }
}
