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

import com.epam.pipeline.eventsourcing.*;
import com.epam.pipeline.eventsourcing.acl.ACLUpdateEventProducer;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
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
    public EventEngine eventSourcingEngine() {
        final EventEngine eventEngine = new EventEngine(redisHost, redisPort);

        initEventHandlers(
                preferenceManager.getPreference(SystemPreferences.SYSTEM_EVENT_SOURCING_CONFIG),
                eventEngine
        );

        // Re-initialize handlers on configuration change
        preferenceManager.getObservablePreference(SystemPreferences.SYSTEM_EVENT_SOURCING_CONFIG)
            .subscribe(eventTopics -> initEventHandlers(eventTopics, eventEngine));

        return eventEngine;
    }

    private void initEventHandlers(final Map<String, EventTopic> eventSourcingTopics,
                                   final EventEngine eventEngine) {
        final Map<String, EventHandler> eventHandlersByType = eventHandlers.stream()
                .collect(Collectors.toMap(EventHandler::getEventType, eventHandler -> eventHandler));
        eventSourcingTopics.forEach((topicType, eventTopic) -> {
            final EventHandler eventHandler = eventHandlersByType.get(topicType);
            if (eventHandler != null) {
                if (eventTopic.isEnabled()) {
                    eventEngine.enableHandler(
                            eventTopic.getStream(), Long.MAX_VALUE, eventHandler,
                            eventTopic.getTimeout(), true
                    );
                } else {
                    eventEngine.disableHandler(eventHandler);
                }
            }
        });
    }

    @Bean
    public ACLUpdateEventProducer aclEventSourcingProducer(final EventEngine eventEngine) {
        final ACLUpdateEventProducer aclUpdateEventProducer = new ACLUpdateEventProducer();

        preferenceManager.getObservablePreference(SystemPreferences.SYSTEM_EVENT_SOURCING_CONFIG)
                .subscribe(eventTopics -> reconfigureACLEventProducer(eventEngine, aclUpdateEventProducer));

        reconfigureACLEventProducer(eventEngine, aclUpdateEventProducer);

        return aclUpdateEventProducer;
    }

    private void reconfigureACLEventProducer(final EventEngine eventEngine,
                                             final ACLUpdateEventProducer aclEventProducer) {

        final EventTopic aclTopic = preferenceManager
                .getPreference(SystemPreferences.SYSTEM_EVENT_SOURCING_CONFIG)
                .get(aclEventProducer.getEventType());

        if (aclTopic != null) {
            if (aclTopic.isEnabled()) {
                aclEventProducer.init(
                    eventEngine.registerProducer(
                        aclEventProducer.getName(), aclEventProducer.getEventType(), aclTopic.getStream()
                    )
                );
            } else {
                aclEventProducer.init(null);
            }
        }
    }
}