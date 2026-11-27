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

package com.epam.dockercompscan.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonMapperTest {

    private static final String MASTER_LABEL = "node-role.kubernetes.io/master";
    private static final String OS_LABEL = "kubernetes.io/os";
    private static final String LINUX = "linux";

    private final JsonMapper mapper = new JsonMapper();

    @Test
    public void shouldKeepMapEntriesWithEmptyValues() throws JsonProcessingException {
        final Map<String, Object> labels = new LinkedHashMap<>();
        labels.put(MASTER_LABEL, "");
        labels.put("empty-list", Collections.emptyList());
        labels.put(OS_LABEL, LINUX);

        assertEquals("{\"labels\":{\"node-role.kubernetes.io/master\":\"\",\"empty-list\":[],"
                        + "\"kubernetes.io/os\":\"linux\"}}",
                mapper.writeValueAsString(new Node(null, Collections.emptyList(), labels)));
    }

    @Test
    public void shouldSkipMapEntriesWithNullValues() throws JsonProcessingException {
        final Map<String, Object> labels = new LinkedHashMap<>();
        labels.put(MASTER_LABEL, null);
        labels.put(OS_LABEL, LINUX);

        assertEquals("{\"labels\":{\"kubernetes.io/os\":\"linux\"}}",
                mapper.writeValueAsString(new Node(null, Collections.emptyList(), labels)));
    }

    @Test
    public void shouldSkipNullAndEmptyProperties() throws JsonProcessingException {
        assertEquals("{\"name\":\"node\"}",
                mapper.writeValueAsString(new Node("node", Collections.emptyList(), Collections.emptyMap())));
        assertEquals("{}", mapper.writeValueAsString(new Node("", null, null)));
    }

    private static final class Node {

        private final String name;
        private final List<String> roles;
        private final Map<String, Object> labels;

        private Node(final String name, final List<String> roles, final Map<String, Object> labels) {
            this.name = name;
            this.roles = roles;
            this.labels = labels;
        }

        public String getName() {
            return name;
        }

        public List<String> getRoles() {
            return roles;
        }

        public Map<String, Object> getLabels() {
            return labels;
        }
    }
}
