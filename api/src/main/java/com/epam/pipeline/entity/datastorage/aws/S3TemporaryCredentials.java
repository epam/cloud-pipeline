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

package com.epam.pipeline.entity.datastorage.aws;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.epam.pipeline.entity.datastorage.AbstractTemporaryCredentials;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.utils.PasswordGenerator;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class S3TemporaryCredentials extends AbstractTemporaryCredentials {

    private static final String GET_OBJECT_ACTION = "s3:GetObject";
    private static final String GET_VERSION_ACTION = "s3:GetObjectVersion";
    private static final String PUT_OBJECT_ACTION = "s3:PutObject";
    private static final String DELETE_OBJECT_ACTION = "s3:DeleteObject";
    private static final String DELETE_VERSION_ACTION = "s3:DeleteObjectVersion";
    private static final String LIST_OBJECTS_ACTION = "s3:ListBucket";
    private static final String LIST_VERSIONS_ACTION = "s3:ListBucketVersions";
    private static final String PUT_OBJECT_TAGGING_ACTION = "s3:PutObjectTagging";
    private static final String GET_OBJECT_TAGGING_ACTION = "s3:GetObjectTagging";
    private static final String DELETE_OBJECT_TAGGING_ACTION = "s3:DeleteObjectTagging";
    private static final String DELETE_OBJECT_VERSION_TAGGING_ACTION = "s3:DeleteObjectVersionTagging";
    private static final String PUT_OBJECT_VERSION_TAGGING_ACTION = "s3:PutObjectVersionTagging";
    private static final String GET_OBJECT_VERSION_TAGGING_ACTION = "s3:GetObjectVersionTagging";
    private static final String KMS_DECRYPT_ACTION = "kms:Decrypt";
    private static final String KMS_ENCRYPT_ACTION = "kms:Encrypt";
    private static final String KMS_REENCRYPT_ACTION = "kms:ReEncrypt*";
    private static final String KMS_GENERATE_DATA_KEY_ACTION = "kms:GenerateDataKey*";
    private static final String KMS_DESCRIBE_KEY_ACTION = "kms:DescribeKey";
    private static final String ARN_AWS_S3_PREFIX = "arn:aws:s3:::";

    private String sessionName;

    public S3TemporaryCredentials() {
        this.sessionName = "SessionID-" + PasswordGenerator.generateRandomString(10);
    }

    @Override public AbstractTemporaryCredentials generate(List<DataStorageAction> actions) {
        String policy = createPolicyWithPermissions(actions);
        AssumeRoleRequest assumeRoleRequest =
                new AssumeRoleRequest()
                        .withDurationSeconds(getDuration())
                        .withPolicy(policy)
                        .withRoleSessionName(sessionName)
                        .withRoleArn(getRole());

        AWSSecurityTokenServiceClientBuilder builder = AWSSecurityTokenServiceClientBuilder.standard();
        builder.setRegion(getAwsRegionId());
        builder.setCredentials(DefaultAWSCredentialsProviderChain.getInstance());
        AssumeRoleResult assumeRoleResult = builder.build().assumeRole(assumeRoleRequest);

        Credentials resultingCredentials = assumeRoleResult.getCredentials();
        setAccessKey(resultingCredentials.getSecretAccessKey());
        setKeyId(resultingCredentials.getAccessKeyId());
        setToken(resultingCredentials.getSessionToken());
        setExpirationTime(expirationTimeWithUTC(resultingCredentials.getExpiration()));
        setRegion(getAwsRegionId());
        return this;
    }

    private String expirationTimeWithUTC(Date expiration) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return simpleDateFormat.format(expiration);
    }

    private String createPolicyWithPermissions(List<DataStorageAction> actions) {
        ObjectNode resultPolicy = JsonNodeFactory.instance.objectNode();
        resultPolicy.put("Version", "2012-10-17");
        ArrayNode statements = resultPolicy.putArray("Statement");
        final String kmsArn = getKmsArn();
        if (StringUtils.isNotBlank(kmsArn)) {
            addKmsActionToStatement(kmsArn, statements);
        }
        for (DataStorageAction action : actions) {
            addActionToStatement(action, statements, true);
            addActionToStatement(action, statements, false);
        }
        return resultPolicy.toString();
    }

    private void addActionToStatement(DataStorageAction dataStorageActions, ArrayNode statements, boolean listBucket) {
        ObjectNode statement = JsonNodeFactory.instance.objectNode();
        statement.put("Effect", "Allow");
        ArrayNode actions = statement.putArray("Action");
        if (listBucket) {
            actions.add(LIST_OBJECTS_ACTION);
            if (dataStorageActions.getReadVersion()) {
                actions.add(LIST_VERSIONS_ACTION);
            }
        } else {
            if (dataStorageActions.getRead()) {
                actions.add(GET_OBJECT_ACTION);
                actions.add(GET_OBJECT_TAGGING_ACTION);
                if (dataStorageActions.getReadVersion()) {
                    actions.add(GET_VERSION_ACTION);
                    actions.add(GET_OBJECT_VERSION_TAGGING_ACTION);
                }
            }
            if (dataStorageActions.getWrite()) {
                actions.add(PUT_OBJECT_ACTION);
                actions.add(DELETE_OBJECT_ACTION);
                actions.add(PUT_OBJECT_TAGGING_ACTION);
                actions.add(DELETE_OBJECT_TAGGING_ACTION);
                if (dataStorageActions.getWriteVersion()) {
                    actions.add(DELETE_VERSION_ACTION);
                    actions.add(PUT_OBJECT_VERSION_TAGGING_ACTION);
                    actions.add(DELETE_OBJECT_VERSION_TAGGING_ACTION);
                }
            }
        }
        ArrayNode resource = statement.putArray("Resource");
        String s3Arn = ARN_AWS_S3_PREFIX + dataStorageActions.getBucketName();
        if (!listBucket) {
            s3Arn += "/*";
        }
        resource.add(s3Arn);
        statements.add(statement);
    }

    private void addKmsActionToStatement(String kmsArn, ArrayNode statements) {
        ObjectNode statement = JsonNodeFactory.instance.objectNode();
        statement.put("Effect", "Allow");
        ArrayNode actions = statement.putArray("Action");
        actions.add(KMS_DECRYPT_ACTION);
        actions.add(KMS_ENCRYPT_ACTION);
        actions.add(KMS_REENCRYPT_ACTION);
        actions.add(KMS_GENERATE_DATA_KEY_ACTION);
        actions.add(KMS_DESCRIBE_KEY_ACTION);
        statement.put("Resource", kmsArn);
        statements.add(statement);
    }
}
