package com.epam.pipeline.elasticsearchagent.service.index;

import com.epam.pipeline.elasticsearchagent.model.PermissionsContainer;
import com.epam.pipeline.elasticsearchagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.elasticsearchagent.service.impl.IndexRequestContainer;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.storage.StorageFileMapper;
import com.epam.pipeline.elasticsearchagent.service.lock.LockService;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.S3bucketDataStorage;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.vo.EntityPermissionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.elasticsearchagent.utils.ESConstants.DOC_MAPPING_TYPE;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageDocumentService {

    private final StorageFileMapper mapper = new StorageFileMapper();
    private final CloudPipelineAPIClient cloudPipelineAPIClient;
    private final ElasticsearchServiceClient elasticsearchServiceClient;
    private final LockService lockService;

    @Value("${sync.s3-file.bulk.insert.size:1000}")
    private Integer bulkInsertSize;

    @Value("${sync.s3-file.tag.value.delimiter:;}")
    private String tagDelimiter;

    @Value("${sync.index.common.prefix}")
    private String indexPrefix;
    @Value("${sync.s3-file.index.name}")
    private String indexName;
    @Value("${sync.s3-file.index.mapping}")
    private String indexSettingsPath;

    public void indexFile(final Long storageId,
                          final List<DataStorageFile> files) {
        final AbstractDataStorage storage = cloudPipelineAPIClient.loadDataStorage(storageId);
        final String region = getRegion(storage);

        final EntityPermissionVO entityPermission = cloudPipelineAPIClient
                .loadPermissionsForEntity(storage.getId(), storage.getAclClass());

        final PermissionsContainer permissionsContainer = new PermissionsContainer();
        permissionsContainer.add(Optional.ofNullable(entityPermission)
                .map(EntityPermissionVO::getPermissions)
                .orElse(Collections.emptySet()), storage.getOwner());

        SimpleLock simpleLock = null;
        try {
            simpleLock = lockService.obtainLock(storageId);
            final List<String> indices = getIndices(storageId);
            if (CollectionUtils.isEmpty(indices)) {
                log.debug("Index for storage {} doesn't exist yet. Documents won't be inserted.", storageId);
                return;
            }

            log.debug("Inserting {} document(s) for storage {} into indices {}",
                    files.size(), storageId, indices);

            indices.forEach(index -> {
                try (IndexRequestContainer requestContainer = getRequestContainer(index, bulkInsertSize)) {
                    files.stream()
                            .map(file -> createIndexRequest(
                                    file, storage, permissionsContainer, index, region)
                            ).forEach(requestContainer::add);
                }
            });
        } finally {
            if (simpleLock != null) {
                simpleLock.unlock();
            }
        }
    }

    private String getRegion(final AbstractDataStorage storage) {
        if (storage instanceof S3bucketDataStorage) {
            final Long regionId = ((S3bucketDataStorage) storage).getRegionId();
            return cloudPipelineAPIClient.loadRegion(regionId).getRegionCode();
        }
        return null;
    }

    private List<String> getIndices(final Long storageId) {
        final String indexPattern = indexPrefix + indexName +  String.format("-%d", storageId);
        final List<String> indices = elasticsearchServiceClient.findIndices("*-" + indexPattern);
        return indices;
    }

    private IndexRequestContainer getRequestContainer(final String indexName, final int bulkInsertSize) {
        return new IndexRequestContainer(requests -> elasticsearchServiceClient.sendRequests(indexName, requests),
                bulkInsertSize);
    }

    private IndexRequest createIndexRequest(final DataStorageFile file,
                                            final AbstractDataStorage dataStorage,
                                            final PermissionsContainer permissionsContainer,
                                            final String indexName,
                                            final String region) {
        return new IndexRequest(indexName, DOC_MAPPING_TYPE, file.getPath())
                .source(mapper.fileToDocument(file, dataStorage, region,
                        permissionsContainer, getDocumentType(dataStorage), tagDelimiter, null));
    }

    private SearchDocumentType getDocumentType(final AbstractDataStorage dataStorage) {
        switch (dataStorage.getType()) {
            case AZ: return SearchDocumentType.AZ_BLOB_FILE;
            case GS: return SearchDocumentType.GS_FILE;
            case NFS: return SearchDocumentType.NFS_FILE;
            default: return SearchDocumentType.S3_FILE;
        }
    }
}
