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

import com.epam.pipeline.billingreportagent.model.pricing.AzureEAPricingMeter;
import com.epam.pipeline.billingreportagent.model.pricing.AzureEAPricingResult;
import com.epam.pipeline.billingreportagent.service.impl.pricing.AzurePricingClient;
import com.epam.pipeline.entity.region.AzureRegion;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
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
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AzureEARawPriceLoader {

    private static final int READ_TIMEOUT = 30;
    private static final int CONNECT_TIMEOUT = 30;
    private static final String API_VERSION = "2019-10-01";
    private static final String AUTH_TOKEN_TEMPLATE = "Bearer %s";
    private static final String CURRENCY = "USD";


    private LocalDateTime lastPriceUpdate;
    private final Long retentionTimeoutMinutes;
    private final Map<String, AzureEAPricingResult> azureRegionPricing = new HashMap<>();

    @Autowired
    public AzureEARawPriceLoader(final @Value("${sync.storage.azure.price.retention.minutes:30}")
                                   Long retentionTimeoutMinutes) {
        this.lastPriceUpdate = LocalDateTime.now();
        this.retentionTimeoutMinutes = retentionTimeoutMinutes;
    }

    public AzureEAPricingResult getRawPricesUsingPipelineRegion(final AzureRegion region) throws IOException {
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

    private AzureEAPricingResult getPriceSheet(final AzureRegion region, ApplicationTokenCredentials credentials)
            throws IOException {
        return AzureEAPricingResult.builder().properties(
                AzureEAPricingResult.PricingProperties.builder().pricesheets(
                        getPriceSheet(region, credentials, new ArrayList<>(), null)
                                .stream()
                                .filter(details -> region.getMeterRegionName().equals(details.getMeterRegion()))
                                .filter(details -> CURRENCY.equals(details.getCurrencyCode()))
                                .collect(Collectors.toList())
                ).build()
        ).build();
    }

    private List<AzureEAPricingMeter> getPriceSheet(final AzureRegion region, ApplicationTokenCredentials credentials,
                                                    List<AzureEAPricingMeter> buffer, String skiptoken
    ) throws IOException {
        Response<AzureEAPricingResult> meterDetails = buildRetrofitClient(credentials.environment().resourceManagerEndpoint())
                .getPricesheet(String.format(AUTH_TOKEN_TEMPLATE,
                        credentials.getToken(credentials.environment().resourceManagerEndpoint())),
                        region.getSubscription(),
                        API_VERSION, "meterDetails", 10000, skiptoken).execute();
        if (!meterDetails.isSuccessful()) {
            throw new IllegalStateException(String.format("Can't load Azure price list for region with id=%s!",
                    region.getId()));
        }
        Optional<AzureEAPricingResult.PricingProperties> pricingProperties = Optional.ofNullable(meterDetails.body()).map(AzureEAPricingResult::getProperties);
        if (pricingProperties.isPresent()) {
            AzureEAPricingResult.PricingProperties properties = pricingProperties.get();
            buffer.addAll(properties.getPricesheets());
            if (StringUtils.isNotBlank(properties.getNextLink())) {
                Pattern pattern = Pattern.compile(".*skiptoken=([^&]+).*");
                Matcher matcher = pattern.matcher(properties.getNextLink());
                if (matcher.matches()) {
                    skiptoken = matcher.group(1);
                    return getPriceSheet(region, credentials, buffer, skiptoken);
                }
            }
        }
        return buffer;
    }

    private ApplicationTokenCredentials getCredentialsFromFile(final String credentialsPath) {
        try {
            return ApplicationTokenCredentials.fromFile(new File(credentialsPath));
        } catch (IOException e) {
            throw new IllegalArgumentException("Can't access given Azure auth file!");
        }
    }

    private AzureEAPricingResult getAzurePricing(final AzureRegion region) throws IOException {
        final ApplicationTokenCredentials credentials = getCredentialsFromFile(region.getAuthFile());
        return getPriceSheet(region, credentials);
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
