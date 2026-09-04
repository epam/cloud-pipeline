/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dao.pipeline;

import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.pipeline.CommitStatus;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.PipelineRunResult;
import com.epam.pipeline.entity.pipeline.run.parameter.RunSid;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.*;

@Transactional
public class PipelineRunResultDaoTest extends AbstractJdbcTest {
    private static final String TEST_USER = "TEST";
    private static final String TEST_POD_ID = "pod1";

    private static final long TEST_PARENT_1 = 12;

    private static final String TEST_REVISION_1 = "abcdefg1";
    private static final String USER = "test_user";

    private static final String TEST_REPO = "///";
    private static final String TEST_REPO_SSH = "git@test";

    private static final String TEST_PIPELINE_NAME = "Test";
    private static final String TEST_PLATFORM = "linux";

    @Autowired
    private PipelineRunDao pipelineRunDao;

    @Autowired
    private PipelineDao pipelineDao;

    @Autowired
    private CloudRegionDao regionDao;

    @Autowired
    private PipelineRunResultDao runResultDao;


    private Pipeline testPipeline;
    private AbstractCloudRegion cloudRegion;

    @BeforeEach    public void setup() {
        testPipeline = new Pipeline();
        testPipeline.setName(TEST_PIPELINE_NAME);
        testPipeline.setRepository(TEST_REPO);
        testPipeline.setRepositorySsh(TEST_REPO_SSH);
        testPipeline.setOwner(TEST_USER);
        pipelineDao.createPipeline(testPipeline);

        cloudRegion = ObjectCreatorUtils.getDefaultAwsRegion();
        regionDao.create(cloudRegion);
    }

    @Test
    public void canSaveAndLoadRunResults() {
        Pipeline testPipeline = getPipeline();
        PipelineRun run = createRun(testPipeline.getId(), null, TaskStatus.RUNNING, TEST_PARENT_1);

        List<PipelineRunResult> runResult = Arrays.asList(
            PipelineRunResult.builder().runId(run.getId())
                    .name(TEST_NAME).fileMask(TEST_STRING)
                    .items(Collections.singletonList(TEST_STRING)).build(),
            PipelineRunResult.builder().runId(run.getId())
                    .name(TEST_PIPELINE_NAME).fileMask(TEST_PIPELINE_NAME)
                    .items(Collections.singletonList(TEST_PIPELINE_NAME)).build()
        );

        runResultDao.addPipelineRunResults(runResult);

        List<PipelineRunResult> loaded = runResultDao.loadPipelineRunResultsForRun(run.getId());
        Assertions.assertThat(loaded).containsOnly(runResult.toArray(new PipelineRunResult[0]));
    }

    @Test
    public void shouldDeleteRunResultsByRunIds() {
        Pipeline testPipeline = getPipeline();
        PipelineRun run1 = createRun(testPipeline.getId(), null, TaskStatus.RUNNING, TEST_PARENT_1);
        PipelineRun run2 = createRun(testPipeline.getId(), null, TaskStatus.RUNNING, TEST_PARENT_1);
        PipelineRun run3 = createRun(testPipeline.getId(), null, TaskStatus.RUNNING, TEST_PARENT_1);

        runResultDao.addPipelineRunResults(Collections.singletonList(
                PipelineRunResult.builder().runId(run1.getId())
                        .name(TEST_NAME).fileMask(TEST_STRING)
                        .items(Collections.singletonList(TEST_STRING)).build()));
        runResultDao.addPipelineRunResults(Collections.singletonList(
                PipelineRunResult.builder().runId(run2.getId())
                        .name(TEST_NAME).fileMask(TEST_STRING)
                        .items(Collections.singletonList(TEST_STRING)).build()));
        runResultDao.addPipelineRunResults(Collections.singletonList(
                PipelineRunResult.builder().runId(run3.getId())
                        .name(TEST_NAME).fileMask(TEST_STRING)
                        .items(Collections.singletonList(TEST_STRING)).build()));

        runResultDao.deletePipelineRunResultsByRunIds(Arrays.asList(run1.getId(), run2.getId()), false);

        Assertions.assertThat(runResultDao.loadPipelineRunResultsForRun(run1.getId())).isEmpty();
        Assertions.assertThat(runResultDao.loadPipelineRunResultsForRun(run2.getId())).isEmpty();
        Assertions.assertThat(runResultDao.loadPipelineRunResultsForRun(run3.getId())).isNotEmpty();
    }


    private PipelineRun createRun(Long pipelineId, String params, TaskStatus status, Long parentRunId) {
        return createPipelineRun(pipelineId, params, status,
                parentRunId, null, null, null, null);
    }


    private PipelineRun createPipelineRun(Long pipelineId, String params, TaskStatus status, Long parentRunId,
                                          Long entitiesId,  Boolean isSpot, Long configurationId,
                                          List<RunSid> runSids) {
        PipelineRun run = new PipelineRun();
        run.setPipelineId(pipelineId);
        run.setVersion(TEST_REVISION_1);
        run.setStartDate(new Date());
        run.setInstanceStartDate(run.getStartDate());
        run.setEndDate(new Date());
        run.setStatus(status);
        run.setCommitStatus(CommitStatus.NOT_COMMITTED);
        run.setLastChangeCommitTime(new Date());
        run.setPodId(TEST_POD_ID);
        run.setParams(params);
        run.setOwner(USER);
        run.setParentRunId(parentRunId);
        run.setRunSids(runSids);
        run.setPlatform(TEST_PLATFORM);

        RunInstance instance = new RunInstance();
        instance.setCloudRegionId(cloudRegion.getId());
        instance.setCloudProvider(CloudProvider.AWS);
        instance.setSpot(isSpot);
        instance.setNodeId("1");
        instance.setNodePlatform(TEST_PLATFORM);
        run.setInstance(instance);
        run.setEntitiesIds(Collections.singletonList(entitiesId));
        run.setConfigurationId(configurationId);
        pipelineRunDao.createPipelineRun(run);
        return run;
    }

    private Pipeline getPipeline() {
        Pipeline testPipeline2 = new Pipeline();
        testPipeline2.setName(TEST_PIPELINE_NAME);
        testPipeline2.setRepository(TEST_REPO);
        testPipeline2.setRepositorySsh(TEST_REPO_SSH);
        testPipeline2.setOwner(TEST_USER);
        pipelineDao.createPipeline(testPipeline2);
        return testPipeline2;
    }

}
