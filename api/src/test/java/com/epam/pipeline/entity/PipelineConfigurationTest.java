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

package com.epam.pipeline.entity;

import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.epam.pipeline.config.JsonMapper;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class PipelineConfigurationTest {

    private static final String WITH_TYPE_OF_PARAMS_JSON =
            "{" +
                "\"parameters\": {" +
                    "\"main_file\" : {" +
                        "\"value\" : \"\"," +
                        "\"required\" : \"true\"," +
                        "\"type\" : \"string\"," +
                        "\"enum\" : [{\"name\": \"v1\"}, {\"name\": \"v2\"}]" +
                    "}," +
                    "\"main_class\" : {" +
                        "\"value\" : \"\"," +
                        "\"required\" : \"false\"," +
                        "\"type\" : \"class\"," +
                        "\"enum\" : [\"v1\", \"v2\"]" +
                    "}," +
                    "\"instance_size\" : {" +
                        "\"type\" : \"string\"," +
                        "\"validation\": [{\"throw\":\"a == a\", \"message\": \"error\"}]" +
                    "}," +
                    "\"instance_disk\" : \"200\"" +
                "}" +
            "}";

    public static final String EXP_INSTANCE_DISK = "200";
    private static final String STRING_TYPE = "string";
    private static final String CLASS_TYPE = "class";
    private static final String EMPTY = "";

    private JsonMapper mapper = new JsonMapper();

    @Before
    public void setup() {
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                .configure(SerializationFeature.WRITE_NULL_MAP_VALUES, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Test
    public void parsingConfigurationJsonTest() throws IOException {
        final PipelineConfiguration pipelineConfiguration = mapper.readValue(
                WITH_TYPE_OF_PARAMS_JSON, PipelineConfiguration.class
        );

        assertTrue(
                pipelineConfiguration
                        .getParameters()
                        .values()
                        .stream()
                        .allMatch(v -> v.getType() != null)
        );

        final PipeConfValueVO mainFile = pipelineConfiguration.getParameters().get("main_file");
        assertEquals(STRING_TYPE, mainFile.getType());
        assertTrue(mainFile.isRequired());

        final PipeConfValueVO mainClass = pipelineConfiguration.getParameters().get("main_class");
        assertEquals(CLASS_TYPE, mainClass.getType());
        assertFalse(mainClass.isRequired());

        final PipeConfValueVO instanceSize = pipelineConfiguration.getParameters().get("instance_size");
        assertEquals(STRING_TYPE, instanceSize.getType());
        assertFalse(instanceSize.isRequired());
        assertEquals(EMPTY, instanceSize.getValue());
        assertEquals(1, instanceSize.getValidation().size());

        final PipeConfValueVO instanceDisk = pipelineConfiguration.getParameters().get("instance_disk");
        assertEquals(STRING_TYPE, instanceDisk.getType());
        assertFalse(instanceDisk.isRequired());
        assertEquals(EXP_INSTANCE_DISK, instanceDisk.getValue());
    }
}
