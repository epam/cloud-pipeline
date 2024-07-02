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

import com.epam.pipeline.elasticsearchagent.service.ObjectStorageFileManager;
import com.epam.pipeline.elasticsearchagent.utils.ESConstants;
import com.epam.pipeline.utils.StreamUtils;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.microsoft.azure.storage.blob.AnonymousCredentials;
import com.microsoft.azure.storage.blob.BlobListingDetails;
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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.net.URL;
import java.sql.Date;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.epam.pipeline.elasticsearchagent.utils.ESConstants.HIDDEN_FILE_NAME;

@Slf4j
@RequiredArgsConstructor
public class AzureBlobManager implements ObjectStorageFileManager {

    private static final String BLOB_URL_FORMAT = "https://%s.blob.core.windows.net%s";
    private static final int LIST_PAGE_SIZE = 1000;

    @Getter
    private final DataStorageType type = DataStorageType.AZ;

    @Override
    public Stream<DataStorageFile> files(final String storage,
                                         final String path,
                                         final Supplier<TemporaryCredentials> credentialsSupplier) {
        return StreamUtils.from(new AzureFlatSegmentIterator(buildContainerUrl(storage, credentialsSupplier), path))
                .map(response -> Optional.of(response.body())
                        .map(ListBlobsFlatSegmentResponse::segment)
                        .map(BlobFlatListSegment::blobItems)
                        .orElseGet(Collections::emptyList))
                .flatMap(List::stream)
                .filter(blob -> !Objects.equals(blob.name(), null))
                .filter(blob -> !StringUtils.endsWithIgnoreCase(blob.name(), HIDDEN_FILE_NAME.toLowerCase()))
                .map(this::convertToStorageFile);
    }

    @Override
    public Stream<DataStorageFile> versions(final String storage,
                                            final String path,
                                            final Supplier<TemporaryCredentials> credentialsSupplier,
                                            final boolean showDeleted) {
        return files(storage, path, credentialsSupplier);
    }

    @Override
    public Stream<DataStorageFile> versionsWithNativeTags(final String storage,
                                                          final String path,
                                                          final Supplier<TemporaryCredentials> credentialsSupplier) {
        return files(storage, path, credentialsSupplier);
    }

    private ContainerURL buildContainerUrl(final String storage,
                                           final Supplier<TemporaryCredentials> credentialsSupplier) {
        // TODO 26.03.2021: Support temporary credentials refresh mechanism
        final TemporaryCredentials credentials = credentialsSupplier.get();
        final ServiceURL serviceURL = new ServiceURL(
                url(String.format(BLOB_URL_FORMAT, credentials.getAccessKey(), credentials.getToken())),
                StorageURL.createPipeline(new AnonymousCredentials(), new PipelineOptions()));
        return serviceURL.createContainerURL(storage);
    }

    private DataStorageFile convertToStorageFile(final BlobItem blob) {
        final DataStorageFile file = new DataStorageFile();
        file.setName(FilenameUtils.getName(blob.name()));
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

    @RequiredArgsConstructor
    private static class AzureFlatSegmentIterator implements Iterator<ContainerListBlobFlatSegmentResponse> {

        private final ContainerURL container;
        private final String path;

        private String nextMarker;
        private ContainerListBlobFlatSegmentResponse response;

        @Override
        public boolean hasNext() {
            return response == null || StringUtils.isNotBlank(nextMarker);
        }

        @Override
        public ContainerListBlobFlatSegmentResponse next() {
            response = unwrap(container.listBlobsFlatSegment(nextMarker, new ListBlobsOptions()
                    .withPrefix(path)
                    .withMaxResults(LIST_PAGE_SIZE)
                    .withDetails(new BlobListingDetails()
                            .withMetadata(true))));
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
