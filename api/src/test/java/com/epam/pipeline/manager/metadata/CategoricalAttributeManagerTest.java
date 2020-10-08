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

package com.epam.pipeline.manager.metadata;

import static com.epam.pipeline.util.CategoricalAttributeTestUtils.assertAttribute;
import static com.epam.pipeline.util.CategoricalAttributeTestUtils.fromStrings;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.MetadataVO;
import com.epam.pipeline.entity.metadata.CategoricalAttribute;
import com.epam.pipeline.entity.metadata.CategoricalAttributeValue;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.user.UserManager;
import org.apache.commons.collections4.CollectionUtils;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Transactional
public class CategoricalAttributeManagerTest extends AbstractSpringTest {

    private static final String KEY_1 = "key1";
    private static final String KEY_2 = "key2";
    private static final String INVALID_KEY = "invalid_key";
    private static final String SENSITIVE_KEY = "sensitive_metadata_key";
    private static final String VALUE_1 = "valueA";
    private static final String VALUE_2 = "valueB";
    private static final String VALUE_3 = "valueC";
    private static final String TYPE = "string";
    private static final String TEST_USER = "TEST_USER";

    @Autowired
    private CategoricalAttributeManager categoricalAttributeManager;

    @Autowired
    private MetadataManager metadataManager;

    @Autowired
    private UserManager userManager;

    @Autowired
    private PreferenceManager preferenceManager;

    @Test
    public void syncWithMetadata() {
        preferenceManager.update(Collections.singletonList(new Preference(
                SystemPreferences.MISC_METADATA_SENSITIVE_KEYS.getKey(),
                String.format("[\"%s\"]", SENSITIVE_KEY))));
        Assert.assertEquals(0, categoricalAttributeManager.loadAll().size());

        final PipelineUser testUser = userManager
            .createUser(TEST_USER, Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), null);
        final EntityVO entityVO = new EntityVO(testUser.getId(), AclClass.PIPELINE_USER);
        final Map<String, PipeConfValue> data = new HashMap<>();
        data.put(KEY_1, new PipeConfValue(TYPE, VALUE_1));
        data.put(KEY_2, new PipeConfValue(TYPE, VALUE_2));
        data.put(SENSITIVE_KEY, new PipeConfValue(TYPE, VALUE_2));
        final MetadataVO metadataVO = new MetadataVO();
        metadataVO.setEntity(entityVO);
        metadataVO.setData(data);
        metadataManager.updateMetadataItem(metadataVO);
        categoricalAttributeManager.syncWithMetadata();

        final Map<String, List<String>> categoricalAttributesAfterSync = categoricalAttributeManager.loadAll().stream()
            .collect(Collectors.toMap(CategoricalAttribute::getKey,
                attribute -> attribute.getValues().stream()
                                          .map(CategoricalAttributeValue::getValue)
                                          .collect(Collectors.toList())));
        Assert.assertEquals(2, categoricalAttributesAfterSync.size());
        Assert.assertThat(categoricalAttributesAfterSync.get(KEY_1),
                          CoreMatchers.is(Collections.singletonList(VALUE_1)));
        Assert.assertThat(categoricalAttributesAfterSync.get(KEY_2),
                          CoreMatchers.is(Collections.singletonList(VALUE_2)));
        Assert.assertFalse(categoricalAttributesAfterSync.containsKey(SENSITIVE_KEY));
    }

    @Test
    public void testLinkCreationAndCleanup() {
        final List<CategoricalAttributeValue> valuesWithoutLinks =
            fromStrings(KEY_1, Arrays.asList(VALUE_1, VALUE_2));
        final CategoricalAttributeValue valueWithLink = new CategoricalAttributeValue(KEY_2, VALUE_3);
        valueWithLink.setLinks(Collections.singletonList(new CategoricalAttributeValue(KEY_1, VALUE_1)));
        final List<CategoricalAttribute> attributes =
            Arrays.asList(new CategoricalAttribute(KEY_1, valuesWithoutLinks),
                          new CategoricalAttribute(KEY_2, Collections.singletonList(valueWithLink)));

        categoricalAttributeManager.updateCategoricalAttributes(attributes);

        final Map<String, List<CategoricalAttributeValue>> attributeMap = categoricalAttributeManager.loadAll().stream()
            .collect(Collectors.toMap(CategoricalAttribute::getKey, CategoricalAttribute::getValues));
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
                .collect(Collectors.toMap(CategoricalAttribute::getKey, CategoricalAttribute::getValues));
        Assert.assertTrue(attributeMapAfterDelete.values().stream()
                              .flatMap(Collection::stream)
                              .map(CategoricalAttributeValue::getLinks)
                              .allMatch(CollectionUtils::isEmpty));
    }

    @Test(expected = IllegalArgumentException.class)
    public void loadValuesForNonExistentKey() {
        categoricalAttributeManager.loadAllValuesForKey(INVALID_KEY);
    }

    @Test
    public void testUpdateAttributesValues() {
        final List<CategoricalAttribute> values = new ArrayList<>();
        values.add(new CategoricalAttribute(KEY_1, fromStrings(KEY_1, Arrays.asList(VALUE_1, VALUE_2))));
        Assert.assertTrue(categoricalAttributeManager.updateCategoricalAttributes(values));
        final List<CategoricalAttribute> attributes = categoricalAttributeManager.loadAll();
        Assert.assertEquals(1, attributes.size());
        assertAttribute(attributes.get(0), KEY_1, VALUE_1, VALUE_2);

        final List<CategoricalAttribute> valuesToReplace = new ArrayList<>();
        valuesToReplace.add(new CategoricalAttribute(KEY_1, fromStrings(KEY_1, Collections.singletonList(VALUE_3))));
        Assert.assertTrue(categoricalAttributeManager.updateCategoricalAttributes(valuesToReplace));
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
        categoricalAttributeManager.updateCategoricalAttributes(attributes);
    }
}
