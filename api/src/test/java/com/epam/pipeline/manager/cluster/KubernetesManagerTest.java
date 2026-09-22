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

package com.epam.pipeline.manager.cluster;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeCondition;
import io.fabric8.kubernetes.api.model.NodeStatus;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KubernetesManagerTest {

    private static final String KUBELET_HEARTBEAT = "2026-09-22T08:49:06Z";
    private static final String FLANNEL_HEARTBEAT = "2026-09-22T08:43:40Z";
    private static final LocalDateTime KUBELET_HEARTBEAT_DATE_TIME = LocalDateTime.parse("2026-09-22T08:49:06");

    private static final String NETWORK_UNAVAILABLE = "NetworkUnavailable";
    private static final String MEMORY_PRESSURE = "MemoryPressure";
    private static final String DISK_PRESSURE = "DiskPressure";
    private static final String PID_PRESSURE = "PIDPressure";
    private static final String OUT_OF_DISK = "OutOfDisk";
    private static final String READY = "Ready";

    private static final String TRUE = "True";
    private static final String FALSE = "False";
    private static final String UNKNOWN = "Unknown";

    private static final String FLANNEL_IS_UP = "FlannelIsUp";
    private static final String NODE_STATUS_UNKNOWN = "NodeStatusUnknown";
    private static final String KUBELET_OUT_OF_DISK = "KubeletOutOfDisk";
    private static final String KUBELET_READY = "KubeletReady";
    private static final String KUBELET_NOT_READY = "KubeletNotReady";
    private static final String KUBELET_HAS_SUFFICIENT_MEMORY = "KubeletHasSufficientMemory";
    private static final String KUBELET_HAS_NO_DISK_PRESSURE = "KubeletHasNoDiskPressure";
    private static final String KUBELET_HAS_SUFFICIENT_PID = "KubeletHasSufficientPID";

    private final KubernetesManager manager = new KubernetesManager();

    @Test
    public void nodeShouldBeUnavailableIfNetworkPluginConditionGoesFirst() {
        assertTrue(manager.isNodeUnavailable(nodeWithDeadKubelet()));
        assertFalse(manager.isNodeAvailable(nodeWithDeadKubelet()));
    }

    @Test
    public void nodeShouldBeAvailableIfAllConditionsAreHealthy() {
        assertFalse(manager.isNodeUnavailable(healthyNode()));
        assertTrue(manager.isNodeAvailable(healthyNode()));
    }

    @Test
    public void nodeShouldBeUnavailableIfConditionsAreMissing() {
        assertTrue(manager.isNodeUnavailable(node(Collections.emptyList())));
        assertTrue(manager.isNodeUnavailable(node(null)));
        assertTrue(manager.isNodeUnavailable(new Node()));
        assertTrue(manager.isNodeUnavailable(null));
    }

    @Test
    public void nodeShouldBeUnavailableIfKubeletIsOutOfDisk() {
        assertTrue(manager.isNodeUnavailable(node(Arrays.asList(
                condition(NETWORK_UNAVAILABLE, FALSE, FLANNEL_IS_UP, FLANNEL_HEARTBEAT),
                condition(OUT_OF_DISK, TRUE, KUBELET_OUT_OF_DISK, KUBELET_HEARTBEAT),
                condition(READY, TRUE, KUBELET_READY, KUBELET_HEARTBEAT)))));
    }

    @Test
    public void nodeShouldStayAvailableIfKubeletReportsItselfNotReady() {
        assertFalse(manager.isNodeUnavailable(node(Arrays.asList(
                condition(NETWORK_UNAVAILABLE, FALSE, FLANNEL_IS_UP, FLANNEL_HEARTBEAT),
                condition(READY, FALSE, KUBELET_NOT_READY, KUBELET_HEARTBEAT)))));
    }

    @Test
    public void nodeShouldStayAvailableIfConditionsCarryNoReason() {
        // a condition without a reason tells nothing about the node being out of order,
        // and shall not become a termination trigger of its own
        assertFalse(manager.isNodeUnavailable(node(Arrays.asList(
                condition(NETWORK_UNAVAILABLE, FALSE, null, FLANNEL_HEARTBEAT),
                condition(READY, TRUE, null, KUBELET_HEARTBEAT)))));
    }

    @Test
    public void lastConditionDateTimeShouldBeTakenFromReadyCondition() {
        assertEquals(Optional.of(KUBELET_HEARTBEAT_DATE_TIME),
                manager.getLastConditionDateTime(nodeWithDeadKubelet()));
    }

    @Test
    public void lastConditionDateTimeShouldIgnoreHeartbeatsOfOtherConditions() {
        assertFalse(manager.getLastConditionDateTime(node(Arrays.asList(
                condition(NETWORK_UNAVAILABLE, FALSE, FLANNEL_IS_UP, FLANNEL_HEARTBEAT),
                condition(MEMORY_PRESSURE, UNKNOWN, NODE_STATUS_UNKNOWN, KUBELET_HEARTBEAT)))).isPresent());
    }

    @Test
    public void lastConditionDateTimeShouldBeEmptyWithoutHeartbeats() {
        assertFalse(manager.getLastConditionDateTime(node(Collections.emptyList())).isPresent());
        assertFalse(manager.getLastConditionDateTime(node(Collections.singletonList(
                condition(READY, UNKNOWN, NODE_STATUS_UNKNOWN, null)))).isPresent());
    }

    private Node nodeWithDeadKubelet() {
        return node(Arrays.asList(
                condition(NETWORK_UNAVAILABLE, FALSE, FLANNEL_IS_UP, FLANNEL_HEARTBEAT),
                condition(MEMORY_PRESSURE, UNKNOWN, NODE_STATUS_UNKNOWN, KUBELET_HEARTBEAT),
                condition(DISK_PRESSURE, UNKNOWN, NODE_STATUS_UNKNOWN, KUBELET_HEARTBEAT),
                condition(PID_PRESSURE, UNKNOWN, NODE_STATUS_UNKNOWN, KUBELET_HEARTBEAT),
                condition(READY, UNKNOWN, NODE_STATUS_UNKNOWN, KUBELET_HEARTBEAT)));
    }

    private Node healthyNode() {
        return node(Arrays.asList(
                condition(NETWORK_UNAVAILABLE, FALSE, FLANNEL_IS_UP, FLANNEL_HEARTBEAT),
                condition(MEMORY_PRESSURE, FALSE, KUBELET_HAS_SUFFICIENT_MEMORY, KUBELET_HEARTBEAT),
                condition(DISK_PRESSURE, FALSE, KUBELET_HAS_NO_DISK_PRESSURE, KUBELET_HEARTBEAT),
                condition(PID_PRESSURE, FALSE, KUBELET_HAS_SUFFICIENT_PID, KUBELET_HEARTBEAT),
                condition(READY, TRUE, KUBELET_READY, KUBELET_HEARTBEAT)));
    }

    private Node node(final List<NodeCondition> conditions) {
        final NodeStatus status = new NodeStatus();
        status.setConditions(conditions);
        final Node node = new Node();
        node.setStatus(status);
        return node;
    }

    private NodeCondition condition(final String type, final String status, final String reason,
                                    final String lastHeartbeatTime) {
        final NodeCondition condition = new NodeCondition();
        condition.setType(type);
        condition.setStatus(status);
        condition.setReason(reason);
        condition.setLastHeartbeatTime(lastHeartbeatTime);
        return condition;
    }
}
