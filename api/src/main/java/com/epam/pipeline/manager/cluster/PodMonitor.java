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

package com.epam.pipeline.manager.cluster;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.cloud.InstanceTerminationState;
import com.epam.pipeline.entity.configuration.ExecutionEnvironment;
import com.epam.pipeline.entity.notification.NotificationSettings;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.notification.NotificationSettingsManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RestartRunManager;
import com.epam.pipeline.manager.pipeline.RunLogManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.scheduling.AbstractSchedulingManager;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import static com.epam.pipeline.entity.notification.NotificationSettings.NotificationType;

/**
 * A service class, that monitors running tasks and adjust their statuses
 */
@Service
@ConditionalOnProperty(value = "cluster.disable.task.monitoring", matchIfMissing = true, havingValue = "false")
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class PodMonitor extends AbstractSchedulingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PodMonitor.class);

    private final PodMonitorCore core;

    @Autowired
    public PodMonitor(final PodMonitorCore core) {
        this.core = core;
    }

    @PostConstruct
    public void setup() {
        core.setPreferenceManager(preferenceManager);
        scheduleFixedDelay(core::updateStatus, SystemPreferences.LAUNCH_TASK_STATUS_UPDATE_RATE, "Task Status Update");
    }

    public void updateStatus() {
        core.updateStatus();
    }

    @Component
    private static class PodMonitorCore {
    private static final String PIPELINE_ID_LABEL = "pipeline_id";
    private static final String CLUSTER_ID_LABEL = "cluster_id";
    private static final int DELETE_RETRY_ATTEMPTS = 5;
    private static final long DELETE_RETRY_DELAY = 5L;
    private static final int POD_RELEASE_TIMEOUT = 3000;

    private BlockingQueue<PipelineRun> queueToKill = new LinkedBlockingQueue<>();

    @Value("${kube.namespace}")
    private String kubeNamespace;

    private final RunLogManager runLogManager;
    private final PipelineRunManager pipelineRunManager;
    private final MessageHelper messageHelper;
    private final KubernetesManager kubernetesManager;
    private final NotificationSettingsManager notificationSettingsManager;
    private final NotificationManager notificationManager;
    private final ToolManager toolManager;
    private final RestartRunManager restartRunManager;
    private final CloudFacade cloudFacade;
    private PreferenceManager preferenceManager;

    @Autowired
    PodMonitorCore(final RunLogManager runLogManager,
                   final PipelineRunManager pipelineRunManager,
                   final MessageHelper messageHelper,
                   final KubernetesManager kubernetesManager,
                   final NotificationSettingsManager notificationSettingsManager,
                   final NotificationManager notificationManager,
                   final ToolManager toolManager,
                   final RestartRunManager restartRunManager,
                   final CloudFacade cloudFacade) {
        this.runLogManager = runLogManager;
        this.pipelineRunManager = pipelineRunManager;
        this.messageHelper = messageHelper;
        this.kubernetesManager = kubernetesManager;
        this.notificationSettingsManager = notificationSettingsManager;
        this.notificationManager = notificationManager;
        this.toolManager = toolManager;
        this.restartRunManager = restartRunManager;
        this.cloudFacade = cloudFacade;
    }

    private void setPreferenceManager(final PreferenceManager preferenceManager) {
        this.preferenceManager = preferenceManager;
    }

    /**
     * Queries statuses of pods of running tasks and adjust task statuses corresponding to pods statuses
     */
    @SchedulerLock(name = "PodMonitor_updateStatus", lockAtMostForString = "PT5M")
    public void updateStatus() {
        LOGGER.debug(messageHelper.getMessage(MessageConstants.DEBUG_MONITOR_CHECK_RUNNING));
        List<PipelineRun> running = pipelineRunManager.loadRunningAndTerminatedPipelineRuns();
        for (PipelineRun run : running) {
            if (!run.getExecutionPreferences().getEnvironment().isMonitored()) {
                if (run.getStatus().isFinal()) {
                    run.setTerminating(false);
                    pipelineRunManager.updatePipelineStatus(run);
                }
                LOGGER.debug("Skipping run {} in exec environment {}", run.getId(),
                        run.getExecutionPreferences().getEnvironment());
                continue;
            }
            LOGGER.debug("RUN ID {} status {} terminating {}", run.getId(), run.getStatus(), run.isTerminating());
            try (KubernetesClient client = kubernetesManager.getKubernetesClient()) {
                Pod pod = client.pods().inNamespace(kubeNamespace).withName(run.getPodId()).get();
                //check maybe run was already processed with master node
                PipelineRun currentRunState = pipelineRunManager.loadPipelineRun(run.getId());
                if (pod == null && currentRunState.getStatus().isFinal()) {
                    LOGGER.debug("Run ID {} is already in final status {}",
                            run.getId(), currentRunState.getStatus());
                    setRunFinished(currentRunState, pod, client);
                    continue;
                }
                if (pod == null || run.isTerminating()) {
                    setRunFinished(run, pod, client);
                } else {
                    PodStatus status = pod.getStatus();
                    // update pod IP, if it is not set yet
                    if (StringUtils.isEmpty(run.getPodIP())) {
                        if (StringUtils.isEmpty(status.getPodIP())) {
                            notifyIfExceedsThreshold(run, pod, NotificationType.LONG_INIT);
                        } else {
                            run.setPodIP(status.getPodIP());
                            pipelineRunManager.updatePodIP(run);
                        }
                    }

                    if (status.getPhase().equals(KubernetesConstants.POD_SUCCEEDED_PHASE)) {
                        run.setStatus(TaskStatus.SUCCESS);
                        run.setEndDate(DateUtils.now());
                        run.setTerminating(false);
                        //check that all tasks managed to reports its statuses
                        if (!checkChildrenPods(run, client, pod)) {
                            continue;
                        }
                    } else if (status.getPhase().equals(KubernetesConstants.POD_FAILED_PHASE) ||
                            (status.getReason() != null &&
                                    status.getReason().equals(KubernetesConstants.NODE_LOST))) {
                        setRunFinished(run, pod, client);
                    } else {
                        notifyIfExceedsThreshold(run, pod, NotificationType.LONG_RUNNING);
                        continue;
                    }
                }
                pipelineRunManager.updatePipelineStatus(run);
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
        LOGGER.debug(messageHelper.getMessage(MessageConstants.DEBUG_MONITOR_CHECK_FINISHED));
    }

    @Scheduled(fixedDelay = POD_RELEASE_TIMEOUT)
    public void releaseUnusedPods() {
        while (!queueToKill.isEmpty()) {
            try {
                PipelineRun pipelineRun = queueToKill.take();
                if (!pipelineRun.getExecutionPreferences().getEnvironment().isMonitored()) {
                    LOGGER.debug("Finishing non monitored run {} in {}",
                            pipelineRun.getId(), pipelineRun.getExecutionPreferences().getEnvironment());
                    finishRun(pipelineRun);
                    continue;
                }
                LOGGER.info(messageHelper.getMessage(MessageConstants.INFO_MONITOR_KILL_TASK,
                        pipelineRun.getPodId()));
                boolean isPipelineDeleted = killChildrenPods(pipelineRun.getPodId(), pipelineRun);
                if (isPipelineDeleted) {
                    finishRun(pipelineRun);
                }
            } catch (Exception e) {
                LOGGER.error(messageHelper
                        .getMessage(MessageConstants.ERROR_POD_RELEASE_TASK, e));
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Put tasks from specified {@link PipelineRun} to a queue for killing
     *
     * @param run a {@link PipelineRun} which tasks to kill
     */
    private void killAsync(PipelineRun run) {
        queueToKill.add(run);
    }

    private void notifyIfExceedsThreshold(PipelineRun run, Pod pod, NotificationType type) {
        NotificationSettings settings = notificationSettingsManager.load(type);
        if (settings == null || !settings.isEnabled()) {
            LOGGER.warn(messageHelper.getMessage(MessageConstants.ERROR_NOTIFICATION_SETTINGS_NOT_FOUND, type));
            return;
        }

        // get the diff measured in seconds
        long threshold = settings.getThreshold();
        long resendDelay = settings.getResendDelay();

        boolean isClusterNode = pod.getMetadata() != null && pod.getMetadata().getLabels() != null &&
                                pod.getMetadata().getLabels().containsKey(CLUSTER_ID_LABEL);

        if (threshold > 0 && !isClusterNode) {
            long duration = Duration.between(run.getStartDate().toInstant(), DateUtils.now().toInstant())
                .abs()
                .getSeconds();
            if (duration >= threshold) {
                Date lastNotificationDate = run.getLastNotificationTime();
                if (checkNeedOfNotificationResend(lastNotificationDate, resendDelay)) {
                    notificationManager.notifyLongRunningTask(run, settings);

                    run.setLastNotificationTime(DateUtils.now());
                    pipelineRunManager.updatePipelineRunLastNotification(run);
                }
            }
        }
    }

    private boolean checkChildrenPods(PipelineRun run, KubernetesClient client, Pod parent) {
        try {
            //check all children
            List<PipelineTask> tasks = runLogManager.loadTasksByRunId(run.getId());
            tasks.forEach(task -> {
                if (task.getStatus() == TaskStatus.RUNNING && !StringUtils.isEmpty(task.getInstance())) {
                    Pod pod = client.pods().inNamespace(kubeNamespace).withName(task.getInstance()).get();
                    getPodLogs(run, pod);
                }
            });
            clearWorkerNodes(run, client);
            //save luigi log
            getPodLogs(run, parent);
            //delete all pods
            LOGGER.debug("Clearing pods for successful pipeline: {}.", run.getPodId());
            client.pods().inNamespace(kubeNamespace).withLabel(PIPELINE_ID_LABEL, run.getPodId()).delete();
        } catch (KubernetesClientException e) {
            LOGGER.debug(e.getMessage(), e);

        }
        return ensurePipelineIsDeleted(String.valueOf(run.getId()), run.getPodId(), client);
    }

    private void clearWorkerNodes(PipelineRun run, KubernetesClient client) {
        List<Pod> workers = getWorkerPods(String.valueOf(run.getId()), client);
        workers.forEach(worker -> {
            String runIdLabel = getRunIdLabel(worker);
            LOGGER.debug("Clearing worker {} node for parent run {}.", runIdLabel, run.getId());
            Long workerId = Long.parseLong(runIdLabel);
            PipelineRun workerRun = pipelineRunManager.loadPipelineRun(workerId);
            getPodLogs(workerRun, worker);
            workerRun.setTerminating(false);
            workerRun.setStatus(run.getStatus());
            workerRun.setEndDate(run.getEndDate());
            pipelineRunManager.updatePipelineStatus(workerRun);
            checkAndUpdateInstanceState(workerRun, false);
            client.pods().inNamespace(kubeNamespace)
                    .withLabel(KubernetesConstants.RUN_ID_LABEL, runIdLabel).delete();
        });
    }

    private void checkAndUpdateInstanceState(final PipelineRun run, final boolean allowRestart) {
        final RunInstance instance = run.getInstance();
        if (instance == null ||
                run.getExecutionPreferences().getEnvironment() != ExecutionEnvironment.CLOUD_PLATFORM ||
                StringUtils.isBlank(instance.getNodeId()) || run.getStatus() == TaskStatus.STOPPED) {
            return;
        }

        final Optional<InstanceTerminationState> state = cloudFacade.getInstanceTerminationState(
                instance.getCloudRegionId(), instance.getNodeId());
        state.ifPresent(reason -> {
            pipelineRunManager.updateStateReasonMessage(run, reason.getStateMessage());
            if (allowRestart && shouldRerunBatchRun(run, reason.getStateCode())) {
                LOGGER.debug("Restarting run {}", run.getId());
                pipelineRunManager.restartRun(run);
            }
        });

    }

    private String getRunIdLabel(Pod worker) {
        return worker.getMetadata().getLabels().get(KubernetesConstants.RUN_ID_LABEL);
    }

    private void getPodLogs(PipelineRun pipelineRun, Pod pod) {
        String log = "";
        TaskStatus status = getStatus(pipelineRun, pod);
        String instance = pod == null ? pipelineRun.getPodId() : pod.getMetadata().getName();
        try {
            if (pod != null) {
                LOGGER.debug("LOGS FOR POD: " + pod.getMetadata().getName());
                log = kubernetesManager.getPodLogs(pod.getMetadata().getName(),
                        preferenceManager.getPreference(SystemPreferences.SYSTEM_LIMIT_LOG_LINES));
            }
        } catch (KubernetesClientException e) {
            LOGGER.debug(e.getMessage(), e);
        } finally {
            saveLog(pipelineRun, instance, log, status);
        }
    }

    private TaskStatus getStatus(PipelineRun pipelineRun, Pod pod) {
        TaskStatus status;
        if (pod == null || pod.getStatus() == null || pod.getStatus().getPhase() == null) {
            return pipelineRun.getStatus();
        }
        switch (pod.getStatus().getPhase()) {
            case KubernetesConstants.POD_SUCCEEDED_PHASE: {
                status = TaskStatus.SUCCESS;
                break;
            }
            case KubernetesConstants.POD_FAILED_PHASE: {
                status = TaskStatus.FAILURE;
                break;
            }
            default: {
                status = pipelineRun.getStatus();
                break;
            }
        }
        return status;
    }

    private void saveLog(PipelineRun pipelineRun, String instance, String log, TaskStatus status) {
        RunLog runLog = new RunLog();
        runLog.setDate(DateUtils.now());
        runLog.setLogText(log);
        setTaskName(pipelineRun, runLog, instance);
        runLog.setStatus(status);
        runLog.setRunId(pipelineRun.getId());
        runLog.setInstance(instance);
        runLogManager.saveLog(runLog);
    }

    private void setTaskName(PipelineRun pipelineRun, RunLog runLog, String pod) {
        String pipelineName = pipelineRun.getTaskName();
        if (pipelineRun.getPodId().equals(pod)) {
            runLog.setTaskName(pipelineName);
        } else {
            String taskName = runLogManager.loadTaskByInstance(pod, pipelineRun);
            if (taskName != null) {
                runLog.setTaskName(taskName);
            } else {
                runLog.setTaskName(pipelineName);
            }
        }
    }

    private void setRunFinished(PipelineRun run, Pod pod, KubernetesClient client) {
        savePodStatus(run, pod, client);
        checkAndUpdateInstanceState(run, true);
        run.setStatus(run.getStatus().isFinal() ? run.getStatus() : TaskStatus.FAILURE);
        run.setTerminating(true);
        run.setEndDate(DateUtils.now());
        notificationManager.removeNotificationTimestamps(run.getId());
        killAsync(run);
    }

    private void savePodStatus(PipelineRun run, Pod pod, KubernetesClient client) {
        StringBuilder status = new StringBuilder(run.getPodStatus() == null ? "" : run.getPodStatus());
        if (pod == null) {
            status.append(KubernetesConstants.NODE_LOST);
            pipelineRunManager.updatePodStatus(run.getId(), status.toString());
        } else {
            List<ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();
            // if something happens in container
            if (!CollectionUtils.isEmpty(containerStatuses)) {
                status.append(containerStatuses.stream()
                        // we don't need to store successful completed containers info
                        .filter(containerStatus ->
                                containerStatus.getState() != null
                                        && containerStatus.getState().getTerminated() != null
                                        && containerStatus.getState().getTerminated().getExitCode() != 0)
                        .map(containerStatus -> String.format("%s (%s)",
                                containerStatus.getState().getTerminated().getReason(),
                                containerStatus.getState().getTerminated().getExitCode()))
                        .collect(Collectors.joining(",")));
            }
            if (StringUtils.isEmpty(status.toString())) {
                return;
            }
            Node node = StringUtils.isBlank(run.getInstance().getNodeName()) ?
                    null : client.nodes().withName(run.getInstance().getNodeName()).get();
            if (node == null) {
                node = findAvailableNodeByRunIdLabel(client, run.getId().toString());
            }
            if (node == null) {
                pipelineRunManager.updatePodStatus(run.getId(), status.toString());
                return;
            }
            pipelineRunManager.updatePodStatus(run.getId(),
                    kubernetesManager.updateStatusWithNodeConditions(status, node));
        }
    }

    private Node findAvailableNodeByRunIdLabel(KubernetesClient client, String runId) {
        List<Node> nodes = client.nodes().withLabel(KubernetesConstants.RUN_ID_LABEL, runId).list().getItems();
        if (CollectionUtils.isEmpty(nodes)) {
            return null;
        }
        return nodes.get(0);
    }

    private void finishRun(PipelineRun pipelineRun) {
        pipelineRun.setTerminating(false);
        pipelineRunManager.updatePipelineStatus(pipelineRun);
    }

    private boolean killChildrenPods(String podId, PipelineRun run) {
        LOGGER.info(messageHelper.getMessage(MessageConstants.INFO_MONITOR_KILL_TASK, podId));
        Integer preference = preferenceManager.getPreference(SystemPreferences.SYSTEM_LIMIT_LOG_LINES);
        try (KubernetesClient client = kubernetesManager.getKubernetesClient()) {
            //get pipeline logs
            String log = "";
            try {
                log = kubernetesManager.getPodLogs(run.getPodId(), preference);
            } catch (KubernetesClientException e) {
                LOGGER.error(e.getMessage(), e);
            }
            //delete pipeline pod
            client.pods().inNamespace(kubeNamespace).withName(run.getPodId())
                .withGracePeriod(0L).delete();

            PodList podList =
                client.pods().inNamespace(kubeNamespace).withLabel(PIPELINE_ID_LABEL, podId)
                    .list();
            podList.getItems().forEach(pod -> {
                LOGGER.info(messageHelper
                                .getMessage(MessageConstants.INFO_MONITOR_KILL_TASK, pod.getMetadata().getName()));
                //skip pipeline pod, since it is already deleted
                if (pod.getMetadata().getName().equals(podId)) {
                    return;
                }
                getPodLogs(run, pod);
                client.pods().inNamespace(kubeNamespace).withName(pod.getMetadata().getName())
                    .delete();
            });

            clearWorkerNodes(run, client);
            //Pipeline logs should be saved the last to prevent ambiguous statuses
            saveLog(run, run.getPodId(), log, run.getStatus());

            //check that we really deleted all pods
            return ensurePipelineIsDeleted(String.valueOf(run.getId()), podId, client);
        }
    }

    private boolean ensurePipelineIsDeleted(String runId, String podId, KubernetesClient client) {
        List<Pod> leftPods = getChildPods(runId, podId, client);
        int count = 0;
        while (!CollectionUtils.isEmpty(leftPods) && count < DELETE_RETRY_ATTEMPTS) {

            client.pods().inNamespace(kubeNamespace)
                    .withLabel(PIPELINE_ID_LABEL, podId)
                    .withGracePeriod(0)
                    .delete();

            client.pods().inNamespace(kubeNamespace)
                    .withLabel(KubernetesConstants.POD_WORKER_NODE_LABEL, runId)
                    .withGracePeriod(0)
                    .delete();

            leftPods = getChildPods(runId, podId, client);
            count++;
            try {
                Thread.sleep(DELETE_RETRY_DELAY);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.error(e.getMessage(), e);
            }
        }
        return CollectionUtils.isEmpty(leftPods);
    }

    private List<Pod> getChildPods(String runId, String podId, KubernetesClient client) {
        List<Pod> children = new ArrayList<>();
        children.addAll(client.pods()
                .inNamespace(kubeNamespace)
                .withLabel(PIPELINE_ID_LABEL, podId)
                .list().getItems());
        children.addAll(getWorkerPods(runId, client));
        return children;
    }

    private List<Pod> getWorkerPods(String runId, KubernetesClient client) {
        return client.pods().inNamespace(kubeNamespace)
                .withLabel(KubernetesConstants.POD_WORKER_NODE_LABEL, runId)
                .list().getItems();
    }

    private boolean checkNeedOfNotificationResend(Date lastNotificationDate, long resendDelay) {
        return lastNotificationDate == null
               || resendDelay != 0
                  && Duration.between(lastNotificationDate.toInstant(), DateUtils.now().toInstant()).abs().getSeconds()
                     >= resendDelay;
    }

    private boolean shouldRerunBatchRun(PipelineRun run, String stateReason) {
        boolean isSpot = run.getInstance().getSpot() != null && run.getInstance().getSpot();
        return run.getStatus() != TaskStatus.STOPPED && isSpot && isParentBatchJob(run) &&
                isStateReasonForRestart(stateReason) && checkRetryRestartCount(run.getId());
    }

    private boolean isParentBatchJob(PipelineRun run) {
        return isNotClusterRun(run)
                && run.getParentRunId() == null
                && run.getExecutionPreferences().getEnvironment() == ExecutionEnvironment.CLOUD_PLATFORM
                && CollectionUtils.isEmpty(toolManager.loadByNameOrId(run.getDockerImage()).getEndpoints());
    }

    private boolean isNotClusterRun(PipelineRun run) {
        return run.getNodeCount() == null || run.getNodeCount() == 0;
    }

    private boolean isStateReasonForRestart(String stateReason) {
        final List<String> rerunReasons = preferenceManager.getPreference(
                SystemPreferences.INSTANCE_RESTART_STATE_REASONS);
        if (CollectionUtils.isEmpty(rerunReasons)) {
            LOGGER.debug(messageHelper.getMessage(MessageConstants.ERROR_RESTART_STATE_REASONS_NOT_FOUND));
            return false;
        }
        return rerunReasons.contains(stateReason);
    }

    private boolean checkRetryRestartCount(Long runId) {
        Integer countOfRestartRun = restartRunManager.countRestartRuns(runId);
        if (preferenceManager.getPreference(SystemPreferences.CLUSTER_BATCH_RETRY_COUNT) <= 0 ||
                countOfRestartRun >= preferenceManager.getPreference(SystemPreferences.CLUSTER_BATCH_RETRY_COUNT)) {
            LOGGER.debug(messageHelper.getMessage(
                    MessageConstants.ERROR_EXCEED_MAX_RESTART_RUN_COUNT, runId));
            return false;
        }
        return true;
    }
    }
}
