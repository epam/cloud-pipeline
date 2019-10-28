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

package com.epam.pipeline.manager.region;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.region.AzurePolicy;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.AzureRegionCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.exception.cloud.azure.AzureException;
import com.microsoft.aad.adal4j.AuthenticationException;
import com.microsoft.azure.credentials.AzureCliCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import com.microsoft.azure.storage.blob.*;
import com.microsoft.rest.LogLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
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
    private static final int SHIFT_MASK = 0xFF;
    private static final Integer MAX_TRIES_COUNT = 1;
    private static final Integer TRY_TIMEOUT = 2;


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
        originalRegion.setAzurePolicy(updatedRegion.getAzurePolicy());
        originalRegion.setCorsRules(updatedRegion.getCorsRules());
        originalRegion.setAuthFile(updatedRegion.getAuthFile());
        originalRegion.setPriceOfferId(updatedRegion.getPriceOfferId());
        originalRegion.setAzureApiUrl(updatedRegion.getAzureApiUrl());
        originalRegion.setMeterRegionName(updatedRegion.getMeterRegionName());
        originalRegion.setSshPublicKeyPath(updatedRegion.getSshPublicKeyPath());
        originalRegion.setFileShareMounts(updatedRegion.getFileShareMounts());
        return originalRegion;
    }

    @Override
    public AzureRegionCredentials mergeCredentials(final AzureRegionCredentials oldCredentials,
                                                   final AzureRegionCredentials updatedCredentials) {
        return updatedCredentials;
    }

    void validateStorageAccount(final String storageAccountName, final String storageAccountKey) {
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
        if (Objects.isNull(policy)) {
            return;
        }

        Assert.isTrue(StringUtils.isNotBlank(policy.getIpMax()) || StringUtils.isNotBlank(policy.getIpMin()),
                messageHelper.getMessage(MessageConstants.ERROR_AZURE_IP_RANGE_IS_INVALID, policy.getIpMax(),
                        policy.getIpMin()));
        Assert.isTrue(isValidIpAddress(policy.getIpMax()),
                messageHelper.getMessage(MessageConstants.ERROR_AZURE_IP_IS_INVALID, policy.getIpMax()));
        Assert.isTrue(isValidIpAddress(policy.getIpMin()),
                messageHelper.getMessage(MessageConstants.ERROR_AZURE_IP_IS_INVALID, policy.getIpMin()));
        Assert.isTrue(isIpRangeValid(policy), messageHelper
                .getMessage(MessageConstants.ERROR_AZURE_IP_RANGE_IS_INVALID, policy.getIpMax(), policy.getIpMin()));
    }

    private boolean isValidIpAddress(final String ip) {
        return StringUtils.isBlank(ip) || InetAddressValidator.getInstance().isValid(ip);
    }

    void checkResourceGroupExistence(final String resourceGroup, final String authFilePath) {
        Assert.isTrue(StringUtils.isNotBlank(resourceGroup), messageHelper.getMessage(
                MessageConstants.ERROR_AZURE_RESOURCE_GROUP_NOT_FOUND, resourceGroup));
        try {
            final Azure client = buildClient(authFilePath);
            Assert.isTrue(client.resourceGroups().contain(resourceGroup), messageHelper.getMessage(
                    MessageConstants.ERROR_AZURE_RESOURCE_GROUP_NOT_FOUND, resourceGroup));
        } catch (AuthenticationException e) {
            throw new IllegalArgumentException(messageHelper.getMessage(
                    MessageConstants.ERROR_AZURE_AUTH_FILE_IS_INVALID), e);
        }
    }

    private Azure buildClient(final String authFile) {
        try {
            final Azure.Configurable builder = Azure.configure()
                    .withLogLevel(LogLevel.BASIC);
            return authenticate(authFile, builder)
                    .withDefaultSubscription();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new AzureException(e);
        }
    }

    private Azure.Authenticated authenticate(final String authFile,
                                             Azure.Configurable builder) throws IOException {
        if (StringUtils.isBlank(authFile)) {
            return builder.authenticate(AzureCliCredentials.create());
        }
        return builder.authenticate(new File(authFile));
    }

    private boolean isIpRangeValid(final AzurePolicy policy) {
        if (!(StringUtils.isNotBlank(policy.getIpMax()) && StringUtils.isNotBlank(policy.getIpMin()))) {
            // is not a range
            return true;
        }

        try {
            final byte[] maxIp = InetAddress.getByName(policy.getIpMax()).getAddress();
            final byte[] minIp = InetAddress.getByName(policy.getIpMin()).getAddress();

            if (maxIp.length < minIp.length) {
                return false;
            }

            if (maxIp.length > minIp.length) {
                return true;
            }

            for (int i = 0; i < maxIp.length; i++) {
                final int b1 = unsignedByteToInt(maxIp[i]);
                final int b2 = unsignedByteToInt(minIp[i]);
                if (b1 != b2) {
                    return b1 > b2;
                }
            }
            return true;
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(messageHelper.getMessage(
                    MessageConstants.ERROR_AZURE_IP_RANGE_IS_INVALID, policy.getIpMax(), policy.getIpMin()), e);
        }
    }

    private int unsignedByteToInt(final byte b) {
        return (int) b & SHIFT_MASK;
    }

}
