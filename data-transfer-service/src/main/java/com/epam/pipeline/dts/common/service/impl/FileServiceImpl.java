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

package com.epam.pipeline.dts.common.service.impl;

import com.epam.pipeline.dts.common.service.FileService;
import com.epam.pipeline.dts.util.Utils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileServiceImpl implements FileService {

    @Override
    public Path getLocalFile(final String path) {
        Utils.checkLocalPathReadability(path);
        return Paths.get(path);
    }

    @Override
    public Path getOrCreateFolder(final String path) {
        Assert.isTrue(StringUtils.isNotBlank(path), "Path must be specified.");
        final Path folderPath = Paths.get(path);
        final File folder = folderPath.toFile();
        if (folder.exists()) {
            Assert.isTrue(folder.canWrite(), String.format("Cannot write to path %s.", path));
            Assert.isTrue(folder.isDirectory(), String.format("Provided path %s is not a folder.", path));
            return folderPath;
        } else  {
            return createFolder(folderPath);
        }
    }

    @Override
    public Path createFolder(final Path path) {
        try {
            return Files.createDirectories(path.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public void writeToFile(final Path file,
                            final String content) throws IOException {
        try(FileWriter writer = new FileWriter(file.toFile())) {
            IOUtils.write(content, writer);
        }
    }

    @Override
    public String readFileContent(Path file) throws IOException {
        try (FileReader reader = new FileReader(file.toFile())) {
            return IOUtils.toString(reader);
        }
    }
}
