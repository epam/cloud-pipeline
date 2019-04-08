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
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class GCPInstancePriceService implements CloudInstancePriceService<GCPRegion> {

    static final String PREEMPTIBLE_TERM_TYPE = "Preemptible";
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
                    .flatMap(machine -> instanceOffers(region, machine, prices).stream())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get instance types and prices from GCP.", e);
            return Collections.emptyList();
        }
    }

    private List<InstanceOffer> instanceOffers(final GCPRegion region,
                                               final GCPMachine machine,
                                               final Set<GCPResourcePrice> prices) {
        return Arrays.stream(GCPBilling.values())
                .map(billing -> getPrice(machine, billing, prices)
                        .map(price ->
                        InstanceOffer.builder()
                                .termType(termType(billing))
                                .tenancy(SHARED_TENANCY)
                                .productFamily(INSTANCE_PRODUCT_FAMILY)
                                .pricePerUnit(price)
                                .priceListPublishDate(new Date())
                                .currency(CURRENCY)
                                .instanceType(machine.getName())
                                .regionId(region.getId())
                                .unit(HOURS_UNIT)
                                .volumeType("SSD")
                                .operatingSystem("Linux")
                                .instanceFamily(readFamily(machine.getFamily()))
                                .vCPU(machine.getCpu())
                                .gpu(machine.getGpu())
                                .memory(machine.getRam())
                                .build()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private String termType(final GCPBilling billing) {
        return billing == GCPBilling.ON_DEMAND
                ? ON_DEMAND_TERM_TYPE
                : PREEMPTIBLE_TERM_TYPE;
    }

    private Optional<Double> getPrice(final GCPMachine machine,
                                      final GCPBilling billing,
                                      final Set<GCPResourcePrice> prices) {
        final List<Optional<GCPResourcePrice>> machinePrices = Arrays.stream(GCPResourceType.values())
                .filter(type -> type.isRequiredFor(machine))
                .map(type -> findPrice(prices, type, billing, type.familyFor(machine)))
                .collect(Collectors.toList());
        if (!machinePrices.stream().allMatch(Optional::isPresent)) {
            return Optional.empty();
        }
        final long nanos = machinePrices.stream()
                .map(Optional::get)
                .mapToLong(price -> price.in(machine))
                .sum();
        final double price = new BigDecimal((double) nanos / 1_000_000_000.0)
                .setScale(2, RoundingMode.HALF_EVEN)
                .doubleValue();
        return Optional.of(price);
    }

    private Optional<GCPResourcePrice> findPrice(final Set<GCPResourcePrice> prices,
                                                 final GCPResourceType type,
                                                 final GCPBilling billing,
                                                 final String family) {
        return prices.stream()
                .filter(price -> price.getBilling() == billing)
                .filter(price -> price.getType() == type)
                .filter(price -> price.getFamily().equals(family))
                .findFirst();
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
