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

package com.epam.pipeline.eventsourcing.acl;

import com.epam.pipeline.eventsourcing.EventEngine;
import com.epam.pipeline.eventsourcing.EventTopic;
import com.epam.pipeline.eventsourcing.EventType;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.acls.model.AclCache;

@Configuration
public class ACLEventConfiguration {

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private String applicationId;

    @Bean
    public ACLUpdateEventProducer aclEventSourcingProducer(final EventEngine eventEngine) {
        final ACLUpdateEventProducer aclUpdateEventProducer = new ACLUpdateEventProducer();

        preferenceManager.getObservablePreference(SystemPreferences.SYSTEM_EVENT_SOURCING_CONFIG)
                .subscribe(eventTopics -> reconfigureACLEventProducer(eventEngine, aclUpdateEventProducer));

        reconfigureACLEventProducer(eventEngine, aclUpdateEventProducer);

        return aclUpdateEventProducer;
    }

    @Bean
    public ACLUpdateEventHandler aclEventSourcingHandler(final EventEngine eventEngine, final AclCache aclCache) {
        final ACLUpdateEventHandler aclUpdateEventHandler = new ACLUpdateEventHandler(
                String.format("%s:%s", applicationId, ACLUpdateEventHandler.class.getSimpleName()),
                applicationId, aclCache
        );
        reconfigureACLEventHandler(eventEngine, aclUpdateEventHandler);

        preferenceManager.getObservablePreference(SystemPreferences.SYSTEM_EVENT_SOURCING_CONFIG)
                .subscribe(eventTopics -> reconfigureACLEventHandler(eventEngine, aclUpdateEventHandler));

        return aclUpdateEventHandler;
    }

    private void reconfigureACLEventProducer(final EventEngine eventEngine,
                                             final ACLUpdateEventProducer aclEventProducer) {

        final EventTopic aclTopic = preferenceManager
                .getPreference(SystemPreferences.SYSTEM_EVENT_SOURCING_CONFIG)
                .get(EventType.ACL.name());

        if (aclTopic != null) {
            if (aclTopic.isEnabled()) {
                aclEventProducer.init(
                    eventEngine.enableProducer(
                            String.format("%s:%s", applicationId, ACLUpdateEventProducer.class.getSimpleName()),
                            applicationId, EventType.ACL.name(), aclTopic.getStream()
                    )
                );
            } else {
                aclEventProducer.init(null);
            }
        }
    }

    private void reconfigureACLEventHandler(final EventEngine eventEngine,
                                            final ACLUpdateEventHandler aclUpdateEventHandler) {

        final EventTopic aclTopic = preferenceManager
                .getPreference(SystemPreferences.SYSTEM_EVENT_SOURCING_CONFIG)
                .get(aclUpdateEventHandler.getEventType());

        if (aclTopic != null) {
            if (aclTopic.isEnabled()) {
                eventEngine.enableHandlerFromNow(
                        aclTopic.getStream(), aclUpdateEventHandler,
                        aclTopic.getTimeout(), true
                );
            } else {
                eventEngine.disableHandler(aclUpdateEventHandler.getId());
            }
        }
    }
}
