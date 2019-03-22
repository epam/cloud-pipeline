/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.run;

import com.epam.pipeline.elasticsearchagent.exception.EntityNotFoundException;
import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.elasticsearchagent.model.PipelineRunWithLog;
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.vo.EntityPermissionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.elasticsearchagent.LoaderVerificationUtils.verifyPermissions;
import static com.epam.pipeline.elasticsearchagent.LoaderVerificationUtils.verifyPipelineRun;
import static com.epam.pipeline.elasticsearchagent.LoaderVerificationUtils.verifyPipelineUser;
import static com.epam.pipeline.elasticsearchagent.LoaderVerificationUtils.verifyRunInstance;
import static com.epam.pipeline.elasticsearchagent.LoaderVerificationUtils.verifyRunLogs;
import static com.epam.pipeline.elasticsearchagent.LoaderVerificationUtils.verifyRunParameters;
import static com.epam.pipeline.elasticsearchagent.LoaderVerificationUtils.verifyRunStatuses;
import static com.epam.pipeline.elasticsearchagent.ObjectCreationUtils.buildEntityPermissionVO;
import static com.epam.pipeline.elasticsearchagent.TestConstants.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"PMD.TooManyStaticImports"})
class PipelineRunLoaderTest {

    private static final int NODE_DISK = 40;
    private static final BigDecimal PRICE = new BigDecimal("1.2");

    @Mock
    private CloudPipelineAPIClient apiClient;

    @BeforeEach
    void setup() {
        EntityPermissionVO entityPermissionVO =
                buildEntityPermissionVO(USER_NAME, ALLOWED_USERS, DENIED_USERS, ALLOWED_GROUPS, DENIED_GROUPS);

        when(apiClient.loadUserByName(anyString())).thenReturn(USER);
        when(apiClient.loadPermissionsForEntity(anyLong(), any())).thenReturn(entityPermissionVO);
    }

    @Test
    void shouldLoadPipelineRunTest() throws EntityNotFoundException {
        RunInstance instance = new RunInstance();
        instance.setNodeType("type");
        instance.setAwsRegionId(TEST_REGION);
        instance.setSpot(true);
        instance.setNodeDisk(NODE_DISK);
        instance.setNodeId("id");
        instance.setNodeImage(TEST_PATH);
        instance.setNodeName(TEST_NAME);

        RunStatus runStatus = new RunStatus();
        runStatus.setRunId(1L);
        runStatus.setStatus(TaskStatus.SUCCESS);

        PipelineRunParameter parameter = new PipelineRunParameter();
        parameter.setName(TEST_NAME);
        parameter.setValue(TEST_VALUE);

        PipelineRun expectedPipelineRun = new PipelineRun();
        expectedPipelineRun.setId(1L);
        expectedPipelineRun.setName(TEST_NAME);
        expectedPipelineRun.setPipelineName(TEST_NAME);
        expectedPipelineRun.setPipelineId(1L);
        expectedPipelineRun.setInstance(instance);
        expectedPipelineRun.setStatus(TaskStatus.SUCCESS);
        expectedPipelineRun.setVersion(TEST_VERSION);
        expectedPipelineRun.setRunStatuses(Collections.singletonList(runStatus));
        expectedPipelineRun.setPricePerHour(PRICE);
        expectedPipelineRun.setOwner(TEST_NAME);
        expectedPipelineRun.setPipelineRunParameters(Collections.singletonList(parameter));

        RunLog runLog = new RunLog();
        runLog.setLogText(TEST_DESCRIPTION);
        runLog.setStatus(TaskStatus.SUCCESS);
        runLog.setTask(new PipelineTask(TEST_NAME));
        List<RunLog> runLogs = Collections.singletonList(runLog);

        PipelineRunWithLog expectedPipelineRunWithLog = new PipelineRunWithLog();
        expectedPipelineRunWithLog.setPipelineRun(expectedPipelineRun);
        expectedPipelineRunWithLog.setRunOwner(USER);
        expectedPipelineRunWithLog.setRunLogs(runLogs);

        PipelineRunLoader pipelineRunLoader = new PipelineRunLoader(apiClient);

        when(apiClient.loadPipelineRunWithLogs(anyLong())).thenReturn(expectedPipelineRunWithLog);
        when(apiClient.loadPipelineRun(anyLong())).thenReturn(expectedPipelineRun);

        Optional<EntityContainer<PipelineRunWithLog>> container = pipelineRunLoader.loadEntity(1L);
        EntityContainer<PipelineRunWithLog> pipelineRunEntityContainer = container.orElseThrow(AssertionError::new);
        PipelineRunWithLog actualPipelineRunWithLog = pipelineRunEntityContainer.getEntity();

        assertNotNull(actualPipelineRunWithLog);

        PipelineRun actualPipelineRun = actualPipelineRunWithLog.getPipelineRun();
        assertNotNull(actualPipelineRun);

        List<RunLog> actualRunLogs = actualPipelineRunWithLog.getRunLogs();
        assertNotNull(actualRunLogs);

        verifyPipelineRun(expectedPipelineRun, actualPipelineRun);
        verifyRunInstance(expectedPipelineRun.getInstance(), actualPipelineRun.getInstance());
        verifyRunStatuses(expectedPipelineRun.getRunStatuses(), actualPipelineRun.getRunStatuses());
        verifyRunParameters(expectedPipelineRun.getPipelineRunParameters(),
                actualPipelineRun.getPipelineRunParameters());
        verifyRunLogs(runLogs, actualRunLogs);

        verifyPipelineUser(pipelineRunEntityContainer.getOwner());
        verifyPermissions(PERMISSIONS_CONTAINER_WITH_OWNER, pipelineRunEntityContainer.getPermissions());
    }
}