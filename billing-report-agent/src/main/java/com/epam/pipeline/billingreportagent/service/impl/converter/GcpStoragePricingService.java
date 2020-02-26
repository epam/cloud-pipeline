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
import com.epam.pipeline.entity.region.CloudProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.cloudbilling.Cloudbilling;
import com.google.api.services.cloudbilling.model.ListServicesResponse;
import com.google.api.services.cloudbilling.model.ListSkusResponse;
import com.google.api.services.cloudbilling.model.Service;
import com.google.api.services.cloudbilling.model.Sku;
import com.google.api.services.cloudbilling.model.TierRate;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class GcpStoragePricingService extends AbstractStoragePricingService {

    private static final String GCP_STORAGE_SERVICES_FAMILY = "Cloud Storage";
    private static final String GCP_DATA_STORAGE_GROUP = "RegionalStorage";

    public GcpStoragePricingService() {
        super(GCP_STORAGE_SERVICES_FAMILY);
    }

    @Override
    public void loadFullPriceList() throws IOException, GeneralSecurityException {
        final String accessToken = GoogleCredential.getApplicationDefault().getAccessToken();
        final Cloudbilling cloudbilling = new Cloudbilling(GoogleNetHttpTransport.newTrustedTransport(),
                                                           JacksonFactory.getDefaultInstance(),
                                                           null);
        final ListServicesResponse services = cloudbilling.services().list().setAccessToken(accessToken).execute();
        final Service cloudStorageService = services.getServices().stream()
            .filter(service -> service.getDisplayName().equals(GCP_STORAGE_SERVICES_FAMILY))
            .findAny()
            .orElseThrow(() -> new RuntimeException("No services received from GCP!"));

        final ListSkusResponse skuResponse = cloudbilling.services()
            .skus()
            .list(cloudStorageService.getName())
            .setAccessToken(accessToken)
            .execute();

        final Map<List<String>, List<TierRate>> dataStoragePriceTable = skuResponse.getSkus().stream()
            .filter(sku -> sku.getCategory().getResourceGroup().equals(GCP_DATA_STORAGE_GROUP))
            .collect(
                Collectors.toMap(Sku::getServiceRegions,
                                 sku -> sku.getPricingInfo().get(0).getPricingExpression().getTieredRates())
            );

        dataStoragePriceTable.forEach((regions, gcpRates) -> {
            final StoragePricing pricing = convertGcpTierRateToStoragePrices(gcpRates);
            regions.forEach(region -> putRegionPricing(region, pricing));
        });
    }

    @Override
    protected CloudProvider getProvider() {
        return CloudProvider.GCP;
    }

    private StoragePricing convertGcpTierRateToStoragePrices(final List<TierRate> rates) {
        final TierRate upperBound = new TierRate();
        upperBound.setStartUsageAmount(Double.POSITIVE_INFINITY);
        rates.add(upperBound);
        final StoragePricing pricingRanges = new StoragePricing();
        for (int i = 0; i < rates.size() - 1; i++) {
            final TierRate currentRate = rates.get(i);
            final TierRate nextRate = rates.get(i + 1);
            final long startRange = currentRate.getStartUsageAmount().longValue() * BYTES_TO_GB;
            final Double startUsageAmount = nextRate.getStartUsageAmount();
            final long endRange = Double.isInfinite(startUsageAmount)
                                  ? Long.MAX_VALUE
                                  : startUsageAmount.longValue() * BYTES_TO_GB;
            final BigDecimal priceCentsPerGb =
                new BigDecimal(currentRate.getUnitPrice().getNanos().doubleValue() / Math.pow(10, 9),
                               new MathContext(PRECISION))
                    .multiply(BigDecimal.valueOf(CENTS_IN_DOLLAR));
            pricingRanges.addPrice(new StoragePricing.StoragePricingEntity(startRange, endRange, priceCentsPerGb));
        }
        return pricingRanges;
    }
}
