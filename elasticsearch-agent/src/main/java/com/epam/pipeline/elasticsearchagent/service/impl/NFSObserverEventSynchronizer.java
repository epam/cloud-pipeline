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
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.vo.EntityPermissionVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.codecs.lucene50.Lucene50PostingsFormat;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@ConditionalOnProperty(value = "sync.nfs-file.observer.sync.disable", matchIfMissing = true, havingValue = "false")
public class NFSObserverEventSynchronizer extends NFSSynchronizer {

    public static final String BACKSLASH = "/";
    public static final String COMMA = ",";

    private final String eventsBucketName;
    private final String eventsBucketFolderPath;
    private final ObjectStorageFileManager eventBucketFileManager;

    public NFSObserverEventSynchronizer(final @Value("${sync.nfs-file.index.mapping}") String indexSettingsPath,
                                        final @Value("${sync.nfs-file.root.mount.point}") String rootMountPoint,
                                        final @Value("${sync.index.common.prefix}") String indexPrefix,
                                        final @Value("${sync.nfs-file.index.name}") String indexName,
                                        final @Value("${sync.nfs-file.bulk.insert.size}") Integer bulkInsertSize,
                                        final @Value("${sync.nfs-file.bulk.load.tags.size}") Integer bulkLoadTagsSize,
                                        final @Value("${sync.nfs-file.observer.sync.target.bucket}")
                                            String eventsBucketUriStr,
                                        final CloudPipelineAPIClient cloudPipelineAPIClient,
                                        final ElasticsearchServiceClient elasticsearchServiceClient,
                                        final ElasticIndexService elasticIndexService,
                                        final List<ObjectStorageFileManager> objectStorageFileManagers) {
        super(indexSettingsPath, rootMountPoint, indexPrefix, indexName, bulkInsertSize, bulkLoadTagsSize,
              cloudPipelineAPIClient, elasticsearchServiceClient, elasticIndexService);


        final URI eventsBucketUri = URI.create(eventsBucketUriStr);
        final String bucketScheme = eventsBucketUri.getScheme();
        this.eventsBucketName = eventsBucketUri.getAuthority();
        this.eventsBucketFolderPath = Optional.ofNullable(eventsBucketUri.getPath())
            .map(path -> StringUtils.removeEnd(path, BACKSLASH))
            .map(path -> StringUtils.removeStart(path, BACKSLASH))
            .orElse(StringUtils.EMPTY);
        this.eventBucketFileManager = objectStorageFileManagers.stream()
            .filter(manager -> manager.getType().getId().equalsIgnoreCase(bucketScheme))
            .findAny()
            .orElseThrow(() -> new IllegalArgumentException("Can't find a file manger for scheme " + bucketScheme));
    }

    @Override
    public void synchronize(final LocalDateTime lastSyncTime, final LocalDateTime syncStart) {
        final Map<String, AbstractDataStorage> storagePathMapping = getCloudPipelineAPIClient().loadAllDataStorages()
            .stream()
            .collect(Collectors.toMap(AbstractDataStorage::getPath, Function.identity()));
        final AbstractDataStorage eventsStorage = storagePathMapping.get(eventsBucketName);
        storagePathMapping.values().removeIf(dataStorage -> dataStorage.getType() != DataStorageType.NFS);

        final Supplier<TemporaryCredentials> listingCredentialsSupplier = getListingCredentialsSupplier(eventsStorage);
        final Map<String, List<DataStorageFile>> filesByRun =
            eventBucketFileManager.files(eventsBucketName, eventsBucketFolderPath, listingCredentialsSupplier)
                .collect(Collectors.groupingBy(this::getProducerName));

        filesByRun.forEach((producer, fileList) -> {
            final Supplier<TemporaryCredentials> readingCredentialsSupplier =
                getReadingCredentialsSupplier(eventsStorage);
            loadAllEventsFromFiles(fileList, readingCredentialsSupplier)
                .filter(event -> storagePathMapping.get(event.getStorage()) != null)
                .collect(Collectors.groupingBy(NFSObserverEvent::getStorage))
                .values()
                .stream()
                .map(this::mergeEvents)
                .flatMap(Collection::stream)
                .forEach(event -> processEvent(storagePathMapping, event));
        });

    }

    private void processEvent(final Map<String, AbstractDataStorage> storagePathMapping,
                              final NFSObserverEvent event) {
        final AbstractDataStorage dataStorage = storagePathMapping.get(event.getStorage());
        if (dataStorage == null) {
            log.info("No storage with root [{}] is registered in the system, skipping", event.getStorage());
            return;
        }
        final Optional<SearchHit> existingDocument = searchExistingDocument(event, dataStorage);
        final String storageName = getStorageName(dataStorage.getPath());
        final Path mountFolder = Paths.get(getRootMountPoint(), getMountDirName(dataStorage.getPath()), storageName);
        final Path absoluteFilePath = mountFolder.resolve(event.getFilePath());
        final boolean fileExists = absoluteFilePath.toFile().exists();

        if (fileExists) {
            final DataStorageFile storageFile = convertToStorageFile(absoluteFilePath, mountFolder);
            final PermissionsContainer permissionsContainer = getPermissionContainer(dataStorage);
            final IndexRequest request;
            if (!existingDocument.isPresent()) {
                final String newIndex = createIndexForStorageIfNotExists(dataStorage);
                request = createIndexRequest(storageFile, newIndex, dataStorage, permissionsContainer);
            } else {
                request = existingDocument
                    .map(document -> updateIndexRequest(dataStorage, storageFile, permissionsContainer, document))
                    .get();
            }
            getElasticsearchServiceClient().sendRequests(request.index(), Collections.singletonList(request));
        } else if (existingDocument.isPresent()) {
            existingDocument
                .map(hit -> new DeleteRequest(hit.getIndex(), Lucene50PostingsFormat.DOC_EXTENSION, hit.getId()))
                .ifPresent(getElasticsearchServiceClient()::deleteDocument);
        }
    }

    private IndexRequest updateIndexRequest(final AbstractDataStorage dataStorage, final DataStorageFile file,
                                            final PermissionsContainer container, final SearchHit hit) {
        final IndexRequest updateRequest = createIndexRequest(file, hit.getIndex(), dataStorage, container);
        updateRequest.id(hit.getId());
        return updateRequest;
    }

    private Optional<SearchHit> searchExistingDocument(NFSObserverEvent event, AbstractDataStorage dataStorage) {
        return Optional.of(getElasticsearchServiceClient().search(buildSearchRequest(event, dataStorage)))
            .map(SearchResponse::getHits)
            .filter(hits -> hits.getTotalHits() == 1)
            .map(hits -> hits.getAt(0));
    }

    private SearchRequest buildSearchRequest(final NFSObserverEvent event, final AbstractDataStorage dataStorage) {
        final SearchSourceBuilder source = new SearchSourceBuilder()
            .query(QueryBuilders.boolQuery()
                       .must(QueryBuilders.termQuery("path", event.getFilePath()))
                       .must(QueryBuilders.termQuery("storage_id", dataStorage.getId())))
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

    private Supplier<TemporaryCredentials> getListingCredentialsSupplier(final AbstractDataStorage eventsStorage) {
        return () -> getTemporaryCredentials(eventsStorage, true, false);
    }

    private Supplier<TemporaryCredentials> getReadingCredentialsSupplier(final AbstractDataStorage eventsStorage) {
        return () -> getTemporaryCredentials(eventsStorage, false, true);
    }

    private TemporaryCredentials getTemporaryCredentials(final AbstractDataStorage dataStorage,
                                                         final boolean list,
                                                         final boolean read) {
        log.debug("Retrieving {} data storage {} temporary credentials...", DataStorageType.S3, dataStorage.getPath());
        final DataStorageAction action = new DataStorageAction();
        action.setBucketName(dataStorage.getPath());
        action.setId(dataStorage.getId());
        action.setRead(read);
        action.setList(list);
        return getCloudPipelineAPIClient().generateTemporaryCredentials(Collections.singletonList(action));
    }
}
