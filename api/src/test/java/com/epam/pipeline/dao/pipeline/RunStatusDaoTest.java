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
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunPrice;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatusInfo;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
public class RunStatusDaoTest extends AbstractJdbcTest {

    private static final String TEST_NAME = "TEST";
    private static final String TEST_REPOSITORY = "///";
    private static final String TEST_REPOSITORY_SSH = "git@test";
    private static final BigDecimal PRICE_PER_HOUR = new BigDecimal("0.10");
    private static final BigDecimal COMPUTE_PRICE_PER_HOUR = new BigDecimal("0.09600");
    private static final BigDecimal DISK_PRICE_PER_HOUR = new BigDecimal("0.00013");
    private static final BigDecimal UPDATED_PRICE_PER_HOUR = new BigDecimal("0.39");
    private static final BigDecimal UPDATED_COMPUTE_PRICE_PER_HOUR = new BigDecimal("0.38400");
    private static final BigDecimal UPDATED_DISK_PRICE_PER_HOUR = new BigDecimal("0.00026");

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

    @BeforeEach    public void setup() {
        cloudRegion = ObjectCreatorUtils.getDefaultAwsRegion();
        regionDao.create(cloudRegion);

        testPipeline = new Pipeline();
        testPipeline.setName(TEST_NAME);
        testPipeline.setRepository(TEST_REPOSITORY);
        testPipeline.setRepositorySsh(TEST_REPOSITORY_SSH);
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
        List<RunStatus> result = runStatusDao.loadRunStatus(Collections.singletonList(testRun.getId()), false);
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
    public void shouldSaveAndLoadRunStatusInfo() {
        runStatusDao.saveStatus(RunStatus.builder()
                .runId(testRun.getId())
                .status(TaskStatus.RUNNING)
                .timestamp(DateUtils.nowUTC())
                .runStatusInfo(RunStatusInfo.of(PRICE_PER_HOUR, COMPUTE_PRICE_PER_HOUR, DISK_PRICE_PER_HOUR))
                .build());

        final List<RunStatus> statuses = runStatusDao.loadRunStatus(testRun.getId());
        assertThat(statuses, hasSize(1));
        assertPrices(statuses.get(0), PRICE_PER_HOUR, COMPUTE_PRICE_PER_HOUR, DISK_PRICE_PER_HOUR);
    }

    @Test
    public void shouldLoadRunStatusWithoutRunStatusInfo() {
        createStatus();

        final List<RunStatus> statuses = runStatusDao.loadRunStatus(testRun.getId());
        assertThat(statuses, hasSize(1));
        assertNull(statuses.get(0).getRunStatusInfo());
    }

    @Test
    public void shouldUpdatePriceOfTheLatestRunStatusOnly() {
        final LocalDateTime now = DateUtils.nowUTC();
        runStatusDao.saveStatus(RunStatus.builder()
                .runId(testRun.getId())
                .status(TaskStatus.RUNNING)
                .timestamp(now.minusHours(1))
                .runStatusInfo(RunStatusInfo.of(PRICE_PER_HOUR, COMPUTE_PRICE_PER_HOUR, DISK_PRICE_PER_HOUR))
                .build());
        runStatusDao.saveStatus(RunStatus.builder()
                .runId(testRun.getId())
                .status(TaskStatus.PAUSED)
                .timestamp(now)
                .runStatusInfo(RunStatusInfo.of(PRICE_PER_HOUR, COMPUTE_PRICE_PER_HOUR, DISK_PRICE_PER_HOUR))
                .build());

        testRun.setPricePerHour(UPDATED_PRICE_PER_HOUR);
        testRun.setComputePricePerHour(UPDATED_COMPUTE_PRICE_PER_HOUR);
        testRun.setDiskPricePerHour(UPDATED_DISK_PRICE_PER_HOUR);
        runStatusDao.updatePriceForCurrentActiveRunStatus(testRun);

        final Map<TaskStatus, RunStatus> statuses = runStatusDao.loadRunStatus(testRun.getId()).stream()
                .collect(Collectors.toMap(RunStatus::getStatus, Function.identity()));
        assertPrices(statuses.get(TaskStatus.RUNNING), PRICE_PER_HOUR, COMPUTE_PRICE_PER_HOUR, DISK_PRICE_PER_HOUR);
        assertPrices(statuses.get(TaskStatus.PAUSED), UPDATED_PRICE_PER_HOUR, UPDATED_COMPUTE_PRICE_PER_HOUR,
                UPDATED_DISK_PRICE_PER_HOUR);
    }

    @Test
    public void shouldUpdatePriceOfRunStatusWithoutPricePerHour() {
        createStatus();

        testRun.setPricePerHour(null);
        testRun.setComputePricePerHour(UPDATED_COMPUTE_PRICE_PER_HOUR);
        testRun.setDiskPricePerHour(null);
        runStatusDao.updatePriceForCurrentActiveRunStatus(testRun);

        final List<RunStatus> statuses = runStatusDao.loadRunStatus(testRun.getId());
        assertThat(statuses, hasSize(1));
        assertPrices(statuses.get(0), null, UPDATED_COMPUTE_PRICE_PER_HOUR, null);
    }

    @Test
    public void shouldSkipPriceUpdateIfComputePriceIsMissing() {
        createStatus();

        testRun.setPricePerHour(UPDATED_PRICE_PER_HOUR);
        testRun.setComputePricePerHour(null);
        testRun.setDiskPricePerHour(UPDATED_DISK_PRICE_PER_HOUR);
        runStatusDao.updatePriceForCurrentActiveRunStatus(testRun);

        final List<RunStatus> statuses = runStatusDao.loadRunStatus(testRun.getId());
        assertThat(statuses, hasSize(1));
        assertNull(statuses.get(0).getRunStatusInfo());
    }

    private void assertPrices(final RunStatus status, final BigDecimal pricePerHour,
                              final BigDecimal computePricePerHour, final BigDecimal diskPricePerHour) {
        final RunPrice price = status.getRunStatusInfo().getPrice();
        assertPrice(pricePerHour, price.getPricePerHour());
        assertPrice(computePricePerHour, price.getComputePricePerHour());
        assertPrice(diskPricePerHour, price.getDiskPricePerHour());
    }

    private void assertPrice(final BigDecimal expected, final BigDecimal actual) {
        if (expected == null) {
            assertNull(actual);
            return;
        }
        assertThat(actual.compareTo(expected), equalTo(0));
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
