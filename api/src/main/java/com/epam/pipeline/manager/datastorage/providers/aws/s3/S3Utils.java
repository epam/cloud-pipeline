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

package com.epam.pipeline.manager.datastorage.providers.aws.s3;

import com.amazonaws.services.s3.model.Region;

import java.util.Arrays;
import java.util.Optional;

public final class S3Utils {

    private S3Utils() {
        //no op
    }

    public static Optional<String> getRegionFromBucketName(String bucket) {
        return Arrays.stream(Region.values())
                .map(Region::getFirstRegionId)
                .filter(region ->
                        bucket.startsWith(region + "-"))
                .findFirst();
    }
}
