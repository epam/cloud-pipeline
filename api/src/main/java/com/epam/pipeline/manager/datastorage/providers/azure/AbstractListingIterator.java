/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.datastorage.providers.azure;

import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.microsoft.azure.storage.blob.ContainerURL;
import com.microsoft.azure.storage.blob.ListBlobsOptions;
import com.microsoft.azure.storage.blob.models.ContainerListBlobFlatSegmentResponse;
import com.microsoft.azure.storage.blob.models.ContainerListBlobHierarchySegmentResponse;
import com.microsoft.azure.storage.blob.models.ListBlobsFlatSegmentResponse;
import com.microsoft.azure.storage.blob.models.ListBlobsHierarchySegmentResponse;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.epam.pipeline.manager.datastorage.providers.azure.AzureStorageHelper.unwrap;

abstract class AbstractListingIterator<T> implements Iterator<T> {
    protected final ContainerURL container;
    protected final String path;
    protected final int pageSize;
    @Getter
    protected String nextMarker;

    protected T response = null;

    private AbstractListingIterator(final ContainerURL container, final String path, final String nextMarker,
                                    final int pageSize) {
        this.container = container;
        this.path = path;
        this.nextMarker = nextMarker;
        this.pageSize = pageSize;
    }

    @Override
    public boolean hasNext() {
        return response == null || StringUtils.isNotBlank(nextMarker);
    }

    @Override
    public T next() {
        response = loadNextResponse();
        nextMarker = retrieveNextMarker(response);
        return response;
    }

    protected abstract T loadNextResponse();

    protected abstract String retrieveNextMarker(T response);

    public Stream<T> stream() {
        final Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(this, 0);
        return StreamSupport.stream(spliterator, false);
    }

    public static HierarchyIterator hierarchy(final ContainerURL container, final String path, final String nextMarker,
                                              final int pageSize) {
        return new HierarchyIterator(container, path, nextMarker, pageSize);
    }

    public static HierarchyIterator hierarchy(final ContainerURL container, final String path) {
        return new HierarchyIterator(container, path, null, AzureStorageHelper.MAX_PAGE_SIZE);
    }

    public static FlatIterator flat(final ContainerURL container, final String path, final String nextMarker,
                                    final int pageSize) {
        return new FlatIterator(container, path, nextMarker, pageSize);
    }

    public static FlatIterator flat(final ContainerURL container, final String path) {
        return new FlatIterator(container, path, null, AzureStorageHelper.MAX_PAGE_SIZE);
    }

    static class HierarchyIterator extends AbstractListingIterator<ContainerListBlobHierarchySegmentResponse> {

        private final ListBlobsOptions options = new ListBlobsOptions().withPrefix(path).withMaxResults(pageSize);

        private HierarchyIterator(final ContainerURL container, final String path, final String nextMarker,
                                  final int pageSize) {
            super(container, path, nextMarker, pageSize);
        }

        @Override
        protected ContainerListBlobHierarchySegmentResponse loadNextResponse() {
            return unwrap(container.listBlobsHierarchySegment(nextMarker, ProviderUtils.DELIMITER, options));
        }

        @Override
        protected String retrieveNextMarker(final ContainerListBlobHierarchySegmentResponse response) {
            return Optional.ofNullable(response)
                    .map(ContainerListBlobHierarchySegmentResponse::body)
                    .map(ListBlobsHierarchySegmentResponse::nextMarker)
                    .orElse(null);
        }
    }

    static class FlatIterator extends AbstractListingIterator<ContainerListBlobFlatSegmentResponse> {

        private final ListBlobsOptions options = new ListBlobsOptions().withPrefix(path).withMaxResults(pageSize);

        private FlatIterator(final ContainerURL container, final String path, final String nextMarker, final int pageSize) {
            super(container, path, nextMarker, pageSize);
        }

        @Override
        protected ContainerListBlobFlatSegmentResponse loadNextResponse() {
            return unwrap(container.listBlobsFlatSegment(nextMarker, options));
        }

        @Override
        protected String retrieveNextMarker(final ContainerListBlobFlatSegmentResponse response) {
            return Optional.ofNullable(response)
                    .map(ContainerListBlobFlatSegmentResponse::body)
                    .map(ListBlobsFlatSegmentResponse::nextMarker)
                    .orElse(null);
        }
    }
}
