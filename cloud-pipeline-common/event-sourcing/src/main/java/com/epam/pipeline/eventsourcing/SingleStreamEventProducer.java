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

import lombok.AllArgsConstructor;
import org.redisson.api.RStream;
import org.redisson.api.stream.StreamAddArgs;

import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor
public final class SingleStreamEventProducer implements EventProducer {

    private final String id;
    private final String applicationId;
    private final String type;
    private final RStream<String, String> stream;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getApplicationId() {
        return applicationId;
    }

    @Override
    public String getEventType() {
        return type;
    }

    @Override
    public long put(final Map<String, String> data) {
        final Map<String, String> event = new HashMap<>(data);
        event.put(Event.APPLICATION_ID_FIELD, getApplicationId());
        event.put(Event.EVENT_TYPE_FIELD, getEventType());
        return stream.add(StreamAddArgs.entries(event)).getId0();
    }

}
