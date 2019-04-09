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
import com.epam.pipeline.manager.cloud.gcp.GCPBilling;
import com.epam.pipeline.manager.cloud.gcp.GCPResourcePrice;
import com.epam.pipeline.manager.cloud.gcp.GCPResourceType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Google Cloud Provider billable object.
 */
@RequiredArgsConstructor
@Getter
@EqualsAndHashCode
public abstract class AbstractGCPObject {
    protected static final String BILLING_KEY_PATTERN = "%s_%s_%s";
    protected static final String SHORT_BILLING_KEY_PATTERN = "%s_%s";

    private final String name;
    private final String family;

    public abstract InstanceOffer toInstanceOffer(GCPBilling billing, double pricePerUnit, Long regionId);

    public abstract boolean isRequired(GCPResourceType type);

    public abstract String billingKey(GCPBilling billing, GCPResourceType type);

    public abstract long totalPrice(List<GCPResourcePrice> prices);

}
