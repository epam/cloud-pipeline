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

import com.epam.pipeline.monitor.model.node.NodeGpuUsages;
import com.epam.pipeline.monitor.monitoring.MonitoringService;
import com.epam.pipeline.monitor.rest.CloudPipelineAPIClient;
import com.epam.pipeline.monitor.rest.NodeReporterAPIExecutor;
import com.epam.pipeline.monitor.service.elasticsearch.MonitoringElasticseachService;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NodeGpuUsageMonitoringService implements MonitoringService {

    private static final int CONNECTION_TIMEOUT_MS = 2 * 1000;
    private static final String UNRESOLVED_POD = "unresolved pod";
    private static final String UNRESOLVED_NODE = "unresolved node";

    private final String monitorEnabledPreferenceName;
    private final CloudPipelineAPIClient cloudPipelineClient;
    private final Executor executor;
    private final NodeReporterService nodeReporterService;
    private final NodeReporterAPIExecutor nodeReporterClient;
    private final MonitoringElasticseachService monitoringElasticseachService;

    public NodeGpuUsageMonitoringService(@Value("${preference.name.usage.node.gpu.enable}")
                                         final String monitorEnabledPreferenceName,
                                         @Value("${monitoring.gpu.usage.pool.size:1}") final int poolSize,
                                         final CloudPipelineAPIClient cloudPipelineClient,
                                         final NodeReporterService nodeReporterService,
                                         final NodeReporterAPIExecutor nodeReporterClient,
                                         final MonitoringElasticseachService monitoringElasticseachService) {
        this.monitorEnabledPreferenceName = monitorEnabledPreferenceName;
        this.cloudPipelineClient = cloudPipelineClient;
        this.executor = Executors.newFixedThreadPool(poolSize);
        this.nodeReporterService = nodeReporterService;
        this.nodeReporterClient = nodeReporterClient;
        this.monitoringElasticseachService = monitoringElasticseachService;
    }

    @Override
    public void monitor() {
        if (!cloudPipelineClient.getBooleanPreference(monitorEnabledPreferenceName)) {
            log.debug("Gpu usage monitor is not enabled");
            return;
        }
        
        try (KubernetesClient client = getKubernetesClient()) {
            log.info("Collecting nodes gpu usages...");
            final Set<String> nodeNames = nodeReporterService.getMonitoringNodeNames(client);
            final List<Pod> pods = nodeReporterService.getReportingPods(client, nodeNames);
            final List<NodeGpuUsages> usages = collectUsages(pods);

            monitoringElasticseachService.saveGpuUsages(usages);
        } catch (KubernetesClientException e) {
            log.error("An error occurred while sending request to k8s", e);
        }
    }

    private List<NodeGpuUsages> collectUsages(final List<Pod> reportingPods) {
        return requestUsages(reportingPods).stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    private List<CompletableFuture<NodeGpuUsages>> requestUsages(final List<Pod> reporterPods) {
        return reporterPods.stream()
                .map(pod -> requestUsages(pod)
                        .exceptionally(e -> {
                            log.error("Failed to collect usages from {} for {}.",
                                    getPodName(pod).orElse(UNRESOLVED_POD),
                                    getNodeName(pod).orElse(UNRESOLVED_NODE), e);
                            return NodeGpuUsages.builder().build();
                        }))
                .collect(Collectors.toList());
    }

    private CompletableFuture<NodeGpuUsages> requestUsages(final Pod pod) {
        return CompletableFuture.supplyAsync(() -> getStats(pod).orElseThrow(IllegalArgumentException::new), executor);
    }

    private Optional<NodeGpuUsages> getStats(final Pod pod) {
        final String nodename = getNodeName(pod).orElse(UNRESOLVED_NODE);
        log.info("Retrieving usages from {} for {}...", getPodName(pod).orElse(UNRESOLVED_POD), nodename);
        return getPodIp(pod)
                .map(nodeReporterClient::getGpuStats)
                .map(stat -> NodeGpuUsages.builder()
                        .usages(stat)
                        .nodename(nodename)
                        .timestamp(LocalDateTime.now())
                        .build());
    }
    
    private KubernetesClient getKubernetesClient() {
        return new DefaultKubernetesClient(new ConfigBuilder()
                .withConnectionTimeout(CONNECTION_TIMEOUT_MS)
                .build());
    }

    private static Optional<String> getPodName(final Pod pod) {
        return Optional.ofNullable(pod)
                .map(Pod::getMetadata)
                .map(ObjectMeta::getName);
    }

    private static Optional<String> getNodeName(final Pod pod) {
        return Optional.ofNullable(pod)
                .map(Pod::getSpec)
                .map(PodSpec::getNodeName);
    }

    private static Optional<String> getPodIp(final Pod pod) {
        return Optional.ofNullable(pod)
                .map(Pod::getStatus)
                .map(PodStatus::getPodIP);
    }
}
