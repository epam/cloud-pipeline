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

package com.epam.pipeline.dts.listing.service;

import com.epam.pipeline.dts.listing.exception.ForbiddenException;
import com.epam.pipeline.dts.listing.exception.NotFoundException;
import com.epam.pipeline.dts.listing.model.ListingItemsPaging;
import com.epam.pipeline.dts.listing.model.ListingItemType;
import com.epam.pipeline.dts.listing.model.ListingItem;
import com.epam.pipeline.dts.listing.rest.dto.ItemsListingRequestDTO;
import com.epam.pipeline.dts.listing.service.impl.LocalListingService;
import com.epam.pipeline.dts.transfer.service.AbstractTransferTest;
import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LocalListingServiceTest extends AbstractTransferTest {
    private static final String ROOT_FOLDER = "/root";
    private static final String FILE1 = "file1";
    private static final String FILE2 = "file2";
    private static final String FOLDER = "folder";
    private static final int ALL_PERMISSIONS = 7;

    private final LocalListingService listingService = new LocalListingService();

    @Test
    void listingShouldReturnFolderContent() throws IOException {
        try (FileSystem fs = MemoryFileSystemBuilder.newEmpty().build()) {
            Path pathToFolder = fs.getPath(ROOT_FOLDER);
            Files.createDirectory(pathToFolder);
            Path file1 = pathToFolder.resolve(FILE1);
            Files.createFile(file1);
            Path folder = pathToFolder.resolve(FOLDER);
            Files.createDirectory(folder);
            Path file2 = folder.resolve(FILE2);
            Files.createFile(file2);

            List<ListingItem> actual = listingService.list(new ItemsListingRequestDTO(pathToFolder, null, null, null))
                    .getResults();
            List<ListingItem> expected = Stream
                    .of(ListingItem
                                    .builder()
                                    .type(ListingItemType.File)
                                    .permission(ALL_PERMISSIONS)
                                    .path(FILE1)
                                    .size(0L)
                                    .name(FILE1)
                                    .changed(Files.getLastModifiedTime(file1).toString())
                                    .build(),
                            ListingItem
                                    .builder()
                                    .type(ListingItemType.Folder)
                                    .permission(ALL_PERMISSIONS)
                                    .path(FOLDER)
                                    .name(FOLDER)
                                    .build())
                    .collect(Collectors.toList());
            assertTransferItems(expected, actual);
        }
    }

    @Test
    void listingShouldReturnFile() throws IOException {
        try (FileSystem fs = MemoryFileSystemBuilder.newEmpty().build()) {
            Path pathToFolder = fs.getPath(ROOT_FOLDER);
            Files.createDirectory(pathToFolder);
            Path file1 = pathToFolder.resolve(FILE1);
            Files.createFile(file1);

            List<ListingItem> actual = listingService.list(new ItemsListingRequestDTO(file1, null, null, null))
                    .getResults();
            List<ListingItem> expected = Stream
                    .of(ListingItem
                                    .builder()
                                    .type(ListingItemType.File)
                                    .permission(ALL_PERMISSIONS)
                                    .path(FILE1)
                                    .size(0L)
                                    .name(FILE1)
                                    .changed(Files.getLastModifiedTime(file1).toString())
                                    .build())
                    .collect(Collectors.toList());
            assertTransferItems(expected, actual);
        }
    }

    @Test
    void listingShouldReturnFileWithNoExecutePermission() throws IOException {
        try (FileSystem fs = getFSWithPosixAttributes()) {
            Path file = createFileWithPermissions(fs, Stream
                    .of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
                    .collect(Collectors.toSet()));

            List<ListingItem> actual = listingService.list(new ItemsListingRequestDTO(file, null, null, null))
                    .getResults();
            List<ListingItem> expected = Stream
                    .of(ListingItem
                            .builder()
                            .type(ListingItemType.File)
                            .permission(3)
                            .path(FILE1)
                            .size(0L)
                            .name(FILE1)
                            .changed(Files.getLastModifiedTime(file).toString())
                            .build())
                    .collect(Collectors.toList());
            assertTransferItems(expected, actual);
        }
    }

    @Test
    void listingShouldReturnFileWithNoWritePermission() throws IOException {
        try (FileSystem fs = getFSWithPosixAttributes()) {
            Path file = createFileWithPermissions(fs, Stream
                    .of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE)
                    .collect(Collectors.toSet()));

            List<ListingItem> actual = listingService.list(new ItemsListingRequestDTO(file, null, null, null))
                    .getResults();
            List<ListingItem> expected = Stream
                    .of(ListingItem
                            .builder()
                            .type(ListingItemType.File)
                            .permission(5)
                            .path(FILE1)
                            .size(0L)
                            .name(FILE1)
                            .changed(Files.getLastModifiedTime(file).toString())
                            .build())
                    .collect(Collectors.toList());
            assertTransferItems(expected, actual);
        }
    }

    @Test
    void listingShouldFailIfFolderHasNoReadPermission() throws IOException {
        try (FileSystem fs = getFSWithPosixAttributes()) {
            Path pathToFolder = fs.getPath(ROOT_FOLDER);
            Files.createDirectory(pathToFolder);
            Path file1 = pathToFolder.resolve(FILE1);
            Files.createFile(file1);
            Files.setPosixFilePermissions(pathToFolder, Collections.emptySet());
            assertThrows(ForbiddenException.class, 
                () -> listingService.list(new ItemsListingRequestDTO(pathToFolder, null, null, null)));
        }
    }

    @Test
    void listingShouldFailIfFolderDoesNotExist() throws IOException {
        try (FileSystem fs = getFSWithPosixAttributes()) {
            Path pathToFolder = fs.getPath(ROOT_FOLDER);
            assertThrows(NotFoundException.class, 
                () -> listingService.list(new ItemsListingRequestDTO(pathToFolder, null, null, null)));
        }
    }

    @Test
    void listingShouldReturnFolderContentWithPaging() {
        try (FileSystem fs = MemoryFileSystemBuilder.newEmpty().build()) {
            Path pathToFolder = fs.getPath(ROOT_FOLDER);
            Files.createDirectory(pathToFolder);
            Path file1 = pathToFolder.resolve(FILE1);
            Files.createFile(file1);
            Path folder = pathToFolder.resolve(FOLDER);
            Files.createDirectory(folder);
            Path file2 = folder.resolve(FILE2);
            Files.createFile(file2);

            ListingItemsPaging result = listingService.list(new ItemsListingRequestDTO(pathToFolder, 1, "1", null));
            List<ListingItem> actual = result.getResults();
            List<ListingItem> expected = Stream
                    .of(ListingItem
                                    .builder()
                                    .type(ListingItemType.File)
                                    .permission(ALL_PERMISSIONS)
                                    .path(FILE1)
                                    .size(0L)
                                    .name(FILE1)
                                    .changed(Files.getLastModifiedTime(file1).toString())
                                    .build())
                    .collect(Collectors.toList());
            assertTransferItems(expected, actual);
            assertThat(result.getNextPageMarker(), is("2"));

            result = listingService.list(new ItemsListingRequestDTO(pathToFolder, 1, "2", null));
            actual = result.getResults();
            expected = Stream
                    .of(ListingItem
                            .builder()
                            .type(ListingItemType.Folder)
                            .permission(ALL_PERMISSIONS)
                            .path(FOLDER)
                            .name(FOLDER)
                            .build())
                    .collect(Collectors.toList());
            assertTransferItems(expected, actual);
            assertNull(result.getNextPageMarker());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void assertTransferItems(List<ListingItem> expected, List<ListingItem> actual) {
        Map<String, ListingItem> expectedMap = expected
                .stream()
                .collect(Collectors.toMap(ListingItem::getPath, Function.identity()));
        assertThat(actual.size(), is(expected.size()));
        actual.forEach(item -> assertThat(item, is(expectedMap.get(item.getPath()))));
    }

    private static FileSystem getFSWithPosixAttributes() throws IOException {
        return MemoryFileSystemBuilder
                .newLinux()
                .addFileAttributeView(PosixFileAttributeView.class)
                .build();
    }

    private static Path createFileWithPermissions(FileSystem fileSystem,
                                                  Set<PosixFilePermission> permissions) throws IOException {
        Path pathToFolder = fileSystem.getPath(ROOT_FOLDER);
        Files.createDirectory(pathToFolder);
        Path file = pathToFolder.resolve(FILE1);
        Files.createFile(file);
        Files.setPosixFilePermissions(file, permissions);
        return file;
    }
}
