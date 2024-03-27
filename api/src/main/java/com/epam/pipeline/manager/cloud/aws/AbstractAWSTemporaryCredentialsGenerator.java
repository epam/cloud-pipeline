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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.datastorage.aws.AbstractAWSDataStorage;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.AwsRegionCredentials;
import com.epam.pipeline.manager.cloud.TemporaryCredentialsGenerator;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public abstract class AbstractAWSTemporaryCredentialsGenerator<T extends AbstractAWSDataStorage>
        implements TemporaryCredentialsGenerator<T> {

    private static final String KMS_DECRYPT_ACTION = "kms:Decrypt";
    private static final String KMS_ENCRYPT_ACTION = "kms:Encrypt";
    private static final String KMS_REENCRYPT_ACTION = "kms:ReEncrypt*";
    private static final String KMS_GENERATE_DATA_KEY_ACTION = "kms:GenerateDataKey*";
    private static final String KMS_DESCRIBE_KEY_ACTION = "kms:DescribeKey";
    static final String ACTION = "Action";
    static final String RESOURCE = "Resource";

    private final CloudRegionManager cloudRegionManager;
    private final PreferenceManager preferenceManager;
    private final MessageHelper messageHelper;

    @Override
    public TemporaryCredentials generate(final List<DataStorageAction> actions,
                                         final List<T> storages) {
        final Integer duration = preferenceManager.getPreference(
                SystemPreferences.DATA_STORAGE_TEMP_CREDENTIALS_DURATION);
        final List<Pair<T, AwsRegion>> storagesWithRegions = storages.stream()
                .map(storage -> new ImmutablePair<>(storage, cloudRegionManager.getAwsRegion(storage)))
                .collect(Collectors.toList());

        final Optional<AwsRegion> customEndpoint = storagesWithRegions.stream()
                .map(Pair::getRight)
                .filter(region -> StringUtils.isNotBlank(region.getS3Endpoint()))
                .findFirst();

        if (customEndpoint.isPresent()) {
            return localRegionCredentials(customEndpoint.get());
        }

        final String role = buildRole(storagesWithRegions);
        final String profile = buildProfile(storagesWithRegions);
        final String policy = createPolicyWithPermissions(actions, buildKmsArns(storagesWithRegions));
        final String regionCode = buildRegion(storagesWithRegions);

        return AWSUtils.generate(duration, policy, role, profile, regionCode);
    }

    private TemporaryCredentials localRegionCredentials(final AwsRegion region) {
        final AwsRegionCredentials credentials = cloudRegionManager.loadCredentials(region);
        Assert.notNull(credentials, "Missing local AWS region credentials");
        return TemporaryCredentials.builder()
                .keyId(credentials.getKeyId())
                .accessKey(credentials.getAccessKey())
                .region(region.getRegionCode())
                .build();
    }

    @Override
    public AwsRegion getRegion(final T dataStorage) {
        return cloudRegionManager.getAwsRegion(dataStorage);
    }

    String createPolicyWithPermissions(final List<DataStorageAction> actions, final List<String> kmsArns) {
        final ObjectNode resultPolicy = JsonNodeFactory.instance.objectNode();
        resultPolicy.put("Version", "2012-10-17");
        final ArrayNode statements = resultPolicy.putArray("Statement");
        ListUtils.emptyIfNull(kmsArns)
                .forEach(kmsArn -> addKmsActionToStatement(kmsArn, statements));
        ListUtils.emptyIfNull(actions)
                .forEach(action -> addActionsToStatement(action, statements));
        return resultPolicy.toString();
    }

    void addActionsToStatement(final DataStorageAction action, final ArrayNode statements) {
        if (action.isList() || action.isRead() || action.isWrite()) {
            addListingPermissions(action, statements);
        }
        if (action.isRead() || action.isWrite()) {
            addActionToStatement(action, statements);
        }
    }

    ObjectNode getStatement() {
        final ObjectNode statement = JsonNodeFactory.instance.objectNode();
        statement.put("Effect", "Allow");
        return statement;
    }

    abstract void addListingPermissions(DataStorageAction action, ArrayNode statements);

    abstract void addActionToStatement(DataStorageAction action, ArrayNode statements);


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

    private List<String> buildKmsArns(final List<Pair<T, AwsRegion>> storagesWithRegions) {
        return storagesWithRegions.stream()
                .map(pair -> AWSUtils.getKeyArnValue(pair.getLeft(), pair.getRight()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private String buildRole(final List<Pair<T, AwsRegion>> storagesWithRegions) {
        final List<String> roles = storagesWithRegions.stream()
                .map(pair -> AWSUtils.getRoleValue(pair.getLeft(), pair.getRight()))
                .distinct()
                .collect(Collectors.toList());
        Assert.state(roles.size() == 1,
                messageHelper.getMessage(MessageConstants.ERROR_AWS_S3_ROLE_UNIQUENESS));
        return roles.get(0);
    }

    private String buildProfile(final List<Pair<T, AwsRegion>> storagesWithRegions) {
        final List<String> profiles = storagesWithRegions.stream()
                .map(Pair::getRight)
                .map(AwsRegion::getProfile)
                .distinct()
                .collect(Collectors.toList());
        Assert.state(profiles.size() == 1,
                messageHelper.getMessage(MessageConstants.ERROR_AWS_PROFILE_UNIQUENESS));
        return profiles.get(0);
    }

    private String buildRegion(final List<Pair<T, AwsRegion>> storagesWithRegions) {
        if (CollectionUtils.isEmpty(storagesWithRegions)) {
            return null;
        }
        final Pair<T, AwsRegion> firstStorageWithRegion = storagesWithRegions.get(0);
        final boolean sameRegion = storagesWithRegions.stream()
                .allMatch(storageWithRegion -> assertRegion(firstStorageWithRegion, storageWithRegion));
        return sameRegion ? firstStorageWithRegion.getRight().getRegionCode() : null;
    }

    private boolean assertRegion(final Pair<T, AwsRegion> expected,
                                 final Pair<T, AwsRegion> actual) {
        return Objects.equals(expected.getRight().getId(), actual.getRight().getId());
    }
}
