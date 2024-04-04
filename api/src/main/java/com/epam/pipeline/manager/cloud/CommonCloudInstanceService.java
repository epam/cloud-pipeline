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

package com.epam.pipeline.manager.cloud;

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.exception.CmdExecutionException;
import com.epam.pipeline.manager.CmdExecutor;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.cluster.node.NodeResourcesService;
import com.epam.pipeline.manager.cluster.node.NodeResources;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.utils.CommonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommonCloudInstanceService {

    private final PreferenceManager preferenceManager;
    private final UserManager userManager;
    private final AuthManager authManager;
    private final PipelineRunCRUDService runCRUDService;
    private final KubernetesManager kubernetesManager;
    private final NodeResourcesService nodeResourcesService;

    public RunInstance runNodeUpScript(final CmdExecutor cmdExecutor,
                                       final Long runId,
                                       final RunInstance instance,
                                       final String command,
                                       final Map<String, String> envVars) {
        log.debug("Scaling cluster up. Command: {}.", command);
        final Map<String, String> mergedEnvVars = buildEnvVars(runId, instance, envVars);
        log.debug("EnvVars: {}", mergedEnvVars);
        final String output = cmdExecutor.executeCommandWithEnvVars(command, mergedEnvVars);
        log.debug("Scale up output: {}.", output);
        readInstanceId(instance, output);
        return instance;
    }

    public void runNodeDownScript(final CmdExecutor cmdExecutor,
                                  final String command,
                                  final Map<String, String> envVars) {
        log.debug("Scaling cluster down. Command: {}.", command);
        executeCmd(cmdExecutor, command, envVars);
    }

    public boolean runNodeReassignScript(final CmdExecutor cmdExecutor,
                                         final String command,
                                         final Long oldId,
                                         final Long newId,
                                         final Map<String, String> envVars) {
        return runNodeReassignScript(cmdExecutor, command, String.valueOf(oldId), String.valueOf(newId), envVars);
    }

    public boolean runNodeReassignScript(final CmdExecutor cmdExecutor,
                                         final String command,
                                         final String oldId,
                                         final String newId,
                                         final Map<String, String> envVars) {
        log.debug("Reusing Node with previous ID {} for rud ID {}. Command {}.", oldId, newId, command);
        try {
            cmdExecutor.executeCommandWithEnvVars(command, envVars);
            return true;
        } catch (CmdExecutionException e) {
            log.debug(e.getMessage(), e);
            log.error("Failed to reassign node from {} to {}", oldId, newId);
            return false;
        }
    }

    public void runTerminateNodeScript(final String command, final CmdExecutor cmdExecutor,
                                       final Map<String, String> envVars) {
        log.debug("Terminating node. Command: {}.", command);
        executeCmd(cmdExecutor, command, envVars);
    }

    public LocalDateTime getNodeLaunchTimeFromKube(final Long runId) {
        return kubernetesManager.findNodeByRunId(String.valueOf(runId))
                .map(node -> node.getMetadata().getCreationTimestamp())
                .filter(StringUtils::isNotBlank)
                .map(timestamp -> {
                    try {
                        return ZonedDateTime.parse(timestamp, KubernetesConstants.KUBE_DATE_FORMATTER)
                                .toLocalDateTime();
                    } catch (DateTimeParseException e) {
                        log.error("Failed to parse date from Kubernetes API: {}", timestamp);
                        return null;
                    }
                }).orElse(null);
    }

    private void readInstanceId(final RunInstance instance, final String output) {
        final String[] node = output.split("\\s+");
        if (node.length == 3) {
            instance.setNodeId(node[0]);
            instance.setNodeIP(node[1]);
            instance.setNodeName(node[2]);
        }
    }

    private Map<String, String> buildEnvVars(final Long runId, final RunInstance instance,
                                             final Map<String, String> envVars) {
        return CommonUtils.mergeMaps(envVars,
                buildCommonEnvVars(runId, instance),
                buildPipeAuthEnvVars(runId));
    }

    private Map<String, String> buildCommonEnvVars(final Long runId, final RunInstance instance) {
        final NodeResources resources = nodeResourcesService.build(instance);
        return CommonUtils.mergeMaps(
                buildKubeReservationsEnvVars(runId, resources),
                buildSystemReservationsEnvVars(runId, resources));
    }

    private Map<String, String> buildKubeReservationsEnvVars(final Long runId, final NodeResources resources) {
        if (StringUtils.isBlank(resources.getKubeMem())) {
            log.warn("Kube memory reservation for run #{} is missing", runId);
            return Collections.emptyMap();
        }
        log.debug("Configuring kube reservations for run #{}: memory={}", runId, resources.getKubeMem());
        return Collections.singletonMap("KUBE_RESERVED_MEM", resources.getKubeMem());
    }

    private Map<String, String> buildSystemReservationsEnvVars(final Long runId,
                                                               final NodeResources resources) {
        if (StringUtils.isBlank(resources.getSystemMem())) {
            log.warn("System memory reservation for run #{} is missing", runId);
            return Collections.emptyMap();
        }
        log.debug("Configuring system reservations for run #{}: memory={}", runId, resources.getSystemMem());
        return Collections.singletonMap("SYSTEM_RESERVED_MEM", resources.getSystemMem());
    }

    private Map<String, String> buildPipeAuthEnvVars(final Long id) {
        final Map<String, String> envVars = new HashMap<>();
        envVars.put("API", preferenceManager.getPreference(SystemPreferences.BASE_API_HOST));
        envVars.put("API_TOKEN", getApiTokenForRun(id));
        return envVars;
    }

    private String getApiTokenForRun(final Long runId) {
        if (Objects.isNull(runId)) {
            return authManager.issueTokenForCurrentUser().getToken();
        }
        PipelineRun run = runCRUDService.loadRunById(runId);
        UserContext owner = userManager.loadUserContext(run.getOwner());
        return authManager.issueToken(owner, null).getToken();
    }

    private void executeCmd(final CmdExecutor cmdExecutor,
                            final String command,
                            final Map<String, String> envVars) {
        try {
            cmdExecutor.executeCommandWithEnvVars(command, envVars);
        } catch (CmdExecutionException e) {
            log.error(e.getMessage(), e);
        }
    }
}
