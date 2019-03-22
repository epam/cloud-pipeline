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

public enum CommitStatus {
    NOT_COMMITTED(0), COMMITTING(1), FAILURE(2), SUCCESS(3);

    private long id;
    private static Map<Long, CommitStatus> idMap = new HashMap<>();
    static {
        idMap.put(NOT_COMMITTED.id, NOT_COMMITTED);
        idMap.put(COMMITTING.id, COMMITTING);
        idMap.put(FAILURE.id, FAILURE);
        idMap.put(SUCCESS.id, SUCCESS);
    }

    private static Map<String, CommitStatus> namesMap = new HashMap<>();
    static {
        namesMap.put(NOT_COMMITTED.name(), NOT_COMMITTED);
        namesMap.put(COMMITTING.name(), COMMITTING);
        namesMap.put(FAILURE.name(), FAILURE);
        namesMap.put(SUCCESS.name(), SUCCESS);
    }

    CommitStatus(long id) {
        this.id = id;
    }

    public Long getId() {
        return this.id;
    }

    public static CommitStatus getById(Long id) {
        if (id == null) {
            return null;
        }
        return idMap.get(id);
    }

    public static CommitStatus getByName(String name) {
        if (name == null) {
            return null;
        }
        return namesMap.get(name);
    }
}
