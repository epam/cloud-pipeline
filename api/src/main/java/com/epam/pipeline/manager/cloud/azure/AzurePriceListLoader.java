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
import com.epam.pipeline.entity.pricing.azure.AzurePricingMeter;
import com.epam.pipeline.entity.pricing.azure.AzurePricingResult;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.ComputeResourceType;
import com.microsoft.azure.management.compute.ResourceSkuCapabilities;
import com.microsoft.azure.management.compute.ResourceSkuRestrictionsReasonCode;
import com.microsoft.azure.management.compute.implementation.ResourceSkuInner;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import com.microsoft.azure.management.resources.fluentcore.model.HasInner;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.Assert;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.manager.cloud.CloudInstancePriceService.*;
import static com.epam.pipeline.manager.cloud.azure.AzurePricingClient.executeRequest;

@Slf4j
public class AzurePriceListLoader {

    private static final String CURRENCY = "USD";
    private static final String LOCALE = "en-US";
    private static final String REGION_INFO = "US";
    private static final String API_VERSION = "2016-08-31-preview";
    private static final String WINDOWS_OS = "Windows";
    private static final String LINUX_OS = "Linux";
    private static final String V_CPU_CAPABILITY = "vCPUs";
    private static final String GPU_CAPABILITY = "GPUs";
    private static final String MEMORY_CAPABILITY = "MemoryGB";
    private static final String IS_PREMIUM_CAPABILITY = "PremiumIO";
    private static final String DISK_SIZE_CAPABILITY = "MaxSizeGiB";
    private static final String PROMO = "Promo";
    private static final String STANDARD = "Standard";
    private static final String BASIC = "Basic";
    private static final String DISK_INDICATOR = "Disks";
    private static final String VIRTUAL_MACHINES_CATEGORY = "Virtual Machines";
    private static final String DISKS_CATEGORY = "Storage";
    private static final String LOW_PRIORITY_VM_POSTFIX = " Low Priority";
    private static final String DELIMITER = "/";
    private static final String AZURE_PRICING_FILTERS =
            "OfferDurableId eq '%s' and Currency eq '%s' and Locale eq '%s' and RegionInfo eq '%s'";
    private static final int READ_TIMEOUT = 30;
    private static final int CONNECT_TIMEOUT = 30;
    private static final String GENERAL_PURPOSE_FAMILY = "General purpose";
    private static final String GPU_FAMILY = "GPU instance";
    public static final String EMPTY = "";

    private final AzurePricingClient azurePricingClient;
    private final String meterRegionName;
    private final String azureApiUrl;
    private final String authPath;
    private final String offerId;

    public AzurePriceListLoader(final String authPath, final String offerId, String meterRegionName,
                                final String azureApiUrl) {
        this.authPath = authPath;
        this.offerId = offerId;
        this.meterRegionName = meterRegionName;
        this.azureApiUrl = azureApiUrl;
        this.azurePricingClient = buildRetrofitClient(azureApiUrl);
    }

    public List<InstanceOffer> load(final AbstractCloudRegion region) throws IOException {
        final ApplicationTokenCredentials credentials = ApplicationTokenCredentials.fromFile(new File(authPath));
        final Azure client = Azure.authenticate(credentials).withDefaultSubscription();

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

        final String subscriptionsId = client.subscriptionId();
        Assert.isTrue(StringUtils.isNotBlank(subscriptionsId), "Could not find subscription ID");

        final String token = credentials.getToken(azureApiUrl);
        Assert.isTrue(StringUtils.isNotBlank(token), "Could not find access token");

        final AzurePricingResult prices = getPricing(subscriptionsId, offerId, token);
        Assert.isTrue(Objects.nonNull(prices) && CollectionUtils.isNotEmpty(prices.getMeters()),
                "Azure prices prices is empty");

        return mergeSkusWithPrices(prices.getMeters(), vmSkusByName, diskSkusByName, meterRegionName, region.getId());
    }

    private boolean isAvailableForSubscription(final ResourceSkuInner sku) {
        return ListUtils.emptyIfNull(sku.restrictions())
                .stream()
                .noneMatch(restriction -> restriction.reasonCode()
                        .equals(ResourceSkuRestrictionsReasonCode.NOT_AVAILABLE_FOR_SUBSCRIPTION));
    }

    private AzurePricingClient buildRetrofitClient(final String azureApiUrl) {
        final OkHttpClient client = new OkHttpClient.Builder()
                .followRedirects(true)
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .hostnameVerifier((s, sslSession) -> true)
                .build();
        final ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        final Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(azureApiUrl)
                .addConverterFactory(JacksonConverterFactory.create(mapper))
                .client(client)
                .build();
        return retrofit.create(AzurePricingClient.class);
    }

    private AzurePricingResult getPricing(final String subscription, final String offerId, final String token) {
        final String filter = String.format(AZURE_PRICING_FILTERS, offerId, CURRENCY, LOCALE, REGION_INFO);
        return executeRequest(azurePricingClient.getPricing("Bearer " + token, subscription, filter, API_VERSION));
    }

    List<InstanceOffer> mergeSkusWithPrices(final List<AzurePricingMeter> prices,
                                            final Map<String, ResourceSkuInner> virtualMachineSkus,
                                            final Map<String, ResourceSkuInner> diskSkusByName,
                                            final String meterRegion, final Long regionId) {
        return prices.stream()
                .filter(meter -> meterRegion.equalsIgnoreCase(meter.getMeterRegion()))
                .filter(this::verifyPricingMeters)
                .flatMap(meter -> {
                    if (virtualMachinesCategory(meter)) {
                        return getVmSizes(meter.getMeterName())
                                .stream()
                                .map(vmSize -> buildVmInstanceOffer(virtualMachineSkus, meter, vmSize, regionId));
                    }
                    if (diskCategory(meter)) {
                        return Stream.of(buildDiskInstanceOffer(diskSkusByName, meter, regionId));
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private boolean verifyPricingMeters(final AzurePricingMeter meter) {
        return StringUtils.isNotBlank(meter.getMeterCategory())
                && StringUtils.isNotBlank(meter.getMeterId())
                && StringUtils.isNotBlank(meter.getMeterName())
                && StringUtils.isNotBlank(meter.getMeterSubCategory())
                && StringUtils.isNotBlank(meter.getMeterRegion())
                && MapUtils.isNotEmpty(meter.getMeterRates());
    }

    private InstanceOffer buildDiskInstanceOffer(final Map<String, ResourceSkuInner> diskSkusByName,
                                                 final AzurePricingMeter meter, final Long regionId) {
        final ResourceSkuInner diskSku = getDiskSku(diskSkusByName, meter.getMeterName());
        if (diskSku == null) {
            return null;
        }

        final List<ResourceSkuCapabilities> capabilities = diskSku.capabilities();
        final Map<String, String> capabilitiesByName = capabilities.stream()
                .collect(Collectors.toMap(ResourceSkuCapabilities::name,
                        ResourceSkuCapabilities::value));

        return InstanceOffer.builder()
                .tenancy(SHARED_TENANCY)
                .productFamily(STORAGE_PRODUCT_FAMILY)
                .sku(diskSku.size())
                .priceListPublishDate(new Date())
                .currency(CURRENCY)
                .pricePerUnit(meter.getMeterRates().getOrDefault("0", 0f))
                .regionId(regionId)
                .unit(meter.getUnit())
                .volumeType(GENERAL_PURPOSE_VOLUME_TYPE)
                .memory(Float.parseFloat(capabilitiesByName.getOrDefault(DISK_SIZE_CAPABILITY, "0")))
                .build();
    }

    private InstanceOffer buildVmInstanceOffer(final Map<String, ResourceSkuInner> virtualMachineSkus,
                                               final AzurePricingMeter meter, final String vmSize,
                                               final Long regionId) {
        final String subCategory = meter.getMeterSubCategory();

        final ResourceSkuInner resourceSku = getVirtualMachineSku(virtualMachineSkus, vmSize, subCategory);
        if (resourceSku == null) {
            return null;
        }
        final List<ResourceSkuCapabilities> capabilities = resourceSku.capabilities();
        final Map<String, String> capabilitiesByName = capabilities.stream()
                .collect(Collectors.toMap(ResourceSkuCapabilities::name,
                        ResourceSkuCapabilities::value));

        final int gpu = Integer.parseInt(capabilitiesByName.getOrDefault(GPU_CAPABILITY, "0"));
        return InstanceOffer.builder()
                .termType(meter.getMeterName().contains(LOW_PRIORITY_VM_POSTFIX)
                        ? PriceType.LOW_PRIORITY.getName()
                        : PriceType.ON_DEMAND.getName()
                )
                .tenancy(SHARED_TENANCY)
                .productFamily(INSTANCE_PRODUCT_FAMILY)
                .sku(meter.getMeterId())
                .priceListPublishDate(new Date())
                .currency(CURRENCY)
                .instanceType(resourceSku.name())
                .pricePerUnit(meter.getMeterRates().getOrDefault("0", 0f))
                .regionId(regionId)
                .unit(HOURS_UNIT)
                .volumeType(getDiskType(capabilitiesByName.getOrDefault(IS_PREMIUM_CAPABILITY, "false")))
                .operatingSystem(getOperatingSystem(meter.getMeterSubCategory()))
                .instanceFamily(gpu == 0 ? GENERAL_PURPOSE_FAMILY : GPU_FAMILY)
                .vCPU(Integer.parseInt(capabilitiesByName.getOrDefault(V_CPU_CAPABILITY, "0")))
                .gpu(gpu)
                .memory(Float.parseFloat(capabilitiesByName.getOrDefault(MEMORY_CAPABILITY, "0")))
                .build();
    }

    private String getDiskType(final String isPremiumSupport) {
        return isPremiumSupport.equalsIgnoreCase("true") ? "Premium" : "Standard";
    }

    private boolean virtualMachinesCategory(final AzurePricingMeter meter) {
        return meter.getMeterCategory().equals(VIRTUAL_MACHINES_CATEGORY)
                && meter.getMeterSubCategory().contains("Series");
    }

    private boolean diskCategory(final AzurePricingMeter meter) {
        return meter.getMeterCategory().equals(DISKS_CATEGORY) && meter.getMeterSubCategory().contains("SSD")
                && meter.getMeterSubCategory().contains(DISK_INDICATOR)
                && meter.getMeterName().contains(DISK_INDICATOR);
    }

    private ResourceSkuInner getVirtualMachineSku(final Map<String, ResourceSkuInner> virtualMachineSkus,
                                                  final String vmSize, final String subCategory) {
        String vmSkusKey = (subCategory.contains(BASIC) ? BASIC : STANDARD) + "_" + vmSize;

        if (subCategory.contains(PROMO)) {
            vmSkusKey += "_" + PROMO;
        }

        return virtualMachineSkus.get(vmSkusKey);
    }

    private ResourceSkuInner getDiskSku(final Map<String, ResourceSkuInner> disksSkus,
                                        final String diskName) {
        final String diskSkusKey = diskName.replace(DISK_INDICATOR, "").trim();
        return disksSkus.get(diskSkusKey);
    }

    private String getOperatingSystem(final String subCategory) {
        return subCategory.trim().endsWith(WINDOWS_OS) ? WINDOWS_OS : LINUX_OS;
    }

    private List<String> getVmSizes(final String rawMeterName) {
        return Arrays.stream(rawMeterName.split(DELIMITER))
                .map(vmSize -> vmSize.trim()
                        .replaceAll(LOW_PRIORITY_VM_POSTFIX, EMPTY)
                        .replaceAll(" ", "_"))
                .collect(Collectors.toList());
    }
}
