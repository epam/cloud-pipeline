/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.entity.cloud.CloudInstanceOperationResult;
import com.epam.pipeline.entity.cloud.CloudInstanceState;
import com.epam.pipeline.entity.cluster.container.ImagePullPolicy;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.CommitStatus;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.exception.CmdExecutionException;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.cluster.performancemonitoring.ResourceMonitoringManager;
import com.epam.pipeline.manager.execution.PipelineLauncher;
import com.epam.pipeline.manager.execution.SystemParams;
import com.epam.pipeline.manager.pipeline.PipelineConfigurationManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.PipelineRunServiceUrlManager;
import com.epam.pipeline.manager.pipeline.RunLogManager;
import com.epam.pipeline.manager.pipeline.ToolGroupManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.utils.CommonUtils;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private static final String COMMIT_COMMAND_DESCRIPTION = "ssh session to commit pipeline run";
    private static final String CONTAINER_LAYERS_COUNT_COMMAND_DESCRIPTION =
            "ssh session to get docker container layers count";
    private static final String PAUSE_COMMAND_DESCRIPTION = "Error is occured during to pause pipeline run";
    private static final String REJOIN_COMMAND_DESCRIPTION = "Error is occured during to resume pipeline run";
    private static final String EMPTY = "";
    private static final String RESUME_RUN_TASK = "ResumePipelineRun";
    public static final String PAUSE_RUN_TASK = "PausePipelineRun";
    public static final String DELIMITER = "/";
    public static final int COMMAND_CANNOT_EXECUTE_CODE = 126;
    public static final String NONE = "NONE";

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
    private PipelineLauncher launcher;

    @Autowired
    private PipelineConfigurationManager configurationManager;

    @Autowired
    private NodesManager nodesManager;

    @Autowired
    private CloudRegionManager regionManager;

    @Autowired
    private RunLogManager runLogManager;

    @Autowired
    private PipelineRunServiceUrlManager serviceUrlManager;

    @Value("${commit.run.scripts.root.url}")
    private String commitScriptsDistributionsUrl;

    @Value("${commit.run.script.starter.url}")
    private String commitRunStarterScriptUrl;

    @Value("${container.layers.script.url}")
    private String containerLayersCountScriptUrl;

    @Value("${pause.run.script.url}")
    private String pauseRunScriptUrl;

    private Lock resumeLock = new ReentrantLock();

    public PipelineRun commitContainer(PipelineRun run, DockerRegistry registry,
                                       String newImageName, boolean clearContainer, boolean stopPipeline) {
        final String containerId = kubernetesManager.getContainerIdFromKubernetesPod(run.getPodId(), 
                run.getActualDockerImage());

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

            final String commitContainerCommand = DockerCommitCommand.builder()
                    .runScriptUrl(commitRunStarterScriptUrl)
                    .api(preferenceManager.getPreference(SystemPreferences.BASE_API_HOST))
                    .apiToken(apiToken)
                    .commitDistributionUrl(commitScriptsDistributionsUrl)
                    .distributionUrl(preferenceManager.getPreference(SystemPreferences.BASE_PIPE_DISTR_URL))
                    .runId(String.valueOf(run.getId()))
                    .containerId(containerId)
                    .cleanUp(String.valueOf(clearContainer))
                    .additionalEnvsToClean(getEnvsToCleanUp())
                    .stopPipeline(String.valueOf(stopPipeline))
                    .timeout(String.valueOf(preferenceManager.getPreference(SystemPreferences.COMMIT_TIMEOUT)))
                    .registryToPush(registry.getPath())
                    .registryToPushId(String.valueOf(registry.getId()))
                    .toolGroupId(String.valueOf(toolGroup.getId()))
                    .newImageName(newImageName)
                    .dockerLogin(dockerLogin)
                    .dockerPassword(dockerPassword)
                    .isPipelineAuth(String.valueOf(registry.isPipelineAuth()))
                    .preCommitCommand(preferenceManager.getPreference(SystemPreferences.PRE_COMMIT_COMMAND_PATH))
                    .postCommitCommand(preferenceManager.getPreference(SystemPreferences.POST_COMMIT_COMMAND_PATH))
                    .build()
                    .getCommand();

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

    public long getContainerLayers(final PipelineRun run) {
        final String containerId = kubernetesManager.getContainerIdFromKubernetesPod(run.getPodId(),
                run.getActualDockerImage());
        final long layersCount;
        try {
            Assert.notNull(containerId,
                    messageHelper.getMessage(MessageConstants.ERROR_CONTAINER_ID_FOR_RUN_NOT_FOUND, run.getId()));
            final String containerLayersCommand = DockerContainerLayersCommand.builder()
                    .runScriptUrl(containerLayersCountScriptUrl)
                    .containerId(containerId)
                    .build()
                    .getCommand();
            Process sshConnection = submitCommandViaSSH(run.getInstance().getNodeIP(), containerLayersCommand);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(sshConnection.getInputStream()))) {
                final String result = reader.lines().collect(Collectors.joining());
                layersCount = Long.parseLong(result);
            }
            boolean isFinished = sshConnection.waitFor(
                    preferenceManager.getPreference(SystemPreferences.GET_LAYERS_COUNT_TIMEOUT), TimeUnit.SECONDS);
            Assert.state(isFinished && sshConnection.exitValue() == 0,
                    messageHelper.getMessage(MessageConstants.ERROR_GET_CONTAINER_LAYERS_COUNT_FAILED, run.getId()));
        } catch (IllegalStateException | IllegalArgumentException | IOException e) {
            LOGGER.error(e.getMessage());
            throw new CmdExecutionException(CONTAINER_LAYERS_COUNT_COMMAND_DESCRIPTION, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CmdExecutionException(CONTAINER_LAYERS_COUNT_COMMAND_DESCRIPTION, e);
        }
        return layersCount;
    }

    @Async("pauseRunExecutor")
    public void pauseRun(final PipelineRun run, final boolean rerunPause) {
        try {
            if (!rerunPause) {
                final boolean scriptLaunchedSuccessfully = launchPauseRunScript(run);
                if (!scriptLaunchedSuccessfully) {
                    return;
                }
                addRunLog(run, messageHelper.getMessage(MessageConstants.INFO_LOG_PAUSE_COMPLETED), PAUSE_RUN_TASK,
                        TaskStatus.SUCCESS);
            }

            final RunInstance instance = run.getInstance();
            kubernetesManager.deletePod(run.getPodId());
            stopInstanceIfNeed(run.getId(), instance);
            kubernetesManager.deleteNode(instance.getNodeName());
            run.setStatus(TaskStatus.PAUSED);
            runManager.updatePipelineStatus(run);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            addRunLog(run, e.getMessage(), PAUSE_RUN_TASK);
            failRunAndTerminateNode(run, e);
            throw new IllegalArgumentException(PAUSE_COMMAND_DESCRIPTION, e);
        }
    }

    @Async("pauseRunExecutor")
    public void resumeRun(PipelineRun run, List<String> endpoints) {
        try {
            final AbstractCloudRegion cloudRegion = regionManager.load(run.getInstance().getCloudRegionId());
            final boolean instanceStartedSuccessfully = startInstanceIfNeed(run, run.getInstance().getNodeId(),
                    cloudRegion.getId());
            if (!instanceStartedSuccessfully) {
                return;
            }

            kubernetesManager.waitForNodeReady(run.getInstance().getNodeName(),
                    run.getId().toString(), cloudRegion.getRegionCode());

            launchPodIfRequired(run, endpoints);

            kubernetesManager.removeNodeLabel(run.getInstance().getNodeName(),
                    KubernetesConstants.PAUSED_NODE_LABEL);
            run.setStatus(TaskStatus.RUNNING);
            runManager.updatePipelineStatus(run);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            addRunLog(run, e.getMessage(), RESUME_RUN_TASK);
            failRunAndTerminateNode(run, e);
            throw new IllegalArgumentException(REJOIN_COMMAND_DESCRIPTION, e);
        }
    }

    private String getEnvsToCleanUp() {
        return preferenceManager.getPreference(SystemPreferences.ADDITIONAL_ENVS_TO_CLEAN).equals(EMPTY)
                ? NONE
                : "\"" + preferenceManager.getPreference(SystemPreferences.ADDITIONAL_ENVS_TO_CLEAN) + "\"";
    }

    //method is synchronized to prevent double pod creation
    private void launchPodIfRequired(final PipelineRun run, final List<String> endpoints) {
        resumeLock.lock();
        try {
            if (Objects.isNull(kubernetesManager.findPodById(run.getPodId()))) {
                final PipelineConfiguration configuration = getResumeConfiguration(run);
                launcher.launch(run, configuration, endpoints,  run.getId().toString(),
                        true, run.getPodId(), null, ImagePullPolicy.NEVER);
            }
        } finally {
            resumeLock.unlock();
        }
    }

    private PipelineConfiguration getResumeConfiguration(final PipelineRun run) {
        final PipelineConfiguration configuration = configurationManager.getConfigurationFromRun(run);
        final Map<String, String> envs = getResumeRunEnvVars(configuration);
        configuration.setEnvironmentParams(envs);
        return configuration;
    }

    private Map<String, String> getResumeRunEnvVars(final PipelineConfiguration configuration) {
        final Map<String, String> envs = configuration.getEnvironmentParams();
        final Map<String, String> resumeEnvVar = Collections.singletonMap(
                SystemParams.RESUMED_RUN.getEnvName(), "true");
        if (MapUtils.isEmpty(envs)) {
            return resumeEnvVar;
        }
        return CommonUtils.mergeMaps(envs, resumeEnvVar);
    }

    private void rollbackRunToPausedState(final PipelineRun run,
                                          final CloudInstanceOperationResult startInstanceResult) {
        final String msg = messageHelper.getMessage(MessageConstants.WARN_RESUME_RUN_FAILED,
                startInstanceResult.getMessage());
        addRunLog(run, msg, RESUME_RUN_TASK);
        LOGGER.warn(msg);
        run.setStatus(TaskStatus.PAUSED);
        // set stateReasonMessage here only for NotificationAspect, this status won't be persisted in DB,
        // but would be passed to aspect and used for RunStatus update
        run.setStateReasonMessage(msg);
        runManager.updatePipelineStatus(run);
    }

    Process submitCommandViaSSH(String ip, String commandToExecute) throws IOException {
        String kubePipelineNodeUserName = preferenceManager.getPreference(SystemPreferences.COMMIT_USERNAME);
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
        nodesManager.terminateRun(run);
    }

    private void addRunLog(final PipelineRun run, final String logMessage, final String taskName) {
        addRunLog(run, logMessage, taskName, null);
    }

    private void addRunLog(final PipelineRun run, final String logMessage, final String taskName,
                           final TaskStatus status) {
        final RunLog runLog = RunLog.builder()
                .date(DateUtils.now())
                .runId(run.getId())
                .instance(run.getPodId())
                .status(Objects.isNull(status) ? run.getStatus() : status)
                .taskName(taskName)
                .logText(logMessage)
                .build();
        runLogManager.saveLog(runLog);
    }

    private void removeUtilizationLevelTags(final PipelineRun run) {
        Stream.of(ResourceMonitoringManager.UTILIZATION_LEVEL_LOW,
                  ResourceMonitoringManager.UTILIZATION_LEVEL_HIGH)
            .filter(run::hasTag)
            .forEach(run::removeTag);
    }

    private void stopInstanceIfNeed(final Long runId, final RunInstance instance) {
        final CloudInstanceState cloudInstanceState = cloudFacade.getInstanceState(runId);
        validateInstanceState(cloudInstanceState);
        switch (cloudInstanceState) {
            case RUNNING:
                cloudFacade.stopInstance(instance.getCloudRegionId(), instance.getNodeId());
                break;
            case TERMINATED:
                throw new IllegalStateException(messageHelper
                        .getMessage(MessageConstants.ERROR_STOP_START_INSTANCE_TERMINATED, "stop"));
            case STOPPING:
            default:
                break;
        }
    }

    private boolean startInstanceIfNeed(final PipelineRun run, final String nodeId, final Long regionId) {
        final CloudInstanceState cloudInstanceState = cloudFacade.getInstanceState(run.getId());
        validateInstanceState(cloudInstanceState);
        switch (cloudInstanceState) {
            case STOPPED:
                final CloudInstanceOperationResult startInstanceResult = cloudFacade.startInstance(regionId, nodeId);
                if (startInstanceResult.getStatus() != CloudInstanceOperationResult.Status.OK) {
                    rollbackRunToPausedState(run, startInstanceResult);
                    return false;
                }
                break;
            case STOPPING:
                rollbackRunToPausedState(run, CloudInstanceOperationResult.fail(
                        messageHelper.getMessage(MessageConstants.WARN_INSTANCE_STOPPING)));
                return false;
            case TERMINATED:
                throw new IllegalStateException(messageHelper
                        .getMessage(MessageConstants.ERROR_STOP_START_INSTANCE_TERMINATED, "start"));
            default:
                break;
        }
        return true;
    }

    private boolean launchPauseRunScript(final PipelineRun run) throws IOException, InterruptedException {
        final String containerId = kubernetesManager.getContainerIdFromKubernetesPod(run.getPodId(),
                run.getActualDockerImage());
        final String apiToken = authManager.issueTokenForCurrentUser().getToken();
        Assert.notNull(containerId,
                messageHelper.getMessage(MessageConstants.ERROR_CONTAINER_ID_FOR_RUN_NOT_FOUND, run.getId()));

        final String pauseRunCommand = DockerPauseCommand.builder()
                .runPauseScriptUrl(pauseRunScriptUrl)
                .api(preferenceManager.getPreference(SystemPreferences.BASE_API_HOST))
                .apiToken(apiToken)
                .pauseDistributionUrl(commitScriptsDistributionsUrl)
                .distributionUrl(preferenceManager.getPreference(SystemPreferences.BASE_PIPE_DISTR_URL))
                .runId(String.valueOf(run.getId()))
                .containerId(containerId)
                .timeout(String.valueOf(preferenceManager.getPreference(SystemPreferences.COMMIT_TIMEOUT)))
                .newImageName(run.getActualDockerImage())
                .defaultTaskName(run.getTaskName())
                .preCommitCommand(preferenceManager.getPreference(SystemPreferences.PRE_COMMIT_COMMAND_PATH))
                .postCommitCommand(preferenceManager.getPreference(SystemPreferences.POST_COMMIT_COMMAND_PATH))
                .build()
                .getCommand();

        final RunInstance instance = run.getInstance();
        kubernetesManager.addNodeLabel(instance.getNodeName(), KubernetesConstants.PAUSED_NODE_LABEL,
                TaskStatus.PAUSED.name());

        run.setPodIP(null);
        removeUtilizationLevelTags(run);
        runManager.updateRunInfo(run);
        serviceUrlManager.clear(run.getId());

        final Process sshConnection = submitCommandViaSSH(instance.getNodeIP(), pauseRunCommand);

        //TODO: change SystemPreferences.COMMIT_TIMEOUT in according to
        // f_EPMCMBIBPC-2025_add_lastStatusUpdate_time branch
        final boolean isFinished = sshConnection.waitFor(
                preferenceManager.getPreference(SystemPreferences.PAUSE_TIMEOUT), TimeUnit.SECONDS);

        if (isFinished && sshConnection.exitValue() == COMMAND_CANNOT_EXECUTE_CODE) {
            //TODO: change in according to f_EPMCMBIBPC-2025_add_lastStatusUpdate_time branch
            run.setStatus(TaskStatus.RUNNING);
            runManager.updatePipelineStatus(run);
            kubernetesManager.removeNodeLabel(instance.getNodeName(), KubernetesConstants.PAUSED_NODE_LABEL);
            return false;
        }
        Assert.state(sshConnection.exitValue() == 0,
                messageHelper.getMessage(MessageConstants.ERROR_RUN_PIPELINES_PAUSE_FAILED, run.getId()));
        return true;
    }

    private void validateInstanceState(final CloudInstanceState state) {
        Assert.notNull(state, "Cannot determine instance state");
    }
}
