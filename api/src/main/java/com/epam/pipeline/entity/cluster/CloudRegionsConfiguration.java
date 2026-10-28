/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.cluster;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudRegionsConfiguration {

    private List<NetworkConfiguration> regions;
    private Map<String, String> tags;

    public Map<String, String> allowedNetworks(final String regionCode) {
        if (regionCode == null) {
            return Collections.emptyMap();
        }
        return ListUtils.emptyIfNull(regions).stream()
                .filter(region -> regionCode.equals(region.getName()))
                .filter(region -> MapUtils.isNotEmpty(region.getAllowedNetworks()))
                .findFirst()
                .map(NetworkConfiguration::getAllowedNetworks)
                .map(Collections::unmodifiableMap)
                .orElseGet(Collections::emptyMap);
    }
}
