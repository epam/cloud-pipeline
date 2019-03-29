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

package com.epam.pipeline.manager.cloud.gcp;

import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.MachineType;
import com.google.api.services.compute.model.MachineTypeList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class GCPInstancePriceService implements CloudInstancePriceService<GCPRegion> {

    private final GCPClient gcpClient;

    @Override
    public List<InstanceOffer> refreshPriceListForRegion(final GCPRegion region) {
        try {
            final Compute client = gcpClient.buildComputeClient(region);
            final String zone = region.getRegionCode();
            final MachineTypeList list = client.machineTypes()
                    .list(region.getProject(), zone)
                    .execute();
            return ListUtils.emptyIfNull(list.getItems())
                    .stream()
                    .map(machineType -> buildInstanceOffer(machineType, region.getId()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to get instance types and prices form GCP: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private InstanceOffer buildInstanceOffer(final MachineType machineType, final Long regionId) {
        double memory = new BigDecimal((double) machineType.getMemoryMb() / 1024)
                .setScale(2, RoundingMode.HALF_EVEN).doubleValue();
        return InstanceOffer.builder()
                .termType(ON_DEMAND_TERM_TYPE)
                .tenancy(SHARED_TENANCY)
                .productFamily(INSTANCE_PRODUCT_FAMILY)
                .sku(machineType.getName())
                .priceListPublishDate(new Date())
                .currency(CURRENCY)
                .instanceType(machineType.getName())
                .pricePerUnit(0.0)
                .regionId(regionId)
                .unit(HOURS_UNIT)
                .volumeType("SSD")
                .operatingSystem("Linux")
                .instanceFamily("Common")
                .vCPU(machineType.getGuestCpus())
                .gpu(0)
                .memory(memory)
                .build();
    }

    @Override
    public double getSpotPrice(final String instanceType, final GCPRegion region) {
        return 0;
    }

    @Override
    public double getPriceForDisk(final List<InstanceOffer> offers, final int instanceDisk,
                                  final String instanceType, final GCPRegion region) {
        return 0;
    }

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.GCP;
    }
}
