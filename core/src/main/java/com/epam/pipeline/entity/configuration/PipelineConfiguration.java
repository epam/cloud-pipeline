/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.configuration;

import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.pipeline.run.ExecutionPreferences;
import com.epam.pipeline.entity.pipeline.run.PipelineStartNotificationRequest;
import com.epam.pipeline.entity.pipeline.run.RunAssignPolicy;
import com.epam.pipeline.entity.pipeline.run.parameter.RunSid;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.collections4.ListUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class represents pipeline configuration, that is described in config.json file and
 * stored in pipeline's repository.
 */
@Setter
@Getter
@NoArgsConstructor
@EqualsAndHashCode
public class PipelineConfiguration implements Cloneable {

    private static final String MAIN_FILE = "main_file";
    private static final String MAIN_CLASS = "main_class";
    private static final String INSTANCE_SIZE = "instance_size";
    private static final String INSTANCE_IMAGE = "instance_image";
    private static final String INSTANCE_DISK = "instance_disk";
    private static final String PRICE_TYPE = "price_type";
    private static final String DOCKER_IMAGE = "docker_image";
    private static final String TIMEOUT = "timeout";
    private static final String CMD_TEMPLATE = "cmd_template";
    private static final String PARAMETERS = "parameters";
    private static final String DEFAULT_LANGUAGE = "other";
    private static final String LANGUAGE = "language";
    private static final String DEFAULT = "default";
    private static final String NODE_COUNT = "node_count";
    private static final String CLUSTER_ROLE = "cluster_role";
    private static final String WORKER_CMD = "worker_cmd";
    private static final String IS_SPOT = "is_spot";
    private static final String NON_PAUSE = "non_pause";
    private static final String RUN_AS = "run_as";
    private static final String SHARED_WITH_USERS = "share_with_users";
    private static final String SHARED_WITH_ROLES = "share_with_roles";
    private static final String NOTIFICATIONS = "notifications";
    private static final String RAW_EDIT = "raw";

    public static final String EXECUTION_ENVIRONMENT = "EXEC_ENVIRONMENT";

    @JsonProperty(value = MAIN_FILE)
    private String mainFile;

    @JsonProperty(value = MAIN_CLASS)
    private String mainClass;

    @JsonProperty(value = INSTANCE_SIZE)
    private String instanceType;

    @JsonProperty(value = INSTANCE_IMAGE)
    private String instanceImage;

    @JsonProperty(value = INSTANCE_DISK)
    private String instanceDisk;

    @JsonProperty(value = DOCKER_IMAGE)
    private String dockerImage;

    @JsonProperty(value = TIMEOUT)
    private Long timeout;

    @JsonProperty(value = CMD_TEMPLATE)
    private String cmdTemplate;

    @JsonProperty(value = LANGUAGE)
    private String language = DEFAULT_LANGUAGE;

    @JsonProperty(value = NODE_COUNT)
    private Integer nodeCount;

    @JsonProperty(value = WORKER_CMD)
    private String workerCmd;

    @JsonProperty(value = PARAMETERS)
    @JsonDeserialize(using = PipelineConfValuesMapDeserializer.class)
    private Map<String, PipeConfValueVO> parameters = new LinkedHashMap<>();

    @JsonProperty(value = IS_SPOT)
    private Boolean isSpot;

    private boolean nonPause;

    private Long cloudRegionId;

    @JsonIgnore
    private Map<String, String> environmentParams = new LinkedHashMap<>();

    @JsonIgnore
    private String secretName;

    @JsonIgnore
    private String clusterRole;

    @JsonIgnore
    private GitCredentials gitCredentials;

    @JsonIgnore
    private boolean eraseRunEndpoints = false;

    @JsonIgnore
    private ExecutionPreferences executionPreferences = ExecutionPreferences.getDefault();

    @JsonIgnore
    private String prettyUrl;

    @JsonIgnore
    private Integer effectiveDiskSize;

    @JsonProperty(value = RUN_AS)
    private String runAs;

    @JsonProperty(value = SHARED_WITH_USERS)
    private List<RunSid> sharedWithUsers;

    @JsonProperty(value = SHARED_WITH_ROLES)
    private List<RunSid> sharedWithRoles;

    @JsonProperty(value = NOTIFICATIONS)
    private List<PipelineStartNotificationRequest> notifications;

    private Map<String, String> tags;

    private Map<String, String> kubeLabels;

    private RunAssignPolicy podAssignPolicy;

    private String kubeServiceAccount;

    @JsonProperty(value = RAW_EDIT)
    private Boolean rawEdit;

    @JsonIgnore
    public void setParameters(Map<String, PipeConfValueVO> parameters) {
        this.parameters = parameters;
    }

    public void buildEnvVariables() {
        putParamIfPresent(environmentParams, MAIN_FILE, getMainFile());
        putParamIfPresent(environmentParams, MAIN_FILE, getMainFile());
        putParamIfPresent(environmentParams, MAIN_CLASS, getMainClass());
        putParamIfPresent(environmentParams, INSTANCE_IMAGE, getMainFile());
        putParamIfPresent(environmentParams, INSTANCE_SIZE, getInstanceType());
        putParamIfPresent(environmentParams, INSTANCE_DISK, getInstanceDisk());
        putParamIfPresent(environmentParams, DOCKER_IMAGE, getDockerImage());
        putParamIfPresent(environmentParams, TIMEOUT, getTimeout());
        putParamIfPresent(environmentParams, NODE_COUNT, getNodeCount());
        putParamIfPresent(environmentParams, CLUSTER_ROLE, getClusterRole());
        putParamIfPresent(environmentParams, EXECUTION_ENVIRONMENT, executionPreferences.getEnvironment().name());
        putParamIfPresent(environmentParams, PRICE_TYPE,
                Optional.ofNullable(getIsSpot())
                        .filter(spot -> spot)
                        .map(spot -> PriceType.SPOT)
                        .orElse(PriceType.ON_DEMAND)
                        .getLiteral());
    }

    /**
     * For json deserialization only. Provides backward compatibility for field name.
     * @param regionId
     */
    @JsonProperty("awsRegionId")
    public void setAwsRegionId(final Long regionId) {
        this.cloudRegionId = regionId;
    }

    private static void putParamIfPresent(final Map<String, String> params, final String name, final String value) {
        if (value != null) {
            params.put(name, value);
        }
    }

    private static void putParamIfPresent(final Map<String, String> params, final String name, final Number value) {
        if (value != null) {
            putParamIfPresent(params, name, String.valueOf(value));
        }
    }

    @JsonIgnore
    public List<RunSid> mergeRunSids(final List<RunSid> external) {
        final Set<RunSid> runSids = new HashSet<>(ListUtils.emptyIfNull(external));
        runSids.addAll(adjustPrincipal(ListUtils.emptyIfNull(sharedWithUsers), true));
        runSids.addAll(adjustPrincipal(ListUtils.emptyIfNull(sharedWithRoles), false));
        return new ArrayList<>(runSids);
    }

    private List<RunSid> adjustPrincipal(final List<RunSid> runsSids, final boolean principal) {
        return runsSids.stream()
                .peek(runSid -> runSid.setIsPrincipal(principal))
                .collect(Collectors.toList());
    }

    @JsonIgnore
    @Override
    public PipelineConfiguration clone() {
        try {
            final PipelineConfiguration clone = (PipelineConfiguration) super.clone();
            if (this.parameters != null) {
                clone.setParameters(new HashMap<>(this.parameters));
            }
            if (this.environmentParams != null) {
                clone.setEnvironmentParams(new HashMap<>(this.environmentParams));
            }
            if (this.sharedWithUsers != null) {
                clone.setSharedWithUsers(new ArrayList<>(this.sharedWithUsers));
            }
            if (this.sharedWithRoles != null) {
                clone.setSharedWithRoles(new ArrayList<>(this.sharedWithRoles));
            }
            if (this.notifications != null) {
                clone.setNotifications(new ArrayList<>(this.notifications));
            }
            if (this.tags != null) {
                clone.setTags(new HashMap<>(this.tags));
            }
            if (this.kubeLabels != null) {
                clone.setKubeLabels(new HashMap<>(this.kubeLabels));
            }
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("There was an error while trying to clone PipelineConfiguration object", e);
        }
    }
}
