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

package com.epam.pipeline.entity.configuration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PipelineConfValuesMapDeserializer extends JsonDeserializer<Map<String, PipeConfValueVO>> {

    private static final String VALUE_FIELD = "value";
    private static final String TYPE_FIELD = "type";
    private static final String REQUIRED_FIELD = "required";
    private static final String ENUM_FIELD = "enum";
    private static final String DESCRIPTION_FIELD = "description";
    private static final  NullNode NULL_NODE = NullNode.getInstance();



    @Override
    public Map<String, PipeConfValueVO> deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
        Map<String, PipeConfValueVO> parameters = new LinkedHashMap<>();
        JsonNode treeNode = p.readValueAsTree();
        Iterator<String> childNames = treeNode.fieldNames();
        while (childNames.hasNext()) {
            String name = childNames.next();
            JsonNode child = treeNode.get(name);
            PipeConfValueVO parameter = new PipeConfValueVO();
            if (child.isValueNode()) {
                parameter.setValue(child.asText().trim());
            } else {
                JsonNode value = child.get(VALUE_FIELD);
                if (hasValue(value)) {
                    parameter.setValue(value.asText().trim());
                }
                JsonNode type = child.get(TYPE_FIELD);
                if (hasValue(type)) {
                    parameter.setType(type.asText());
                }
                JsonNode required = child.get(REQUIRED_FIELD);
                if (hasValue(required)) {
                    parameter.setRequired(required.asBoolean());
                }
                JsonNode availableValuesNode = child.get(ENUM_FIELD);
                if (hasValue(availableValuesNode) && availableValuesNode.isArray()) {
                    List<String> availableValues = new ArrayList<>();
                    availableValuesNode.forEach(arrayItem -> availableValues.add(arrayItem.asText()));
                    parameter.setAvailableValues(availableValues);
                }
                final JsonNode description = child.get(DESCRIPTION_FIELD);
                if (hasValue(description)) {
                    parameter.setValue(description.asText());
                }
            }
            parameters.put(name, parameter);
        }
        return parameters;
    }

    private boolean hasValue(final JsonNode required) {
        return required != null && !required.equals(NULL_NODE);
    }
}
