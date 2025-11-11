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

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.AwsRegionCredentials;
import com.epam.pipeline.manager.cloud.aws.AWSUtils;
import com.epam.pipeline.manager.datastorage.providers.StorageEventCollector;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * Provides methods for AWS S3 operations in specified region.
 */
public class RegionAwareS3Helper extends S3Helper {

    private final AwsRegion region;
    private final AwsRegionCredentials credentials;

    public RegionAwareS3Helper(final StorageEventCollector events,
                               final MessageHelper messageHelper,
                               final AwsRegion region,
                               final AwsRegionCredentials credentials) {
        super(events, messageHelper);
        this.region = region;
        this.credentials = credentials;
    }

    @Override
    public AmazonS3 getDefaultS3Client() {
        final AmazonS3ClientBuilder clientBuilder = AmazonS3ClientBuilder.standard();

        if (StringUtils.isNotBlank(region.getS3Endpoint())) {
            clientBuilder.withEndpointConfiguration(
                    new AwsClientBuilder.EndpointConfiguration(region.getS3Endpoint(), null)
            );
            clientBuilder.withPathStyleAccessEnabled(true);
        } else {
            clientBuilder.withRegion(region.getRegionCode());
        }

        if (Objects.nonNull(credentials)) {
            final BasicAWSCredentials awsCredentials =
                    new BasicAWSCredentials(credentials.getKeyId(), credentials.getAccessKey());
            clientBuilder.withCredentials(new AWSStaticCredentialsProvider(awsCredentials));
        } else {
            clientBuilder.withCredentials(AWSUtils.getCredentialsProvider(region.getProfile()));
        }
        return clientBuilder.build();
    }
}
