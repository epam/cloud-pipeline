/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dao.run;

import com.epam.pipeline.dao.pipeline.PipelineDao;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.PipelineRunServiceUrl;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import com.epam.pipeline.util.TestUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class RunServiceUrlDaoTest extends AbstractJdbcTest {

    private static final String TEST_REGION_1 = "region1";
    private static final String TEST_REGION_2 = "region2";
    private static final String TEST_SERVICE_URL = "service_url";
    private static final String TEST_PIPELINE_NAME = "Test";
    private static final String TEST_USER = "TEST";
    private static final String TEST_REPO = "///";
    private static final String TEST_REPO_SSH = "git@test";
    private static final String USER = "test_user";

    @Autowired
    private RunServiceUrlDao runServiceUrlDao;
    @Autowired
    private PipelineRunDao pipelineRunDao;
    @Autowired
    private PipelineDao pipelineDao;
    @Autowired
    private CloudRegionDao cloudRegionDao;

    private PipelineRun pipelineRun;
    private PipelineRunServiceUrl pipelineRunServiceUrl1;
    private PipelineRunServiceUrl pipelineRunServiceUrl2;

    @Before
    public void setup() {
        final Pipeline testPipeline = new Pipeline();
        testPipeline.setName(TEST_PIPELINE_NAME);
        testPipeline.setRepository(TEST_REPO);
        testPipeline.setRepositorySsh(TEST_REPO_SSH);
        testPipeline.setOwner(TEST_USER);
        pipelineDao.createPipeline(testPipeline);

        final AbstractCloudRegion cloudRegion = ObjectCreatorUtils.getDefaultAwsRegion();
        cloudRegionDao.create(cloudRegion);

        pipelineRun = TestUtils.createPipelineRun(testPipeline.getId(), null, TaskStatus.RUNNING, USER,
                null, null, true, null, null,
                "pod-id", cloudRegion.getId());
        pipelineRunDao.createPipelineRun(pipelineRun);

        pipelineRunServiceUrl1 = pipelineRunServiceUrl(TEST_REGION_1);
        pipelineRunServiceUrl2 = pipelineRunServiceUrl(TEST_REGION_2);

        runServiceUrlDao.save(pipelineRunServiceUrl1);
        runServiceUrlDao.save(pipelineRunServiceUrl2);
    }

    @Test
    public void shouldUpdateServiceUrl() {
        final PipelineRunServiceUrl loadedServiceUrl = runServiceUrlDao
                .findByPipelineRunIdAndRegion(pipelineRun.getId(), TEST_REGION_1)
                .orElseThrow(() -> new IllegalStateException("Service url is not exists"));
        runServiceUrlDao.deleteById(loadedServiceUrl.getId());
        assertThat(runServiceUrlDao
                .findByPipelineRunIdAndRegion(pipelineRun.getId(), TEST_REGION_1).isPresent())
                .isFalse();
        assertServiceUrlExists(TEST_REGION_2);


        final PipelineRunServiceUrl pipelineRunServiceUrl = pipelineRunServiceUrl(TEST_REGION_1);
        runServiceUrlDao.save(pipelineRunServiceUrl);

        assertServiceUrlExists(TEST_REGION_1);
        assertServiceUrlExists(TEST_REGION_2);
    }

    @Test
    public void shouldFindByRunId() {
        final List<PipelineRunServiceUrl> result = runServiceUrlDao.findByRunId(pipelineRun.getId());
        assertThat(result).hasSize(2)
                .contains(pipelineRunServiceUrl1)
                .contains(pipelineRunServiceUrl2);
    }

    @Test
    public void shouldDeleteByRunId() {
        runServiceUrlDao.deleteByPipelineRunId(pipelineRun.getId());
        final List<PipelineRunServiceUrl> empty = runServiceUrlDao.findByRunId(pipelineRun.getId());
        assertThat(empty).isEmpty();
    }

    private void assertServiceUrlExists(final String region) {
        assertThat(runServiceUrlDao.findByPipelineRunIdAndRegion(pipelineRun.getId(), region)
                .isPresent())
                .isTrue();
    }

    private PipelineRunServiceUrl pipelineRunServiceUrl(final String region) {
        final PipelineRunServiceUrl pipelineRunServiceUrl = new PipelineRunServiceUrl();
        pipelineRunServiceUrl.setPipelineRunId(pipelineRun.getId());
        pipelineRunServiceUrl.setRegion(region);
        pipelineRunServiceUrl.setServiceUrl(TEST_SERVICE_URL);
        return pipelineRunServiceUrl;
    }

}