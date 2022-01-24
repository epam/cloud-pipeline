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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

@Builder
@Getter
@AllArgsConstructor
@EqualsAndHashCode(of = "correspondingField")
public class BillingGrouping {

    public static final BillingGrouping RESOURCE_TYPE = new BillingGrouping("resource_type", "Resource",
            false, false);
    public static final BillingGrouping RUN_INSTANCE_TYPE = new BillingGrouping("instance_type", "Instance Type",
            true, false);
    public static final BillingGrouping RUN_COMPUTE_TYPE = new BillingGrouping("compute_type", "Compute Type",
            false, false);
    public static final BillingGrouping PIPELINE = new BillingGrouping("pipeline", "Pipeline",
            true, false);
    public static final BillingGrouping TOOL = new BillingGrouping("tool", "Tool",
            true, false);
    public static final BillingGrouping STORAGE = new BillingGrouping("storage_id", "Storage",
            false, true);
    public static final BillingGrouping STORAGE_TYPE = new BillingGrouping("storage_type", "Storage Type",
            false, false);
    public static final BillingGrouping USER = new BillingGrouping("owner", "Owner",
            true, true);
    public static final BillingGrouping BILLING_CENTER = new BillingGrouping("billing_center", "Billing Center",
            true, true);

    public static final Map<String, BillingGrouping> DEFAULT_GROUPING_BY_NAME = new HashMap<String, BillingGrouping>(){{
        put(RESOURCE_TYPE.getCorrespondingField(), RESOURCE_TYPE);
        put(RUN_INSTANCE_TYPE.getCorrespondingField(), RUN_INSTANCE_TYPE);
        put(RUN_COMPUTE_TYPE.getCorrespondingField(), RUN_COMPUTE_TYPE);
        put(PIPELINE.getCorrespondingField(), PIPELINE);
        put(TOOL.getCorrespondingField(), TOOL);
        put(STORAGE.getCorrespondingField(), STORAGE);
        put(STORAGE_TYPE.getCorrespondingField(), STORAGE_TYPE);
        put(USER.getCorrespondingField(), USER);
        put(BILLING_CENTER.getCorrespondingField(), BILLING_CENTER);
    }};

    private final String correspondingField;
    private final String name;
    private final boolean runUsageDetailsRequired;
    private final boolean storageUsageDetailsRequired;

    @NotNull
    public static BillingGrouping getDefault(final String correspondingField) {
        return DEFAULT_GROUPING_BY_NAME.getOrDefault(correspondingField, fromField(correspondingField));
    }

    private static BillingGrouping fromField(final String correspondingField) {
        return BillingGrouping.builder().name(correspondingField).correspondingField(correspondingField).build();
    }

}
