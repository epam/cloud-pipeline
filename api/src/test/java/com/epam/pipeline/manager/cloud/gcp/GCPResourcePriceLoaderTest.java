/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.cloud.gcp;

import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.cloud.gcp.resource.GCPMachine;
import com.epam.pipeline.manager.gcp.price.BillingAccountPrice;
import com.epam.pipeline.manager.gcp.price.BillingAccountPriceResponse;
import com.epam.pipeline.manager.gcp.price.GCPPriceApiService;
import com.epam.pipeline.manager.gcp.price.GCPPriceApiServiceFactory;
import com.epam.pipeline.manager.gcp.price.Rate;
import com.epam.pipeline.manager.gcp.price.Tier;
import com.google.api.services.cloudbilling.Cloudbilling;
import com.google.api.services.cloudbilling.model.Category;
import com.google.api.services.cloudbilling.model.ListSkusResponse;
import com.google.api.services.cloudbilling.model.Money;
import com.google.api.services.cloudbilling.model.PricingExpression;
import com.google.api.services.cloudbilling.model.PricingInfo;
import com.google.api.services.cloudbilling.model.Sku;
import com.google.api.services.cloudbilling.model.TierRate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.epam.pipeline.manager.cloud.gcp.GCPBilling.PREEMPTIBLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("checkstyle:magicnumber")
public class GCPResourcePriceLoaderTest {

    // Constants
    private static final String BILLING_ACCOUNT_ID = "billing-account-123";
    private static final String CURRENCY = "USD";
    private static final String SKU_ID = "sku-123";
    private static final String REGION_CODE = "us-west1";
    private static final String PRICE_KEY = "billingAccounts/" + BILLING_ACCOUNT_ID + "/skus/" + SKU_ID + "/price";
    private static final String RESOURCE_FAMILY = "Compute";
    private static final String USAGE_TYPE = "Preemptible";
    private static final String RESOURCE_GROUP = "standard";
    private static final String PREFIX = "Custom Machine";
    public static final String T_2_D_STANDARD_8 = "t2d-standard-8";
    public static final int CPU_8 = 8;
    public static final double RAM_32 = 32.0;
    public static final int GPU_0 = 0;
    public static final int PAGE_SIZE = 5000;

    // Mocks
    @Mock
    private GCPClient gcpClient;
    @Mock
    private Cloudbilling cloudbilling;
    @Mock
    private Cloudbilling.Services services;
    @Mock
    private Cloudbilling.Services.Skus skus;
    @Mock
    private Cloudbilling.Services.Skus.List listRequest;
    @Mock
    private GCPPriceApiServiceFactory gcpPriceApiServiceFactory;
    @Mock
    private GCPPriceApiService priceApiService;
    @Mock
    private Call<BillingAccountPriceResponse> call;

    // Instance under test
    @InjectMocks
    private GCPResourcePriceLoader priceLoader;

    // Test data
    private GCPRegion region;
    private Sku publicSku;
    private BillingAccountPrice billingPrice;
    private List<GCPResourceRequest> requests;

    @Before
    public void setUp() throws IOException {
        // Initialize test data
        setupRegion();
        setupPublicSku();
        setupBillingPrice();
        setupRequests();

        // Configure mocks
        setupGcpClientMocks();
        setupCloudBillingMocks();
        setupPriceApiMocks();
    }

    @Test
    public void shouldLoadPricesWithAccountSpecificSKUs() throws IOException {
        ListSkusResponse skuResponse = new ListSkusResponse().setSkus(Collections.singletonList(publicSku));
        BillingAccountPriceResponse priceResponseBody = new BillingAccountPriceResponse();
        priceResponseBody.setBillingAccountPrices(Collections.singletonList(billingPrice));
        Response<BillingAccountPriceResponse> response = Response.success(priceResponseBody);

        ReflectionTestUtils.setField(priceLoader, "gcpBillingAccountId", BILLING_ACCOUNT_ID);

        when(listRequest.execute()).thenReturn(skuResponse).thenReturn(new ListSkusResponse());
        when(priceApiService.getAllPrices(BILLING_ACCOUNT_ID, CURRENCY, "", PAGE_SIZE)).thenReturn(call);
        when(call.execute()).thenReturn(response);

        Set<GCPResourcePrice> result = priceLoader.load(region, requests);

        assertThat(result).hasSize(1);
        GCPResourcePrice price = result.iterator().next();
        assertThat(price.getNanos()).isEqualTo(2_750_000_000L);
    }

    @Test
    public void shouldLoadPublicSKUsWhenBillingAccountIdIsNull() throws IOException {
        ListSkusResponse skuResponse = new ListSkusResponse().setSkus(Collections.singletonList(publicSku));

        when(listRequest.execute()).thenReturn(skuResponse).thenReturn(new ListSkusResponse());

        Set<GCPResourcePrice> result = priceLoader.load(region, requests);

        assertThat(result).hasSize(1);
        GCPResourcePrice price = result.iterator().next();
        assertThat(price.getNanos()).isEqualTo(1_500_000_000L); // 1 unit + 500M nanos
    }

    @Test
    public void shouldLoadNoPricesWhenNoBillingPriceMatches() throws IOException {
        BillingAccountPrice nonMatchingPrice = new BillingAccountPrice();
        nonMatchingPrice.setName("non-matching-key");
        BillingAccountPriceResponse priceResponseBody = new BillingAccountPriceResponse();
        priceResponseBody.setBillingAccountPrices(Collections.singletonList(nonMatchingPrice));
        Response<BillingAccountPriceResponse> response = Response.success(priceResponseBody);
        ReflectionTestUtils.setField(priceLoader, "gcpBillingAccountId", BILLING_ACCOUNT_ID);

        when(listRequest.execute()).thenReturn(new ListSkusResponse().setSkus(Collections.singletonList(publicSku)))
                .thenReturn(new ListSkusResponse());
        when(priceApiService.getAllPrices(BILLING_ACCOUNT_ID, CURRENCY, "", PAGE_SIZE)).thenReturn(call);
        when(call.execute()).thenReturn(response);

        Set<GCPResourcePrice> result = priceLoader.load(region, requests);

        assertThat(result).isEmpty();
    }

    @Test
    public void shouldLoadPublicSKUsWhenIOExceptionOccurs() throws IOException {
        ListSkusResponse skuResponse = new ListSkusResponse().setSkus(Collections.singletonList(publicSku));

        when(listRequest.execute()).thenReturn(skuResponse).thenReturn(new ListSkusResponse());
        when(priceApiService.getAllPrices(anyString(), anyString(), anyString(), anyInt())).thenReturn(call);
        when(call.execute()).thenThrow(new IOException("API error"));

        Set<GCPResourcePrice> result = priceLoader.load(region, requests);

        assertThat(result).hasSize(1);
        GCPResourcePrice price = result.iterator().next();
        assertThat(price.getNanos()).isEqualTo(1_500_000_000L); // 1 unit + 500M nanos
    }

    private void setupRegion() {
        region = new GCPRegion();
        region.setRegionCode(REGION_CODE);
    }

    private void setupPublicSku() {
        publicSku = new Sku();
        publicSku.setSkuId(SKU_ID);
        publicSku.setDescription(PREFIX + " Instance");
        publicSku.setServiceRegions(Collections.singletonList(REGION_CODE));
        Category category = new Category();
        category.setResourceFamily(RESOURCE_FAMILY).setUsageType(USAGE_TYPE).setResourceGroup(RESOURCE_GROUP);
        publicSku.setCategory(category);
        PricingInfo pricingInfo = new PricingInfo();
        PricingExpression pricingExpression = new PricingExpression();
        TierRate tierRate = new TierRate();
        Money money = new Money();
        money.setCurrencyCode("USD").setUnits(1L).setNanos(500_000_000);
        tierRate.setUnitPrice(money);
        pricingExpression.setTieredRates(Collections.singletonList(tierRate));
        pricingInfo.setPricingExpression(pricingExpression);
        publicSku.setPricingInfo(Collections.singletonList(pricingInfo));
    }

    private void setupBillingPrice() {
        billingPrice = new BillingAccountPrice();
        billingPrice.setName(PRICE_KEY);
        com.epam.pipeline.manager.gcp.price.Money newPrice = new com.epam.pipeline.manager.gcp.price.Money();
        newPrice.setCurrency("USD");
        newPrice.setUnits(2L);
        newPrice.setNanos(750_000_000);
        Tier tier = new Tier();
        tier.setContractPrice(newPrice);
        Rate rate = new Rate();
        rate.setTiers(Collections.singletonList(tier));
        billingPrice.setRate(rate);
    }

    private void setupRequests() {
        GCPResourceMapping gcpResourceMapping = new GCPResourceMapping(PREFIX, RESOURCE_GROUP);
        GCPMachine gcpMachine = new GCPMachine(T_2_D_STANDARD_8, RESOURCE_GROUP, CPU_8, RAM_32,
                RAM_32, GPU_0, null, null);
        GCPResourceRequest gcpResourceRequest = new GCPResourceRequest(GCPResourceType.CPU, PREEMPTIBLE,
                gcpMachine, gcpResourceMapping);
        requests = Collections.singletonList(gcpResourceRequest);
    }

    private void setupGcpClientMocks() throws IOException {
        when(gcpClient.buildBillingClient(region)).thenReturn(cloudbilling);
        when(gcpClient.generateToken(region)).thenReturn("token");
    }

    private void setupCloudBillingMocks() throws IOException {
        when(cloudbilling.services()).thenReturn(services);
        when(services.skus()).thenReturn(skus);
        when(skus.list(anyString())).thenReturn(listRequest);
        when(listRequest.setPageToken(any())).thenReturn(listRequest);
        when(listRequest.setPageSize(anyInt())).thenReturn(listRequest);
    }

    private void setupPriceApiMocks() {
        when(gcpPriceApiServiceFactory.buildClient(anyString())).thenReturn(priceApiService);
    }
}