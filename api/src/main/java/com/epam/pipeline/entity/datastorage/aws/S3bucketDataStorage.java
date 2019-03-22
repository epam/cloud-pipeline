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

import java.util.List;

import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3Constants;
import lombok.Getter;
import lombok.Setter;

/**
 * An entity, that represents a Data Storage, backed by Amazon S3 bucket
 */
@Getter
@Setter
public class S3bucketDataStorage extends AbstractDataStorage {

    /**
     * Id of AWS region in which bucket is created.
     * If null bucket is assumed to be created in default regions (for backward compatibility only).
     */
    private Long regionId;
    /**
     * A list of allowed CIDR strings, that define access control
     */
    private List<String> allowedCidrs;

    private static final String DELIMITER = S3Constants.DELIMITER;

    public S3bucketDataStorage(final Long id, final String name, final String path) {
        this(id, name, normalizeBucketName(path), DEFAULT_POLICY, "");
    }

    public S3bucketDataStorage(final Long id, final String name, final String path,
            final StoragePolicy policy, String mountPoint) {
        super(id, name, normalizeBucketName(path), DataStorageType.S3, policy, mountPoint);
    }

    public static String normalizeBucketName(String name) {
        String bucketName = name.trim().toLowerCase();
        bucketName = bucketName.replaceAll("[^a-z0-9\\-]+", "-");
        return bucketName;
    }

    @Override
    public String getMountOptions() {
        return "";
    }

    @Override
    public String getDelimiter() {
        return DELIMITER;
    }

    @Override
    public String getPathMask() {
        return  String.format("s3://%s", getPath());
    }

    @Override
    public boolean isPolicySupported() {
        return true;
    }

}
