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
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RestartRunManager;
import com.epam.pipeline.manager.pipeline.RunLogManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Service
@ConditionalOnProperty(value = "cluster.disable.task.monitoring",
    matchIfMissing = true,
    havingValue = "false")
public class PodReleaseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PodReleaseService.class);
    private static final String PIPELINE_ID_LABEL = "pipeline_id";
    private static final int DELETE_RETRY_ATTEMPTS = 5;
    private static final long DELETE_RETRY_DELAY = 5L;

    private final MessageHelper messageHelper;
    private final PipelineRunManager pipelineRunManager;
    private final KubernetesManager kubernetesManager;
    private final RunLogManager runLogManager;
    private final ToolManager toolManager;
    private final RestartRunManager restartRunManager;
    private final CloudFacade cloudFacade;
    private final PreferenceManager preferenceManager;
    private final String kubeNamespace;
    private final BlockingQueue<PipelineRun> queueToKill;

    @Autowired
    public PodReleaseService(final MessageHelper messageHelper,
                             final PipelineRunManager pipelineRunManager,
                             final KubernetesManager kubernetesManager,
                             final RunLogManager runLogManager,
                             final ToolManager toolManager,
                             final RestartRunManager restartRunManager,
                             final CloudFacade cloudFacade,
                             final PreferenceManager preferenceManager,
                             final @Value("${kube.namespace}")
                                 String kubeNamespace) {
        this.messageHelper = messageHelper;
        this.pipelineRunManager = pipelineRunManager;
        this.kubernetesManager = kubernetesManager;
        this.runLogManager = runLogManager;
        this.toolManager = toolManager;
        this.restartRunManager = restartRunManager;
        this.cloudFacade = cloudFacade;
        this.preferenceManager = preferenceManager;
        this.kubeNamespace = kubeNamespace;
        this.queueToKill = new LinkedBlockingQueue<>();
    }

    @Scheduled(fixedDelayString = "${cluster.unused.pod.release.rate:1000}")
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
    public void killAsync(PipelineRun run) {
        queueToKill.add(run);
    }

    void clearWorkerNodes(PipelineRun run, KubernetesClient client) {
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

    void getPodLogs(PipelineRun pipelineRun, Pod pod) {
        String log = "";
        TaskStatus status = getStatus(pipelineRun, pod);
        String instance = pod == null ? pipelineRun.getPodId() : pod.getMetadata().getName();
        try {
            if (pod != null) {
                LOGGER.debug("LOGS FOR POD: " + pod.getMetadata().getName());
                log = kubernetesManager.getPodLogs(pod.getMetadata().getName(),
                                                   preferenceManager
                                                       .getPreference(SystemPreferences.SYSTEM_LIMIT_LOG_LINES));
            }
        } catch (KubernetesClientException e) {
            LOGGER.debug(e.getMessage(), e);
        } finally {
            saveLog(pipelineRun, instance, log, status);
        }
    }

    void checkAndUpdateInstanceState(final PipelineRun run, final boolean allowRestart) {
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

    boolean ensurePipelineIsDeleted(String runId, String podId, KubernetesClient client) {
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

    private List<Pod> getWorkerPods(String runId, KubernetesClient client) {
        return client.pods().inNamespace(kubeNamespace)
            .withLabel(KubernetesConstants.POD_WORKER_NODE_LABEL, runId)
            .list().getItems();
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

    private String getRunIdLabel(Pod worker) {
        return worker.getMetadata().getLabels().get(KubernetesConstants.RUN_ID_LABEL);
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

    private List<Pod> getChildPods(String runId, String podId, KubernetesClient client) {
        List<Pod> children = new ArrayList<>();
        children.addAll(client.pods()
                            .inNamespace(kubeNamespace)
                            .withLabel(PIPELINE_ID_LABEL, podId)
                            .list().getItems());
        children.addAll(getWorkerPods(runId, client));
        return children;
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

    private boolean isNotClusterRun(PipelineRun run) {
        return run.getNodeCount() == null || run.getNodeCount() == 0;
    }
}
