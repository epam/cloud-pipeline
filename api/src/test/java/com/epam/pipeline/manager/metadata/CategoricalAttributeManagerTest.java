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

package com.epam.pipeline.manager.metadata;

import static com.epam.pipeline.util.CategoricalAttributeTestUtils.assertAttribute;
import static com.epam.pipeline.util.CategoricalAttributeTestUtils.fromStrings;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.entity.metadata.CategoricalAttribute;
import com.epam.pipeline.entity.metadata.CategoricalAttributeValue;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Transactional
public class CategoricalAttributeManagerTest extends AbstractSpringTest {

    private static final String KEY_1 = "key1";
    private static final String KEY_2 = "key2";
    private static final String INVALID_KEY = "invalid_key";
    private static final String VALUE_1 = "valueA";
    private static final String VALUE_2 = "valueB";
    private static final String VALUE_3 = "valueC";
    private static final String OWNER_1 = "OWNER_1";
    private static final String OWNER_2 = "OWNER_2";

    @Autowired
    private CategoricalAttributeManager categoricalAttributeManager;

    @Test
    public void testLinkCreationAndCleanup() {
        final List<CategoricalAttributeValue> valuesWithoutLinks =
            fromStrings(KEY_1, Arrays.asList(VALUE_1, VALUE_2));
        final CategoricalAttributeValue valueWithLink = new CategoricalAttributeValue(KEY_2, VALUE_3);
        valueWithLink.setLinks(Collections.singletonList(new CategoricalAttributeValue(KEY_1, VALUE_1)));
        final List<CategoricalAttribute> attributes =
            Arrays.asList(new CategoricalAttribute(KEY_1, valuesWithoutLinks),
                          new CategoricalAttribute(KEY_2, Collections.singletonList(valueWithLink)));

        attributes.forEach(categoricalAttributeManager::create);

        final Map<String, List<CategoricalAttributeValue>> attributeMap = categoricalAttributeManager.loadAll().stream()
            .collect(Collectors.toMap(CategoricalAttribute::getName, CategoricalAttribute::getValues));
        Assert.assertTrue(attributeMap.get(KEY_1).stream()
                              .map(CategoricalAttributeValue::getLinks)
                              .allMatch(CollectionUtils::isEmpty));
        final List<CategoricalAttributeValue> valuesForKey2 = attributeMap.get(KEY_2);
        Assert.assertEquals(1, valuesForKey2.size());
        final CategoricalAttributeValue value1ForKey2 = valuesForKey2.get(0);
        Assert.assertEquals(KEY_2, value1ForKey2.getKey());
        Assert.assertEquals(VALUE_3, value1ForKey2.getValue());
        final List<CategoricalAttributeValue> links = value1ForKey2.getLinks();
        Assert.assertEquals(1, links.size());
        final CategoricalAttributeValue linkToKey1Value1 = links.get(0);
        Assert.assertEquals(KEY_1, linkToKey1Value1.getKey());
        Assert.assertEquals(VALUE_1, linkToKey1Value1.getValue());
        Assert.assertFalse(linkToKey1Value1.getAutofill());
        Assert.assertEquals(attributeMap.get(KEY_1).stream()
                                .filter(value -> value.getKey().equals(KEY_1) && value.getValue().equals(VALUE_1))
                                .map(CategoricalAttributeValue::getId)
                                .findAny()
                                .get(),
                            linkToKey1Value1.getId());

        categoricalAttributeManager.deleteAttributeValue(KEY_1, VALUE_1);
        final Map<String, List<CategoricalAttributeValue>> attributeMapAfterDelete =
            categoricalAttributeManager.loadAll().stream()
                .collect(Collectors.toMap(CategoricalAttribute::getName, CategoricalAttribute::getValues));
        Assert.assertTrue(attributeMapAfterDelete.values().stream()
                              .flatMap(Collection::stream)
                              .map(CategoricalAttributeValue::getLinks)
                              .allMatch(CollectionUtils::isEmpty));
    }

    @Test(expected = IllegalArgumentException.class)
    public void loadValuesForNonExistentKey() {
        categoricalAttributeManager.loadByNameOrId(INVALID_KEY);
    }

    @Test
    public void testUpdateAttributesValues() {
        categoricalAttributeManager.create(
            new CategoricalAttribute(KEY_1, fromStrings(KEY_1, Arrays.asList(VALUE_1, VALUE_2))));
        final List<CategoricalAttribute> attributes = categoricalAttributeManager.loadAll();
        Assert.assertEquals(1, attributes.size());
        assertAttribute(attributes.get(0), KEY_1, VALUE_1, VALUE_2);

        final List<CategoricalAttribute> valuesToReplace = new ArrayList<>();
        valuesToReplace.add(new CategoricalAttribute(KEY_1, fromStrings(KEY_1, Collections.singletonList(VALUE_3))));
        Assert.assertTrue(categoricalAttributeManager.updateValues(valuesToReplace));
        final List<CategoricalAttribute> attributesAfter = categoricalAttributeManager.loadAll();
        Assert.assertEquals(1, attributesAfter.size());
        assertAttribute(attributesAfter.get(0), KEY_1, VALUE_3);
    }


    @Test(expected = IllegalArgumentException.class)
    public void testCreateLinkOnNonExistentAttributeValue() {
        final CategoricalAttributeValue valueWithLink = new CategoricalAttributeValue(KEY_2, VALUE_3);
        valueWithLink.setLinks(Collections.singletonList(new CategoricalAttributeValue(KEY_1, VALUE_1)));
        final List<CategoricalAttribute> attributes = Collections
            .singletonList(new CategoricalAttribute(KEY_2, Collections.singletonList(valueWithLink)));
        categoricalAttributeManager.updateValues(attributes);
    }

    @Test
    public void shouldDeleteLinksOnUpdate() {
        final CategoricalAttribute attribute = new CategoricalAttribute(KEY_1,
                fromStrings(KEY_1, Arrays.asList(VALUE_1, VALUE_2)));
        categoricalAttributeManager.create(attribute);
        final CategoricalAttributeValue valueWithLink = new CategoricalAttributeValue(KEY_2, VALUE_3);
        valueWithLink.setLinks(Arrays.asList(
                new CategoricalAttributeValue(KEY_1, VALUE_1),
                new CategoricalAttributeValue(KEY_1, VALUE_2)));
        final CategoricalAttribute attributeWithLink = new CategoricalAttribute(KEY_2,
                Collections.singletonList(valueWithLink));
        categoricalAttributeManager.create(attributeWithLink);
        final CategoricalAttribute loadedAttrWithLink = categoricalAttributeManager.loadByNameOrId(KEY_2);
        assertThat(loadedAttrWithLink.getValues(), hasSize(1));
        assertThat(loadedAttrWithLink.getValues().get(0).getLinks(), hasSize(2));

        loadedAttrWithLink.getValues().get(0).setLinks(
                Collections.singletonList(new CategoricalAttributeValue(KEY_1, VALUE_2)));
        categoricalAttributeManager.update(loadedAttrWithLink);

        final CategoricalAttribute loadedAttrWithoutLink = categoricalAttributeManager.loadByNameOrId(KEY_2);
        assertThat(loadedAttrWithoutLink.getValues(), hasSize(1));
        final List<CategoricalAttributeValue> links = loadedAttrWithoutLink.getValues().get(0).getLinks();
        assertThat(links, hasSize(1));
        assertThat(links.get(0).getKey(), equalTo(KEY_1));
        assertThat(links.get(0).getValue(), equalTo(VALUE_2));
    }

    @Test
    public void shouldUpdateOwner() {
        final CategoricalAttribute attributeToCreate =
            new CategoricalAttribute(KEY_1, fromStrings(KEY_1, Arrays.asList(VALUE_1, VALUE_2)));
        attributeToCreate.setOwner(OWNER_1);
        categoricalAttributeManager.create(attributeToCreate);
        final CategoricalAttribute createdAttribute =
            categoricalAttributeManager.loadByNameOrId(attributeToCreate.getName());
        Assert.assertEquals(OWNER_1, createdAttribute.getOwner());
        categoricalAttributeManager.changeOwner(createdAttribute.getId(), OWNER_2);
        final CategoricalAttribute updatedAttribute = categoricalAttributeManager.load(createdAttribute.getId());
        Assert.assertEquals(OWNER_2, updatedAttribute.getOwner());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionOnAttributeNullNameKey() {
        final CategoricalAttribute attributeWithNullKeyAndName =
            new CategoricalAttribute(null, fromStrings(KEY_1, Arrays.asList(VALUE_1, VALUE_2)));
        categoricalAttributeManager.create(attributeWithNullKeyAndName);
    }
}
