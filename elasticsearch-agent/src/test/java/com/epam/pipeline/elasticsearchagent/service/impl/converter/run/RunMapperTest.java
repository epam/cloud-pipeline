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

import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.elasticsearchagent.model.PipelineRunWithLog;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;

import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyPermissions;
import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyPipelineRun;
import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyPipelineUser;
import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyRunLogs;
import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyRunParameters;
import static com.epam.pipeline.elasticsearchagent.TestConstants.PERMISSIONS_CONTAINER;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_DESCRIPTION;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_NAME;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_PATH;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_VALUE;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_VERSION;
import static com.epam.pipeline.elasticsearchagent.TestConstants.USER;

@SuppressWarnings({"PMD.TooManyStaticImports"})
class RunMapperTest {

    private static final int NODE_DISK = 40;
    private static final BigDecimal PRICE = new BigDecimal("1.2");

    @Test
    void shouldMapRun() throws IOException {
        PipelineRunMapper mapper = new PipelineRunMapper();

        PipelineRunWithLog pipelineRunWithLog = new PipelineRunWithLog();

        RunInstance instance = new RunInstance();
        instance.setNodeType("type");
        instance.setCloudRegionId(1L);
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

        PipelineRun run = new PipelineRun();
        run.setId(1L);
        run.setName(TEST_NAME);
        run.setPipelineName(TEST_NAME);
        run.setInstance(instance);
        run.setStatus(TaskStatus.SUCCESS);
        run.setPipelineName(TEST_NAME);
        run.setVersion(TEST_VERSION);
        run.setRunStatuses(Collections.singletonList(runStatus));
        run.setPricePerHour(PRICE);
        run.setPipelineRunParameters(Collections.singletonList(parameter));

        RunLog runLog = new RunLog();
        runLog.setLogText(TEST_DESCRIPTION);
        runLog.setStatus(TaskStatus.SUCCESS);
        runLog.setTask(new PipelineTask(TEST_NAME));

        pipelineRunWithLog.setPipelineRun(run);
        pipelineRunWithLog.setRunOwner(USER);
        pipelineRunWithLog.setRunLogs(Collections.singletonList(runLog));

        EntityContainer<PipelineRunWithLog> container = EntityContainer.<PipelineRunWithLog>builder()
                .entity(pipelineRunWithLog)
                .owner(USER)
                .permissions(PERMISSIONS_CONTAINER)
                .build();
        XContentBuilder contentBuilder = mapper.map(container);

        verifyPipelineRun(run, TEST_NAME + " " + TEST_VERSION, contentBuilder);
        verifyRunParameters(Collections.singletonList(TEST_NAME + " " + TEST_VALUE), contentBuilder);
        verifyRunLogs(Collections.singletonList(TEST_NAME + " " + TEST_DESCRIPTION), contentBuilder);
        verifyPipelineUser(USER, contentBuilder);
        verifyPermissions(PERMISSIONS_CONTAINER, contentBuilder);
    }
}
