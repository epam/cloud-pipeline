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

package com.epam.pipeline.entity.datastorage;

import java.util.HashMap;
import java.util.Map;

public enum DataStorageItemType {
    Folder(0),
    File(1);

    private long id;
    private static Map<Long, DataStorageItemType> idMap = new HashMap<>();
    static {
        idMap.put(Folder.id, Folder);
        idMap.put(File.id, File);
    }
    private static Map<String, DataStorageItemType> namesMap = new HashMap<>();
    static {
        namesMap.put(Folder.name(), Folder);
        namesMap.put(File.name(), File);
    }

    DataStorageItemType(long id) {
        this.id = id;
    }

    public static DataStorageItemType getById(Long id) {
        if (id == null) {
            return null;
        }
        return idMap.get(id);
    }

    public static DataStorageItemType getByName(String name) {
        if (name == null) {
            return null;
        }
        return namesMap.get(name);
    }
}
