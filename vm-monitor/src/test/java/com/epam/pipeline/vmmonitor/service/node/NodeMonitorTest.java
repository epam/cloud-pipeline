/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.vmmonitor.service.node;

import com.epam.pipeline.entity.reporter.NodeReporterStatsType;
import com.epam.pipeline.vmmonitor.model.instance.NodeThresholdEvent;
import com.epam.pipeline.vmmonitor.service.Notifier;
import com.epam.pipeline.vmmonitor.service.pipeline.NodeReporterServer;
import com.epam.pipeline.vmmonitor.service.pipeline.NodeStatsClient;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.api.model.NodeListBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class NodeMonitorTest {

    /**
     * The answer of the node reporter of a host whose fs.file-max is LONG_MAX, as systemd 240 or later sets it.
     */
    private static final String HOST_STATS = "/node-reporter/host-stats-file-max-long-max.json";
    private static final String NAMESPACE = "default";
    private static final String MONITORING_NODE_LABEL = "cloud-pipeline/cp-node-monitor";
    private static final String REPORTING_POD_NAME = "cp-node-reporter";
    private static final String NAME_LABEL = "name";
    private static final String LABEL_ENABLED = "true";
    private static final String NODE = "ip-10-0-1-15.ec2.internal";
    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2026, 11, 13, 10, 15, 30, 125_000_000);
    private static final long THRESHOLD = 60L;

    @SuppressWarnings("unchecked")
    private final Notifier<List<NodeThresholdEvent>> notifier = mock(Notifier.class);
    private final KubernetesClient kubernetesClient = mock(KubernetesClient.class);
    private NodeReporterServer server;
    private NodeMonitor monitor;

    @BeforeEach
    public void setUp() throws IOException {
        server = NodeReporterServer.start(HOST_STATS);
        final Map<NodeReporterStatsType, Long> thresholds =
                Collections.singletonMap(NodeReporterStatsType.NOFILE, THRESHOLD);
        monitor = new NodeMonitor(notifier, new NodeStatsClient("http", server.getPort()), Runnable::run,
                NAMESPACE, MONITORING_NODE_LABEL, REPORTING_POD_NAME, thresholds) {
            @Override
            public KubernetesClient getKubernetesClient() {
                return kubernetesClient;
            }
        };
        final Node node = new NodeBuilder()
                .withNewMetadata().withName(NODE).endMetadata()
                .build();
        final Pod pod = new PodBuilder()
                .withNewMetadata().withName(REPORTING_POD_NAME + "-x7k2p").endMetadata()
                .withNewSpec().withNodeName(NODE).endSpec()
                .withNewStatus().withPodIP(server.getHost()).endStatus()
                .build();
        mockMonitoringNode(node);
        mockReportingPod(pod);
    }

    @AfterEach
    public void tearDown() {
        server.close();
    }

    @Test
    public void monitorShouldNotifyAboutAProcessAboveTheThresholdOnAHostWithFileMaxOfLongMax() {
        monitor.monitor();

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<NodeThresholdEvent>> events = ArgumentCaptor.forClass(List.class);
        verify(notifier).notify(events.capture());
        assertEquals(Collections.singletonList(NodeThresholdEvent.builder()
                .parameter("Number of open files")
                .node(NODE)
                .process("containerd")
                .usage("97")
                .threshold(String.valueOf(THRESHOLD))
                .timestamp(TIMESTAMP)
                .build()), events.getValue());
    }

    @SuppressWarnings("rawtypes")
    private void mockMonitoringNode(final Node node) {
        final NonNamespaceOperation operation = mock(NonNamespaceOperation.class);
        final FilterWatchListDeletable labelled = mock(FilterWatchListDeletable.class);
        doReturn(operation).when(kubernetesClient).nodes();
        doReturn(labelled).when(operation).withLabel(MONITORING_NODE_LABEL, LABEL_ENABLED);
        doReturn(new NodeListBuilder().withItems(node).build()).when(labelled).list();
    }

    @SuppressWarnings("rawtypes")
    private void mockReportingPod(final Pod pod) {
        final MixedOperation operation = mock(MixedOperation.class);
        final NonNamespaceOperation namespaced = mock(NonNamespaceOperation.class);
        final FilterWatchListDeletable labelled = mock(FilterWatchListDeletable.class);
        doReturn(operation).when(kubernetesClient).pods();
        doReturn(namespaced).when(operation).inNamespace(NAMESPACE);
        doReturn(labelled).when(namespaced).withLabel(NAME_LABEL, REPORTING_POD_NAME);
        doReturn(new PodListBuilder().withItems(pod).build()).when(labelled).list();
    }
}
