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

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class KubernetesDeploymentMonitorImpl implements KubernetesDeploymentMonitor {

    private static final String NAMESPACE_DELIMITER = "@";
    private static final String DELIMITER = ",";

    private final KubernetesNotifier kubernetesNotifier;
    private final List<String> monitoredDeployments;
    private final String defaultNamespace;

    public KubernetesDeploymentMonitorImpl(
            final KubernetesNotifier kubernetesNotifier,
            final @Value("${monitor.k8s.deployment.names}") String monitoredDeployments,
            final @Value("${monitor.k8s.deployment.default.namespace}") String defaultNamespace) {
        this.kubernetesNotifier = kubernetesNotifier;
        this.monitoredDeployments =  Arrays.stream(monitoredDeployments.split(DELIMITER))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
        this.defaultNamespace = defaultNamespace;
    }

    @Override
    public void monitorDeployments() {
        if (CollectionUtils.isEmpty(monitoredDeployments)) {
            log.debug("No k8s deployments are monitored.");
            return;
        }
        monitoredDeployments.forEach(this::checkDeploymentStatus);
    }

    private void checkDeploymentStatus(final String deploymentName) {
        try (KubernetesClient client = new DefaultKubernetesClient()) {
            final String namespace = getDeploymentNamespace(deploymentName);
            final Deployment deployment = client.apps().deployments()
                    .inNamespace(namespace)
                    .withName(deploymentName).get();
            if (deployment == null) {
                log.debug("Failed to find deployment by name {}.", deploymentName);
                kubernetesNotifier.notifyMissingDeployment(deploymentName);
                return;
            }
            final Integer requiredReplicas = Optional.ofNullable(deployment.getSpec().getReplicas()).orElse(0);
            final Integer readyReplicas = Optional.ofNullable(deployment.getStatus().getReadyReplicas()).orElse(0);
            log.debug("Checking deployment {} status: required replicas {} - available replicas {}.",
                    deploymentName, requiredReplicas, readyReplicas);
            if (!requiredReplicas.equals(readyReplicas)) {
                kubernetesNotifier.notifyDeploymentNotComplete(deploymentName, requiredReplicas, readyReplicas);
            }
        } catch (KubernetesClientException e) {
            log.error("Ann error occurred while sending request to k8s: {}", e.getMessage());
        }
    }

    private String getDeploymentNamespace(final String deploymentName) {
        if (deploymentName.contains(NAMESPACE_DELIMITER)) {
            return deploymentName.split(NAMESPACE_DELIMITER)[0];
        }
        return defaultNamespace;
    }
}
