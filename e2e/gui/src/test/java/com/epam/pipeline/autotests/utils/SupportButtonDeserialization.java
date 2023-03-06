/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.autotests.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class SupportButtonDeserialization extends StdDeserializer<SupportButton> {

    public SupportButtonDeserialization() {
        this(null);
    }

    protected SupportButtonDeserialization(Class<?> vc) {
        super(vc);
    }

    @Override
    public SupportButton deserialize(final JsonParser p, final DeserializationContext ctxt)
            throws IOException {
        final JsonNode node = p.getCodec().readTree(p);
        final List<SupportButton.Icon> icons = new LinkedList<>();
        node.fields().forEachRemaining(n -> n.getValue().fields().forEachRemaining(f -> {
            final SupportButton.Icon icon = new SupportButton.Icon();
            icon.setIcon(f.getValue().get("icon").asText());
            icon.setName(f.getKey());
            final String content = f.getValue().get("content").asText().replaceAll("<br>", "\n");
            icon.setContent(content);
            icons.add(icon);
        }));
        final SupportButton supportButton = new SupportButton();
        supportButton.setIcons(icons);
        return supportButton;
    }
}
