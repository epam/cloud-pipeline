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
import com.epam.pipeline.entity.pricing.azure.AzurePricingMeter;
import com.epam.pipeline.entity.pricing.azure.AzureRateCardPricingMeter;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import com.microsoft.azure.management.compute.ResourceSkuCapabilities;
import com.microsoft.azure.management.compute.implementation.ResourceSkuInner;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.epam.pipeline.manager.cloud.CloudInstancePriceService.HOURS_UNIT;
import static org.junit.Assert.assertEquals;

public class AzurePriceListLoaderTest {

    private static final String ANY = "any";
    private static final float PRICE = 5f;
    private static final Long REGION_ID = 1L;
    private static final String CURRENCY = "USD";
    private static final String LINUX_OS = "Linux";
    private static final String VIRTUAL_MACHINES_CATEGORY = "Virtual Machines";
    private static final String DISKS_CATEGORY = "Storage";
    private static final String METER_REGION = "US East 2";
    private static final String DISK_UNIT = "1/Month";
    private static final String DISK_UNIT_2 = "10/Month";
    private static final String VM_METER_SUB_CATEGORY = "Dv2/DSv2 Series";
    private static final String VM_METER_NAME = "D13 v2/DS13 v2";
    private static final String VM_TYPE = "standardd13v2";
    private static final String VM_BASIC_TYPE = "basica0";
    private static final String VM_FAMILY = "General purpose";
    private static final String PREMIUM_DISK_METER_SUB_CATEGORY = "Premium SSD Managed Disks";
    private static final String DISK_NAME = "P6";
    private static final float DISK_SIZE = 64.0f;
    private static final float OS_DISK_SIZE = 56.0f;
    private static final String RC_HOURS_UNIT = "1 Hour";
    private static final String HOURS_UNIT_2 = "10 Hour";
    private static final String V_CPUS = "vCPUs";
    private static final String PREMIUM_IO = "PremiumIO";
    private static final String GPUS = "GPUs";
    private static final String MEMORY_GB = "MemoryGB";
    private static final String VM_FAMILY_1 = "standardDSv2Family";
    private static final String VM_FAMILY_2 = "basicAFamily";

    @Test
    public void rateCardShouldCalculateInstancePrices() {
        final List<AzurePricingMeter> prices = Arrays.asList(
                AzureRateCardPricingMeter.builder()
                        .meterCategory(VIRTUAL_MACHINES_CATEGORY)
                        .meterId(ANY)
                        .meterRates(Collections.singletonMap("0", PRICE))
                        .meterSubCategory(VM_METER_SUB_CATEGORY)
                        .meterName(VM_METER_NAME)
                        .unit(RC_HOURS_UNIT)
                        .meterRegion(METER_REGION)
                        .build(),
                AzureRateCardPricingMeter.builder()
                        .meterCategory(DISKS_CATEGORY)
                        .meterId(ANY)
                        .meterRates(Collections.singletonMap("0", PRICE))
                        .meterSubCategory(PREMIUM_DISK_METER_SUB_CATEGORY)
                        .meterName(DISK_NAME + " Disks")
                        .meterRegion(METER_REGION)
                        .unit(DISK_UNIT)
                        .build()
        );

        final List<ResourceSkuCapabilities> vmCapabilities1 = Arrays.asList(
                createResourceSkuCapabilities(MEMORY_GB, "56"),
                createResourceSkuCapabilities(V_CPUS, "8"),
                createResourceSkuCapabilities(GPUS, "0"),
                createResourceSkuCapabilities(PREMIUM_IO, "True"));
        final ResourceSkuInner vmSku1 = createVirtualMachinesSku(VM_TYPE, VM_FAMILY_1, vmCapabilities1);

        // should be filtered cause it does not match prices list
        final List<ResourceSkuCapabilities> vmCapabilities2 = Arrays.asList(
                createResourceSkuCapabilities(MEMORY_GB, "0.75"),
                createResourceSkuCapabilities(V_CPUS, "1"),
                createResourceSkuCapabilities(GPUS, "0"),
                createResourceSkuCapabilities(PREMIUM_IO, "False"));
        final ResourceSkuInner vmSku2 = createVirtualMachinesSku(VM_BASIC_TYPE, VM_FAMILY_2, vmCapabilities2);

        final Map<String, ResourceSkuInner> vmSkus = new HashMap<>();
        vmSkus.put(vmSku1.name(), vmSku1);
        vmSkus.put(vmSku2.name(), vmSku2);

        final List<ResourceSkuCapabilities> diskCapabilities1 = Collections.singletonList(
                createResourceSkuCapabilities("MaxSizeGiB", "64"));
        final ResourceSkuInner diskSku1 = createDiskSku(DISK_NAME, diskCapabilities1);

        final Map<String, ResourceSkuInner> diskSkus = new HashMap<>();
        diskSkus.put(diskSku1.size(), diskSku1);

        final AzureRateCardPriceListLoader loader = new AzureRateCardPriceListLoader(ANY, ANY, "",
                "https://any.com/");
        final List<InstanceOffer> actualOffers = loader.mergeSkusWithPrices(prices, vmSkus, diskSkus,
                METER_REGION, REGION_ID);

        final Map<String, InstanceOffer> expectedOffers = new HashMap<>();
        expectedOffers.put(CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY, InstanceOffer.builder()
                .termType(CloudInstancePriceService.TermType.ON_DEMAND.getName())
                .tenancy(CloudInstancePriceService.SHARED_TENANCY)
                .productFamily(CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY)
                .sku(ANY)
                .currency(CURRENCY)
                .instanceType(VM_TYPE)
                .pricePerUnit(PRICE)
                .regionId(REGION_ID)
                .unit(HOURS_UNIT)
                .volumeType("Premium")
                .operatingSystem(LINUX_OS)
                .instanceFamily(VM_FAMILY)
                .vCPU(8)
                .gpu(0)
                .memory(OS_DISK_SIZE)
                .build());
        expectedOffers.put(CloudInstancePriceService.STORAGE_PRODUCT_FAMILY, InstanceOffer.builder()
                .tenancy(CloudInstancePriceService.SHARED_TENANCY)
                .productFamily(CloudInstancePriceService.STORAGE_PRODUCT_FAMILY)
                .sku(DISK_NAME)
                .currency(CURRENCY)
                .pricePerUnit(PRICE)
                .regionId(REGION_ID)
                .volumeType(CloudInstancePriceService.GENERAL_PURPOSE_VOLUME_TYPE)
                .memory(DISK_SIZE)
                .unit(DISK_UNIT)
                .build());

        assertEquals(2, actualOffers.size());
        assertOffers(expectedOffers, actualOffers);
    }

    @Test
    public void eaPriceLoaderShouldCalculateInstancePrices() {
        final List<AzureEAPricingMeter> prices = Arrays.asList(
                AzureEAPricingMeter.builder()
                    .meterRegion(METER_REGION)
                    .meterSubCategory(VM_METER_SUB_CATEGORY)
                    .meterCategory(VIRTUAL_MACHINES_CATEGORY)
                    .meterName(VM_METER_NAME)
                    .unit("1 Hour")
                    .unitPrice(PRICE)
                    .meterId(ANY)
                    .build(),
                AzureEAPricingMeter.builder()
                    .meterId(ANY)
                    .unitPrice(PRICE)
                    .unit(DISK_UNIT)
                    .meterCategory(DISKS_CATEGORY)
                    .meterSubCategory(PREMIUM_DISK_METER_SUB_CATEGORY)
                    .meterName(DISK_NAME + " Disks")
                    .meterRegion(METER_REGION)
                    .unit(DISK_UNIT)
                    .build()
        );

        final List<ResourceSkuCapabilities> vmCapabilities1 = Arrays.asList(
                createResourceSkuCapabilities(MEMORY_GB, "56"),
                createResourceSkuCapabilities(V_CPUS, "8"),
                createResourceSkuCapabilities(GPUS, "0"),
                createResourceSkuCapabilities(PREMIUM_IO, "True"));
        final ResourceSkuInner vmSku1 = createVirtualMachinesSku(VM_TYPE, VM_FAMILY_1, vmCapabilities1);

        // should be filtered cause it does not match prices list
        final List<ResourceSkuCapabilities> vmCapabilities2 = Arrays.asList(
                createResourceSkuCapabilities(MEMORY_GB, "0.75"),
                createResourceSkuCapabilities(V_CPUS, "1"),
                createResourceSkuCapabilities(GPUS, "0"),
                createResourceSkuCapabilities(PREMIUM_IO, "False"));
        final ResourceSkuInner vmSku2 = createVirtualMachinesSku(VM_BASIC_TYPE, VM_FAMILY_2, vmCapabilities2);

        final Map<String, ResourceSkuInner> vmSkus = new HashMap<>();
        vmSkus.put(vmSku1.name(), vmSku1);
        vmSkus.put(vmSku2.name(), vmSku2);

        final List<ResourceSkuCapabilities> diskCapabilities1 = Collections.singletonList(
                createResourceSkuCapabilities("MaxSizeGiB", "64"));
        final ResourceSkuInner diskSku1 = createDiskSku(DISK_NAME, diskCapabilities1);

        final Map<String, ResourceSkuInner> diskSkus = new HashMap<>();
        diskSkus.put(diskSku1.size(), diskSku1);

        final AzureEAPriceListLoader loader = new AzureEAPriceListLoader(ANY, "",
                "https://any.com/");
        final List<InstanceOffer> actualOffers = loader.mergeSkusWithPrices(prices, vmSkus, diskSkus,
                METER_REGION, REGION_ID);

        final Map<String, InstanceOffer> expectedOffers = new HashMap<>();
        expectedOffers.put(CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY, InstanceOffer.builder()
                .termType(CloudInstancePriceService.TermType.ON_DEMAND.getName())
                .tenancy(CloudInstancePriceService.SHARED_TENANCY)
                .productFamily(CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY)
                .sku(ANY)
                .currency(CURRENCY)
                .instanceType(VM_TYPE)
                .pricePerUnit(PRICE)
                .regionId(REGION_ID)
                .unit(HOURS_UNIT)
                .volumeType("Premium")
                .operatingSystem(LINUX_OS)
                .instanceFamily(VM_FAMILY)
                .vCPU(8)
                .gpu(0)
                .memory(OS_DISK_SIZE)
                .build());
        expectedOffers.put(CloudInstancePriceService.STORAGE_PRODUCT_FAMILY, InstanceOffer.builder()
                .tenancy(CloudInstancePriceService.SHARED_TENANCY)
                .productFamily(CloudInstancePriceService.STORAGE_PRODUCT_FAMILY)
                .sku(DISK_NAME)
                .currency(CURRENCY)
                .pricePerUnit(PRICE)
                .regionId(REGION_ID)
                .volumeType(CloudInstancePriceService.GENERAL_PURPOSE_VOLUME_TYPE)
                .memory(DISK_SIZE)
                .unit(DISK_UNIT)
                .build());

        assertEquals(2, actualOffers.size());
        assertOffers(expectedOffers, actualOffers);
    }

    @Test
    public void eaPriceLoaderShouldCalculateInstancePricesWithNonStandardPriceUnit() {
        final List<AzureEAPricingMeter> prices = Arrays.asList(
                AzureEAPricingMeter.builder()
                        .meterRegion(METER_REGION)
                        .meterSubCategory(VM_METER_SUB_CATEGORY)
                        .meterCategory(VIRTUAL_MACHINES_CATEGORY)
                        .meterName(VM_METER_NAME)
                        .unit(HOURS_UNIT_2)
                        .unitPrice(PRICE)
                        .meterId(ANY)
                        .build(),
                AzureEAPricingMeter.builder()
                        .meterId(ANY)
                        .unitPrice(PRICE)
                        .unit(DISK_UNIT_2)
                        .meterCategory(DISKS_CATEGORY)
                        .meterSubCategory(PREMIUM_DISK_METER_SUB_CATEGORY)
                        .meterName(DISK_NAME + " Disks")
                        .meterRegion(METER_REGION)
                        .unit(DISK_UNIT_2)
                        .build()
        );

        final List<ResourceSkuCapabilities> vmCapabilities1 = Arrays.asList(
                createResourceSkuCapabilities(MEMORY_GB, "56"),
                createResourceSkuCapabilities(V_CPUS, "8"),
                createResourceSkuCapabilities(GPUS, "0"),
                createResourceSkuCapabilities(PREMIUM_IO, "True"));
        final ResourceSkuInner vmSku1 = createVirtualMachinesSku(VM_TYPE, VM_FAMILY_1, vmCapabilities1);

        // should be filtered cause it does not match prices list
        final List<ResourceSkuCapabilities> vmCapabilities2 = Arrays.asList(
                createResourceSkuCapabilities(MEMORY_GB, "0.75"),
                createResourceSkuCapabilities(V_CPUS, "1"),
                createResourceSkuCapabilities(GPUS, "0"),
                createResourceSkuCapabilities(PREMIUM_IO, "False"));
        final ResourceSkuInner vmSku2 = createVirtualMachinesSku(VM_BASIC_TYPE, VM_FAMILY_2, vmCapabilities2);

        final Map<String, ResourceSkuInner> vmSkus = new HashMap<>();
        vmSkus.put(vmSku1.name(), vmSku1);
        vmSkus.put(vmSku2.name(), vmSku2);

        final List<ResourceSkuCapabilities> diskCapabilities1 = Collections.singletonList(
                createResourceSkuCapabilities("MaxSizeGiB", "64"));
        final ResourceSkuInner diskSku1 = createDiskSku(DISK_NAME, diskCapabilities1);

        final Map<String, ResourceSkuInner> diskSkus = new HashMap<>();
        diskSkus.put(diskSku1.size(), diskSku1);

        final AzureEAPriceListLoader loader = new AzureEAPriceListLoader(ANY, "",
                "https://any.com/");
        final List<InstanceOffer> actualOffers = loader.mergeSkusWithPrices(prices, vmSkus, diskSkus,
                METER_REGION, REGION_ID);

        final Map<String, InstanceOffer> expectedOffers = new HashMap<>();
        expectedOffers.put(CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY, InstanceOffer.builder()
                .termType(CloudInstancePriceService.TermType.ON_DEMAND.getName())
                .tenancy(CloudInstancePriceService.SHARED_TENANCY)
                .productFamily(CloudInstancePriceService.INSTANCE_PRODUCT_FAMILY)
                .sku(ANY)
                .currency(CURRENCY)
                .instanceType(VM_TYPE)
                .pricePerUnit(PRICE / 10)
                .regionId(REGION_ID)
                .unit(HOURS_UNIT)
                .volumeType("Premium")
                .operatingSystem(LINUX_OS)
                .instanceFamily(VM_FAMILY)
                .vCPU(8)
                .gpu(0)
                .memory(OS_DISK_SIZE)
                .build());
        expectedOffers.put(CloudInstancePriceService.STORAGE_PRODUCT_FAMILY, InstanceOffer.builder()
                .tenancy(CloudInstancePriceService.SHARED_TENANCY)
                .productFamily(CloudInstancePriceService.STORAGE_PRODUCT_FAMILY)
                .sku(DISK_NAME)
                .currency(CURRENCY)
                .pricePerUnit(PRICE / 10)
                .regionId(REGION_ID)
                .volumeType(CloudInstancePriceService.GENERAL_PURPOSE_VOLUME_TYPE)
                .memory(DISK_SIZE)
                .unit(DISK_UNIT)
                .build());

        assertEquals(2, actualOffers.size());
        assertOffers(expectedOffers, actualOffers);
    }

    private void assertOffers(final Map<String, InstanceOffer> expectedOffers, final List<InstanceOffer> actualOffers) {
        actualOffers.forEach(offer -> assertOffer(expectedOffers.get(offer.getProductFamily()), offer));
    }

    private void assertOffer(final InstanceOffer expectedOffer, final InstanceOffer actualOffer) {
        assertEquals(expectedOffer.getTermType(), actualOffer.getTermType());
        assertEquals(expectedOffer.getTenancy(), actualOffer.getTenancy());
        assertEquals(expectedOffer.getSku(), actualOffer.getSku());
        assertEquals(expectedOffer.getCurrency(), actualOffer.getCurrency());
        assertEquals(expectedOffer.getInstanceType(), actualOffer.getInstanceType());
        assertEquals(expectedOffer.getPricePerUnit(), actualOffer.getPricePerUnit(), 0.0);
        assertEquals(expectedOffer.getRegionId(), actualOffer.getRegionId());
        assertEquals(expectedOffer.getUnit(), actualOffer.getUnit());
        assertEquals(expectedOffer.getVolumeType(), actualOffer.getVolumeType());
        assertEquals(expectedOffer.getOperatingSystem(), actualOffer.getOperatingSystem());
        assertEquals(expectedOffer.getInstanceFamily(), actualOffer.getInstanceFamily());
        assertEquals(expectedOffer.getVCPU(), actualOffer.getVCPU());
        assertEquals(expectedOffer.getGpu(), actualOffer.getGpu());
        assertEquals(expectedOffer.getMemory(), actualOffer.getMemory(), 0.0);
    }

    private ResourceSkuInner createVirtualMachinesSku(final String name, final String family,
                                                      final List<ResourceSkuCapabilities> capabilities) {
        final ResourceSkuInner vmSku = new ResourceSkuInner();
        ReflectionTestUtils.setField(vmSku, "resourceType", "virtualMachines");
        ReflectionTestUtils.setField(vmSku, "name", name);
        ReflectionTestUtils.setField(vmSku, "family", family);
        ReflectionTestUtils.setField(vmSku, "capabilities", capabilities);
        return vmSku;
    }

    private ResourceSkuCapabilities createResourceSkuCapabilities(final String name, final String value) {
        final ResourceSkuCapabilities capability = new ResourceSkuCapabilities();
        ReflectionTestUtils.setField(capability, "name", name);
        ReflectionTestUtils.setField(capability, "value", value);
        return capability;
    }

    private ResourceSkuInner createDiskSku(final String size, final List<ResourceSkuCapabilities> capabilities) {
        final ResourceSkuInner vmSku = new ResourceSkuInner();
        ReflectionTestUtils.setField(vmSku, "resourceType", "disks");
        ReflectionTestUtils.setField(vmSku, "size", size);
        ReflectionTestUtils.setField(vmSku, "capabilities", capabilities);
        return vmSku;
    }
}
