/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.app;

import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.eventsourcing.EventHandler;
import com.epam.pipeline.eventsourcing.EventSourcingEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@ConditionalOnProperty(value = "event.sourcing.enabled", havingValue = "true")
public class EventSourcingConfiguration {

    @Value("${event.sourcing.redis.host:}")
    private String redisHost;

    @Value("${event.sourcing.redis.port:}")
    private Integer redisPort;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired(required = false)
    private List<EventHandler> eventHandlers;

    @Bean
    public EventSourcingEngine eventSourcingEngine() {
        final EventSourcingEngine eventSourcingEngine = new EventSourcingEngine(redisHost, redisPort);
        final Map<String, EventHandler> eventHandlersByType = eventHandlers.stream()
                .collect(Collectors.toMap(EventHandler::getEventType, eventHandler -> eventHandler));
        preferenceManager.getObservablePreference(SystemPreferences.SYSTEM_EVENT_SOURCING_CONFIG)
            .subscribe(eventSourcingTopics ->
                eventSourcingTopics.forEach(eventSourcingTopic -> {
                    final EventHandler eventHandler = eventHandlersByType.get(eventSourcingTopic.getEventType());
                    if (eventHandler != null) {
                        eventSourcingEngine.enableHandler(
                                eventSourcingTopic.getName(), 0, eventHandler,
                                eventSourcingTopic.getTimeout(), true
                        );
                    }
                })
            );
        return eventSourcingEngine;
    }
}