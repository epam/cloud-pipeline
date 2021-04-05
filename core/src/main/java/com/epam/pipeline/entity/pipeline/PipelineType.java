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

    private long id;
    private static Map<Long, PipelineType> idMap = new HashMap<>();
    static {
        idMap.put(PIPELINE.id, PIPELINE);
        idMap.put(VERSIONED_STORAGE.id, VERSIONED_STORAGE);
    }
    private static Map<String, PipelineType> namesMap = new HashMap<>();
    static {
        namesMap.put(PIPELINE.name(), PIPELINE);
        namesMap.put(VERSIONED_STORAGE.name(), VERSIONED_STORAGE);
    }

    PipelineType(long id) {
        this.id = id;
    }

    public static PipelineType getById(Long id) {
        if (id == null) {
            return null;
        }
        return idMap.get(id);
    }

    public static PipelineType getByName(String name) {
        if (name == null) {
            return null;
        }
        return namesMap.get(name);
    }

    public Long getId() {
        return this.id;
    }
}
