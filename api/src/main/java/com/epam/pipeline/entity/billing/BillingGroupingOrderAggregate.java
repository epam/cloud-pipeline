/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.manager.billing.BillingUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum BillingGroupingOrderAggregate {

    DEFAULT(null, BillingUtils.COST_FIELD, BillingUtils.COST_FIELD, null),

    STORAGE(BillingGrouping.getStorageGrouping(), BillingUtils.COST_FIELD, BillingUtils.STORAGE_USAGE_FIELD, null),
    STANDARD(BillingGrouping.getStorageGrouping(),
            "standard_total_cost", "standard_total_usage_bytes", null),
    GLACIER(BillingGrouping.getStorageGrouping(),
            "glacier_total_cost", "glacier_total_usage_bytes", null),
    GLACIER_IR(BillingGrouping.getStorageGrouping(),
            "glacier_ir_total_cost", "glacier_ir_total_usage_bytes", null),
    DEEP_ARCHIVE(BillingGrouping.getStorageGrouping(),
            "deep_archive_total_cost", "deep_archive_total_usage_bytes", null),
    RUN(BillingGrouping.getRunGrouping(), BillingUtils.COST_FIELD,
            BillingUtils.RUN_USAGE_FIELD, BillingUtils.RUN_ID_FIELD),
    RUN_COMPUTE(BillingGrouping.getRunGrouping(), BillingUtils.COMPUTE_COST_FIELD,
            BillingUtils.RUN_USAGE_FIELD, BillingUtils.RUN_ID_FIELD),
    RUN_DISK(BillingGrouping.getRunGrouping(), BillingUtils.DISK_COST_FIELD,
            BillingUtils.RUN_USAGE_FIELD, BillingUtils.RUN_ID_FIELD);

    private final Set<BillingGrouping> groups;
    private final String costField;
    private final String usageField;
    private final String countField;
}
