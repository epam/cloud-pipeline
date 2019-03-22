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

/**
 * Interface for all services that support work with event for secured entities
 */
public interface EntityEventService {

    AclClass getSupportedClass();

    /**
     * Creates a new update event for specified entity. If children exist creates a new update events for all of them.
     * Also, creates a new update event for all issues that refer to specified entity.
     *
     * @param id entities ID
     */
    void updateEventsWithChildrenAndIssues(Long id);
}
