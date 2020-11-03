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

import com.epam.pipeline.billingreportagent.model.pricing.AzurePricingResult;
import com.epam.pipeline.billingreportagent.service.impl.pricing.AzurePricingClient;
import com.epam.pipeline.entity.region.AzureRegion;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class AzureRawPriceLoader {

    private static final int READ_TIMEOUT = 30;
    private static final int CONNECT_TIMEOUT = 30;
    private static final String API_VERSION = "2016-08-31-preview";
    private static final String AUTH_TOKEN_TEMPLATE = "Bearer %s";
    private static final String FILTER_TEMPLATE =
        "OfferDurableId eq '%s' and Currency eq 'USD' and Locale eq 'en-US' and RegionInfo eq 'US'";

    private LocalDateTime lastPriceUpdate;
    private final Long retentionTimeoutMinutes;
    private final Map<String, Response<AzurePricingResult>> azureRegionPricing = new HashMap<>();

    @Autowired
    public AzureRawPriceLoader(final @Value("${sync.storage.azure.price.retention.minutes:30}")
                                   Long retentionTimeoutMinutes) {
        this.lastPriceUpdate = LocalDateTime.now();
        this.retentionTimeoutMinutes = retentionTimeoutMinutes;
    }

    public Response<AzurePricingResult> getRawPricesUsingPipelineRegion(final AzureRegion region) throws IOException {
        final LocalDateTime pricesUpdateStart = LocalDateTime.now();
        final String offerAndSubscription = getRegionOfferAndSubscription(region);
        if (!azureRegionPricing.containsKey(offerAndSubscription)
            || lastPriceUpdate.plus(retentionTimeoutMinutes, ChronoUnit.MINUTES).isBefore(pricesUpdateStart)) {
            azureRegionPricing.put(offerAndSubscription, getAzurePricing(region));
            lastPriceUpdate = LocalDateTime.now();
        }
        return azureRegionPricing.get(offerAndSubscription);
    }

    public String getRegionOfferAndSubscription(final AzureRegion region) {
        return String.join(",", region.getPriceOfferId(), region.getSubscription());
    }

    private ApplicationTokenCredentials getCredentialsFromFile(final String credentialsPath) {
        try {
            return ApplicationTokenCredentials.fromFile(new File(credentialsPath));
        } catch (IOException e) {
            throw new IllegalArgumentException("Can't access given Azure auth file!");
        }
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
