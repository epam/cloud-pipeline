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

package com.epam.pipeline.manager.cloud.gcp;

import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.google.api.services.cloudbilling.Cloudbilling;
import com.google.api.services.cloudbilling.model.ListSkusResponse;
import com.google.api.services.cloudbilling.model.PricingExpression;
import com.google.api.services.cloudbilling.model.PricingInfo;
import com.google.api.services.cloudbilling.model.Sku;
import com.google.api.services.cloudbilling.model.TierRate;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Google Cloud Provider resource price loader.
 */
@Component
@RequiredArgsConstructor
public class GCPResourcePriceLoader {

    private static final String COMPUTE_ENGINE_SERVICE_NAME = "services/6F81-5844-456A";
    private static final int SKUS_PAGE_SIZE = 2000;
    private static final long BILLION = 1_000_000_000L;

    private final PreferenceManager preferenceManager;
    private final GCPClient gcpClient;

    /**
     * Loads prices for all the given Google Cloud Provider machines in the specified region.
     */
    public Set<GCPResourcePrice> load(final GCPRegion region, final List<GCPMachine> machines) {
        try {
            final Cloudbilling cloudbilling = gcpClient.buildBillingClient(region);
            final Map<String, String> prefixes = loadBillingPrefixes();
            final List<GCPResourceRequest> requests = requests(machines, prefixes);
            final String regionName = regionName(region);
            return loadPrices(requests, cloudbilling, regionName);
        } catch (IOException e) {
            throw new GCPInstancePriceException("GCP machine prices loading has failed.", e);
        }
    }

    private Map<String, String> loadBillingPrefixes() {
        return MapUtils.emptyIfNull(preferenceManager.getPreference(SystemPreferences.GCP_BILLING_PREFIXES));
    }

    private List<GCPResourceRequest> requests(final List<GCPMachine> machines, final Map<String, String> prefixes) {
        return machines.stream()
                .flatMap(machine -> Arrays.stream(GCPResourceType.values())
                        .filter(type -> type.isRequired(machine))
                        .flatMap(type -> requests(machine, type, prefixes)))
                .distinct()
                .collect(Collectors.toList());
    }

    private Stream<GCPResourceRequest> requests(final GCPMachine machine,
                                                final GCPResourceType type,
                                                final Map<String, String> prefixes) {
        return Arrays.stream(GCPBilling.values())
                .map(billing -> Optional.of(type.billingKey(billing, machine))
                        .map(prefixes::get)
                        .map(prefix -> new GCPResourceRequest(type.family(machine), type, billing, prefix)))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private String regionName(final GCPRegion region) {
        return region.getRegionCode().replaceFirst("-\\w$", "");
    }

    private Set<GCPResourcePrice> loadPrices(final List<GCPResourceRequest> requests,
                                             final Cloudbilling cloudbilling,
                                             final String regionName) throws IOException {
        String nextPageToken = null;
        final Set<GCPResourcePrice> prices = new HashSet<>();
        while (true) {
            final ListSkusResponse response = cloudbilling.services().skus()
                    .list(COMPUTE_ENGINE_SERVICE_NAME)
                    .setPageToken(nextPageToken)
                    .setPageSize(SKUS_PAGE_SIZE)
                    .execute();
            final List<Sku> currentSkus = Optional.of(response)
                    .map(ListSkusResponse::getSkus)
                    .orElseGet(Collections::emptyList);
            final List<GCPResourcePrice> currentPrices = currentSkus.stream()
                    .filter(sku -> sku.getDescription() != null)
                    .flatMap(sku -> requests.stream()
                            .filter(request -> sku.getDescription().startsWith(request.getPrefix()))
                            .filter(request -> CollectionUtils.emptyIfNull(sku.getServiceRegions())
                                    .contains(regionName))
                            .map(request -> price(request, sku)))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
            prices.addAll(currentPrices);
            nextPageToken = response.getNextPageToken();
            if (StringUtils.isBlank(nextPageToken)) {
                break;
            }
        }
        return prices;
    }

    private Optional<GCPResourcePrice> price(final GCPResourceRequest request, final Sku sku) {
        return Optional.ofNullable(sku.getPricingInfo())
                .filter(CollectionUtils::isNotEmpty)
                .map(this::lastElement)
                .map(PricingInfo::getPricingExpression)
                .map(PricingExpression::getTieredRates)
                .filter(CollectionUtils::isNotEmpty)
                .map(this::firstElement)
                .map(TierRate::getUnitPrice)
                .filter(money -> money.getUnits() != null && money.getNanos() != null)
                .map(money -> money.getUnits() * BILLION + money.getNanos())
                .map(nanos -> price(request, nanos));
    }

    private <T> T lastElement(final List<T> list) {
        return list.get(list.size() - 1);
    }

    private <T> T firstElement(final List<T> list) {
        return list.get(0);
    }

    private GCPResourcePrice price(final GCPResourceRequest request, final Long nanos) {
        return new GCPResourcePrice(request.getFamily(), request.getType(), request.getBilling(), nanos);
    }
}
