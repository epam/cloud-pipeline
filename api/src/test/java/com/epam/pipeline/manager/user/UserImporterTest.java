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

package com.epam.pipeline.manager.user;

import com.epam.pipeline.entity.metadata.CategoricalAttribute;
import com.epam.pipeline.entity.metadata.CategoricalAttributeValue;
import com.epam.pipeline.entity.user.PipelineUserEvent;
import com.epam.pipeline.entity.user.PipelineUserWithStoragePath;
import com.epam.pipeline.entity.user.Role;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class UserImporterTest {
    private static final String HEADER = "UserName,Groups,Key1,Key2,Key3,Key4\n";
    private static final String CONTENT = HEADER + "user1,test1|test2,Value1,Value2,Value3,Value4";
    private static final String EMPTY_USER_CONTENT = HEADER + ",test1|test2,Value1,Value2,Value3,Value4";
    private static final String EMPTY_CONTENT = HEADER + "user1,,,,,";
    private static final String TEST_USER = "USER1";
    private static final String TEST_ROLE_1 = "ROLE_TEST1";
    private static final String TEST_ROLE_2 = "ROLE_TEST2";
    private static final String KEY1 = "Key1";
    private static final String KEY2 = "Key2";
    private static final String KEY3 = "Key3";
    private static final String KEY4 = "Key4";
    private static final String VALUE1 = "Value1";
    private static final String VALUE2 = "Value2";
    private static final String VALUE3 = "Value3";

    @Test
    public void shouldParseCsvFile() {
        final MultipartFile file = getFile(CONTENT);
        final List<String> allowedMetadata = Arrays.asList(KEY1, KEY2);
        final List<PipelineUserEvent> events = new ArrayList<>();

        final List<CategoricalAttribute> attributes = new ArrayList<>();
        attributes.add(new CategoricalAttribute(KEY1, attributesList(KEY1, VALUE1)));
        attributes.add(new CategoricalAttribute(KEY2, attributesList(KEY2, VALUE1)));
        attributes.add(new CategoricalAttribute(KEY4, attributesList(KEY4, VALUE1)));

        final List<PipelineUserWithStoragePath> resultUsers =
                new UserImporter(attributes, allowedMetadata, events).importUsers(file);

        assertThat(resultUsers).hasSize(1);
        final PipelineUserWithStoragePath resultUser = resultUsers.get(0);
        assertThat(resultUser.getUserName()).isEqualTo(TEST_USER);
        assertThat(resultUser.getRoles().stream().map(Role::getName).collect(Collectors.toList()))
                .hasSize(2)
                .contains(TEST_ROLE_1)
                .contains(TEST_ROLE_2);
        assertThat(resultUser.getMetadata()).hasSize(3).containsKeys(KEY1, KEY2, KEY3);
        assertThat(resultUser.getMetadata().get(KEY1).getValue()).isEqualTo(VALUE1);
        assertThat(resultUser.getMetadata().get(KEY2).getValue()).isEqualTo(VALUE2);
        assertThat(resultUser.getMetadata().get(KEY3).getValue()).isEqualTo(VALUE3);
        assertThat(attributes).hasSize(3);
        assertThat(attributes.stream().map(CategoricalAttribute::getKey).collect(Collectors.toList()))
                .contains(KEY1, KEY2, KEY4);
        attributes.forEach(attribute -> {
            assertAttributeValue(attribute, KEY1, Collections.singletonList(VALUE1));
            assertAttributeValue(attribute, KEY2, Arrays.asList(VALUE1, VALUE2));
            assertAttributeValue(attribute, KEY4, Collections.singletonList(VALUE1));
        });
        assertThat(events).hasSize(2);
    }

    @Test
    public void shouldSkipUserIfNameNotFound() {
        final MultipartFile file = getFile(EMPTY_USER_CONTENT);
        final List<String> allowedMetadata = Arrays.asList(KEY1, KEY2);
        final ArrayList<CategoricalAttribute> attributes = new ArrayList<>();
        final List<PipelineUserEvent> events = new ArrayList<>();
        final UserImporter userImporter = new UserImporter(attributes, allowedMetadata, events);

        assertThat(userImporter.importUsers(file)).hasSize(0);
        assertThat(attributes).hasSize(0);
        assertThat(events).hasSize(0);
    }

    @Test
    public void shouldParseIfRolesAndMetadataEmpty() {
        final MultipartFile file = getFile(EMPTY_CONTENT);
        final List<String> allowedMetadata = Arrays.asList(KEY1, KEY2);
        final ArrayList<CategoricalAttribute> attributes = new ArrayList<>();
        final List<PipelineUserEvent> events = new ArrayList<>();

        final List<PipelineUserWithStoragePath> resultUsers =
                new UserImporter(attributes, allowedMetadata, events).importUsers(file);

        assertThat(resultUsers).hasSize(1);
        final PipelineUserWithStoragePath resultUser = resultUsers.get(0);
        assertThat(resultUser.getUserName()).isEqualTo(TEST_USER);
        assertThat(resultUser.getRoles()).hasSize(0);
        assertThat(resultUser.getMetadata()).hasSize(0);
        assertThat(attributes).hasSize(0);
        assertThat(events).hasSize(0);
    }

    private void assertAttributeValue(final CategoricalAttribute attribute,
                                      final String expectedKey, final List<String> expectedValues) {
        if (Objects.equals(attribute.getKey(), expectedKey)) {
            assertThat(attribute.getValues().stream()
                    .peek(attributeValue -> assertThat(attributeValue.getKey()).isEqualTo(expectedKey))
                    .map(CategoricalAttributeValue::getValue)
                    .collect(Collectors.toList()))
                    .hasSize(expectedValues.size())
                    .containsAll(expectedValues);
        }
    }

    private List<CategoricalAttributeValue> attributesList(final String key, final String value) {
        final CategoricalAttributeValue attributeValue = new CategoricalAttributeValue();
        attributeValue.setKey(key);
        attributeValue.setValue(value);
        final List<CategoricalAttributeValue> attributeValues = new ArrayList<>();
        attributeValues.add(attributeValue);
        return attributeValues;
    }

    private MultipartFile getFile(final String content) {
        return new MockMultipartFile(content, content.getBytes());
    }
}
