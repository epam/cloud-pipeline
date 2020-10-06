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

package com.epam.pipeline.controller.region;

import com.epam.pipeline.controller.vo.region.AWSRegionDTO;
import com.epam.pipeline.controller.vo.region.AzureRegionDTO;
import com.epam.pipeline.controller.vo.region.GCPRegionDTO;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.AzurePolicy;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.GCPCustomInstanceType;
import com.epam.pipeline.entity.region.GCPRegion;

import java.util.ArrayList;
import java.util.List;

public final class RegionCreatorUtils {

    private RegionCreatorUtils() {

    }

    public static AwsRegion getDefaultAwsRegion() {
        AwsRegion region = new AwsRegion();
        region.setRegionCode("us-east-1");
        region.setName("US East");
        region.setDefault(true);
        return region;
    }

    public static AzureRegion getDefaultAzureRegion() {
        AzureRegion region = new AzureRegion();
        region.setDefault(true);
        region.setProvider(CloudProvider.AZURE);
        region.setResourceGroup("resourceGroup");
        region.setStorageAccount("storageAcc");
        return region;
    }

    public static GCPRegion getDefaultGcpRegion() {
        GCPRegion region = new GCPRegion();
        region.setCustomInstanceTypes(getGcpCustomInstanceTypes());
        region.setDefault(true);
        return region;
    }

    private static List<GCPCustomInstanceType> getGcpCustomInstanceTypes() {
        List<GCPCustomInstanceType> customInstanceTypes = new ArrayList<>();
        customInstanceTypes.add(GCPCustomInstanceType.withCpu(4, 16));
        customInstanceTypes.add(GCPCustomInstanceType.withGpu(4, 16, 4, "test"));
        return customInstanceTypes;
    }

    public static AWSRegionDTO getDefaultAwsRegionDTO() {
        AWSRegionDTO awsRegionDTO = new AWSRegionDTO();
        awsRegionDTO.setRegionCode("us-east-1");
        awsRegionDTO.setName("US East");
        awsRegionDTO.setKmsKeyId("test");
        awsRegionDTO.setKmsKeyArn("tset");
        awsRegionDTO.setProfile("testProfile");
        awsRegionDTO.setSshKeyName("testKey");
        awsRegionDTO.setTempCredentialsRole("testRole");
        awsRegionDTO.setDefault(true);
        return awsRegionDTO;
    }

    public static AzureRegionDTO getDefaultAzureRegionDTO() {
        AzureRegionDTO regionDTO = new AzureRegionDTO();
        regionDTO.setDefault(true);
        regionDTO.setResourceGroup("resourceGroup");
        regionDTO.setStorageAccount("storageAcc");
        regionDTO.setStorageAccountKey("testKey");
        regionDTO.setAzurePolicy(new AzurePolicy());
        regionDTO.setSubscription("testSub");
        regionDTO.setAzureApiUrl("testUrl");
        return regionDTO;
    }

    public static GCPRegionDTO getDefaultGcpRegionDTO() {
        GCPRegionDTO regionDTO = new GCPRegionDTO();
        regionDTO.setCorsRules("corsRules");
        regionDTO.setApplicationName("testName");
        regionDTO.setImpersonatedAccount("testAccount");
        regionDTO.setProject("testProject");
        regionDTO.setCustomInstanceTypes(getGcpCustomInstanceTypes());
        regionDTO.setDefault(true);
        return regionDTO;
    }
}
