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

package com.epam.pipeline.entity.pipeline;

import java.util.HashMap;
import java.util.Map;

public enum RepositoryType {
    GITLAB(0), GITHUB(1);

    private long id;
    private static Map<Long, RepositoryType> idMap = new HashMap<>();
    static {
        idMap.put(GITLAB.id, GITLAB);
        idMap.put(GITHUB.id, GITHUB);
    }
    private static Map<String, RepositoryType> namesMap = new HashMap<>();
    static {
        namesMap.put(GITLAB.name(), GITLAB);
        namesMap.put(GITHUB.name(), GITHUB);
    }

    RepositoryType(long id) {
        this.id = id;
    }

    public static RepositoryType getById(Long id) {
        if (id == null) {
            return null;
        }
        return idMap.get(id);
    }

    public static RepositoryType getByName(String name) {
        if (name == null) {
            return null;
        }
        return namesMap.get(name);
    }

    public Long getId() {
        return this.id;
    }
}
