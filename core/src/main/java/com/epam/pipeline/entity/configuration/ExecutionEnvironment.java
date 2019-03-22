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

package com.epam.pipeline.entity.configuration;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Supported environments for running analysis
 */
@AllArgsConstructor
@Getter
public enum ExecutionEnvironment {

    CLOUD_PLATFORM(0, true), FIRECLOUD(1, true), DTS(2, false);
    private final long id;
    private final boolean monitored;

    private static final Map<Long, ExecutionEnvironment> ID_MAP = new HashMap<>();
    static {
        ID_MAP.put(CLOUD_PLATFORM.id, CLOUD_PLATFORM);
        ID_MAP.put(FIRECLOUD.id, FIRECLOUD);
        ID_MAP.put(DTS.id, DTS);
    }

    public static ExecutionEnvironment getById(Long id) {
        return ID_MAP.get(id);
    }
}
