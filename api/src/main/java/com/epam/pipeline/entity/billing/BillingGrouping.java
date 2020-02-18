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

public enum BillingGrouping {
    RESOURCE_TYPE("resource_type", false, false),
    RUN_INSTANCE_TYPE("instance_type", true, false),
    RUN_COMPUTE_TYPE("compute_type", false, false),
    PIPELINE("pipeline", true, false),
    TOOL("tool", true, false),
    STORAGE("storage_id", false, true),
    STORAGE_TYPE("storage_type", false, false),
    USER("owner", true, true),
    BILLING_CENTER("billing_center", true, true);

    private final String correspondingField;
    private final boolean runUsageDetailsRequired;
    private final boolean storageUsageDetailsRequired;

    BillingGrouping(final String correspondingField, final boolean runUsageDetailsRequired,
                    final boolean storageUsageDetailsRequired) {
        this.correspondingField = correspondingField;
        this.runUsageDetailsRequired = runUsageDetailsRequired;
        this.storageUsageDetailsRequired = storageUsageDetailsRequired;
    }

    public String getCorrespondingField() {
        return correspondingField;
    }

    public boolean runUsageDetailsRequired() {
        return runUsageDetailsRequired;
    }

    public boolean storageUsageDetailsRequired() {
        return storageUsageDetailsRequired;
    }
}
