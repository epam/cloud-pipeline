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

package com.epam.pipeline.test.creator.region;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.region.AWSRegionDTO;
import com.epam.pipeline.controller.vo.region.AzureRegionDTO;
import com.epam.pipeline.controller.vo.region.GCPRegionDTO;
import com.epam.pipeline.entity.info.CloudRegionInfo;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.AzurePolicy;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.GCPCustomInstanceType;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;

public final class RegionCreatorUtils {

    public static final TypeReference<Result<AwsRegion>> AWS_REGION_TYPE = new TypeReference<Result<AwsRegion>>() { };
    public static final TypeReference<Result<AzureRegion>> AZURE_REGION_TYPE =
            new TypeReference<Result<AzureRegion>>() { };
    public static final TypeReference<Result<GCPRegion>> GCP_REGION_TYPE = new TypeReference<Result<GCPRegion>>() { };
    public static final TypeReference<Result<List<CloudProvider>>> CLOUD_PROVIDER_LIST_TYPE =
            new TypeReference<Result<List<CloudProvider>>>() { };
    public static final TypeReference<Result<List<AwsRegion>>> AWS_REGION_LIST_TYPE =
            new TypeReference<Result<List<AwsRegion>>>() { };
    public static final TypeReference<Result<List<CloudRegionInfo>>> CLOUD_REGION_INFO_LIST_TYPE =
            new TypeReference<Result<List<CloudRegionInfo>>>() { };
    public static final TypeReference<Result<List<String>>> STRING_LIST_TYPE =
            new TypeReference<Result<List<String>>>() { };
    private static final boolean TRUE = true;
    private static final int NUMBER = 4;

    private RegionCreatorUtils() {

    }

    public static AwsRegion getDefaultAwsRegion() {
        final AwsRegion region = new AwsRegion();
        region.setRegionCode(TEST_STRING);
        region.setName(TEST_STRING);
        region.setDefault(TRUE);
        return region;
    }

    public static AwsRegion getDefaultAwsRegion(final Long id) {
        final AwsRegion region = ObjectCreatorUtils.getDefaultAwsRegion();
        region.setId(id);
        return region;
    }

    public static AwsRegion getNonDefaultAwsRegion(final Long id) {
        final AwsRegion region = getDefaultAwsRegion(id);
        region.setDefault(false);
        return region;
    }

    public static AzureRegion getDefaultAzureRegion() {
        final AzureRegion region = new AzureRegion();
        region.setDefault(TRUE);
        region.setProvider(CloudProvider.AZURE);
        region.setResourceGroup(TEST_STRING);
        region.setStorageAccount(TEST_STRING);
        return region;
    }

    public static GCPRegion getDefaultGcpRegion() {
        final GCPRegion region = new GCPRegion();
        region.setCustomInstanceTypes(getGcpCustomInstanceTypes());
        region.setDefault(TRUE);
        return region;
    }

    private static List<GCPCustomInstanceType> getGcpCustomInstanceTypes() {
        final List<GCPCustomInstanceType> customInstanceTypes = new ArrayList<>();
        customInstanceTypes.add(GCPCustomInstanceType.withCpu(NUMBER, NUMBER));
        customInstanceTypes.add(GCPCustomInstanceType.withGpu(NUMBER, NUMBER, NUMBER, TEST_STRING));
        return customInstanceTypes;
    }

    public static AWSRegionDTO getDefaultAwsRegionDTO() {
        final AWSRegionDTO awsRegionDTO = new AWSRegionDTO();
        awsRegionDTO.setRegionCode(TEST_STRING);
        awsRegionDTO.setName(TEST_STRING);
        awsRegionDTO.setKmsKeyId(TEST_STRING);
        awsRegionDTO.setKmsKeyArn(TEST_STRING);
        awsRegionDTO.setProfile(TEST_STRING);
        awsRegionDTO.setSshKeyName(TEST_STRING);
        awsRegionDTO.setTempCredentialsRole(TEST_STRING);
        awsRegionDTO.setDefault(TRUE);
        return awsRegionDTO;
    }

    public static AzureRegionDTO getDefaultAzureRegionDTO() {
        final AzureRegionDTO regionDTO = new AzureRegionDTO();
        regionDTO.setDefault(TRUE);
        regionDTO.setResourceGroup(TEST_STRING);
        regionDTO.setStorageAccount(TEST_STRING);
        regionDTO.setStorageAccountKey(TEST_STRING);
        regionDTO.setAzurePolicy(new AzurePolicy());
        regionDTO.setSubscription(TEST_STRING);
        regionDTO.setAzureApiUrl(TEST_STRING);
        return regionDTO;
    }

    public static GCPRegionDTO getDefaultGcpRegionDTO() {
        final GCPRegionDTO regionDTO = new GCPRegionDTO();
        regionDTO.setCorsRules(TEST_STRING);
        regionDTO.setApplicationName(TEST_STRING);
        regionDTO.setImpersonatedAccount(TEST_STRING);
        regionDTO.setProject(TEST_STRING);
        regionDTO.setCustomInstanceTypes(getGcpCustomInstanceTypes());
        regionDTO.setDefault(TRUE);
        return regionDTO;
    }
}
