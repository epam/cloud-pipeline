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

package com.epam.pipeline.manager.cloud.local;

import com.epam.pipeline.controller.vo.InstanceOfferRequestVO;
import com.epam.pipeline.dao.cluster.InstanceOfferDao;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.CustomInstanceType;
import com.epam.pipeline.entity.region.LocalRegion;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LocalInstancePriceService implements CloudInstancePriceService<LocalRegion> {

    private final InstanceOfferDao instanceOfferDao;

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.LOCAL;
    }

    @Override
    public List<InstanceOffer> refreshPriceListForRegion(final LocalRegion region) {
        return ListUtils.emptyIfNull(region.getCustomInstanceTypes())
                .stream()
                .map(instance -> toOffer(instance, region))
                .collect(Collectors.toList());
    }

    private InstanceOffer toOffer(final CustomInstanceType instance,
                                  final LocalRegion region) {
        final String type = toInstanceType(instance);
        return InstanceOffer.builder()
                .pricePerUnit(0.0)
                .cloudProvider(CloudProvider.LOCAL)
                .regionId(region.getId())
                .gpu(instance.getGpu())
                .memory(instance.getRam())
                .memoryUnit("GiB")
                .vCPU(instance.getCpu())
                .termType(CloudInstancePriceService.TermType.ON_DEMAND.getName())
                .operatingSystem(CloudInstancePriceService.LINUX_OPERATING_SYSTEM)
                .tenancy(CloudInstancePriceService.SHARED_TENANCY)
                .unit(CloudInstancePriceService.HOURS_UNIT)
                .productFamily(CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY)
                .instanceType(type)
                .sku(type)
                .priceListPublishDate(new Date())
                .build();
    }


    private String toInstanceType(final CustomInstanceType instance) {
        if (instance.getGpu() > 0) {
            return String.format(String.format("gpu-%d-%d-%d-%s",
                    instance.getCpu(),
                    (int)instance.getRam(),
                    instance.getGpu(),
                    instance.getGpuType().toLowerCase()));
        }
        return String.format(String.format("cpu-%d-%d", instance.getCpu(), (int)instance.getRam()));
    }

    @Override
    public double getSpotPrice(final String instanceType,
                               final LocalRegion region) {
        return 0;
    }

    @Override
    public double getPriceForDisk(final List<InstanceOffer> offers,
                                  final int instanceDisk,
                                  final String instanceType,
                                  final boolean spot,
                                  final LocalRegion region) {
        return 0;
    }
    @Override
    public List<InstanceType> getAllInstanceTypes(final Long regionId,
                                                  final boolean spot) {
        if (spot) {
            return Collections.emptyList();
        }
        final InstanceOfferRequestVO requestVO = new InstanceOfferRequestVO();
        requestVO.setTermType(TermType.ON_DEMAND.getName());
        requestVO.setOperatingSystem(CloudInstancePriceService.LINUX_OPERATING_SYSTEM);
        requestVO.setTenancy(CloudInstancePriceService.SHARED_TENANCY);
        requestVO.setUnit(CloudInstancePriceService.HOURS_UNIT);
        requestVO.setProductFamily(CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY);
        requestVO.setRegionId(regionId);
        requestVO.setCloudProvider(CloudProvider.LOCAL.name());
        return instanceOfferDao.loadInstanceTypes(requestVO);
    }
}
