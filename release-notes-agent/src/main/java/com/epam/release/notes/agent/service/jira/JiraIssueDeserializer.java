/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.release.notes.agent.service.jira;

import com.epam.release.notes.agent.entity.jira.JiraIssueVO;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.EMPTY;

public class JiraIssueDeserializer extends StdDeserializer<JiraIssueVO> {

    private static final String ID = "id";
    private static final String KEY = "key";
    private static final String FIELDS = "fields";

    protected JiraIssueDeserializer() {
        this(null);
    }

    protected JiraIssueDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public JiraIssueVO deserialize(final JsonParser p, final DeserializationContext ctxt) throws IOException {
        final JsonNode node = p.getCodec().readTree(p);
        JiraIssueVO.JiraIssueVOBuilder jiraIssueBuilder = JiraIssueVO.builder()
                .id(Optional.ofNullable(node.get(ID).asText()).orElse(EMPTY))
                .key(Optional.ofNullable(node.get(KEY).asText()).orElse(EMPTY));
        final JsonNode jsonFieldNode = node.get(FIELDS);
        final Iterator<String> fieldNames = jsonFieldNode.fieldNames();
        final Map<String, String> fields = new HashMap<>();
        while (fieldNames.hasNext()) {
            final String key = fieldNames.next();
            fields.put(key, Optional.ofNullable(jsonFieldNode.get(key).asText()).orElse(EMPTY));
        }
        return jiraIssueBuilder.fields(fields).build();
    }
}
