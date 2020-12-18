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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class UserImporterTest {
    private static final String HEADER = "UserName,Groups,Key1,Key2,Key3\n";
    private static final String CONTENT = HEADER + "user1,test1|test2,Value1,Value2,Value3";
    private static final String EMPTY_USER_CONTENT = HEADER + ",test1|test2,Value1,Value2,Value3";
    private static final String EMPTY_CONTENT = HEADER + "user1,,,,";
    private static final String TEST_USER = "USER1";
    private static final String TEST_ROLE_1 = "ROLE_TEST1";
    private static final String TEST_ROLE_2 = "ROLE_TEST2";
    private static final String KEY1 = "Key1";
    private static final String KEY2 = "Key2";
    private static final String VALUE1 = "Value1";
    private static final String VALUE2 = "Value2";

    @Test
    public void shouldParseCsvFile() {
        final MultipartFile file = getFile(CONTENT);
        final List<String> allowedMetadata = Arrays.asList(KEY1, KEY2);
        final ArrayList<CategoricalAttribute> attributes = new ArrayList<>();
        final ArrayList<PipelineUserEvent> events = new ArrayList<>();

        final List<PipelineUserWithStoragePath> resultUsers =
                new UserImporter(events, attributes, allowedMetadata).importUsers(file);

        assertThat(resultUsers).hasSize(1);
        final PipelineUserWithStoragePath resultUser = resultUsers.get(0);
        assertThat(resultUser.getUserName()).isEqualTo(TEST_USER);
        assertThat(resultUser.getRoles().stream().map(Role::getName).collect(Collectors.toList()))
                .hasSize(2)
                .contains(TEST_ROLE_1)
                .contains(TEST_ROLE_2);
        assertThat(resultUser.getMetadata()).hasSize(2).containsKeys(KEY1, KEY2);
        assertThat(resultUser.getMetadata().get(KEY1).getValue()).isEqualTo(VALUE1);
        assertThat(resultUser.getMetadata().get(KEY2).getValue()).isEqualTo(VALUE2);
        assertThat(attributes).hasSize(2);
        assertThat(attributes.stream().map(CategoricalAttribute::getKey).collect(Collectors.toList()))
                .contains(KEY1, KEY2);
        attributes.forEach(attribute -> {
            assertThat(attribute.getValues()).hasSize(1);
            assertAttributeValue(attribute, KEY1, VALUE1);
            assertAttributeValue(attribute, KEY2, VALUE2);
        });
        assertThat(events).hasSize(2);
    }

    @Test
    public void shouldSkipUserIfNameNotFound() {
        final MultipartFile file = getFile(EMPTY_USER_CONTENT);
        final List<String> allowedMetadata = Arrays.asList(KEY1, KEY2);
        final ArrayList<CategoricalAttribute> attributes = new ArrayList<>();
        final ArrayList<PipelineUserEvent> events = new ArrayList<>();
        final UserImporter userImporter = new UserImporter(events, attributes, allowedMetadata);

        assertThat(userImporter.importUsers(file)).hasSize(0);
        assertThat(attributes).hasSize(0);
        assertThat(events).hasSize(0);
    }

    @Test
    public void shouldParseIfRolesAndMetadataEmpty() {
        final MultipartFile file = getFile(EMPTY_CONTENT);
        final List<String> allowedMetadata = Arrays.asList(KEY1, KEY2);
        final ArrayList<CategoricalAttribute> attributes = new ArrayList<>();
        final ArrayList<PipelineUserEvent> events = new ArrayList<>();

        final List<PipelineUserWithStoragePath> resultUsers =
                new UserImporter(events, attributes, allowedMetadata).importUsers(file);

        assertThat(resultUsers).hasSize(1);
        final PipelineUserWithStoragePath resultUser = resultUsers.get(0);
        assertThat(resultUser.getUserName()).isEqualTo(TEST_USER);
        assertThat(resultUser.getRoles()).hasSize(0);
        assertThat(resultUser.getMetadata()).hasSize(0);
        assertThat(attributes).hasSize(0);
        assertThat(events).hasSize(0);
    }

    private void assertAttributeValue(final CategoricalAttribute attribute,
                                      final String expectedKey, final String expectedValue) {
        if (Objects.equals(attribute.getKey(), expectedKey)) {
            final CategoricalAttributeValue attributeValue = attribute.getValues().get(0);
            assertThat(attributeValue.getKey()).isEqualTo(expectedKey);
            assertThat(attributeValue.getValue()).isEqualTo(expectedValue);
        }
    }

    private MultipartFile getFile(final String content) {
        return new MockMultipartFile(content, content.getBytes());
    }
}
