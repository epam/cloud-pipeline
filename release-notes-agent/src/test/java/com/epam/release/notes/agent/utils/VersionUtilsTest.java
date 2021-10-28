/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.release.notes.agent.utils;

import com.epam.release.notes.agent.entity.version.Version;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VersionUtilsTest {

    private static final String VERSION_FILE_NAME = "version.txt";
    private static final String SAVED_VERSION = "0.17.0.10920.c426ffba4ba95a5f4dc46089bf9f9a89aec2df00";
    private Path tempFileWithVersion;

    @SneakyThrows
    @BeforeEach
    void setUp() {
        tempFileWithVersion = Files.createTempFile(VERSION_FILE_NAME, "").toAbsolutePath();
        Files.write(tempFileWithVersion, Collections.singleton(SAVED_VERSION));
    }

    @AfterEach
    void tearDown() {
        tempFileWithVersion.toFile().delete();
    }

    @Test
    void updateVersionInFile() {
        final Version newVersion = Version.builder()
                .major("0.17.0")
                .buildNumber("11111")
                .sha("ef3e3rr4t4t4t")
                .build();
        VersionUtils.updateVersionInFile(newVersion, tempFileWithVersion.toString());
        final String updatedVersion = readVersionFromFile(tempFileWithVersion.toString());
        assertEquals(newVersion.toString(), updatedVersion);
    }

    private String readVersionFromFile(final String versionFilePath) {
        try {
            return Files.lines(Paths.get(versionFilePath))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Provided versioned file is empty. Check file and set correct version."));
        } catch (IOException e) {
            throw new RuntimeException(format("Unable to get file from path %s", versionFilePath));
        }
    }
}
