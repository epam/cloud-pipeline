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

package com.epam.pipeline.repository.run;

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
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class PipelineRunServiceUrlRepositoryTest extends AbstractJdbcTest {
    private static final String TEST_REGION_1 = "region1";
    private static final String TEST_REGION_2 = "region2";
    private static final String TEST_SERVICE_URL = "service_url";
    private static final String TEST_PIPELINE_NAME = "Test";
    private static final String TEST_USER = "TEST";
    private static final String TEST_REPO = "///";
    private static final String TEST_REPO_SSH = "git@test";
    private static final String USER = "test_user";

    @Autowired
    private PipelineRunServiceUrlRepository pipelineRunServiceUrlRepository;
    @Autowired
    private PipelineRunDao pipelineRunDao;
    @Autowired
    private PipelineDao pipelineDao;
    @Autowired
    private CloudRegionDao cloudRegionDao;
    @Autowired
    private TestEntityManager entityManager;

    private PipelineRun pipelineRun;

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
    }

    @Test
    public void shouldUpdateServiceUrl() {
        final PipelineRunServiceUrl pipelineRunServiceUrl1 = pipelineRunServiceUrl(TEST_REGION_1);
        final PipelineRunServiceUrl pipelineRunServiceUrl2 = pipelineRunServiceUrl(TEST_REGION_2);
        pipelineRunServiceUrlRepository.save(pipelineRunServiceUrl1);
        pipelineRunServiceUrlRepository.save(pipelineRunServiceUrl2);

        entityManager.flush();

        final PipelineRunServiceUrl loadedServiceUrl = pipelineRunServiceUrlRepository
                .findByPipelineRunIdAndRegion(pipelineRun.getId(), TEST_REGION_1)
                .orElseThrow(() -> new IllegalStateException("Service url is not exists"));
        pipelineRunServiceUrlRepository.delete(loadedServiceUrl);
        entityManager.flush();
        assertThat(pipelineRunServiceUrlRepository
                .findByPipelineRunIdAndRegion(pipelineRun.getId(), TEST_REGION_1).isPresent())
                .isFalse();
        assertServiceUrlExists(TEST_REGION_2);

        entityManager.flush();

        final PipelineRunServiceUrl pipelineRunServiceUrl = pipelineRunServiceUrl(TEST_REGION_1);
        pipelineRunServiceUrlRepository.save(pipelineRunServiceUrl);

        assertServiceUrlExists(TEST_REGION_1);
        assertServiceUrlExists(TEST_REGION_2);
    }

    @Test
    public void shouldFindByRunId() {
        final PipelineRunServiceUrl pipelineRunServiceUrl1 = pipelineRunServiceUrl(TEST_REGION_1);
        final PipelineRunServiceUrl pipelineRunServiceUrl2 = pipelineRunServiceUrl(TEST_REGION_2);
        pipelineRunServiceUrlRepository.save(pipelineRunServiceUrl1);
        pipelineRunServiceUrlRepository.save(pipelineRunServiceUrl2);

        entityManager.flush();

        final List<PipelineRunServiceUrl> result = StreamSupport.stream(pipelineRunServiceUrlRepository
                .findByPipelineRunId(pipelineRun.getId()).spliterator(), false).collect(Collectors.toList());
        assertThat(result).hasSize(2)
                .contains(pipelineRunServiceUrl1)
                .contains(pipelineRunServiceUrl2);
    }

    private void assertServiceUrlExists(final String region) {
        assertThat(pipelineRunServiceUrlRepository.findByPipelineRunIdAndRegion(pipelineRun.getId(), region)
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
