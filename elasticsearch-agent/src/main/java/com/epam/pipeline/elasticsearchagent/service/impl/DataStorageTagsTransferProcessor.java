package com.epam.pipeline.elasticsearchagent.service.impl;

import com.epam.pipeline.elasticsearchagent.service.ElasticsearchSynchronizer;
import com.epam.pipeline.elasticsearchagent.service.ObjectStorageFileManager;
import com.epam.pipeline.elasticsearchagent.utils.IteratorUtils;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.vo.data.storage.DataStorageTagInsertBatchRequest;
import com.epam.pipeline.vo.data.storage.DataStorageTagInsertRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
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
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "sync.tags.transfer.disable", matchIfMissing = true, havingValue = "false")
public class DataStorageTagsTransferProcessor implements ElasticsearchSynchronizer {

    private final CloudPipelineAPIClient cloudPipelineAPIClient;
    private final List<ObjectStorageFileManager> fileManagers;
    private final Map<DataStorageType, ObjectStorageFileManager> fileManagerMap = fileManagers.stream()
            .collect(Collectors.toMap(ObjectStorageFileManager::getType, Function.identity()));

    @Override
    public void synchronize(final LocalDateTime lastSyncTime, final LocalDateTime syncStart) {
        log.debug("Started data storage tags processor");
        cloudPipelineAPIClient.loadAllDataStorages()
                .parallelStream()
                .forEach(storage -> {
                    final boolean isVersioningEnabled = storage.isVersioningEnabled();
                    Optional.ofNullable(storage.getType()).map(fileManagerMap::get)
                            .map(it -> it.listVersionsWithTags(storage, getTemporaryCredentials(storage)))
                            .map(Stream::iterator)
                            .map(IteratorUtils::chunked)
                            .map(IteratorUtils::streamFrom)
                            .orElseGet(Stream::empty)
                            .map(chunk -> chunk.stream()
                                    .flatMap(isVersioningEnabled ? this::versionedTags : this::nonVersionedTags)
                                    .collect(Collectors.toList()))
                            .map(DataStorageTagInsertBatchRequest::new)
                            .forEach(request ->
                                    cloudPipelineAPIClient.insertDataStorageTags(storage.getId(), request));
                });
    }

    private Stream<DataStorageTagInsertRequest> versionedTags(final DataStorageFile file) {
        final Stream<DataStorageTagInsertRequest> dataStorageTagInsertRequestStream = file.getTags().entrySet().stream()
                .map(e -> new DataStorageTagInsertRequest(
                        file.getPath(), file.getVersion(), e.getKey(), e.getValue()));
        return BooleanUtils.toBoolean(MapUtils.emptyIfNull(file.getLabels()).get("LATEST"))
                ? dataStorageTagInsertRequestStream.flatMap(r -> Stream.of(r,
                new DataStorageTagInsertRequest(r.getPath(), null, r.getKey(), r.getValue())))
                : dataStorageTagInsertRequestStream;
    }

    private Stream<DataStorageTagInsertRequest> nonVersionedTags(final DataStorageFile file) {
        return file.getTags().entrySet().stream()
                .map(e -> new DataStorageTagInsertRequest(
                        file.getPath(), null, e.getKey(), e.getValue()));
    }

    private TemporaryCredentials getTemporaryCredentials(final AbstractDataStorage storage) {
        final DataStorageAction action = new DataStorageAction();
        action.setBucketName(storage.getPath());
        action.setId(storage.getId());
        action.setWrite(false);
        action.setRead(true);
        return cloudPipelineAPIClient.generateTemporaryCredentials(Collections.singletonList(action));
    }
}
