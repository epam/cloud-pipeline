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

package com.epam.pipeline.manager.cluster.network;

import com.epam.pipeline.entity.cluster.CloudRegionsConfiguration;
import com.epam.pipeline.entity.cluster.NetworkConfiguration;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * The zones and subnets a region's nodes may be launched into, as the region's {@code networks} in
 * {@code cluster.networks.config} configures them.
 *
 * <p>A region's entry is the one whose {@code name} is the region's code - the rule the launch scripts apply, so
 * what is checked here is what a launch will find.
 */
@Service
@RequiredArgsConstructor
public class RegionNetworksService {

    private final PreferenceManager preferenceManager;

    /**
     * Zone to subnet. Empty when the region configures no networks, in which case a node may go to any of the
     * region's zones, into its default subnet there.
     */
    public Map<String, String> allowedNetworks(final String regionCode) {
        return find(regionCode)
                .map(NetworkConfiguration::getAllowedNetworks)
                .map(Collections::unmodifiableMap)
                .orElseGet(Collections::emptyMap);
    }

    private Optional<NetworkConfiguration> find(final String regionCode) {
        final CloudRegionsConfiguration configuration =
                preferenceManager.getPreference(SystemPreferences.CLUSTER_NETWORKS_CONFIG);
        if (configuration == null || regionCode == null) {
            return Optional.empty();
        }
        return ListUtils.emptyIfNull(configuration.getRegions()).stream()
                .filter(region -> regionCode.equals(region.getName()))
                .filter(region -> MapUtils.isNotEmpty(region.getAllowedNetworks()))
                .findFirst();
    }
}
