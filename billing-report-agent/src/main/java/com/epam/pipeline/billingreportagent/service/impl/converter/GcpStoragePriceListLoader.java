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
import com.epam.pipeline.client.gcp.GCPPriceApiService;
import com.epam.pipeline.client.gcp.GCPPriceApiServiceFactory;
import com.epam.pipeline.entity.gcp.price.BillingAccountPriceResponse;
import com.epam.pipeline.entity.gcp.price.Tier;
import com.epam.pipeline.entity.region.CloudProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.cloudbilling.Cloudbilling;
import com.google.api.services.cloudbilling.model.ListSkusResponse;
import com.google.api.services.cloudbilling.model.Money;
import com.google.api.services.cloudbilling.model.Sku;
import com.google.api.services.cloudbilling.model.TierRate;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class GcpStoragePriceListLoader implements StoragePriceListLoader{
    private final String billingAccountId;
    private static final String GCP_STORAGE_SERVICE = "services/95FF-2EF5-5EA1";
    private static final List<String> BILLING_SCOPES =
            Collections.singletonList("https://www.googleapis.com/auth/cloud-platform");
    private static final List<String> SUPPORTED_STORAGE = Arrays.asList("RegionalStorage", "MultiRegionalStorage");

    private static final String PRICE_TEMPLATE = "billingAccounts/%s/skus/%s/price";

    private static final int BILLING_ACCOUNT_SKUS_PAGE_SIZE = 5000;

    public GcpStoragePriceListLoader(final String billingAccountId) {
        this.billingAccountId = billingAccountId;
    }

    @Override
    public Map<String, StoragePricing> loadFullPriceList() throws IOException, GeneralSecurityException {
        final GoogleCredential credentials = GoogleCredential.getApplicationDefault();
        final Cloudbilling cloudBilling = new Cloudbilling.Builder(GoogleNetHttpTransport.newTrustedTransport(),
                JacksonFactory.getDefaultInstance(), credentials.createScoped(BILLING_SCOPES))
                .setApplicationName("Cloud Pipeline Billing Agent")
                .build();

        final ListSkusResponse skuResponse = cloudBilling.services()
            .skus()
            .list(GCP_STORAGE_SERVICE)
            .execute();

        Map<String, Sku> skuMap = skuResponse.getSkus()
                .stream()
                .filter(sku -> SUPPORTED_STORAGE.contains(sku.getCategory().getResourceGroup()))
                .collect(Collectors.toMap(s ->
                        String.format(PRICE_TEMPLATE, billingAccountId, s.getSkuId()), Function.identity()));

        if (StringUtils.isNotBlank(billingAccountId)) {
            GCPPriceApiService gcpPriceApiService = new GCPPriceApiServiceFactory().buildClient(generateToken());
            String currencyCode = extractCurrency(skuMap);

            String pageToken = null;
            do {
                BillingAccountPriceResponse billingAccPrice = gcpPriceApiService.getAllPrices(billingAccountId,
                    currencyCode, pageToken, BILLING_ACCOUNT_SKUS_PAGE_SIZE).execute().body();

                pageToken = billingAccPrice.getNextPageToken();

                billingAccPrice.getBillingAccountPrices().stream().forEach(element -> {
                    final String key = element.getName();
                    if (skuMap.containsKey(key)) {
                        List<TierRate> tierRates = skuMap.get(key).getPricingInfo().get(0)
                            .getPricingExpression().getTieredRates();
                        for (int i = 0; i < tierRates.size(); i++) {
                            Tier currTier = element.getRate().getTiers().get(i);
                            if (currTier != null) {
                                Double startAmount = Double.valueOf(currTier.getStartAmount().getValue());
                                tierRates.get(i).setStartUsageAmount(startAmount);
                                Money currMoney = tierRates.get(i).getUnitPrice();
                                currMoney.setUnits(currTier.getContractPrice().getUnits());
                                currMoney.setNanos(currTier.getContractPrice().getNanos());
                            }
                        }
                    }
                });
            } while (StringUtils.isNotBlank(pageToken));
        }

        return skuMap.values().stream()
            .map(this::convertSku)
            .map(Map::entrySet)
            .flatMap(Set::stream)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (k1, k2) -> k1));
    }

    private String generateToken() throws IOException {
        GoogleCredentials googleCredentials = GoogleCredentials.getApplicationDefault().createScoped(BILLING_SCOPES);
        googleCredentials.refreshIfExpired();
        return googleCredentials.getAccessToken().getTokenValue();
    }

    private String extractCurrency(final Map<String, Sku> skuMap) {
        return skuMap.values().stream()
            .filter(Objects::nonNull)
            .map(sku -> sku.getPricingInfo())
            .filter(list -> list != null && !list.isEmpty())
            .map(list -> list.get(0).getPricingExpression())
            .filter(Objects::nonNull)
            .map(expr -> expr.getTieredRates())
            .filter(list -> list != null && !list.isEmpty())
            .map(list -> list.get(0).getUnitPrice().getCurrencyCode())
            .findFirst()
            .orElse("USD");
    }

    private Map<String, StoragePricing> convertSku(final Sku sku) {
        final List<TierRate> tieredRates = sku.getPricingInfo().get(0).getPricingExpression().getTieredRates();
        final TierRate upperBound = new TierRate();
        upperBound.setStartUsageAmount(Double.POSITIVE_INFINITY);
        tieredRates.add(upperBound);
        return sku.getServiceRegions().stream()
            .collect(Collectors.toMap(Function.identity(), v -> convertGcpTierRateToStoragePrices(tieredRates)));
    }

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.GCP;
    }

    private StoragePricing convertGcpTierRateToStoragePrices(final List<TierRate> rates) {
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
            pricingRanges.addPrice(AwsStoragePriceListLoader.DEFAULT_STORAGE_CLASS,
                    new StoragePricing.StoragePricingEntity(startRange, endRange, priceCentsPerGb));
        }
        return pricingRanges;
    }
}
