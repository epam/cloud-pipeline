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

package com.epam.pipeline.entity.datastorage.aws;

import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * An entity, that represents a Data Storage, backed by Amazon S3 bucket
 */
@Getter
@Setter
@NoArgsConstructor
public class S3bucketDataStorage extends AWSDataStorage {

    /**
     * A list of allowed CIDR strings, that define access control
     */
    private List<String> allowedCidrs;

    public S3bucketDataStorage(final Long id, final String name, final String path) {
        this(id, name, ProviderUtils.normalizeBucketName(path), DEFAULT_POLICY, "");
    }

    public S3bucketDataStorage(final Long id, final String name, final String path,
            final StoragePolicy policy, String mountPoint) {
        super(id, name, ProviderUtils.normalizeBucketName(path), DataStorageType.S3, policy, mountPoint);
    }

    public S3bucketDataStorage(final DataStorageVO vo) {
        super(vo.getId(), vo.getName(), ProviderUtils.normalizeBucketName(vo.getPath()),
                DataStorageType.S3, vo.getStoragePolicy(), vo.getMountPoint());
    }

    @Override
    public String getPathMask() {
        return  "s3://" + getPath();
    }

    @Override
    public boolean isPolicySupported() {
        return true;
    }

}
