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

import com.epam.pipeline.billingreportagent.model.billing.StoragePricing;
import com.epam.pipeline.billingreportagent.model.pricing.AzurePricingMeter;
import com.epam.pipeline.billingreportagent.model.pricing.AzurePricingResult;
import com.epam.pipeline.billingreportagent.service.impl.pricing.AzurePricingClient;
import com.epam.pipeline.entity.region.CloudProvider;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AzureStoragePriceListLoader implements StoragePriceListLoader {

    private static final int READ_TIMEOUT = 30;
    private static final int CONNECT_TIMEOUT = 30;
    private static final String API_VERSION = "2016-08-31-preview";
    private static final String STORAGE_CATEGORY = "Storage";
    private static final String GB_MONTH_UNIT = "1 GB/Month";
    private static final String AUTH_TOKEN_TEMPLATE = "Bearer %s";
    private static final String STORAGE_SUBCATEGORY_BLOCK_BLOB = "General Block Blob";
    private static final String REDUNDANCY_TYPE = "LRS";
    private static final String FILTER_TEMPLATE =
        "OfferDurableId eq '%s' and Currency eq 'USD' and Locale eq 'en-US' and RegionInfo eq 'US'";

    private AzurePricingClient azurePricingClient;
    private ApplicationTokenCredentials credentials;
    private String authPath;
    private String offerId;

    public AzureStoragePriceListLoader(final String offerId, final String authPath) {
        this.offerId = offerId;
        this.authPath = authPath;
        initAzureCredentialsAndClient();
    }

    private void initAzureCredentialsAndClient() {
        try {
            credentials = ApplicationTokenCredentials.fromFile(new File(authPath));
        } catch (IOException e) {
            throw new IllegalArgumentException("Can't access given Azure auth file!");
        }
        azurePricingClient = buildRetrofitClient(credentials.environment().resourceManagerEndpoint());
    }

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.AZURE;
    }

    @Override
    public Map<String, StoragePricing> loadFullPriceList() throws Exception {
        final String token = credentials.getToken(credentials.environment().resourceManagerEndpoint());
        final Response<AzurePricingResult> pricingResponse =
            azurePricingClient.getPricing(String.format(AUTH_TOKEN_TEMPLATE, token),
                                          credentials.defaultSubscriptionId(),
                                          String.format(FILTER_TEMPLATE, offerId),
                                          API_VERSION).execute();
        if (!pricingResponse.isSuccessful()) {
            throw new IllegalStateException("Can't load Azure price list!");
        }
        return Optional.ofNullable(pricingResponse.body()).orElseGet(() -> {
            final AzurePricingResult azurePricingResult = new AzurePricingResult();
            azurePricingResult.setMeters(Collections.emptyList());
            return azurePricingResult;
        }).getMeters()
            .stream()
            .filter(meter -> GB_MONTH_UNIT.equals(meter.getUnit()))
            .filter(meter -> STORAGE_CATEGORY.equals(meter.getMeterCategory()))
            .filter(meter -> STORAGE_SUBCATEGORY_BLOCK_BLOB.equals(meter.getMeterSubCategory()))
            .filter(meter -> meter.getMeterName().startsWith(REDUNDANCY_TYPE))
            .filter(meter -> StringUtils.isNotEmpty(meter.getMeterRegion()))
            .collect(Collectors.toMap(AzurePricingMeter::getMeterRegion,
                                      this::convertAzurePricing));
    }

    private StoragePricing convertAzurePricing(final AzurePricingMeter azurePricing) {
        final Map<String, Float> rates = azurePricing.getMeterRates();
        final StoragePricing storagePricing = new StoragePricing();
        final List<StoragePricing.StoragePricingEntity> pricing = rates.entrySet().stream()
            .map(e -> {
                final long rangeStart = Float.valueOf(e.getKey()).longValue();
                final BigDecimal cost = BigDecimal.valueOf(e.getValue().doubleValue());
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
