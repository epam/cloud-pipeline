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
import com.epam.pipeline.elasticsearchagent.service.ObjectStorageFileManager;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.utils.StreamUtils;
import com.epam.pipeline.vo.EntityPermissionVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
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
    private static final String PATH_FIELD = "path";
    private static final String STORAGE_ID_FIELD = "storage_id";
    private static final String DOC_MAPPING_TYPE = "_doc";

    private final String eventsBucketName;
    private final String eventsBucketFolderPath;
    private final Integer eventsFileChunkSize;
    private final ObjectStorageFileManager eventBucketFileManager;

    public NFSObserverEventSynchronizer(final @Value("${sync.nfs-file.index.mapping}") String indexSettingsPath,
                                        final @Value("${sync.nfs-file.root.mount.point}") String rootMountPoint,
                                        final @Value("${sync.index.common.prefix}") String indexPrefix,
                                        final @Value("${sync.nfs-file.index.name}") String indexName,
                                        final @Value("${sync.nfs-file.bulk.insert.size}") Integer bulkInsertSize,
                                        final @Value("${sync.nfs-file.bulk.load.tags.size}") Integer bulkLoadTagsSize,
                                        final @Value("${sync.nfs-file.observer.sync.target.bucket}")
                                            String eventsBucketUriStr,
                                        final @Value("${sync.nfs-file.observer.sync.files.chunk}")
                                            Integer eventsFileChunkSize,
                                        final CloudPipelineAPIClient cloudPipelineAPIClient,
                                        final ElasticsearchServiceClient elasticsearchServiceClient,
                                        final ElasticIndexService elasticIndexService,
                                        final List<ObjectStorageFileManager> objectStorageFileManagers,
                                        final NFSStorageMounter nfsMounter) {
        super(indexSettingsPath, rootMountPoint, indexPrefix, indexName, bulkInsertSize, bulkLoadTagsSize,
              cloudPipelineAPIClient, elasticsearchServiceClient, elasticIndexService, nfsMounter);

        this.eventsFileChunkSize = eventsFileChunkSize;
        final URI eventsBucketURI = URI.create(eventsBucketUriStr);
        final String bucketScheme = getEventsBucketScheme(eventsBucketURI);
        this.eventsBucketName = eventsBucketURI.getAuthority();
        this.eventsBucketFolderPath = getEventsBucketFolder(eventsBucketURI);
        this.eventBucketFileManager = findObjectStorageFileManager(objectStorageFileManagers, bucketScheme);
    }

    @Override
    public void synchronize(final LocalDateTime lastSyncTime, final LocalDateTime syncStart) {
        final Map<String, AbstractDataStorage> storagePathMapping = getCloudPipelineAPIClient().loadAllDataStorages()
            .stream()
            .collect(Collectors.toMap(AbstractDataStorage::getPath, Function.identity()));
        final AbstractDataStorage eventsStorage = storagePathMapping.get(eventsBucketName);
        storagePathMapping.values().removeIf(dataStorage -> dataStorage.getType() != DataStorageType.NFS);
        loadEventsFilesGroupedByProducer(getListingCredentials(eventsStorage))
            .forEach((producer, fileList) ->
                         processEventsFromFilesInChunks(storagePathMapping, eventsStorage, producer, fileList));
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
        final Map<String, SearchHit> searchHitMap = findIndexedFiles(dataStorage, events);
        final String indexForNewFiles = createIndexForStorageIfNotExists(dataStorage);
        final Path mountFolder = mountStorageToRootIfNecessary(dataStorage);
        if (mountFolder == null) {
            log.warn("Unable to retrieve mount for [{}], skipping...", dataStorage.getName());
            return;
        }
        final Stream<DataStorageFile> fileUpdates = events.stream()
            .map(event -> mapUpdateEventToFile(requestContainer, mountFolder, searchHitMap, event))
            .filter(Objects::nonNull);
        final PermissionsContainer permissionsContainer = getPermissionContainer(dataStorage);
        processFilesTagsInChunks(dataStorage, fileUpdates)
            .map(file -> mapToElasticRequest(dataStorage, searchHitMap, indexForNewFiles, permissionsContainer, file))
            .forEach(requestContainer::add);
    }

    private IndexRequest mapToElasticRequest(final AbstractDataStorage dataStorage,
                                             final Map<String, SearchHit> searchHitMap,
                                             final String newIndex,
                                             final PermissionsContainer permissionsContainer,
                                             final DataStorageFile storageFile) {
        return Optional.ofNullable(searchHitMap.get(storageFile.getPath()))
            .map(document -> updateIndexRequest(dataStorage, storageFile, permissionsContainer, document))
            .orElseGet(() -> createIndexRequest(storageFile, newIndex, dataStorage, permissionsContainer));
    }

    private DataStorageFile mapUpdateEventToFile(final IndexRequestContainer requestContainer,
                                                 final Path mountFolder,
                                                 final Map<String, SearchHit> searchHitMap,
                                                 final NFSObserverEvent event) {
        Assert.isTrue(mountFolder.toFile().exists(),
                      String.format("Mount folder [%s] doesn't exist - stop chunk synchronization...", mountFolder));
        final Path absoluteFilePath = mountFolder.resolve(event.getFilePath());
        final boolean fileExists = absoluteFilePath.toFile().exists();

        if (fileExists) {
            return convertToStorageFile(absoluteFilePath, mountFolder);
        } else {
            Optional.ofNullable(searchHitMap.get(event.getFilePath()))
                .map(hit -> new DeleteRequest(hit.getIndex(), DOC_MAPPING_TYPE, hit.getId()))
                .ifPresent(requestContainer::add);
            return null;
        }
    }

    private Map<String, SearchHit> findIndexedFiles(final AbstractDataStorage dataStorage,
                                                    final Collection<NFSObserverEvent> events) {
        final MultiSearchRequest multiSearchRequest = new MultiSearchRequest();
        events.stream().map(event -> buildSearchRequest(dataStorage, event)).forEach(multiSearchRequest::add);
        final MultiSearchResponse multiSearchResponse = getElasticsearchServiceClient().search(multiSearchRequest);
        return Stream.of(multiSearchResponse.getResponses())
            .map(MultiSearchResponse.Item::getResponse)
            .map(SearchResponse::getHits)
            .filter(hits -> hits.getTotalHits() == 1)
            .map(hits -> hits.getAt(0))
            .collect(Collectors.toMap(hit -> (String)hit.getSourceAsMap().get(PATH_FIELD), Function.identity()));
    }

    private IndexRequest updateIndexRequest(final AbstractDataStorage dataStorage, final DataStorageFile file,
                                            final PermissionsContainer container, final SearchHit hit) {
        final IndexRequest updateRequest = createIndexRequest(file, hit.getIndex(), dataStorage, container);
        updateRequest.id(hit.getId());
        return updateRequest;
    }

    private SearchRequest buildSearchRequest(final AbstractDataStorage dataStorage, final NFSObserverEvent event) {
        final SearchSourceBuilder source = new SearchSourceBuilder()
            .query(QueryBuilders.boolQuery()
                       .must(QueryBuilders.termQuery(PATH_FIELD, event.getFilePath()))
                       .must(QueryBuilders.termQuery(STORAGE_ID_FIELD, dataStorage.getId())))
            .size(1);
        return new SearchRequest("*")
            .indicesOptions(IndicesOptions.lenientExpandOpen())
            .source(source);
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
        final String alias = getIndexPrefix() + getIndexName() + String.format("-%d", dataStorage.getId());
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
        return new NFSObserverEvent(Long.parseLong(eventDetails[0]),
                                    NFSObserverEventType.fromCode(eventDetails[1]),
                                    eventDetails[2],
                                    eventDetails[3]);
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


    private String getEventsBucketScheme(final URI eventsBucketURI) {
        return Optional.ofNullable(eventsBucketURI.getScheme())
            .orElseThrow(() -> new IllegalArgumentException("Scheme isn't specified for events bucket!"));
    }

    private ObjectStorageFileManager findObjectStorageFileManager(final List<ObjectStorageFileManager> managers,
                                                                  final String bucketScheme) {
        return managers.stream()
            .filter(manager -> manager.getType().getId().equalsIgnoreCase(bucketScheme))
            .findAny()
            .orElseThrow(() -> new IllegalArgumentException("Can't find a file manager for scheme " + bucketScheme));
    }

    private String getEventsBucketFolder(final URI eventsBucketUri) {
        return Optional.ofNullable(eventsBucketUri.getPath())
            .map(path -> StringUtils.removeEnd(path, BACKSLASH))
            .map(path -> StringUtils.removeStart(path, BACKSLASH))
            .orElse(StringUtils.EMPTY);
    }
}
