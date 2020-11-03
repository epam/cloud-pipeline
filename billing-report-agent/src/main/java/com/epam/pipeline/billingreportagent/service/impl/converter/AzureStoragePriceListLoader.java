/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.billingreportagent.service.impl.converter;

import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.model.billing.StoragePricing;
import com.epam.pipeline.billingreportagent.model.pricing.AzurePricingMeter;
import com.epam.pipeline.billingreportagent.model.pricing.AzurePricingResult;
import com.epam.pipeline.billingreportagent.service.impl.loader.CloudRegionLoader;
import com.epam.pipeline.billingreportagent.service.impl.pricing.AzurePricingClient;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.util.Assert;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Slf4j
public class AzureStoragePriceListLoader implements StoragePriceListLoader {

    private static final int READ_TIMEOUT = 30;
    private static final int CONNECT_TIMEOUT = 30;
    private static final String API_VERSION = "2016-08-31-preview";
    private static final String GB_MONTH_UNIT = "1 GB/Month";
    private static final String AUTH_TOKEN_TEMPLATE = "Bearer %s";
    private static final String STORAGE_SUBCATEGORY_BLOCK_BLOB = "General Block Blob";
    private static final String REDUNDANCY_TYPE = "Hot LRS Data Stored";
    private static final String FILTER_TEMPLATE =
        "OfferDurableId eq '%s' and Currency eq 'USD' and Locale eq 'en-US' and RegionInfo eq 'US'";

    private CloudRegionLoader regionLoader;

    public AzureStoragePriceListLoader(final CloudRegionLoader regionLoader) {
        this.regionLoader = regionLoader;
    }

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.AZURE;
    }

    @Override
    public Map<String, StoragePricing> loadFullPriceList() throws Exception {
        final List<AzureRegion> activeAzureRegions = regionLoader.loadAllEntities().stream()
            .map(EntityContainer::getEntity)
            .filter(AzureRegion.class::isInstance)
            .map(AzureRegion.class::cast)
            .collect(Collectors.toList());
        Assert.isTrue(CollectionUtils.isNotEmpty(activeAzureRegions), "No Azure regions found in current deployment!");
        assertOffersAndRegionMetersAreUnique(activeAzureRegions);
        final Map<String, Response<AzurePricingResult>> azureRegionPricing = new HashMap<>();
        for (final AzureRegion region : activeAzureRegions) {
            final String offerAndSubscription = getRegionOfferAndSubscription(region);
            if (!azureRegionPricing.containsKey(offerAndSubscription)) {
                azureRegionPricing.put(offerAndSubscription, getAzurePricing(region));
            }
        }
        return activeAzureRegions.stream()
            .collect(Collectors.toMap(AbstractCloudRegion::getRegionCode,
                region -> {
                    final String offerAndSubscription = getRegionOfferAndSubscription(region);
                    return getStringStoragePricingForRegion(
                    azureRegionPricing.get(offerAndSubscription),
                    region.getMeterRegionName());
                },
                (region1, region2) -> region1));
    }

    private void assertOffersAndRegionMetersAreUnique(final List<AzureRegion> activeAzureRegions) {
        final Map<String, Set<String>> regionOfferMapping =
            mapRegionsOnFunctionResult(activeAzureRegions, this::getRegionOfferAndSubscription);
        final Map<String, Set<String>> regionMeterMapping =
            mapRegionsOnFunctionResult(activeAzureRegions, AzureRegion::getMeterRegionName);
        for (final String regionName : regionOfferMapping.keySet()) {
            if (regionOfferMapping.get(regionName).size() > 1) {
                throw new IllegalArgumentException(
                    String.format("Regions with the same name [%s] specifies different"
                                  + " offerId or subscription!", regionName));
            }
            if (regionMeterMapping.get(regionName).size() > 1) {
                throw new IllegalArgumentException(
                    String.format("Regions with the same name [%s] specifies different"
                                  + " meter region name!", regionName));
            }
        }
    }

    private Map<String, Set<String>> mapRegionsOnFunctionResult(final List<AzureRegion> activeAzureRegions,
                                                                final Function<AzureRegion, String> function) {
        return activeAzureRegions.stream()
            .collect(Collectors.groupingBy(AbstractCloudRegion::getRegionCode,
                                           Collector.of(HashSet::new,
                                               (set, region) -> set.add(function.apply(region)),
                                               (left, right) -> {
                                                   left.addAll(right);
                                                   return left;
                                               },
                                               Function.identity())));
    }

    private ApplicationTokenCredentials getCredentialsFromFile(final String credentialsPath) {
        try {
            return ApplicationTokenCredentials.fromFile(new File(credentialsPath));
        } catch (IOException e) {
            throw new IllegalArgumentException("Can't access given Azure auth file!");
        }
    }

    private String getRegionOfferAndSubscription(final AzureRegion region) {
        return String.join(",", region.getPriceOfferId(), region.getSubscription());
    }

    private StoragePricing getStringStoragePricingForRegion(
        final Response<AzurePricingResult> pricingResponse,
        final String meterRegion) {
        return Optional.ofNullable(pricingResponse.body()).orElseGet(() -> {
            final AzurePricingResult azurePricingResult = new AzurePricingResult();
            azurePricingResult.setMeters(Collections.emptyList());
            return azurePricingResult;
        }).getMeters()
            .stream()
            .filter(meter -> GB_MONTH_UNIT.equals(meter.getUnit()))
            .filter(meter -> meter.getMeterSubCategory().startsWith(STORAGE_SUBCATEGORY_BLOCK_BLOB))
            .filter(meter -> meter.getMeterName().startsWith(REDUNDANCY_TYPE))
            .filter(meter -> meterRegion.equals(meter.getMeterRegion()))
            .findFirst()
            .map(this::convertAzurePricing)
            .orElseThrow(() -> new IllegalArgumentException(
                String.format("No [%s] region is presented in prices retrieved!", meterRegion)));
    }

    private Response<AzurePricingResult> getAzurePricing(final AzureRegion region) throws IOException {
        final ApplicationTokenCredentials credentials = getCredentialsFromFile(region.getAuthFile());
        final String resourceManagerEndpoint = credentials.environment().resourceManagerEndpoint();
        final Response<AzurePricingResult> pricingResponse = buildRetrofitClient(resourceManagerEndpoint)
            .getPricing(String.format(AUTH_TOKEN_TEMPLATE,
                                      credentials.getToken(resourceManagerEndpoint)),
                        region.getSubscription(),
                        String.format(FILTER_TEMPLATE, region.getPriceOfferId()),
                        API_VERSION).execute();
        if (!pricingResponse.isSuccessful()) {
            throw new IllegalStateException(String.format("Can't load Azure price list for region with id=%s!",
                                                          region.getId()));
        }
        return pricingResponse;
    }

    private StoragePricing convertAzurePricing(final AzurePricingMeter azurePricing) {
        final Map<String, Float> rates = azurePricing.getMeterRates();
        log.debug("Reading price from {}", azurePricing);
        final StoragePricing storagePricing = new StoragePricing();
        final List<StoragePricing.StoragePricingEntity> pricing = rates.entrySet().stream()
            .map(e -> {
                final long rangeStart = Float.valueOf(e.getKey()).longValue();
                final BigDecimal cost = BigDecimal.valueOf(e.getValue().doubleValue())
                    .multiply(BigDecimal.valueOf(CENTS_IN_DOLLAR));
                return new StoragePricing.StoragePricingEntity(rangeStart * BYTES_TO_GB, null, cost);
            })
            .sorted(Comparator.comparing(StoragePricing.StoragePricingEntity::getBeginRangeBytes))
            .collect(Collectors.toList());
        final int pricingSize = pricing.size();
        for (int i = 0; i < pricingSize - 1; i++) {
            pricing.get(i).setEndRangeBytes(pricing.get(i + 1).getBeginRangeBytes());
        }
        pricing.get(pricingSize - 1).setEndRangeBytes(Long.MAX_VALUE);
        storagePricing.getPrices().addAll(pricing);
        return storagePricing;
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
}
