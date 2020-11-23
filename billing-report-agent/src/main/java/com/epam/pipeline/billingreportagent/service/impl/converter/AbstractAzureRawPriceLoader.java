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

import com.epam.pipeline.billingreportagent.model.pricing.AzurePricingEntity;
import com.epam.pipeline.billingreportagent.service.impl.pricing.AzurePricingClient;
import com.epam.pipeline.entity.region.AzureRegion;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public abstract class AbstractAzureRawPriceLoader {

    private static final int READ_TIMEOUT = 30;
    private static final int CONNECT_TIMEOUT = 30;
    public static final String AUTH_TOKEN_TEMPLATE = "Bearer %s";
    public static final String CURRENCY = "USD";


    protected LocalDateTime lastPriceUpdate;
    protected final Long retentionTimeoutMinutes;
    protected final Map<String, List<AzurePricingEntity>> azureRegionPricing = new HashMap<>();

    @Autowired
    public AbstractAzureRawPriceLoader(final @Value("${sync.storage.azure.price.retention.minutes:30}")
                                   Long retentionTimeoutMinutes) {
        this.lastPriceUpdate = LocalDateTime.now();
        this.retentionTimeoutMinutes = retentionTimeoutMinutes;
    }

    public List<AzurePricingEntity> getRawPricesUsingPipelineRegion(final AzureRegion region) throws IOException {
        final String offerAndSubscription = getRegionOfferAndSubscription(region);
        updatePricesIfRequired(region, offerAndSubscription);
        return azureRegionPricing.get(offerAndSubscription);
    }

    public String getRegionOfferAndSubscription(final AzureRegion region) {
        return String.join(",", region.getPriceOfferId(), region.getSubscription());
    }

    private synchronized void updatePricesIfRequired(final AzureRegion region, final String offerAndSubscription)
        throws IOException {
        final LocalDateTime pricesUpdateStart = LocalDateTime.now();
        if (!azureRegionPricing.containsKey(offerAndSubscription)
            || lastPriceUpdate.plus(retentionTimeoutMinutes, ChronoUnit.MINUTES).isBefore(pricesUpdateStart)) {
            azureRegionPricing.put(offerAndSubscription, getAzurePricing(region));
            lastPriceUpdate = LocalDateTime.now();
        }
    }

    protected ApplicationTokenCredentials getCredentialsFromFile(final String credentialsPath) {
        try {
            return ApplicationTokenCredentials.fromFile(new File(credentialsPath));
        } catch (IOException e) {
            throw new IllegalArgumentException("Can't access given Azure auth file!");
        }
    }

    protected abstract List<AzurePricingEntity> getAzurePricing(final AzureRegion region) throws IOException;

    protected AzurePricingClient buildRetrofitClient(final String azureApiUrl) {
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
