/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.eventsourcing;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;

import java.util.Map;

/**
 * Represents a message in Redis Stream.
 * */
@Value
@Builder
@ToString
public class Event {

    public static final String EVENT_TYPE_FIELD = "eventType";
    public static final String APPLICATION_ID_FIELD = "applicationId";

    /**
     * Type of the message.
     * f.i.: ACL
     * */
    String type;

    /**
     * Unique identifier of the application which sent this event.
     * */
    String applicationId;

    /**
     * Any sort of the data to transfer.
     * */
    Map<String, String> data;

    public static Event create(final String applicationId, final String type,
                               final Map<String, String> data) {
        return Event.builder()
                .type(type)
                .applicationId(applicationId)
                .data(data)
                .build();
    }

    public static Event fromRawData(final Map<String, String> data) {
        return Event.builder()
                .type(data.remove(EVENT_TYPE_FIELD))
                .applicationId(data.remove(APPLICATION_ID_FIELD))
                .data(data)
                .build();
    }
}

