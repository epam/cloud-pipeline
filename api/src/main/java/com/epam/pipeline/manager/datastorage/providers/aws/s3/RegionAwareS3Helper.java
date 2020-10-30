/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.cloud.aws.AWSUtils;

/**
 * Provides methods for AWS S3 operations in specified region.
 */
public class RegionAwareS3Helper extends S3Helper {

    private final AwsRegion region;

    public RegionAwareS3Helper(final AwsRegion region, final MessageHelper messageHelper) {
        super(messageHelper);
        this.region = region;
    }

    @Override
    public AmazonS3 getDefaultS3Client() {
        return AmazonS3ClientBuilder.standard()
                .withRegion(region.getRegionCode())
                .withCredentials(AWSUtils.getCredentialsProvider(region.getProfile()))
                .build();
    }
}
