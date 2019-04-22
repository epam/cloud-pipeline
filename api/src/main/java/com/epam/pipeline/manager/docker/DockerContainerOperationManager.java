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

package com.epam.pipeline.manager.docker;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.PipelineRunServiceUrlVO;
import com.epam.pipeline.entity.pipeline.CommitStatus;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.exception.CmdExecutionException;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.execution.PipelineExecutor;
import com.epam.pipeline.manager.execution.PipelineLauncher;
import com.epam.pipeline.manager.execution.SystemParams;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.ToolGroupManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import io.fabric8.kubernetes.api.model.EnvVar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class DockerContainerOperationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerContainerOperationManager.class);

    private static final Pattern GROUP_AND_IMAGE = Pattern.compile("^(.*)\\/(.*)$");

    private static final String SSH_COMMAND = "ssh";
    private static final String IDENTITY_PARAM = "-i";
    private static final String SSH_CMD_OPTION = "-o";
    private static final String STRICT_HOST_KEY_CHECKING_NO = "StrictHostKeyChecking=no";
    private static final String GLOBAL_KNOWN_HOSTS_FILE = "GlobalKnownHostsFile=/dev/null";
    private static final String USER_KNOWN_HOSTS_FILE = "UserKnownHostsFile=/dev/null";
    private static final String COMMIT_COMMAND_TEMPLATE = "curl -k -s \"%s\" | sudo -E /bin/bash " +
            "--login /dev/stdin %s %s %s %s %d %s %b %b %d %s %d %d %s %s %s %b %s %s &> ~/commit_pipeline.log &";
    private static final String PAUSE_COMMAND_TEMPLATE = "curl -k -s \"%s\" | sudo -E /bin/bash " +
            "--login /dev/stdin %s %s %s %s %d %s %d %s %s %s &> ~/pause_pipeline.log";
    private static final String COMMIT_COMMAND_DESCRIPTION = "ssh session to commit pipeline run";
    private static final String PAUSE_COMMAND_DESCRIPTION = "Error is occured during to pause pipeline run";
    private static final String REJOIN_COMMAND_DESCRIPTION = "Error is occured during to resume pipeline run";
    private static final String EMPTY = "";
    public static final String DELIMITER = "/";
    public static final int COMMAND_CANNOT_EXECUTE_CODE = 126;

    @Autowired
    private PipelineRunManager runManager;

    @Autowired
    private KubernetesManager kubernetesManager;

    @Autowired
    private ToolGroupManager toolGroupManager;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private AuthManager authManager;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private CloudFacade cloudFacade;

    @Autowired
    private PipelineExecutor executor;

    @Autowired
    private NodesManager nodesManager;

    @Autowired
    private CloudRegionManager regionManager;

    @Value("${commit.run.scripts.root.url}")
    private String commitScriptsDistributionsUrl;

    @Value("${commit.run.script.starter.url}")
    private String commitRunStarterScriptUrl;

    @Value("${pause.run.script.url}")
    private String pauseRunScriptUrl;

    @Value("${launch.script.url}")
    private String launchScriptUrl;

    public PipelineRun commitContainer(PipelineRun run, DockerRegistry registry,
                                       String newImageName, boolean clearContainer, boolean stopPipeline) {
        final String containerId = kubernetesManager.getContainerIdFromKubernetesPod(
                run.getPodId(), run.getDockerImage());

        final String apiToken = authManager.issueTokenForCurrentUser(null).getToken();

        String dockerLogin;
        String dockerPassword;
        //Let's use pipe auth if it's enabled for registry
        if (registry.isPipelineAuth()) {
            dockerLogin = authManager.getAuthorizedUser();
            dockerPassword = apiToken;
        } else {
            dockerLogin = registry.getUserName() == null ? EMPTY : registry.getUserName();
            dockerPassword = registry.getPassword() == null ? EMPTY : registry.getPassword();
        }

        if (newImageName.startsWith(registry.getPath())) {
            newImageName = newImageName.replace(registry.getPath() + DELIMITER, EMPTY);
        }

        Matcher matcher = GROUP_AND_IMAGE.matcher(newImageName);
        Assert.isTrue(matcher.find(),
                messageHelper.getMessage(MessageConstants.ERROR_TOOL_GROUP_IS_NOT_PROVIDED, newImageName));
        String toolGroupName = matcher.group(1);
        ToolGroup toolGroup = toolGroupManager.loadByNameOrId(registry.getPath() + DELIMITER + toolGroupName);

        try {
            Assert.notNull(containerId,
                    messageHelper.getMessage(MessageConstants.ERROR_CONTAINER_ID_FOR_RUN_NOT_FOUND, run.getId()));

            String commitContainerCommand = String.format(
                    COMMIT_COMMAND_TEMPLATE,
                    commitRunStarterScriptUrl,
                    preferenceManager.getPreference(SystemPreferences.BASE_API_HOST),
                    apiToken,
                    commitScriptsDistributionsUrl,
                    preferenceManager.getPreference(SystemPreferences.BASE_PIPE_DISTR_URL),
                    run.getId(),
                    containerId,
                    clearContainer,
                    stopPipeline,
                    preferenceManager.getPreference(SystemPreferences.COMMIT_TIMEOUT),
                    registry.getPath(),
                    registry.getId(),
                    toolGroup.getId(),
                    newImageName,
                    dockerLogin,
                    dockerPassword,
                    registry.isPipelineAuth(),
                    preferenceManager.getPreference(SystemPreferences.PRE_COMMIT_COMMAND_PATH),
                    preferenceManager.getPreference(SystemPreferences.POST_COMMIT_COMMAND_PATH)
            );

            Process sshConnection = submitCommandViaSSH(run.getInstance().getNodeIP(), commitContainerCommand);
            boolean isFinished = sshConnection.waitFor(
                    preferenceManager.getPreference(SystemPreferences.COMMIT_TIMEOUT), TimeUnit.SECONDS);
            Assert.state(isFinished && sshConnection.exitValue() == 0,
                    messageHelper.getMessage(MessageConstants.ERROR_RUN_PIPELINES_COMMIT_FAILED, run.getId()));
        } catch (IllegalStateException | IllegalArgumentException | IOException e) {
            LOGGER.error(e.getMessage());
            updatePipelineRunCommitStatus(run, CommitStatus.FAILURE);
            throw new CmdExecutionException(COMMIT_COMMAND_DESCRIPTION, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            updatePipelineRunCommitStatus(run, CommitStatus.FAILURE);
            throw new CmdExecutionException(COMMIT_COMMAND_DESCRIPTION, e);
        }
        updatePipelineRunCommitStatus(run, CommitStatus.COMMITTING);
        return run;
    }

    @Async("pauseRunExecutor")
    public void pauseRun(PipelineRun run) {
        try {
            final String containerId = kubernetesManager.getContainerIdFromKubernetesPod(
                    run.getPodId(), run.getDockerImage());
            final String apiToken = authManager.issueTokenForCurrentUser().getToken();
            Assert.notNull(containerId,
                    messageHelper.getMessage(MessageConstants.ERROR_CONTAINER_ID_FOR_RUN_NOT_FOUND, run.getId()));

            String pauseRunCommand = String.format(
                    PAUSE_COMMAND_TEMPLATE,
                    pauseRunScriptUrl,
                    preferenceManager.getPreference(SystemPreferences.BASE_API_HOST),
                    apiToken,
                    commitScriptsDistributionsUrl,
                    preferenceManager.getPreference(SystemPreferences.BASE_PIPE_DISTR_URL),
                    run.getId(),
                    containerId,
                    preferenceManager.getPreference(SystemPreferences.COMMIT_TIMEOUT),
                    run.getDockerImage(),
                    run.getTaskName(),
                    preferenceManager.getPreference(SystemPreferences.PRE_COMMIT_COMMAND_PATH)
            );

            RunInstance instance = run.getInstance();
            kubernetesManager.addNodeLabel(instance.getNodeName(), KubernetesConstants.PAUSED_NODE_LABEL,
                    TaskStatus.PAUSED.name());

            run.setPodIP(null);
            runManager.updatePodIP(run);
            runManager.updateServiceUrl(run.getId(), new PipelineRunServiceUrlVO());

            Process sshConnection = submitCommandViaSSH(instance.getNodeIP(), pauseRunCommand);

            //TODO: change SystemPreferences.COMMIT_TIMEOUT in according to
            // f_EPMCMBIBPC-2025_add_lastStatusUpdate_time branche
            boolean isFinished = sshConnection.waitFor(
                    preferenceManager.getPreference(SystemPreferences.COMMIT_TIMEOUT), TimeUnit.SECONDS);

            if (isFinished && sshConnection.exitValue() == COMMAND_CANNOT_EXECUTE_CODE) {
                //TODO: change in according to f_EPMCMBIBPC-2025_add_lastStatusUpdate_time branche
                run.setStatus(TaskStatus.RUNNING);
                runManager.updatePipelineStatus(run);
                kubernetesManager.removeNodeLabel(instance.getNodeName(), KubernetesConstants.PAUSED_NODE_LABEL);
                return;
            }
            Assert.state(sshConnection.exitValue() == 0,
                    messageHelper.getMessage(MessageConstants.ERROR_RUN_PIPELINES_PAUSE_FAILED, run.getId()));

            kubernetesManager.deletePod(run.getPodId());
            cloudFacade.stopInstance(instance.getCloudRegionId(), instance.getNodeId());
            kubernetesManager.deleteNode(instance.getNodeName());

            run.setStatus(TaskStatus.PAUSED);
            runManager.updatePipelineStatus(run);
        } catch (Exception e) {
            failRunAndTerminateNode(run, e);
            throw new IllegalArgumentException(PAUSE_COMMAND_DESCRIPTION, e);
        }
    }

    @Async("pauseRunExecutor")
    public void resumeRun(PipelineRun run, List<String> endpoints) {
        try {

            AbstractCloudRegion cloudRegion = regionManager.load(run.getInstance().getCloudRegionId());
            cloudFacade.startInstance(cloudRegion.getId(), run.getInstance().getNodeId());

            kubernetesManager.waitForNodeReady(run.getInstance().getNodeName(),
                    run.getId().toString(), cloudRegion.getRegionCode());
            String cmd = String.format(PipelineLauncher.LAUNCH_TEMPLATE, launchScriptUrl,
                    launchScriptUrl, "", "", run.getActualCmd());
            List<EnvVar> envVars = Collections.singletonList(
                    new EnvVar(SystemParams.RESUMED_RUN.getEnvName(), "true", null));
            executor.launchRootPod(cmd, run, envVars, endpoints,
                    run.getPodId(), run.getId().toString(), null, null, false);
            kubernetesManager.removeNodeLabel(run.getInstance().getNodeName(),
                    KubernetesConstants.PAUSED_NODE_LABEL);
            run.setStatus(TaskStatus.RUNNING);
            runManager.updatePipelineStatus(run);
        } catch (Exception e) {
            failRunAndTerminateNode(run, e);
            throw new IllegalArgumentException(REJOIN_COMMAND_DESCRIPTION, e);
        }
    }

    private Process submitCommandViaSSH(String ip, String commandToExecute) throws IOException {
        String kubePipelineNodeUserName  = preferenceManager.getPreference(SystemPreferences.COMMIT_USERNAME);
        String pemKeyPath = preferenceManager.getPreference(SystemPreferences.COMMIT_DEPLOY_KEY);

        String sshCommand = String.format("%s %s %s %s %s %s %s %s %s %s %s",
                SSH_COMMAND,
                SSH_CMD_OPTION, STRICT_HOST_KEY_CHECKING_NO,
                SSH_CMD_OPTION, GLOBAL_KNOWN_HOSTS_FILE,
                SSH_CMD_OPTION, USER_KNOWN_HOSTS_FILE,
                IDENTITY_PARAM, pemKeyPath,
                kubePipelineNodeUserName + "@" + ip,
                commandToExecute
        );
        LOGGER.info(messageHelper.getMessage(MessageConstants.INFO_EXECUTE_COMMIT_RUN_PIPELINES, sshCommand));
        return Runtime.getRuntime().exec(sshCommand);
    }

    private void updatePipelineRunCommitStatus(PipelineRun run, CommitStatus commitStatus) {
        run.setCommitStatus(commitStatus);
        run.setLastChangeCommitTime(DateUtils.now());
        runManager.updatePipelineCommitStatus(run);
    }

    private void failRunAndTerminateNode(PipelineRun run, Exception e) {
        LOGGER.error(e.getMessage());
        run.setEndDate(DateUtils.now());
        run.setStatus(TaskStatus.FAILURE);
        runManager.updatePipelineStatus(run);
        nodesManager.terminateNode(run.getInstance().getNodeName());
    }
}

