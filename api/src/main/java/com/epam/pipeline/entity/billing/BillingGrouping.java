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

import java.util.Collections;
import java.util.List;
import java.util.Map;

public enum BillingGrouping {
    RESOURCE_TYPE("resource_type"),
    RUN_INSTANCE_TYPE("instance_type"),
    RUN_COMPUTE_TYPE("compute_type"),
    PIPELINE("pipeline"),
    TOOL("tool"),
    STORAGE("id", Collections.singletonMap("resource_type", Collections.singletonList("STORAGE"))),
    STORAGE_TYPE("storage_type"),
    USER("owner");

    private final String correspondingField;
    private final Map<String, List<String>> requiredDefaultFilters;

    BillingGrouping(final String correspondingField) {
        this(correspondingField, Collections.emptyMap());
    }

    BillingGrouping(final String correspondingField, final Map<String, List<String>> requiredDefaultFilters) {
        this.correspondingField = correspondingField;
        this.requiredDefaultFilters = requiredDefaultFilters;
    }

    public String getCorrespondingField() {
        return correspondingField;
    }

    public Map<String, List<String>> getRequiredDefaultFilters() {
        return requiredDefaultFilters;
    }
}
