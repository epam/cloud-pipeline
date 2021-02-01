/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.exception.CmdExecutionException;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.cluster.pool.NodePoolManager;
import com.epam.pipeline.manager.parallel.ParallelExecutorService;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.util.CurrentThreadExecutorService;
import com.epam.pipeline.util.KubernetesTestUtils;
import io.fabric8.kubernetes.api.model.DoneableNode;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;

import java.util.Collections;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AutoscaleManagerTest {
    private static final String TEST_KUBE_NAMESPACE = "testNamespace";
    private static final Long TEST_RUN_ID = 111L;

    @Mock
    private PipelineRunManager pipelineRunManager;

    @Mock
    private ParallelExecutorService executorService;

    @Mock
    private AutoscalerService autoscalerService;

    @Mock
    private NodesManager nodesManager;

    @Mock
    private KubernetesManager kubernetesManager;

    @Mock
    private KubernetesClient kubernetesClient;

    @Mock
    private PreferenceManager preferenceManager;

    @Mock
    private CloudFacade cloudFacade;

    @Mock
    private NodePoolManager nodePoolManager;

    @Mock
    private ReassignHandler reassignHandler;

    @Mock
    private ScaleDownHandler scaleDownHandler;

    private AutoscaleManager.AutoscaleManagerCore autoscaleManagerCore;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        autoscaleManagerCore = new AutoscaleManager.AutoscaleManagerCore(
                pipelineRunManager, executorService,
                autoscalerService, nodesManager, kubernetesManager,
                preferenceManager, TEST_KUBE_NAMESPACE, cloudFacade,
                nodePoolManager, reassignHandler, scaleDownHandler, Collections.emptyList());
        Whitebox.setInternalState(autoscaleManagerCore, "preferenceManager", preferenceManager);

        when(executorService.getExecutorService()).thenReturn(new CurrentThreadExecutorService());

        // Mock preferences
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_NODEUP_RETRY_COUNT)).thenReturn(2);
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_SPOT_MAX_ATTEMPTS)).thenReturn(1);
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_SPOT)).thenReturn(true);
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_MAX_SIZE)).thenReturn(1);
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_NODEUP_MAX_THREADS)).thenReturn(1);
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_MIN_SIZE))
            .thenReturn(SystemPreferences.CLUSTER_MIN_SIZE.getDefaultValue());
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_RANDOM_SCHEDULING))
            .thenReturn(SystemPreferences.CLUSTER_RANDOM_SCHEDULING.getDefaultValue());
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_HIGH_NON_BATCH_PRIORITY))
            .thenReturn(SystemPreferences.CLUSTER_HIGH_NON_BATCH_PRIORITY.getDefaultValue());

        when(kubernetesManager.getKubernetesClient(any(Config.class))).thenReturn(kubernetesClient);

        // Mock no nodes in cluster
        NonNamespaceOperation<Node, NodeList, DoneableNode, Resource<Node, DoneableNode>> mockNodes =
            new KubernetesTestUtils.MockNodes()
                .mockWithLabel(KubernetesConstants.RUN_ID_LABEL)
                    .mockWithoutLabel(KubernetesConstants.PAUSED_NODE_LABEL)
                    .mockNodeList(Collections.emptyList())
                .and()
                .getMockedEntity();

        when(kubernetesClient.nodes()).thenReturn(mockNodes);

        // Mock one unsceduled pod
        Pod unscheduledPipelinePod = new Pod();

        ObjectMeta metadata = new ObjectMeta();
        metadata.setLabels(Collections.singletonMap(KubernetesConstants.RUN_ID_LABEL, TEST_RUN_ID.toString()));
        unscheduledPipelinePod.setMetadata(metadata);

        PodStatus status = new PodStatus();
        status.setPhase("Pending");
        PodCondition condition = new PodCondition();
        condition.setReason(KubernetesConstants.POD_UNSCHEDULABLE);
        status.setConditions(Collections.singletonList(condition));
        unscheduledPipelinePod.setStatus(status);

        PipelineRun testRun = new PipelineRun();
        testRun.setStatus(TaskStatus.RUNNING);
        testRun.setPipelineRunParameters(Collections.emptyList());

        RunInstance spotInstance = new RunInstance();
        spotInstance.setSpot(true);
        testRun.setInstance(spotInstance);

        when(pipelineRunManager.loadPipelineRun(eq(TEST_RUN_ID))).thenReturn(testRun);
        when(autoscalerService.fillInstance(any(RunInstance.class)))
            .thenAnswer(invocation -> invocation.getArguments()[0]);

        MixedOperation<Pod, PodList, DoneablePod, PodResource<Pod, DoneablePod>> mockPods =
            new KubernetesTestUtils.MockPods()
                .mockNamespace(TEST_KUBE_NAMESPACE)
                    .mockWithLabel("type", "pipeline")
                    .mockWithLabel(KubernetesConstants.RUN_ID_LABEL)
                    .mockPodList(Collections.singletonList(unscheduledPipelinePod))
                .and()
                .getMockedEntity();

        doReturn(mockPods.inNamespace(TEST_KUBE_NAMESPACE)
                .withLabel("type", "pipeline")
                .withLabel(KubernetesConstants.RUN_ID_LABEL)
                .list()).when(kubernetesManager).getPodList(any());

        doReturn(mockNodes
                .withLabel(KubernetesConstants.RUN_ID_LABEL)
                .withoutLabel(KubernetesConstants.PAUSED_NODE_LABEL)
                .list()).when(kubernetesManager).getAvailableNodes(any());
    }

    @Test
    public void testAutoChangeToSpot() {
        when(kubernetesManager.isPodUnscheduled(any())).thenReturn(true);

        when(cloudFacade.scaleUpNode(eq(TEST_RUN_ID),
                                    argThat(Matchers.hasProperty("spot", Matchers.is(true)))))
            .thenThrow(new CmdExecutionException("", 5, ""));

        autoscaleManagerCore.runAutoscaling(); // this time spot scheduling should fail
        verify(cloudFacade).scaleUpNode(eq(TEST_RUN_ID),
                                       argThat(Matchers.hasProperty("spot", Matchers.is(true))));

        autoscaleManagerCore.runAutoscaling(); // this time it should be a on-demand request
        verify(cloudFacade, times(2))
            .scaleUpNode(eq(TEST_RUN_ID), argThat(
                Matchers.hasProperty("spot", Matchers.is(false))));
    }
}