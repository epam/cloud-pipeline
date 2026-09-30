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

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An {@code amis} rule's mask, matched the way the launch scripts' {@code fnmatch} matches it - or a pool would start
 * from a different rule than its nodes would have launched with.
 */
public class RegionNetworksServiceMaskTest {

    private static final String M5_LARGE = "m5.large";

    @Test
    public void shouldMatchWildcards() {
        assertThat(RegionNetworksService.matchesMask(M5_LARGE, "*")).isTrue();
        assertThat(RegionNetworksService.matchesMask(M5_LARGE, "m5.*")).isTrue();
        assertThat(RegionNetworksService.matchesMask(M5_LARGE, "m?.large")).isTrue();
        assertThat(RegionNetworksService.matchesMask(M5_LARGE, "g*")).isFalse();
    }

    @Test
    public void shouldMatchCharacterSets() {
        assertThat(RegionNetworksService.matchesMask("m5a.large", "m5[ad].*")).isTrue();
        assertThat(RegionNetworksService.matchesMask("m5n.large", "m5[ad].*")).isFalse();
        assertThat(RegionNetworksService.matchesMask("m5n.large", "m5[!ad].*")).isTrue();
    }

    @Test
    public void shouldMatchNothingWithoutAMaskOrForAnUnparsableOne() {
        assertThat(RegionNetworksService.matchesMask(M5_LARGE, null)).isFalse();
        assertThat(RegionNetworksService.matchesMask(M5_LARGE, "m5[")).isFalse();
    }
}
