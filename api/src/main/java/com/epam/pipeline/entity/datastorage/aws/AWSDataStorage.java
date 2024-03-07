/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.datastorage.aws;

import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class AWSDataStorage extends AbstractDataStorage {
    /**
     * Id of AWS region in which bucket is created.
     * If null bucket is assumed to be created in default regions (for backward compatibility only).
     */
    private Long regionId;

    public AWSDataStorage() {
    }

    public AWSDataStorage(final Long id, String name, String path,
                          DataStorageType dataStorageType, StoragePolicy policy, String mountPoint) {
        super(id, name, path, dataStorageType, policy, mountPoint);
    }
}
