/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.cloud.TemporaryCredentialsGenerator;
import com.epam.pipeline.utils.PasswordGenerator;
import org.apache.commons.lang3.StringUtils;

public final class AWSUtils {

    private static final String EMPTY_KEY_ARN = "NONE";

    private AWSUtils() {
        //no op
    }

    public static AWSCredentialsProvider getCredentialsProvider(final String profile) {
        if (StringUtils.isEmpty(profile)) {
            return DefaultAWSCredentialsProviderChain.getInstance();
        }
        return new ProfileCredentialsProvider(profile);
    }

    /**
     * @return KMS key arn from storage if it is specified, otherwise returns key from region
     */
    public static String getKeyArnValue(final S3bucketDataStorage storage, final AwsRegion region) {
        if (StringUtils.isBlank(storage.getKmsKeyArn())) {
            return region.getKmsKeyArn();
        }
        if (EMPTY_KEY_ARN.equals(storage.getKmsKeyArn())) {
            return null;
        }
        return storage.getKmsKeyArn();
    }

    /**
     * @return Role from storage if it is specified, otherwise returns role from region
     */
    public static String getRoleValue(final S3bucketDataStorage storage, final AwsRegion region) {
        if (StringUtils.isBlank(storage.getTempCredentialsRole())) {
            return region.getTempCredentialsRole();
        }
        return storage.getTempCredentialsRole();
    }

    public static TemporaryCredentials generate(final Integer duration, final String policy, final String role,
                                                final String profile, final String regionCode) {
        final String sessionName = "SessionID-" + PasswordGenerator.generateRandomString(10);

        final AssumeRoleRequest assumeRoleRequest = new AssumeRoleRequest()
                .withDurationSeconds(duration)
                .withPolicy(policy)
                .withRoleSessionName(sessionName)
                .withRoleArn(role);

        final AssumeRoleResult assumeRoleResult = AWSSecurityTokenServiceClientBuilder.standard()
                .withCredentials(AWSUtils.getCredentialsProvider(profile))
                .build()
                .assumeRole(assumeRoleRequest);
        final Credentials resultingCredentials = assumeRoleResult.getCredentials();

        return TemporaryCredentials.builder()
                .accessKey(resultingCredentials.getSecretAccessKey())
                .keyId(resultingCredentials.getAccessKeyId())
                .token(resultingCredentials.getSessionToken())
                .expirationTime(TemporaryCredentialsGenerator
                        .expirationTimeWithUTC(resultingCredentials.getExpiration()))
                .region(regionCode)
                .build();
    }
}
