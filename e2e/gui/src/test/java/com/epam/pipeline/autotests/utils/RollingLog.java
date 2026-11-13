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
package com.epam.pipeline.autotests.utils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static org.testng.Assert.assertTrue;

/**
 * The log of a platform service, read from its log directory: the current file, e.g. notifier.log, and the files it
 * is rolled over to every day, e.g. notifier-yyyyMMdd.log. The names sort in the order the files were written:
 * the dated files by date, then the current one.
 */
public class RollingLog {

    private static final int READ_ATTEMPTS = 3;

    private final Path dir;
    private final String service;
    private final String currentFile;
    private final Pattern logFile;

    /**
     * @param dir the log directory
     * @param service the service, for the messages, e.g. "notifier"
     * @param name the name of the current file without ".log", e.g. "notifier"
     */
    public RollingLog(final Path dir, final String service, final String name) {
        this.dir = dir;
        this.service = service;
        this.currentFile = name + ".log";
        this.logFile = Pattern.compile(Pattern.quote(name) + "(-\\d{8})?\\.log");
    }

    /**
     * Fails unless the current file is readable and was written within the last {@code maxAge}. An empty or old
     * directory is not the log of the running service.
     */
    public void assertCurrent(final Duration maxAge) {
        final Path current = dir.resolve(currentFile);
        assertTrue(Files.isReadable(current), format("%s is not readable: the %s log directory is not mounted, "
                + "or not readable by the tests user", current, service));
        final long age = System.currentTimeMillis() - lastModified(current);
        assertTrue(age <= maxAge.toMillis(), format("%s was last written %d s ago: it is not the log of the running "
                + "%s", current, age / 1000, service));
    }

    /**
     * The lines of the log files written since the time (ms since the epoch). The files are listed again after they
     * are read: if the service rolled the current file over meanwhile, the lines it moved are in a file that was not
     * listed, so the files are read again.
     */
    public List<String> lines(final long since) {
        for (int attempt = 1; ; attempt++) {
            final Set<Path> files = files(since);
            final List<String> lines = new ArrayList<>();
            try {
                for (final Path file : files) {
                    lines.addAll(Files.readAllLines(file, StandardCharsets.UTF_8));
                }
            } catch (NoSuchFileException e) {
                // Renamed by a rollover after it was listed
                if (attempt < READ_ATTEMPTS) {
                    continue;
                }
                throw new UncheckedIOException(format("Unable to read %s", e.getFile()), e);
            } catch (IOException e) {
                throw new UncheckedIOException(format("Unable to read the %s log", service), e);
            }
            if (attempt == READ_ATTEMPTS || files(since).equals(files)) {
                return lines;
            }
        }
    }

    /**
     * The log files written since the time, by name, which is the order they were written in.
     */
    private Set<Path> files(final long since) {
        final Set<Path> files = new TreeSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (final Path file : stream) {
                if (logFile.matcher(file.getFileName().toString()).matches() && lastModified(file) >= since) {
                    files.add(file);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(format("Unable to list %s", dir), e);
        }
        return files;
    }

    private static long lastModified(final Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            throw new UncheckedIOException(format("Unable to read the time of %s", file), e);
        }
    }
}
