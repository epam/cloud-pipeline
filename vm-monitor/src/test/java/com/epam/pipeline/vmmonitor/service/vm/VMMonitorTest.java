/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.vmmonitor.service.vm;

import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.vmmonitor.model.vm.VirtualMachine;
import com.epam.pipeline.vmmonitor.service.pipeline.CloudPipelineAPIClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class VMMonitorTest {

    private static final String TEST_STRING = "TEST";
    private static final String RUN_ID_LABEL = "runid";
    private static final String POOL_ID_LABEL = "pool_id";
    private static final String RUN_ID_VALUE = "p-123";
    private static final String POOL_ID_VALUE = "123";
    private static final Long POOL_ID = 123L;
    private final Map<String, String> reqLabels = new HashMap<>();
    private final AwsRegion region = new AwsRegion(CloudProvider.AWS, TEST_STRING, TEST_STRING, TEST_STRING,
            TEST_STRING, TEST_STRING, TEST_STRING, TEST_STRING, 0, true);
    private VirtualMachine vm;
    private VMMonitor monitor;

    private final CloudPipelineAPIClient mockApiClient = mock(CloudPipelineAPIClient.class);
    private final VMMonitorService mockService = mock(AWSMonitorService.class);
    private final VMNotifier notifier = mock(VMNotifier.class);

    @BeforeEach
    public void setUp() {
        reqLabels.put(RUN_ID_LABEL, RUN_ID_VALUE);
        reqLabels.put(POOL_ID_LABEL, POOL_ID_VALUE);
        doReturn(CloudProvider.AWS).when(mockService).provider();
        vm = VirtualMachine.builder().tags(reqLabels).build();
        monitor = new VMMonitor(mockApiClient, notifier, Collections.singletonList(mockService),
                RUN_ID_LABEL, RUN_ID_LABEL, POOL_ID_LABEL);

    }

    @Test
    public void shouldNotNotifyMissingNodeWhenRunIdIsNotNumericAndPoolIdExists() {
        final NodeInstance nodeInstance = new NodeInstance();
        nodeInstance.setRunId(RUN_ID_VALUE);
        nodeInstance.setLabels(reqLabels);
        final PipelineRun pipelineRun = new PipelineRun();
        pipelineRun.setStatus(TaskStatus.RUNNING);
        final NodePool nodePool = new NodePool();
        nodePool.setId(POOL_ID);
        doReturn(Collections.singletonList(region)).when(mockApiClient).loadRegions();
        doReturn(Collections.singletonList(vm)).when(mockService).fetchRunningVms(region);
        doReturn(Collections.singletonList(nodeInstance)).when(mockApiClient).findNodes(vm.getPrivateIp());
        doReturn(pipelineRun).when(mockApiClient).loadRun(POOL_ID);
        doReturn(Collections.singletonList(nodePool)).when(mockApiClient).loadNodePools();
        monitor.monitor();

        verify(notifier, never()).notifyMissingNode(vm);
    }

    @Test
    public void shouldNotifyMissingNodeWhenRunIdIsNotNumericAndPoolIdDoesNotExist() {
        doReturn(Collections.singletonList(region)).when(mockApiClient).loadRegions();
        doReturn(Collections.singletonList(vm)).when(mockService).fetchRunningVms(region);
        monitor.monitor();

        verify(notifier).notifyMissingNode(vm);
    }
}
