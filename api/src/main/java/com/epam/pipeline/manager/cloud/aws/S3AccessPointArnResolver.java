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

package com.epam.pipeline.manager.cloud.aws;

import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Finds the S3 access point ARN to grant in the temporary credentials of a storage.
 * A linked storage has no ARN of its own as a rule, so it uses the ARN of its source storage,
 * as long as both storages share the same root: the ARN belongs to that access point alias only.
 * A link restricted by linking masks does not inherit it: temporary credentials do not apply the masks,
 * so the inherited ARN would expose the whole link path.
 */
@Component
@RequiredArgsConstructor
public class S3AccessPointArnResolver {

    private final DataStorageDao dataStorageDao;

    public String resolve(final S3bucketDataStorage storage) {
        if (StringUtils.isNotBlank(storage.getAccessPointArn()) || storage.getSourceStorageId() == null
                || CollectionUtils.isNotEmpty(storage.getLinkingMasks())) {
            return storage.getAccessPointArn();
        }
        final AbstractDataStorage source = dataStorageDao.loadDataStorage(storage.getSourceStorageId());
        if (source instanceof S3bucketDataStorage && Objects.equals(source.getRoot(), storage.getRoot())) {
            return ((S3bucketDataStorage) source).getAccessPointArn();
        }
        return null;
    }
}
