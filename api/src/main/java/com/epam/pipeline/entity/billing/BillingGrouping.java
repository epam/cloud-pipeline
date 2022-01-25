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

package com.epam.pipeline.entity.billing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BillingGrouping {

    RESOURCE_TYPE(BillingGroupingType.COMMON, "resource_type", false, false),
    RUN_INSTANCE_TYPE(BillingGroupingType.RUN, "instance_type", true, false),
    RUN_COMPUTE_TYPE(BillingGroupingType.RUN, "compute_type", false, false),
    PIPELINE(BillingGroupingType.RUN, "pipeline", true, false),
    TOOL(BillingGroupingType.RUN, "tool", true, false),
    STORAGE(BillingGroupingType.STORAGE, "storage_id", false, true),
    STORAGE_TYPE(BillingGroupingType.STORAGE, "storage_type", false, false),
    USER(BillingGroupingType.COMMON, "owner", true, true),
    BILLING_CENTER(BillingGroupingType.COMMON, "billing_center", true, true);

    private final BillingGroupingType type;
    private final String correspondingField;
    private final boolean runUsageDetailsRequired;
    private final boolean storageUsageDetailsRequired;

}
