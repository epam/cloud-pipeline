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

package com.epam.pipeline.entity.pricing.azure;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@NoArgsConstructor
@Data
@Builder
@AllArgsConstructor
public class AzurePricingMeter {
    @JsonProperty(value = "MeterId")
    private String meterId;
    @JsonProperty(value = "MeterCategory")
    private String meterCategory;
    @JsonProperty(value = "MeterSubCategory")
    private String meterSubCategory;
    @JsonProperty(value = "MeterName")
    private String meterName;
    @JsonProperty(value = "Unit")
    private String unit;
    @JsonProperty(value = "MeterRates")
    private Map<String, Float> meterRates;
    @JsonProperty(value = "MeterRegion")
    private String meterRegion;
}
