/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.datastorage;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import com.epam.pipeline.utils.CommonUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DataStoragePathLoader {

    private final DataStorageDao dataStorageDao;
    private final MessageHelper messageHelper;
    private final CheckPermissionHelper permissionHelper;

    @SuppressWarnings("unchecked")
    public AbstractDataStorage loadDataStorageByPathOrId(final String pathOrId) {
        return CommonUtils.first(
            () -> loadById(pathOrId),
            () -> loadByNameOrPath(pathOrId),
            () -> loadByPrefixes(pathOrId))
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_NOT_FOUND, pathOrId)));
    }

    private Optional<AbstractDataStorage> loadById(final String id) {
        if (NumberUtils.isDigits(id)) {
            return Optional.ofNullable(dataStorageDao.loadDataStorage(Long.parseLong(id)));
        }
        return Optional.empty();
    }

    private Optional<AbstractDataStorage> loadByNameOrPath(final String path) {
        return Optional.ofNullable(dataStorageDao.loadDataStorageByNameOrPath(path, path));
    }

    // returns allowed to read storage with the longest prefix
    private Optional<AbstractDataStorage> loadByPrefixes(final String path) {
        final Collection<String> prefixes = splitPathByPrefix(path);
        final List<AbstractDataStorage> storages = dataStorageDao.loadDataStoragesByPrefixes(prefixes);
        return ListUtils.emptyIfNull(storages)
                .stream()
                .filter(storage -> permissionHelper.isAllowed("READ", storage))
                .max(Comparator.comparing(storage -> storage.getPath().length()));
    }

    private Collection<String> splitPathByPrefix(final String path) {
        final Set<String> result = new HashSet<>();
        result.add(path);
        int currentIndex = path.indexOf(ProviderUtils.DELIMITER);
        while (currentIndex != -1) {
            result.add(path.substring(0, currentIndex));
            currentIndex = path.indexOf(ProviderUtils.DELIMITER, currentIndex + 1);
        }
        //add all variants of prefixes to result - with and without trailing delimiter
        return Stream.concat(result.stream(), result.stream()
                .map(prefix -> prefix.endsWith(ProviderUtils.DELIMITER) ?
                        ProviderUtils.withoutTrailingDelimiter(prefix) :
                        ProviderUtils.withTrailingDelimiter(prefix)))
                .collect(Collectors.toSet());
    }
}
