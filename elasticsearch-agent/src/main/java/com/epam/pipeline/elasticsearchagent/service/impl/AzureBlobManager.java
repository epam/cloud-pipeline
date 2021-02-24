/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.elasticsearchagent.service.impl;

import com.epam.pipeline.elasticsearchagent.model.PermissionsContainer;
import com.epam.pipeline.elasticsearchagent.service.ObjectStorageFileManager;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.storage.StorageFileMapper;
import com.epam.pipeline.elasticsearchagent.utils.ChunkedIterator;
import com.epam.pipeline.elasticsearchagent.utils.ESConstants;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.vo.data.storage.DataStorageTagLoadBatchRequest;
import com.epam.pipeline.vo.data.storage.DataStorageTagLoadRequest;
import com.microsoft.azure.storage.blob.AnonymousCredentials;
import com.microsoft.azure.storage.blob.ContainerURL;
import com.microsoft.azure.storage.blob.ListBlobsOptions;
import com.microsoft.azure.storage.blob.PipelineOptions;
import com.microsoft.azure.storage.blob.ServiceURL;
import com.microsoft.azure.storage.blob.StorageException;
import com.microsoft.azure.storage.blob.StorageURL;
import com.microsoft.azure.storage.blob.models.BlobFlatListSegment;
import com.microsoft.azure.storage.blob.models.BlobItem;
import com.microsoft.azure.storage.blob.models.ContainerListBlobFlatSegmentResponse;
import com.microsoft.azure.storage.blob.models.ListBlobsFlatSegmentResponse;
import com.microsoft.azure.storage.blob.models.StorageErrorException;
import io.reactivex.Single;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.action.index.IndexRequest;

import java.net.URL;
import java.sql.Date;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.epam.pipeline.elasticsearchagent.utils.ESConstants.DOC_MAPPING_TYPE;
import static com.epam.pipeline.elasticsearchagent.utils.ESConstants.HIDDEN_FILE_NAME;

@Slf4j
@RequiredArgsConstructor
public class AzureBlobManager implements ObjectStorageFileManager {

    private static final String BLOB_URL_FORMAT = "https://%s.blob.core.windows.net%s";
    private static final int LIST_PAGE_SIZE = 1000;

    private final CloudPipelineAPIClient cloudPipelineAPIClient;
    private final StorageFileMapper fileMapper = new StorageFileMapper();

    public void listAndIndexFiles(final String indexName,
                                  final AbstractDataStorage storage,
                                  final TemporaryCredentials credentials,
                                  final PermissionsContainer permissions,
                                  final IndexRequestContainer indexContainer) {
        iterateFilesInChunks(storage, credentials).forEachRemaining(chunk -> {
            final Map<String, Map<String, String>> pathTags = cloudPipelineAPIClient.loadDataStorageTagsMap(
                    storage.getId(),
                    new DataStorageTagLoadBatchRequest(
                            chunk.stream()
                                    .map(DataStorageFile::getPath)
                                    .map(DataStorageTagLoadRequest::new)
                                    .collect(Collectors.toList())));
            for (final DataStorageFile file : chunk) {
                file.setTags(pathTags.get(file.getPath()));
                indexContainer.add(createIndexRequest(file, storage, credentials.getRegion(), permissions, indexName));
            }
        });
    }

    private ChunkedIterator<DataStorageFile> iterateFilesInChunks(final AbstractDataStorage storage,
                                                                  final TemporaryCredentials credentials) {
        final int chunkSize = 100;
        return new ChunkedIterator<>(iterateFiles(storage, credentials), chunkSize);
    }

    private Iterator<DataStorageFile> iterateFiles(final AbstractDataStorage storage,
                                                   final TemporaryCredentials credentials) {
        final AzureFlatSegmentIterator segmentIterator = new AzureFlatSegmentIterator(buildContainerUrl(storage, credentials), "");
        final Spliterator<ContainerListBlobFlatSegmentResponse> spliterator = Spliterators.spliteratorUnknownSize(segmentIterator, 0);
        final Stream<ContainerListBlobFlatSegmentResponse> stream = StreamSupport.stream(spliterator, false);
        return stream
                .map(response -> Optional.of(response.body())
                        .map(ListBlobsFlatSegmentResponse::segment)
                        .map(BlobFlatListSegment::blobItems)
                        .orElseGet(Collections::emptyList))
                .flatMap(List::stream)
                .filter(blob -> !Objects.equals(blob.name(), null))
                .filter(blob -> !StringUtils.endsWithIgnoreCase(blob.name(), HIDDEN_FILE_NAME.toLowerCase()))
                .map(this::convertToStorageFile)
                .iterator();
    }

    private ContainerURL buildContainerUrl(final AbstractDataStorage storage,
                                           final TemporaryCredentials credentials) {
        final AnonymousCredentials creds = new AnonymousCredentials();
        final ServiceURL serviceURL = new ServiceURL(
                url(String.format(BLOB_URL_FORMAT, credentials.getAccessKey(), credentials.getToken())),
                StorageURL.createPipeline(creds, new PipelineOptions()));
        return serviceURL.createContainerURL(storage.getPath());
    }

    private IndexRequest createIndexRequest(final DataStorageFile item,
                                            final AbstractDataStorage storage,
                                            final String region,
                                            final PermissionsContainer permissions,
                                            final String indexName) {
        return new IndexRequest(indexName, DOC_MAPPING_TYPE)
                .source(fileMapper.fileToDocument(item, storage, region, permissions, SearchDocumentType.AZ_BLOB_FILE));
    }

    private DataStorageFile convertToStorageFile(final BlobItem blob) {
        final DataStorageFile file = new DataStorageFile();
        file.setName(blob.name());
        file.setPath(blob.name());
        file.setSize(blob.properties().contentLength());
        file.setChanged(ESConstants.FILE_DATE_FORMAT.format(Date.from(blob.properties().lastModified().toInstant())));
        if (blob.properties().accessTier() != null) {
            file.setLabels(Collections.singletonMap(ESConstants.STORAGE_CLASS_LABEL,
                    blob.properties().accessTier().toString()));
        }
        file.setTags(blob.metadata());
        return file;
    }

    @SneakyThrows
    private URL url(final String blobUrl) {
        return new URL(blobUrl);
    }

    private static class AzureFlatSegmentIterator implements Iterator<ContainerListBlobFlatSegmentResponse> {

        private final ContainerURL container;
        private final String path;
        private final int pageSize;

        private String nextMarker;
        private ContainerListBlobFlatSegmentResponse response = null;

        private AzureFlatSegmentIterator(final ContainerURL container, final String path, final String nextMarker,
                                         final int pageSize) {
            this.container = container;
            this.path = path;
            this.pageSize = pageSize;
        }

        public AzureFlatSegmentIterator(final ContainerURL container, final String path) {
            this(container, path, null, LIST_PAGE_SIZE);
        }

        @Override
        public boolean hasNext() {
            return response == null || StringUtils.isNotBlank(nextMarker);
        }

        @Override
        public ContainerListBlobFlatSegmentResponse next() {
            response = unwrap(container.listBlobsFlatSegment(nextMarker, new ListBlobsOptions()
                    .withPrefix(path)
                    .withMaxResults(pageSize)));
            nextMarker = Optional.ofNullable(response)
                    .map(ContainerListBlobFlatSegmentResponse::body)
                    .map(ListBlobsFlatSegmentResponse::nextMarker)
                    .orElse(null);
            return response;
        }

        private <T> T unwrap(final Single<T> single) {
            final Pair<T, Throwable> pair = single
                    .map(this::success)
                    .onErrorReturn(this::failure)
                    .blockingGet();
            return pair.getLeft() != null ? pair.getLeft() : throwException(pair.getRight());
        }

        private <T> Pair<T, Throwable> success(final T t) {
            return Pair.of(t, null);
        }

        private <T> Pair<T, Throwable> failure(final Throwable e) {
            return Pair.of(null, e);
        }

        private <T> T throwException(final Throwable e) {
            log.debug("Exception occurred while calling Azure API.", e);
            if (e instanceof StorageException) {
                throw new DataStorageException(((StorageException) e).message(), e);
            } else if (e instanceof StorageErrorException) {
                throw new DataStorageException(((StorageErrorException) e).body().message(), e);
            } else {
                throw new DataStorageException(e.getMessage(), e);
            }
        }
    }

}
