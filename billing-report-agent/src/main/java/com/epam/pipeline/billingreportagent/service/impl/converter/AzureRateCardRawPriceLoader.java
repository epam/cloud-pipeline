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
import com.epam.pipeline.billingreportagent.model.pricing.AzureRateCardPricingResult;
import com.epam.pipeline.entity.region.AzureRegion;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import retrofit2.Response;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AzureRateCardRawPriceLoader extends AbstractAzureRawPriceLoader {

    private static final String API_VERSION = "2016-08-31-preview";
    private static final String FILTER_TEMPLATE =
        "OfferDurableId eq '%s' and Currency eq 'USD' and Locale eq 'en-US' and RegionInfo eq 'US'";


    @Autowired
    public AzureRateCardRawPriceLoader(final @Value("${sync.storage.azure.price.retention.minutes:30}")
                                   Long retentionTimeoutMinutes) {
        super(retentionTimeoutMinutes);
    }

    protected List<AzurePricingEntity> getAzurePricing(final AzureRegion region) throws IOException {
        final ApplicationTokenCredentials credentials = getCredentialsFromFile(region.getAuthFile());
        final String resourceManagerEndpoint = credentials.environment().resourceManagerEndpoint();
        final Response<AzureRateCardPricingResult> pricingResponse = buildRetrofitClient(resourceManagerEndpoint)
            .getPricing(String.format(AUTH_TOKEN_TEMPLATE,
                                      credentials.getToken(resourceManagerEndpoint)),
                        region.getSubscription(),
                        String.format(FILTER_TEMPLATE, region.getPriceOfferId()),
                        API_VERSION).execute();
        if (!pricingResponse.isSuccessful()) {
            throw new IllegalStateException(String.format("Can't load Azure price list for region with id=%s!",
                                                          region.getId()));
        }
        return Optional.ofNullable(pricingResponse.body())
                .map(AzureRateCardPricingResult::getMeters)
                .orElse(Collections.emptyList())
                .stream()
                .map(meter -> AzurePricingEntity.builder()
                        .meterCategory(meter.getMeterCategory())
                        .meterSubCategory(meter.getMeterSubCategory())
                        .meterName(meter.getMeterName())
                        .meterRegion(meter.getMeterRegion())
                        .meterRates(meter.getMeterRates())
                        .unit(meter.getUnit())
                        .build())
                .collect(Collectors.toList());
    }

}
