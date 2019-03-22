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

public enum SystemNotificationSeverity {
    INFO(0), WARNING(1), CRITICAL(2);

    private long id;
    private static Map<Long, SystemNotificationSeverity> idMap = new HashMap<>();
    static {
        idMap.put(INFO.id, INFO);
        idMap.put(WARNING.id, WARNING);
        idMap.put(CRITICAL.id, CRITICAL);
    }
    private static Map<String, SystemNotificationSeverity> namesMap = new HashMap<>();
    static {
        namesMap.put(INFO.name(), INFO);
        namesMap.put(WARNING.name(), WARNING);
        namesMap.put(CRITICAL.name(), CRITICAL);
    }

    SystemNotificationSeverity(long id) {
        this.id = id;
    }

    public static SystemNotificationSeverity getById(Long id) {
        if (id == null) {
            return null;
        }
        return idMap.get(id);
    }

    public static SystemNotificationSeverity getByName(String name) {
        if (name == null) {
            return null;
        }
        return namesMap.get(name);
    }

    public Long getId() {
        return this.id;
    }

}
