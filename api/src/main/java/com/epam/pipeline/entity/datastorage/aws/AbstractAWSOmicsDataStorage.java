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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An entity, that represents a Data Storage, backed by Amazon S3 bucket
 */
@Getter
@Setter
@NoArgsConstructor
public abstract class AbstractAWSOmicsDataStorage extends AbstractAWSDataStorage {

    public AbstractAWSOmicsDataStorage(final Long id, final String name, final String path, DataStorageType type) {
        super(id, name, path, type, null, "");
    }

    public AbstractAWSOmicsDataStorage(final DataStorageVO vo) {
        this(vo.getId(), vo.getName(), null, vo.getType());
    }

    @Override
    public String getPathMask() {
        return  "omics://" + getPath();
    }

    @Override
    public boolean isPolicySupported() {
        return false;
    }

    public abstract String getCloudStorageId();
}
