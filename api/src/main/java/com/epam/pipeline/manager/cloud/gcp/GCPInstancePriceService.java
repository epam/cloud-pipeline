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
import com.epam.pipeline.manager.cloud.gcp.extractor.GCPMachineExtractor;
import com.epam.pipeline.manager.cloud.gcp.resource.GCPDisk;
import com.epam.pipeline.manager.cloud.gcp.resource.GCPObject;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class GCPInstancePriceService implements CloudInstancePriceService<GCPRegion> {

    private static final long BILLION = 1_000_000_000L;
    private static final int PRICES_PRECISION = 5;
    static final String PREEMPTIBLE_TERM_TYPE = "Preemptible";

    private final PreferenceManager preferenceManager;
    private final List<GCPMachineExtractor> extractors;
    private final GCPResourcePriceLoader priceLoader;

    @Override
    public List<InstanceOffer> refreshPriceListForRegion(final GCPRegion region) {
        try {
            final List<GCPObject> objects = availableObjects(region);
            final Map<String, String> prefixes = loadBillingPrefixes();
            final List<GCPResourceRequest> requests = requests(objects, prefixes);
            final Set<GCPResourcePrice> prices = priceLoader.load(region, requests);
            return objects.stream()
                    .flatMap(object -> Arrays.stream(GCPBilling.values())
                            .map(billing -> object.toInstanceOffer(billing,
                                    getPrice(object, billing, prices), region.getId())))
                    .collect(Collectors.toList());
        } catch (GCPInstancePriceException e) {
            log.error("Failed to get instance types and prices from GCP.", e);
            return Collections.emptyList();
        }
    }

    private List<GCPObject> availableObjects(final GCPRegion region) {
        final List<GCPObject> machines = extractors.stream()
                .flatMap(it -> it.extract(region).stream())
                .collect(Collectors.toList());
        final List<GCPObject> objects = new ArrayList<>();
        objects.addAll(machines);
        objects.add(new GCPDisk("SSD", "SSD"));
        return objects;
    }

    private Map<String, String> loadBillingPrefixes() {
        return MapUtils.emptyIfNull(preferenceManager.getPreference(SystemPreferences.GCP_BILLING_PREFIXES));
    }

    private List<GCPResourceRequest> requests(final List<GCPObject> objects, final Map<String, String> prefixes) {
        return objects.stream()
                .flatMap(machine -> Arrays.stream(GCPResourceType.values())
                        .filter(type -> type.isRequired(machine))
                        .flatMap(type -> requests(machine, type, prefixes)))
                .distinct()
                .collect(Collectors.toList());
    }

    private Stream<GCPResourceRequest> requests(final GCPObject object,
                                                final GCPResourceType type,
                                                final Map<String, String> prefixes) {
        return Arrays.stream(GCPBilling.values())
                .map(billing -> Optional.of(type.billingKey(billing, object))
                        .map(prefixes::get)
                        .map(prefix -> new GCPResourceRequest(type, billing, prefix, object)))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private Double getPrice(final GCPObject object,
                            final GCPBilling billing,
                            final Set<GCPResourcePrice> prices) {
        final Map<GCPResourceType, Optional<GCPResourcePrice>> requiredPrices = Arrays.stream(GCPResourceType.values())
                .filter(type -> type.isRequired(object))
                .collect(Collectors.toMap(Function.identity(),
                    type -> findPrice(prices, type, billing, type.family(object))));
        final Optional<GCPResourceType> typeWithMissingPrice = requiredPrices.entrySet()
                .stream()
                .filter(entry -> !entry.getValue().isPresent())
                .map(Map.Entry::getKey)
                .findFirst();
        if (typeWithMissingPrice.isPresent()) {
            log.error(String.format("Price for %s for GCP object %s wasn't found.", typeWithMissingPrice.get(),
                    object.getName()));
            return 0.0;
        }
        final long nanos = requiredPrices.values()
                .stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .mapToLong(price -> price.getRequest().getType().price(object, price))
                .sum();
        return new BigDecimal(((double) nanos) / BILLION)
                .setScale(PRICES_PRECISION, RoundingMode.HALF_EVEN)
                .doubleValue();
    }

    private Optional<GCPResourcePrice> findPrice(final Set<GCPResourcePrice> prices,
                                                 final GCPResourceType type,
                                                 final GCPBilling billing,
                                                 final String family) {
        return prices.stream()
                .filter(price -> price.getRequest().getType() == type)
                .filter(price -> price.getRequest().getBilling() == billing)
                .filter(price -> type.family(price.getRequest().getObject()).equals(family))
                .findFirst();
    }

    @Override
    public double getSpotPrice(final String instanceType, final GCPRegion region) {
        return 0;
    }

    @Override
    public double getPriceForDisk(final List<InstanceOffer> offers,
                                  final int instanceDisk,
                                  final String instanceType,
                                  final GCPRegion region) {
        return offers.stream()
                .filter(offer -> offer.getInstanceType().equals(instanceType))
                .map(offer -> offer.getPricePerUnit() * instanceDisk)
                .findFirst()
                .orElseThrow(() -> new GCPInstancePriceException(String.format("Requested storage instance type %s " +
                        "wasn't found in region %s offers.", instanceType, region.getId())));
    }

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.GCP;
    }

}
