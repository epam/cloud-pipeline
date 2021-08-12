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

package com.epam.pipeline.util;

import static org.hamcrest.Matchers.containsInAnyOrder;

import com.epam.pipeline.entity.metadata.CategoricalAttribute;
import com.epam.pipeline.entity.metadata.CategoricalAttributeValue;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Assert;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CategoricalAttributeTestUtils {

    private CategoricalAttributeTestUtils() {
    }

    public static void assertAttribute(final CategoricalAttribute attributeAfter, final String key,
                                       final String... values) {
        Assert.assertEquals(key, attributeAfter.getName());
        final CategoricalAttributeValue[] attributeValues = Stream.of(values)
            .map(v -> new CategoricalAttributeValue(key, v))
            .toArray(CategoricalAttributeValue[]::new);
        Assert.assertThat(attributeAfter.getValues(), containsInAnyOrder(attributeValues));
    }

    public static Map<String, List<String>> convertToMap(final Collection<CategoricalAttribute> attributes) {
        return attributes.stream()
            .collect(Collectors.toMap(CategoricalAttribute::getName,
                attribute -> CollectionUtils.emptyIfNull(attribute.getValues()).stream()
                    .map(CategoricalAttributeValue::getValue)
                    .collect(Collectors.toList())));
    }

    public static void assertValuesPresentedForKeyInMap(final Map<String, List<String>> attributesWithValues,
                                                        final String key,
                                                        final String... values) {
        final List<String> valuesForKey = attributesWithValues.get(key);
        Assert.assertEquals(values.length, valuesForKey.size());
        Assert.assertThat(valuesForKey, containsInAnyOrder(values));
    }

    public static List<CategoricalAttributeValue> fromStrings(final String key, final List<String> strings) {
        return strings.stream()
            .map(s -> new CategoricalAttributeValue(key, s))
            .collect(Collectors.toList());
    }

    public static Map<String, List<String>> extractAttributesContent(final List<CategoricalAttribute> attributes) {
        return attributes.stream().collect(Collectors.toMap(CategoricalAttribute::getName, attribute ->
            attribute.getValues().stream()
                .map(CategoricalAttributeValue::getValue)
                .collect(Collectors.toList())));
    }
}
