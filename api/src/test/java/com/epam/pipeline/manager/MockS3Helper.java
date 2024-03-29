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

package com.epam.pipeline.manager;

import com.amazonaws.services.s3.model.CORSRule;
import com.epam.pipeline.entity.datastorage.ActionStatus;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3Helper;

import java.util.List;
import java.util.Map;

public class MockS3Helper extends S3Helper {

    public MockS3Helper() {
        super(null, null);
    }

    @Override public String createS3Bucket(final String name) {
        return name;
    }

    @Override public ActionStatus postCreationProcessing(final String name,
                                                         final String policy,
                                                         final List<String> allowedCidrs,
                                                         final List<CORSRule> corsRules,
                                                         final AwsRegion region,
                                                         final boolean shared,
                                                         final String kmsKey,
                                                         final Map<String, String> tags) {
        return ActionStatus.success();
    }

    @Override public void deleteS3Bucket(S3bucketDataStorage storage) {
        // no op
    }

    @Override public void applyStoragePolicy(String bucketName, StoragePolicy storagePolicy) {
        // no op
    }

    @Override public boolean checkBucket(String bucket) {
        return false;
    }
}
