/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.run.RestartRun;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@Transactional
public class RestartRunDaoTest extends AbstractJdbcTest {

    private static final long TEST_RUN_ID_1 = 1;
    private static final long TEST_RUN_ID_2 = 2;
    private static final long TEST_RUN_ID_3 = 3;
    private static final long TEST_RUN_ID_4 = 4;
    private static final long TEST_RUN_ID_5 = 5;
    private static final long TEST_RUN_ID_6 = 6;
    private static final String TEST_NAME = "TEST";
    private static final String TEST_REPOSITORY = "///";
    private static final String TEST_REPOSITORY_SSH = "git@test";

    @Autowired
    private RestartRunDao restartRunDao;

    @Autowired
    private PipelineRunDao pipelineRunDao;

    @Autowired
    private PipelineDao pipelineDao;

    @Autowired
    private CloudRegionDao regionDao;

    private AbstractCloudRegion cloudRegion;

    private Pipeline testPipeline;
    private RestartRun restartRun1;
    private RestartRun restartRun2;
    private RestartRun restartRun3;
    private RestartRun restartRun4;

    @Before
    public void setup() {
        cloudRegion = ObjectCreatorUtils.getDefaultAwsRegion();
        regionDao.create(cloudRegion);

        testPipeline = new Pipeline();
        testPipeline.setName(TEST_NAME);
        testPipeline.setRepository(TEST_REPOSITORY);
        testPipeline.setRepositorySsh(TEST_REPOSITORY_SSH);
        testPipeline.setOwner(TEST_NAME);
        pipelineDao.createPipeline(testPipeline);

        Long pipelineId = testPipeline.getId();

        createPipelineRun(TEST_RUN_ID_1, pipelineId, null);

        restartRun1 = new RestartRun();
        restartRun1.setParentRunId(TEST_RUN_ID_1);
        restartRun1.setRestartedRunId(TEST_RUN_ID_2);
        restartRun1.setDate(new Date());

        createPipelineRun(TEST_RUN_ID_2, pipelineId, TEST_RUN_ID_1);

        restartRun2 = new RestartRun();
        restartRun2.setParentRunId(TEST_RUN_ID_2);
        restartRun2.setRestartedRunId(TEST_RUN_ID_3);
        restartRun2.setDate(new Date());

        createPipelineRun(TEST_RUN_ID_3, pipelineId, TEST_RUN_ID_2);

        restartRun3 = new RestartRun();
        restartRun3.setParentRunId(TEST_RUN_ID_3);
        restartRun3.setRestartedRunId(TEST_RUN_ID_4);
        restartRun3.setDate(new Date());

        createPipelineRun(TEST_RUN_ID_5, pipelineId, null);

        restartRun4 = new RestartRun();
        restartRun4.setParentRunId(TEST_RUN_ID_5);
        restartRun4.setRestartedRunId(TEST_RUN_ID_6);
        restartRun4.setDate(new Date());
    }

    @Test
    public void testCreateAndLoadPipelineRestartRun() {
        restartRunDao.createPipelineRestartRun(restartRun1);
        RestartRun loadRestartRun = restartRunDao.loadPipelineRestartedRunForParentRun(restartRun1.getParentRunId());

        assertNotNull(loadRestartRun);
        assertEquals(loadRestartRun.getParentRunId(), restartRun1.getParentRunId());
        assertEquals(loadRestartRun.getRestartedRunId(), restartRun1.getRestartedRunId());
    }

    @Test
    public void testCountPipelineRestartRuns() {
        restartRunDao.createPipelineRestartRun(restartRun1);
        restartRunDao.createPipelineRestartRun(restartRun2);
        restartRunDao.createPipelineRestartRun(restartRun4);

        int countRuns = restartRunDao.countPipelineRestartRuns(restartRun3.getParentRunId());
        assertEquals(2, countRuns);
    }

    @Test
    public void testLoadAllRestartedRunsForInitialRun() {
        restartRunDao.createPipelineRestartRun(restartRun1);
        restartRunDao.createPipelineRestartRun(restartRun2);
        restartRunDao.createPipelineRestartRun(restartRun3);
        restartRunDao.createPipelineRestartRun(restartRun4);

        List<RestartRun> runs = restartRunDao.loadAllRestartedRunsForInitialRun(restartRun2.getRestartedRunId());
        assertNotNull(runs);
        assertEquals(3, runs.size());
        assertEquals(runs.get(0).getParentRunId(), restartRun1.getParentRunId());
        assertEquals(runs.get(1).getParentRunId(), restartRun2.getParentRunId());
        assertEquals(runs.get(2).getParentRunId(), restartRun3.getParentRunId());

        List<RestartRun> runs2 = restartRunDao.loadAllRestartedRunsForInitialRun(restartRun4.getRestartedRunId());
        assertNotNull(runs);
        assertEquals(1, runs2.size());
        assertEquals(runs2.get(0).getParentRunId(), restartRun4.getParentRunId());
    }

    private PipelineRun createPipelineRun(Long runId, Long pipelineId, Long parentRunId) {
        PipelineRun run = ObjectCreatorUtils.createPipelineRun(runId, pipelineId, parentRunId, cloudRegion.getId());
        pipelineRunDao.createPipelineRun(run);
        return run;
    }
}
