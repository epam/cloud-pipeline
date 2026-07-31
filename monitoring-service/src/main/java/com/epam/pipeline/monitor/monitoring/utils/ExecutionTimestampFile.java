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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Represents a plain text file that stores execution timestamps, one per line, in the format
 * {@code yyyy-MM-dd'T'HH:mm:ss.SSS}. Each {@link #write} call appends a new line; {@link #read}
 * returns the timestamp from the last line, making the file a compact audit log of past runs while
 * keeping retrieval O(1) regardless of how many entries have accumulated.
 *
 * <p>The file is created on the first {@link #write} call if it does not yet exist.
 *
 * <p>At construction time the file is validated: if it already exists its last line must be a
 * parseable timestamp, otherwise an {@link IllegalArgumentException} is thrown to prevent
 * accidentally pointing this instance at an unrelated file.
 */
@Slf4j
public class ExecutionTimestampFile {

    static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private static final int FILE_READER_BLOCK_SIZE = 4096;

    private final String filePath;

    /**
     * Creates an instance backed by the file at {@code filePath}.
     *
     * @param filePath path to the timestamp file; the file need not exist yet
     * @throws IllegalArgumentException if the file exists and its last line cannot be parsed as a
     *                                  timestamp, indicating it is not a valid timestamp file
     */
    public ExecutionTimestampFile(final String filePath) {
        this.filePath = filePath;
        validate();
    }

    /**
     * Reads the timestamp from the last line of the file.
     *
     * @return the parsed {@link LocalDateTime}, or {@code null} if the file does not exist, is
     *         empty, or its last line cannot be parsed
     */
    public LocalDateTime read() {
        try (ReversedLinesFileReader reader = new ReversedLinesFileReader(
                new File(filePath), FILE_READER_BLOCK_SIZE, StandardCharsets.UTF_8)) {
            final String lastLine = reader.readLine();
            if (StringUtils.isBlank(lastLine)) {
                return null;
            }
            return LocalDateTime.parse(lastLine.trim(), DATE_FORMATTER);
        } catch (IOException e) {
            log.trace("Error reading last execution time file {}", filePath, e);
            return null;
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse last execution time from {}", filePath, e);
            return null;
        }
    }

    /**
     * Appends {@code time} as a new line to the file, creating the file if it does not exist.
     * Errors are logged but not rethrown so that a write failure does not interrupt the caller.
     *
     * @param time the timestamp to persist
     */
    public void write(final LocalDateTime time) {
        try {
            Files.write(Paths.get(filePath),
                    (time.format(DATE_FORMATTER) + System.lineSeparator()).getBytes(),
                    StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        } catch (IOException e) {
            log.error("Failed to write last execution time to {}", filePath, e);
        }
    }

    private void validate() {
        final File file = new File(filePath);
        if (!file.exists()) {
            return;
        }
        try (ReversedLinesFileReader reader = new ReversedLinesFileReader(
                file, FILE_READER_BLOCK_SIZE, StandardCharsets.UTF_8)) {
            final String lastLine = reader.readLine();
            if (StringUtils.isBlank(lastLine)) {
                return;
            }
            LocalDateTime.parse(lastLine.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "File " + filePath + " exists but does not appear to be a timestamp file", e);
        } catch (IOException e) {
            log.warn("Could not validate timestamp file {}", filePath, e);
        }
    }
}
