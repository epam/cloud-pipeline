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

import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.eventsourcing.EventProducer;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class ACLUpdateEventProducer {

    protected static final String ACL_CLASS_FIELD = "aclClass";
    protected static final String ENTITY_ID_FIELD = "id";

    private final AtomicReference<EventProducer> inner;

    public ACLUpdateEventProducer() {
        inner = new AtomicReference<>();
    }

    public void init(final EventProducer producer) {
        this.inner.set(producer);
    }

    public long put(final Long id, final AclClass aclClass) {
        log.debug("Publishing ACL update event {}#{}", aclClass, id);
        if (inner.get() != null) {
            final Map<String, String> data = new HashMap<>();
            data.put(ACL_CLASS_FIELD, aclClass.name());
            data.put(ENTITY_ID_FIELD, id.toString());
            final long eventId = inner.get().put(data);
            log.debug("Published ACL update event #{}", eventId);
            return eventId;
        }
        return -1;
    }
}
