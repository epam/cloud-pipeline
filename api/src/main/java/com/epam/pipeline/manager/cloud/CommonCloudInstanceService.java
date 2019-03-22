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
import com.epam.pipeline.manager.cloud.commands.AbstractClusterCommand;
import com.epam.pipeline.manager.cloud.commands.ReassignCommand;
import com.epam.pipeline.manager.cloud.commands.TerminateNodeCommand;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.utils.CommonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommonCloudInstanceService {

    private final PreferenceManager preferenceManager;
    private final UserManager userManager;
    private final AuthManager authManager;
    private final PipelineRunManager pipelineRunManager;

    public RunInstance runNodeUpScript(final CmdExecutor cmdExecutor,
                                       final Long runId,
                                       final RunInstance instance,
                                       final String command,
                                       final Map<String, String> envVars) {
        log.debug("Scaling cluster up. Command: {}.", command);
        final Map<String, String> authEnvVars = buildPipeAuthEnvVars(runId);
        final Map<String, String> mergedEnvVars = CommonUtils.mergeMaps(envVars, authEnvVars);
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

    public boolean runNodeReassignScript(final Long oldId,
                                         final Long newId,
                                         final CmdExecutor cmdExecutor,
                                         final String reassignScript,
                                         final Map<String, String> envVars) {
        final String command = buildNodeReassignCommand(reassignScript, oldId, newId);
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

    public String buildTerminateNodeCommand(final String internalIp, final String nodeName,
                                            final String nodeTerminateScript) {
        return TerminateNodeCommand.builder()
                .executable(AbstractClusterCommand.EXECUTABLE)
                .script(nodeTerminateScript)
                .internalIp(internalIp)
                .nodeName(nodeName)
                .build()
                .getCommand();
    }

    private String buildNodeReassignCommand(final String reassignScript,
                                            final Long oldId,
                                            final Long newId) {
        return ReassignCommand.builder()
                .executable(AbstractClusterCommand.EXECUTABLE)
                .script(reassignScript)
                .oldRunId(String.valueOf(oldId))
                .newRunId(String.valueOf(newId))
                .build()
                .getCommand();
    }

    private void readInstanceId(final RunInstance instance, final String output) {
        final String[] node = output.split("\\s+");
        if (node.length == 3) {
            instance.setNodeId(node[0]);
            instance.setNodeIP(node[1]);
            instance.setNodeName(node[2]);
        }
    }

    private Map<String, String> buildPipeAuthEnvVars(final Long id) {
        final Map<String, String> envVars = new HashMap<>();
        envVars.put("API", preferenceManager.getPreference(SystemPreferences.BASE_API_HOST));
        envVars.put("API_TOKEN", getApiTokenForRun(id));
        return envVars;
    }

    private String getApiTokenForRun(final Long runId) {
        PipelineRun run = pipelineRunManager.loadPipelineRun(runId);
        UserContext owner = Optional.ofNullable(authManager.getUserContext())
                .orElse(userManager.loadUserContext(run.getOwner()));
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
