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

package com.epam.pipeline.entity.contextual;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Contextual preference applicable level.
 */
@RequiredArgsConstructor
@Getter
public enum ContextualPreferenceLevel {

    /**
     * User contextual preference level.
     *
     * It is associated with a single user.
     */
    USER(0),

    /**
     * User role contextual preference level.
     *
     * It is associated with all users with a single role.
     */
    ROLE(1),

    /**
     * Tool contextual preference level.
     *
     * It is associated with a single tool.
     */
    TOOL(2),

    /**
     * System preference level.
     *
     * It is translated to a system preference.
     */
    SYSTEM(3);

    private final long id;

    public static Optional<ContextualPreferenceLevel> getById(final long id) {
        return Arrays.stream(values())
                .filter(it -> it.id == id)
                .findFirst();
    }
}
