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

package com.epam.pipeline.entity.datastorage.azure;

import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AzureBlobStorage extends AbstractDataStorage {

    private Long regionId;

    public AzureBlobStorage(final Long id, final String name, final String path, final StoragePolicy policy,
                            final String mountPoint) {
        super(id, name, ProviderUtils.normalizeBucketName(path), DataStorageType.AZ, policy, mountPoint);
    }

    @Override
    public String getDelimiter() {
        return ProviderUtils.DELIMITER;
    }

    @Override
    public String getPathMask() {
        return String.format("az://%s", getPath());
    }

    @Override
    public boolean isPolicySupported() {
        return false;
    }
}
