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
import com.epam.pipeline.elasticsearchagent.utils.ESConstants;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.microsoft.azure.storage.blob.AnonymousCredentials;
import com.microsoft.azure.storage.blob.ContainerURL;
import com.microsoft.azure.storage.blob.ListBlobsOptions;
import com.microsoft.azure.storage.blob.PipelineOptions;
import com.microsoft.azure.storage.blob.ServiceURL;
import com.microsoft.azure.storage.blob.StorageURL;
import com.microsoft.azure.storage.blob.models.BlobItem;
import com.microsoft.azure.storage.blob.models.ContainerListBlobFlatSegmentResponse;
import io.reactivex.Single;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.action.index.IndexRequest;

import java.net.URL;
import java.sql.Date;
import java.util.Collections;

import static com.epam.pipeline.elasticsearchagent.utils.ESConstants.DOC_MAPPING_TYPE;
import static com.epam.pipeline.elasticsearchagent.utils.ESConstants.HIDDEN_FILE_NAME;

@Slf4j
@RequiredArgsConstructor
public class AzureBlobManager implements ObjectStorageFileManager {

    private static final String BLOB_URL_FORMAT = "https://%s.blob.core.windows.net%s";
    private static final int LIST_PAGE_SIZE = 1000;
    private final StorageFileMapper fileMapper = new StorageFileMapper();

    public void listAndIndexFiles(final String indexName,
                                  final AbstractDataStorage storage,
                                  final TemporaryCredentials credentials,
                                  final PermissionsContainer permissions,
                                  final IndexRequestContainer indexContainer) {
        final ContainerURL containerURL = buildContainerUrl(storage, credentials);
        final ListBlobsOptions options = new ListBlobsOptions()
                .withMaxResults(LIST_PAGE_SIZE)
                .withPrefix("");
        unwrap(containerURL.listBlobsFlatSegment(null, options)
                .flatMap(response -> listBlobs(containerURL, response, indexContainer,
                        storage, credentials.getRegion(), permissions, indexName)));
    }

    private ContainerURL buildContainerUrl(final AbstractDataStorage storage,
                                           final TemporaryCredentials credentials) {
        final AnonymousCredentials creds = new AnonymousCredentials();
        final ServiceURL serviceURL = new ServiceURL(
                url(String.format(BLOB_URL_FORMAT, credentials.getAccessKey(), credentials.getToken())),
                StorageURL.createPipeline(creds, new PipelineOptions()));
        return serviceURL.createContainerURL(storage.getPath());
    }

    private Single<ContainerListBlobFlatSegmentResponse> listBlobs(
            final ContainerURL containerURL,
            final ContainerListBlobFlatSegmentResponse response,
            final IndexRequestContainer indexContainer,
            final AbstractDataStorage storage,
            final String region,
            final PermissionsContainer permissions,
            final String indexName) {
        ListUtils.emptyIfNull(response.body().segment().blobItems())
                .forEach(blob -> indexContainer.add(blobToIndexRequest(blob, storage, region, permissions, indexName)));
        if (response.body().nextMarker() == null) {
            return Single.just(response);
        } else {
            final String nextMarker = response.body().nextMarker();
            return containerURL.listBlobsFlatSegment(nextMarker, new ListBlobsOptions().withMaxResults(1), null)
                    .flatMap(containersListBlobFlatSegmentResponse ->
                            listBlobs(containerURL, containersListBlobFlatSegmentResponse,
                                    indexContainer, storage, region, permissions, indexName));
        }
    }

    private IndexRequest blobToIndexRequest(final BlobItem blob,
                                            final AbstractDataStorage storage,
                                            final String region,
                                            final PermissionsContainer permissions,
                                            final String indexName) {
        return createIndexRequest(convertToStorageFile(blob), storage, region, permissions, indexName);
    }

    private IndexRequest createIndexRequest(final DataStorageFile item,
                                            final AbstractDataStorage storage,
                                            final String region,
                                            final PermissionsContainer permissions,
                                            final String indexName) {
        return new IndexRequest(indexName, DOC_MAPPING_TYPE)
                .source(fileMapper.fileToDocument(item, storage, region, permissions));
    }

    private DataStorageFile convertToStorageFile(final BlobItem blob) {
        final String relativePath = blob.name();
        if (StringUtils.endsWithIgnoreCase(relativePath, HIDDEN_FILE_NAME.toLowerCase())) {
            return null;
        }
        final DataStorageFile file = new DataStorageFile();
        file.setName(relativePath);
        file.setPath(relativePath);
        file.setSize(blob.properties().contentLength());
        file.setChanged(ESConstants.FILE_DATE_FORMAT.format(Date.from(blob.properties().lastModified().toInstant())));
        if (blob.properties().accessTier() != null) {
            file.setLabels(Collections.singletonMap("StorageClass",  blob.properties().accessTier().toString()));
        }
        file.setTags(blob.metadata());
        return file;
    }

    @SneakyThrows
    private URL url(final String blobUrl) {
        return new URL(blobUrl);
    }

    private <T> T unwrap(final Single<T> single) {
        final Pair<T, Throwable> pair = single.map(this::success).onErrorReturn(this::failure).blockingGet();
        return pair.getLeft();
    }

    private <T> Pair<T, Throwable> success(final T t) {
        return Pair.of(t, null);
    }

    private <T> Pair<T, Throwable> failure(final Throwable e) {
        log.error(e.getMessage(), e);
        return Pair.of(null, e);
    }
}
