/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.codeborne.selenide.Selenide;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static org.testng.Assert.assertEquals;

/**
 * Calls the platform API from the GUI tab, with its session: the GUI's own calls also authenticate by its cookies.
 * The tab must show a platform page (e.g. after {@code open(C.ROOT_ADDRESS)}), not an SSH terminal, which is served
 * from the edge address.
 */
public final class PlatformApi {

    private static final ObjectMapper JSON = new ObjectMapper();

    private PlatformApi() {
    }

    /**
     * Returns the response of {@code <root address>/restapi/<path>}, which must have the "OK" status.
     */
    public static JsonNode call(final String method, final String path, final String body) {
        final String url = C.ROOT_ADDRESS.replaceAll("/+$", "") + "/restapi/" + path;
        final Object response = Selenide.executeAsyncJavaScript(
                "const done = arguments[arguments.length - 1];"
                        + "fetch(arguments[0], {method: arguments[1], body: arguments[2], credentials: 'include',"
                        + " headers: {'Content-Type': 'application/json; charset=UTF-8'}})"
                        + ".then(r => r.text()).then(done, e => done('fetch failed: ' + e));",
                url, method, body);
        try {
            final JsonNode json = JSON.readTree(String.valueOf(response));
            assertEquals(json.path("status").asText(), "OK", format("%s %s: %s", method, path, response));
            return json;
        } catch (IOException e) {
            throw new IllegalStateException(format("%s %s: %s", method, path, response), e);
        }
    }

    /**
     * Returns the payload of a GET of {@code <root address>/restapi/<path>}.
     */
    public static JsonNode get(final String path) {
        return call("GET", path, null).path("payload");
    }

    /**
     * The address the notifier sends the emails of a user (as the API returns the user) to: the "email" attribute, in
     * any case.
     */
    public static Optional<String> email(final JsonNode user) {
        final Iterator<Map.Entry<String, JsonNode>> attributes = user.path("attributes").fields();
        while (attributes.hasNext()) {
            final Map.Entry<String, JsonNode> attribute = attributes.next();
            if (attribute.getKey().equalsIgnoreCase("email") && !attribute.getValue().asText().trim().isEmpty()) {
                return Optional.of(attribute.getValue().asText().trim());
            }
        }
        return Optional.empty();
    }
}
