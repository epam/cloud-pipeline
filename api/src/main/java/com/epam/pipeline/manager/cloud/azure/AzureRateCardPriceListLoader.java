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
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.credentials.AzureTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.implementation.ResourceSkuInner;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.Assert;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.manager.cloud.CloudInstancePriceService.TermType;
import static com.epam.pipeline.manager.cloud.azure.AzurePricingClient.executeRequest;

@Slf4j
public class AzureRateCardPriceListLoader extends AbstractAzurePriceListLoader {

    private static final String API_VERSION = "2016-08-31-preview";

    private final AzurePricingClient azurePricingClient;

    private final String offerId;

    public AzureRateCardPriceListLoader(final String authPath, final String offerId, String meterRegionName,
                                        final String azureApiUrl) {
        super(meterRegionName, azureApiUrl, authPath);
        this.offerId = offerId;
        this.azurePricingClient = buildRetrofitClient(azureApiUrl);
    }

    @Override
    protected List<InstanceOffer> getInstanceOffers(AbstractCloudRegion region, AzureTokenCredentials credentials,
                                                    Azure client, Map<String, ResourceSkuInner> vmSkusByName,
                                                    Map<String, ResourceSkuInner> diskSkusByName) throws IOException {
        final Optional<AzurePricingResult> prices = getPricing(client.subscriptionId(), credentials);
        return prices.filter(p -> CollectionUtils.isNotEmpty(p.getMeters()))
                .map(p -> mergeSkusWithPrices(p.getMeters(), vmSkusByName, diskSkusByName,
                        meterRegionName, region.getId()))
                .orElseGet(() -> getOffersFromSku(vmSkusByName, diskSkusByName, region.getId()));
    }

    @Override
    public String getAPIVersion() {
        return API_VERSION;
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

    private Optional<AzurePricingResult> getPricing(final String subscription,
                                                    final AzureTokenCredentials credentials) throws IOException {
        if (StringUtils.isBlank(offerId)) {
            return Optional.empty();
        }
        Assert.isTrue(StringUtils.isNotBlank(subscription), "Could not find subscription ID");
        final String token = credentials.getToken(azureApiUrl);
        Assert.isTrue(StringUtils.isNotBlank(token), "Could not find access token");
        final String filter = String.format(AZURE_PRICING_FILTERS, offerId,
                CloudInstancePriceService.CURRENCY, LOCALE, REGION_INFO);
        return Optional.ofNullable(
                executeRequest(azurePricingClient.getPricing("Bearer " + token, subscription,
                        filter, getAPIVersion())));
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
        return diskSkuToOffer(regionId, diskSku,
                meter.getMeterRates().getOrDefault("0", 0f), meter.getUnit());
    }

    private InstanceOffer buildVmInstanceOffer(final Map<String, ResourceSkuInner> virtualMachineSkus,
                                               final AzurePricingMeter meter, final String vmSize,
                                               final Long regionId) {
        final String subCategory = meter.getMeterSubCategory();
        final ResourceSkuInner resourceSku = getVirtualMachineSku(virtualMachineSkus, vmSize, subCategory);
        if (resourceSku == null) {
            return null;
        }
        return vmSkuToOffer(regionId, resourceSku,
                meter.getMeterRates().getOrDefault("0", 0f),
                meter.getMeterId(),
                meter.getMeterName().contains(LOW_PRIORITY_VM_POSTFIX)
                        ? TermType.LOW_PRIORITY
                        : TermType.ON_DEMAND,
                getOperatingSystem(meter.getMeterSubCategory()));
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

}
