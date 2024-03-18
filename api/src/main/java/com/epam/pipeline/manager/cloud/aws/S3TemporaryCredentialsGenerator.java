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
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.AwsRegionCredentials;
import com.epam.pipeline.manager.cloud.TemporaryCredentialsGenerator;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
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
public class S3TemporaryCredentialsGenerator extends AbstractAWSTemporaryCredentialsGenerator<S3bucketDataStorage> {

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
    private static final String ARN_AWS_S3_PREFIX = "arn:aws:s3:::";

    public S3TemporaryCredentialsGenerator(final CloudRegionManager cloudRegionManager,
                                           final PreferenceManager preferenceManager,
                                           final MessageHelper messageHelper) {
        super(cloudRegionManager, preferenceManager, messageHelper);
    }

    @Override
    public DataStorageType getStorageType() {
        return DataStorageType.S3;
    }

    @Override
    void addListingPermissions(final DataStorageAction action, final ArrayNode statements) {
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

    @Override
    void addActionToStatement(final DataStorageAction action, final ArrayNode statements) {
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

    private String buildS3Arn(final DataStorageAction action, final boolean list) {
        return list ? ARN_AWS_S3_PREFIX + ProviderUtils.withoutTrailingDelimiter(action.getBucketName())
                : ARN_AWS_S3_PREFIX + ProviderUtils.withoutTrailingDelimiter(action.getPath()) + "/*";
    }

}
