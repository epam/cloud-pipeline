/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
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
 *
 */

package com.epam.pipeline.vmmonitor.service.vm;

import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.vmmonitor.model.vm.MissingLabelsSummary;
import com.epam.pipeline.vmmonitor.model.vm.MissingNodeSummary;
import com.epam.pipeline.vmmonitor.model.vm.VirtualMachine;
import com.epam.pipeline.vmmonitor.service.notification.VMNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

@Component
@Slf4j
public class VMNotifier {

    private final VMNotificationService notificationService;
    private final String missingNodeSubject;
    private final String missingNodeTemplatePath;
    private final String missingLabelsSubject;
    private final String missingLabelsTemplatePath;
    private final Queue<MissingNodeSummary> missingNodes;
    private final Queue<MissingLabelsSummary> missingLabelsSummaries;

    public VMNotifier(
            final VMNotificationService notificationService,
            @Value("${notification.missing-node.subject}") final String missingNodeSubject,
            @Value("${notification.missing-node.template}") final String missingNodeTemplatePath,
            @Value("${notification.missing-labels.subject}") final String missingLabelsSubject,
            @Value("${notification.missing-labels.template}") final String missingLabelsTemplatePath) {
        this.notificationService = notificationService;
        this.missingNodeSubject = missingNodeSubject;
        this.missingNodeTemplatePath = missingNodeTemplatePath;
        this.missingLabelsSubject = missingLabelsSubject;
        this.missingLabelsTemplatePath = missingLabelsTemplatePath;
        this.missingNodes = new LinkedList<>();
        this.missingLabelsSummaries = new LinkedList<>();
    }

    public void sendNotifications() {
        notifyOnQueuedElements(missingNodeSubject, missingNodeTemplatePath, missingNodes, "nodes", "missingNodes");
        notifyOnQueuedElements(missingLabelsSubject, missingLabelsTemplatePath, missingLabelsSummaries, "labels",
                               "missingLabelsSummaries");
    }

    public void queueMissingNodeNotification(final VirtualMachine vm, final List<PipelineRun> matchingRuns,
                                             final Long matchingPoolId) {
        missingNodes.add(new MissingNodeSummary(vm, matchingRuns, matchingPoolId));
    }

    public void checkMissingNodesQueue(final Set<String> runningVmsIds) {
        if (CollectionUtils.isNotEmpty(missingNodes)) {
            missingNodes.removeIf(missingNodeSummary ->
                                      !runningVmsIds.contains(missingNodeSummary.getVm().getInstanceId()));
        }
    }

    public void queueMissingLabelsNotification(final NodeInstance node, final VirtualMachine vm,
                                               final List<String> labels, final RunStatus runStatus,
                                               final Long poolId) {
        missingLabelsSummaries.add(new MissingLabelsSummary(node.getName(), labels, vm.getInstanceType(),
                                                            node.getCreationTimestamp(), runStatus, poolId));
    }

    private void notifyOnQueuedElements(final String emailSubject, final String emailTemplatePath,
                                        final Queue<?> queue, final String queueName, final String parameterName) {
        if (CollectionUtils.isNotEmpty(queue)) {
            log.debug("Sending notification on {} missing {}}", queue.size(), queueName);
            final Map<String, Object> parameters = Collections.singletonMap(parameterName, queue);
            notificationService.sendMessage(parameters, emailSubject, emailTemplatePath);
            queue.clear();
        } else {
            log.debug("No missing {} notifications queued.", queueName);
        }
    }
}
