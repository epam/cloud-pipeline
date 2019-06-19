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
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.AzureRegionCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AzureRegionHelper implements CloudRegionHelper<AzureRegion, AzureRegionCredentials> {

    private static final String GOVERNMENT_PREFIX = "GOV_";
    private final MessageHelper messageHelper;

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.AZURE;
    }

    @Override
    public void validateRegion(final AzureRegion region, final AzureRegionCredentials credentials) {
        validateRegionCode(region.getRegionCode(), messageHelper);
        Assert.isTrue(StringUtils.isNotBlank(region.getStorageAccount()),
                messageHelper.getMessage(MessageConstants.ERROR_AZURE_STORAGE_ACC_REQUIRED));
        Assert.isTrue(StringUtils.isNotBlank(credentials.getStorageAccountKey()),
                messageHelper.getMessage(MessageConstants.ERROR_AZURE_STORAGE_KEY_REQUIRED));
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
}
