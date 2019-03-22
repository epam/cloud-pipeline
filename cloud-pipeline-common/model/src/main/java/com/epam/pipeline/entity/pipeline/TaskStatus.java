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

public enum TaskStatus {
    SUCCESS(0, true, false), FAILURE(1, true, false), RUNNING(2, false, false), STOPPED(3, true, false),
    PAUSING(4, false, true), PAUSED(5, false, true), RESUMING(6, false, true);

    private final long id;
    private final boolean finalStatus;
    private final boolean pause;

    private static Map<Long, TaskStatus> idMap = new HashMap<>();
    static {
        idMap.put(SUCCESS.id, SUCCESS);
        idMap.put(FAILURE.id, FAILURE);
        idMap.put(RUNNING.id, RUNNING);
        idMap.put(STOPPED.id, STOPPED);
        idMap.put(PAUSING.id, PAUSING);
        idMap.put(PAUSED.id, PAUSED);
        idMap.put(RESUMING.id, RESUMING);
    }
    private static Map<String, TaskStatus> namesMap = new HashMap<>();
    static {
        namesMap.put(SUCCESS.name(), SUCCESS);
        namesMap.put(FAILURE.name(), FAILURE);
        namesMap.put(RUNNING.name(), RUNNING);
        namesMap.put(STOPPED.name(), STOPPED);
        namesMap.put(PAUSING.name(), PAUSING);
        namesMap.put(PAUSED.name(), PAUSED);
        namesMap.put(RESUMING.name(), RESUMING);
    }

    TaskStatus(long id, boolean finalStatus, boolean pause) {
        this.id = id;
        this.finalStatus = finalStatus;
        this.pause = pause;
    }

    public static TaskStatus getById(Long id) {
        if (id == null) {
            return null;
        }
        return idMap.get(id);
    }

    public static TaskStatus getByName(String name) {
        if (name == null) {
            return null;
        }
        return namesMap.get(name);
    }

    public boolean isFinal() {
        return finalStatus;
    }

    public boolean isPause() {
        return pause;
    }

    public Long getId() {
        return this.id;
    }
}
