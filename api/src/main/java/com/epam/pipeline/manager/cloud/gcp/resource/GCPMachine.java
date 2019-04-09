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

package com.epam.pipeline.manager.cloud.gcp.resource;

import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import com.epam.pipeline.manager.cloud.gcp.GCPBilling;
import lombok.Getter;
import org.apache.commons.lang3.text.WordUtils;

import java.util.Date;

@Getter
public class GCPMachine extends GCPObject {
    private final int cpu;
    private final double ram;
    private final int gpu;
    private final String gpuType;

    public GCPMachine(final String name, final String family, final int cpu, final double ram, final int gpu,
                      final String gpuType) {
        super(name, family);
        this.cpu = cpu;
        this.ram = ram;
        this.gpu = gpu;
        this.gpuType = gpuType;
    }

    @Override
    public InstanceOffer toInstanceOffer(final GCPBilling billing, final double pricePerUnit, final Long regionId) {
        return InstanceOffer.builder()
                .termType(billing.termType())
                .tenancy(CloudInstancePriceService.SHARED_TENANCY)
                .productFamily(CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY)
                .sku(getName())
                .pricePerUnit(pricePerUnit)
                .priceListPublishDate(new Date())
                .currency(CloudInstancePriceService.CURRENCY)
                .instanceType(getName())
                .regionId(regionId)
                .unit(CloudInstancePriceService.HOURS_UNIT)
                .volumeType("SSD")
                .operatingSystem("Linux")
                .instanceFamily(WordUtils.capitalizeFully(getFamily()))
                .vCPU(getCpu())
                .gpu(getGpu())
                .memory(getRam())
                .build();
    }
}
