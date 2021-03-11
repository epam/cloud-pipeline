/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.elasticsearchagent.service;

import com.epam.pipeline.elasticsearchagent.model.PermissionsContainer;
import com.epam.pipeline.elasticsearchagent.service.impl.IndexRequestContainer;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;

import java.util.stream.Stream;

/**
 * Lists all files in specified {@code AbstractDataStorage} and ass them to ES index
 */
public interface ObjectStorageFileManager {
    
    DataStorageType getType();

    void listAndIndexFiles(String indexName, AbstractDataStorage dataStorage,
                           TemporaryCredentials credentials,
                           PermissionsContainer permissionsContainer,
                           IndexRequestContainer requestContainer);

    Stream<DataStorageFile> listVersionsWithTags(AbstractDataStorage dataStorage,
                                                 TemporaryCredentials credentials);
}
