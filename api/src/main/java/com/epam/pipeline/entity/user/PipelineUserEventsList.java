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

package com.epam.pipeline.entity.user;

import com.epam.pipeline.controller.ResultStatus;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
public class PipelineUserEventsList {
    private final List<PipelineUserEvent> events;
    private final String userName;

    public PipelineUserEventsList(final String userName) {
        this.events = new ArrayList<>();
        this.userName = userName;
    }

    public void error(final String message) {
        events.add(PipelineUserEvent.builder()
                .userName(userName)
                .message(message)
                .status(ResultStatus.ERROR)
                .created(LocalDateTime.now())
                .build());
    }

    public void info(final String message) {
        events.add(PipelineUserEvent.builder()
                .userName(userName)
                .message(message)
                .status(ResultStatus.INFO)
                .created(LocalDateTime.now())
                .build());
    }
}
