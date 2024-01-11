/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.LocalRegion;
import com.epam.pipeline.entity.region.LocalRegionCredentials;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class LocalRegionHelperImpl implements CloudRegionHelper<LocalRegion, LocalRegionCredentials> {
    @Override
    public CloudProvider getProvider() {
        return CloudProvider.LOCAL;
    }

    @Override
    public void validateRegion(LocalRegion region, LocalRegionCredentials credentials) {
        //pass
    }

    @Override
    public List<String> loadAvailableRegions() {
        return Collections.emptyList();
    }

    @Override
    public LocalRegion mergeRegions(LocalRegion originalRegion, LocalRegion updatedRegion) {
        originalRegion.setName(updatedRegion.getName());
        originalRegion.setDefault(updatedRegion.isDefault());
        originalRegion.setFileShareMounts(updatedRegion.getFileShareMounts());
        originalRegion.setMountObjectStorageRule(updatedRegion.getMountObjectStorageRule());
        originalRegion.setMountFileStorageRule(updatedRegion.getMountFileStorageRule());
        originalRegion.setMountCredentialsRule(updatedRegion.getMountCredentialsRule());
        originalRegion.setStorageLifecycleServiceProperties(updatedRegion.getStorageLifecycleServiceProperties());
        originalRegion.setGlobalDistributionUrl(updatedRegion.getGlobalDistributionUrl());
        originalRegion.setDnsHostedZoneId(updatedRegion.getDnsHostedZoneId());
        originalRegion.setDnsHostedZoneBase(updatedRegion.getDnsHostedZoneBase());
        originalRegion.setRunShiftPolicy(updatedRegion.getRunShiftPolicy());
        originalRegion.setCustomInstanceTypes(updatedRegion.getCustomInstanceTypes());
        return originalRegion;
    }
}
