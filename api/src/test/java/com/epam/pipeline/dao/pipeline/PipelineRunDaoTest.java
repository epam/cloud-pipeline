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
import com.epam.pipeline.controller.vo.PagingRunFilterVO;
import com.epam.pipeline.controller.vo.PipelineRunFilterVO;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.CommitStatus;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.parameter.RunAccessType;
import com.epam.pipeline.entity.pipeline.run.parameter.RunSid;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.execution.EnvVarsBuilder;
import com.epam.pipeline.manager.execution.EnvVarsBuilderTest;
import com.epam.pipeline.manager.execution.SystemParams;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Transactional
public class PipelineRunDaoTest extends AbstractSpringTest {
    private static final String TEST_USER = "TEST";
    private static final String TEST_PARAMS = "123 321";
    private static final String TEST_POD_ID = "pod1";
    private static final int HOURS_23 = 23;
    private static final int MINUTES_59 = 59;
    private static final int TEST_PAGE_SIZE = 10;

    private static final long TEST_PARENT_1 = 12;
    private static final long TEST_PARENT_2 = 123;

    private static final String TEST_REVISION_1 = "abcdefg1";
    private static final String TEST_REVISION_2 = "abcdefg2";
    private static final String TEST_REVISION_3 = "abcdefg3";
    private static final String USER = "test_user";

    private static final Long CONFIGURATION_ID_1 = 1L;
    private static final Long CONFIGURATION_ID_2 = 2L;
    private static final Long CONFIGURATION_ID_3 = 3L;

    private static final Long ENTITY_ID_1 = 1L;
    private static final Long ENTITY_ID_2 = 2L;
    private static final Long ENTITY_ID_3 = 3L;

    private static final String POD_STATUS = "PodLost";
    private static final String PRETTY_URL = "service";
    private static final String TEST_SERVICE_URL = "service_url";
    private static final String GROUP_NAME = "group_1";
    private static final BigDecimal PRICE_PER_HOUR = new BigDecimal("0.08");
    private static final String TEST_REPO = "///";
    private static final String TEST_REPO_SSH = "git@test";

    @Autowired
    private PipelineRunDao pipelineRunDao;

    @Autowired
    private PipelineDao pipelineDao;

    @Autowired
    private CloudRegionDao regionDao;

    private Pipeline testPipeline;
    private AbstractCloudRegion cloudRegion;

    @Before
    public void setup() {
        testPipeline = new Pipeline();
        testPipeline.setName("Test");
        testPipeline.setRepository(TEST_REPO);
        testPipeline.setRepositorySsh(TEST_REPO_SSH);
        testPipeline.setOwner(TEST_USER);
        pipelineDao.createPipeline(testPipeline);

        cloudRegion = ObjectCreatorUtils.getDefaultAwsRegion();
        regionDao.create(cloudRegion);
    }

    @Test
    public void testLoadByIds() {
        PipelineRun run1 = createTestPipelineRun();
        createTestPipelineRun();
        List<PipelineRun> pipelineRuns =
                pipelineRunDao.loadPipelineRuns(Collections.singletonList(run1.getId()));
        assertEquals(1, pipelineRuns.size());
        assertEquals(run1.getId(), pipelineRuns.get(0).getId());
    }

    @Test
    public void updateRunStatus() {
        PipelineRun run = createTestPipelineRun();

        run.setStatus(TaskStatus.SUCCESS);
        run.setEndDate(new Date());
        run.setTerminating(true);

        pipelineRunDao.updateRunStatus(run);

        PipelineRun loadedRun = pipelineRunDao.loadPipelineRun(run.getId());
        assertEquals(run.getEndDate(), loadedRun.getEndDate());
        assertEquals(run.getStatus(), loadedRun.getStatus());
        assertEquals(run.isTerminating(), loadedRun.isTerminating());
    }

    @Test
    public void updateRunCommitStatus() {
        PipelineRun run = createTestPipelineRun();

        run.setCommitStatus(CommitStatus.SUCCESS);

        pipelineRunDao.updateRunCommitStatus(run);

        PipelineRun loadedRun = pipelineRunDao.loadPipelineRun(run.getId());
        assertEquals(run.getCommitStatus(), loadedRun.getCommitStatus());
    }

    @Test
    public void pipelineRunShouldContainsCmdTemplateAndActualCmd() {
        PipelineRun run = new PipelineRun();

        run.setPipelineId(testPipeline.getId());
        run.setVersion("abcdefg");
        run.setStartDate(new Date());
        run.setEndDate(run.getStartDate());
        run.setStatus(TaskStatus.RUNNING);
        run.setCommitStatus(CommitStatus.NOT_COMMITTED);
        run.setLastChangeCommitTime(new Date());
        run.setPodId(TEST_POD_ID);
        run.setParams(TEST_PARAMS);
        run.setOwner(USER);
        setRunInstance(run);

        String cmdTemplate = "cmdTemplate";
        String actualCmd = "ActualCmd";
        run.setCmdTemplate(cmdTemplate);
        run.setActualCmd(actualCmd);
        run.setDockerImage("dockerImage");

        pipelineRunDao.createPipelineRun(run);

        PipelineRun loadedRun = pipelineRunDao.loadPipelineRun(run.getId());
        assertEquals(loadedRun.getCmdTemplate(), cmdTemplate);
        assertEquals(loadedRun.getActualCmd(), actualCmd);

    }

    @Test
    public void loadRunningPipelineRuns() {
        PipelineRun run = createTestPipelineRun();

        List<PipelineRun> running = pipelineRunDao.loadRunningAndTerminatedPipelineRuns();
        assertFalse(running.isEmpty());
        assertTrue(running.stream().anyMatch(r -> Objects.equals(r.getId(), run.getId())));

        run.setStatus(TaskStatus.FAILURE);
        run.setTerminating(true);
        pipelineRunDao.updateRunStatus(run);

        List<PipelineRun> terminating = pipelineRunDao.loadRunningAndTerminatedPipelineRuns();
        assertFalse(terminating.isEmpty());
        assertFalse(terminating.stream().anyMatch(r -> r.getStatus() == TaskStatus.RUNNING));
        assertTrue(terminating.stream().anyMatch(r -> Objects.equals(r.getId(), run.getId())));
    }


    @Test
    public void searchRunsByParentId() {
        Pipeline testPipeline = getPipeline();

        createRunWithParams(testPipeline.getId(), String.format("aparent-id=%d", TEST_PARENT_1));
        createRunWithParams(testPipeline.getId(), String.format("parent-id=%d", TEST_PARENT_1));
        createRunWithParams(testPipeline.getId(), String.format("key=var|parent-id=%d", TEST_PARENT_1));
        createRunWithParams(testPipeline.getId(), String.format("parent-id=%d|key=var", TEST_PARENT_1));
        createRunWithParams(testPipeline.getId(), String.format("parent-id=%d=string|key=var", TEST_PARENT_1));
        createRunWithParams(testPipeline.getId(), String.format("key1=var1|parent-id=%d|key=var", TEST_PARENT_1));

        createRunWithParams(testPipeline.getId(), String.format("aparent-id=%d", TEST_PARENT_1));
        createRunWithParams(testPipeline.getId(), String.format("parent-id=%d", TEST_PARENT_2));
        createRunWithParams(testPipeline.getId(), String.format("key=var|parent-id=%d", TEST_PARENT_2));
        createRunWithParams(testPipeline.getId(), String.format("parent-id=%d|key=var", TEST_PARENT_2));
        createRunWithParams(testPipeline.getId(), String.format("key1=var1|parent-id=%d|key=var", TEST_PARENT_2));
        createRunWithParams(testPipeline.getId(), String.format("key1=var1|parent-id=%d=input|key=var", TEST_PARENT_2));

        checkOnlyOneParentPresent(TEST_PARENT_1);
        checkOnlyOneParentPresent(TEST_PARENT_2);
    }

    @Test
    public void searchGroupingRun() {
        Pipeline testPipeline = getPipeline();
        PipelineRun parent = createRun(testPipeline.getId(), null, TaskStatus.SUCCESS, null);
        PipelineRun child = createRun(testPipeline.getId(), null, TaskStatus.SUCCESS, parent.getId());
        PipelineRun lonely = createRun(testPipeline.getId(), null, TaskStatus.SUCCESS, null);
        PagingRunFilterVO filterVO = new PagingRunFilterVO();
        filterVO.setPage(1);
        filterVO.setPageSize(TEST_PAGE_SIZE);
        filterVO.setStatuses(Collections.singletonList(TaskStatus.SUCCESS));

        List<PipelineRun> runs = pipelineRunDao.searchPipelineGroups(filterVO, null);
        assertEquals(2, runs.size());
        assertEquals(lonely.getId(), runs.get(0).getId());
        assertEquals(parent.getId(), runs.get(1).getId());
        assertEquals(1, runs.get(1).getChildRuns().size());
        assertEquals(child.getId(), runs.get(1).getChildRuns().get(0).getId());

        assertEquals(2L, pipelineRunDao.countRootRuns(filterVO, null).longValue());

    }

    @Test
    public void searchPipelineRuns() {
        Pipeline testPipeline2 = getPipeline();

        LocalDate now = LocalDate.now();
        LocalDate date = LocalDate.of(now.getYear(), now.getMonth(), 1);
        PipelineRun stopped = new PipelineRun();
        stopped.setPipelineId(testPipeline.getId());
        stopped.setVersion(TEST_REVISION_1);
        stopped.setStartDate(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        stopped.setEndDate(stopped.getStartDate());
        stopped.setStatus(TaskStatus.STOPPED);
        stopped.setCommitStatus(CommitStatus.NOT_COMMITTED);
        stopped.setLastChangeCommitTime(new Date());
        stopped.setPodId(TEST_POD_ID);
        stopped.setParams(TEST_PARAMS);
        stopped.setOwner(USER);
        stopped.setConfigurationId(CONFIGURATION_ID_1);
        stopped.setEntitiesIds(Stream.of(ENTITY_ID_1, ENTITY_ID_2).collect(Collectors.toList()));
        setRunInstance(stopped);
        pipelineRunDao.createPipelineRun(stopped);

        LocalDate date2 = LocalDate.of(now.getYear(), now.getMonth(), 2);
        PipelineRun failed = new PipelineRun();
        failed.setPipelineId(testPipeline.getId());
        failed.setVersion(TEST_REVISION_3);
        failed.setStartDate(Date.from(date2.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        failed.setEndDate(failed.getStartDate());
        failed.setStatus(TaskStatus.FAILURE);
        failed.setCommitStatus(CommitStatus.NOT_COMMITTED);
        failed.setLastChangeCommitTime(new Date());
        failed.setPodId(TEST_POD_ID);
        failed.setParams(TEST_PARAMS);
        failed.setOwner(USER);
        failed.setConfigurationId(CONFIGURATION_ID_2);
        failed.setEntitiesIds(Stream.of(ENTITY_ID_2, ENTITY_ID_3).collect(Collectors.toList()));
        setRunInstance(failed);
        pipelineRunDao.createPipelineRun(failed);

        LocalDate date3 = LocalDate.of(now.getYear(), now.getMonth(), 3);
        PipelineRun running = new PipelineRun();
        running.setPipelineId(testPipeline2.getId());
        running.setVersion(TEST_REVISION_2);
        running.setStartDate(Date.from(date3.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        running.setEndDate(running.getStartDate());
        running.setStatus(TaskStatus.RUNNING);
        running.setCommitStatus(CommitStatus.NOT_COMMITTED);
        running.setLastChangeCommitTime(new Date());
        running.setPodId(TEST_POD_ID);
        running.setParams(TEST_PARAMS);
        running.setOwner(USER);
        running.setConfigurationId(CONFIGURATION_ID_3);
        running.setEntitiesIds(Stream.of(ENTITY_ID_1, ENTITY_ID_3).collect(Collectors.toList()));
        setRunInstance(running);
        pipelineRunDao.createPipelineRun(running);

        PagingRunFilterVO filterVO = new PagingRunFilterVO();
        filterVO.setPage(1);
        filterVO.setPageSize(TEST_PAGE_SIZE);
        List<PipelineRun> runs = pipelineRunDao.searchPipelineRuns(filterVO);
        assertFalse(runs.isEmpty());
        assertEquals(3, runs.size());

        filterVO.setPipelineIds(Collections.singletonList(testPipeline.getId()));
        runs = pipelineRunDao.searchPipelineRuns(filterVO);
        assertEquals(2, runs.size());

        assertTrue(runs.stream().allMatch(r -> r.getVersion().equals(TEST_REVISION_1) || r.getVersion()
                .equals(TEST_REVISION_3)));

        filterVO.setStatuses(Arrays.asList(TaskStatus.FAILURE, TaskStatus.STOPPED));
        runs = pipelineRunDao.searchPipelineRuns(filterVO);
        assertEquals(2, runs.size());
        filterVO.setStatuses(null);

        filterVO.setVersions(Collections.singletonList(TEST_REVISION_3));
        runs = pipelineRunDao.searchPipelineRuns(filterVO);
        assertEquals(1, runs.size());
        assertTrue(runs.stream().allMatch(r -> r.getVersion().equals(TEST_REVISION_3)));

        filterVO.setStatuses(Collections.singletonList(TaskStatus.RUNNING));
        runs = pipelineRunDao.searchPipelineRuns(filterVO);
        assertTrue(runs.isEmpty());

        filterVO.setVersions(null);
        filterVO.setPipelineIds(null);
        filterVO.setStatuses(Collections.singletonList(TaskStatus.FAILURE));
        runs = pipelineRunDao.searchPipelineRuns(filterVO);
        assertEquals(1, runs.size());
        assertTrue(runs.stream().allMatch(r -> r.getStatus() == TaskStatus.FAILURE));

        filterVO.setStatuses(null);
        filterVO.setStartDateFrom(Date.from(date2.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        runs = pipelineRunDao.searchPipelineRuns(filterVO);
        assertEquals(2, runs.size());
        assertTrue(runs.stream().allMatch(r -> r.getStartDate().getTime() >=
                date2.atStartOfDay(ZoneId.systemDefault()).toInstant().getEpochSecond()));

        filterVO.setEndDateTo(Date.from(LocalDateTime.of(now.getYear(), now.getMonth(), 2, HOURS_23, MINUTES_59)
                .atZone(ZoneId.systemDefault()).toInstant()));
        runs = pipelineRunDao.searchPipelineRuns(filterVO);
        assertEquals(1, runs.size());

        // test filter by configuration ids
        filterVO.setStartDateFrom(null);
        filterVO.setEndDateTo(null);
        filterVO.setConfigurationIds(Stream.of(CONFIGURATION_ID_1, CONFIGURATION_ID_2).collect(Collectors.toList()));
        runs = pipelineRunDao.searchPipelineRuns(filterVO);
        assertEquals(2, runs.size());

        // test filter by entities ids
        filterVO.setConfigurationIds(null);
        filterVO.setEntitiesIds(Stream.of(ENTITY_ID_1, ENTITY_ID_2).collect(Collectors.toList()));
        runs = pipelineRunDao.searchPipelineRuns(filterVO);
        assertEquals(3, runs.size());

        filterVO.setEntitiesIds(Stream.of(ENTITY_ID_1).collect(Collectors.toList()));
        runs = pipelineRunDao.searchPipelineRuns(filterVO);
        assertEquals(2, runs.size());
    }

    private Pipeline getPipeline() {
        Pipeline testPipeline2 = new Pipeline();
        testPipeline2.setName("Test");
        testPipeline2.setRepository(TEST_REPO);
        testPipeline2.setRepositorySsh(TEST_REPO_SSH);
        testPipeline2.setOwner(TEST_USER);
        pipelineDao.createPipeline(testPipeline2);
        return testPipeline2;
    }

    @Test
    public void testPaging() {
        createTestPipelineRun();

        int count = pipelineRunDao.countFilteredPipelineRuns(new PipelineRunFilterVO(), null);
        assertTrue(count > 0);
    }

    @Test
    public void runPipelineWithEntitiesIds() {
        Long entitiesId = 1L;

        PipelineRun run = createRunWithEntitiesIds(testPipeline.getId(), TaskStatus.SUCCESS, null, entitiesId);
        PipelineRun loadedRun = pipelineRunDao.loadPipelineRun(run.getId());
        assertEquals(Collections.singletonList(entitiesId), loadedRun.getEntitiesIds());
    }

    @Test
    public void spotOnDemandSelectionTest() {
        createRunWithSpotFlag(null);
        createRunWithSpotFlag(true);
        createRunWithSpotFlag(false);
        List<PipelineRun> runs = pipelineRunDao.loadAllRunsForPipeline(testPipeline.getId());
        assertNull(getSpotFlag(runs.get(0)));
        assertEquals(true, getSpotFlag(runs.get(1)));
        assertEquals(false, getSpotFlag(runs.get(2)));
    }

    @Test
    public void runPipelineWithConfiguration() {
        Long configurationId = 1L;

        PipelineRun run = createRunWithConfigurationId(testPipeline.getId(), configurationId);
        PipelineRun loadedRun = pipelineRunDao.loadPipelineRun(run.getId());
        assertEquals(configurationId, loadedRun.getConfigurationId());
    }

    @Test
    public void updatePodStatus() {
        PipelineRun run = createRun(testPipeline.getId(), null, TaskStatus.SUCCESS, null);
        run.setPodStatus(POD_STATUS);
        pipelineRunDao.updatePodStatus(run);
        PipelineRun loadedRun = pipelineRunDao.loadPipelineRun(run.getId());
        assertEquals(POD_STATUS, loadedRun.getPodStatus());
    }

    @Test
    public void updateProlongedAtTimeTest() {
        PipelineRun run = createTestPipelineRun();
        LocalDateTime time = DateUtils.nowUTC();

        PipelineRun loadedRun = pipelineRunDao.loadPipelineRun(run.getId());
        assertNull(loadedRun.getProlongedAtTime());

        run.setProlongedAtTime(time);
        pipelineRunDao.updateProlongIdleRunAndLastIdleNotificationTime(run);

        loadedRun = pipelineRunDao.loadPipelineRun(run.getId());
        assertEquals(time, loadedRun.getProlongedAtTime());
    }

    @Test
    public void runPipelineWithRunSids() {
        List<RunSid> runSids = new ArrayList<>();
        RunSid runSid1 = new RunSid();
        runSid1.setName(TEST_USER);
        runSid1.setIsPrincipal(true);
        runSids.add(runSid1);
        runSid1.setAccessType(RunAccessType.ENDPOINT);

        RunSid runSid2 = new RunSid();
        runSid2.setName(GROUP_NAME);
        runSid2.setIsPrincipal(false);
        runSid2.setAccessType(RunAccessType.ENDPOINT);
        runSids.add(runSid2);

        Pipeline testPipeline = getPipeline();
        PipelineRun run = createRunWithRunSids(testPipeline.getId(), null, runSids);
        PipelineRun loadedRun = pipelineRunDao.loadPipelineRun(run.getId());
        assertEquals(runSids.size(), loadedRun.getRunSids().size());
    }

    @Test
    public void testUpdateWithRunSids() {
        List<RunSid> runSids = new ArrayList<>();
        RunSid runSid1 = new RunSid();
        runSid1.setName(TEST_USER);
        runSid1.setIsPrincipal(true);
        runSid1.setAccessType(RunAccessType.ENDPOINT);
        runSids.add(runSid1);

        RunSid runSid2 = new RunSid();
        runSid2.setName(GROUP_NAME);
        runSid2.setIsPrincipal(false);
        runSid2.setAccessType(RunAccessType.ENDPOINT);

        Pipeline testPipeline = getPipeline();
        PipelineRun run = createRunWithRunSids(testPipeline.getId(), null, runSids);
        pipelineRunDao.deleteRunSids(run.getId());
        PipelineRun loadedRuns = pipelineRunDao.loadPipelineRun(run.getId());
        assertTrue(loadedRuns.getRunSids().isEmpty());

        runSids.add(runSid2);
        pipelineRunDao.createRunSids(run.getId(), runSids);
        loadedRuns = pipelineRunDao.loadPipelineRun(run.getId());
        assertEquals(2, loadedRuns.getRunSids().size());
    }

    @Test
    public void loadPipelineWithEnvVars() {
        PipelineRun run = createTestPipelineRun();
        PipelineRun loadedRun = pipelineRunDao.loadPipelineRun(run.getId());

        assertEquals(run.getId(), loadedRun.getId());
        assertEquals(run.getEnvVars(), loadedRun.getEnvVars());
    }

    @Test
    public void testLoadRunWithoutPipeline() {
        PipelineRun run = createTestPipelineRun(null);
        PipelineRun loadedRun = pipelineRunDao.loadPipelineRun(run.getId());

        assertNull(loadedRun.getPipelineId());
    }

    @Test
    public void testUpdateWithPrettyUrlRun() {
        PipelineRun run = createTestPipelineRun(testPipeline.getId());
        run.setPrettyUrl(PRETTY_URL);
        pipelineRunDao.updateRun(run);
        PipelineRun loaded = pipelineRunDao.loadPipelineRun(run.getId());
        assertEquals(run.getId(), loaded.getId());
        assertEquals(PRETTY_URL, loaded.getPrettyUrl());

        Optional<PipelineRun> runByPrettyUrl = pipelineRunDao.loadRunByPrettyUrl(PRETTY_URL);
        assertTrue(runByPrettyUrl.isPresent());
        assertEquals(run.getId(), runByPrettyUrl.get().getId());
    }

    @Test
    public void testLoadActiveServicesByOwner() {
        PipelineRun run = createTestPipelineRun();

        PagingRunFilterVO filterVO = new PagingRunFilterVO();
        filterVO.setPage(1);
        filterVO.setPageSize(TEST_PAGE_SIZE);

        PipelineUser user = new PipelineUser();
        user.setUserName(USER);
        List<PipelineRun> runs = pipelineRunDao.loadActiveServices(filterVO, user);
        assertEquals(1, runs.size());
        assertEquals(run.getId(), runs.get(0).getId());
        assertEquals(1, runs.size());
    }

    @Test
    public void testLoadActiveServicesByUserIsPrincipal() {
        List<RunSid> runSids = new ArrayList<>();

        RunSid runSid1 = new RunSid();
        runSid1.setName(TEST_USER);
        runSid1.setIsPrincipal(true);
        runSids.add(runSid1);

        Pipeline testPipeline = getPipeline();
        PipelineRun run = createRunWithRunSids(testPipeline.getId(), null, runSids);

        PagingRunFilterVO filterVO = new PagingRunFilterVO();
        filterVO.setPage(1);
        filterVO.setPageSize(TEST_PAGE_SIZE);

        PipelineUser user = new PipelineUser();
        user.setUserName(TEST_USER);
        List<PipelineRun> runs = pipelineRunDao.loadActiveServices(filterVO, user);
        assertEquals(1, runs.size());
        assertEquals(run.getId(), runs.get(0).getId());
        assertEquals(1, runs.size());

        int servicesCount = pipelineRunDao.countActiveServices(user);
        assertEquals(runs.size(), servicesCount);
    }

    @Test
    public void testLoadActiveServicesByUserInGroup() {
        List<RunSid> runSids = new ArrayList<>();

        RunSid runSid = new RunSid();
        runSid.setName(GROUP_NAME);
        runSid.setIsPrincipal(false);
        runSids.add(runSid);

        Pipeline testPipeline = getPipeline();
        PipelineRun run = createRunWithRunSids(testPipeline.getId(), null, runSids);

        PagingRunFilterVO filterVO = new PagingRunFilterVO();
        filterVO.setPage(1);
        filterVO.setPageSize(TEST_PAGE_SIZE);

        PipelineUser user = new PipelineUser();
        user.setUserName(TEST_USER);
        user.setGroups(Collections.singletonList(GROUP_NAME));
        List<PipelineRun> runs = pipelineRunDao.loadActiveServices(filterVO, user);
        assertEquals(1, runs.size());
        assertEquals(run.getId(), runs.get(0).getId());
        assertEquals(1, runs.size());

    }

    @Test
    public void testUpdateRunsLastNotification() {
        PipelineRun run1 = createTestPipelineRun();

        Date lastNotificationDate = DateUtils.now();
        LocalDateTime lastIdleNotificationDate = DateUtils.nowUTC();
        run1.setLastNotificationTime(lastNotificationDate);
        run1.setLastIdleNotificationTime(lastIdleNotificationDate);

        pipelineRunDao.updateRunLastNotification(run1);
        PipelineRun loadedRun = pipelineRunDao.loadPipelineRun(run1.getId());

        assertEquals(loadedRun.getLastNotificationTime(), lastNotificationDate);
        assertEquals(loadedRun.getLastIdleNotificationTime(), lastIdleNotificationDate);

        PipelineRun run2 = createTestPipelineRun();
        PipelineRun run3 = createTestPipelineRun();
        Stream.of(run2, run3).forEach(r -> {
            r.setLastNotificationTime(lastNotificationDate);
            r.setLastIdleNotificationTime(lastIdleNotificationDate);
        });

        pipelineRunDao.updateRunsLastNotification(Arrays.asList(run2, run3));
        Stream.of(run2, run3).forEach(r -> {
            PipelineRun loaded = pipelineRunDao.loadPipelineRun(r.getId());
            assertEquals(loaded.getLastNotificationTime(), lastNotificationDate);
            assertEquals(loaded.getLastIdleNotificationTime(), lastIdleNotificationDate);
        });

        List<PipelineRun> running = pipelineRunDao.loadRunningPipelineRuns();
        assertFalse(running.isEmpty());
        running.forEach(loaded -> {
            assertEquals(loaded.getLastNotificationTime(), lastNotificationDate);
            assertEquals(loaded.getLastIdleNotificationTime(), lastIdleNotificationDate);
        });
    }

    @Test
    public void testLoadRunWithPricePerHour() {
        PipelineRun run = createTestPipelineRun(testPipeline.getId());
        run.setPricePerHour(PRICE_PER_HOUR);
        pipelineRunDao.updateRun(run);
        PipelineRun loaded = pipelineRunDao.loadPipelineRun(run.getId());
        assertEquals(run.getId(), loaded.getId());
        assertEquals(PRICE_PER_HOUR, loaded.getPricePerHour());
    }

    private PipelineRun createTestPipelineRun() {
        return createTestPipelineRun(testPipeline.getId());
    }

    private PipelineRun createTestPipelineRun(Long pipelineId) {
        PipelineRun run = new PipelineRun();
        run.setPipelineId(pipelineId);
        run.setVersion("abcdefg");
        run.setStartDate(new Date());
        run.setEndDate(run.getStartDate());
        run.setStatus(TaskStatus.RUNNING);
        run.setCommitStatus(CommitStatus.NOT_COMMITTED);
        run.setLastChangeCommitTime(new Date());
        run.setPodId(TEST_POD_ID);
        run.setParams(TEST_PARAMS);
        run.setOwner(USER);
        run.setServiceUrl(TEST_SERVICE_URL);

        Map<SystemParams, String> systemParams = EnvVarsBuilderTest.matchSystemParams();
        PipelineConfiguration configuration = EnvVarsBuilderTest.matchPipeConfig();
        EnvVarsBuilder.buildEnvVars(run, configuration, systemParams, null);
        run.setEnvVars(run.getEnvVars());
        setRunInstance(run);
        pipelineRunDao.createPipelineRun(run);
        return run;
    }

    private void setRunInstance(final PipelineRun run) {
        RunInstance runInstance = new RunInstance();
        runInstance.setCloudProvider(CloudProvider.AWS);
        runInstance.setCloudRegionId(cloudRegion.getId());
        run.setInstance(runInstance);
    }

    private Boolean getSpotFlag(PipelineRun pipelineRun) {
        pipelineRunDao.loadAllRunsForPipeline(testPipeline.getId());
        return pipelineRun.getInstance().getSpot();
    }

    private void checkOnlyOneParentPresent(Long parentId) {
        PagingRunFilterVO filterVO = new PagingRunFilterVO();
        filterVO.setPage(1);
        filterVO.setPageSize(TEST_PAGE_SIZE);
        filterVO.setParentId(parentId);

        List<PipelineRun> runs = pipelineRunDao.searchPipelineRuns(filterVO);
        assertEquals(5, runs.size());
        assertTrue(runs.stream().allMatch(run ->
                run.getPipelineRunParameters()
                        .stream()
                        .allMatch(param ->
                                !param.getName().equals("parent-id") ||
                                        param.getValue().equals(String.valueOf(parentId)))));
    }

    private PipelineRun createRunWithParams(Long pipelineId, String params) {
        return createRun(pipelineId, params, TaskStatus.STOPPED, null);
    }

    private PipelineRun createRun(Long pipelineId, String params, TaskStatus status, Long parentRunId) {
        return createPipelineRun(pipelineId, params, status,
                parentRunId, null, null, null, null);
    }

    private PipelineRun createRunWithEntitiesIds(Long pipelineId, TaskStatus status, Long parentRunId,
                                                 Long entitiesId) {
        return createPipelineRun(pipelineId, null, status,
                parentRunId, entitiesId, null, null, null);
    }

    private PipelineRun createRunWithSpotFlag(Boolean isSpot) {
        return createPipelineRun(testPipeline.getId(), null, TaskStatus.STOPPED,
                null, null, isSpot, null, null);
    }

    private PipelineRun createRunWithConfigurationId(Long pipelineId, Long configurationId) {
        return createPipelineRun(pipelineId, null, TaskStatus.SUCCESS,
                null, null, null, configurationId, null);
    }

    private PipelineRun createRunWithRunSids(Long pipelineId, String params, List<RunSid> runSids) {
        return createPipelineRun(pipelineId, params, TaskStatus.RUNNING,
                null, null, null, null, runSids);
    }

    private PipelineRun createPipelineRun(Long pipelineId, String params, TaskStatus status, Long parentRunId,
                                          Long entitiesId,  Boolean isSpot, Long configurationId,
                                          List<RunSid> runSids) {
        PipelineRun run = new PipelineRun();
        run.setPipelineId(pipelineId);
        run.setVersion(TEST_REVISION_1);
        run.setStartDate(new Date());
        run.setEndDate(new Date());
        run.setStatus(status);
        run.setCommitStatus(CommitStatus.NOT_COMMITTED);
        run.setLastChangeCommitTime(new Date());
        run.setPodId(TEST_POD_ID);
        run.setParams(params);
        run.setOwner(USER);
        run.setParentRunId(parentRunId);
        run.setRunSids(runSids);
        run.setServiceUrl(TEST_SERVICE_URL);

        RunInstance instance = new RunInstance();
        instance.setCloudRegionId(cloudRegion.getId());
        instance.setCloudProvider(CloudProvider.AWS);
        instance.setSpot(isSpot);
        instance.setNodeId("1");
        run.setInstance(instance);
        run.setEntitiesIds(Collections.singletonList(entitiesId));
        run.setConfigurationId(configurationId);
        pipelineRunDao.createPipelineRun(run);
        return run;
    }

}
