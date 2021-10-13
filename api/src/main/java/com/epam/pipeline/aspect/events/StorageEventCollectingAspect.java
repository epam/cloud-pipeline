/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.aspect.events;

import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSObserverEventType;
import com.epam.pipeline.manager.datastorage.StorageEventsService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;


@Aspect
@Component
@RequiredArgsConstructor
public class StorageEventCollectingAspect {

    private static final String FOLDER_EVENT_WILDCARD = "/*";

    private final StorageEventsService storageEventsService;


    @After("execution(* com.epam.pipeline.manager.datastorage.providers.nfs.NFSStorageProvider.createFile(..)) && "
           + "args(dataStorage, path, ..)")
    public void generateFileCreationEvent(final JoinPoint joinPoint,
                                          final NFSDataStorage dataStorage, final String path) {
        storageEventsService.addEvent(dataStorage, path, NFSObserverEventType.CREATED);
    }

    @After("execution(* com.epam.pipeline.manager.datastorage.providers.nfs.NFSStorageProvider.moveFile(..)) && "
           + "args(dataStorage, oldPath, newPath, ..)")
    public void generateFileMovingEvent(final JoinPoint joinPoint,
                                        final NFSDataStorage dataStorage, final String oldPath, final String newPath) {
        storageEventsService.addEvent(dataStorage, oldPath, NFSObserverEventType.MOVED_FROM);
        storageEventsService.addEvent(dataStorage, newPath, NFSObserverEventType.MOVED_TO);
    }

    @After("execution(* com.epam.pipeline.manager.datastorage.providers.nfs.NFSStorageProvider.deleteFile(..)) && "
           + "args(dataStorage, path, ..)")
    public void generateFileRemovalEvent(final JoinPoint joinPoint,
                                         final NFSDataStorage dataStorage, final String path) {
        storageEventsService.addEvent(dataStorage, path, NFSObserverEventType.DELETED);
    }

    @After("execution(* com.epam.pipeline.manager.datastorage.providers.nfs.NFSStorageProvider.moveFolder(..)) && "
           + "args(dataStorage, oldFolderPath, newFolderPath, ..)")
    public void generateFolderMovingEvents(final JoinPoint joinPoint, final NFSDataStorage dataStorage,
                                           final String oldFolderPath, final String newFolderPath) {
        storageEventsService.addEvent(dataStorage,
                                      oldFolderPath + FOLDER_EVENT_WILDCARD,
                                      newFolderPath + FOLDER_EVENT_WILDCARD,
                                      NFSObserverEventType.FOLDER_MOVED);

    }

    @After("execution(* com.epam.pipeline.manager.datastorage.providers.nfs.NFSStorageProvider.deleteFolder(..)) && "
            + "args(dataStorage, path, ..)")
    public void generateFolderRemovalEvents(final JoinPoint joinPoint,
                                            final NFSDataStorage dataStorage, final String path) {
        storageEventsService.addEvent(dataStorage, path + FOLDER_EVENT_WILDCARD, NFSObserverEventType.DELETED);
    }
}
