/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cloud.aws.capacityreservation;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSSessionCredentials;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.cloud.aws.AWSUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;

/**
 * Builds the AWS SDK v2 {@link Ec2Client} that the capacity reservation calls need.
 *
 * <p>The rest of the platform talks to EC2 through SDK v1; future-dated capacity reservations exist only in v2, so
 * this provider keeps that second SDK in one place. Credentials are resolved from the region's own configuration,
 * in the order v1 resolves them: an assumed IAM role first, then a named profile, then the default chain.
 *
 * <p>A role is assumed through the v1 {@link AWSUtils} provider and the result handed to v2 as static credentials,
 * so the session token obtained for the role is preserved rather than dropped.
 */
@Service
public class AWSV2EC2ClientProvider {

    public Ec2Client client(final AwsRegion region) {
        return Ec2Client.builder()
                .region(Region.of(region.getRegionCode()))
                .credentialsProvider(credentialsProvider(region))
                .build();
    }

    private AwsCredentialsProvider credentialsProvider(final AwsRegion region) {
        if (StringUtils.isNotBlank(region.getIamRole())) {
            return StaticCredentialsProvider.create(assumedRoleCredentials(region));
        }
        if (StringUtils.isNotBlank(region.getProfile())) {
            return ProfileCredentialsProvider.create(region.getProfile());
        }
        return DefaultCredentialsProvider.create();
    }

    private AwsCredentials assumedRoleCredentials(final AwsRegion region) {
        final AWSCredentials credentials = AWSUtils.getCredentialsProvider(region).getCredentials();
        if (credentials instanceof AWSSessionCredentials) {
            final AWSSessionCredentials session = (AWSSessionCredentials) credentials;
            return AwsSessionCredentials.create(session.getAWSAccessKeyId(), session.getAWSSecretKey(),
                    session.getSessionToken());
        }
        return AwsBasicCredentials.create(credentials.getAWSAccessKeyId(), credentials.getAWSSecretKey());
    }
}
