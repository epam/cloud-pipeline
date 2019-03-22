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
import com.epam.pipeline.manager.pipeline.ToolGroupManager;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DockerRegistryEventService implements EntityEventService {

    private final EventManager eventManager;
    private final ToolGroupManager toolGroupManager;
    private final ToolGroupEventService toolGroupEventService;

    @Override
    public AclClass getSupportedClass() {
        return AclClass.DOCKER_REGISTRY;
    }

    @Override
    public void updateEventsWithChildrenAndIssues(final Long id) {
        eventManager.addUpdateEventsForIssues(id, AclClass.DOCKER_REGISTRY);

        eventManager.addUpdateEvent(EventObjectType.DOCKER_REGISTRY.name().toLowerCase(), id);
        ListUtils.emptyIfNull(toolGroupManager.loadByRegistryId(id))
                .forEach(toolGroup -> toolGroupEventService.updateEventsWithChildrenAndIssues(toolGroup.getId()));
    }
}
