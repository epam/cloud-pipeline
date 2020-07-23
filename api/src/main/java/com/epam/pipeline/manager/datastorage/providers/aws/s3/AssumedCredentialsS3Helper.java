/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.datastorage.providers.aws.s3;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.region.AwsRegion;

public class AssumedCredentialsS3Helper extends S3Helper {

    private static final String ROLE_SESSION_NAME = "CLOUD_PIPELINE_SESSION";
    private static final int MIN_SESSION_DURATION = 900;

    private final String roleArn;
    private final AwsRegion region;

    public AssumedCredentialsS3Helper(final String roleArn,
                                      final AwsRegion region,
                                      final MessageHelper messageHelper) {
        super(messageHelper);
        this.roleArn = roleArn;
        this.region = region;
    }

    @Override
    public AmazonS3 getDefaultS3Client() {
        STSAssumeRoleSessionCredentialsProvider credentialsProvider =
                new STSAssumeRoleSessionCredentialsProvider.Builder(roleArn, ROLE_SESSION_NAME)
                .withRoleSessionDurationSeconds(MIN_SESSION_DURATION)
                .build();
        return AmazonS3ClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion(region.getRegionCode())
                .build();
    }
}
