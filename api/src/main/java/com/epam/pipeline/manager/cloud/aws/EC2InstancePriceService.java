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

package com.epam.pipeline.manager.cloud.aws;

import com.epam.pipeline.controller.vo.InstanceOfferRequestVO;
import com.epam.pipeline.dao.cluster.InstanceOfferDao;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import com.epam.pipeline.manager.cloud.offer.InstanceOfferReader;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class EC2InstancePriceService implements CloudInstancePriceService<AwsRegion> {

    private static final String FALLBACK_AWS_EC2_PRICING_URL_TEMPLATE =
            "https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonEC2/current/%s/index.csv";
    private static final boolean FALLBACK_FETCH_GPU = true;

    @Getter
    private final CloudProvider provider = CloudProvider.AWS;
    private final InstanceOfferDao instanceOfferDao;
    private final EC2Helper ec2Helper;
    private final PreferenceManager preferenceManager;

    @Override
    public List<InstanceOffer> refreshPriceListForRegion(final AwsRegion region) {
        try (InputStream is = new URL(getPricingUrl(region)).openStream();
             BufferedReader br = new BufferedReader(new InputStreamReader(is));
             InstanceOfferReader reader = getReader(region, br)) {
            return reader.read();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private InstanceOfferReader getReader(final AwsRegion region, final BufferedReader br) {
        InstanceOfferReader reader = new AWSPriceListReader(br, region, getComputeFamilies());
        if (isFetchGpu()) {
            reader = new AWSInstanceOfferGpuReader(reader, region, ec2Helper, getGpuCoresMapping());
        }
        return reader;
    }

    private boolean isFetchGpu() {
        return Optional.of(SystemPreferences.CLUSTER_INSTANCE_OFFER_FETCH_GPU)
                .map(preferenceManager::getPreference)
                .orElse(FALLBACK_FETCH_GPU);
    }

    private String getPricingUrl(final AwsRegion region) {
        return String.format(getPricingUrlTemplate(), region.getRegionCode());
    }

    private String getPricingUrlTemplate() {
        return Optional.of(SystemPreferences.CLUSTER_AWS_EC2_PRICING_URL_TEMPLATE)
                .map(preferenceManager::getPreference)
                .orElse(FALLBACK_AWS_EC2_PRICING_URL_TEMPLATE);
    }

    private Set<String> getComputeFamilies() {
        return Optional.of(SystemPreferences.INSTANCE_COMPUTE_FAMILY_NAMES)
                .map(preferenceManager::getPreference)
                .orElseGet(Collections::emptySet);
    }

    private Map<String, Integer> getGpuCoresMapping() {
        return Optional.of(SystemPreferences.CLUSTER_INSTANCE_GPU_CORES_MAPPING)
                .map(preferenceManager::getPreference)
                .orElseGet(Collections::emptyMap);
    }

    @Override
    public double getSpotPrice(final String instanceType, final AwsRegion region) {
        return ec2Helper.getSpotPrice(instanceType, region);
    }

    @Override
    public double getPriceForDisk(final List<InstanceOffer> offers, final int instanceDisk,
                                 final String instanceType, final boolean spot, final AwsRegion region) {
        if (offers.size() == 1) {
            return offers.get(0).getPricePerUnit() / (DAYS_IN_MONTH * HOURS_IN_DAY) * instanceDisk;
        }
        return 0;
    }

    @Override
    public List<InstanceType> getAllInstanceTypes(final Long regionId, final boolean spot) {
        final InstanceOfferRequestVO requestVO = new InstanceOfferRequestVO();
        requestVO.setTermType(TermType.ON_DEMAND.getName());
        requestVO.setOperatingSystem(CloudInstancePriceService.LINUX_OPERATING_SYSTEM);
        requestVO.setTenancy(CloudInstancePriceService.SHARED_TENANCY);
        requestVO.setUnit(CloudInstancePriceService.HOURS_UNIT);
        requestVO.setProductFamily(CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY);
        requestVO.setRegionId(regionId);
        requestVO.setCloudProvider(CloudProvider.AWS.name());
        return instanceOfferDao.loadInstanceTypes(requestVO);
    }
}
