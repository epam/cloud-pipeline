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

package com.epam.pipeline.controller.vo.data.storage;

import java.util.HashMap;
import java.util.Map;

public enum UpdateDataStorageItemActionTypes {
    Create(0),
    Move(1);

    private long id;
    private static Map<Long, UpdateDataStorageItemActionTypes> idMap = new HashMap<>();
    static {
        idMap.put(Create.id, Create);
        idMap.put(Move.id, Move);
    }
    private static Map<String, UpdateDataStorageItemActionTypes> namesMap = new HashMap<>();
    static {
        namesMap.put(Create.name(), Create);
        namesMap.put(Move.name(), Move);
    }

    UpdateDataStorageItemActionTypes(long id) {
        this.id = id;
    }

    public static UpdateDataStorageItemActionTypes getById(Long id) {
        if (id == null) {
            return null;
        }
        return idMap.get(id);
    }

    public static UpdateDataStorageItemActionTypes getByName(String name) {
        if (name == null) {
            return null;
        }
        return namesMap.get(name);
    }
}
