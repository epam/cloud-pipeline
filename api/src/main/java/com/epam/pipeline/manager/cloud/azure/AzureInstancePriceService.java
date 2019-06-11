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

package com.epam.pipeline.manager.cloud.azure;

import com.epam.pipeline.controller.vo.InstanceOfferRequestVO;
import com.epam.pipeline.dao.cluster.InstanceOfferDao;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AzureInstancePriceService implements CloudInstancePriceService<AzureRegion> {

    private final InstanceOfferDao instanceOfferDao;

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.AZURE;
    }

    @Override
    public List<InstanceOffer> refreshPriceListForRegion(final AzureRegion region) {
        try {
            final String authPath = region.getAuthFile();
            Assert.isTrue(StringUtils.isNotBlank(authPath), "Azure auth file path must be specified");

            final String offerId = region.getPriceOfferId();
            Assert.notNull(offerId, "Cannot find offer durable ID");

            return new AzurePriceListLoader(authPath, offerId, region.getMeterRegionName(), region.getAzureApiUrl())
                    .load(region);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public double getSpotPrice(final String instanceType, final AzureRegion region) {
        final InstanceOfferRequestVO requestVO = new InstanceOfferRequestVO();
        requestVO.setInstanceType(instanceType);
        requestVO.setTermType(TermType.LOW_PRIORITY.getName());
        requestVO.setOperatingSystem(CloudInstancePriceService.LINUX_OPERATING_SYSTEM);
        requestVO.setTenancy(CloudInstancePriceService.SHARED_TENANCY);
        requestVO.setUnit(CloudInstancePriceService.HOURS_UNIT);
        requestVO.setProductFamily(CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY);
        requestVO.setRegionId(region.getId());
        final List<InstanceOffer> offers = ListUtils.emptyIfNull(instanceOfferDao.loadInstanceOffers(requestVO));
        return offers.stream()
                .findFirst()
                .map(InstanceOffer::getPricePerUnit)
                .orElse(0.0);
    }

    @Override
    public double getPriceForDisk(final List<InstanceOffer> diskOffers, final int instanceDisk,
                                 final String instanceType, final AzureRegion region) {
        final InstanceOfferRequestVO requestVO = new InstanceOfferRequestVO();
        requestVO.setTermType(TermType.ON_DEMAND.getName());
        requestVO.setOperatingSystem(LINUX_OPERATING_SYSTEM);
        requestVO.setTenancy(SHARED_TENANCY);
        requestVO.setUnit(HOURS_UNIT);
        requestVO.setProductFamily(INSTANCE_PRODUCT_FAMILY);
        requestVO.setRegionId(region.getId());
        requestVO.setInstanceType(instanceType);
        requestVO.setCloudProvider(CloudProvider.AZURE.name());
        final List<InstanceOffer> vmOffers = instanceOfferDao.loadInstanceOffers(requestVO);
        if (vmOffers.size() != 1) {
            log.debug("Virtual machines count should be exactly 1, but found '{}' for instance type '{}'",
                    vmOffers.size(), instanceType);
            return 0;
        }

        final InstanceOffer suitableOffer = getSuitableOffer(diskOffers, instanceDisk, vmOffers.get(0));
        if (Objects.isNull(suitableOffer) || Double.compare(suitableOffer.getMemory(), 0.0) == 0) {
            log.debug("Disk size was not found for instance type '{}'", instanceType);
            return 0;
        }

        return suitableOffer.getPricePerUnit() / (DAYS_IN_MONTH * HOURS_IN_DAY);
    }

    @Override
    public List<InstanceType> getAllInstanceTypes(final Long regionId, final boolean spot) {
        final InstanceOfferRequestVO requestVO = new InstanceOfferRequestVO();
        requestVO.setTermType(spot ? TermType.LOW_PRIORITY.getName() : TermType.ON_DEMAND.getName());
        requestVO.setOperatingSystem(CloudInstancePriceService.LINUX_OPERATING_SYSTEM);
        requestVO.setTenancy(CloudInstancePriceService.SHARED_TENANCY);
        requestVO.setUnit(CloudInstancePriceService.HOURS_UNIT);
        requestVO.setProductFamily(CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY);
        requestVO.setRegionId(regionId);
        requestVO.setCloudProvider(CloudProvider.AZURE.name());
        return instanceOfferDao.loadInstanceTypes(requestVO);
    }

    private InstanceOffer getSuitableOffer(final List<InstanceOffer> diskOffers, final float instanceDisk,
                                           final InstanceOffer vmOffer) {
        final TreeSet<InstanceOffer> disks = new TreeSet<>(Comparator.comparingDouble(InstanceOffer::getMemory));
        disks.addAll(diskOffers.stream()
                .filter(offer -> checkDiskType(offer.getSku(), vmOffer.getVolumeType()))
                .collect(Collectors.toList()));
        return disks.ceiling(InstanceOffer.builder().memory(instanceDisk).build());
    }

    private boolean checkDiskType(final String diskSize, final String instanceDiskType) {
        final boolean isPremium = instanceDiskType.equalsIgnoreCase("Premium");
        return isPremium && diskSize.startsWith("P") || !isPremium && diskSize.startsWith("E");
    }
}
