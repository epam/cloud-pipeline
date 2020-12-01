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
import com.epam.pipeline.entity.pricing.azure.AzureEAPricingMeter;
import com.epam.pipeline.entity.pricing.azure.AzureEAPricingResult;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.microsoft.azure.credentials.AzureTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.implementation.ResourceSkuInner;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.epam.pipeline.manager.cloud.azure.AzurePricingClient.executeRequest;

@Slf4j
public class AzureEAPriceListLoader extends AbstractAzurePriceListLoader {

    private static final String API_VERSION = "2019-10-01";
    private static final int BATCH_SIZE = 10000;


    public AzureEAPriceListLoader(final String authPath, final String meterRegionName, final String azureApiUrl) {
        super(meterRegionName, azureApiUrl, authPath);
    }

    protected List<InstanceOffer> getInstanceOffers(final AbstractCloudRegion region,
                                                    final AzureTokenCredentials credentials,
                                                    final Azure client,
                                                    final Map<String, ResourceSkuInner> vmSkusByName,
                                                    final Map<String, ResourceSkuInner> diskSkusByName)
                                                    throws IOException {
        final Optional<AzureEAPricingResult> prices = getPricing(client.subscriptionId(), credentials);
        return prices.filter(p -> CollectionUtils.isNotEmpty(p.getProperties().getPricesheets()))
                .map(p -> mergeSkusWithPrices(p.getProperties().getPricesheets(), vmSkusByName, diskSkusByName,
                        meterRegionName, region.getId()))
                .orElseGet(() -> getOffersFromSku(vmSkusByName, diskSkusByName, region.getId()));
    }

    @Override
    public String getAPIVersion() {
        return API_VERSION;
    }



    private Optional<AzureEAPricingResult> getPricing(final String subscription,
                                                      final AzureTokenCredentials credentials) throws IOException {
        Assert.isTrue(StringUtils.isNotBlank(subscription), "Could not find subscription ID");
        return Optional.of(getPriceSheet(subscription, credentials));
    }

    private AzureEAPricingResult getPriceSheet(final String subscription, final AzureTokenCredentials credentials)
            throws IOException {
        return AzureEAPricingResult.builder().properties(
                AzureEAPricingResult.PricingProperties.builder().pricesheets(
                    getPriceSheet(new ArrayList<>(), subscription, credentials, null)
                            .stream()
                            .filter(details -> meterRegionName.equals(details.getMeterRegion()))
                            .collect(Collectors.toList())
                ).build()
        ).build();
    }

    private List<AzureEAPricingMeter> getPriceSheet(final List<AzureEAPricingMeter> buffer, final String subscription,
                                                    final AzureTokenCredentials credentials, String skiptoken
    ) throws IOException {
        final String token = credentials.getToken(azureApiUrl);
        Assert.isTrue(StringUtils.isNotBlank(token), "Could not find access token");
        final AzureEAPricingResult meterDetails = executeRequest(azurePricingClient.getPricesheet(
                "Bearer " + token, subscription, getAPIVersion(), "meterDetails", BATCH_SIZE, skiptoken));
        if (meterDetails != null && meterDetails.getProperties() != null) {
            buffer.addAll(meterDetails.getProperties().getPricesheets());
            if (StringUtils.isNotBlank(meterDetails.getProperties().getNextLink())) {
                Pattern pattern = Pattern.compile(".*skiptoken=([^&]+).*");
                Matcher matcher = pattern.matcher(meterDetails.getProperties().getNextLink());
                if (matcher.matches()) {
                    skiptoken = matcher.group(1);
                    return getPriceSheet(buffer, subscription, credentials, skiptoken);
                }
            }
        }
        return buffer;
    }

}
