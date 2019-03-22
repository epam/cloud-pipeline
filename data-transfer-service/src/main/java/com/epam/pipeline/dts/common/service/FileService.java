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

package com.epam.pipeline.dts.common.service;

import java.io.IOException;
import java.nio.file.Path;

public interface FileService {

    Path getLocalFile(String path);
    Path getOrCreateFolder(String path);
    Path createFolder(Path path);
    void writeToFile(Path file, String scriptContent) throws IOException;
    String readFileContent(Path file) throws IOException;
}
