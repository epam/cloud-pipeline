package com.epam.pipeline.vmmonitor.service.node;

import com.epam.pipeline.entity.reporter.NodeReporterHostStats;
import com.epam.pipeline.entity.reporter.NodeReporterProcessStats;
import com.epam.pipeline.entity.reporter.NodeReporterStatsLimit;
import com.epam.pipeline.entity.reporter.NodeReporterStatsType;
import com.epam.pipeline.entity.reporter.NodeReporterStatsValue;
import com.epam.pipeline.vmmonitor.model.instance.NodeThresholdEvent;
import com.epam.pipeline.vmmonitor.service.Monitor;
import com.epam.pipeline.vmmonitor.service.Notifier;
import com.epam.pipeline.vmmonitor.service.pipeline.NodeStatsClient;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class NodeMonitor implements Monitor {

    private static final int CONNECTION_TIMEOUT_MS = 2 * 1000;
    private static final double PERCENT_MULTIPLIER = 100.0;
    private static final String NAME_LABEL = "name";
    private static final String LABEL_ENABLED = "true";
    private static final String UNRESOLVED_POD = "unresolved pod";
    private static final String UNRESOLVED_NODE = "unresolved node";

    private final Notifier<List<NodeThresholdEvent>> notifier;
    private final NodeStatsClient nodeStatsClient;
    private final Executor executor;
    private final String namespace;
    private final String monitoringNodeLabel;
    private final String reportingPodName;
    private final Map<NodeReporterStatsType, Long> thresholds;

    @Override
    public void monitor() {
        try (KubernetesClient client = getKubernetesClient()) {
            log.info("Collecting node threshold events...");
            final Set<String> nodeNames = getMonitoringNodeNames(client);
            final List<Pod> pods = getReportingPods(client, nodeNames);
            final List<NodeThresholdEvent> events = resolveEvents(pods);
            if (CollectionUtils.isNotEmpty(events)) {
                notifier.notify(events);
            }
        } catch (KubernetesClientException e) {
            log.error("An error occurred while sending request to k8s", e);
        }
    }

    public KubernetesClient getKubernetesClient() {
        return new DefaultKubernetesClient(new ConfigBuilder()
                .withConnectionTimeout(CONNECTION_TIMEOUT_MS)
                .build());
    }

    private Set<String> getMonitoringNodeNames(final KubernetesClient client) {
        return monitoringNodes(client)
                .map(Node::getMetadata)
                .filter(Objects::nonNull)
                .map(ObjectMeta::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Stream<Node> monitoringNodes(final KubernetesClient client) {
        return Optional.ofNullable(client.nodes()
                        .withLabel(monitoringNodeLabel, LABEL_ENABLED)
                        .list()
                        .getItems())
                .map(List::stream)
                .orElseGet(Stream::empty);
    }

    private List<Pod> getReportingPods(final KubernetesClient client, final Set<String> monitoringNodeNames) {
        return reportingPods(client)
                .filter(pod -> monitoringNodeNames.contains(pod.getSpec().getNodeName()))
                .collect(Collectors.toList());
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

    private List<NodeThresholdEvent> resolveEvents(final List<Pod> reportingPods) {
        return requestStats(reportingPods).stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    private List<CompletableFuture<List<NodeThresholdEvent>>> requestStats(final List<Pod> reporterPods) {
        return reporterPods.stream()
                .map(pod -> requestStats(pod)
                        .thenApply(this::toEvents)
                        .exceptionally(e -> {
                            log.error("Failed to resolve stats from {} for {}.",
                                    getPodName(pod).orElse(UNRESOLVED_POD),
                                    getNodeName(pod).orElse(UNRESOLVED_NODE), e);
                            return Collections.emptyList();
                        }))
                .collect(Collectors.toList());
    }

    private CompletableFuture<NodeReporterHostStats> requestStats(final Pod pod) {
        return CompletableFuture.supplyAsync(() -> getStats(pod).orElseThrow(IllegalArgumentException::new), executor);
    }

    private Optional<NodeReporterHostStats> getStats(final Pod pod) {
        log.info("Retrieving stats from {} for {}...",
                getPodName(pod).orElse(UNRESOLVED_POD),
                getNodeName(pod).orElse(UNRESOLVED_NODE));
        return getPodIp(pod).map(nodeStatsClient::load);
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

    private List<NodeThresholdEvent> toEvents(final NodeReporterHostStats stats) {
        log.debug("Resolving stats...");
        return thresholds.entrySet().stream()
                .flatMap(entry -> events(stats, entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private Stream<NodeThresholdEvent> events(final NodeReporterHostStats host,
                                              final NodeReporterStatsType type,
                                              final Long threshold) {
        return Optional.of(host)
                .map(NodeReporterHostStats::getProcesses)
                .map(List::stream)
                .orElseGet(Stream::empty)
                .flatMap(process -> events(process, type, threshold))
                .map(event -> event.toBuilder()
                        .node(host.getName())
                        .timestamp(host.getTimestamp())
                        .build());
    }

    private Stream<NodeThresholdEvent> events(final NodeReporterProcessStats process,
                                              final NodeReporterStatsType type,
                                              final Long threshold) {
        return getUsage(process, type)
                .flatMap(usage -> toEvent(process, type, threshold, usage))
                .map(Stream::of)
                .orElseGet(Stream::empty);
    }

    private Optional<Long> getUsage(final NodeReporterProcessStats process,
                                    final NodeReporterStatsType type) {
        return getValue(process, type)
                .flatMap(value -> getHardLimit(process, type).map(limit -> value / limit * PERCENT_MULTIPLIER))
                .map(Double::longValue);
    }

    private Optional<Double> getValue(final NodeReporterProcessStats process, final NodeReporterStatsType type) {
        return Optional.ofNullable(process)
                .map(NodeReporterProcessStats::getStats)
                .map(typeStats -> typeStats.get(type))
                .map(NodeReporterStatsValue::getValue)
                .filter(it -> it >= 0)
                .map(Integer::doubleValue);
    }

    private Optional<Double> getHardLimit(final NodeReporterProcessStats process, final NodeReporterStatsType type) {
        return Optional.ofNullable(process)
                .map(NodeReporterProcessStats::getLimits)
                .map(typeStats -> typeStats.get(type))
                .map(NodeReporterStatsLimit::getHardLimit)
                .filter(it -> it > 0)
                .map(Integer::doubleValue);
    }

    private Optional<NodeThresholdEvent> toEvent(final NodeReporterProcessStats process,
                                                 final NodeReporterStatsType type,
                                                 final Long threshold,
                                                 final Long usage) {
        log.debug("Resolved {} {}% usage of {}.", type, usage, process.getName());
        if (usage > threshold) {
            log.warn("Detected {} {}% threshold exceeding of {}.", type, threshold, process.getName());
            return Optional.of(NodeThresholdEvent.builder()
                    .parameter(getParameterDescription(type))
                    .process(process.getName())
                    .usage(String.valueOf(usage))
                    .threshold(String.valueOf(threshold))
                    .build());
        }
        return Optional.empty();
    }

    private String getParameterDescription(final NodeReporterStatsType type) {
        switch (type) {
            case NOFILE: return "Number of open files";
            default: return "Unresolved";
        }
    }
}
