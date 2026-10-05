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

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

public class CloudRegionsConfigurationTest {

    private static final String M5_LARGE = "m5.large";
    private static final String REGION_CODE = "eu-central-1";
    private static final String ZONE = "eu-central-1b";
    private static final String SUBNET = "subnet-b";
    private static final String AMI = "ami-pool";
    private static final String RESTRICTED_AMI = "ami-restricted";
    private static final String LINUX = "linux";

    /**
     * An {@code amis} rule's mask, matched the way the launch scripts' {@code fnmatch} matches it - or a pool would
     * start from a different rule than its nodes would have launched with.
     */
    @Test
    public void shouldMatchWildcards() {
        assertThat(CloudRegionsConfiguration.matchesMask(M5_LARGE, "*")).isTrue();
        assertThat(CloudRegionsConfiguration.matchesMask(M5_LARGE, "m5.*")).isTrue();
        assertThat(CloudRegionsConfiguration.matchesMask(M5_LARGE, "m?.large")).isTrue();
        assertThat(CloudRegionsConfiguration.matchesMask(M5_LARGE, "g*")).isFalse();
    }

    @Test
    public void shouldMatchCharacterSets() {
        assertThat(CloudRegionsConfiguration.matchesMask("m5a.large", "m5[ad].*")).isTrue();
        assertThat(CloudRegionsConfiguration.matchesMask("m5n.large", "m5[ad].*")).isFalse();
        assertThat(CloudRegionsConfiguration.matchesMask("m5n.large", "m5[!ad].*")).isTrue();
    }

    @Test
    public void shouldMatchNothingWithoutAMaskOrForAnUnparsableOne() {
        assertThat(CloudRegionsConfiguration.matchesMask(M5_LARGE, null)).isFalse();
        assertThat(CloudRegionsConfiguration.matchesMask(M5_LARGE, "m5[")).isFalse();
    }

    /**
     * The launch scripts take the first entry named after the region that configures networks at all.
     */
    @Test
    public void shouldTakeTheNetworksOfTheFirstEntryOfTheRegionThatConfiguresThem() {
        final CloudRegionsConfiguration configuration = configuration(
                region("us-east-1", Collections.singletonMap("us-east-1a", "subnet-a"), null),
                region(REGION_CODE, Collections.emptyMap(), null),
                region(REGION_CODE, Collections.singletonMap(ZONE, SUBNET), null));

        assertThat(configuration.allowedNetworks(REGION_CODE)).containsOnly(entry(ZONE, SUBNET));
    }

    @Test
    public void shouldNotLetACallerChangeTheConfiguredNetworks() {
        final CloudRegionsConfiguration configuration = configuration(
                region(REGION_CODE, new HashMap<>(Collections.singletonMap(ZONE, SUBNET)), null));

        assertThatThrownBy(() -> configuration.allowedNetworks(REGION_CODE).put("eu-central-1c", "subnet-c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void shouldConfigureNoNetworksForAnUnknownRegionOrNone() {
        final CloudRegionsConfiguration configuration = configuration(
                region(REGION_CODE, Collections.singletonMap(ZONE, SUBNET), null));

        assertThat(configuration.allowedNetworks("us-east-1")).isEmpty();
        assertThat(configuration.allowedNetworks(null)).isEmpty();
        assertThat(new CloudRegionsConfiguration().allowedNetworks(REGION_CODE)).isEmpty();
    }

    /**
     * Rules restricted to a docker image or to roles are not a pool's, and what only chooses between rules is not
     * copied.
     */
    @Test
    public void shouldCopyTheFirstUnrestrictedRuleMatchingTheInstanceType() {
        final AMIConfiguration forImage = rule(RESTRICTED_AMI, "m5.*");
        forImage.setDockerImages(Collections.singletonList("library/centos"));
        final AMIConfiguration forRole = rule(RESTRICTED_AMI, "*");
        forRole.setPermissions(Collections.singletonList("ROLE_USER"));
        final AMIConfiguration forGpu = rule(RESTRICTED_AMI, "p*");
        final AMIConfiguration matching = rule(AMI, "m5*");
        matching.setInitScript("/opt/api/scripts/init.sh");
        matching.setAdditionalSpec(new HashMap<>(Collections.singletonMap("IamInstanceProfile", "profile")));
        matching.setEmbeddedScripts(new HashMap<>(Collections.singletonMap("setup.sh", "/opt/api/setup.sh")));
        matching.setAvailabilityZone(ZONE);
        matching.setSubnet(SUBNET);
        final CloudRegionsConfiguration configuration = configuration(
                region(REGION_CODE, null, Arrays.asList(forImage, forRole, forGpu, matching)));

        final Optional<AMIConfiguration> copy = configuration.amiConfigurationFor(REGION_CODE, M5_LARGE);

        assertThat(copy.isPresent()).isTrue();
        assertThat(copy.get()).isNotSameAs(matching);
        assertThat(copy.get().getAdditionalSpec()).isEqualTo(matching.getAdditionalSpec())
                .isNotSameAs(matching.getAdditionalSpec());
        assertThat(copy.get().getEmbeddedScripts()).isEqualTo(matching.getEmbeddedScripts())
                .isNotSameAs(matching.getEmbeddedScripts());
        assertThat(copy.get().getAmi()).isEqualTo(AMI);
        assertThat(copy.get().getInitScript()).isEqualTo("/opt/api/scripts/init.sh");
        assertThat(copy.get().getAvailabilityZone()).isEqualTo(ZONE);
        assertThat(copy.get().getSubnet()).isNull();
        assertThat(copy.get().getInstanceMask()).isNull();
        assertThat(copy.get().getPlatform()).isNull();
    }

    /**
     * Unlike the networks, the rules are only looked for in the first entry named after the region - even one with
     * none of its own.
     */
    @Test
    public void shouldLookForARuleOnlyInTheFirstEntryOfTheRegion() {
        final CloudRegionsConfiguration configuration = configuration(
                region(REGION_CODE, null, null),
                region(REGION_CODE, null, Collections.singletonList(rule(AMI, "m5*"))));

        assertThat(configuration.amiConfigurationFor(REGION_CODE, M5_LARGE).isPresent()).isFalse();
    }

    @Test
    public void shouldFindNoRuleForAnUnknownRegionOrInstanceType() {
        final CloudRegionsConfiguration configuration = configuration(
                region(REGION_CODE, null, Collections.singletonList(rule(AMI, "m5*"))));

        assertThat(configuration.amiConfigurationFor("us-east-1", M5_LARGE).isPresent()).isFalse();
        assertThat(configuration.amiConfigurationFor(null, M5_LARGE).isPresent()).isFalse();
        assertThat(configuration.amiConfigurationFor(REGION_CODE, "p3.2xlarge").isPresent()).isFalse();
    }

    private static CloudRegionsConfiguration configuration(final NetworkConfiguration... regions) {
        final CloudRegionsConfiguration configuration = new CloudRegionsConfiguration();
        configuration.setRegions(Arrays.asList(regions));
        return configuration;
    }

    private static NetworkConfiguration region(final String name, final Map<String, String> networks,
                                               final List<AMIConfiguration> amis) {
        final NetworkConfiguration region = new NetworkConfiguration();
        region.setName(name);
        region.setAllowedNetworks(networks);
        region.setAmis(amis);
        return region;
    }

    private static AMIConfiguration rule(final String ami, final String mask) {
        final AMIConfiguration rule = new AMIConfiguration();
        rule.setPlatform(LINUX);
        rule.setInstanceMask(mask);
        rule.setAmi(ami);
        return rule;
    }
}
