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

import com.epam.pipeline.entity.cluster.AMIConfiguration;
import com.epam.pipeline.entity.cluster.CloudRegionsConfiguration;
import com.epam.pipeline.entity.cluster.NetworkConfiguration;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;

import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
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

    /**
     * Node pools launch Linux nodes only.
     */
    private static final String LINUX = "linux";

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

    /**
     * The launch configuration a new pool of this instance type starts from: a copy of the first of the region's
     * {@code amis} rules that the launch scripts would match for its nodes, with the fields that only choose between
     * rules left out. Empty when no rule matches.
     *
     * <p>A rule restricted to a docker image is skipped - a pool's nodes are not launched for any one run - and so is a
     * rule restricted to roles or groups: a pool is shared by whoever its filter admits, and a rule meant for some
     * would reach all of them. An administrator can still give a pool such a configuration explicitly.
     */
    public Optional<AMIConfiguration> launchConfigurationFor(final String regionCode, final String instanceType) {
        final CloudRegionsConfiguration configuration =
                preferenceManager.getPreference(SystemPreferences.CLUSTER_NETWORKS_CONFIG);
        if (configuration == null || regionCode == null) {
            return Optional.empty();
        }
        return ListUtils.emptyIfNull(configuration.getRegions()).stream()
                .filter(region -> regionCode.equals(region.getName()))
                .findFirst()
                .flatMap(region -> ListUtils.emptyIfNull(region.getAmis()).stream()
                        .filter(rule -> LINUX.equals(rule.getPlatform()))
                        .filter(rule -> matchesMask(instanceType, rule.getInstanceMask()))
                        .filter(rule -> CollectionUtils.isEmpty(rule.getDockerImages()))
                        .filter(rule -> CollectionUtils.isEmpty(rule.getPermissions()))
                        .findFirst())
                .map(RegionNetworksService::copyForPool);
    }

    /**
     * The launch scripts match a rule's mask with {@code fnmatch}: {@code *}, {@code ?}, {@code [seq]} and
     * {@code [!seq]} - which a glob matches the same way for names without a {@code /}, as instance types are.
     */
    static boolean matchesMask(final String instanceType, final String mask) {
        if (instanceType == null || mask == null) {
            return false;
        }
        try {
            return FileSystems.getDefault().getPathMatcher("glob:" + mask).matches(Paths.get(instanceType));
        } catch (IllegalArgumentException e) {
            // An unparsable mask, or a name that is not a path - neither is a match.
            return false;
        }
    }

    /**
     * Only the fields the launch scripts take from a rule: a rule has no subnet of its own.
     */
    private static AMIConfiguration copyForPool(final AMIConfiguration rule) {
        final AMIConfiguration copy = new AMIConfiguration();
        copy.setAmi(rule.getAmi());
        copy.setInitScript(rule.getInitScript());
        copy.setFsType(rule.getFsType());
        copy.setEmbeddedScripts(rule.getEmbeddedScripts() == null ? null : new HashMap<>(rule.getEmbeddedScripts()));
        copy.setAdditionalSpec(rule.getAdditionalSpec() == null ? null : new HashMap<>(rule.getAdditionalSpec()));
        copy.setAvailabilityZone(rule.getAvailabilityZone());
        return copy;
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
