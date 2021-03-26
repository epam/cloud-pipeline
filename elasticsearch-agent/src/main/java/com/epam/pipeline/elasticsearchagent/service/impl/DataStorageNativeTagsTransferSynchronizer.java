package com.epam.pipeline.elasticsearchagent.service.impl;

import com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer;
import com.epam.pipeline.elasticsearchagent.service.ObjectStorageFileManager;
import com.epam.pipeline.utils.StreamUtils;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.vo.data.storage.DataStorageTagInsertBatchRequest;
import com.epam.pipeline.vo.data.storage.DataStorageTagInsertRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@ConditionalOnProperty(value = "sync.native.tags.transfer.disable", matchIfMissing = true, havingValue = "false")
public class DataStorageNativeTagsTransferSynchronizer implements ElasticsearchSynchronizer {

    private final CloudPipelineAPIClient cloudPipelineAPIClient;
    private final Map<DataStorageType, ObjectStorageFileManager> fileManagers;
    private final int bulkInsertSize;

    public DataStorageNativeTagsTransferSynchronizer(
            final CloudPipelineAPIClient cloudPipelineAPIClient,
            final List<ObjectStorageFileManager> fileManagers,
            @Value("${sync.native.tags.transfer.bulk.insert.size:1000}") final int bulkInsertSize) {
        this.cloudPipelineAPIClient = cloudPipelineAPIClient;
        this.fileManagers = fileManagers.stream()
                .collect(Collectors.toMap(ObjectStorageFileManager::getType, Function.identity()));
        this.bulkInsertSize = bulkInsertSize;
    }

    @Override
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void synchronize(final LocalDateTime lastSyncTime, final LocalDateTime syncStart) {
        log.debug("Started data storage native tags transfer synchronization");
        cloudPipelineAPIClient.loadAllDataStorages()
                .parallelStream()
                .forEach(storage -> {
                    log.debug("Started {} data storage {} native tags transfer synchronization", 
                            storage.getType(), storage.getPath());
                    try {
                        final boolean isVersioningEnabled = storage.isVersioningEnabled();
                        Optional.ofNullable(storage.getType()).map(fileManagers::get)
                                .map(fileManager -> fileManager
                                        .versionsWithNativeTags(storage.getRoot(),
                                                Optional.ofNullable(storage.getPrefix()).orElse(StringUtils.EMPTY),
                                                () -> getTemporaryCredentials(storage))
                                        .map(chunk -> isVersioningEnabled ? versionedTags(chunk) : tags(chunk))
                                        .map(stream -> stream.collect(Collectors.toList()))
                                        .filter(CollectionUtils::isNotEmpty))
                                .map(stream -> StreamUtils.windowed(stream, bulkInsertSize))
                                .orElseGet(Stream::empty)
                                .map(DataStorageTagInsertBatchRequest::new)
                                .forEach(request -> {
                                    cloudPipelineAPIClient.insertDataStorageTags(storage.getId(), request);
                                    log.debug("{} native tags of {} data storage {} have been transferred",
                                            request.getRequests().size(), storage.getType(), storage.getPath());
                                });
                    } catch (Exception e) {
                        log.error(String.format("Failed %s data storage %s native tags transfer synchronization", 
                                storage.getType(), storage.getPath()), e);
                    }
                    log.debug("Finished {} data storage {} native tags transfer synchronization",
                            storage.getType(), storage.getPath());
                });
        log.debug("Finished data storage native tags transfer synchronization");
    }

    private Stream<DataStorageTagInsertRequest> versionedTags(final DataStorageFile file) {
        final Stream<DataStorageTagInsertRequest> dataStorageTagInsertRequestStream =
                MapUtils.emptyIfNull(file.getTags()).entrySet().stream()
                        .map(e -> new DataStorageTagInsertRequest(
                                file.getPath(), file.getVersion(), e.getKey(), e.getValue()));
        return BooleanUtils.toBoolean(MapUtils.emptyIfNull(file.getLabels()).get("LATEST"))
                ? dataStorageTagInsertRequestStream.flatMap(r -> Stream.of(r,
                new DataStorageTagInsertRequest(r.getPath(), null, r.getKey(), r.getValue())))
                : dataStorageTagInsertRequestStream;
    }

    private Stream<DataStorageTagInsertRequest> tags(final DataStorageFile file) {
        return MapUtils.emptyIfNull(file.getTags()).entrySet().stream()
                .map(e -> new DataStorageTagInsertRequest(
                        file.getPath(), null, e.getKey(), e.getValue()));
    }

    private TemporaryCredentials getTemporaryCredentials(final AbstractDataStorage storage) {
        log.debug("Retrieving {} data storage {} temporary credentials...", storage.getType(), storage.getPath());
        final DataStorageAction action = new DataStorageAction();
        action.setBucketName(storage.getPath());
        action.setId(storage.getId());
        action.setWrite(false);
        action.setWriteVersion(false);
        action.setRead(true);
        action.setReadVersion(true);
        action.setList(true);
        action.setListVersion(true);
        return cloudPipelineAPIClient.generateTemporaryCredentials(Collections.singletonList(action));
    }
}
