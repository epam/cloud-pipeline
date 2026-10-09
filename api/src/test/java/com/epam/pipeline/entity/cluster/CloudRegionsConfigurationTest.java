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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

public class CloudRegionsConfigurationTest {

    private static final String REGION_CODE = "eu-central-1";
    private static final String ZONE = "eu-central-1b";
    private static final String SUBNET = "subnet-b";

    @Test
    public void shouldTakeTheNetworksOfTheFirstEntryOfTheRegionThatConfiguresThem() {
        final CloudRegionsConfiguration configuration = configuration(
                region("us-east-1", Collections.singletonMap("us-east-1a", "subnet-a")),
                region(REGION_CODE, Collections.emptyMap()),
                region(REGION_CODE, Collections.singletonMap(ZONE, SUBNET)));

        assertThat(configuration.allowedNetworks(REGION_CODE)).containsOnly(entry(ZONE, SUBNET));
    }

    @Test
    public void shouldNotLetACallerChangeTheConfiguredNetworks() {
        final CloudRegionsConfiguration configuration = configuration(
                region(REGION_CODE, new HashMap<>(Collections.singletonMap(ZONE, SUBNET))));

        assertThatThrownBy(() -> configuration.allowedNetworks(REGION_CODE).put("eu-central-1c", "subnet-c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void shouldConfigureNoNetworksForAnUnknownRegionOrNone() {
        final CloudRegionsConfiguration configuration = configuration(
                region(REGION_CODE, Collections.singletonMap(ZONE, SUBNET)));

        assertThat(configuration.allowedNetworks("us-east-1")).isEmpty();
        assertThat(configuration.allowedNetworks(null)).isEmpty();
        assertThat(new CloudRegionsConfiguration().allowedNetworks(REGION_CODE)).isEmpty();
    }

    private static CloudRegionsConfiguration configuration(final NetworkConfiguration... regions) {
        final CloudRegionsConfiguration configuration = new CloudRegionsConfiguration();
        configuration.setRegions(Arrays.asList(regions));
        return configuration;
    }

    private static NetworkConfiguration region(final String name, final Map<String, String> networks) {
        final NetworkConfiguration region = new NetworkConfiguration();
        region.setName(name);
        region.setAllowedNetworks(networks);
        return region;
    }
}
