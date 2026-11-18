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
package com.epam.pipeline.autotests.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static java.lang.String.format;

/**
 * The settings of a tool's "latest" version, the one its runs with default settings and its launch form take their
 * defaults from (CP_CAP_LIMIT_MOUNTS among them), read and written back through the API ({@link PlatformApi}: as the
 * user of the GUI tab, which must show a platform page, and be an admin who is not impersonating). A test that changes
 * them in the tool's settings saves them first and puts them back as they were: the page's own fields can still
 * show the defaults of a tool whose version settings have not loaded yet.
 */
public final class ToolVersionSettings {

    private static final String VERSION = "latest";

    private ToolVersionSettings() {
    }

    /**
     * The tool's id, and the "latest" version's settings and allowCommit flag (no settings if the version has none).
     *
     * @param image the tool as group/name
     */
    public static JsonNode save(final String image) {
        final long toolId = PlatformApi.get(format("tool/load?image=%s", image)).path("id").asLong();
        final ObjectNode saved = JsonNodeFactory.instance.objectNode();
        saved.put("toolId", toolId);
        saved.put("allowCommit", true);
        saved.putArray("settings");
        for (final JsonNode version : PlatformApi.get(format("tool/%d/settings?version=%s", toolId, VERSION))) {
            if (VERSION.equals(version.path("version").asText())) {
                saved.put("allowCommit", !version.has("allowCommit") || version.path("allowCommit").asBoolean(true));
                if (version.path("settings").isArray()) {
                    saved.set("settings", version.path("settings"));
                }
            }
        }
        return saved;
    }

    /**
     * Writes back what {@link #save(String)} returned. Nothing happens for null (the settings were not saved).
     */
    public static void restore(final JsonNode saved) {
        if (saved == null) {
            return;
        }
        PlatformApi.call("POST", format("tool/%d/settings?version=%s&allowCommit=%s", saved.path("toolId").asLong(),
                VERSION, saved.path("allowCommit").asBoolean(true)), saved.path("settings").toString());
    }
}
