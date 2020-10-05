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
import com.epam.pipeline.entity.metadata.CategoricalAttribute;
import com.epam.pipeline.entity.metadata.CategoricalAttributeValue;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

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
    public void testUpdateAttributesValues() {
        final List<CategoricalAttribute> values = new ArrayList<>();
        values.add(new CategoricalAttribute(ATTRIBUTE_KEY_1,
                                            fromStrings(ATTRIBUTE_KEY_1,
                                                        Arrays.asList(ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2))));
        Assert.assertTrue(categoricalAttributeDao.insertAttributesValues(values));
        final List<CategoricalAttribute> attributes = categoricalAttributeDao.loadAll();
        Assert.assertEquals(1, attributes.size());
        assertAttribute(attributes.get(0), ATTRIBUTE_KEY_1, ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2);

        final List<CategoricalAttribute> valuesToReplace = new ArrayList<>();
        valuesToReplace.add(new CategoricalAttribute(ATTRIBUTE_KEY_1,
                                                     fromStrings(ATTRIBUTE_KEY_1,
                                                                 Collections.singletonList(ATTRIBUTE_VALUE_3))));
        Assert.assertTrue(categoricalAttributeDao.updateCategoricalAttributes(valuesToReplace));
        final List<CategoricalAttribute> attributesAfter = categoricalAttributeDao.loadAll();
        Assert.assertEquals(1, attributesAfter.size());
        assertAttribute(attributesAfter.get(0), ATTRIBUTE_KEY_1, ATTRIBUTE_VALUE_3);
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
        final List<CategoricalAttribute> values = Arrays.asList(
            new CategoricalAttribute(ATTRIBUTE_KEY_1, fromStrings(ATTRIBUTE_KEY_1,
                                                                  Arrays.asList(ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2))),
            new CategoricalAttribute(ATTRIBUTE_KEY_2, fromStrings(ATTRIBUTE_KEY_2,
                                                                  Collections.singletonList(ATTRIBUTE_VALUE_3))));
        categoricalAttributeDao.insertAttributesValues(values);
        final CategoricalAttribute attributeWithValues = categoricalAttributeDao.loadAllValuesForKey(ATTRIBUTE_KEY_1);
        Assert.assertNotNull(attributeWithValues);
        Assert.assertEquals(ATTRIBUTE_KEY_1, attributeWithValues.getKey());
        final List<CategoricalAttributeValue> attributeValues = Stream.of(ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2)
            .map(v -> new CategoricalAttributeValue(attributeWithValues.getKey(), v))
            .collect(Collectors.toList());
        Assert.assertThat(attributeWithValues.getValues(), CoreMatchers.is(attributeValues));
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

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testLinkCreationAndCleanup() {
        final List<CategoricalAttributeValue> valuesWithoutLinks =
            fromStrings(ATTRIBUTE_KEY_1, Arrays.asList(ATTRIBUTE_VALUE_1, ATTRIBUTE_VALUE_2));
        final CategoricalAttributeValue valueWithLink = new CategoricalAttributeValue(ATTRIBUTE_KEY_2,
                                                                                      ATTRIBUTE_VALUE_3);
        valueWithLink.setLinks(Collections.singletonList(new CategoricalAttributeValue(ATTRIBUTE_KEY_1,
                                                                                       ATTRIBUTE_VALUE_1)));
        final List<CategoricalAttribute> attributes =
            Arrays.asList(new CategoricalAttribute(ATTRIBUTE_KEY_1, valuesWithoutLinks),
                          new CategoricalAttribute(ATTRIBUTE_KEY_2, Collections.singletonList(valueWithLink)));

        categoricalAttributeDao.insertAttributesValues(attributes);

        final Map<String, List<CategoricalAttributeValue>> attributeMap = categoricalAttributeDao.loadAll().stream()
            .collect(Collectors.toMap(CategoricalAttribute::getKey, CategoricalAttribute::getValues));
        Assert.assertTrue(attributeMap.get(ATTRIBUTE_KEY_1).stream()
                              .map(CategoricalAttributeValue::getLinks)
                              .allMatch(CollectionUtils::isEmpty));
        final List<CategoricalAttributeValue> valuesForKey2 = attributeMap.get(ATTRIBUTE_KEY_2);
        Assert.assertEquals(1, valuesForKey2.size());
        final CategoricalAttributeValue value1ForKey2 = valuesForKey2.get(0);
        Assert.assertEquals(ATTRIBUTE_KEY_2, value1ForKey2.getKey());
        Assert.assertEquals(ATTRIBUTE_VALUE_3, value1ForKey2.getValue());
        final List<CategoricalAttributeValue> links = value1ForKey2.getLinks();
        Assert.assertEquals(1, links.size());
        final CategoricalAttributeValue linkToKey1Value1 = links.get(0);
        Assert.assertEquals(ATTRIBUTE_KEY_1, linkToKey1Value1.getKey());
        Assert.assertEquals(ATTRIBUTE_VALUE_1, linkToKey1Value1.getValue());
        Assert.assertFalse(linkToKey1Value1.getAutofill());
        Assert.assertEquals(attributeMap.get(ATTRIBUTE_KEY_1).stream()
                                .filter(value -> value.getKey().equals(ATTRIBUTE_KEY_1)
                                                 && value.getValue().equals(ATTRIBUTE_VALUE_1))
                                .map(CategoricalAttributeValue::getId)
                                .findAny()
                                .get(),
                            linkToKey1Value1.getId());

        categoricalAttributeDao.deleteAttributeValue(ATTRIBUTE_KEY_1, ATTRIBUTE_VALUE_1);
        final Map<String, List<CategoricalAttributeValue>> attributeMapAfterDelete =
            categoricalAttributeDao.loadAll().stream()
                .collect(Collectors.toMap(CategoricalAttribute::getKey, CategoricalAttribute::getValues));
        Assert.assertTrue(attributeMapAfterDelete.values().stream()
                              .flatMap(Collection::stream)
                              .map(CategoricalAttributeValue::getLinks)
                              .allMatch(CollectionUtils::isEmpty));
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testCreateLinkOnNonExistentAttributeValue() {
        final CategoricalAttributeValue valueWithLink = new CategoricalAttributeValue(ATTRIBUTE_KEY_2,
                                                                                      ATTRIBUTE_VALUE_3);
        valueWithLink.setLinks(Collections.singletonList(new CategoricalAttributeValue(ATTRIBUTE_KEY_1,
                                                                                       ATTRIBUTE_VALUE_1)));
        final List<CategoricalAttribute> attributes = Collections
            .singletonList(new CategoricalAttribute(ATTRIBUTE_KEY_2, Collections.singletonList(valueWithLink)));
        categoricalAttributeDao.insertAttributesValues(attributes);
    }

    private void assertAttribute(final CategoricalAttribute attributeAfter, final String key,
                                 final String ... values) {
        Assert.assertEquals(key, attributeAfter.getKey());
        final List<CategoricalAttributeValue> attributeValues = Stream.of(values)
            .map(v -> new CategoricalAttributeValue(key, v))
            .collect(Collectors.toList());
        Assert.assertThat(attributeAfter.getValues(), CoreMatchers.is(attributeValues));
    }

    private Map<String, List<String>> convertToMap(final Collection<CategoricalAttribute> attributes) {
        return attributes.stream()
            .collect(Collectors.toMap(CategoricalAttribute::getKey,
                attribute -> attribute.getValues().stream()
                                          .map(CategoricalAttributeValue::getValue)
                                          .collect(Collectors.toList())));
    }

    private void assertValuesPresentedForKeyInMap(final Map<String, List<String>> attributesWithValues,
                                                  final String key,
                                                  final String... values) {
        final List<String> valuesForKey = attributesWithValues.get(key);
        Assert.assertEquals(values.length, valuesForKey.size());
        Assert.assertThat(valuesForKey, contains(values));
    }

    private List<CategoricalAttributeValue> fromStrings(final String key, final List<String> strings) {
        return strings.stream()
            .map(s -> new CategoricalAttributeValue(key, s))
            .collect(Collectors.toList());
    }
}
