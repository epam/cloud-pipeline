/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import io.fabric8.kubernetes.api.model.DoneableNode;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeCondition;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static com.epam.pipeline.test.creator.cluster.NodeCreatorUtils.getEmptyNode;
import static com.epam.pipeline.test.creator.cluster.NodeCreatorUtils.getNodeCondition;
import static com.epam.pipeline.test.creator.cluster.NodeCreatorUtils.getNodeWithConditions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class KubernetesManagerTest {

    private static final String NODE_ID = "1";
    private static final String NODE_START = "2026-10-13T09:00:53Z";
    private static final String HEARTBEAT = "2026-10-13T10:50:43Z";
    private static final String TRANSITION = "2026-10-13T10:51:30Z";
    private static final LocalDateTime TRANSITION_DATE_TIME = LocalDateTime.parse("2026-10-13T10:51:30");
    private static final String FLANNEL_IS_UP = "FlannelIsUp";
    private static final String KUBELET_READY = "KubeletReady";
    private static final String KUBELET_NOT_READY = "KubeletNotReady";
    private static final String NODE_STATUS_UNKNOWN = "NodeStatusUnknown";
    private static final String NODE_STATUS_NEVER_UPDATED = "NodeStatusNeverUpdated";

    private final KubernetesManager kubernetesManager = new KubernetesManager();

    @Test
    public void shouldTreatNodeAsAvailableWhenReadyIsTrue() {
        final Node node = getFlannelNode(KubernetesConstants.TRUE, KUBELET_READY);

        assertThat(kubernetesManager.isNodeAvailable(node)).isTrue();
        assertThat(kubernetesManager.isNodeUnavailable(node)).isFalse();
    }

    @Test
    public void shouldTreatNodeAsUnavailableWhenReadyIsUnknownBehindNetworkCondition() {
        final Node node = getFlannelNode(KubernetesConstants.UNKNOWN, NODE_STATUS_UNKNOWN);

        assertThat(kubernetesManager.isNodeUnavailable(node)).isTrue();
        assertThat(kubernetesManager.isNodeAvailable(node)).isFalse();
    }

    @Test
    public void shouldTreatNodeAsUnavailableWhenKubeletNeverPostedStatus() {
        final Node node = getNodeWithConditions(Collections.emptyMap(), getNodeCondition(
                KubernetesConstants.READY, KubernetesConstants.UNKNOWN, NODE_STATUS_NEVER_UPDATED,
                NODE_START, TRANSITION));

        assertThat(kubernetesManager.isNodeUnavailable(node)).isTrue();
        assertThat(kubernetesManager.isNodeAvailable(node)).isFalse();
    }

    @Test
    public void shouldNotTreatNodeAsUnavailableWhenReadyIsFalse() {
        final Node node = getFlannelNode(KubernetesConstants.FALSE, KUBELET_NOT_READY);

        assertThat(kubernetesManager.isNodeUnavailable(node)).isFalse();
        assertThat(kubernetesManager.isNodeAvailable(node)).isTrue();
    }

    @Test
    public void shouldTreatNodeWithoutReadyConditionAsNeitherAvailableNorUnavailable() {
        assertNeitherAvailableNorUnavailable(getNodeWithConditions(Collections.emptyMap(), getNetworkCondition()));
        assertNeitherAvailableNorUnavailable(getNodeWithConditions(Collections.emptyMap()));
        assertNeitherAvailableNorUnavailable(getEmptyNode());
    }

    @Test
    public void shouldTakeConditionDateTimeFromReadyTransitionTime() {
        final Node node = getFlannelNode(KubernetesConstants.UNKNOWN, NODE_STATUS_UNKNOWN);

        assertThat(kubernetesManager.getReadyTransitionDateTime(node)).isEqualTo(Optional.of(TRANSITION_DATE_TIME));
    }

    @Test
    public void shouldNotReturnConditionDateTimeWithoutReadyCondition() {
        final Node node = getNodeWithConditions(Collections.emptyMap(), getNetworkCondition());

        assertThat(kubernetesManager.getReadyTransitionDateTime(node)).isEqualTo(Optional.empty());
    }

    @Test
    public void shouldTreatNodeAsReadyWhenReadyIsTrue() {
        assertThat(isNodeReady(getFlannelNode(KubernetesConstants.TRUE, KUBELET_READY))).isTrue();
    }

    @Test
    public void shouldNotTreatNodeAsReadyWhenReadyIsNotTrue() {
        assertThat(isNodeReady(getFlannelNode(KubernetesConstants.FALSE, KUBELET_NOT_READY))).isFalse();
        assertThat(isNodeReady(getFlannelNode(KubernetesConstants.UNKNOWN, NODE_STATUS_UNKNOWN))).isFalse();
        assertThat(isNodeReady(getNodeWithConditions(Collections.emptyMap(), getNetworkCondition()))).isFalse();
    }

    @Test
    public void shouldTreatNotReadyNodeAsAvailableButNotReady() {
        final KubernetesClient client = getClient(getFlannelNode(KubernetesConstants.FALSE, KUBELET_NOT_READY));

        assertThat(kubernetesManager.isNodeAvailable(client, NODE_ID)).isTrue();
        assertThat(kubernetesManager.isNodeReady(client, NODE_ID)).isFalse();
    }

    private boolean isNodeReady(final Node node) {
        return kubernetesManager.isNodeReady(getClient(node), NODE_ID);
    }

    @SuppressWarnings("unchecked")
    private KubernetesClient getClient(final Node node) {
        final KubernetesClient client = mock(KubernetesClient.class);
        final NonNamespaceOperation<Node, NodeList, DoneableNode, Resource<Node, DoneableNode>> nodes =
                mock(NonNamespaceOperation.class);
        final FilterWatchListDeletable<Node, NodeList, Boolean, Watch, Watcher<Node>> runNodes =
                mock(FilterWatchListDeletable.class);
        final FilterWatchListDeletable<Node, NodeList, Boolean, Watch, Watcher<Node>> notPausedRunNodes =
                mock(FilterWatchListDeletable.class);
        doReturn(nodes).when(client).nodes();
        doReturn(runNodes).when(nodes).withLabel(KubernetesConstants.RUN_ID_LABEL, NODE_ID);
        doReturn(notPausedRunNodes).when(runNodes).withoutLabel(KubernetesConstants.PAUSED_NODE_LABEL);
        doReturn(new NodeList("", Collections.singletonList(node), "", null)).when(notPausedRunNodes).list();
        return client;
    }

    private void assertNeitherAvailableNorUnavailable(final Node node) {
        assertThat(kubernetesManager.isNodeAvailable(node)).isFalse();
        assertThat(kubernetesManager.isNodeUnavailable(node)).isFalse();
    }

    /**
     * Flannel puts NetworkUnavailable first and never updates it after the node start.
     */
    private Node getFlannelNode(final String readyStatus, final String readyReason) {
        return getNodeWithConditions(Collections.emptyMap(),
                getNetworkCondition(),
                getNodeCondition(KubernetesConstants.READY, readyStatus, readyReason, HEARTBEAT, TRANSITION));
    }

    private NodeCondition getNetworkCondition() {
        return getNodeCondition(KubernetesConstants.NETWORK_UNAVAILABLE, KubernetesConstants.FALSE, FLANNEL_IS_UP,
                NODE_START, NODE_START);
    }
}
