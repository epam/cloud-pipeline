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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.acls.domain.ObjectIdentityImpl;
import org.springframework.security.acls.model.AclCache;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ACLUpdateEventHandler implements EventHandler {

    public static final String HANDLER_NAME = "ACLUpdate";
    @Autowired
    AclCache aclCache;

    @Override
    public String getName() {
        return HANDLER_NAME;
    }

    @Override
    public String getEventType() {
        return EventType.ACL_UPDATE.name();
    }

    @Override
    public void handle(Event event) {
        final ObjectIdentityImpl objectIdentity = new ObjectIdentityImpl(
                event.getData().get("entityClass"),
                event.getData().get("entityClass")
        );
        aclCache.evictFromCache(objectIdentity);
    }

}
