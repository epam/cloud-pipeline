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

package com.epam.pipeline.entity.monitoring;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum LongPausedRunAction {
    NOTIFY, STOP;

    public static boolean contains(String value) {
        return VALUE_SET.contains(value);
    }

    private static final Set<String> VALUE_SET = Arrays.stream(LongPausedRunAction.values())
            .map(Enum::name)
            .collect(Collectors.toSet());
}
