/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.vmmonitor.model.vm.VirtualMachine;
import com.epam.pipeline.vmmonitor.service.notification.VMNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class VMNotifier {

    private final VMNotificationService notificationService;
    private final String missingNodeSubject;
    private final String missingNodeTemplatePath;
    private final String missingLabelsSubject;
    private final String missingLabelsTemplatePath;

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
    }

    public void notifyMissingNode(final VirtualMachine vm) {
        final Map<String, Object> parameters = new HashMap<>();
        addVmParameters(vm, parameters);
        log.debug("Sending missing node notification for VM {} {}", vm.getInstanceId(), vm.getCloudProvider());
        notificationService.sendMessage(parameters, missingNodeSubject, missingNodeTemplatePath);
    }

    public void notifyMissingLabels(final VirtualMachine vm,
                                    final NodeInstance node,
                                    final List<String> labels) {
        final Map<String, Object> parameters = new HashMap<>();
        addVmParameters(vm, parameters);
        addNodeParameters(node, parameters);
        parameters.put("missingLabels", labels.stream().collect(Collectors.joining(",")));
        log.debug("Sending missing labels notification for VM {} {}", vm.getInstanceId(), vm.getCloudProvider());
        notificationService.sendMessage(parameters, missingLabelsSubject, missingLabelsTemplatePath);
    }

    private void addNodeParameters(final NodeInstance node, final Map<String, Object> parameters) {
        parameters.put("nodeName", node.getName());
    }

    private void addVmParameters(final VirtualMachine vm, final Map<String, Object> parameters) {
        parameters.put("instanceId", vm.getInstanceId());
        parameters.put("privateIp", vm.getPrivateIp());
        parameters.put("provider", vm.getCloudProvider().name());
        parameters.put("instanceName", vm.getInstanceName());
    }
}
