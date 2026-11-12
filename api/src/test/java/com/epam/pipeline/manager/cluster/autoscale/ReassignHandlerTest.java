/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.cluster.pool.InstanceRequest;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.cluster.pool.RunningInstance;
import com.epam.pipeline.entity.cluster.pool.filter.PoolFilter;
import com.epam.pipeline.entity.cluster.pool.filter.PoolFilterOperator;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.PoolInstanceFilterType;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.RunOwnerPoolInstanceFilter;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cluster.autoscale.filter.PoolFilterHandler;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipelineRun;
import static com.epam.pipeline.util.CustomMatchers.anyStringMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ReassignHandlerTest {

    private static final String WINDOWS = "windows";
    private static final String CP_CREATE_NEW_NODE = "CP_CREATE_NEW_NODE";
    private static final String CP_CAP_DEDICATED_INSTANCE = "CP_CAP_DEDICATED_INSTANCE";
    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final long FREE_NODE_RUN_ID = 2L;
    private static final String FREE_NODE = String.valueOf(FREE_NODE_RUN_ID);
    private static final String FREE_POOL_NODE = AutoscaleContants.NODE_POOL_PREFIX + "1";
    private static final String NODE_INSTANCE_ID = "i-0123456789abcdef0";

    private final AutoscalerService autoscalerService = mock(AutoscalerService.class);
    private final CloudFacade cloudFacade = mock(CloudFacade.class);
    private final PipelineRunManager pipelineRunManager = mock(PipelineRunManager.class);
    private final MetadataManager metadataManager = mock(MetadataManager.class);
    private final IAMProfileVerifier iamProfileVerifier = mock(IAMProfileVerifier.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final InstanceRequest requiredInstance = new InstanceRequest();

    private final ReassignHandler reassignHandler = newReassignHandler(new ArrayList<>());

    private Set<String> scheduledRuns;
    private Set<String> reassignedNodes;

    @Before
    public void setUp() {
        scheduledRuns = new HashSet<>();
        reassignedNodes = new HashSet<>();
        doReturn(false).when(preferenceManager).getPreference(SystemPreferences.CLUSTER_DISABLE_REASSIGN);
        mockFreeNode(FREE_NODE, null);
        doReturn(true).when(cloudFacade).reassignNode(eq(FREE_NODE_RUN_ID), eq(ID), anyStringMap());
        doReturn(true).when(cloudFacade).reassignPoolNode(eq(FREE_POOL_NODE), eq(ID), anyStringMap());
    }

    @Test
    public void shouldNotReassignWithCreateNewNodeParameter() {
        doReturn(Optional.of(pipelineRunWithCreateNewNodeParameter())).when(pipelineRunManager).findRun(ID);

        final boolean result = reassignHandler.tryReassignNode(null, null, null,
                String.valueOf(ID), ID, null, null);
        assertThat(result).isFalse();
    }

    @Test
    public void shouldNotReassignWindowsToolRun() {
        final PipelineRun pipelineRun = getPipelineRun(ID);
        pipelineRun.setPlatform(WINDOWS);
        pipelineRun.setPipelineRunParameters(Collections.emptyList());
        doReturn(Optional.of(pipelineRun)).when(pipelineRunManager).findRun(ID);

        final boolean result = reassignHandler.tryReassignNode(null, null, null,
                String.valueOf(ID), ID, null, null);
        assertThat(result).isFalse();
    }

    @Test
    public void shouldNotReassignRunOnWindowsNode() {
        final PipelineRun pipelineRun = getPipelineRun(ID);
        pipelineRun.setPipelineRunParameters(Collections.emptyList());
        doReturn(Optional.of(pipelineRun)).when(pipelineRunManager).findRun(ID);
        final String nodeId = "1";
        final List<String> nodes = Collections.singletonList(nodeId);

        doReturn(getRunningWindowsInstance())
            .when(autoscalerService).getPreviousRunInstance(Mockito.anyString(), Mockito.any());
        final boolean result = reassignHandler.tryReassignNode(null, null, null,
                String.valueOf(ID), ID, null, nodes);
        assertThat(result).isFalse();
    }

    @Test
    public void shouldNotReassignWithOtherTrueValuesOfCreateNewNodeParameter() {
        for (final String value : Arrays.asList("TRUE", "yes", "on", "Y", "t")) {
            mockRun(pipelineRun(new PipelineRunParameter(CP_CREATE_NEW_NODE, value)));

            assertThat(tryReassign(FREE_NODE)).as(value).isFalse();
        }
        verify(cloudFacade, never()).reassignNode(anyLong(), anyLong(), anyStringMap());
    }

    @Test
    public void shouldNotReassignWithTrueDedicatedInstanceParameter() {
        mockRun(pipelineRun(new PipelineRunParameter(CP_CAP_DEDICATED_INSTANCE, TRUE)));

        assertThat(tryReassign(FREE_NODE)).isFalse();
        verify(autoscalerService, never()).getPreviousRunInstance(anyString(), any());
        verify(cloudFacade, never()).reassignNode(anyLong(), anyLong(), anyStringMap());
    }

    @Test
    public void shouldNotReassignRestrictedImageRunWithoutCreateNewNodeParameter() {
        final PipelineRun run = pipelineRun();
        mockRun(run);
        doReturn(true).when(iamProfileVerifier).isImageRestricted(run);

        assertThat(tryReassign(FREE_NODE)).isFalse();
        verify(cloudFacade, never()).reassignNode(anyLong(), anyLong(), anyStringMap());
    }

    @Test
    public void shouldNotReassignWhenReassignIsDisabled() {
        mockRun(pipelineRun());
        doReturn(true).when(preferenceManager).getPreference(SystemPreferences.CLUSTER_DISABLE_REASSIGN);

        assertThat(tryReassign(FREE_NODE)).isFalse();
        verify(cloudFacade, never()).reassignNode(anyLong(), anyLong(), anyStringMap());
    }

    @Test
    public void shouldReassignWithoutCreateNewNodeParameter() {
        mockRun(pipelineRun());

        assertThat(tryReassign(FREE_NODE)).isTrue();
        verify(cloudFacade).reassignNode(eq(FREE_NODE_RUN_ID), eq(ID), anyStringMap());
        assertThat(scheduledRuns).containsExactly(String.valueOf(ID));
        assertThat(reassignedNodes).containsExactly(FREE_NODE);
    }

    @Test
    public void shouldReassignWithFalseCreateNewNodeAndDedicatedInstanceParameters() {
        mockRun(pipelineRun(new PipelineRunParameter(CP_CREATE_NEW_NODE, FALSE),
                new PipelineRunParameter(CP_CAP_DEDICATED_INSTANCE, FALSE)));

        assertThat(tryReassign(FREE_NODE)).isTrue();
        verify(cloudFacade).reassignNode(eq(FREE_NODE_RUN_ID), eq(ID), anyStringMap());
    }

    @Test
    public void shouldReassignRunWithNullParameterList() {
        final PipelineRun run = getPipelineRun(ID);
        run.setPipelineRunParameters(null);
        mockRun(run);

        assertThat(tryReassign(FREE_NODE)).isTrue();
        verify(cloudFacade).reassignNode(eq(FREE_NODE_RUN_ID), eq(ID), anyStringMap());
    }

    @Test
    public void shouldReassignNodeWithoutPoolToRunThatIsNotFound() {
        doReturn(Optional.empty()).when(pipelineRunManager).findRun(ID);

        assertThat(tryReassign(FREE_NODE)).isTrue();
        verify(cloudFacade).reassignNode(eq(FREE_NODE_RUN_ID), eq(ID), anyStringMap());
    }

    @Test
    public void shouldNotReassignPoolNodeWithFilterToRunThatIsNotFound() {
        doReturn(Optional.empty()).when(pipelineRunManager).findRun(ID);
        mockFreeNode(FREE_POOL_NODE, poolWithFilter(new RunOwnerPoolInstanceFilter()));

        assertThat(tryReassign(FREE_POOL_NODE)).isFalse();
        verify(cloudFacade, never()).reassignPoolNode(anyString(), anyLong(), anyStringMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldMatchPoolFilterWithTheRunLoadedOnce() {
        final PipelineRun run = pipelineRun();
        mockRun(run);
        final RunOwnerPoolInstanceFilter ownerFilter = new RunOwnerPoolInstanceFilter();
        final PoolFilterHandler ownerHandler = mock(PoolFilterHandler.class);
        doReturn(PoolInstanceFilterType.RUN_OWNER).when(ownerHandler).type();
        doReturn(true).when(ownerHandler).matches(ownerFilter, run);
        mockFreeNode(FREE_POOL_NODE, poolWithFilter(ownerFilter));

        assertThat(newReassignHandler(Collections.singletonList(ownerHandler))
                .tryReassignNode(null, scheduledRuns, reassignedNodes, String.valueOf(ID), ID,
                        requiredInstance, Collections.singletonList(FREE_POOL_NODE))).isTrue();
        verify(ownerHandler).matches(ownerFilter, run);
        verify(pipelineRunManager, times(1)).findRun(ID);
        verify(metadataManager).prepareCloudResourceTags(run);
        verify(cloudFacade).reassignPoolNode(eq(FREE_POOL_NODE), eq(ID), anyStringMap());
    }

    @Test
    public void shouldAllowReassignOfRunWithoutReasonsToRefuseIt() {
        assertThat(reassignHandler.isReassignAllowed(pipelineRun())).isTrue();
        assertThat(reassignHandler.isReassignAllowed(getPipelineRun(ID))).isTrue();
    }

    @Test
    public void shouldNotAllowReassignOfRunWithReasonsToRefuseIt() {
        final PipelineRun windowsRun = pipelineRun();
        windowsRun.setPlatform(WINDOWS);
        final PipelineRun restrictedRun = pipelineRun();
        doReturn(true).when(iamProfileVerifier).isImageRestricted(restrictedRun);

        assertThat(reassignHandler.isReassignAllowed(windowsRun)).isFalse();
        assertThat(reassignHandler.isReassignAllowed(
                pipelineRun(new PipelineRunParameter(CP_CREATE_NEW_NODE, TRUE)))).isFalse();
        assertThat(reassignHandler.isReassignAllowed(
                pipelineRun(new PipelineRunParameter(CP_CAP_DEDICATED_INSTANCE, TRUE)))).isFalse();
        assertThat(reassignHandler.isReassignAllowed(restrictedRun)).isFalse();
    }

    @Test
    public void shouldNotAllowReassignOfAnyRunWhenReassignIsDisabled() {
        doReturn(true).when(preferenceManager).getPreference(SystemPreferences.CLUSTER_DISABLE_REASSIGN);

        assertThat(reassignHandler.isReassignAllowed(pipelineRun())).isFalse();
    }

    private boolean tryReassign(final String freeNode) {
        return reassignHandler.tryReassignNode(null, scheduledRuns, reassignedNodes, String.valueOf(ID), ID,
                requiredInstance, Collections.singletonList(freeNode));
    }

    private ReassignHandler newReassignHandler(final List<PoolFilterHandler> filterHandlers) {
        return new ReassignHandler(autoscalerService, cloudFacade, pipelineRunManager, filterHandlers,
                metadataManager, iamProfileVerifier, preferenceManager);
    }

    private void mockRun(final PipelineRun run) {
        doReturn(Optional.of(run)).when(pipelineRunManager).findRun(ID);
    }

    private void mockFreeNode(final String nodeId, final NodePool pool) {
        final RunInstance instance = new RunInstance();
        instance.setNodeId(NODE_INSTANCE_ID);
        final RunningInstance freeNode = new RunningInstance();
        freeNode.setInstance(instance);
        freeNode.setPool(pool);
        freeNode.setPrePulledImages(Collections.emptySet());
        doReturn(freeNode).when(autoscalerService).getPreviousRunInstance(nodeId, null);
        doReturn(true).when(autoscalerService).requirementsMatchWithImages(freeNode, requiredInstance);
        doReturn(true).when(autoscalerService).requirementsMatch(freeNode, requiredInstance);
    }

    private NodePool poolWithFilter(final RunOwnerPoolInstanceFilter filter) {
        final NodePool pool = new NodePool();
        pool.setFilter(new PoolFilter(PoolFilterOperator.AND, Collections.singletonList(filter)));
        return pool;
    }

    private RunningInstance getRunningWindowsInstance() {
        final RunningInstance runningInstance = new RunningInstance();
        final RunInstance runInstance = new RunInstance();
        runInstance.setNodePlatform(WINDOWS);
        runningInstance.setInstance(runInstance);
        return runningInstance;
    }

    private PipelineRun pipelineRunWithCreateNewNodeParameter() {
        final PipelineRunParameter pipelineRunParameter =
                new PipelineRunParameter(CP_CREATE_NEW_NODE, TRUE);
        final PipelineRun pipelineRun = getPipelineRun(ID);
        pipelineRun.setPipelineRunParameters(Collections.singletonList(pipelineRunParameter));
        return pipelineRun;
    }

    private PipelineRun pipelineRun(final PipelineRunParameter... parameters) {
        final PipelineRun run = getPipelineRun(ID);
        final List<PipelineRunParameter> runParameters = new ArrayList<>();
        Collections.addAll(runParameters, parameters);
        run.setPipelineRunParameters(runParameters);
        return run;
    }
}
