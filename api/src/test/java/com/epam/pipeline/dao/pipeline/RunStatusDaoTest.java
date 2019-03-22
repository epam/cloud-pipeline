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

package com.epam.pipeline.dao.pipeline;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@Transactional
public class RunStatusDaoTest extends AbstractSpringTest {

    private static final String TEST_NAME = "TEST";
    private static final String TEST_REPOSITORY = "///";

    @Autowired
    private PipelineRunDao pipelineRunDao;

    @Autowired
    private PipelineDao pipelineDao;

    @Autowired
    private RunStatusDao runStatusDao;

    @Autowired
    private CloudRegionDao regionDao;

    private AbstractCloudRegion cloudRegion;

    private Pipeline testPipeline;

    private PipelineRun testRun;

    @Before
    public void setup() {
        cloudRegion = ObjectCreatorUtils.getDefaultAwsRegion();
        regionDao.create(cloudRegion);

        testPipeline = new Pipeline();
        testPipeline.setName(TEST_NAME);
        testPipeline.setRepository(TEST_REPOSITORY);
        testPipeline.setOwner(TEST_NAME);
        pipelineDao.createPipeline(testPipeline);

        testRun = ObjectCreatorUtils.createPipelineRun(1L, testPipeline.getId(),
                null, cloudRegion.getId());
        pipelineRunDao.createPipelineRun(testRun);
    }

    @Test
    public void shouldCreateNewRunStatus() {
        createStatus();
        assertThat(runStatusDao.loadRunStatus(testRun.getId()), hasSize(1));
    }

    @Test
    public void shouldLoadRunStatusByList() {
        createStatus();
        List<RunStatus> result = runStatusDao.loadRunStatus(Collections.singletonList(testRun.getId()));
        assertThat(result, hasSize(1));
        assertThat(result.get(0).getRunId(), equalTo(testRun.getId()));
    }

    @Test
    public void shouldDeleteStatusesByRunId() {
        createStatus();
        runStatusDao.deleteRunStatus(testRun.getId());
        assertTrue(runStatusDao.loadRunStatus(testRun.getId()).isEmpty());
    }

    @Test
    public void shouldDeleteStatusesByPipelineId() {
        createStatus();
        runStatusDao.deleteRunStatusForPipeline(testPipeline.getId());
        assertTrue(runStatusDao.loadRunStatus(testRun.getId()).isEmpty());
    }

    private RunStatus createStatus() {
        RunStatus runStatus = RunStatus.builder()
                .runId(testRun.getId())
                .status(TaskStatus.RUNNING)
                .timestamp(DateUtils.nowUTC()).build();
        runStatusDao.saveStatus(runStatus);
        return runStatus;
    }
}
