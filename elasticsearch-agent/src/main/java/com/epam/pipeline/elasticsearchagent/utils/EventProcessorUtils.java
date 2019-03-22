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
package com.epam.pipeline.elasticsearchagent.utils;

import com.epam.pipeline.elasticsearchagent.model.EventType;
import com.epam.pipeline.elasticsearchagent.model.PipelineEvent;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class EventProcessorUtils {

    private static final String PATHS_DELIMITER = ";";

    private EventProcessorUtils() {}

    public static List<String> splitOnPaths(final String pipelineFileIndexPaths) {
        return Arrays.stream(pipelineFileIndexPaths.split(PATHS_DELIMITER))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
    }

    public static List<PipelineEvent> mergeEvents(final List<PipelineEvent> events) {
        Map<Long, List<PipelineEvent>> groupById = events.stream()
                .collect(Collectors.groupingBy(PipelineEvent::getObjectId));
        return groupById.entrySet()
                .stream()
                .map(entry -> mergeEventsByObject(entry.getValue()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private static Optional<PipelineEvent> mergeEventsByObject(final List<PipelineEvent> events) {
        Optional<PipelineEvent> deleteEvent = events.stream()
                .filter(event -> event.getEventType() == EventType.DELETE)
                .findFirst();
        if (deleteEvent.isPresent()) {
            return deleteEvent;
        }
        return events.stream().filter(event -> event.getEventType() != EventType.DELETE)
                .findFirst();
    }

}
