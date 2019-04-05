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
import com.google.api.services.cloudbilling.Cloudbilling;
import com.google.api.services.cloudbilling.model.ListSkusResponse;
import com.google.api.services.cloudbilling.model.PricingExpression;
import com.google.api.services.cloudbilling.model.PricingInfo;
import com.google.api.services.cloudbilling.model.Sku;
import com.google.api.services.cloudbilling.model.TierRate;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.MachineType;
import com.google.api.services.compute.model.MachineTypeList;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class GCPInstancePriceService implements CloudInstancePriceService<GCPRegion> {

    private static final String DELIMITER = "-";
    private static final String COMPUTE_ENGINE_SERVICE_NAME = "services/6F81-5844-456A";

    private final GCPClient gcpClient;

    /**
     * Retrieves all available GCP machines types which names follows one of the following patterns:
     *
     * {prefix}-{instance_family}-{number_of_cpu}
     * {prefix}-{instance_family}
     */
    @Override
    public List<InstanceOffer> refreshPriceListForRegion(final GCPRegion region) {
        try {
            final Compute client = gcpClient.buildComputeClient(region);
            final String zone = region.getRegionCode();
            final MachineTypeList response = client.machineTypes().list(region.getProject(), zone).execute();
            final List<MachineType> machineTypes = Optional.of(response)
                    .map(MachineTypeList::getItems)
                    .orElseGet(Collections::emptyList);
            final List<Machine> predefinedMachines = machineTypes.stream()
                    .filter(machine -> machine.getName() != null)
                    .map(Machine::from)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
            final Set<ResourcePrice> prices = getPrices(region, predefinedMachines);
            return predefinedMachines.stream()
                    .map(machine -> {
                        final InstanceOffer instanceOffer = InstanceOffer.builder()
                                .instanceType(machine.family + "-" + machine.cpu)
                                .instanceFamily(machine.family)
                                .memory(machine.getRam())
                                .vCPU(machine.getCpu())
                                .build();
                        final long priceNanos = prices.stream()
                                .filter(price -> price.getFamily().equals(machine.getFamily()))
                                .mapToLong(price -> price.in(machine))
                                .sum();
                        final double price = new BigDecimal((double) priceNanos / 1_000_000_000.0)
                                .setScale(2, RoundingMode.HALF_EVEN).doubleValue();
                        instanceOffer.setPricePerUnit(price);
                        return instanceOffer;
                    })
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
                .instanceFamily(readFamily(machineType.getName()))
                .vCPU(machineType.getGuestCpus())
                .gpu(0)
                .memory(memory)
                .build();
    }

    private Set<ResourcePrice> getPrices(final GCPRegion region, final List<Machine> predefinedMachines) throws IOException {
        final Cloudbilling cloudbilling = gcpClient.buildBillingClient(region);
        final Map<String, String> prefixes = loadPrefixes();
        final List<Sku> skus = getAllSkus(cloudbilling);
        System.out.println(String.format("Number of skus: %s", skus.size()));
        final List<ResourceRequest> requests = predefinedMachines.stream()
                .map(machine -> {
                    final List<ResourceRequest> machineRequests = new ArrayList<>();
                    if (machine.getCpu() > 0) {
                        final String billingKey = ResourceType.CPU.alias() + "_ondemand_" + machine.getFamily();
                        final String billingPrefix = prefixes.get(billingKey);
                        if (billingPrefix != null) {
                            machineRequests.add(new ResourceRequest(machine.getFamily(), ResourceType.CPU, billingPrefix));
                        }
                    }
                    if (machine.getRam() > 0) {
                        final String billingKey = ResourceType.RAM.alias() + "_ondemand_" + machine.getFamily();
                        final String billingPrefix = prefixes.get(billingKey);
                        if (billingPrefix != null) {
                            machineRequests.add(new ResourceRequest(machine.getFamily(), ResourceType.RAM, billingPrefix));
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
                                .map(it -> new ResourcePrice(request.getFamily(), request.getType(), it)))
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

    @Value
    static class Machine {
        private final String prefix;
        private final String family;
        private final int cpu;
        private final double ram;
        private final int gpu;
        private final String gpuType;

        public static Optional<Machine> from(final MachineType machineType) {
            final String[] elements = Optional.ofNullable(machineType.getName()).orElse("").split("-");
            if (elements.length == 2) {
                return Optional.of(new Machine(elements[0], elements[1], 1, 0, 0, null));
            }
            if (elements.length == 3) {
                try {
                    final int cpu = Integer.parseInt(elements[2]);
                    final double memory = new BigDecimal((double) machineType.getMemoryMb() / 1024)
                            .setScale(2, RoundingMode.HALF_EVEN)
                            .doubleValue();
                    return Optional.of(new Machine(elements[0], elements[1], cpu, memory, 0, null));
                } catch (NumberFormatException e) {
                    log.warn(String.format("GCP Machine Type name '%s' parsing has failed.", machineType), e);
                    return Optional.empty();
                }
            }
            return Optional.empty();
        }
    }

    enum ResourceType {
        CPU, RAM, GPU;

        public String alias() {
            return name().toLowerCase();
        }
    }

    @Value
    static class ResourceRequest {
        private final String family;
        private final ResourceType type;
        private final String prefix;
    }

    @Value
    static class ResourcePrice {
        private final String family;
        private final ResourceType type;
        private final long nanos;

        public long in(final Machine machine) {
            switch (type) {
                case CPU: return machine.getCpu() * nanos;
                case RAM: return Math.round(machine.getRam() * nanos);
                case GPU: return machine.getGpu() * nanos;
                default: return 0;
            }
        }
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
