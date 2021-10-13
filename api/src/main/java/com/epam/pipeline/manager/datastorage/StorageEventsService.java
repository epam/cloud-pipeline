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

package com.epam.pipeline.manager.datastorage;

import com.epam.pipeline.config.Constants;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.nfs.NFSObserverEvent;
import com.epam.pipeline.entity.datastorage.nfs.NFSObserverEventType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;

@Service
@Slf4j
@ConditionalOnProperty(value = "data.storage.nfs.events.disable.sync", matchIfMissing = true, havingValue = "false")
public class StorageEventsService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss.SSSSSS");
    private static final String API_EVENTS_SUBFOLDER_NAME = "api";
    private static final String EVENTS_FILE_PREFIX = "events-";

    private final List<NFSObserverEvent> events;
    private final DataStorageManager storageManager;
    private final AbstractDataStorage syncStorage;
    private final String eventsBucketEventsPath;

    public StorageEventsService(final DataStorageManager storageManager,
                                final @Value("${data.storage.nfs.events.sync.bucket:}") String eventsBucketUriStr) {
        this.events = new CopyOnWriteArrayList<>();
        final URI eventsBucketURI = URI.create(eventsBucketUriStr);
        checkBucketScheme(eventsBucketURI);
        this.syncStorage = storageManager.loadByPathOrId(eventsBucketURI.getHost());
        this.eventsBucketEventsPath = buildEventsBucketInnerPath(eventsBucketURI);
        this.storageManager = storageManager;
    }

    @Scheduled(fixedDelayString = "${data.storage.nfs.events.dump.timeout:30000}")
    @PreDestroy
    public void dumpEvents() {
        if (CollectionUtils.isNotEmpty(events)) {
            final StringJoiner joiner = new StringJoiner(Constants.NEWLINE);
            final List<NFSObserverEvent> eventsToDump = events.stream()
                .peek(event -> joiner.add(eventToString(event)))
                .collect(Collectors.toList());
            log.info("Dumping {} NFS events", eventsToDump.size());
            storageManager.createDataStorageFile(syncStorage.getId(), eventsBucketEventsPath, getEventsFileName(),
                                                 joiner.toString().getBytes(StandardCharsets.UTF_8));
            events.removeAll(eventsToDump);
        }
    }

    public void addEvent(final AbstractDataStorage dataStorage, final String pathFrom, final String pathTo,
                         final NFSObserverEventType eventType) {
        events.add(fileToEvent(dataStorage, pathFrom, pathTo, eventType));
    }

    public void addEvent(final AbstractDataStorage dataStorage, final String path,
                         final NFSObserverEventType eventType) {
        addEvent(dataStorage, path, null, eventType);
    }

    private NFSObserverEvent fileToEvent(final AbstractDataStorage dataStorage,
                                         final String pathFrom,
                                         final String pathTo,
                                         final NFSObserverEventType eventType) {
        return new NFSObserverEvent(Instant.now().toEpochMilli(), eventType, dataStorage.getPath(), pathFrom, pathTo);
    }

    private String eventToString(final NFSObserverEvent event) {
        final String eventString = String.join(Constants.COMMA,
                                               event.getTimestamp().toString(), event.getEventType().getEventCode(),
                                               event.getStorage(), event.getFilePathFrom());
        return Optional.ofNullable(event.getFilePathTo())
            .map(pathTo -> eventString + Constants.COMMA + pathTo)
            .orElse(eventString);
    }

    private void checkBucketScheme(final URI eventsBucketURI) {
        Optional.ofNullable(eventsBucketURI.getScheme())
            .filter(StringUtils::isNotEmpty)
            .map(String::toUpperCase)
            .map(DataStorageType::getById)
            .filter(Objects::nonNull)
            .orElseThrow(() -> new IllegalArgumentException("Scheme isn't specified for events bucket!"));
    }

    private String getEventsBucketFolder(final URI eventsBucketUri) {
        return Optional.ofNullable(eventsBucketUri.getPath())
            .map(path -> StringUtils.removeEnd(path, Constants.PATH_DELIMITER))
            .map(path -> StringUtils.removeStart(path, Constants.PATH_DELIMITER))
            .orElse(StringUtils.EMPTY);
    }

    private String buildEventsBucketInnerPath(URI eventsBucketURI) {
        return Paths.get(getEventsBucketFolder(eventsBucketURI), API_EVENTS_SUBFOLDER_NAME).toString();
    }

    private String getEventsFileName() {
        return EVENTS_FILE_PREFIX + TIME_FORMATTER.format(LocalDateTime.now(ZoneOffset.UTC));
    }
}
