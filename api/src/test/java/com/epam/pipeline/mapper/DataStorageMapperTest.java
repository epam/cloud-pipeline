/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DataStorageMapperTest {

    private static final String ACCESS_POINT_ARN = "arn:aws:s3:us-east-1:123456789012:accesspoint/test-ap";
    private static final String ROLE = "arn:aws:iam::123456789012:role/test-role";

    private final AbstractDataStorageMapper mapper = Mappers.getMapper(AbstractDataStorageMapper.class);

    @Test
    public void shouldNotCopyS3AccessPointArn() {
        final S3bucketDataStorage storage = new S3bucketDataStorage(1L, "storage", "storage-ext-s3alias");
        storage.setAccessPointArn(ACCESS_POINT_ARN);
        storage.setTempCredentialsRole(ROLE);

        final DataStorageVO vo = mapper.toDataStorageVO(storage);

        assertNull(vo.getAccessPointArn());
        assertEquals(ROLE, vo.getTempCredentialsRole());
    }
}
