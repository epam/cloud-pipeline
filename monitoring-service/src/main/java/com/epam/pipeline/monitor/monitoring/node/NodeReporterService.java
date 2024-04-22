/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
 */

package com.epam.pipeline.monitor.monitoring.node;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class NodeReporterService {

    private static final String NAME_LABEL = "name";
    private static final String LABEL_ENABLED = "true";

    private final String monitoringNodeLabel;
    private final String reportingPodName;
    private final String namespace;

    public NodeReporterService(@Value("${monitoring.node.label:cloud-pipeline/cp-node-monitor}")
                               final String monitoringNodeLabel,
                               @Value("${node.reporter.srv.pod.name:cp-node-reporter}")
                               final String reportingPodName,
                               @Value("${node.reporter.srv.namespace:default}") final String namespace) {
        this.monitoringNodeLabel = monitoringNodeLabel;
        this.reportingPodName = reportingPodName;
        this.namespace = namespace;
    }

    public Set<String> getMonitoringNodeNames(final KubernetesClient client) {
        return monitoringNodes(client)
                .map(Node::getMetadata)
                .filter(Objects::nonNull)
                .map(ObjectMeta::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public List<Pod> getReportingPods(final KubernetesClient client, final Set<String> monitoringNodeNames) {
        return reportingPods(client)
                .filter(pod -> monitoringNodeNames.contains(pod.getSpec().getNodeName()))
                .collect(Collectors.toList());
    }

    private Stream<Node> monitoringNodes(final KubernetesClient client) {
        return Optional.ofNullable(client.nodes()
                        .withLabel(monitoringNodeLabel, LABEL_ENABLED)
                        .list()
                        .getItems())
                .map(List::stream)
                .orElseGet(Stream::empty);
    }

    private Stream<Pod> reportingPods(final KubernetesClient client) {
        return Optional.ofNullable(client.pods()
                        .inNamespace(namespace)
                        .withLabel(NAME_LABEL, reportingPodName)
                        .list()
                        .getItems())
                .map(List::stream)
                .orElseGet(Stream::empty);
    }
}
