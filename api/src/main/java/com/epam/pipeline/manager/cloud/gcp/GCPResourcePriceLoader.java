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

package com.epam.pipeline.manager.cloud.gcp;

import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.gcp.price.BillingAccountPrice;
import com.epam.pipeline.manager.gcp.price.BillingAccountPriceResponse;
import com.epam.pipeline.manager.gcp.price.GCPPriceApiService;
import com.epam.pipeline.manager.gcp.price.GCPPriceApiServiceFactory;
import com.google.api.services.cloudbilling.Cloudbilling;
import com.google.api.services.cloudbilling.model.ListSkusResponse;
import com.google.api.services.cloudbilling.model.PricingExpression;
import com.google.api.services.cloudbilling.model.PricingInfo;
import com.google.api.services.cloudbilling.model.Sku;
import com.google.api.services.cloudbilling.model.TierRate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import retrofit2.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.epam.pipeline.manager.cloud.CloudInstancePriceService.CURRENCY;

/**
 * Google Cloud Provider resource price loader.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class GCPResourcePriceLoader {

    private static final String COMPUTE_ENGINE_SERVICE_NAME = "services/6F81-5844-456A";
    private static final String PRICE_TEMPLATE = "billingAccounts/%s/skus/%s/price";
    private static final int SKUS_PAGE_SIZE = 2000;
    private static final int BILLING_ACCOUNT_SKUS_PAGE_SIZE = 5000;
    private static final long BILLION = 1_000_000_000L;

    private final GCPPriceApiServiceFactory gcpPriceApiServiceFactory;

    private final GCPClient gcpClient;

    @Value("${gcp.billing.account.id:}")
    private String gcpBillingAccountId;

    /**
     * Retrieves available prices for all the given requests in the specified region.
     */
    public Set<GCPResourcePrice> load(final GCPRegion region, final List<GCPResourceRequest> requests) {
        try {
            final Cloudbilling cloudbilling = gcpClient.buildBillingClient(region);
            final GCPPriceApiService priceApiService = gcpPriceApiServiceFactory
                .buildClient(gcpClient.generateToken(region));
            final String regionName = regionName(region);
            return loadPrices(requests, cloudbilling, priceApiService, regionName);
        } catch (IOException e) {
            throw new GCPInstancePriceException("GCP machine prices loading has failed.", e);
        }
    }

    private String regionName(final GCPRegion region) {
        return region.getRegionCode().replaceFirst("-\\w$", "");
    }

    private Set<GCPResourcePrice> loadPrices(final List<GCPResourceRequest> requests,
                                             final Cloudbilling cloudbilling,
                                             final GCPPriceApiService priceApiService,
                                             final String regionName) throws IOException {
        String nextPageToken = null;
        List<Sku> publicSKUs = new ArrayList<>();
        while (true) {
            final ListSkusResponse response = cloudbilling.services().skus()
                    .list(COMPUTE_ENGINE_SERVICE_NAME)
                    .setPageToken(nextPageToken)
                    .setPageSize(SKUS_PAGE_SIZE)
                    .execute();
            final List<Sku> currentSkus = Optional.of(response)
                    .map(ListSkusResponse::getSkus)
                    .orElseGet(Collections::emptyList);

            publicSKUs.addAll(currentSkus);
            nextPageToken = response.getNextPageToken();
            if (StringUtils.isBlank(nextPageToken)) {
                break;
            }
        }

        if (StringUtils.isNotBlank(gcpBillingAccountId)) {
            publicSKUs = fetchAccountSpecificSKUs(priceApiService, publicSKUs);
        }

        return publicSKUs.stream()
                .filter(sku -> sku.getDescription() != null)
                .flatMap(sku -> requests.stream()
                        .filter(request -> matches(sku, request, regionName))
                        .map(request -> price(request, sku)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }

    private List<Sku> fetchAccountSpecificSKUs(GCPPriceApiService priceApiService, List<Sku> publicSKUs) {
        final Map<String, Sku> skuMap = publicSKUs.stream()
                .collect(Collectors
                    .toMap(s -> String.format(PRICE_TEMPLATE, gcpBillingAccountId, s.getSkuId()), Function.identity()));
        try {
            String pageToken = "";
            List<Sku> skus = new ArrayList<>();
            do {
                Response<BillingAccountPriceResponse> response = priceApiService
                    .getAllPrices(gcpBillingAccountId, CURRENCY, pageToken, BILLING_ACCOUNT_SKUS_PAGE_SIZE)
                    .execute();
                skus.addAll(
                    response.body().getBillingAccountPrices().stream()
                        .filter(price -> skuMap.containsKey(price.getName()))
                        .map(price -> changePriceOfPublicSKU(price, skuMap.get(price.getName())))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));
                pageToken = Optional.ofNullable(response.body().getNextPageToken()).orElse("");
            } while (!pageToken.isEmpty());
            return skus;
        } catch (IOException e) {
            log.error("Failed to fetch Billing Account Specific SKUs: {}", e.getMessage());
            return publicSKUs;
        }
    }

    private Sku changePriceOfPublicSKU(BillingAccountPrice billingAccountPrice, Sku sku) {
        try {
            com.epam.pipeline.manager.gcp.price.Money
                    newPrice = billingAccountPrice.getRate().getTiers().get(0).getContractPrice();
            Optional.ofNullable(sku.getPricingInfo())
                    .filter(CollectionUtils::isNotEmpty)
                    .map(this::lastElement)
                    .map(PricingInfo::getPricingExpression)
                    .map(PricingExpression::getTieredRates)
                    .filter(CollectionUtils::isNotEmpty)
                    .map(this::firstElement)
                    .map(TierRate::getUnitPrice)
                    .ifPresent(currentPrice -> {
                        currentPrice.setCurrencyCode(newPrice.getCurrency());
                        currentPrice.setUnits(newPrice.getUnits());
                        currentPrice.setNanos(newPrice.getNanos());
                    });
            return sku;
        } catch (Exception e) {
            log.error("Failed to change price for public sku: {}", e.getMessage());
            return sku;
        }

    }

    private boolean matches(final Sku sku, final GCPResourceRequest request, final String region) {
        return sku.getDescription() != null
                && sku.getCategory() != null
                && sku.getServiceRegions() != null
                && sku.getDescription().startsWith(request.getMapping().getPrefix())
                && request.getObject().resourceFamily().equals(sku.getCategory().getResourceFamily())
                && request.getBilling().termType().equals(sku.getCategory().getUsageType())
                && request.getMapping().getGroup().equals(sku.getCategory().getResourceGroup())
                && sku.getServiceRegions().contains(region);
    }

    private Optional<GCPResourcePrice> price(final GCPResourceRequest request, final Sku sku) {
        return Optional.ofNullable(sku.getPricingInfo())
                .filter(CollectionUtils::isNotEmpty)
                .map(this::lastElement)
                .map(PricingInfo::getPricingExpression)
                .map(PricingExpression::getTieredRates)
                .filter(CollectionUtils::isNotEmpty)
                .map(this::firstElement)
                .map(TierRate::getUnitPrice)
                .filter(money -> money.getUnits() != null && money.getNanos() != null)
                .map(money -> request.getType().normalize(money.getUnits() * BILLION + money.getNanos()))
                .map(nanos -> price(request, nanos));
    }

    private <T> T lastElement(final List<T> list) {
        return list.get(list.size() - 1);
    }

    private <T> T firstElement(final List<T> list) {
        return list.get(0);
    }

    private GCPResourcePrice price(final GCPResourceRequest request, final Long nanos) {
        return new GCPResourcePrice(request, nanos);
    }
}
