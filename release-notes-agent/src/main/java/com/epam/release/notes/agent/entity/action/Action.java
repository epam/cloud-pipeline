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
package com.epam.release.notes.agent.entity.action;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum Action {
    POST("post");

    private final String name;
    private static final Map<String, Action> ID_MAP;

    static {
        ID_MAP = Arrays.stream(values()).collect(Collectors.toMap(v -> v.name, v -> v));
    }

    public static Action getByName(final String action) {
        Validate.notBlank(action, "Action is not defined.");
        return Validate.notNull(ID_MAP.get(action), "Unsupported action: " + action);
    }
}
