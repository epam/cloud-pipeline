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
import com.epam.pipeline.entity.pricing.azure.AzureRateCardPricingResult;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import com.microsoft.azure.credentials.AzureTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.implementation.ResourceSkuInner;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static com.epam.pipeline.manager.cloud.azure.AzurePricingClient.executeRequest;

@Slf4j
public class AzureRateCardPriceListLoader extends AbstractAzurePriceListLoader {

    private static final String API_VERSION = "2016-08-31-preview";

    private final String offerId;

    public AzureRateCardPriceListLoader(final String authPath, final String offerId, final String meterRegionName,
                                        final String azureApiUrl) {
        super(meterRegionName, azureApiUrl, authPath);
        this.offerId = offerId;
    }

    @Override
    protected List<InstanceOffer> getInstanceOffers(final AbstractCloudRegion region,
                                                    final AzureTokenCredentials credentials,
                                                    final Azure client,
                                                    final Map<String, ResourceSkuInner> vmSkusByName,
                                                    final Map<String, ResourceSkuInner> diskSkusByName)
                                                    throws IOException {
        final Optional<AzureRateCardPricingResult> prices = getPricing(client.subscriptionId(), credentials);
        return prices.filter(p -> CollectionUtils.isNotEmpty(p.getMeters()))
                .map(p -> mergeSkusWithPrices(p.getMeters(), vmSkusByName, diskSkusByName,
                        meterRegionName, region.getId()))
                .orElseGet(() -> getOffersFromSku(vmSkusByName, diskSkusByName, region.getId()));
    }

    @Override
    public String getAPIVersion() {
        return API_VERSION;
    }

    private Optional<AzureRateCardPricingResult> getPricing(final String subscription,
                                                            final AzureTokenCredentials credentials)
                                                            throws IOException {
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



}
