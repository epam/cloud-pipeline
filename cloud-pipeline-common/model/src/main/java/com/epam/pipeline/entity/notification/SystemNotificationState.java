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

package com.epam.pipeline.entity.notification;

import java.util.HashMap;
import java.util.Map;

public enum SystemNotificationState {
    INACTIVE(0), ACTIVE(1);

    private long id;
    private static Map<Long, SystemNotificationState> idMap = new HashMap<>();
    static {
        idMap.put(INACTIVE.id, INACTIVE);
        idMap.put(ACTIVE.id, ACTIVE);
    }
    private static Map<String, SystemNotificationState> namesMap = new HashMap<>();
    static {
        namesMap.put(INACTIVE.name(), INACTIVE);
        namesMap.put(ACTIVE.name(), ACTIVE);
    }

    SystemNotificationState(long id) {
        this.id = id;
    }

    public static SystemNotificationState getById(Long id) {
        if (id == null) {
            return null;
        }
        return idMap.get(id);
    }

    public static SystemNotificationState getByName(String name) {
        if (name == null) {
            return null;
        }
        return namesMap.get(name);
    }

    public Long getId() {
        return this.id;
    }
}
