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

package com.epam.pipeline.manager.region;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.region.AzurePolicy;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.AzureRegionCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.datastorage.providers.azure.AzureHelper;
import com.epam.pipeline.utils.Base64Utils;
import com.epam.pipeline.utils.NetworkUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.aad.adal4j.AuthenticationException;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import com.microsoft.azure.storage.blob.PipelineOptions;
import com.microsoft.azure.storage.blob.RequestRetryOptions;
import com.microsoft.azure.storage.blob.RetryPolicyType;
import com.microsoft.azure.storage.blob.ServiceURL;
import com.microsoft.azure.storage.blob.SharedKeyCredentials;
import com.microsoft.azure.storage.blob.StorageURL;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class AzureRegionHelper implements CloudRegionHelper<AzureRegion, AzureRegionCredentials> {

    private static final String GOVERNMENT_PREFIX = "GOV_";
    private final MessageHelper messageHelper;
    private static final String BLOB_URL_FORMAT = "https://%s.blob.core.windows.net";
    private static final Integer MAX_TRIES_COUNT = 1;
    private static final Integer TRY_TIMEOUT = 2;

    private static final String STORAGE_ACCOUNT = "storage_account";
    private static final String STORAGE_KEY = "storage_key";

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.AZURE;
    }

    @Override
    public void validateRegion(final AzureRegion region, final AzureRegionCredentials credentials) {
        validateRegionCode(region.getRegionCode(), messageHelper);
        validateStorageAccount(region.getStorageAccount(), credentials.getStorageAccountKey());
        checkResourceGroupExistence(region.getResourceGroup(), region.getAuthFile());
        validateStoragePolicy(region.getAzurePolicy());

    }

    @Override
    public List<String> loadAvailableRegions() {
        return Arrays.stream(Region.values())
                .filter(region -> !region.name().startsWith(GOVERNMENT_PREFIX))
                .map(Region::name)
                .collect(Collectors.toList());
    }

    @Override
    public AzureRegion mergeRegions(final AzureRegion originalRegion, final AzureRegion updatedRegion) {
        originalRegion.setName(updatedRegion.getName());
        originalRegion.setDefault(updatedRegion.isDefault());
        if (azurePolicyExist(updatedRegion.getAzurePolicy())) {
            originalRegion.setAzurePolicy(updatedRegion.getAzurePolicy());
        } else {
            originalRegion.setAzurePolicy(null);
        }
        originalRegion.setCorsRules(updatedRegion.getCorsRules());
        originalRegion.setAuthFile(updatedRegion.getAuthFile());
        originalRegion.setPriceOfferId(updatedRegion.getPriceOfferId());
        originalRegion.setEnterpriseAgreements(updatedRegion.isEnterpriseAgreements());
        originalRegion.setAzureApiUrl(updatedRegion.getAzureApiUrl());
        originalRegion.setMeterRegionName(updatedRegion.getMeterRegionName());
        originalRegion.setSshPublicKeyPath(updatedRegion.getSshPublicKeyPath());
        originalRegion.setFileShareMounts(updatedRegion.getFileShareMounts());
        originalRegion.setMountStorageRule(updatedRegion.getMountStorageRule());
        return originalRegion;
    }

    private boolean azurePolicyExist(AzurePolicy policy) {
        return !Objects.isNull(policy) &&
                (StringUtils.isNotBlank(policy.getIpMax()) && StringUtils.isNotBlank(policy.getIpMin()));
    }

    @Override
    public AzureRegionCredentials mergeCredentials(final AzureRegionCredentials oldCredentials,
                                                   final AzureRegionCredentials updatedCredentials) {
        if (StringUtils.isNotBlank(updatedCredentials.getStorageAccountKey())) {
            oldCredentials.setStorageAccountKey(updatedCredentials.getStorageAccountKey());
        }
        return oldCredentials;
    }

    @Override
    public String serializeCredentials(AzureRegion region, AzureRegionCredentials credentials) {
        final HashMap<String, String> creds = new HashMap<>();
        creds.put(STORAGE_ACCOUNT, region.getStorageAccount());
        creds.put(STORAGE_KEY, credentials.getStorageAccountKey());
        try {
            return Base64Utils.encodeBase64Map(creds);
        } catch (JsonProcessingException e) {
            log.error(e.getMessage());
            return Base64Utils.EMPTY_ENCODED_MAP;
        }
    }

    private void validateStorageAccount(final String storageAccountName, final String storageAccountKey) {
        Assert.isTrue(StringUtils.isNotBlank(storageAccountName),
                messageHelper.getMessage(MessageConstants.ERROR_AZURE_STORAGE_ACC_REQUIRED));
        Assert.isTrue(StringUtils.isNotBlank(storageAccountKey),
                messageHelper.getMessage(MessageConstants.ERROR_AZURE_STORAGE_KEY_REQUIRED));
        checkThatCredentialsIsActive(storageAccountName, storageAccountKey);
    }

    void checkThatCredentialsIsActive(final String storageAccountName, final String storageAccountKey) {
        try {
            final SharedKeyCredentials credentials = new SharedKeyCredentials(storageAccountName, storageAccountKey);
            final RequestRetryOptions requestRetryOptions = new RequestRetryOptions(RetryPolicyType.EXPONENTIAL,
                    MAX_TRIES_COUNT, TRY_TIMEOUT, null, null, null);
            final PipelineOptions pipelineOptions = new PipelineOptions().withRequestRetryOptions(requestRetryOptions);
            final ServiceURL serviceURL = new ServiceURL(new URL(
                    String.format(BLOB_URL_FORMAT, storageAccountName)),
                    StorageURL.createPipeline(credentials, pipelineOptions));
            serviceURL.getProperties().blockingGet();
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    messageHelper.getMessage(MessageConstants.ERROR_AZURE_STORAGE_CREDENTIAL_INVALID), e);
        }
    }

    void validateStoragePolicy(final AzurePolicy policy) {
        if (Objects.isNull(policy) ||
                (StringUtils.isBlank(policy.getIpMax()) && StringUtils.isBlank(policy.getIpMin()))) {
            return;
        }

        Assert.isTrue(StringUtils.isNotBlank(policy.getIpMax()) && StringUtils.isNotBlank(policy.getIpMin()),
                messageHelper.getMessage(MessageConstants.ERROR_AZURE_IP_RANGE_IS_INVALID, policy.getIpMax(),
                        policy.getIpMin()));
        Assert.isTrue(NetworkUtils.isValidIpAddress(policy.getIpMax()),
                messageHelper.getMessage(MessageConstants.ERROR_AZURE_IP_IS_INVALID, policy.getIpMax()));
        Assert.isTrue(NetworkUtils.isValidIpAddress(policy.getIpMin()),
                messageHelper.getMessage(MessageConstants.ERROR_AZURE_IP_IS_INVALID, policy.getIpMin()));
        Assert.isTrue(NetworkUtils.isIpRangeValid(policy), messageHelper
                .getMessage(MessageConstants.ERROR_AZURE_IP_RANGE_IS_INVALID, policy.getIpMax(), policy.getIpMin()));
    }

    void checkResourceGroupExistence(final String resourceGroup, final String authFilePath) {
        Assert.isTrue(StringUtils.isNotBlank(resourceGroup), messageHelper.getMessage(
                MessageConstants.ERROR_AZURE_RESOURCE_GROUP_NOT_FOUND, resourceGroup));
        try {
            final Azure client = AzureHelper.buildClient(authFilePath);
            Assert.isTrue(client.resourceGroups().contain(resourceGroup), messageHelper.getMessage(
                    MessageConstants.ERROR_AZURE_RESOURCE_GROUP_NOT_FOUND, resourceGroup));
        } catch (AuthenticationException e) {
            throw new IllegalArgumentException(messageHelper.getMessage(
                    MessageConstants.ERROR_AZURE_AUTH_FILE_IS_INVALID), e);
        }
    }
}
