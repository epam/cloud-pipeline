/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import com.epam.pipeline.entity.region.AbstractCloudRegionCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.GCPCustomInstanceType;
import com.epam.pipeline.entity.region.GCPCustomVMType;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


@Component
@RequiredArgsConstructor
public class GCPRegionHelper implements CloudRegionHelper<GCPRegion, AbstractCloudRegionCredentials> {

    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;

    @Override
    public void validateRegion(final GCPRegion region, final AbstractCloudRegionCredentials credentials) {
        validateRegionCode(region.getRegionCode(), messageHelper);
        Assert.state(StringUtils.isNotBlank(region.getProject()),
                messageHelper.getMessage(MessageConstants.ERROR_GCP_PROJECT_REQUIRED));
        Assert.state(StringUtils.isNotBlank(region.getSshPublicKeyPath()),
                messageHelper.getMessage(MessageConstants.ERROR_GCP_SSH_KEY_REQUIRED));
        Assert.state(StringUtils.isNotBlank(region.getImpersonatedAccount()),
                messageHelper.getMessage(MessageConstants.ERROR_GCP_IMP_ACC_REQUIRED));
        ListUtils.emptyIfNull(region.getCustomInstanceTypes()).forEach(this::validateCustomInstanceType);
    }

    @Override
    public List<String> loadAvailableRegions() {
        return preferenceManager.getPreference(SystemPreferences.GCP_REGION_LIST);
    }

    @Override
    public GCPRegion mergeRegions(final GCPRegion originalRegion, final GCPRegion updatedRegion) {
        originalRegion.setName(updatedRegion.getName());
        originalRegion.setCorsRules(updatedRegion.getCorsRules());
        originalRegion.setPolicy(updatedRegion.getPolicy());
        originalRegion.setDefault(updatedRegion.isDefault());
        originalRegion.setSshPublicKeyPath(updatedRegion.getSshPublicKeyPath());
        originalRegion.setAuthFile(updatedRegion.getAuthFile());
        originalRegion.setProject(updatedRegion.getProject());
        originalRegion.setApplicationName(updatedRegion.getApplicationName());
        originalRegion.setImpersonatedAccount(updatedRegion.getImpersonatedAccount());
        originalRegion.setCustomInstanceTypes(updatedRegion.getCustomInstanceTypes());
        originalRegion.setBackupDuration(updatedRegion.getBackupDuration());
        originalRegion.setVersioningEnabled(updatedRegion.isVersioningEnabled());
        originalRegion.setMountObjectStorageRule(updatedRegion.getMountObjectStorageRule());
        originalRegion.setMountFileStorageRule(updatedRegion.getMountFileStorageRule());
        originalRegion.setMountCredentialsRule(updatedRegion.getMountCredentialsRule());
        originalRegion.setStorageLifecycleServiceProperties(updatedRegion.getStorageLifecycleServiceProperties());
        originalRegion.setGlobalDistributionUrl(updatedRegion.getGlobalDistributionUrl());
        originalRegion.setDnsHostedZoneId(updatedRegion.getDnsHostedZoneId());
        originalRegion.setDnsHostedZoneBase(updatedRegion.getDnsHostedZoneBase());
        originalRegion.setRunShiftPolicy(updatedRegion.getRunShiftPolicy());
        return originalRegion;
    }

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.GCP;
    }

    private void validateCustomInstanceType(final GCPCustomInstanceType instanceType) {
        Assert.state(instanceType.getCpu() > 1,
                messageHelper.getMessage(MessageConstants.ERROR_GCP_CUSTOM_INSTANCE_CPU_LOWER_LIMIT));
        if (StringUtils.isBlank(instanceType.getFamily())) {
            return;
        }
        Assert.state(EnumUtils.isValidEnum(GCPCustomVMType.class, instanceType.getFamily()),
                messageHelper.getMessage(MessageConstants.ERROR_GCP_CUSTOM_INSTANCE_FAMILY_NOT_SUPPORTED,
                        instanceType.getFamily(), Arrays.stream(GCPCustomVMType.values())
                                .map(GCPCustomVMType::name)
                                .collect(Collectors.joining(", "))));
        final GCPCustomVMType family = GCPCustomVMType.valueOf(instanceType.getFamily().toLowerCase());
        final boolean gpuEnabled = StringUtils.isNotBlank(instanceType.getGpuType()) && instanceType.getGpu() > 0;
        Assert.state(!gpuEnabled || GCPCustomVMType.n1.equals(family),
                messageHelper.getMessage(MessageConstants.ERROR_GCP_CUSTOM_INSTANCE_FAMILY_NOT_ALLOWED_WITH_GPU));
        instanceType.setFamily(family.name());
    }
}
