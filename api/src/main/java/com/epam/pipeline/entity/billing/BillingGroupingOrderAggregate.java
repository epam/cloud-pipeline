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

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BillingGroupingOrderAggregate {

    DEFAULT(null, "cost", "cost"),

    STORAGE(BillingGrouping.STORAGE, "cost", "usage_bytes"),
    STANDARD(BillingGrouping.STORAGE,
            "standard_total_cost", "standard_total_usage_bytes"),
    GLACIER(BillingGrouping.STORAGE,
            "glacier_total_cost", "glacier_total_usage_bytes"),
    GLACIER_IR(BillingGrouping.STORAGE,
            "glacier_ir_total_cost", "glacier_ir_total_usage_bytes"),
    DEEP_ARCHIVE(BillingGrouping.STORAGE,
            "deep_archive_total_cost", "deep_archive_total_usage_bytes");

    private final BillingGrouping group;
    private final String costField;
    private final String usageField;
}
