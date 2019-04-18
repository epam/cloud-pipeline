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
import com.epam.pipeline.manager.cloud.gcp.GCPResourcePrice;
import com.epam.pipeline.manager.cloud.gcp.GCPResourceType;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;

import java.util.Date;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = true)
public class GCPMachine extends AbstractGCPObject {
    private final int cpu;
    private final double ram;
    private final double extendedRam;
    private final int gpu;
    private final String gpuType;

    public GCPMachine(final String name,
                      final String family,
                      final int cpu,
                      final double ram,
                      final double extendedRam,
                      final int gpu,
                      final String gpuType) {
        super(name, family);
        this.cpu = cpu;
        this.ram = ram;
        this.extendedRam = extendedRam;
        this.gpu = gpu;
        this.gpuType = gpuType;
    }

    public static GCPMachine withCpu(final String name,
                                     final String family,
                                     final int cpu,
                                     final double ram,
                                     final double extendedRam) {
        return new GCPMachine(name, family, cpu, ram, extendedRam, 0, null);
    }

    public static GCPMachine withGpu(final String name,
                                     final String family,
                                     final int cpu,
                                     final double ram,
                                     final double extendedRam,
                                     final int gpu,
                                     final String gpuType) {
        return new GCPMachine(name, family, cpu, ram, extendedRam, gpu, gpuType);
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

    @Override
    public boolean isRequired(final GCPResourceType type) {
        switch (type) {
            case CPU:
                return cpu > 0;
            case RAM:
                return ram > 0;
            case EXTENDED_RAM:
                return extendedRam > 0;
            case GPU:
                return gpu > 0 && StringUtils.isNotBlank(gpuType);
            default:
                return false;
        }
    }

    @Override
    public long totalPrice(final List<GCPResourcePrice> prices) {
        return prices.stream()
                .mapToLong(price -> {
                    switch (price.getRequest().getType()) {
                        case CPU:
                            return cpu * price.getNanos();
                        case RAM:
                            return Math.round(ram * price.getNanos());
                        case EXTENDED_RAM:
                            return Math.round(extendedRam * price.getNanos());
                        case GPU:
                            return gpu * price.getNanos();
                        default:
                            return 0;
                    }
                })
                .sum();
    }

    @Override
    public String billingKey(final GCPBilling billing, final GCPResourceType type) {
        if (type == GCPResourceType.GPU) {
            return String.format(BILLING_KEY_PATTERN, type.alias(), billing.alias(), getGpuType().toLowerCase());
        }
        return String.format(BILLING_KEY_PATTERN, type.alias(), billing.alias(), getFamily());
    }

    @Override
    public String resourceFamily() {
        return "Compute";
    }
}
