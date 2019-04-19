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

package com.epam.pipeline.manager.cloud.gcp.resource;

import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import com.epam.pipeline.manager.cloud.gcp.GCPBilling;
import com.epam.pipeline.manager.cloud.gcp.GCPResourcePrice;
import com.epam.pipeline.manager.cloud.gcp.GCPResourceType;
import org.apache.commons.lang3.text.WordUtils;

import java.util.Date;
import java.util.List;

public class GCPDisk extends AbstractGCPObject {

    public GCPDisk(final String name, final String family) {
        super(name, family);
    }

    @Override
    public InstanceOffer toInstanceOffer(final GCPBilling billing, final double pricePerUnit, final Long regionId) {
        return InstanceOffer.builder()
                .termType(billing.termType())
                .tenancy(CloudInstancePriceService.SHARED_TENANCY)
                .productFamily(CloudInstancePriceService.STORAGE_PRODUCT_FAMILY)
                .sku(getName())
                .pricePerUnit(pricePerUnit)
                .priceListPublishDate(new Date())
                .currency(CloudInstancePriceService.CURRENCY)
                .instanceType(getName())
                .regionId(regionId)
                .unit(CloudInstancePriceService.HOURS_UNIT)
                .volumeType(CloudInstancePriceService.GENERAL_PURPOSE_VOLUME_TYPE)
                .operatingSystem("Linux")
                .instanceFamily(WordUtils.capitalizeFully(getFamily()))
                .build();
    }

    @Override
    public boolean isRequired(final GCPResourceType type) {
        return type == GCPResourceType.DISK;
    }

    @Override
    public long totalPrice(final List<GCPResourcePrice> prices) {
        return prices.stream()
                .mapToLong(GCPResourcePrice::getNanos)
                .sum();
    }

    @Override
    public String billingKey(final GCPBilling billing, final GCPResourceType type) {
        return String.format(SHORT_BILLING_KEY_PATTERN, type.alias(), billing.alias());
    }

    @Override
    public String resourceFamily() {
        return "Storage";
    }
}
