/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public interface PipelineCLI {

    default String uploadData(Path source, String destination) {
        return uploadData(source.toString(), destination);
    }

    default String uploadData(String source, String destination) {
        return uploadData(source, destination, Collections.emptyList());
    }

    default String uploadData(String source, String destination, List<String> include) {
        return uploadData(source, destination, include, null);
    }

    /**
     * Upload local file {@code source} to s3 bucket by path {@code destination}.
     *
     * @param source Path to a file to be uploaded.
     * @param destination Destination S3 file path.
     * @return Url of the file in bucket.
     */
    String uploadData(String source, String destination, List<String> include, String username);

    default void downloadData(String source, Path destination) {
        downloadData(source, destination.toString());
    }

    default void downloadData(String source, String destination) {
        downloadData(source, destination, Collections.emptyList());
    }

    default void downloadData(String source, String destination, List<String> include) {
        downloadData(source, destination, include, null);
    }

    /**
     * Download file from {@code destination} to local file {@code source}.
     *
     * @param source S3 file path.
     * @param destination Path to a file to be downloaded.
     */
    void downloadData(String source, String destination, List<String> include, String username);

    /**
     * Retrieves a remote file description if one exists.
     *
     * @param targetPath Remote file path.
     * @return Remote file description if one was found.
     */
    Optional<RemoteFileDescription> retrieveDescription(String targetPath);
}
