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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class GCPInstancePriceService implements CloudInstancePriceService<GCPRegion> {

    private static final String DELIMITER = "-";
    private static final String COMPUTE_ENGINE_SERVICE_NAME = "services/6F81-5844-456A";

    private final List<GCPMachineExtractor> extractors;
    private final GCPResourcePriceLoader priceLoader;

    @Override
    public List<InstanceOffer> refreshPriceListForRegion(final GCPRegion region) {
        try {
            final List<GCPMachine> machines = extractors.stream()
                    .flatMap(it -> it.extract(region).stream())
                    .collect(Collectors.toList());
            final Set<GCPResourcePrice> prices = priceLoader.load(region, machines);
            return machines.stream()
                    .map(machine -> {
                        final InstanceOffer instanceOffer = buildInstanceOffer(region, machine);
                        final double price = getPrice(machine, prices);
                        instanceOffer.setPricePerUnit(price);
                        return instanceOffer;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get instance types and prices from GCP: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private double getPrice(final GCPMachine machine, final Set<GCPResourcePrice> prices) {
        final long nanos = prices.stream()
                .filter(price -> price.getFamily().equals(machine.getFamily()))
                .mapToLong(price -> price.in(machine))
                .sum();
        return new BigDecimal((double) nanos / 1_000_000_000.0)
                .setScale(2, RoundingMode.HALF_EVEN)
                .doubleValue();
    }

    private InstanceOffer buildInstanceOffer(final GCPRegion region, final GCPMachine machine) {
        return InstanceOffer.builder()
                .termType(ON_DEMAND_TERM_TYPE)
                .tenancy(SHARED_TENANCY)
                .productFamily(INSTANCE_PRODUCT_FAMILY)
                .sku(machine.getSku())
                .priceListPublishDate(new Date())
                .currency(CURRENCY)
                .instanceType(machine.getFamily() + "-" + machine.getCpu())
                .regionId(region.getId())
                .unit(HOURS_UNIT)
                .volumeType("SSD")
                .operatingSystem("Linux")
                .instanceFamily(readFamily(machine.getFamily()))
                .vCPU(machine.getCpu())
                .gpu(machine.getGpu())
                .memory(machine.getRam())
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

    private String readFamily(final String name) {
        // expected format n1-standard-1
        if (!name.contains(DELIMITER)) {
            return "General purpose";
        }
        final String[] chunks = name.split(DELIMITER);
        return WordUtils.capitalizeFully(chunks[1]);
    }
}
