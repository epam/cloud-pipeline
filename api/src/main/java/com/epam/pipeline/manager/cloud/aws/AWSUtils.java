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
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.datastorage.aws.AbstractAWSDataStorage;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.cloud.TemporaryCredentialsGenerator;
import com.epam.pipeline.utils.PasswordGenerator;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class AWSUtils {

    public static final String AWS_ACCESS_KEY_ID_VAR = "AWS_ACCESS_KEY_ID";
    public static final String AWS_SECRET_ACCESS_KEY_VAR = "AWS_SECRET_ACCESS_KEY";
    public static final String AWS_SESSION_TOKEN_VAR = "AWS_SESSION_TOKEN";
    public static final String AWS_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss z";
    public static final DateTimeFormatter AWS_DATE_FORMATTER = DateTimeFormatter.ofPattern(AWS_DATE_FORMAT);
    public static final String ROLE_SESSION_NAME = "CLOUD_PIPELINE_SESSION";
    public static final int MIN_SESSION_DURATION = 900;

    private static final String EMPTY_KEY_ARN = "NONE";
    private static final String EMPTY_JSON = "{}";

    private AWSUtils() {
        //no op
    }

    public static AWSCredentialsProvider getCredentialsProvider(final AwsRegion region) {
        if (StringUtils.isNotBlank(region.getIamRole())) {
            return new STSAssumeRoleSessionCredentialsProvider.Builder(region.getIamRole(), ROLE_SESSION_NAME)
                    .withRoleSessionDurationSeconds(MIN_SESSION_DURATION)
                    .build();
        }
        return getCredentialsProvider(region.getProfile());
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
    public static String getKeyArnValue(final AbstractAWSDataStorage storage, final AwsRegion region) {
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
    public static String getRoleValue(final AbstractAWSDataStorage storage, final AwsRegion region) {
        if (StringUtils.isBlank(storage.getTempCredentialsRole())) {
            return region.getTempCredentialsRole();
        }
        return storage.getTempCredentialsRole();
    }

    public static String getPolicy(final String policy) {
        return StringUtils.isBlank(policy) || EMPTY_JSON.equals(policy) ? null : policy;
    }

    public static TemporaryCredentials generate(final Integer duration, final String policy, final String role,
                                                final String profile, final String regionCode) {
        final String sessionName = "SessionID-" + PasswordGenerator.generateRandomString(10);

        final AssumeRoleRequest assumeRoleRequest = new AssumeRoleRequest()
                .withDurationSeconds(duration)
                .withPolicy(getPolicy(policy))
                .withRoleSessionName(sessionName)
                .withRoleArn(role);

        final AssumeRoleResult assumeRoleResult = AWSSecurityTokenServiceClientBuilder.standard()
                .withCredentials(getCredentialsProvider(profile))
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

    public static LocalDateTime parseAwsDate(final String date) {
        return LocalDateTime.parse(date, AWS_DATE_FORMATTER);
    }


}
