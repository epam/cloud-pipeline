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

import com.epam.pipeline.entity.cluster.pool.InstanceRequest;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.cluster.pool.RunningInstance;
import com.epam.pipeline.entity.cluster.pool.filter.PoolFilter;
import com.epam.pipeline.entity.cluster.pool.filter.PoolFilterOperator;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.PoolInstanceFilter;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.PoolInstanceFilterType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RunStatusManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipelineRun;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class ReassignHandlerTest {

    private static final String WINDOWS = "windows";
    private static final String POOL_NODE_ID = "p-12345";
    private static final String ANOTHER_OWNER = "ANOTHER_USER";

    private final AutoscalerService autoscalerService = mock(AutoscalerService.class);
    private final CloudFacade cloudFacade = mock(CloudFacade.class);
    private final PipelineRunManager pipelineRunManager = mock(PipelineRunManager.class);
    private final MetadataManager metadataManager = mock(MetadataManager.class);
    private final IAMProfileVerifier iamProfileVerifier = mock(IAMProfileVerifier.class);
    private final RunStatusManager runStatusManager = mock(RunStatusManager.class);

    private final ReassignHandler reassignHandler = new ReassignHandler(
            autoscalerService,
            cloudFacade,
            pipelineRunManager,
            new ArrayList<>(),
            metadataManager,
            iamProfileVerifier,
            runStatusManager);

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
    public void shouldNotReassignAReservationPoolNodeOutsideThePoolsWindow() {
        final NodePool pool = inactivePool();
        pool.setCapacityReservation(true);

        assertThat(tryReassignOnto(pool)).isFalse();
        verify(cloudFacade, never()).reassignPoolNode(anyString(), anyLong(), any());
    }

    @Test
    public void shouldStillReassignAnOrdinaryPoolNodeWhenThePoolIsInactive() {
        final NodePool pool = inactivePool();
        doReturn(true).when(cloudFacade).reassignPoolNode(anyString(), anyLong(), any());

        assertThat(tryReassignOnto(pool)).isTrue();
    }

    @Test
    public void shouldReassignAPoolsNodeToItsOwnerWhateverTheOwnershipFilterSays() {
        final NodePool pool = filteredPool(PoolInstanceFilterType.RUN_OWNER);
        pool.setOwner(TEST_STRING.toLowerCase());
        doReturn(true).when(cloudFacade).reassignPoolNode(anyString(), anyLong(), any());

        assertThat(tryReassignOnto(pool)).isTrue();
    }

    @Test
    public void shouldReassignAPoolsNodeToItsOwnerWhateverTheOwnerGroupFilterSays() {
        final NodePool pool = filteredPool(PoolInstanceFilterType.RUN_OWNER_GROUP);
        pool.setOwner(TEST_STRING);
        doReturn(true).when(cloudFacade).reassignPoolNode(anyString(), anyLong(), any());

        assertThat(tryReassignOnto(pool)).isTrue();
    }

    @Test
    public void shouldHoldAFilterAboutTheRunItselfAgainstThePoolsOwnerToo() {
        final NodePool pool = filteredPool(PoolInstanceFilterType.DOCKER_IMAGE);
        pool.setOwner(TEST_STRING);

        assertThat(tryReassignOnto(pool)).isFalse();
        verify(cloudFacade, never()).reassignPoolNode(anyString(), anyLong(), any());
    }

    @Test
    public void shouldNotReassignAFilteredPoolsNodeToSomeoneElsesRun() {
        final NodePool pool = filteredPool(PoolInstanceFilterType.RUN_OWNER);
        pool.setOwner(ANOTHER_OWNER);

        assertThat(tryReassignOnto(pool)).isFalse();
        verify(cloudFacade, never()).reassignPoolNode(anyString(), anyLong(), any());
    }

    @Test
    public void shouldNotGiveAReservationPoolsOwnerANodeOutsideThePoolsWindow() {
        final NodePool pool = filteredPool(PoolInstanceFilterType.RUN_OWNER);
        pool.setOwner(TEST_STRING);
        pool.setCapacityReservation(true);

        assertThat(tryReassignOnto(pool)).isFalse();
        verify(cloudFacade, never()).reassignPoolNode(anyString(), anyLong(), any());
    }

    private boolean tryReassignOnto(final NodePool pool) {
        final PipelineRun pipelineRun = getPipelineRun(ID);
        pipelineRun.setPipelineRunParameters(Collections.emptyList());
        doReturn(Optional.of(pipelineRun)).when(pipelineRunManager).findRun(ID);
        final RunningInstance poolInstance = pool.toRunningInstance();
        poolInstance.getInstance().setNodeId(POOL_NODE_ID);
        doReturn(poolInstance).when(autoscalerService).getPreviousRunInstance(Mockito.anyString(), Mockito.any());
        doReturn(true).when(autoscalerService).requirementsMatch(any(), any());
        final InstanceRequest request = new InstanceRequest();
        request.setInstance(new RunInstance());
        return reassignHandler.tryReassignNode(null, new HashSet<>(), new HashSet<>(),
                String.valueOf(ID), ID, request, Collections.singletonList(POOL_NODE_ID));
    }

    /** A pool that is not active now: it has nodes, and its window has passed. */
    private static NodePool inactivePool() {
        final NodePool pool = new NodePool();
        pool.setId(ID);
        pool.setInstanceType("m5.large");
        pool.setCount(1);
        pool.setStartDate(DateUtils.nowUTC().minusDays(2));
        pool.setEndDate(DateUtils.nowUTC().minusMinutes(1));
        return pool;
    }

    /** A pool with one filter of the given type that matches no run: no filter handler is registered here. */
    private static NodePool filteredPool(final PoolInstanceFilterType type) {
        final PoolInstanceFilter filter = mock(PoolInstanceFilter.class);
        doReturn(type).when(filter).getType();
        final NodePool pool = inactivePool();
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
                new PipelineRunParameter("CP_CREATE_NEW_NODE", "true");
        final PipelineRun pipelineRun = getPipelineRun(ID);
        pipelineRun.setPipelineRunParameters(Collections.singletonList(pipelineRunParameter));
        return pipelineRun;
    }
}
