/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.monitor.monitoring.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutionTimestampFileTest {

    private Path tempDir;
    private Path timestampFile;
    private ExecutionTimestampFile executionTimestampFile;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("execution-timestamp-test");
        timestampFile = tempDir.resolve("last_exec.txt");
        executionTimestampFile = new ExecutionTimestampFile(timestampFile.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(timestampFile);
        Files.deleteIfExists(tempDir);
    }

    @Test
    void constructorSucceedsWhenFileDoesNotExist() {
        // setUp already covers this; explicit assertion for clarity
        assertNull(executionTimestampFile.read());
    }

    @Test
    void constructorSucceedsWhenFileIsEmpty() throws IOException {
        Files.createFile(timestampFile);

        new ExecutionTimestampFile(timestampFile.toString());
    }

    @Test
    void constructorSucceedsWhenFileContainsValidTimestamps() throws IOException {
        Files.write(timestampFile, ("2026-01-01T08:00:00.000\n"
                + "2026-01-02T09:00:00.000\n").getBytes());

        new ExecutionTimestampFile(timestampFile.toString());
    }

    @Test
    void constructorThrowsWhenFileContainsInvalidContent() throws IOException {
        Files.write(timestampFile, "not-a-timestamp\n".getBytes());

        assertThrows(IllegalArgumentException.class,
                () -> new ExecutionTimestampFile(timestampFile.toString()));
    }

    @Test
    void readReturnsNullWhenFileDoesNotExist() {
        assertNull(executionTimestampFile.read());
    }

    @Test
    void readReturnsNullWhenFileIsEmpty() throws IOException {
        Files.createFile(timestampFile);

        assertNull(executionTimestampFile.read());
    }

    @Test
    void readReturnsNullOnInvalidFormatWrittenAfterConstruction() throws IOException {
        // File is written with bad content after the instance is already created,
        // so validation does not run — read() itself gracefully returns null.
        Files.write(timestampFile, "not-a-timestamp\n".getBytes());

        assertNull(executionTimestampFile.read());
    }

    @Test
    void readParsesTimestampFromFile() throws IOException {
        Files.write(timestampFile, "2026-01-15T10:30:00.000\n".getBytes());

        final LocalDateTime result = executionTimestampFile.read();

        assertEquals(LocalDateTime.of(2026, 1, 15, 10, 30, 0), result);
    }

    @Test
    void readReturnsLastLineWhenFileHasMultipleLines() throws IOException {
        Files.write(timestampFile, ("2026-01-01T08:00:00.000\n"
                + "2026-01-02T09:00:00.000\n"
                + "2026-01-03T10:00:00.000\n").getBytes());

        final LocalDateTime result = executionTimestampFile.read();

        assertEquals(LocalDateTime.of(2026, 1, 3, 10, 0, 0), result);
    }

    @Test
    void writeCreatesFileAndPersistsTimestamp() {
        final LocalDateTime time = LocalDateTime.of(2026, 3, 20, 12, 0, 0);

        executionTimestampFile.write(time);

        assertEquals(time, executionTimestampFile.read());
    }

    @Test
    void writeAppendsSubsequentTimestamps() {
        final LocalDateTime first = LocalDateTime.of(2026, 3, 20, 8, 0, 0);
        final LocalDateTime second = LocalDateTime.of(2026, 3, 20, 9, 0, 0);

        executionTimestampFile.write(first);
        executionTimestampFile.write(second);

        assertEquals(second, executionTimestampFile.read());
    }

    @Test
    void writeAndReadRoundtripPreservesMilliseconds() {
        final LocalDateTime time = LocalDateTime.of(2026, 6, 15, 23, 59, 59, 123_000_000);

        executionTimestampFile.write(time);
        final LocalDateTime result = executionTimestampFile.read();

        assertNotNull(result);
        assertEquals(time.withNano(0).plusNanos(123_000_000L), result);
    }
}
