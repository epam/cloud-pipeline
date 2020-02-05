/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.pipeline.run;

import java.util.HashMap;
import java.util.Map;

public enum RunScheduledAction {
    PAUSE(0), RESUME(1), RUN(2);

    private final long id;

    private static Map<Long, RunScheduledAction> idMap = new HashMap<>();
    static {
        idMap.put(PAUSE.id, PAUSE);
        idMap.put(RESUME.id, RESUME);
        idMap.put(RUN.id, RUN);
    }

    RunScheduledAction(final long id) {
        this.id = id;
    }

    public Long getId() {
        return this.id;
    }

    public static RunScheduledAction getById(final Long id) {
        if (id == null) {
            return null;
        }
        return idMap.get(id);
    }
}
