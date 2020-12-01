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

package com.epam.pipeline.billingreportagent.model.pricing;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class AzureEAPricingResult {
    @JsonProperty(value = "properties")
    private PricingProperties properties;

    @Data
    @Builder
    @NoArgsConstructor
    public static class PricingProperties {

        public PricingProperties(String nextLink, List<AzureEAPricingMeter> pricesheets) {
            this.nextLink = nextLink;
            this.pricesheets = pricesheets;
        }

        @JsonProperty(value = "nextLink")
        private String nextLink;

        @JsonProperty(value = "pricesheets")
        private List<AzureEAPricingMeter> pricesheets;
    }
}
