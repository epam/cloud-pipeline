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

package com.epam.pipeline.vmmonitor.service.k8s;

import com.epam.pipeline.vmmonitor.service.notification.VMNotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class KubernetesNotifier {

    private final VMNotificationService notificationService;
    private final String missingDeploymentSubject;
    private final String missingDeploymentTemplate;
    private final String notReadyDeploymentSubject;
    private final String notReadyDeploymentTemplate;

    public KubernetesNotifier(
            final VMNotificationService notificationService,
            final @Value("${notification.missing-deploy.subject}") String missingDeploymentSubject,
            final @Value("${notification.missing-deploy.template}") String missingDeploymentTemplate,
            final @Value("${notification.not-ready-deploy.subject}") String notReadyDeploymentSubject,
            final @Value("${notification.not-ready-deploy.template}") String notReadyDeploymentTemplate) {
        this.notificationService = notificationService;
        this.missingDeploymentSubject = missingDeploymentSubject;
        this.missingDeploymentTemplate = missingDeploymentTemplate;
        this.notReadyDeploymentSubject = notReadyDeploymentSubject;
        this.notReadyDeploymentTemplate = notReadyDeploymentTemplate;
    }

    public void notifyMissingDeployment(final String deploymentName) {
        notificationService.sendMessage(Collections.singletonMap("deploymentName", deploymentName),
                missingDeploymentSubject, missingDeploymentTemplate);
    }

    public void notifyDeploymentNotComplete(final String deploymentName,
                                     final int required,
                                     final int ready) {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("deployment", deploymentName);
        parameters.put("requiredReplicas", required);
        parameters.put("readyReplicas", ready);
        notificationService.sendMessage(parameters, notReadyDeploymentSubject, notReadyDeploymentTemplate);
    }
}
