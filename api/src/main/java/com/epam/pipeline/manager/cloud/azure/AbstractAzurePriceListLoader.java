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

package com.epam.pipeline.manager.cloud.azure;


import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import com.epam.pipeline.manager.datastorage.providers.azure.AzureHelper;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.credentials.AzureTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.ComputeResourceType;
import com.microsoft.azure.management.compute.ResourceSkuCapabilities;
import com.microsoft.azure.management.compute.ResourceSkuRestrictionsReasonCode;
import com.microsoft.azure.management.compute.implementation.ResourceSkuInner;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import com.microsoft.azure.management.resources.fluentcore.model.HasInner;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.manager.cloud.CloudInstanceService.log;

public abstract class AbstractAzurePriceListLoader {

    public static final String LOCALE = "en-US";
    public static final String REGION_INFO = "US";
    public static final String WINDOWS_OS = "Windows";
    public static final String LINUX_OS = "Linux";
    public static final String V_CPU_CAPABILITY = "vCPUs";
    public static final String GPU_CAPABILITY = "GPUs";
    public static final String MEMORY_CAPABILITY = "MemoryGB";
    public static final String IS_PREMIUM_CAPABILITY = "PremiumIO";
    public static final String DISK_SIZE_CAPABILITY = "MaxSizeGiB";
    public static final String PROMO = "Promo";
    public static final String STANDARD = "Standard";
    public static final String BASIC = "Basic";
    public static final String DISK_INDICATOR = "Disks";
    public static final String VIRTUAL_MACHINES_CATEGORY = "Virtual Machines";
    public static final String DISKS_CATEGORY = "Storage";
    public static final String LOW_PRIORITY_VM_POSTFIX = " Low Priority";
    public static final String DELIMITER = "/";
    public static final String AZURE_PRICING_FILTERS =
            "OfferDurableId eq '%s' and Currency eq '%s' and Locale eq '%s' and RegionInfo eq '%s'";
    public static final int READ_TIMEOUT = 60;
    public static final int CONNECT_TIMEOUT = 60;
    public static final String GENERAL_PURPOSE_FAMILY = "General purpose";
    public static final String GPU_FAMILY = "GPU instance";
    public static final double DEFAULT_PRICE = 0.0;
    public static final String LOW_PRIORITY_CAPABLE = "LowPriorityCapable";
    public static final String TRUE = "True";
    public static final String DEFAULT_DISK_UNIT = "1/Month";

    protected final String meterRegionName;
    protected final String azureApiUrl;
    protected final String authPath;

    protected AbstractAzurePriceListLoader(String meterRegionName, String azureApiUrl, String authPath) {
        this.meterRegionName = meterRegionName;
        this.azureApiUrl = azureApiUrl;
        this.authPath = authPath;
    }

    public List<InstanceOffer> load(final AbstractCloudRegion region) throws IOException {
        final AzureTokenCredentials credentials = getAzureCredentials();
        final Azure client = AzureHelper.buildClient(authPath);

        final Map<String, ResourceSkuInner> vmSkusByName = client.computeSkus()
                .listbyRegionAndResourceType(Region.fromName(region.getRegionCode()),
                        ComputeResourceType.VIRTUALMACHINES)
                .stream()
                .map(HasInner::inner)
                .filter(sku -> Objects.nonNull(sku.name()) && isAvailableForSubscription(sku))
                .collect(Collectors.toMap(ResourceSkuInner::name, Function.identity()));

        final Map<String, ResourceSkuInner> diskSkusByName = client.computeSkus()
                .listByResourceType(ComputeResourceType.DISKS)
                .stream()
                .map(HasInner::inner)
                .filter(sku -> Objects.nonNull(sku.size()) && isAvailableForSubscription(sku))
                .collect(Collectors.toMap(ResourceSkuInner::size, Function.identity(), (o1, o2) -> o1));

        return getInstanceOffers(region, credentials, client, vmSkusByName, diskSkusByName);
    }

    protected abstract List<InstanceOffer> getInstanceOffers(AbstractCloudRegion region,
                                                             AzureTokenCredentials credentials, Azure client,
                                                             Map<String, ResourceSkuInner> vmSkusByName,
                                                             Map<String, ResourceSkuInner> diskSkusByName)
            throws IOException;

    public abstract String getAPIVersion();

    protected AzureTokenCredentials getAzureCredentials() throws IOException {
        if (StringUtils.isBlank(authPath)) {
            return AzureHelper.getAzureCliCredentials();
        }
        return ApplicationTokenCredentials.fromFile(new File(authPath));
    }

    protected boolean isLowPriorityAvailable(final ResourceSkuInner sku) {
        return ListUtils.emptyIfNull(sku.capabilities())
                .stream()
                .anyMatch(capability -> LOW_PRIORITY_CAPABLE.equals(capability.name()) &&
                        TRUE.equals(capability.value()));
    }

    protected List<InstanceOffer> getOffersFromSku(final Map<String, ResourceSkuInner> vmSkusByName,
                                                 final Map<String, ResourceSkuInner> diskSkusByName,
                                                 final Long regionId) {
        log.debug("Azure prices are not available. Instance offers will be loaded without price.");
        final Stream<InstanceOffer> onDemandVmOffers = MapUtils.emptyIfNull(vmSkusByName)
                .values()
                .stream()
                .map(sku -> vmSkuToOffer(regionId, sku, DEFAULT_PRICE, sku.name(),
                        CloudInstancePriceService.TermType.ON_DEMAND, LINUX_OS));

        final Stream<InstanceOffer> lowPriorityVmOffers = MapUtils.emptyIfNull(vmSkusByName)
                .values()
                .stream()
                .filter(this::isLowPriorityAvailable)
                .map(sku -> vmSkuToOffer(regionId, sku, DEFAULT_PRICE,
                        sku.name() + LOW_PRIORITY_VM_POSTFIX,
                        CloudInstancePriceService.TermType.LOW_PRIORITY, LINUX_OS));

        final Stream<InstanceOffer> diskOffers = MapUtils.emptyIfNull(diskSkusByName)
                .values()
                .stream()
                .map(sku -> diskSkuToOffer(regionId, sku, DEFAULT_PRICE, DEFAULT_DISK_UNIT));

        return Stream.concat(Stream.concat(onDemandVmOffers, lowPriorityVmOffers), diskOffers)
                .collect(Collectors.toList());
    }

    protected boolean isAvailableForSubscription(final ResourceSkuInner sku) {
        return ListUtils.emptyIfNull(sku.restrictions())
                .stream()
                .noneMatch(restriction -> restriction.reasonCode()
                        .equals(ResourceSkuRestrictionsReasonCode.NOT_AVAILABLE_FOR_SUBSCRIPTION));
    }

    protected InstanceOffer diskSkuToOffer(final Long regionId,
                                         final ResourceSkuInner diskSku,
                                         final double price,
                                         final String unit) {
        final Map<String, String> capabilitiesByName = ListUtils.emptyIfNull(diskSku.capabilities())
                .stream()
                .collect(Collectors.toMap(ResourceSkuCapabilities::name, ResourceSkuCapabilities::value));
        return InstanceOffer.builder()
                .cloudProvider(CloudProvider.AZURE)
                .tenancy(CloudInstancePriceService.SHARED_TENANCY)
                .productFamily(CloudInstancePriceService.STORAGE_PRODUCT_FAMILY)
                .sku(diskSku.size())
                .priceListPublishDate(new Date())
                .currency(CloudInstancePriceService.CURRENCY)
                .pricePerUnit(price)
                .regionId(regionId)
                .unit(unit)
                .volumeType(CloudInstancePriceService.GENERAL_PURPOSE_VOLUME_TYPE)
                .memory(Float.parseFloat(capabilitiesByName.getOrDefault(DISK_SIZE_CAPABILITY, "0")))
                .build();
    }

    protected InstanceOffer vmSkuToOffer(final Long regionId,
                                       final ResourceSkuInner resourceSku,
                                       final double price,
                                       final String sku,
                                       final CloudInstancePriceService.TermType termType,
                                       final String os) {
        final List<ResourceSkuCapabilities> capabilities = resourceSku.capabilities();
        final Map<String, String> capabilitiesByName = capabilities.stream()
                .collect(Collectors.toMap(ResourceSkuCapabilities::name,
                        ResourceSkuCapabilities::value));
        final int gpu = Integer.parseInt(capabilitiesByName.getOrDefault(GPU_CAPABILITY, "0"));
        return InstanceOffer.builder()
                .cloudProvider(CloudProvider.AZURE)
                .termType(termType.getName())
                .tenancy(CloudInstancePriceService.SHARED_TENANCY)
                .productFamily(CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY)
                .sku(sku)
                .priceListPublishDate(new Date())
                .currency(CloudInstancePriceService.CURRENCY)
                .instanceType(resourceSku.name())
                .pricePerUnit(price)
                .regionId(regionId)
                .unit(CloudInstancePriceService.HOURS_UNIT)
                .volumeType(getDiskType(capabilitiesByName.getOrDefault(IS_PREMIUM_CAPABILITY, "false")))
                .operatingSystem(os)
                .instanceFamily(gpu == 0 ? GENERAL_PURPOSE_FAMILY : GPU_FAMILY)
                .vCPU(Integer.parseInt(capabilitiesByName.getOrDefault(V_CPU_CAPABILITY, "0")))
                .gpu(gpu)
                .memory(Float.parseFloat(capabilitiesByName.getOrDefault(MEMORY_CAPABILITY, "0")))
                .build();
    }

    protected String getDiskType(final String isPremiumSupport) {
        return isPremiumSupport.equalsIgnoreCase("true") ? "Premium" : "Standard";
    }

    protected ResourceSkuInner getVirtualMachineSku(final Map<String, ResourceSkuInner> virtualMachineSkus,
                                                  final String vmSize, final String subCategory) {
        String vmSkusKey = (subCategory.contains(BASIC) ? BASIC : STANDARD) + "_" + vmSize;

        if (subCategory.contains(PROMO)) {
            vmSkusKey += "_" + PROMO;
        }

        return virtualMachineSkus.get(vmSkusKey);
    }

    protected ResourceSkuInner getDiskSku(final Map<String, ResourceSkuInner> disksSkus,
                                        final String diskName) {
        final String diskSkusKey = diskName.replace(DISK_INDICATOR, "").trim();
        return disksSkus.get(diskSkusKey);
    }

    protected String getOperatingSystem(final String subCategory) {
        return subCategory.trim().endsWith(WINDOWS_OS) ? WINDOWS_OS : LINUX_OS;
    }

    protected List<String> getVmSizes(final String rawMeterName) {
        return Arrays.stream(rawMeterName.split(DELIMITER))
                .map(vmSize -> vmSize.trim()
                        .replaceAll(LOW_PRIORITY_VM_POSTFIX, StringUtils.EMPTY)
                        .replaceAll(" ", "_"))
                .collect(Collectors.toList());
    }

}
