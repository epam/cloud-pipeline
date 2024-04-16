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

package com.epam.pipeline.manager.datastorage;

import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageListingFilter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@SuppressWarnings("HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DataStorageListingFilterUtils {

    public static List<AbstractDataStorageItem> filterStorageItems(final List<AbstractDataStorageItem> items,
                                                                   final DataStorageListingFilter filter) {
        final DateFormat dateParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

        if (Objects.isNull(filter)) {
            return items;
        }

        return ListUtils.emptyIfNull(items).stream()
                .filter(item -> item instanceof DataStorageFile)
                .map(item -> (DataStorageFile) item)
                .filter(item -> matchNameFilter(item, filter))
                .filter(item -> matchDateFilters(item, filter, dateParser))
                .filter(item -> matchSizeFilters(item, filter))
                .collect(Collectors.toList());
    }

    private static boolean matchNameFilter(final DataStorageFile item, final DataStorageListingFilter filter) {
        if (StringUtils.isBlank(filter.getNameFilter())) {
            return true;
        }
        if (StringUtils.isBlank(item.getName())) {
            return false;
        }
        return StringUtils.containsIgnoreCase(item.getName(), filter.getNameFilter());
    }

    private static boolean matchDateFilters(final DataStorageFile item, final DataStorageListingFilter filter,
                                            final DateFormat dateParser) {
        if (Objects.isNull(filter.getDateAfter()) && Objects.isNull(filter.getDateBefore())) {
            return true;
        }
        if (StringUtils.isBlank(item.getChanged())) {
            return false;
        }
        try {
            final Date itemChangedDate = dateParser.parse(item.getChanged());
            if (Objects.nonNull(filter.getDateAfter()) && itemChangedDate.before(filter.getDateAfter())) {
                return false;
            }
            return !Objects.nonNull(filter.getDateBefore()) || !itemChangedDate.after(filter.getDateBefore());
        } catch (ParseException e) {
            throw new DataStorageException(e);
        }
    }

    private static boolean matchSizeFilters(final DataStorageFile item, final DataStorageListingFilter filter) {
        if (Objects.isNull(filter.getSizeGreaterThan()) && Objects.isNull(filter.getSizeLessThan())) {
            return true;
        }
        final Long itemSize = item.getSize();
        if (Objects.isNull(itemSize)) {
            return false;
        }
        if (Objects.nonNull(filter.getSizeGreaterThan()) && itemSize < filter.getSizeGreaterThan()) {
            return false;
        }
        return !Objects.nonNull(filter.getSizeLessThan()) || itemSize <= filter.getSizeLessThan();
    }
}
