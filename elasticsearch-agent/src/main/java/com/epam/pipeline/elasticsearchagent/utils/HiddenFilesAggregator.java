/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.elasticsearchagent.utils;

import com.epam.pipeline.entity.datastorage.DataStorageFile;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@RequiredArgsConstructor
public class HiddenFilesAggregator {
    private final String hiddenFileName;
    private long totalSize = 0L;
    private boolean hasHiddenFiles = false;
    private DataStorageFile lastFile;

    public void add(DataStorageFile file) {
        this.lastFile = file;
        this.hasHiddenFiles = true;
        this.totalSize += Optional.ofNullable(file.getSize()).orElse(0L);
    }

    public DataStorageFile getHiddenAggregatedFile() {
        if (!hasHiddenFiles) {
            return null;
        }
        this.lastFile.setSize(totalSize);
        this.lastFile.setPath(hiddenFileName);
        this.lastFile.setName(hiddenFileName);
        return this.lastFile;
    }
}
