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

package com.epam.pipeline.assertions.datastorage;

import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public abstract class AbstractS3StorageAssert<S extends AbstractS3StorageAssert<S, A>, A extends S3bucketDataStorage>
        extends AbstractDataStorageAssert<S, A> {

    public AbstractS3StorageAssert(A actual, Class<S> selfType) {
        super(actual, selfType);
    }

    public S hasRegionId(Long regionId) {
        isNotNull();
        if (!Objects.equals(actual.getRegionId(), regionId)) {
            failWithMessage("Expected S3 storage AWS region id to be <%s> but was <%s>.",
                    regionId, actual.getRegionId());
        }
        return myself;
    }

    public S hasAllowedCidrs(List<String> allowedCidrs) {
        isNotNull();
        if (!equalCollections(actual.getAllowedCidrs(), allowedCidrs)) {
            failWithMessage("Expected S3 storage AWS allowed CIDRs to be <%s> but was <%s>.",
                    allowedCidrs, actual.getAllowedCidrs());
        }
        return myself;
    }

    public static boolean equalCollections(Collection<?> first, Collection<?> second) {
        if (first == second) {
            return true;
        }
        if (first == null) {
            return second.isEmpty();
        }
        if (second == null) {
            return first.isEmpty();
        }
        if (first.size() != second.size()) {
            return false;
        }
        return CollectionUtils.disjunction(first, second).isEmpty();
    }
}
