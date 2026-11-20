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

package com.epam.pipeline.entity.region;

import com.epam.pipeline.config.JsonMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CustomInstanceTypeTest {

    private static final int CPU = 2;
    private static final double RAM = 8.0;
    private static final int GPU = 1;
    private static final String GPU_TYPE = "T4";

    @Test
    public void shouldDeserializeFromRegionJson() {
        final String json = "[{\"cpu\": 2, \"ram\": 8.0, \"gpu\": 1, \"gpuType\": \"T4\"}, {\"cpu\": 2, \"ram\": 8.0}]";

        final List<CustomInstanceType> types = JsonMapper.parseData(json,
                new TypeReference<List<CustomInstanceType>>() {}, new JsonMapper());

        assertThat(types.size(), is(2));
        assertThat(types.get(0), is(CustomInstanceType.withGpu(CPU, RAM, GPU, GPU_TYPE)));
        assertThat(types.get(1), is(CustomInstanceType.withCpu(CPU, RAM)));
    }
}
