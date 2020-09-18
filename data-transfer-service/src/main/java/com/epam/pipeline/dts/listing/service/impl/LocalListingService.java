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

package com.epam.pipeline.dts.listing.service.impl;

import com.epam.pipeline.dts.listing.exception.ForbiddenException;
import com.epam.pipeline.dts.listing.exception.NotFoundException;
import com.epam.pipeline.dts.listing.model.ListingItemsPaging;
import com.epam.pipeline.dts.listing.model.ListingItem;
import com.epam.pipeline.dts.listing.model.ListingItemType;
import com.epam.pipeline.dts.listing.service.ListingService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@ConditionalOnProperty(value = "dts.impersonation.enabled", havingValue = "false")
public class LocalListingService implements ListingService {
    private static final int READ_PERMISSION = 1;
    private static final int WRITE_PERMISSION = 1 << 1;
    private static final int EXECUTE_PERMISSION = 1 << 2;
    private static final int NO_PERMISSION = 0;
    private static final int MAX_DEPTH = 1;

    @Override
    public ListingItemsPaging list(@NotNull Path path, Integer pageSize, String marker) {
        verifyPath(path);
        verifyPagingAttributes(pageSize);
        long offset = StringUtils.isNumeric(marker) ? Long.parseLong(marker) : 1;
        Assert.isTrue(offset > 0, "Page marker must be greater than null");
        try (Stream<Path> files = Files.walk(path, MAX_DEPTH)) {
            List<ListingItem> items = files
                    .sorted()
                    .filter(file -> doNotSkipFile(file, path))
                    .skip(offset - 1)
                    .limit(normalizePageSize(pageSize))
                    .map(file -> ListingItem.builder()
                            .path(getRelativePath(file, path))
                            .type(determineListingItemType(file))
                            .permission(buildPermissions(file))
                            .name(file.getFileName().toString())
                            .size(getSize(file))
                            .changed(getLastModifiedTime(file))
                            .build())
                    .collect(Collectors.toList());
            return ListingItemsPaging.builder()
                    .results(items)
                    .nextPageMarker(getNextPageMarker(offset, pageSize, path))
                    .build();
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    String.format("An error occurred during listing local file %s.", path.toAbsolutePath()), e);
        }
    }

    private void verifyPagingAttributes(Integer pageSize) {
        Assert.isTrue(pageSize == null || pageSize > 0,
                String.format("Invalid paging attributes: page size - %s. Page size must be grater then zero,",
                        pageSize));
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null ? Long.MAX_VALUE : pageSize;
    }

    private String getNextPageMarker(long offset, Integer pageSize, Path path) throws IOException {
        if (pageSize == null) {
            return null;
        }
        Long nextOffset = offset + pageSize;
        try (Stream<Path> nextStream = Files.walk(path, MAX_DEPTH)) {
            if (nextStream.skip(nextOffset).findFirst().isPresent()) {
                return nextOffset.toString();
            }
        }
        return null;
    }

    private String getRelativePath(Path path, Path rootPath) {
        return Files.isDirectory(rootPath)
                ? rootPath.relativize(path).toString()
                : rootPath.getFileName().toString();
    }

    private Long getSize(Path path) {
        try {
            return !Files.isDirectory(path) ? Files.size(path) : null;
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Cannot get size for file %s.", path.toAbsolutePath()));
        }
    }

    private String getLastModifiedTime(Path path) {
        try {
            return !Files.isDirectory(path) ? Files.getLastModifiedTime(path).toString() : null;
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Cannot get last modified time for file %s.",
                    path.toAbsolutePath()));
        }
    }

    private static boolean doNotSkipFile(Path path, Path rootPath) {
        return Files.isRegularFile(path)
                || Files.isDirectory(path) && !path.toAbsolutePath().equals(rootPath.toAbsolutePath());
    }

    private ListingItemType determineListingItemType(Path path) {
        return Files.isDirectory(path) ? ListingItemType.Folder : ListingItemType.File;
    }

    private void verifyPath(Path path) {
        if (Files.notExists(path)) {
            throw new NotFoundException(String.format("Required folder or file %s not found", path));
        }
        if (!Files.isReadable(path)) {
            throw new ForbiddenException(String.format("No 'READ' permission for required folder or file %s", path));
        }
    }

    private int buildPermissions(Path path) {
        return (Files.isReadable(path) ? READ_PERMISSION : NO_PERMISSION)
                | (Files.isWritable(path) ? WRITE_PERMISSION : NO_PERMISSION)
                | (Files.isExecutable(path) ? EXECUTE_PERMISSION : NO_PERMISSION);
    }
}
