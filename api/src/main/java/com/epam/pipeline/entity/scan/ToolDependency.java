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

package com.epam.pipeline.entity.scan;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"description"})
public class ToolDependency {

    private long toolId;
    private String toolVersion;
    private String name;
    private String version;
    private Ecosystem ecosystem;
    private String  description;

    @Getter
    @AllArgsConstructor
    public enum Ecosystem {

        PYTHON_DIST("Python.Dist"),
        PYTHON_PKG("Python.Pkg"),
        R_PKG("R.Pkg"),
        SYSTEM("System"),
        JAVA("Java"),
        NMP("npm"),
        SWIFT("Swift.PM"),
        CMAKE("CMAKE"),
        RUBY("Ruby.Bundle"),
        OTHER("OTHER");

        private static Map<String, Ecosystem> map;

        static {
            map = new HashMap<>();
            map.put(PYTHON_DIST.value, PYTHON_DIST);
            map.put(R_PKG.value, R_PKG);
            map.put(SYSTEM.value, SYSTEM);
        }

        private String value;

        public static Ecosystem getByName(String name) {
            return map.get(name);
        }

        @JsonCreator
        public static Ecosystem forValue(String value) {
            Ecosystem ecosystem = map.get(value);
            return ecosystem != null ? ecosystem : OTHER;
        }

        @JsonValue
        @Override
        public String toString() {
            return value;
        }
    }

}
