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

package com.epam.pipeline.manager.cloud.aws;

import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.cloud.TemporaryCredentialsGenerator;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.utils.PasswordGenerator;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class S3TemporaryCredentialsGenerator implements TemporaryCredentialsGenerator<S3bucketDataStorage> {

    private static final String GET_OBJECT_ACTION = "s3:GetObject";
    private static final String GET_OBJECT_ACL_ACTION = "s3:GetObjectAcl";
    private static final String GET_VERSION_ACTION = "s3:GetObjectVersion";
    private static final String GET_VERSION_ACL_ACTION = "s3:GetObjectVersionAcl";
    private static final String PUT_OBJECT_ACTION = "s3:PutObject";
    private static final String PUT_OBJECT_ACL_ACTION = "s3:PutObjectAcl";
    private static final String PUT_OBJECT_VERSION_ACL_ACTION = "s3:PutObjectVersionAcl";
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
    private static final String ACTION = "Action";
    private static final String RESOURCE = "Resource";

    private final CloudRegionManager cloudRegionManager;
    private final PreferenceManager preferenceManager;
    private final MessageHelper messageHelper;

    @Override
    public DataStorageType getStorageType() {
        return DataStorageType.S3;
    }

    @Override
    public TemporaryCredentials generate(final List<DataStorageAction> actions,
                                         final List<S3bucketDataStorage> storages) {
        final Integer duration = preferenceManager.getPreference(
                SystemPreferences.DATA_STORAGE_TEMP_CREDENTIALS_DURATION);
        final List<Pair<S3bucketDataStorage, AwsRegion>> storagesWithRegions = storages.stream()
                .map(storage -> new ImmutablePair<>(storage, cloudRegionManager.getAwsRegion(storage)))
                .collect(Collectors.toList());

        final String role = buildRole(storagesWithRegions);
        final String sessionName = "SessionID-" + PasswordGenerator.generateRandomString(10);
        final String profile = buildProfile(storagesWithRegions);
        final String policy = createPolicyWithPermissions(actions, buildKmsArns(storagesWithRegions));

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
                .region(buildRegion(storagesWithRegions))
                .build();
    }

    @Override
    public AwsRegion getRegion(final S3bucketDataStorage dataStorage) {
        return cloudRegionManager.getAwsRegion(dataStorage);
    }

    private String createPolicyWithPermissions(final List<DataStorageAction> actions, final List<String> kmsArns) {
        final ObjectNode resultPolicy = JsonNodeFactory.instance.objectNode();
        resultPolicy.put("Version", "2012-10-17");
        final ArrayNode statements = resultPolicy.putArray("Statement");
        ListUtils.emptyIfNull(kmsArns)
                .forEach(kmsArn -> addKmsActionToStatement(kmsArn, statements));
        ListUtils.emptyIfNull(actions)
                .forEach(action -> addActionsToStatement(action, statements));
        return resultPolicy.toString();
    }

    private void addActionsToStatement(final DataStorageAction action, final ArrayNode statements) {
        if (action.isList() || action.isRead() || action.isWrite()) {
            addListingPermissions(action, statements);
        }
        if (action.isRead() || action.isWrite()) {
            addActionToStatement(action, statements);
        }
    }

    private void addListingPermissions(final DataStorageAction action, final ArrayNode statements) {
        final ObjectNode statement = getStatement();
        final ArrayNode actions = statement.putArray(ACTION);
        actions.add(LIST_OBJECTS_ACTION);
        if (action.isListVersion() || action.isWriteVersion() || action.isReadVersion()) {
            actions.add(LIST_VERSIONS_ACTION);
        }
        final ArrayNode resource = statement.putArray(RESOURCE);
        resource.add(buildS3Arn(action, true));
        statements.add(statement);
    }

    private void addActionToStatement(final DataStorageAction action, final ArrayNode statements) {
        final ObjectNode statement = getStatement();
        final ArrayNode actions = statement.putArray(ACTION);
        if (action.isRead()) {
            actions.add(GET_OBJECT_ACTION);
            actions.add(GET_OBJECT_ACL_ACTION);
            actions.add(GET_OBJECT_TAGGING_ACTION);
            if (action.isReadVersion()) {
                actions.add(GET_VERSION_ACTION);
                actions.add(GET_VERSION_ACL_ACTION);
                actions.add(GET_OBJECT_VERSION_TAGGING_ACTION);
            }
        }
        if (action.isWrite()) {
            actions.add(PUT_OBJECT_ACTION);
            actions.add(PUT_OBJECT_ACL_ACTION);
            actions.add(DELETE_OBJECT_ACTION);
            actions.add(PUT_OBJECT_TAGGING_ACTION);
            actions.add(DELETE_OBJECT_TAGGING_ACTION);
            if (action.isWriteVersion()) {
                actions.add(DELETE_VERSION_ACTION);
                actions.add(PUT_OBJECT_VERSION_ACL_ACTION);
                actions.add(PUT_OBJECT_VERSION_TAGGING_ACTION);
                actions.add(DELETE_OBJECT_VERSION_TAGGING_ACTION);
            }
        }
        final ArrayNode resource = statement.putArray(RESOURCE);
        resource.add(buildS3Arn(action, false));
        statements.add(statement);
    }

    private ObjectNode getStatement() {
        final ObjectNode statement = JsonNodeFactory.instance.objectNode();
        statement.put("Effect", "Allow");
        return statement;
    }

    private String buildS3Arn(final DataStorageAction action, final boolean list) {
        return list ? ARN_AWS_S3_PREFIX + ProviderUtils.withoutTrailingDelimiter(action.getBucketName())
                : ARN_AWS_S3_PREFIX + ProviderUtils.withoutTrailingDelimiter(action.getPath()) + "/*";
    }

    private void addKmsActionToStatement(final String kmsArn, final ArrayNode statements) {
        final ObjectNode statement = getStatement();
        final ArrayNode actions = statement.putArray(ACTION);
        actions.add(KMS_DECRYPT_ACTION);
        actions.add(KMS_ENCRYPT_ACTION);
        actions.add(KMS_REENCRYPT_ACTION);
        actions.add(KMS_GENERATE_DATA_KEY_ACTION);
        actions.add(KMS_DESCRIBE_KEY_ACTION);
        statement.put(RESOURCE, kmsArn);
        statements.add(statement);
    }

    private List<String> buildKmsArns(final List<Pair<S3bucketDataStorage, AwsRegion>> storagesWithRegions) {
        return storagesWithRegions.stream()
                .map(pair -> AWSUtils.getKeyArnValue(pair.getLeft(), pair.getRight()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private String buildRole(final List<Pair<S3bucketDataStorage, AwsRegion>> storagesWithRegions) {
        final List<String> roles = storagesWithRegions.stream()
                .map(pair -> AWSUtils.getRoleValue(pair.getLeft(), pair.getRight()))
                .distinct()
                .collect(Collectors.toList());
        Assert.state(roles.size() == 1,
                messageHelper.getMessage(MessageConstants.ERROR_AWS_S3_ROLE_UNIQUENESS));
        return roles.get(0);
    }

    private String buildProfile(final List<Pair<S3bucketDataStorage, AwsRegion>> storagesWithRegions) {
        final List<String> profiles = storagesWithRegions.stream()
                .map(Pair::getRight)
                .map(AwsRegion::getProfile)
                .distinct()
                .collect(Collectors.toList());
        Assert.state(profiles.size() == 1,
                messageHelper.getMessage(MessageConstants.ERROR_AWS_PROFILE_UNIQUENESS));
        return profiles.get(0);
    }

    private String buildRegion(final List<Pair<S3bucketDataStorage, AwsRegion>> storagesWithRegions) {
        if (CollectionUtils.isEmpty(storagesWithRegions)) {
            return null;
        }
        final Pair<S3bucketDataStorage, AwsRegion> firstStorageWithRegion = storagesWithRegions.get(0);
        final boolean sameRegion = storagesWithRegions.stream()
                .allMatch(storageWithRegion -> assertRegion(firstStorageWithRegion, storageWithRegion));
        return sameRegion ? firstStorageWithRegion.getRight().getRegionCode() : null;
    }

    private boolean assertRegion(final Pair<S3bucketDataStorage, AwsRegion> expected,
                                 final Pair<S3bucketDataStorage, AwsRegion> actual) {
        return Objects.equals(expected.getRight().getId(), actual.getRight().getId());
    }
}
