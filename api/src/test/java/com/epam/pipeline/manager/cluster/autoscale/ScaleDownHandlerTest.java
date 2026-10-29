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

package com.epam.pipeline.manager.cluster.autoscale;

import com.epam.pipeline.config.Constants;
import com.epam.pipeline.controller.vo.TagsVO;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.RunLog;
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
import io.fabric8.kubernetes.api.model.NodeCondition;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.cluster.NodeCreatorUtils.getNodeCondition;
import static com.epam.pipeline.test.creator.cluster.NodeCreatorUtils.getNodeWithConditions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class ScaleDownHandlerTest {

    private static final Long RUN_ID = 1L;
    private static final String NODE_LABEL = String.valueOf(RUN_ID);
    private static final String NODE_NAME = TEST_STRING;
    private static final String INSTANCE_ID = "i-123";
    private static final int GRACE_MINUTES = 30;
    private static final String READY = "Ready";
    private static final String NETWORK_UNAVAILABLE = "NetworkUnavailable";
    private static final String TRUE = "True";
    private static final String FALSE = "False";
    private static final String UNKNOWN = "Unknown";
    private static final DateTimeFormatter RUN_LOG_DATE_FORMATTER =
            DateTimeFormatter.ofPattern(Constants.FMT_ISO_LOCAL_DATE);

    private final AutoscalerService autoscalerService = mock(AutoscalerService.class);
    private final CloudFacade cloudFacade = mock(CloudFacade.class);
    private final PipelineRunManager pipelineRunManager = mock(PipelineRunManager.class);
    private final RunLogManager runLogManager = mock(RunLogManager.class);
    private final KubernetesManager kubernetesManager = spy(new KubernetesManager());
    private final PipelineRunCRUDService runCRUDService = mock(PipelineRunCRUDService.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final KubernetesClient client = mock(KubernetesClient.class);

    private final ScaleDownHandler scaleDownHandler = new ScaleDownHandler(autoscalerService, cloudFacade,
            pipelineRunManager, runLogManager, kubernetesManager, runCRUDService, preferenceManager);

    private final LocalDateTime now = DateUtils.nowUTC().truncatedTo(ChronoUnit.SECONDS);
    private final LocalDateTime nodeStart = now.minusHours(2);

    @Before
    public void setUp() {
        final PodList pods = new PodList();
        pods.setItems(Collections.emptyList());
        doReturn(pods).when(kubernetesManager).getPodList(client);
        doReturn(Collections.emptyMap()).when(kubernetesManager).getNodeLabels(eq(client), anyString());
        doNothing().when(kubernetesManager).addNodeLabel(anyString(), anyString(), anyString());
        doNothing().when(kubernetesManager).removeNodeLabel(anyString(), anyString());
        doReturn(GRACE_MINUTES).when(preferenceManager)
                .getPreference(SystemPreferences.CLUSTER_NODE_UNAVAILABLE_GRACE_PERIOD_MINUTES);
        doReturn(Optional.of(getRun())).when(pipelineRunManager).findRun(RUN_ID);
    }

    @Test
    public void shouldMarkUnavailableNodeSinceReadyTransitionAndKeepItWithinGracePeriod() {
        final LocalDateTime unavailableSince = now.minusMinutes(5);
        final LocalDateTime lastHeartbeat = now.minusMinutes(GRACE_MINUTES + 10);
        final Node node = getNode(getNodeLabels(),
                getCondition(READY, UNKNOWN, lastHeartbeat, unavailableSince));

        checkFreeNodes(node, Collections.emptySet());

        verify(kubernetesManager).addNodeLabel(NODE_NAME, KubernetesConstants.UNAVAILABLE_NODE_LABEL,
                KubernetesConstants.KUBE_LABEL_DATE_FORMATTER.format(unavailableSince));
        verify(pipelineRunManager).updateTags(eq(RUN_ID), any(TagsVO.class), eq(false));
        verify(cloudFacade, never()).scaleDownNode(anyLong());
    }

    @Test
    public void shouldScaleDownUnavailableNodeAfterGracePeriod() {
        final Node node = getNode(getNodeLabels(),
                getCondition(READY, UNKNOWN, now.minusMinutes(45), now.minusMinutes(40)));

        checkFreeNodes(node, Collections.emptySet());

        verify(cloudFacade).scaleDownNode(RUN_ID);
    }

    @Test
    public void shouldSkipNodeWithoutReadyCondition() {
        final Node node = getNode(getNodeLabels());

        checkFreeNodes(node, Collections.emptySet());

        verify(cloudFacade, never()).scaleDownNode(anyLong());
        verify(kubernetesManager, never()).addNodeLabel(anyString(), anyString(), anyString());
        verify(pipelineRunManager, never()).updateTags(anyLong(), any(TagsVO.class), anyBoolean());
    }

    @Test
    public void shouldScaleDownFreeNodeWhichIsNotReady() {
        final Node node = getNode(getNodeLabels(), getCondition(READY, FALSE, now, now.minusMinutes(5)));

        checkFreeNodes(node, Collections.emptySet());

        verify(kubernetesManager, never()).addNodeLabel(anyString(), anyString(), anyString());
        verify(cloudFacade).scaleDownNode(RUN_ID);
    }

    @Test
    public void shouldMarkRecoveredNodeAvailableSinceReadyTransition() {
        final LocalDateTime availableSince = now.minusMinutes(1);
        final Map<String, String> labels = getNodeLabels();
        labels.put(KubernetesConstants.UNAVAILABLE_NODE_LABEL, TEST_STRING);
        final Node node = getNode(labels, getCondition(READY, TRUE, now, availableSince));

        checkFreeNodes(node, Collections.singleton(NODE_LABEL));

        verify(kubernetesManager).removeNodeLabel(NODE_NAME, KubernetesConstants.UNAVAILABLE_NODE_LABEL);
        final ArgumentCaptor<RunLog> runLog = ArgumentCaptor.forClass(RunLog.class);
        verify(runLogManager).saveLog(runLog.capture());
        assertThat(runLog.getValue().getLogText()).contains(RUN_LOG_DATE_FORMATTER.format(availableSince));
        verify(cloudFacade, never()).scaleDownNode(anyLong());
    }

    private void checkFreeNodes(final Node node, final Set<String> pods) {
        final NodeList nodes = new NodeList();
        nodes.setItems(Collections.singletonList(node));
        doReturn(nodes).when(kubernetesManager).getAvailableNodes(client);
        scaleDownHandler.checkFreeNodes(Collections.emptySet(), client, pods);
    }

    private Node getNode(final Map<String, String> labels) {
        return getNodeWithConditions(labels, getNetworkCondition());
    }

    private Node getNode(final Map<String, String> labels, final NodeCondition readyCondition) {
        return getNodeWithConditions(labels, getNetworkCondition(), readyCondition);
    }

    /**
     * Flannel puts NetworkUnavailable first and never updates it after the node start.
     */
    private NodeCondition getNetworkCondition() {
        return getCondition(NETWORK_UNAVAILABLE, FALSE, nodeStart, nodeStart);
    }

    private NodeCondition getCondition(final String type, final String status,
                                       final LocalDateTime heartbeat, final LocalDateTime transition) {
        return getNodeCondition(type, status, TEST_STRING, toKubeDateTime(heartbeat), toKubeDateTime(transition));
    }

    private String toKubeDateTime(final LocalDateTime dateTime) {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(dateTime) + "Z";
    }

    private Map<String, String> getNodeLabels() {
        final Map<String, String> labels = new HashMap<>();
        labels.put(KubernetesConstants.RUN_ID_LABEL, NODE_LABEL);
        labels.put(KubernetesConstants.CLOUD_INSTANCE_ID_LABEL, INSTANCE_ID);
        return labels;
    }

    private PipelineRun getRun() {
        final RunInstance instance = new RunInstance();
        instance.setNodeId(INSTANCE_ID);
        final PipelineRun run = new PipelineRun();
        run.setId(RUN_ID);
        run.setInstance(instance);
        run.setTags(new HashMap<>());
        return run;
    }
}
