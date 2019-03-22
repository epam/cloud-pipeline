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

package com.epam.pipeline.cmd;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

/**
 * File system compressor that produces .tar.gz files.
 *
 * It works with files and folders.
 */
@Slf4j
@RequiredArgsConstructor
public class TarGzCompressor {

    private static final String TAR_GZ = ".tar.gz";
    private static final String COMPRESS_DIRECTORY_COMMAND = "tar -czf ../%s .";
    private static final String COMPRESS_FILE_COMMAND = "tar -czf %s %s";

    private final CmdExecutor cmdExecutor;

    /**
     * Compresses the resource by the given path.
     *
     * If the archive already exists then it just skips the compressing.
     *
     * @param path        Path of the resource for compression.
     * @param archiveName Base name of the archive file with .tar.gz extension.
     * @return Produced archive path.
     */
    public Path compress(final Path path, final String archiveName) {
        Assert.isTrue(Files.exists(path), String.format("Resource with path %s does not exist", path));
        final String resourceName = path.getFileName().toString();
        final Path archivePath = path.getParent().resolve(archiveName);
        if (Files.exists(archivePath)) {
            log.info(String.format("Archive of the resource %s already exists by path %s", path, archivePath));
        } else {
            if (Files.isDirectory(path)) {
                final String compressDirectory = String.format(COMPRESS_DIRECTORY_COMMAND, archiveName);
                cmdExecutor.executeCommand(compressDirectory, Collections.emptyMap(), path.toFile());
            } else {
                final String compressFile = String.format(COMPRESS_FILE_COMMAND, archiveName, resourceName);
                cmdExecutor.executeCommand(compressFile, Collections.emptyMap(), path.getParent().toFile());
            }
        }
        return archivePath;
    }

    /**
     * Compresses the resource by the given path.
     *
     * If the archive already exists then it just skips the compressing.
     *
     * @param path Path of the resource for compression.
     * @return Produced archive path.
     */
    public Path compress(final Path path) {
        return compress(path, path.getFileName().toString() + TAR_GZ);
    }
}
