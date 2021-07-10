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

import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.model.billing.StoragePricing;
import com.epam.pipeline.billingreportagent.service.impl.loader.CloudRegionLoader;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.GCPRegion;
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
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class GcpStoragePriceListLoader implements StoragePriceListLoader{

    private static final String CP_REGION_CREDS_PATH_TEMPLATE = "/root/.cloud/regioncreds/%d";
    private static final String GCP_STORAGE_SERVICES_FAMILY = "Cloud Storage";
    private static final List<String> BILLING_SCOPES =
            Collections.singletonList("https://www.googleapis.com/auth/cloud-platform");
    private static final List<String> SUPPORTED_STORAGE = Arrays.asList("RegionalStorage", "MultiRegionalStorage");
    private final CloudRegionLoader cloudRegionLoader;

    public GcpStoragePriceListLoader(final CloudRegionLoader cloudRegionLoader) {
        this.cloudRegionLoader = cloudRegionLoader;
    }

    @Override
    public Map<String, StoragePricing> loadFullPriceList() throws IOException, GeneralSecurityException {
        final GoogleCredential googleCredential = cloudRegionLoader.loadAllEntities().stream()
            .map(EntityContainer::getEntity)
            .filter(region -> CloudProvider.GCP.equals(region.getProvider()))
            .map(GCPRegion.class::cast)
            .filter(region -> region.getAuthFile() != null)
            .findAny()
            .map(this::getCredentialsForRegion)
            .orElse(null);
        return loadPriceListWithCredentials(googleCredential != null
                                            ? googleCredential
                                            : GoogleCredential.getApplicationDefault());
    }

    private GoogleCredential getCredentialsForRegion(final GCPRegion region) {
        final String secretPath = String.format(CP_REGION_CREDS_PATH_TEMPLATE, region.getId());
        if (Files.exists(Paths.get(secretPath))) {
            try {
                final String regionSecret = FileUtils.readFileToString(new File(secretPath), StandardCharsets.UTF_8);
                if (regionSecret != null) {
                    try (InputStream is = new ByteArrayInputStream(Base64.decodeBase64(regionSecret))) {
                        return GoogleCredential.fromStream(is);
                    }
                }
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        }
        return null;
    }

    private Map<String, StoragePricing> loadPriceListWithCredentials(final GoogleCredential credentials)
        throws IOException, GeneralSecurityException {
        final Cloudbilling cloudBilling =
            new Cloudbilling.Builder(GoogleNetHttpTransport.newTrustedTransport(),
                                     JacksonFactory.getDefaultInstance(), credentials.createScoped(BILLING_SCOPES))
                .setApplicationName("Cloud Pipeline Billing Agent")
                .build();
        final ListServicesResponse services = cloudBilling.services().list().execute();
        final Service cloudStorageService = services.getServices().stream()
            .filter(service -> service.getDisplayName().equals(GCP_STORAGE_SERVICES_FAMILY))
            .findAny()
            .orElseThrow(() -> new IllegalStateException("No services received from GCP!"));

        final ListSkusResponse skuResponse = cloudBilling.services()
            .skus()
            .list(cloudStorageService.getName())
            .execute();

        return skuResponse.getSkus().stream()
            .filter(sku -> SUPPORTED_STORAGE.contains(sku.getCategory().getResourceGroup()))
            .map(this::convertSku)
            .map(Map::entrySet)
            .flatMap(Set::stream)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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
            pricingRanges.addPrice(new StoragePricing.StoragePricingEntity(startRange, endRange, priceCentsPerGb));
        }
        return pricingRanges;
    }
}
