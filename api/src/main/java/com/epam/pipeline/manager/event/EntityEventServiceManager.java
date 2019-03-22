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

package com.epam.pipeline.manager.event;

import com.epam.pipeline.entity.security.acl.AclClass;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EntityEventServiceManager {

    private Map<AclClass, EntityEventService> services;

    public EntityEventServiceManager(final List<EntityEventService> services) {
        this.services = ListUtils.emptyIfNull(services)
                .stream()
                .collect(Collectors.toMap(EntityEventService::getSupportedClass, Function.identity()));
    }

    public EntityEventService getEventService(final AclClass aclClass) {
        if (!services.containsKey(aclClass)) {
            throw new IllegalArgumentException(String
                    .format("Provided entity class %s is not supported for events", aclClass));
        }
        return services.get(aclClass);
    }

    public void updateEventsWithChildrenAndIssues(final AclClass entityClass, final Long id) {
        getEventService(entityClass).updateEventsWithChildrenAndIssues(id);
    }
}
