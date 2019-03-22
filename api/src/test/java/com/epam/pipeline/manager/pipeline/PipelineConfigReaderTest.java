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

package com.epam.pipeline.manager.pipeline;

import static org.junit.Assert.assertEquals;

import java.util.List;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.configuration.ConfigurationEntry;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.Test;

public class PipelineConfigReaderTest {

    private static final JsonMapper MAPPER = new JsonMapper();

    static {
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                .configure(SerializationFeature.WRITE_NULL_MAP_VALUES, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    private static final String CONFIGURATION_NAME = "config";
    private static final String OBJECT_JSON = "{\"main_file\" : \"test.sh\","
            + String.format("\"name\" : \"%s\",", CONFIGURATION_NAME)
            + "\"instance_size\" : \"m4.large\","
            + "\"instance_disk\" : \"20\","
            + "\"cmd_template\" : \"echo ${p1}\","
            + "\"parameters\": "
            + "{\"p1\" : {\"value\" : \"value1\",\"type\" : \"path\",\"required\": true}}}";
    private static final String ARRAY_JSON = String.format("[%s, %s]", OBJECT_JSON, OBJECT_JSON);

    @Test
    public void readConfigurations() throws Exception {
        List<ConfigurationEntry> configurations =
                new PipelineConfigReader().readConfigurations(ARRAY_JSON, MAPPER);
        assertEquals(2, configurations.size());
        configurations.forEach(conf -> assertEquals(CONFIGURATION_NAME, conf.getName()));
    }

    @Test
    public void readConfiguration() throws Exception {
        List<ConfigurationEntry> configurations =
                new PipelineConfigReader().readConfigurations(OBJECT_JSON, MAPPER);
        assertEquals(1, configurations.size());
        configurations.forEach(conf -> assertEquals("default", conf.getName()));
    }

}
