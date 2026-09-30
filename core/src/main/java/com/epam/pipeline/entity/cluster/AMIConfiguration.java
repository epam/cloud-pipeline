/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.entity.cluster;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Map;

/**
 * How a node is launched: its image and boot, and the cloud-specific parts of its launch request.
 *
 * <p>The same shape serves two places. In a region's {@code amis} of {@code cluster.networks.config} it is a rule,
 * and {@code platform}, {@code instance_mask}, {@code permissions} and {@code docker_image} choose which rule a
 * launch uses. On a node pool it overlays whichever rule matched for the pool's nodes - every field it sets wins,
 * and {@code additional_spec} is merged key by key - so those choosing fields mean nothing there.
 *
 * <p>The JSON names are the launch scripts' own: they read the same objects.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class AMIConfiguration {
    private String platform;
    @JsonProperty("instance_mask")
    private String instanceMask;
    private String ami;
    @JsonProperty("run_parameters")
    private Map<String, Object> runParameters;
    /**
     * Passed to the cloud's launch request as it is - for AWS, arguments of {@code RunInstances}.
     */
    @JsonProperty("additional_spec")
    private Map<String, Object> additionalSpec;
    private List<String> permissions;
    @JsonProperty("docker_image")
    private List<String> dockerImages;
    @JsonProperty("init_script")
    private String initScript;
    @JsonProperty("fs_type")
    private String fsType;
    @JsonProperty("embedded_scripts")
    private Map<String, String> embeddedScripts;
    @JsonProperty("availability_zone")
    private String availabilityZone;
    /**
     * A node launched with this configuration goes into this subnet. It fixes the zone as well, so it has to be the
     * subnet a region's {@code networks} configures for {@link #availabilityZone}, where both are set.
     */
    private String subnet;
}
