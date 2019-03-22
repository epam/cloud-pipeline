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

package com.epam.pipeline.mapper;

import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public abstract class AbstractDataStorageMapper {

    @Mapping(target = "allowedCidrs", ignore = true)
    @Mapping(target = "regionId", ignore = true)
    public abstract DataStorageVO toDataStorageVO(AbstractDataStorage dataStorage);

    @AfterMapping
    public void fillS3Fields(AbstractDataStorage dataStorage, @MappingTarget DataStorageVO dataStorageVO) {
        if (dataStorage instanceof S3bucketDataStorage) {
            S3bucketDataStorage s3bucketDataStorage = (S3bucketDataStorage) dataStorage;
            dataStorageVO.setAllowedCidrs(s3bucketDataStorage.getAllowedCidrs());
            dataStorageVO.setRegionId(s3bucketDataStorage.getRegionId());
        }

    }
}
