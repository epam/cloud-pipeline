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
import com.epam.pipeline.entity.notification.NotificationSettings;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.notification.NotificationSettingsManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RunLogManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.scheduling.AbstractSchedulingManager;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Date;
import java.util.List;
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
    private static final String PIPELINE_ID_LABEL = "pipeline_id";
    private static final String CLUSTER_ID_LABEL = "cluster_id";

    @Autowired private RunLogManager runLogManager;

    @Autowired private PipelineRunManager pipelineRunManager;

    @Autowired private MessageHelper messageHelper;

    @Autowired
    private KubernetesManager kubernetesManager;

    @Autowired
    private NotificationSettingsManager notificationSettingsManager;

    @Autowired
    private NotificationManager notificationManager;

    @Autowired
    private PodReleaseService podReleaseService;

    @Value("${kube.namespace}")
    private String kubeNamespace;

    @PostConstruct
    public void setup() {
        scheduleFixedDelay(this::updateStatus, SystemPreferences.LAUNCH_TASK_STATUS_UPDATE_RATE, "Task Status Update");
    }

    /**
     * Queries statuses of pods of running tasks and adjust task statuses corresponding to pods statuses
     */
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
                    podReleaseService.getPodLogs(run, pod);
                }
            });
            podReleaseService.clearWorkerNodes(run, client);
            //save luigi log
            podReleaseService.getPodLogs(run, parent);
            //delete all pods
            LOGGER.debug("Clearing pods for successful pipeline: {}.", run.getPodId());
            client.pods().inNamespace(kubeNamespace).withLabel(PIPELINE_ID_LABEL, run.getPodId()).delete();
        } catch (KubernetesClientException e) {
            LOGGER.debug(e.getMessage(), e);

        }
        return podReleaseService.ensurePipelineIsDeleted(String.valueOf(run.getId()), run.getPodId(), client);
    }

    private void setRunFinished(PipelineRun run, Pod pod, KubernetesClient client) {
        savePodStatus(run, pod, client);
        podReleaseService.checkAndUpdateInstanceState(run, true);
        run.setStatus(run.getStatus().isFinal() ? run.getStatus() : TaskStatus.FAILURE);
        run.setTerminating(true);
        run.setEndDate(DateUtils.now());
        notificationManager.removeNotificationTimestamps(run.getId());
        podReleaseService.killAsync(run);
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

    private boolean checkNeedOfNotificationResend(Date lastNotificationDate, long resendDelay) {
        return lastNotificationDate == null
               || resendDelay != 0
                  && Duration.between(lastNotificationDate.toInstant(), DateUtils.now().toInstant()).abs().getSeconds()
                     >= resendDelay;
    }
}
