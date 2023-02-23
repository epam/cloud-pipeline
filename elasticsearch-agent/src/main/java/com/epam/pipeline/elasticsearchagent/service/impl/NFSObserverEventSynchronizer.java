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

package com.epam.pipeline.elasticsearchagent.service.impl;

import static com.epam.pipeline.utils.PasswordGenerator.generateRandomString;

import com.epam.pipeline.elasticsearchagent.exception.ElasticClientException;
import com.epam.pipeline.elasticsearchagent.model.PermissionsContainer;
import com.epam.pipeline.elasticsearchagent.model.nfsobserver.NFSObserverEvent;
import com.epam.pipeline.elasticsearchagent.model.nfsobserver.NFSObserverEventType;
import com.epam.pipeline.elasticsearchagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.storage.StorageFileMapper;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.DataStorageWithShareMount;
import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.utils.StreamUtils;
import com.epam.pipeline.vo.EntityPermissionVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@ConditionalOnProperty(value = "sync.nfs-file.observer.sync.disable", matchIfMissing = true, havingValue = "false")
public class NFSObserverEventSynchronizer extends NFSSynchronizer {

    private static final String BACKSLASH = "/";
    private static final String COMMA = ",";
    private static final String FILE_ID_FIELD = "id";
    private static final String STORAGE_ID_FIELD = "storage_id";
    private static final String DOC_MAPPING_TYPE = "_doc";
    private static final String FOLDER_EVENT_WILDCARD = "/*";
    private static final int SCROLLING_PAGE_SIZE = 1000;
    private static final Scroll TIME_SCROLL = new Scroll(new TimeValue(60000));

    private final String eventsBucketName;
    private final String eventsBucketFolderPath;
    private final Integer eventsFileChunkSize;
    private final S3FileManager eventBucketFileManager;
    private final StorageFileMapper fileMapper;
    private final ExecutorService executorService;
    private final Set<Long> activeIndexTasks = ConcurrentHashMap.newKeySet();

    public NFSObserverEventSynchronizer(final @Value("${sync.nfs-file.index.mapping}") String indexSettingsPath,
                                        final @Value("${sync.nfs-file.root.mount.point}") String rootMountPoint,
                                        final @Value("${sync.index.common.prefix}") String indexPrefix,
                                        final @Value("${sync.nfs-file.index.name}") String indexName,
                                        final @Value("${sync.nfs-file.bulk.insert.size}") Integer bulkInsertSize,
                                        final @Value("${sync.nfs-file.observer.sync.target.bucket}")
                                            String eventsBucketUriStr,
                                        final @Value("${sync.nfs-file.observer.sync.files.chunk}")
                                            Integer eventsFileChunkSize,
                                        final @Value("${sync.nfs-file.observer.sync.pool.size:4}")
                                            Integer poolSize,
                                        final CloudPipelineAPIClient cloudPipelineAPIClient,
                                        final ElasticsearchServiceClient elasticsearchServiceClient,
                                        final ElasticIndexService elasticIndexService,
                                        final NFSStorageMounter nfsMounter) {
        super(indexSettingsPath, rootMountPoint, indexPrefix, indexName, bulkInsertSize, cloudPipelineAPIClient,
              elasticsearchServiceClient, elasticIndexService, nfsMounter);

        this.eventsFileChunkSize = eventsFileChunkSize;
        final URI eventsBucketURI = URI.create(eventsBucketUriStr);
        this.eventsBucketName = eventsBucketURI.getAuthority();
        this.eventsBucketFolderPath = getEventsBucketFolder(eventsBucketURI);
        this.eventBucketFileManager = getS3FileManager(eventsBucketURI);
        this.fileMapper = new StorageFileMapper();
        this.executorService = Executors.newFixedThreadPool(poolSize);
    }

    @Override
    public void synchronize(final LocalDateTime lastSyncTime, final LocalDateTime syncStart) {
        log.info("Started NFS events synchronization");
        final Map<String, AbstractDataStorage> storagePathMapping = getCloudPipelineAPIClient().loadAllDataStorages()
            .stream()
            .collect(Collectors.toMap(AbstractDataStorage::getPath, Function.identity()));
        final AbstractDataStorage eventsStorage = storagePathMapping.get(eventsBucketName);
        storagePathMapping.values().removeIf(dataStorage -> dataStorage.getType() != DataStorageType.NFS);
        loadEventsFilesGroupedByProducer(getListingCredentials(eventsStorage))
            .forEach((producer, fileList) ->
                         processEventsFromFilesInChunks(storagePathMapping, eventsStorage, producer, fileList));
        log.info("Finished NFS events synchronization");
    }

    private void processEventsFromFilesInChunks(final Map<String, AbstractDataStorage> storagePathMapping,
                                                final AbstractDataStorage eventsStorage,
                                                final String eventsProducer,
                                                final List<DataStorageFile> fileList) {
        StreamUtils.chunked(fileList.stream(), eventsFileChunkSize)
            .forEach(filesChunk ->
                         processEventsFromFiles(storagePathMapping, eventsStorage, eventsProducer, filesChunk));
        log.info("Finished processing events from [{}]", eventsProducer);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void processEventsFromFiles(final Map<String, AbstractDataStorage> storagePathMapping,
                                        final AbstractDataStorage eventsStorage,
                                        final String eventsProducer,
                                        final List<DataStorageFile> files) {
        try (IndexRequestContainer requestContainer =
                 new IndexRequestContainer(requests -> getElasticsearchServiceClient().sendRequests(null, requests),
                                           getBulkInsertSize())) {
            groupEventsByStorage(storagePathMapping, files, eventsStorage)
                .forEach((dataStorage, events) -> processStorageEvents(requestContainer, dataStorage, events));
            deleteEventFiles(eventsStorage, files);
        } catch (Exception e) {
            log.warn("Some errors occurred during NFS observer events sync from [{}]: {}",
                     eventsProducer, e.getMessage());
            log.error("Error:", e);
        }
    }

    private void deleteEventFiles(final AbstractDataStorage eventsStorage, final List<DataStorageFile> eventsFiles) {
        final TemporaryCredentials writingCredentials = getWritingCredentials(eventsStorage);
        eventsFiles.stream()
            .map(AbstractDataStorageItem::getPath)
            .forEach(path -> eventBucketFileManager.deleteFile(eventsStorage.getPath(),
                                                               path, () -> writingCredentials));
    }

    private Map<AbstractDataStorage, Collection<NFSObserverEvent>> groupEventsByStorage(
        final Map<String, AbstractDataStorage> storagePathMapping,
        final List<DataStorageFile> fileList,
        final AbstractDataStorage eventsStorage) {
        final TemporaryCredentials readingCredentials = getReadingCredentials(eventsStorage);
        return loadAllEventsFromFiles(fileList, () -> readingCredentials)
            .filter(event -> storagePathMapping.get(event.getStorage()) != null)
            .collect(Collectors.groupingBy(NFSObserverEvent::getStorage))
            .entrySet()
            .stream()
            .map(entry -> new AbstractMap.SimpleEntry<>(storagePathMapping.get(entry.getKey()),
                                                        mergeEvents(entry.getValue())))
            .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
    }

    private Map<String, List<DataStorageFile>> loadEventsFilesGroupedByProducer(
        final TemporaryCredentials listingCredentials) {
        return eventBucketFileManager.files(eventsBucketName, eventsBucketFolderPath, () -> listingCredentials)
            .collect(Collectors.groupingBy(this::getProducerName));
    }

    private void processStorageEvents(final IndexRequestContainer requestContainer,
                                      final AbstractDataStorage dataStorage,
                                      final Collection<NFSObserverEvent> events) {
        log.debug("Processing {} events for datastorage [id={}]", events.size(), dataStorage.getId());
        final Map<Boolean, List<NFSObserverEvent>> groupedEvents = flattenEvents(dataStorage, events).stream()
                .collect(Collectors.partitioningBy(e -> NFSObserverEventType.REINDEX.equals(e.getEventType())));
        final DataStorageWithShareMount storageWithShareMount = loadStorageWithShareMount(dataStorage);
        final String regionCode = getRegionCode(storageWithShareMount);
        List<NFSObserverEvent> refreshEvents = groupedEvents.get(true);
        if (CollectionUtils.isNotEmpty(refreshEvents)) {
            reindexStorage(storageWithShareMount);
            return;
        }
        List<NFSObserverEvent> allEvents = groupedEvents.get(false);
        StreamUtils.chunked(allEvents.stream(), eventsFileChunkSize)
                .forEach(eventChunk -> {
                    log.debug("Processing chunk with {} events for datastorage [id={}]",
                            eventChunk.size(), dataStorage.getId());
                    final Map<String, SearchHit> searchHitMap = findIndexedFilesEvent(dataStorage, eventChunk);
                    log.debug("Found {} search hits for events", searchHitMap.size());
                    final String indexForNewFiles = createIndexForStorageIfNotExists(dataStorage);
                    final Path mountFolder = mountStorageToRootIfNecessary(dataStorage);
                    if (mountFolder == null) {
                        log.warn("Unable to retrieve mount for [{}], skipping...", dataStorage.getName());
                        return;
                    }
                    final PermissionsContainer permissionsContainer = getPermissionContainer(dataStorage);
                    eventChunk.stream()
                        .map(event -> mapUpdateEventToFile(requestContainer, mountFolder, searchHitMap, event))
                        .filter(Objects::nonNull)
                        .map(file ->
                                mapToElasticRequest(dataStorage, regionCode, searchHitMap,
                                        indexForNewFiles, permissionsContainer, file))
                        .forEach(requestContainer::add);
                });
        log.debug("Finished processing events for datastorage [id={}]", dataStorage.getId());
    }

    private DataStorageWithShareMount loadStorageWithShareMount(final AbstractDataStorage dataStorage) {
        FileShareMount fileShareMount = null;
        if (dataStorage.getFileShareMountId() != null) {
            fileShareMount = getCloudPipelineAPIClient().loadFileShareMount(dataStorage.getFileShareMountId());
        } else {
            log.warn(
                    String.format("Storage: %s, id: %d doesn't have shareMountId!",
                            dataStorage.getName(), dataStorage.getId()));
        }
        return new DataStorageWithShareMount(dataStorage, fileShareMount);
    }

    private void reindexStorage(final DataStorageWithShareMount dataStorageWithShareMount) {
        final AbstractDataStorage dataStorage = dataStorageWithShareMount.getStorage();
        log.debug("Full reindex is requested for storage {}", dataStorage.getId());
        if (activeIndexTasks.contains(dataStorage.getId())) {
            log.debug("Indexing process is already in progress for storage {}. " +
                    "New request will be skipped", dataStorage.getId());
        } else {
            CompletableFuture.runAsync(() -> {
                log.debug("Starting reindexing of storage {}", dataStorage.getId());
                activeIndexTasks.add(dataStorage.getId());
                createIndexAndDocuments(dataStorageWithShareMount);
                activeIndexTasks.remove(dataStorage.getId());
            }, executorService)
                    .exceptionally(e -> {
                        log.error("An error occurred during storage {} indexing: {}",
                                dataStorage.getId(), e.getMessage());
                        activeIndexTasks.remove(dataStorage.getId());
                        return null;
                    });
        }
    }

    private List<NFSObserverEvent> flattenEvents(final AbstractDataStorage dataStorage,
                                                 final Collection<NFSObserverEvent> events) {
        final List<NFSObserverEvent> allEvents = events.stream()
            .filter(event -> !event.getFilePath().endsWith(FOLDER_EVENT_WILDCARD))
            .collect(Collectors.toList());
        final List<NFSObserverEvent> flattenFolderEvents = events.stream()
            .filter(event -> event.getFilePath().endsWith(FOLDER_EVENT_WILDCARD))
            .map(event -> mapFolderEventToFileEvents(dataStorage, event))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
        allEvents.addAll(flattenFolderEvents);
        return allEvents;
    }

    private List<NFSObserverEvent> mapFolderEventToFileEvents(final AbstractDataStorage dataStorage,
                                                              final NFSObserverEvent folderEvent) {
        final NFSObserverEventType eventType = folderEvent.getEventType();
        switch (eventType) {
            case DELETED:
                return searchEventRelatedFiles(dataStorage, folderEvent);
            case FOLDER_MOVED:
                final List<NFSObserverEvent> filesForMovedFolder = searchEventRelatedFiles(dataStorage, folderEvent);
                return filesForMovedFolder.stream()
                    .flatMap(oldFileEvent -> splitFolderMoveFileEvent(oldFileEvent, folderEvent))
                    .collect(Collectors.toList());
            default:
                return Collections.emptyList();
        }
    }

    private Stream<NFSObserverEvent> splitFolderMoveFileEvent(final NFSObserverEvent event,
                                                              final NFSObserverEvent folderEvent) {
        final String oldFolderPath = extractFolderFromFolderEvent(folderEvent.getFilePath());
        final String newFolderPath = extractFolderFromFolderEvent(folderEvent.getFilePathTo());
        final String oldFilePath = event.getFilePath();
        final String newFilePath = newFolderPath + event.getFilePath().substring(oldFolderPath.length());

        final Long oldFileEventTimestamp = event.getTimestamp();
        final String storage = event.getStorage();
        return Stream.of(
            new NFSObserverEvent(oldFileEventTimestamp, NFSObserverEventType.MOVED_FROM, storage, oldFilePath),
            new NFSObserverEvent(oldFileEventTimestamp + 1, NFSObserverEventType.MOVED_TO, storage, newFilePath)
        );
    }

    private List<NFSObserverEvent> searchEventRelatedFiles(final AbstractDataStorage dataStorage,
                                                           final NFSObserverEvent folderEvent) {
        final List<NFSObserverEvent> allFilesEvents = new ArrayList<>();
        SearchResponse folderResponse = getElasticsearchServiceClient()
            .search(buildFolderSearchRequest(dataStorage, folderEvent));
        do {
            final List<NFSObserverEvent> scrollingPageEvents = mapHitsToPossibleEvents(folderResponse, folderEvent);
            if (CollectionUtils.isNotEmpty(scrollingPageEvents)) {
                allFilesEvents.addAll(scrollingPageEvents);
                folderResponse = getElasticsearchServiceClient()
                    .nextScrollPage(folderResponse.getScrollId(), TIME_SCROLL);
            } else {
                break;
            }
        } while (true);
        return allFilesEvents;
    }

    private List<NFSObserverEvent> mapHitsToPossibleEvents(final SearchResponse folderResponse,
                                                           final NFSObserverEvent folderEvent) {
        return Optional.ofNullable(folderResponse.getHits())
            .map(SearchHits::getHits)
            .map(Stream::of)
            .orElse(Stream.empty())
            .map(hit -> Optional.ofNullable(hit.getSourceAsMap())
                .map(source -> source.get(FILE_ID_FIELD))
                .map(String.class::cast)
                .map(path -> new NFSObserverEvent(folderEvent.getTimestamp(), folderEvent.getEventType(),
                                                  folderEvent.getStorage(), path))
                .orElse(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private IndexRequest mapToElasticRequest(final AbstractDataStorage dataStorage,
                                             final String regionCode,
                                             final Map<String, SearchHit> searchHitMap,
                                             final String newIndex,
                                             final PermissionsContainer permissionsContainer,
                                             final DataStorageFile storageFile) {
        return Optional.ofNullable(searchHitMap.get(storageFile.getPath()))
            .map(document -> {
                log.debug("Mapping `{}` file to update request [{}]", storageFile.getPath(), document.getId());
                return updateIndexRequest(
                        dataStorage, regionCode, storageFile, permissionsContainer, document);
            })
            .orElseGet(() -> {
                log.debug("Mapping `{}` file to create request", storageFile.getPath());
                return createIndexRequest(
                        storageFile, newIndex, dataStorage, permissionsContainer, regionCode);
            });
    }

    private DataStorageFile mapUpdateEventToFile(final IndexRequestContainer requestContainer,
                                                 final Path mountFolder,
                                                 final Map<String, SearchHit> searchHitMap,
                                                 final NFSObserverEvent event) {
        Assert.isTrue(mountFolder.toFile().exists(),
                      String.format("Mount folder [%s] doesn't exist - stop chunk synchronization...", mountFolder));
        log.debug("Processing event: [{}, {}, {}]",
                  event.getEventType().name(), event.getStorage(), event.getFilePath());
        final Path absoluteFilePath = mountFolder.resolve(event.getFilePath());
        final boolean fileExists = absoluteFilePath.toFile().exists();
        log.debug("Checking file existence at {}: {}", absoluteFilePath, fileExists);
        if (fileExists) {
            log.debug("Creating storage file update request");
            return convertToStorageFile(absoluteFilePath, mountFolder);
        } else {
            Optional.ofNullable(searchHitMap.get(event.getFilePath()))
                .map(hit -> {
                    log.debug("Creating storage file removal request");
                    return new DeleteRequest(hit.getIndex(), DOC_MAPPING_TYPE, hit.getId());
                })
                .ifPresent(requestContainer::add);
            return null;
        }
    }

    private Map<String, SearchHit> findIndexedFilesEvent(final AbstractDataStorage dataStorage,
                                                         final Collection<NFSObserverEvent> events) {
        final MultiSearchRequest multiSearchRequest = new MultiSearchRequest();
        events.stream().map(event -> buildFileSearchRequest(dataStorage, event)).forEach(multiSearchRequest::add);
        final MultiSearchResponse multiSearchResponse = getElasticsearchServiceClient().search(multiSearchRequest);
        return Stream.of(multiSearchResponse.getResponses())
            .map(MultiSearchResponse.Item::getResponse)
            .map(SearchResponse::getHits)
            .filter(hits -> hits.getTotalHits() == 1)
            .map(hits -> hits.getAt(0))
            .collect(Collectors.toMap(hit -> (String)hit.getSourceAsMap().get(FILE_ID_FIELD), Function.identity()));
    }

    private IndexRequest updateIndexRequest(final AbstractDataStorage dataStorage, final String regionCode,
                                            final DataStorageFile file, final PermissionsContainer container,
                                            final SearchHit hit) {
        final IndexRequest updateRequest = createIndexRequest(file, hit.getIndex(),
                dataStorage, container, regionCode);
        updateRequest.id(hit.getId());
        return updateRequest;
    }

    private SearchRequest buildFileSearchRequest(final AbstractDataStorage dataStorage, final NFSObserverEvent event) {
        final SearchSourceBuilder source = new SearchSourceBuilder()
            .query(QueryBuilders.boolQuery()
                       .must(QueryBuilders.termQuery(FILE_ID_FIELD, event.getFilePath()))
                       .must(QueryBuilders.termQuery(STORAGE_ID_FIELD, dataStorage.getId())))
            .size(1);
        return new SearchRequest(getAlias(dataStorage))
            .indicesOptions(IndicesOptions.lenientExpandOpen())
            .source(source);
    }

    private SearchRequest buildFolderSearchRequest(final AbstractDataStorage dataStorage,
                                                   final NFSObserverEvent event) {
        final String eventPath = event.getFilePath();
        final SearchSourceBuilder source = new SearchSourceBuilder()
            .query(QueryBuilders.boolQuery()
                       .must(QueryBuilders.prefixQuery(FILE_ID_FIELD, extractFolderFromFolderEvent(eventPath)))
                       .must(QueryBuilders.termQuery(STORAGE_ID_FIELD, dataStorage.getId())))
            .size(SCROLLING_PAGE_SIZE);
        return new SearchRequest(getAlias(dataStorage))
            .scroll(TIME_SCROLL)
            .indicesOptions(IndicesOptions.lenientExpandOpen())
            .source(source);
    }

    private String extractFolderFromFolderEvent(final String folderEventPath) {
        return folderEventPath.equals(FOLDER_EVENT_WILDCARD)
               ? StringUtils.EMPTY
               : folderEventPath.substring(0, folderEventPath.length() - 1);
    }

    private PermissionsContainer getPermissionContainer(final AbstractDataStorage dataStorage) {
        final EntityPermissionVO entityPermission = getCloudPipelineAPIClient()
            .loadPermissionsForEntity(dataStorage.getId(), dataStorage.getAclClass());

        final PermissionsContainer permissionsContainer = new PermissionsContainer();
        if (entityPermission != null) {
            permissionsContainer.add(entityPermission.getPermissions(), dataStorage.getOwner());
        }
        return permissionsContainer;
    }

    private String createIndexForStorageIfNotExists(final AbstractDataStorage dataStorage) {
        final String alias = getAlias(dataStorage);
        final String indexName = generateRandomString(5).toLowerCase() + "-" + alias;
        return Optional.ofNullable(getElasticsearchServiceClient().getIndexNameByAlias(alias))
            .orElseGet(() -> {
                try {
                    getElasticIndexService().createIndexIfNotExist(indexName, getIndexSettingsPath());
                    getElasticsearchServiceClient().createIndexAlias(indexName, alias);
                    return indexName;
                } catch (ElasticClientException e) {
                    log.error(e.getMessage(), e);
                    if (getElasticsearchServiceClient().isIndexExists(indexName)) {
                        getElasticsearchServiceClient().deleteIndex(indexName);
                    }
                    return null;
                }
            });
    }

    private String getAlias(final AbstractDataStorage dataStorage) {
        return getIndexPrefix() + getIndexName() + String.format("-%d", dataStorage.getId());
    }

    private Collection<NFSObserverEvent> mergeEvents(final List<NFSObserverEvent> events) {
        return events.stream()
            .sorted(Comparator.comparing(NFSObserverEvent::getTimestamp))
            .collect(Collectors.toMap(NFSObserverEvent::getFilePath,
                                      Function.identity(),
                                      this::mergeEventsPair))
            .values();
    }

    private NFSObserverEvent mergeEventsPair(final NFSObserverEvent currentEvent, final NFSObserverEvent newEvent) {
        if (currentEvent == null) {
            return newEvent;
        }
        if (currentEvent.getEventType() == NFSObserverEventType.CREATED) {
            final NFSObserverEventType newEventType = newEvent.getEventType();
            if (newEventType == NFSObserverEventType.MOVED_FROM || newEventType == NFSObserverEventType.DELETED) {
                return null;
            }
            return currentEvent;
        }
        return newEvent;
    }

    private Stream<NFSObserverEvent> loadAllEventsFromFiles(
        final List<DataStorageFile> fileList, final Supplier<TemporaryCredentials> readingCredentialsSupplier) {
        return fileList.stream()
            .map(file -> readAllEventsFromFile(file, readingCredentialsSupplier))
            .flatMap(Collection::stream);
    }

    private List<NFSObserverEvent> readAllEventsFromFile(
        final DataStorageFile file, final Supplier<TemporaryCredentials> readingCredentialsSupplier) {
        try (InputStream inputStream = eventBucketFileManager.readFileContent(eventsBucketName,
                                                                              file.getPath(),
                                                                              readingCredentialsSupplier)) {
            return IOUtils.readLines(inputStream, StandardCharsets.UTF_8).stream()
                .map(this::mapStringToEvent)
                .collect(Collectors.toList());
        } catch (IOException ex) {
            return new ArrayList<>();
        }
    }

    private NFSObserverEvent mapStringToEvent(final String line) {
        final String[] eventDetails = line.split(COMMA);
        final String pathTo = eventDetails.length == 5 ? eventDetails[4] : null;
        return new NFSObserverEvent(Long.parseLong(eventDetails[0]),
                                    NFSObserverEventType.fromCode(eventDetails[1]),
                                    eventDetails[2],
                                    eventDetails[3],
                                    pathTo);
    }

    private String getProducerName(final DataStorageFile file) {
        return file.getPath().substring(eventsBucketFolderPath.length() + 1).split(BACKSLASH)[0];
    }

    private TemporaryCredentials getWritingCredentials(final AbstractDataStorage eventsStorage) {
        return getTemporaryCredentials(eventsStorage, false, false, true);
    }

    private TemporaryCredentials getListingCredentials(final AbstractDataStorage eventsStorage) {
        return getTemporaryCredentials(eventsStorage, true, false, false);
    }

    private TemporaryCredentials getReadingCredentials(final AbstractDataStorage eventsStorage) {
        return getTemporaryCredentials(eventsStorage, false, true, false);
    }

    private TemporaryCredentials getTemporaryCredentials(final AbstractDataStorage dataStorage,
                                                         final boolean list,
                                                         final boolean read,
                                                         final boolean write) {
        log.debug("Retrieving {} data storage {} temporary credentials, (listing={}, read={}, write={})...",
                  dataStorage.getType(), dataStorage.getPath(), list, read, write);
        final DataStorageAction action = new DataStorageAction();
        action.setBucketName(dataStorage.getPath());
        action.setId(dataStorage.getId());
        action.setRead(read);
        action.setList(list);
        action.setWrite(write);
        return getCloudPipelineAPIClient().generateTemporaryCredentials(Collections.singletonList(action));
    }

    private S3FileManager getS3FileManager(final URI eventsBucketURI) {
        Assert.isTrue(DataStorageType.S3.getId().equalsIgnoreCase(eventsBucketURI.getScheme()),
                      "Only S3 is supported as events bucket!");
        return new S3FileManager();
    }

    private String getEventsBucketFolder(final URI eventsBucketUri) {
        return Optional.ofNullable(eventsBucketUri.getPath())
            .map(path -> StringUtils.removeEnd(path, BACKSLASH))
            .map(path -> StringUtils.removeStart(path, BACKSLASH))
            .orElse(StringUtils.EMPTY);
    }

    private IndexRequest createIndexRequest(final DataStorageFile file,
                                            final String indexName,
                                            final AbstractDataStorage dataStorage,
                                            final PermissionsContainer permissionsContainer,
                                            final String regionCode) {
        return new IndexRequest(indexName, DOC_MAPPING_TYPE)
                .source(fileMapper.fileToDocument(file, dataStorage, regionCode, permissionsContainer,
                        SearchDocumentType.NFS_FILE));
    }
}
