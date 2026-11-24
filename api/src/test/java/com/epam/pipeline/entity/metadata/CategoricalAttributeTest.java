/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.metadata;

import com.epam.pipeline.config.JsonMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CategoricalAttributeTest {

    private static final String KEY = "Group";
    private static final String VALUE = "A";

    @Test
    public void shouldDeserializeTheAttributesOfAnUpdateRequest() throws Exception {
        final List<CategoricalAttribute> attributes = new JsonMapper().readValue(
                "[{\"key\": \"Group\", \"values\": [{\"key\": \"Group\", \"value\": \"A\"}]}]",
                new TypeReference<List<CategoricalAttribute>>() {});

        assertThat(attributes).hasSize(1);
        assertThat(attributes.get(0).getKey()).isEqualTo(KEY);
        assertThat(attributes.get(0).getValues())
                .extracting(CategoricalAttributeValue::getValue)
                .containsExactly(VALUE);
    }
}
