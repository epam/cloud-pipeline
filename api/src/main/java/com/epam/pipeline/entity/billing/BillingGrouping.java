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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum BillingGrouping {
    RESOURCE_TYPE("resource_type", false),
    RUN_INSTANCE_TYPE("instance_type", true),
    RUN_COMPUTE_TYPE("compute_type", false),
    PIPELINE("pipeline", true),
    TOOL("tool", true),
    STORAGE("id", Collections.singletonMap("resource_type", Arrays.asList("STORAGE")), false),
    STORAGE_TYPE("storage_type", false),
    USER("owner", false),
    BILLING_CENTER("billing_center", false);

    private final String correspondingField;
    private final Map<String, List<String>> requiredDefaultFilters;
    private final boolean usageDetailsRequired;

    BillingGrouping(final String correspondingField, final boolean usageDetailsRequired) {
        this(correspondingField, Collections.emptyMap(), usageDetailsRequired);
    }

    BillingGrouping(final String correspondingField, final Map<String, List<String>> requiredDefaultFilters,
                    final boolean usageDetailsRequired) {
        this.correspondingField = correspondingField;
        this.requiredDefaultFilters = new HashMap<>(requiredDefaultFilters);
        this.usageDetailsRequired = usageDetailsRequired;
    }

    public String getCorrespondingField() {
        return correspondingField;
    }

    public Map<String, List<String>> getRequiredDefaultFilters() {
        return requiredDefaultFilters;
    }

    public boolean usageDetailsRequired() {
        return usageDetailsRequired;
    }
}
