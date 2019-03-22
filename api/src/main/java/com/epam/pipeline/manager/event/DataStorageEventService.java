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

import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataStorageEventService implements EntityEventService {

    private final EventManager eventManager;
    private final DataStorageManager dataStorageManager;

    @Override
    public AclClass getSupportedClass() {
        return AclClass.DATA_STORAGE;
    }

    @Override
    public void updateEventsWithChildrenAndIssues(final Long id) {
        AbstractDataStorage dataStorage = dataStorageManager.load(id);

        eventManager.addUpdateEventsForIssues(id, AclClass.DATA_STORAGE);
        eventManager.addUpdateEvent(dataStorage.getType().name(), id);
    }
}
