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

package com.epam.pipeline.dts;

import lombok.SneakyThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class AbstractTest {

    @SneakyThrows
    public static Path createTempFile(final String fileName) {
        return Files.createTempFile(fileName, "");
    }

    @SneakyThrows
    public static Path createTempFolder(final String folderName) {
        return Files.createTempDirectory(folderName);
    }

    public static void deleteFile(final String path) {
        deleteFile(Paths.get(path));
    }

    public static void deleteFile(final Path path) {
        path.toFile().delete();
    }
}
