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

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.dao.cluster.InstanceOfferDao;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

public class AzureInstancePriceServiceTest extends AbstractSpringTest {

    private static final Long REGION_ID = 1L;
    private static final float PRICE_PER_UNIT1 = 360.0f;
    private static final float PRICE_PER_UNIT2 = 720.0f;
    private static final float SIZE_FOR_P4 = 32;
    private static final float SIZE_FOR_P6 = 64;
    private static final float SIZE_FOR_E8 = 128;
    private static final int INSTANCE_DISK_FOR_64 = 33;
    private static final int INSTANCE_DISK_FOR_32 = 31;
    private static final int INSTANCE_DISK_FOR_128 = 100;
    private static final String INSTANCE_TYPE = "any";
    private static final float PRICE_FOR_P4_DISK = 1f;
    private static final float PRICE_FOR_P6_DISK = 0.5f;
    private static final float PRICE_FOR_E8_DISK = 0.0f;

    @MockBean
    private InstanceOfferDao instanceOfferDao;

    @Autowired
    private AzureInstancePriceService azureInstancePriceService;

    @Test
    public void shouldCalculatePriceForDisk() {

        final AzureRegion region = new AzureRegion();
        region.setId(REGION_ID);
        when(instanceOfferDao.loadInstanceOffers(any()))
                .thenReturn(Collections.singletonList(InstanceOffer.builder()
                        .volumeType("Premium")
                        .build()));

        List<InstanceOffer> diskOffers = Arrays.asList(
                InstanceOffer.builder()
                        .productFamily(CloudInstancePriceService.STORAGE_PRODUCT_FAMILY)
                        .volumeType(CloudInstancePriceService.GENERAL_PURPOSE_VOLUME_TYPE)
                        .regionId(REGION_ID)
                        .memory(SIZE_FOR_P4)
                        .pricePerUnit(PRICE_PER_UNIT1)
                        .sku("P4")
                        .build(),
                InstanceOffer.builder()
                        .productFamily(CloudInstancePriceService.STORAGE_PRODUCT_FAMILY)
                        .volumeType(CloudInstancePriceService.GENERAL_PURPOSE_VOLUME_TYPE)
                        .regionId(REGION_ID)
                        .memory(SIZE_FOR_P6)
                        .pricePerUnit(PRICE_PER_UNIT2)
                        .sku("P6")
                        .build(),
                InstanceOffer.builder()
                        .productFamily(CloudInstancePriceService.STORAGE_PRODUCT_FAMILY)
                        .volumeType(CloudInstancePriceService.GENERAL_PURPOSE_VOLUME_TYPE)
                        .regionId(REGION_ID)
                        .memory(SIZE_FOR_E8)
                        .pricePerUnit(PRICE_PER_UNIT2)
                        .sku("E8")
                        .build());

        double priceForDisk = azureInstancePriceService.getPriceForDisk(diskOffers, INSTANCE_DISK_FOR_64,
                INSTANCE_TYPE, region);
        Assert.assertEquals(PRICE_FOR_P4_DISK, priceForDisk, 0.0);
        priceForDisk = azureInstancePriceService.getPriceForDisk(diskOffers, INSTANCE_DISK_FOR_32,
                INSTANCE_TYPE, region);
        Assert.assertEquals(PRICE_FOR_P6_DISK, priceForDisk, 0.0);
        priceForDisk = azureInstancePriceService.getPriceForDisk(diskOffers, INSTANCE_DISK_FOR_128,
                INSTANCE_TYPE, region);
        Assert.assertEquals(PRICE_FOR_E8_DISK, priceForDisk, 0.0);
    }
}
