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

import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.DataStorageItemType;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSObserverEventType;
import com.epam.pipeline.manager.datastorage.StorageEventsService;
import com.epam.pipeline.manager.datastorage.providers.nfs.NFSStorageProvider;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.stream.Collectors;

@Aspect
@Component
@RequiredArgsConstructor
public class StorageEventCollectingAspect {

    private static final int LISTING_PAGE_SIZE = 100;

    private final StorageEventsService storageEventsService;
    private final NFSStorageProvider nfsStorageProvider;


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
        listAllFilesInFolder(dataStorage, newFolderPath)
            .forEach(file -> {
                final String newFilePath = file.getPath();
                final String oldFilePath = oldFolderPath + newFilePath.substring(newFolderPath.length());
                storageEventsService.addEvent(dataStorage, oldFilePath, NFSObserverEventType.MOVED_FROM);
                storageEventsService.addEvent(dataStorage, newFilePath, NFSObserverEventType.MOVED_TO);
            });

    }

    @Around("execution(* com.epam.pipeline.manager.datastorage.providers.nfs.NFSStorageProvider.deleteFolder(..)) && "
            + "args(dataStorage, path, ..)")
    public void generateFolderRemovalEvents(final ProceedingJoinPoint joinPoint,
                                            final NFSDataStorage dataStorage, final String path) throws Throwable {
        final List<String> pathsToBeRemoved = listAllFilesInFolder(dataStorage, path).stream()
            .map(AbstractDataStorageItem::getPath)
            .collect(Collectors.toList());
        joinPoint.proceed();
        storageEventsService.addEvents(dataStorage, pathsToBeRemoved, NFSObserverEventType.DELETED);
    }

    public List<DataStorageFile> listAllFilesInFolder(final NFSDataStorage storage, final String path) {
        final List<DataStorageFile> allFiles = new ArrayList<>();
        final Queue<DataStorageFolder> folders = new LinkedList<>();
        final DataStorageFolder rootFolder = new DataStorageFolder();
        rootFolder.setPath(path);
        folders.add(rootFolder);
        while (CollectionUtils.isEmpty(folders)) {
            Optional.ofNullable(folders.peek())
                .map(AbstractDataStorageItem::getPath)
                .ifPresent(folderPath -> {
                    String nextPageMarker = "0";
                    do {
                        nextPageMarker = extractFilesAndFolders(storage, folderPath, nextPageMarker, allFiles, folders);
                    } while (nextPageMarker != null);
                });
        }
        return allFiles;
    }

    private String extractFilesAndFolders(final NFSDataStorage storage, final String folderPath, final String marker,
                                          final List<DataStorageFile> allFiles,
                                          final Queue<DataStorageFolder> folders) {
        final DataStorageListing items = nfsStorageProvider.getItems(storage, folderPath, false,
                                                                     LISTING_PAGE_SIZE, marker);
        items.getResults().forEach(element -> {
            if (element.getType() == DataStorageItemType.Folder) {
                folders.add((DataStorageFolder) element);
            } else {
                allFiles.add((DataStorageFile) element);
            }
        });
        return items.getNextPageMarker();
    }
}
