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

package com.epam.pipeline.manager.cluster.autoscale;

import com.epam.pipeline.controller.vo.TagsVO;
import com.epam.pipeline.entity.cloud.CloudInstanceState;
import com.epam.pipeline.entity.cluster.pool.RunningInstance;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RunLogManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class ScaleDownHandlerTest {

    private static final long RUN_ID = 1L;
    private static final String RUN_LABEL = String.valueOf(RUN_ID);
    private static final String NODE_NAME = "node-1";
    private static final String INSTANCE_ID = "i-1";
    private static final int GRACE_MINUTES = 30;
    private static final Duration WITHIN_GRACE = Duration.ofMinutes(10);
    private static final Duration PAST_GRACE = Duration.ofMinutes(60);
    private static final String GRACE_ENV_VAR = "NODE_UNAVAILABLE_GRACE_PERIOD_MINUTES";
    private static final String NODE_UNAVAILABLE_TAG = "NODE_UNAVAILABLE";
    private static final String DATE_SUFFIX = "_date";

    private final AutoscalerService autoscalerService = mock(AutoscalerService.class);
    private final CloudFacade cloudFacade = mock(CloudFacade.class);
    private final PipelineRunManager pipelineRunManager = mock(PipelineRunManager.class);
    private final RunLogManager runLogManager = mock(RunLogManager.class);
    private final KubernetesManager kubernetesManager = mock(KubernetesManager.class);
    private final PipelineRunCRUDService runCRUDService = mock(PipelineRunCRUDService.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final KubernetesClient client = mock(KubernetesClient.class);

    private final ScaleDownHandler scaleDownHandler = new ScaleDownHandler(
            autoscalerService,
            cloudFacade,
            pipelineRunManager,
            runLogManager,
            kubernetesManager,
            runCRUDService,
            preferenceManager);

    private final Node node = createNode();
    private final PipelineRun run = createRun();

    @Before
    public void setUp() {
        final NodeList nodes = new NodeList();
        nodes.setItems(Collections.singletonList(node));
        doReturn(new PodList()).when(kubernetesManager).getPodList(client);
        doReturn(nodes).when(kubernetesManager).getAvailableNodes(client);
        doReturn(true).when(kubernetesManager).isNodeUnavailable(node);
        doReturn(Collections.emptyMap()).when(kubernetesManager).getNodeLabels(client, NODE_NAME);
        doReturn("").when(kubernetesManager).updateStatusWithNodeConditions(any(), any());
        doReturn(GRACE_MINUTES).when(preferenceManager)
                .getPreference(SystemPreferences.CLUSTER_NODE_UNAVAILABLE_GRACE_PERIOD_MINUTES);
        doReturn(DATE_SUFFIX).when(preferenceManager).getPreference(SystemPreferences.SYSTEM_RUN_TAG_DATE_SUFFIX);
        doReturn(Optional.of(run)).when(pipelineRunManager).findRun(RUN_ID);
        doReturn(run).when(runCRUDService).loadRunById(RUN_ID);
        doReturn(new RunningInstance()).when(autoscalerService).getPreviousRunInstance(RUN_LABEL, client);
        doReturn(CloudInstanceState.RUNNING).when(cloudFacade).getInstanceState(RUN_ID);
    }

    @Test
    public void shouldMarkUnavailableNodeWithoutScalingItDownWithinGracePeriod() {
        mockUnavailableFor(WITHIN_GRACE);

        checkFreeNodes();

        verifyNodeMarked();
        verify(cloudFacade).getInstanceState(RUN_ID);
        verifyNodeNotScaledDown();
    }

    @Test
    public void shouldScaleDownUnavailableNodeWithinGracePeriodIfInstanceIsNotRunning() {
        mockUnavailableFor(WITHIN_GRACE);
        doReturn(CloudInstanceState.TERMINATED).when(cloudFacade).getInstanceState(RUN_ID);

        checkFreeNodes();

        verifyNodeMarked();
        verifyNodeScaledDown();
    }

    @Test
    public void shouldMarkUnavailableNodeBeforeScalingItDownIfGracePeriodHasExpired() {
        mockUnavailableFor(PAST_GRACE);

        checkFreeNodes();

        final InOrder order = inOrder(kubernetesManager, pipelineRunManager, runLogManager, cloudFacade);
        order.verify(kubernetesManager)
                .addNodeLabel(eq(NODE_NAME), eq(KubernetesConstants.UNAVAILABLE_NODE_LABEL), anyString());
        order.verify(pipelineRunManager).updateTags(eq(RUN_ID), any(TagsVO.class), eq(false));
        order.verify(runLogManager).saveLog(any(RunLog.class));
        order.verify(pipelineRunManager).updatePipelineStatusIfNotFinal(RUN_ID, TaskStatus.FAILURE);
        order.verify(cloudFacade).scaleDownNode(RUN_ID);
        verify(cloudFacade, never()).getInstanceState(anyLong());
    }

    @Test
    public void shouldSkipMarkedUnavailableNodeWithinGracePeriod() {
        mockUnavailableFor(WITHIN_GRACE);
        mockNodeMarked();

        checkFreeNodes();

        verifyNodeNotMarked();
        verifyNodeNotScaledDown();
    }

    @Test
    public void shouldScaleDownMarkedUnavailableNodeIfGracePeriodHasExpired() {
        mockUnavailableFor(PAST_GRACE);
        mockNodeMarked();

        checkFreeNodes();

        verifyNodeNotMarked();
        verifyNodeScaledDown();
    }

    @Test
    public void shouldPreferRunGracePeriodToDefaultOne() {
        mockUnavailableFor(PAST_GRACE);
        mockNodeMarked();
        run.setEnvVars(Collections.singletonMap(GRACE_ENV_VAR, "120"));

        checkFreeNodes();

        verifyNodeNotScaledDown();
    }

    @Test
    public void shouldIgnoreRunGracePeriodThatDoesNotFitIntoInteger() {
        mockUnavailableFor(WITHIN_GRACE);
        mockNodeMarked();
        run.setEnvVars(Collections.singletonMap(GRACE_ENV_VAR, "3000000000"));

        checkFreeNodes();

        verifyNodeNotScaledDown();
    }

    private void checkFreeNodes() {
        scaleDownHandler.checkFreeNodes(Collections.emptySet(), client, Collections.emptySet());
    }

    private void mockUnavailableFor(final Duration duration) {
        doReturn(Optional.of(DateUtils.nowUTC().minus(duration)))
                .when(kubernetesManager).getReadyHeartbeatDateTime(node);
    }

    private void mockNodeMarked() {
        doReturn(Collections.singletonMap(KubernetesConstants.UNAVAILABLE_NODE_LABEL, "timestamp"))
                .when(kubernetesManager).getNodeLabels(client, NODE_NAME);
    }

    private void verifyNodeMarked() {
        verify(kubernetesManager)
                .addNodeLabel(eq(NODE_NAME), eq(KubernetesConstants.UNAVAILABLE_NODE_LABEL), anyString());
        final ArgumentCaptor<TagsVO> tags = ArgumentCaptor.forClass(TagsVO.class);
        verify(pipelineRunManager).updateTags(eq(RUN_ID), tags.capture(), eq(false));
        assertThat(tags.getValue().getTags())
                .containsEntry(NODE_UNAVAILABLE_TAG, KubernetesConstants.TRUE_LABEL_VALUE)
                .containsKey(NODE_UNAVAILABLE_TAG + DATE_SUFFIX);
        verify(runLogManager).saveLog(any(RunLog.class));
    }

    private void verifyNodeNotMarked() {
        verify(kubernetesManager, never()).addNodeLabel(anyString(), anyString(), anyString());
        verify(pipelineRunManager, never()).updateTags(anyLong(), any(TagsVO.class), eq(false));
        verify(runLogManager, never()).saveLog(any(RunLog.class));
    }

    private void verifyNodeScaledDown() {
        verify(pipelineRunManager).updatePipelineStatusIfNotFinal(RUN_ID, TaskStatus.FAILURE);
        verify(cloudFacade).scaleDownNode(RUN_ID);
    }

    private void verifyNodeNotScaledDown() {
        // the handler logs and swallows any failure, so the check has to reach the scale down decision
        verify(kubernetesManager).getNodeLabels(client, NODE_NAME);
        verify(pipelineRunManager, never()).updatePipelineStatusIfNotFinal(anyLong(), any(TaskStatus.class));
        verify(cloudFacade, never()).scaleDownNode(anyLong());
    }

    private Node createNode() {
        final Map<String, String> labels = new HashMap<>();
        labels.put(KubernetesConstants.RUN_ID_LABEL, RUN_LABEL);
        labels.put(KubernetesConstants.CLOUD_INSTANCE_ID, INSTANCE_ID);
        final ObjectMeta metadata = new ObjectMeta();
        metadata.setName(NODE_NAME);
        metadata.setLabels(labels);
        final Node node = new Node();
        node.setMetadata(metadata);
        return node;
    }

    private PipelineRun createRun() {
        final RunInstance instance = new RunInstance();
        instance.setNodeId(INSTANCE_ID);
        final PipelineRun run = new PipelineRun();
        run.setId(RUN_ID);
        run.setInstance(instance);
        return run;
    }
}
