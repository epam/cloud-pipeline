/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.epam.pipeline.entity.datastorage;

import com.epam.pipeline.entity.utils.ProviderUtils;
import lombok.Getter;
import lombok.Setter;

/**
 * An entity, that represents a Data Storage, backed by Amazon S3 bucket
 */
@Getter
@Setter
public class AWSOmicsSeqStorage extends AbstractDataStorage {

    public AWSOmicsSeqStorage() {
        setType(DataStorageType.AWS_OMICS_SEQ);
    }

    /**
     * Id of AWS region in which omics store is created.
     */
    private Long regionId;

    public AWSOmicsSeqStorage(final Long id, final String name, final String path) {
        this(id, name, ProviderUtils.normalizeBucketName(path), DEFAULT_POLICY, "");
    }

    public AWSOmicsSeqStorage(final Long id, final String name, final String path,
                              final StoragePolicy policy, String mountPoint) {
        super(id, name, ProviderUtils.normalizeBucketName(path), DataStorageType.AWS_OMICS_SEQ, policy, mountPoint);
    }

    @Override
    public String getMountOptions() {
        return "";
    }

    @Override
    public String getDelimiter() {
        return ProviderUtils.DELIMITER;
    }

    @Override
    public String getPathMask() {
        return  String.format("omics://%s", getPath());
    }

    @Override
    public boolean isPolicySupported() {
        return false;
    }

}
