/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.elasticsearchagent.service.impl.converter.storage;

import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.GSBucketStorage;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.search.SearchDocumentType;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.epam.pipeline.elasticsearchagent.TestConstants.PERMISSIONS_CONTAINER;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_PATH;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageFileMapperTest {

    private static final int MAX_DOCUMENT_ID_BYTES = 512;
    private static final String HASHED_DOCUMENT_ID_PREFIX = "sha256:";
    // takes two bytes in UTF-8
    private static final String TWO_BYTE_CHARACTER = "\u00e9";
    // SHA-256 of 513 'a' characters
    private static final String LONG_PATH_HASH = "02425c0f5b0dabf3d2b9115f3f7723a02ad8bcfb1534a0d231614fd42b8188f6";

    private final StorageFileMapper mapper = new StorageFileMapper();

    @Test
    void shouldUsePathAsDocumentIdWhenPathIsShort() {
        assertEquals(TEST_PATH, mapper.getDocumentId(createFile(TEST_PATH)));
    }

    @Test
    void shouldLeaveDocumentIdEmptyWhenPathIsMissing() {
        assertNull(mapper.getDocumentId(createFile(null)));
    }

    @Test
    void shouldUsePathAsDocumentIdWhenPathIsAtLimit() {
        final String path = StringUtils.repeat('a', MAX_DOCUMENT_ID_BYTES);
        assertEquals(path, mapper.getDocumentId(createFile(path)));
    }

    @Test
    void shouldUsePathHashAsDocumentIdWhenPathIsOverLimit() {
        final String path = StringUtils.repeat('a', MAX_DOCUMENT_ID_BYTES + 1);
        assertEquals(HASHED_DOCUMENT_ID_PREFIX + LONG_PATH_HASH, mapper.getDocumentId(createFile(path)));
    }

    @Test
    void shouldUseDifferentDocumentIdsForDifferentLongPaths() {
        final String path = StringUtils.repeat('a', MAX_DOCUMENT_ID_BYTES + 1);
        final String otherPath = StringUtils.repeat('b', MAX_DOCUMENT_ID_BYTES + 1);
        assertNotEquals(mapper.getDocumentId(createFile(path)), mapper.getDocumentId(createFile(otherPath)));
    }

    @Test
    void shouldCountPathBytesRatherThanCharacters() {
        final String pathAtLimit = StringUtils.repeat(TWO_BYTE_CHARACTER, MAX_DOCUMENT_ID_BYTES / 2);
        final String pathOverLimit = pathAtLimit + TWO_BYTE_CHARACTER;
        assertAll(
            () -> assertEquals(pathAtLimit, mapper.getDocumentId(createFile(pathAtLimit))),
            () -> assertTrue(mapper.getDocumentId(createFile(pathOverLimit))
                    .startsWith(HASHED_DOCUMENT_ID_PREFIX))
        );
    }

    @Test
    void shouldKeepFullPathInDocumentWhenDocumentIdIsHashed() {
        final String path = StringUtils.repeat('a', MAX_DOCUMENT_ID_BYTES + 1);
        final AbstractDataStorage dataStorage = new GSBucketStorage(
                1L, "storage", "storage", new StoragePolicy(), null);

        final Map<String, ?> document = mapper.fileToDocument(createFile(path), dataStorage, null,
                PERMISSIONS_CONTAINER, SearchDocumentType.GS_FILE, ";", null);

        assertAll(
            () -> assertEquals(path, document.get("id")),
            () -> assertEquals(path, document.get("path"))
        );
    }

    private DataStorageFile createFile(final String path) {
        final DataStorageFile file = new DataStorageFile();
        file.setName(path);
        file.setPath(path);
        file.setSize(1L);
        return file;
    }
}
