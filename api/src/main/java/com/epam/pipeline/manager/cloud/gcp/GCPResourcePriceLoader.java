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
import com.google.api.services.cloudbilling.Cloudbilling;
import com.google.api.services.cloudbilling.model.ListSkusResponse;
import com.google.api.services.cloudbilling.model.PricingExpression;
import com.google.api.services.cloudbilling.model.PricingInfo;
import com.google.api.services.cloudbilling.model.Sku;
import com.google.api.services.cloudbilling.model.TierRate;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Google Cloud Provider resource price loader.
 */
@Component
@RequiredArgsConstructor
public class GCPResourcePriceLoader {

    private static final String COMPUTE_ENGINE_SERVICE_NAME = "services/6F81-5844-456A";

    private final GCPClient gcpClient;

    /**
     * Loads prices for all the given Google Cloud Provider machines in the specified region.
     */
    @SneakyThrows
    public Set<GCPResourcePrice> load(final GCPRegion region, final List<GCPMachine> machines) {
        final Cloudbilling cloudbilling = gcpClient.buildBillingClient(region);
        final Map<String, String> prefixes = loadPrefixes();
        final List<Sku> skus = getAllSkus(cloudbilling);
        final List<GCPResourceRequest> requests = machines.stream()
                .map(machine -> {
                    final List<GCPResourceRequest> machineRequests = new ArrayList<>();
                    if (machine.getCpu() > 0) {
                        final String billingKey = GCPResourceType.CPU.alias() + "_ondemand_" + machine.getFamily();
                        final String billingPrefix = prefixes.get(billingKey);
                        if (billingPrefix != null) {
                            machineRequests.add(new GCPResourceRequest(machine.getFamily(), GCPResourceType.CPU, billingPrefix));
                        }
                    }
                    if (machine.getRam() > 0) {
                        final String billingKey = GCPResourceType.RAM.alias() + "_ondemand_" + machine.getFamily();
                        final String billingPrefix = prefixes.get(billingKey);
                        if (billingPrefix != null) {
                            machineRequests.add(new GCPResourceRequest(machine.getFamily(), GCPResourceType.RAM, billingPrefix));
                        }
                    }
                    // TODO 02.04.19: Add GPU requests.
                    return machineRequests;
                })
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        final List<Sku> requestedSkus = skus.stream()
                .filter(sku -> requests.stream()
                        .anyMatch(request -> sku.getDescription() != null
                                && sku.getDescription().startsWith(request.getPrefix())))
                .collect(Collectors.toList());
        final String regionName = region.getRegionCode().replaceFirst("-\\w$", "");
        return requests.stream()
                .flatMap(request -> requestedSkus.stream()
                        .filter(sku -> sku.getDescription().startsWith(request.getPrefix()))
                        .filter(sku -> CollectionUtils.emptyIfNull(sku.getServiceRegions()).contains(regionName))
                        .map(sku -> Optional.ofNullable(sku.getPricingInfo())
                                .filter(CollectionUtils::isNotEmpty)
                                .map(this::lastElement)
                                .map(PricingInfo::getPricingExpression)
                                .map(PricingExpression::getTieredRates)
                                .filter(CollectionUtils::isNotEmpty)
                                .map(this::lastElement)
                                .map(TierRate::getUnitPrice)
                                .filter(money -> money.getUnits() != null && money.getNanos() != null)
                                .map(money -> money.getUnits() * 1_000_000_000 + money.getNanos())
                                .map(it -> new GCPResourcePrice(request.getFamily(), request.getType(), it)))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                )
                .collect(Collectors.toSet());
    }

    private List<Sku> getAllSkus(final Cloudbilling cloudbilling) throws IOException {
        final List<Sku> allSkus = new ArrayList<>();
        String nextPageToken = null;
        while (true) {
            final ListSkusResponse response = cloudbilling.services().skus()
                    .list(COMPUTE_ENGINE_SERVICE_NAME)
                    .setPageToken(nextPageToken)
                    .execute();
            final List<Sku> currentSkus = Optional.of(response)
                    .map(ListSkusResponse::getSkus)
                    .orElseGet(Collections::emptyList);
            allSkus.addAll(currentSkus);
            nextPageToken = response.getNextPageToken();
            if (StringUtils.isBlank(nextPageToken)) {
                return allSkus;
            }
        }
    }

    private <T> T lastElement(final List<T> list) {
        return list.get(list.size() - 1);
    }

    private Map<String, String> loadPrefixes() {
        // TODO 02.04.19: Replace with the map from system preferences.
        final Map<String, String> prefixes = new HashMap<>();
        prefixes.put("cpu_ondemand_standard", "N1 Predefined Instance Core");
        prefixes.put("ram_ondemand_standard", "N1 Predefined Instance Ram");
        prefixes.put("cpu_ondemand_highcpu", "N1 Predefined Instance Core");
        prefixes.put("ram_ondemand_highcpu", "N1 Predefined Instance Ram");
        prefixes.put("cpu_ondemand_highmem", "N1 Predefined Instance Core");
        prefixes.put("ram_ondemand_highmem", "N1 Predefined Instance Ram");
        prefixes.put("cpu_ondemand_megamem", "Memory-optimized Instance Core");
        prefixes.put("ram_ondemand_megamem", "Memory-optimized Instance Ram");
        prefixes.put("cpu_ondemand_ultramem", "Memory-optimized Instance Core");
        prefixes.put("ram_ondemand_ultramem", "Memory-optimized Instance Ram");
        prefixes.put("cpu_ondemand_micro", "Micro Instance");
        prefixes.put("cpu_ondemand_small", "Small Instance");
        prefixes.put("cpu_ondemand_custom", "Custom Instance Core");
        prefixes.put("ram_ondemand_custom", "Custom Instance Ram");
        prefixes.put("gpu_ondemand_t4", "Nvidia Tesla T4");
        prefixes.put("gpu_ondemand_p4", "Nvidia Tesla P4");
        prefixes.put("gpu_ondemand_v100", "Nvidia Tesla V100");
        prefixes.put("gpu_ondemand_p100", "Nvidia Tesla P100");
        prefixes.put("gpu_ondemand_k80", "Nvidia Tesla K80");
        return prefixes;
    }
}
