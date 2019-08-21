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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ActionStatus {
    private ActionStatusType status;
    private String message;

    public static ActionStatus success() {
        return ActionStatus.builder()
                .status(ActionStatusType.SUCCESS)
                .build();
    }

    public static ActionStatus notSupported() {
        return ActionStatus.builder()
                .status(ActionStatusType.NOT_SUPPORTED)
                .build();
    }

    public static ActionStatus error(final String message) {
        return ActionStatus.builder()
                .message(message)
                .status(ActionStatusType.ERROR)
                .build();
    }
}
