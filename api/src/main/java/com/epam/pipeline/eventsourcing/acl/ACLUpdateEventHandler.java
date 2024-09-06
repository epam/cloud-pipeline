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

package com.epam.pipeline.eventsourcing.acl;

import com.epam.pipeline.eventsourcing.Event;
import com.epam.pipeline.eventsourcing.EventHandler;
import com.epam.pipeline.eventsourcing.EventType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.security.acls.domain.ObjectIdentityImpl;
import org.springframework.security.acls.model.AclCache;

import java.util.Objects;

@Slf4j
@AllArgsConstructor
public class ACLUpdateEventHandler implements EventHandler {

    protected static final String ACL_CLASS_FIELD = "aclClass";
    protected static final String ID_FIELD = "id";

    private final String id;
    private final String applicationId;
    private final AclCache aclCache;

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
        return EventType.ACL.name();
    }

    @Override
    public void handle(final long eventId, final Event event) {
        log.debug("Consuming ACL update event #{} '{}'", eventId, event);
        if (!validateEvent(event)) {
            return;
        }
        aclCache.evictFromCache(
            new ObjectIdentityImpl(
                event.getData().get(ACL_CLASS_FIELD),
                Long.valueOf(event.getData().get(ID_FIELD))
            )
        );
    }

    boolean validateEvent(Event event) {
        if (Objects.equals(applicationId, event.getApplicationId())) {
            log.info(String.format(
                    "Skipping event %s with the same applicationId: %s", event, event.getApplicationId())
            );
            return false;
        }

        if (!Objects.equals(EventType.ACL.name(), event.getType())) {
            log.warn(
                String.format(
                    "Skipping event %s with wrong eventType, expected %s, got %s",
                        event, EventType.ACL.name(), event.getType()
                )
            );
            return false;
        }

        if (!MapUtils.emptyIfNull(event.getData()).containsKey(ACL_CLASS_FIELD)
                || !MapUtils.emptyIfNull(event.getData()).containsKey(ID_FIELD)) {
            log.warn(
                    String.format(
                            "Skipping ACL event %s, because it doesn't have necessary fields: '[%s, %s]'",
                            event, ACL_CLASS_FIELD, ID_FIELD
                    )
            );
            return false;
        }

        return true;
    }

}
