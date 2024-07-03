/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster.pool;

import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.notification.NotificationManager;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.*;
import static com.epam.pipeline.test.creator.cluster.pool.NodePoolCreatorUtils.getPoolWithoutSchedule;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class NodePoolMonitoringServiceCoreTest {
    private static final Set<String> AVAILABLE_PODS = Stream
            .of(String.valueOf(ID), String.valueOf(ID_2), String.valueOf(ID_2))
            .collect(Collectors.toSet());
    private static final List<Node> AVAILABLE_NODES = Arrays.asList(node(ID, ID), node(ID, ID_2),
            node(ID, null), node(null, null), node(null, ID_3), node(ID_2, 4L));

    private final NodePoolManager nodePoolManager = mock(NodePoolManager.class);
    private final KubernetesManager kubernetesManager = mock(KubernetesManager.class);
    private final NotificationManager notificationManager = mock(NotificationManager.class);
    private final KubernetesPoolService kubernetesPoolService = new KubernetesPoolService(kubernetesManager);
    private final NodePoolMonitoringServiceCore monitoringService =
            new NodePoolMonitoringServiceCore(nodePoolManager, notificationManager, kubernetesPoolService);

    @Test
    public void shouldNotifyIfPoolIsFull() {
        final NodePool fullNodePool = getPoolWithoutSchedule(ID);
        fullNodePool.setMaxSize(2);
        doReturn(Arrays.asList(fullNodePool, getPoolWithoutSchedule(ID_2))).when(nodePoolManager).getActivePools();
        doReturn(AVAILABLE_NODES).when(kubernetesManager).getNodes(any());
        doReturn(AVAILABLE_PODS).when(kubernetesManager).getAllPodIds(any());

        monitoringService.monitor();

        ArgumentCaptor<List<NodePool>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(notificationManager).notifyFullNodePools(captor.capture());
        final List<NodePool> result = captor.getValue();
        assertThat(result, hasSize(1));
        assertThat(result.get(0).getId(), is(ID));
    }

    @Test
    public void shouldNotNotifyIfPoolIsNotFull() {
        doReturn(Collections.singletonList(getPoolWithoutSchedule(ID))).when(nodePoolManager).getActivePools();
        doReturn(AVAILABLE_NODES).when(kubernetesManager).getNodes(any());
        doReturn(AVAILABLE_PODS).when(kubernetesManager).getAllPodIds(any());

        monitoringService.monitor();

        ArgumentCaptor<List<NodePool>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(notificationManager).notifyFullNodePools(captor.capture());
        final List<NodePool> result = captor.getValue();
        assertThat(result, hasSize(0));
    }

    private static Node node(final Long poolId, final Long runId) {
        final Map<String, String> labels = new HashMap<>();
        if (Objects.nonNull(poolId)) {
            labels.put(KubernetesConstants.NODE_POOL_ID_LABEL, String.valueOf(poolId));
        }
        if (Objects.nonNull(runId)) {
            labels.put(KubernetesConstants.RUN_ID_LABEL, String.valueOf(runId));
        }
        final Node node = new Node();
        final ObjectMeta meta = new ObjectMeta();
        meta.setLabels(labels);
        node.setMetadata(meta);
        return node;
    }
}
