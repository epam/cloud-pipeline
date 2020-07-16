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

import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.epam.pipeline.config.JsonMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

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
                        "\"type\" : \"string\"" +
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
        PipelineConfiguration pipelineConfiguration = mapper.readValue(
                WITH_TYPE_OF_PARAMS_JSON, PipelineConfiguration.class
        );

        Assert.assertTrue(
                pipelineConfiguration
                        .getParameters()
                        .values()
                        .stream()
                        .allMatch(v -> v.getType() != null)
        );

        Assert.assertEquals(STRING_TYPE, pipelineConfiguration.getParameters().get("main_file").getType());
        Assert.assertEquals(true, pipelineConfiguration.getParameters().get("main_file").isRequired());

        Assert.assertEquals(CLASS_TYPE, pipelineConfiguration.getParameters().get("main_class").getType());
        Assert.assertEquals(false, pipelineConfiguration.getParameters().get("main_class").isRequired());

        Assert.assertEquals(STRING_TYPE, pipelineConfiguration.getParameters().get("instance_size").getType());
        Assert.assertEquals(false, pipelineConfiguration.getParameters().get("instance_size").isRequired());
        Assert.assertEquals(EMPTY, pipelineConfiguration.getParameters().get("instance_size").getValue());

        Assert.assertEquals(STRING_TYPE, pipelineConfiguration.getParameters().get("instance_disk").getType());
        Assert.assertEquals(false, pipelineConfiguration.getParameters().get("instance_disk").isRequired());
        Assert.assertEquals(EXP_INSTANCE_DISK, pipelineConfiguration.getParameters().get("instance_disk").getValue());

    }

}
