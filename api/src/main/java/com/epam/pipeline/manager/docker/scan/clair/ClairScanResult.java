/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.docker.scan.clair;

import java.util.List;
import java.util.Map;

import com.epam.pipeline.entity.scan.VulnerabilitySeverity;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClairScanResult {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        OBJECT_MAPPER.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
    }

    private String name;
    private String namespaceName;
    private List<ClairFeature> features;

    @JsonProperty("Layer")
    @SuppressWarnings("unchecked") //TODO: try with a JsonRootElement
    public void unpackNestedLayer(Map<String, Object> layer) {
        name = layer.get("Name").toString();
        namespaceName = layer.get("NamespaceName").toString();
        features = OBJECT_MAPPER.convertValue(layer.get("Features"), OBJECT_MAPPER.getTypeFactory()
            .constructParametricType(List.class, ClairFeature.class));
    }

    @Getter
    @Setter
    public static class ClairFeature {
        private String name;
        private String namespaceName;
        private String versionFormat;
        private String version;
        private List<ClairVulnerability> vulnerabilities;
    }

    @Getter
    @Setter
    public static class ClairVulnerability {
        private String name;
        private String namespaceName;
        private String description;
        private String link;
        private VulnerabilitySeverity severity;
        private String fixedBy;
    }
}
