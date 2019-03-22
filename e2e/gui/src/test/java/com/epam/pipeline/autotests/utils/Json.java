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
package com.epam.pipeline.autotests.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.UnaryOperator;

import static com.codeborne.selenide.Selenide.screenshot;

public interface Json {

    static ConfigurationProfile[] stringToProfiles(final String code) {
        final ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(code, ConfigurationProfile[].class);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    static String profilesToString(final ConfigurationProfile[] profiles) {
        final ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(profiles);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    static ConfigurationProfile selectProfileWithName(final String name, final ConfigurationProfile[] profiles) {
        return Arrays.stream(profiles)
                     .filter(profile -> Objects.equals(profile.name, name))
                     .findFirst()
                     .orElseThrow(() -> {
                         screenshot(Json.class.getSimpleName().toLowerCase());
                         return new NoSuchElementException(String.format(
                             "Supposed configuration profile with name %s is not present.", name
                         ));
                     });
    }

    static UnaryOperator<String> transferringJsonToObject(final UnaryOperator<ConfigurationProfile[]> action) {
        return code -> {
            final ConfigurationProfile[] profiles = stringToProfiles(code);
            final ConfigurationProfile[] edited = action.apply(profiles);
            return profilesToString(edited);
        };
    }
}
