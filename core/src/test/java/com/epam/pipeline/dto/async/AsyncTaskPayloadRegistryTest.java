/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.dto.async;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AsyncTaskParams} and {@link AsyncTaskResult} carry a hand-written list of subtypes. A new task
 * type that forgets to register its payload would compile and only fail when a task of that type is
 * read back from the database, so the list is checked here instead.
 */
public class AsyncTaskPayloadRegistryTest {

    private static final String DISCRIMINATOR_ONLY = "{\"type\":\"%s\"}";
    private static final long STORAGE_ID = 42L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @EnumSource(AsyncTaskType.class)
    public void shouldResolveParamsForEveryTaskType(final AsyncTaskType type) throws IOException {
        final AsyncTaskParams params = objectMapper.readValue(
                String.format(DISCRIMINATOR_ONLY, type.name()), AsyncTaskParams.class);

        assertEquals(type, params.getType());
    }

    @ParameterizedTest
    @EnumSource(AsyncTaskType.class)
    public void shouldResolveResultForEveryTaskType(final AsyncTaskType type) throws IOException {
        final AsyncTaskResult result = objectMapper.readValue(
                String.format(DISCRIMINATOR_ONLY, type.name()), AsyncTaskResult.class);

        assertEquals(type, result.getType());
    }

    @ParameterizedTest
    @EnumSource(AsyncTaskType.class)
    public void shouldWriteTypeAsDiscriminatorOnlyOnce(final AsyncTaskType type) throws IOException {
        final AsyncTaskParams params = objectMapper.readValue(
                String.format(DISCRIMINATOR_ONLY, type.name()), AsyncTaskParams.class);

        final String json = objectMapper.writeValueAsString(params);

        assertEquals(1, json.split("\"type\"", -1).length - 1, json);
    }

    @Test
    public void shouldRoundTripDownloadArchivePayloadThroughTheBaseType() throws IOException {
        final DownloadArchiveParams params = DownloadArchiveParams.builder()
                .storageId(STORAGE_ID)
                .paths(java.util.List.of("data/", "notes.txt"))
                .archiveName("selection.zip")
                .build();

        final String json = objectMapper.writeValueAsString((AsyncTaskParams) params);
        final AsyncTaskParams restored = objectMapper.readValue(json, AsyncTaskParams.class);

        assertTrue(restored instanceof DownloadArchiveParams);
        final DownloadArchiveParams downloadArchive = (DownloadArchiveParams) restored;
        assertEquals(params.getStorageId(), downloadArchive.getStorageId());
        assertEquals(params.getPaths(), downloadArchive.getPaths());
        assertEquals(params.getArchiveName(), downloadArchive.getArchiveName());
    }
}
