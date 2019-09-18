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

package com.epam.pipeline.vo.filter;

import java.util.HashMap;
import java.util.Map;

public enum FilterExpressionType {
    LOGICAL(0),
    AND(1),
    OR(2);

    private long id;
    private static Map<Long, FilterExpressionType> idMap = new HashMap<>();

    static {
        idMap.put(LOGICAL.id, LOGICAL);
        idMap.put(AND.id, AND);
        idMap.put(OR.id, OR);
    }

    private static Map<String, FilterExpressionType> namesMap = new HashMap<>();

    static {
        namesMap.put(LOGICAL.name(), LOGICAL);
        namesMap.put(AND.name(), AND);
        namesMap.put(OR.name(), OR);
    }

    FilterExpressionType(long id) {
        this.id = id;
    }

    public static FilterExpressionType getById(Long id) {
        if (id == null) {
            return null;
        }
        return idMap.get(id);
    }

    public static FilterExpressionType getByName(String name) {
        if (name == null) {
            return null;
        }
        return namesMap.get(name);
    }

    public Long getId() {
        return this.id;
    }
}
