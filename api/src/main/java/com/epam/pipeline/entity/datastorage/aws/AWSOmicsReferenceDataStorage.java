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
import com.fasterxml.jackson.annotation.JsonIgnore;
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
public class AWSOmicsReferenceDataStorage extends AbstractAWSOmicsDataStorage {

    public static final Pattern AWS_OMICS_REFERENCE_STORE_PATH_FORMAT =
            Pattern.compile(
                    "(?<account>[^:]*).storage.(?<region>[^:]*).amazonaws.com/(?<referenceStoreId>.*)/reference"
            );

    public static final Pattern REFERENCE_STORE_ARN_FORMAT =
            Pattern.compile(
                    "arn:aws:omics:(?<region>[^:]*):(?<account>[^:]*):referenceStore/(?<referenceStoreId>.*)"
            );
    public static final String REFERENCE_STORE_ID_GROUP = "referenceStoreId";

    public AWSOmicsReferenceDataStorage(final Long id, final String name, final String path) {
        super(id, name, path, DataStorageType.AWS_OMICS_REF);
    }

    public AWSOmicsReferenceDataStorage(final DataStorageVO vo) {
        super(vo);
    }

    @JsonIgnore
    public String getCloudStorageId() {
        final Matcher matcher = AWS_OMICS_REFERENCE_STORE_PATH_FORMAT.matcher(getPath());
        if (matcher.find()) {
            return matcher.group(REFERENCE_STORE_ID_GROUP);
        } else {
            throw new IllegalArgumentException();
        }
    }
}
