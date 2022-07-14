/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cloudaccess.aws;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.CreateAccessKeyRequest;
import com.amazonaws.services.identitymanagement.model.CreateAccessKeyResult;
import com.amazonaws.services.identitymanagement.model.CreateUserRequest;
import com.amazonaws.services.identitymanagement.model.CreateUserResult;
import com.amazonaws.services.identitymanagement.model.DeleteAccessKeyRequest;
import com.amazonaws.services.identitymanagement.model.DeleteUserPolicyRequest;
import com.amazonaws.services.identitymanagement.model.DeleteUserRequest;
import com.amazonaws.services.identitymanagement.model.GetUserPolicyRequest;
import com.amazonaws.services.identitymanagement.model.GetUserPolicyResult;
import com.amazonaws.services.identitymanagement.model.GetUserRequest;
import com.amazonaws.services.identitymanagement.model.GetUserResult;
import com.amazonaws.services.identitymanagement.model.ListAccessKeysRequest;
import com.amazonaws.services.identitymanagement.model.ListAccessKeysResult;
import com.amazonaws.services.identitymanagement.model.NoSuchEntityException;
import com.amazonaws.services.identitymanagement.model.PutUserPolicyRequest;
import com.amazonaws.services.identitymanagement.model.User;
import com.epam.pipeline.entity.cloudaccess.key.AWSCloudUserAccessKeys;
import com.epam.pipeline.entity.cloudaccess.key.CloudUserAccessKeys;
import com.epam.pipeline.entity.cloudaccess.policy.CloudAccessPolicy;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cloud.aws.AWSUtils;
import com.epam.pipeline.manager.cloudaccess.CloudAccessManagementService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.util.URIUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AWSAccessManagementService implements CloudAccessManagementService<AwsRegion> {

    public static final String KMS_POLICY_PREFIX = "_kms";

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.AWS;
    }

    @Override
    public boolean doesCloudUserExist(AwsRegion region, String username) {
        try {
            return Optional.ofNullable(
                    getIAMClient(region).getUser(new GetUserRequest().withUserName(username))
                    ).map(GetUserResult::getUser).map(User::getUserName).isPresent();
        } catch (NoSuchEntityException e) {
            return false;
        }
    }

    @Override
    public void createCloudUser(AwsRegion region, String username) {
        Optional.ofNullable(getIAMClient(region).createUser(new CreateUserRequest().withUserName(username)))
                .map(CreateUserResult::getUser)
                .orElseThrow(() -> new IllegalStateException(
                        String.format("There is a problem with creation of user with name: %s", username)));
    }

    @Override
    public void deleteCloudUser(final AwsRegion region, final String username) {
        getIAMClient(region).deleteUser(new DeleteUserRequest().withUserName(username));
    }

    @Override
    public CloudAccessPolicy getCloudUserPermissions(final AwsRegion region, final String username,
                                                     final String policyName) {
        try {
            final GetUserPolicyResult userPolicy = getIAMClient(region).getUserPolicy(
                    new GetUserPolicyRequest().withUserName(username).withPolicyName(policyName)
            );
            final String policyDocument;
            try {
                policyDocument = URIUtil.decode(userPolicy.getPolicyDocument());
                Assert.hasText(userPolicy.getUserName(), "Empty cloud user name!");
                Assert.hasText(policyDocument, "Empty policy document!");
            } catch (URIException e) {
                throw new IllegalStateException(
                        String.format("Can't parse policy document %s", userPolicy.getPolicyDocument()), e);
            }
            return AWSPolicyMapper.toCloudUserAccessPolicy(userPolicy.getPolicyName(), policyDocument);
        } catch (NoSuchEntityException e) {
            log.debug(String.format("No user policy with name: %s", policyName));
            return null;
        }
    }

    @Override
    public void grantCloudUserPermissions(final AwsRegion region, final String username,
                                          final String policyName,
                                          final CloudAccessPolicy userPolicy) {
        final PutUserPolicyRequest putUserS3PolicyRequest = new PutUserPolicyRequest()
                .withUserName(username)
                .withPolicyName(policyName)
                .withPolicyDocument(AWSPolicyMapper.toPolicyDocument(userPolicy));
        getIAMClient(region).putUserPolicy(putUserS3PolicyRequest);

        final PutUserPolicyRequest putUserKMSPolicyRequest = new PutUserPolicyRequest()
                .withUserName(username)
                .withPolicyName(policyName + KMS_POLICY_PREFIX)
                .withPolicyDocument(AWSPolicyMapper.getKmsPolicyDocument());
        getIAMClient(region).putUserPolicy(putUserKMSPolicyRequest);
    }

    @Override
    public void revokeCloudUserPermissions(final AwsRegion region, final String username, final String policyName) {
        final DeleteUserPolicyRequest deleteUserS3PolicyRequest = new DeleteUserPolicyRequest()
                .withUserName(username)
                .withPolicyName(policyName);
        getIAMClient(region).deleteUserPolicy(deleteUserS3PolicyRequest);

        final DeleteUserPolicyRequest deleteUserKMSPolicyRequest = new DeleteUserPolicyRequest()
                .withUserName(username)
                .withPolicyName(policyName + KMS_POLICY_PREFIX);
        getIAMClient(region).deleteUserPolicy(deleteUserKMSPolicyRequest);
    }

    @Override
    public CloudUserAccessKeys getAccessKeysForUser(final AwsRegion region, final String username,
                                                    final String keyId) {
        return listAccessKeysForUser(region, username).stream()
                .filter(accessKey -> keyId.equals(accessKey.getId()))
                .findFirst().orElse(null);
    }

    @Override
    public List<CloudUserAccessKeys> listAccessKeysForUser(AwsRegion region, String username) {
        try {
            final ListAccessKeysResult accessKeyListing = getIAMClient(region)
                    .listAccessKeys(new ListAccessKeysRequest().withUserName(username));
            return ListUtils.emptyIfNull(accessKeyListing.getAccessKeyMetadata())
                    .stream()
                    .map(accessKey ->
                            AWSCloudUserAccessKeys.builder()
                                    .regionId(region.getId())
                                    .provider(getProvider())
                                    .accessKeyId(accessKey.getAccessKeyId())
                                    .build()
                    ).collect(Collectors.toList());
        } catch (NoSuchEntityException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public CloudUserAccessKeys generateCloudKeysForUser(final AwsRegion region, final String username) {
        final CreateAccessKeyResult accessKey = getIAMClient(region)
                .createAccessKey(new CreateAccessKeyRequest().withUserName(username));
        return AWSCloudUserAccessKeys.builder()
                .regionId(region.getId())
                .provider(getProvider())
                .accessKeyId(accessKey.getAccessKey().getAccessKeyId())
                .secretKey(accessKey.getAccessKey().getSecretAccessKey())
                .build();
    }

    @Override
    public void revokeCloudKeysForUser(final AwsRegion region, final String username, final String keyId) {
        getIAMClient(region).deleteAccessKey(
                new DeleteAccessKeyRequest().withUserName(username).withAccessKeyId(keyId)
        );
    }

    public AmazonIdentityManagement getIAMClient(final AwsRegion awsRegion) {
        return AmazonIdentityManagementClientBuilder
                .standard()
                .withCredentials(AWSUtils.getCredentialsProvider(awsRegion))
                .build();
    }
}
