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
import io.fabric8.kubernetes.api.model.ObjectMeta;
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
    private static final String CREATION_TIMESTAMP = "2026-09-22T08:12:31Z";
    private static final LocalDateTime CREATION_DATE_TIME = LocalDateTime.parse("2026-09-22T08:12:31");

    private static final String NETWORK_UNAVAILABLE = "NetworkUnavailable";
    private static final String MEMORY_PRESSURE = "MemoryPressure";
    private static final String DISK_PRESSURE = "DiskPressure";
    private static final String PID_PRESSURE = "PIDPressure";
    private static final String READY = "Ready";
    private static final String KERNEL_DEADLOCK = "KernelDeadlock";

    private static final String TRUE = "True";
    private static final String FALSE = "False";
    private static final String UNKNOWN = "Unknown";

    private static final String FLANNEL_IS_UP = "FlannelIsUp";
    private static final String NODE_STATUS_UNKNOWN = "NodeStatusUnknown";
    private static final String NODE_STATUS_NEVER_UPDATED = "NodeStatusNeverUpdated";
    private static final String KUBELET_READY = "KubeletReady";
    private static final String KUBELET_NOT_READY = "KubeletNotReady";
    private static final String KUBELET_HAS_SUFFICIENT_MEMORY = "KubeletHasSufficientMemory";
    private static final String KUBELET_HAS_INSUFFICIENT_MEMORY = "KubeletHasInsufficientMemory";
    private static final String KUBELET_HAS_NO_DISK_PRESSURE = "KubeletHasNoDiskPressure";
    private static final String KUBELET_HAS_SUFFICIENT_PID = "KubeletHasSufficientPID";
    private static final String KERNEL_HAS_NO_DEADLOCK = "KernelHasNoDeadlock";

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
    public void nodeShouldBeUnavailableIfReadyConditionIsMissing() {
        assertTrue(manager.isNodeUnavailable(node(Arrays.asList(
                condition(NETWORK_UNAVAILABLE, FALSE, FLANNEL_IS_UP, FLANNEL_HEARTBEAT),
                condition(MEMORY_PRESSURE, FALSE, KUBELET_HAS_SUFFICIENT_MEMORY, KUBELET_HEARTBEAT)))));
    }

    @Test
    public void nodeShouldBeUnavailableIfKubeletNeverPostedItsStatus() {
        assertTrue(manager.isNodeUnavailable(node(Arrays.asList(
                condition(NETWORK_UNAVAILABLE, FALSE, FLANNEL_IS_UP, FLANNEL_HEARTBEAT),
                condition(READY, UNKNOWN, NODE_STATUS_NEVER_UPDATED, KUBELET_HEARTBEAT)))));
    }

    @Test
    public void nodeShouldStayAvailableIfKubeletReportsItselfNotReady() {
        assertFalse(manager.isNodeUnavailable(node(Arrays.asList(
                condition(NETWORK_UNAVAILABLE, FALSE, FLANNEL_IS_UP, FLANNEL_HEARTBEAT),
                condition(READY, FALSE, KUBELET_NOT_READY, KUBELET_HEARTBEAT)))));
    }

    @Test
    public void nodeShouldStayAvailableIfAnotherConditionKeepsStaleUnknownStatus() {
        assertFalse(manager.isNodeUnavailable(node(Arrays.asList(
                condition(NETWORK_UNAVAILABLE, FALSE, FLANNEL_IS_UP, FLANNEL_HEARTBEAT),
                condition(MEMORY_PRESSURE, UNKNOWN, NODE_STATUS_UNKNOWN, FLANNEL_HEARTBEAT),
                condition(READY, TRUE, KUBELET_READY, KUBELET_HEARTBEAT)))));
    }

    @Test
    public void nodeAvailabilityShouldNotDependOnConditionReasons() {
        assertTrue(manager.isNodeUnavailable(node(Arrays.asList(
                condition(NETWORK_UNAVAILABLE, FALSE, null, FLANNEL_HEARTBEAT),
                condition(READY, UNKNOWN, null, KUBELET_HEARTBEAT)))));
        assertFalse(manager.isNodeUnavailable(node(Arrays.asList(
                condition(NETWORK_UNAVAILABLE, FALSE, null, FLANNEL_HEARTBEAT),
                condition(READY, TRUE, null, KUBELET_HEARTBEAT)))));
    }

    @Test
    public void nodeShouldBeReadyOnlyIfReadyStatusIsTrue() {
        assertTrue(manager.isNodeReady(healthyNode()));
        assertFalse(manager.isNodeReady(nodeWithDeadKubelet()));
        assertFalse(manager.isNodeReady(node(Collections.singletonList(
                condition(READY, FALSE, KUBELET_NOT_READY, KUBELET_HEARTBEAT)))));
        assertFalse(manager.isNodeReady(node(Collections.singletonList(
                condition(MEMORY_PRESSURE, FALSE, KUBELET_HAS_SUFFICIENT_MEMORY, KUBELET_HEARTBEAT)))));
        assertFalse(manager.isNodeReady(node(null)));
        assertFalse(manager.isNodeReady(null));
    }

    @Test
    public void readyHeartbeatDateTimeShouldBeTakenFromReadyCondition() {
        assertEquals(Optional.of(KUBELET_HEARTBEAT_DATE_TIME),
                manager.getReadyHeartbeatDateTime(nodeWithDeadKubelet()));
    }

    @Test
    public void readyHeartbeatDateTimeShouldIgnoreHeartbeatsOfOtherConditions() {
        assertFalse(manager.getReadyHeartbeatDateTime(node(Arrays.asList(
                condition(NETWORK_UNAVAILABLE, FALSE, FLANNEL_IS_UP, FLANNEL_HEARTBEAT),
                condition(MEMORY_PRESSURE, UNKNOWN, NODE_STATUS_UNKNOWN, KUBELET_HEARTBEAT)))).isPresent());
    }

    @Test
    public void readyHeartbeatDateTimeShouldBeEmptyWithoutHeartbeats() {
        assertFalse(manager.getReadyHeartbeatDateTime(node(Collections.emptyList())).isPresent());
        assertFalse(manager.getReadyHeartbeatDateTime(node(Collections.singletonList(
                condition(READY, UNKNOWN, NODE_STATUS_UNKNOWN, null)))).isPresent());
    }

    @Test
    public void creationDateTimeShouldBeTakenFromNodeMetadata() {
        final Node node = node(Collections.emptyList());
        final ObjectMeta metadata = new ObjectMeta();
        metadata.setCreationTimestamp(CREATION_TIMESTAMP);
        node.setMetadata(metadata);

        assertEquals(Optional.of(CREATION_DATE_TIME), manager.getCreationDateTime(node));
    }

    @Test
    public void creationDateTimeShouldBeEmptyWithoutCreationTimestamp() {
        final Node node = node(Collections.emptyList());
        node.setMetadata(new ObjectMeta());

        assertFalse(manager.getCreationDateTime(node).isPresent());
        assertFalse(manager.getCreationDateTime(new Node()).isPresent());
        assertFalse(manager.getCreationDateTime(null).isPresent());
    }

    @Test
    public void nodeStatusShouldListFailedAndUnrecognisedConditions() {
        final Node node = node(Arrays.asList(
                condition(NETWORK_UNAVAILABLE, FALSE, FLANNEL_IS_UP, FLANNEL_HEARTBEAT),
                condition(MEMORY_PRESSURE, TRUE, KUBELET_HAS_INSUFFICIENT_MEMORY, KUBELET_HEARTBEAT),
                condition(DISK_PRESSURE, FALSE, KUBELET_HAS_NO_DISK_PRESSURE, KUBELET_HEARTBEAT),
                condition(KERNEL_DEADLOCK, FALSE, KERNEL_HAS_NO_DEADLOCK, KUBELET_HEARTBEAT),
                condition(READY, UNKNOWN, NODE_STATUS_UNKNOWN, KUBELET_HEARTBEAT)));

        assertEquals("MemoryPressure (True KubeletHasInsufficientMemory), "
                        + "KernelDeadlock (False KernelHasNoDeadlock), "
                        + "Ready (Unknown NodeStatusUnknown)",
                manager.updateStatusWithNodeConditions(new StringBuilder(), node));
    }

    @Test
    public void nodeStatusShouldStayUnchangedIfAllConditionsAreHealthy() {
        assertEquals("", manager.updateStatusWithNodeConditions(new StringBuilder(), healthyNode()));
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
