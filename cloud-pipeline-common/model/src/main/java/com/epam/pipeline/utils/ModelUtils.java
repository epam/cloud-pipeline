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

package com.epam.pipeline.utils;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

public interface ModelUtils {

    static LocalDateTime now() {
        return LocalDateTime.now(Clock.systemUTC());
    }

    static Date toDate(LocalDateTime time) {
        return Date.from(time.toInstant(ZoneOffset.UTC));
    }

    static void checkLocalPathReadability(final String path) {
        Assert.isTrue(!StringUtils.isEmpty(path), "Path must be specified.");
        Assert.isTrue(Paths.get(path).toFile().exists(), String.format("Specified path %s does not exists.", path));
        Assert.isTrue(Paths.get(path).toFile().canRead(), String.format("Cannot read path %s.", path));
    }

    static void createFileIfNotExists(final Path path) {
        if (!path.toFile().exists()) {
            try {
                Files.createFile(path);
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot create file", e);
            }
        }
    }

    static void createDirectoryIfNotExits(Path path) {
        File folder = path.toFile();
        if (folder.exists() && folder.isFile()) {
            throw new IllegalArgumentException(
                    String.format("Passed existing file %s as folder", path.toAbsolutePath().toString()));
        }
        if(!folder.exists()) {
            createDirectory(path);
        }
    }

    static void createDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot create directory", e);
        }
    }

    static boolean isDirectoryExists(final File directory) {
        return directory.isDirectory() && directory.exists();
    }

    static String generateRandomString(int length) {
        return RandomStringUtils.randomAlphanumeric(length);
    }

    static String removeNonAlphanumericCharacters(final String string) {
        return string.replaceAll("[^A-Za-z0-9]", "");
    }
}
