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

package com.epam.pipeline.entity.model;

import com.epam.pipeline.config.JsonMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class ModelParametersSerializationAndDeserializationTest {

    private static final String MODEL_PARAMETERS_JSON =
            "{" +
                    "\"type\": \"MODEL\"," +
                    "\"inputs\": [{" +
                            "\"name\": \"input_name\"," +
                            "\"type\": \"input_type\"" +
                    "}]," +
                    "\"outputColumn\": \"output_column\"," +
                    "\"values\": [{" +
                            "\"name\": \"value_name\"," +
                            "\"type\": \"value_type\"" +
                    "}]" +
            "}";
    private static final String PROTOCOL_PARAMETERS_JSON =
            "{" +
                    "\"type\": \"PROTOCOL\"," +
                    "\"inputs\": [{" +
                            "\"name\": \"input_name\"," +
                            "\"type\": \"input_type\"" +
                    "}]," +
                    "\"outputs\": [{" +
                    "\"name\": \"output_name\"," +
                    "\"type\": \"output_type\"" +
                    "}]" +
            "}";
    private static final RModelStepParameters MODEL_PARAMETERS = new RModelStepParameters();
    private static final ProtocolStepParameters PROTOCOL_PARAMETERS = new ProtocolStepParameters();

    static {
        MODEL_PARAMETERS.setType(ModelStepType.MODEL);
        MODEL_PARAMETERS.setInputs(Collections.singletonList(
                InputParameter.builder().name("input_name").type("input_type").build()
        ));
        MODEL_PARAMETERS.setOutputs(Collections.singletonList(
                ConfValue.builder().name("output_name").type("output_type").build()
        ));
        MODEL_PARAMETERS.setOutputColumn("output_column");
        MODEL_PARAMETERS.setValues(Collections.singletonList(
                ConfValue.builder().name("value_name").type("value_type").build()
        ));

        PROTOCOL_PARAMETERS.setType(ModelStepType.PROTOCOL);
        PROTOCOL_PARAMETERS.setInputs(Collections.singletonList(
                InputParameter.builder().name("input_name").type("input_type").build()
        ));
        PROTOCOL_PARAMETERS.setOutputs(Collections.singletonList(
                ConfValue.builder().name("output_name").type("output_type").build()
        ));
    }

    @BeforeClass
    public static void setUp() {
        new JsonMapper().init();
    }

    @Test
    public void deserializationShouldWorkForModelParametersClass() {
        final AbstractStepParameters deserializedModelParameters = JsonMapper.parseData(MODEL_PARAMETERS_JSON,
                new TypeReference<AbstractStepParameters>() {});

        assertThat(deserializedModelParameters, instanceOf(RModelStepParameters.class));
        assertThat(deserializedModelParameters, is(MODEL_PARAMETERS));
    }

    @Test
    public void serializationShouldWorkForModelParametersClass() throws IOException {
        final Path tempFile = Files.createTempFile("modelParametersSerialization", "test");
        try {
            JsonMapper.writeData(MODEL_PARAMETERS, tempFile.toFile());
            final String serializedModelParameters = FileUtils.readFileToString(tempFile.toFile(),
                    Charset.defaultCharset());
            final AbstractStepParameters deserializedModelParameters = JsonMapper.parseData(serializedModelParameters,
                    new TypeReference<AbstractStepParameters>() {});

            assertThat(deserializedModelParameters, instanceOf(RModelStepParameters.class));
            assertThat(deserializedModelParameters, is(MODEL_PARAMETERS));
        } finally {
            Files.delete(tempFile);
        }
    }

    @Test
    public void deserializationShouldWorkForProtocolParametersClass() {
        final AbstractStepParameters deserializedModelParameters = JsonMapper.parseData(PROTOCOL_PARAMETERS_JSON,
                new TypeReference<AbstractStepParameters>() {});

        assertThat(deserializedModelParameters, instanceOf(ProtocolStepParameters.class));
        assertThat(deserializedModelParameters, is(PROTOCOL_PARAMETERS));
    }

    @Test
    public void serializationShouldWorkForProtocolParametersClass() throws IOException {
        final Path tempFile = Files.createTempFile("protocolParametersSerialization", "test");
        try {
            JsonMapper.writeData(PROTOCOL_PARAMETERS, tempFile.toFile());
            final String serializedModelParameters = FileUtils.readFileToString(tempFile.toFile(),
                    Charset.defaultCharset());
            final AbstractStepParameters deserializedModelParameters = JsonMapper.parseData(serializedModelParameters,
                    new TypeReference<AbstractStepParameters>() {});

            assertThat(deserializedModelParameters, instanceOf(ProtocolStepParameters.class));
            assertThat(deserializedModelParameters, is(PROTOCOL_PARAMETERS));
        } finally {
            Files.delete(tempFile);
        }
    }
}
