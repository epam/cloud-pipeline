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
import com.epam.pipeline.billingreportagent.model.pricing.AzurePricingEntity;
import com.epam.pipeline.entity.region.AzureRegion;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import retrofit2.Response;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AzureEARawPriceLoader extends AzureAbstractRawPriceLoader {

    private static final String API_VERSION = "2019-10-01";

    @Autowired
    public AzureEARawPriceLoader(final @Value("${sync.storage.azure.price.retention.minutes:30}")
                                   Long retentionTimeoutMinutes) {
        super(retentionTimeoutMinutes);
    }

    protected List<AzurePricingEntity> getAzurePricing(final AzureRegion region) throws IOException {
        final ApplicationTokenCredentials credentials = getCredentialsFromFile(region.getAuthFile());
        return getPriceSheet(region, credentials);
    }

    private List<AzurePricingEntity> getPriceSheet(final AzureRegion region, ApplicationTokenCredentials credentials)
            throws IOException {
        return getPriceSheet(region, credentials, new ArrayList<>(), null)
                    .stream()
                    .filter(details -> region.getMeterRegionName().equals(details.getMeterRegion()))
                    .map(meter -> AzurePricingEntity.builder()
                            .meterCategory(meter.getMeterCategory())
                            .meterSubCategory(meter.getMeterSubCategory())
                            .meterName(meter.getMeterName())
                            .meterRegion(meter.getMeterRegion())
                            .meterRates(Collections.singletonMap("0", meter.getUnitPrice()))
                            .unit(meter.getUnit())
                            .build())
                    .collect(Collectors.toList());

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

}
