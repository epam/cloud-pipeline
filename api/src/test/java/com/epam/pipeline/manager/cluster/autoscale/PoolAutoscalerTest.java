/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster.autoscale;

import com.epam.pipeline.controller.vo.cluster.pool.NodePoolVO;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.cluster.pool.NodePoolManager;
import com.epam.pipeline.mapper.cluster.pool.NodePoolMapper;
import com.epam.pipeline.test.creator.cluster.pool.NodePoolCreatorUtils;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.junit.Before;
import org.junit.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PoolAutoscalerTest {

    private static final Long POOL_ID = 11L;
    private static final String RUN_ID_1 = "1";
    private static final String RUN_ID_2 = "2";
    private static final String RUN_ID_3 = "3";
    private static final String RUN_ID_4 = "4";

    @Mock
    private KubernetesManager kubernetesManager;
    @Mock
    private NodePoolManager poolManager;
    private NodePoolMapper poolMapper = Mappers.getMapper(NodePoolMapper.class);
    private PoolAutoscaler poolAutoscaler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        poolAutoscaler = new PoolAutoscaler(poolManager, poolMapper, kubernetesManager);
    }

    @Test
    public void shouldScaleUpPoolWhenUpThresholdIsExceeded() {
        initKubeResources(RUN_ID_1, RUN_ID_2, RUN_ID_3, RUN_ID_4);
        final NodePool pool = initPool();

        poolAutoscaler.adjustPoolSizes();

        final NodePoolVO vo = poolMapper.toVO(pool);
        vo.setCount(pool.getCount() + pool.getScaleStep());
        verify(poolManager).createOrUpdate(eq(vo));
    }

    @Test
    public void shouldNotScalePoolWhenThresholdIsNotExceeded() {
        initKubeResources(RUN_ID_1, RUN_ID_2, RUN_ID_3);
        initPool();

        poolAutoscaler.adjustPoolSizes();

        verify(poolManager, times(0)).createOrUpdate(any());
    }

    @Test
    public void shouldScaleDownPoolWhenDownThresholdIsExceeded() {
        initKubeResources(RUN_ID_1);
        final NodePool pool = initPool();

        poolAutoscaler.adjustPoolSizes();

        final NodePoolVO vo = poolMapper.toVO(pool);
        vo.setCount(pool.getCount() - pool.getScaleStep());
        verify(poolManager).createOrUpdate(eq(vo));
    }

    private List<Node> buildNodes(final Long poolId, final List<String> nodeIds) {
        return nodeIds
                .stream()
                .map(id -> {
                    final Map<String, String> labels = new HashMap<>();
                    if (Objects.nonNull(poolId)) {
                        labels.put(KubernetesConstants.NODE_POOL_ID_LABEL, String.valueOf(poolId));
                    }
                    labels.put(KubernetesConstants.RUN_ID_LABEL, id);
                    final ObjectMeta objectMeta = new ObjectMeta();
                    objectMeta.setLabels(labels);
                    final Node node = new Node();
                    node.setMetadata(objectMeta);
                    return node;
                })
                .collect(Collectors.toList());
    }

    private void initKubeResources(final String... runIds) {
        doReturn(buildNodes(POOL_ID, Arrays.asList(RUN_ID_1, RUN_ID_2, RUN_ID_3, RUN_ID_4)))
                .when(kubernetesManager).getNodes(any());
        doReturn(new HashSet<>(Arrays.asList(runIds)))
                .when(kubernetesManager).getAllPodIds(any());
    }

    private NodePool initPool() {
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule(POOL_ID);
        doReturn(Collections.singletonList(pool)).when(poolManager).getActivePools();
        return pool;
    }
}