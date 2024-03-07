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

import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An entity, that represents a Data Storage, backed by Amazon S3 bucket
 */
@Getter
@Setter
@NoArgsConstructor
public class AWSOmicsReferenceDataStorage extends AWSDataStorage {

    public static final String AWS_OMICS_REFERENCE_STORE_PATH_TEMPLATE = "omics://%s.storage.%s.amazonaws.com/%s";
    public static final Pattern AWS_OMICS_REFERENCE_STORE_PATH_FORMAT =
            Pattern.compile("omics://(?<account>[^:]*).storage.(?<region>[^:]*).amazonaws.com/(?<referenceStoreId>.*)");

    public static final Pattern REFERENCE_STORE_ARN_FORMAT =
            Pattern.compile("arn:aws:omics:(?<region>[^:]*):(?<account>[^:]*):referenceStore/(?<referenceStoreId>.*)");

    private String tempCredentialsRole;
    private boolean useAssumedCredentials;

    public AWSOmicsReferenceDataStorage(final Long id, final String name, final String path) {
        this(id, name, ProviderUtils.normalizeBucketName(path), DEFAULT_POLICY, "");
    }

    public AWSOmicsReferenceDataStorage(final Long id, final String name, final String path,
                                        final StoragePolicy policy, String mountPoint) {
        super(id, name, ProviderUtils.normalizeBucketName(path), DataStorageType.AWS_OMICS_REF, policy, mountPoint);
    }

    public AWSOmicsReferenceDataStorage(final DataStorageVO vo) {
        super(vo.getId(), vo.getName(), ProviderUtils.normalizeBucketName(vo.getPath()),
                DataStorageType.AWS_OMICS_REF, vo.getStoragePolicy(), vo.getMountPoint());
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
        return  "omics://" + getPath();
    }

    @Override
    public boolean isPolicySupported() {
        return false;
    }

    public String getCloudStorageId() {
        final Matcher matcher = AWS_OMICS_REFERENCE_STORE_PATH_FORMAT.matcher(getPath());
        if (matcher.find()) {
            return matcher.group();
        } else {
            throw new IllegalArgumentException();
        }
    }
}
