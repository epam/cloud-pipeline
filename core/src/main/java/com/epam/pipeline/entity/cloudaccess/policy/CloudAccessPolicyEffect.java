/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.cloudaccess.policy;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public enum CloudAccessPolicyEffect {
    ALLOW("Allow"), DENY("Deny");

    private static final Map<String, CloudAccessPolicyEffect> EFFECT_MAP = Arrays.stream(
            CloudAccessPolicyEffect.values()).collect(Collectors.toMap(e -> e.awsValue, e -> e));

    public String awsValue;

    CloudAccessPolicyEffect(String awsValue) {
        this.awsValue = awsValue;
    }

    public static CloudAccessPolicyEffect from(String value) {
        return Optional.ofNullable(EFFECT_MAP.get(value))
                .orElseThrow(() -> new IllegalArgumentException("Unsupported effect: " + value));
    }
}
