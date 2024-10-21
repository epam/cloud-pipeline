/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.datastorage.providers.aws.omics;

import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class OmicsPageIterator implements Iterator<List<DataStorageFile>> {

    private String nextKeyMarker;
    private List<DataStorageFile> items;
    private final Function<String, DataStorageListing> fetcher;

    @Override
    public boolean hasNext() {
        return items == null || StringUtils.isNotBlank(nextKeyMarker);
    }

    @Override
    public List<DataStorageFile> next() {
        final DataStorageListing listing = fetcher.apply(nextKeyMarker);
        if (listing.getNextPageMarker() != null) {
            nextKeyMarker = listing.getNextPageMarker();
        } else {
            nextKeyMarker = null;
        }
        items = listing.getResults().stream()
                .map(i -> i instanceof DataStorageFile ? (DataStorageFile) i : null)
                .filter(Objects::nonNull).collect(Collectors.toList());
        return items;
    }
}
