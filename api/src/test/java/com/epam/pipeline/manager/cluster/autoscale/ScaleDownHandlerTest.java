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

import com.epam.pipeline.entity.cluster.pool.InstanceRequest;
import com.epam.pipeline.entity.cluster.pool.RunningInstance;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RunLogManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class ScaleDownHandlerTest {

    private static final Long FREE_NODE_RUN_ID = 1L;
    private static final String FREE_NODE_LABEL = String.valueOf(FREE_NODE_RUN_ID);
    private static final Long PENDING_RUN_ID = 2L;
    private static final String CP_CREATE_NEW_NODE = "CP_CREATE_NEW_NODE";
    private static final String CP_CAP_DEDICATED_INSTANCE = "CP_CAP_DEDICATED_INSTANCE";

    private final AutoscalerService autoscalerService = mock(AutoscalerService.class);
    private final CloudFacade cloudFacade = mock(CloudFacade.class);
    private final PipelineRunManager pipelineRunManager = mock(PipelineRunManager.class);
    private final KubernetesManager kubernetesManager = mock(KubernetesManager.class);
    private final PipelineRunCRUDService runCRUDService = mock(PipelineRunCRUDService.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final KubernetesClient client = mock(KubernetesClient.class);
    private final IAMProfileVerifier iamProfileVerifier = mock(IAMProfileVerifier.class);
    private final ReassignHandler reassignHandler = new ReassignHandler(autoscalerService, cloudFacade,
            pipelineRunManager, Collections.emptyList(), mock(MetadataManager.class), iamProfileVerifier,
            preferenceManager);

    private final ScaleDownHandler scaleDownHandler = new ScaleDownHandler(autoscalerService, cloudFacade,
            pipelineRunManager, mock(RunLogManager.class), kubernetesManager, runCRUDService, preferenceManager,
            reassignHandler);

    private PipelineRun pendingRun;

    /**
     * A free run node matches the pending run and has expired, so it is kept only for the pending run.
     */
    @Before
    public void setUp() {
        pendingRun = new PipelineRun();
        pendingRun.setId(PENDING_RUN_ID);
        pendingRun.setInstance(new RunInstance());
        pendingRun.setPipelineRunParameters(Collections.emptyList());
        doReturn(pendingRun).when(runCRUDService).loadRunById(PENDING_RUN_ID);
        doReturn(false).when(preferenceManager).getPreference(SystemPreferences.CLUSTER_DISABLE_REASSIGN);

        final PodList pods = new PodList();
        pods.setItems(Collections.singletonList(getPod(PENDING_RUN_ID)));
        doReturn(pods).when(kubernetesManager).getPodList(client);
        doReturn(true).when(kubernetesManager).isPodUnscheduled(any(Pod.class));
        final NodeList nodes = new NodeList();
        nodes.setItems(Collections.singletonList(getNode(FREE_NODE_LABEL)));
        doReturn(nodes).when(kubernetesManager).getAvailableNodes(client);

        final RunningInstance freeNode = new RunningInstance();
        freeNode.setInstance(new RunInstance());
        doReturn(freeNode).when(autoscalerService).getPreviousRunInstance(FREE_NODE_LABEL, client);
        doReturn(true).when(autoscalerService).requirementsMatch(eq(freeNode), any(InstanceRequest.class));
        doReturn(Optional.empty()).when(autoscalerService).findPool(FREE_NODE_LABEL, client);
        doReturn(true).when(cloudFacade).isNodeExpired(FREE_NODE_RUN_ID);
    }

    @Test
    public void shouldKeepFreeNodeForPendingRunWhichMayGetIt() {
        checkFreeNodes();

        verifyFreeNodeKept();
    }

    @Test
    public void shouldKeepFreeNodeForPendingRunWithNullParameterList() {
        pendingRun.setPipelineRunParameters(null);

        checkFreeNodes();

        verifyFreeNodeKept();
    }

    @Test
    public void shouldNotKeepFreeNodeForPendingRunWithCreateNewNodeParameter() {
        pendingRun.setPipelineRunParameters(Collections.singletonList(
                new PipelineRunParameter(CP_CREATE_NEW_NODE, "true")));

        checkFreeNodes();

        verify(cloudFacade).scaleDownNode(FREE_NODE_RUN_ID);
    }

    @Test
    public void shouldNotKeepFreeNodeForPendingRunWithDedicatedInstanceParameter() {
        pendingRun.setPipelineRunParameters(Collections.singletonList(
                new PipelineRunParameter(CP_CAP_DEDICATED_INSTANCE, "true")));

        checkFreeNodes();

        verify(cloudFacade).scaleDownNode(FREE_NODE_RUN_ID);
    }

    @Test
    public void shouldNotKeepFreeNodeForPendingWindowsRun() {
        pendingRun.setPlatform(KubernetesConstants.WINDOWS);

        checkFreeNodes();

        verify(cloudFacade).scaleDownNode(FREE_NODE_RUN_ID);
    }

    @Test
    public void shouldNotKeepFreeNodeForPendingRunWithRestrictedImage() {
        doReturn(true).when(iamProfileVerifier).isImageRestricted(pendingRun);

        checkFreeNodes();

        verify(cloudFacade).scaleDownNode(FREE_NODE_RUN_ID);
    }

    @Test
    public void shouldNotKeepFreeNodeForPendingRunWhenReassignIsDisabled() {
        doReturn(true).when(preferenceManager).getPreference(SystemPreferences.CLUSTER_DISABLE_REASSIGN);

        checkFreeNodes();

        verify(cloudFacade).scaleDownNode(FREE_NODE_RUN_ID);
    }

    /**
     * The node is matched with the pending run and kept for it: its expiration is not checked.
     */
    private void verifyFreeNodeKept() {
        verify(autoscalerService).requirementsMatch(any(RunningInstance.class), any(InstanceRequest.class));
        verify(cloudFacade, never()).isNodeExpired(anyLong());
        verify(cloudFacade, never()).scaleDownNode(anyLong());
    }

    private void checkFreeNodes() {
        scaleDownHandler.checkFreeNodes(Collections.emptySet(), client, Collections.emptySet());
    }

    private Pod getPod(final Long runId) {
        final Pod pod = new Pod();
        pod.setMetadata(getMetadata(String.valueOf(runId)));
        return pod;
    }

    private Node getNode(final String runIdLabel) {
        final Node node = new Node();
        node.setMetadata(getMetadata(runIdLabel));
        return node;
    }

    private ObjectMeta getMetadata(final String runIdLabel) {
        final ObjectMeta metadata = new ObjectMeta();
        metadata.setName(runIdLabel);
        metadata.setLabels(Collections.singletonMap(KubernetesConstants.RUN_ID_LABEL, runIdLabel));
        return metadata;
    }
}
