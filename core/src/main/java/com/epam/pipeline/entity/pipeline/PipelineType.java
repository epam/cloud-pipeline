/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.pipeline;

import java.util.HashMap;
import java.util.Map;

public enum PipelineType {
    PIPELINE(0), VERSIONED_STORAGE(1);

    private final long id;

    private static final Map<Long, PipelineType> ID_MAP = new HashMap<>();
    static {
        ID_MAP.put(PIPELINE.id, PIPELINE);
        ID_MAP.put(VERSIONED_STORAGE.id, VERSIONED_STORAGE);
    }

    private static final Map<String, PipelineType> NAMES_MAP = new HashMap<>();
    static {
        NAMES_MAP.put(PIPELINE.name(), PIPELINE);
        NAMES_MAP.put(VERSIONED_STORAGE.name(), VERSIONED_STORAGE);
    }

    PipelineType(final long id) {
        this.id = id;
    }

    public static PipelineType getById(final Long id) {
        if (id == null) {
            return null;
        }
        return ID_MAP.get(id);
    }

    public static PipelineType getByName(final String name) {
        if (name == null) {
            return null;
        }
        return NAMES_MAP.get(name);
    }

    public Long getId() {
        return this.id;
    }
}
