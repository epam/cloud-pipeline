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

package com.epam.pipeline.entity.cluster.monitoring;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class ContainerStatsDeserialization extends JsonDeserializer<RawContainerStats> {

    @Override
    public RawContainerStats deserialize(final JsonParser p,
                                         final DeserializationContext ctxt)
            throws IOException {
        final JsonNode treeNode = p.readValueAsTree();
        final Iterator<String> containers = treeNode.fieldNames();
        if (!containers.hasNext()) {
            return null;
        }
        //only first container required
        final JsonNode containerStats = treeNode.get(containers.next());
        final JsonParser child = containerStats.traverse();
        child.setCodec(p.getCodec());

        final RawContainerStats stats = new RawContainerStats();
        stats.setStats(child
                .readValueAs(new TypeReference<List<RawMonitoringStats.RawStats>>() {}));
        //create dummy spec for container
        final RawMonitoringStats.RawSpec spec = new RawMonitoringStats.RawSpec();
        spec.setHasCpu(true);
        spec.setHasFilesystem(true);
        spec.setHasMemory(true);
        spec.setHasNetwork(false);
        stats.setSpec(spec);
        return stats;
    }
}
