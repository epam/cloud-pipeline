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

package com.epam.pipeline.dao.pipeline;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.controller.vo.PagingRunFilterVO;
import com.epam.pipeline.controller.vo.PipelineRunFilterVO;
import com.epam.pipeline.dao.filter.FilterDao;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.CommitStatus;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
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
import com.epam.pipeline.manager.filter.FilterExpression;
import com.epam.pipeline.manager.filter.FilterExpressionType;
import com.epam.pipeline.manager.filter.FilterOperandType;
import com.epam.pipeline.manager.filter.WrongFilterException;
import com.epam.pipeline.util.TestUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@Transactional
public class PipelineRunDaoTest extends AbstractSpringTest {
    private static final String TEST_USER = "TEST";
    private static final String TEST_PARAMS = "123 321";
    private static final String TEST_POD_ID = "pod1";
    private static final String TEST_NODE_IMAGE = "nodeImage";
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
    private static final BigDecimal COMPUTE_PRICE_PER_HOUR = new BigDecimal("0.04000");
    private static final BigDecimal DISK_PRICE_PER_HOUR = new BigDecimal("0.00012");
    private static final String TEST_REPO = "///";
    private static final String TEST_REPO_SSH = "git@test";

    private static final String TAG_KEY_1 = "key1";
    private static final String TAG_KEY_2 = "key2";
    private static final String TAG_VALUE_1 = "value1";
    private static final String TAG_VALUE_2 = "value2";
    private static final ZoneId ZONE_ID = ZoneId.systemDefault();
    private static final String DOCKER_IMAGE = "dockerImage";
    private static final String ACTUAL_DOCKER_IMAGE = "actualDockerImage";

    @Autowired
    private PipelineRunDao pipelineRunDao;

    @Autowired
    private RunStatusDao runStatusDao;

    @Autowired
    private FilterDao filterDao;

    @Autowired
    private RunLogDao logDao;

    @Autowired
    private PipelineDao pipelineDao;

    @Autowired
    private CloudRegionDao regionDao;

    @Value("${run.pipeline.init.task.name?:InitializeEnvironment}")
    private String initTaskName;

    @Value("${run.pipeline.nodeup.task.name?:InitializeNode}")
    private String nodeUpTaskName;

    private Pipeline testPipeline;
    private AbstractCloudRegion cloudRegion;
    private static final LocalDateTime SYNC_PERIOD_START = LocalDateTime.of(2019, 4, 2, 0, 0);
    private static final LocalDateTime SYNC_PERIOD_END = LocalDateTime.of(2019, 4, 3, 0, 0);

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
    public void testFilterPipelineRuns() throws WrongFilterException {
        PipelineRun run1 =  TestUtils.createPipelineRun(testPipeline.getId(), null, TaskStatus.RUNNING, USER,
                null, null, true, null, null, "pod-id", cloudRegion.getId());
        PipelineRun run2 =  TestUtils.createPipelineRun(testPipeline.getId(), null, TaskStatus.RUNNING, USER,
                null, null, true, null, null, "pod-id2", cloudRegion.getId());
        pipelineRunDao.createPipelineRun(run1);
        pipelineRunDao.createPipelineRun(run2);
        FilterExpression logicalExpression = new FilterExpression();
        logicalExpression.setFilterExpressionType(FilterExpressionType.LOGICAL);
        logicalExpression.setField("pod.id");
        logicalExpression.setOperand(FilterOperandType.EQUALS.getOperand());
        logicalExpression.setValue(run1.getPodId());
        List<PipelineRun> pipelineRuns = filterDao.filterPipelineRuns(
                FilterExpression.prepare(logicalExpression), 1, 2, 0);
        assertEquals(1, pipelineRuns.size());
        assertEquals(pipelineRuns.get(0).getPodId(), run1.getPodId());
    }

    @Test
    public void testLoadPipelineRunsActiveInPeriod() {
        final LocalDateTime beforeSyncStart = SYNC_PERIOD_START.minusHours(12);
        final LocalDateTime afterSyncStart = SYNC_PERIOD_START.plusHours(12);

        createRunWithStartEndDates(beforeSyncStart, beforeSyncStart.plusHours(6));
        createRunWithStartEndDates(beforeSyncStart, afterSyncStart);
        createRunWithStartEndDates(afterSyncStart, afterSyncStart.plusHours(6));
        createRunWithStartEndDates(beforeSyncStart, null);
        createRunWithStartEndDates(afterSyncStart, null);

        pipelineRunDao.loadAllRunsForPipeline(testPipeline.getId())
            .forEach(run -> {
                runStatusDao.saveStatus(new RunStatus(run.getId(), TaskStatus.RUNNING, null,
                                                      LocalDateTime.ofInstant(run.getStartDate().toInstant(),
                                                                              ZoneId.systemDefault())));
                if (run.getEndDate() != null) {
                    runStatusDao.saveStatus(new RunStatus(run.getId(), TaskStatus.STOPPED, null,
                                                          LocalDateTime.ofInstant(run.getEndDate().toInstant(),
                                                                                  ZoneId.systemDefault())));
                }
            });
        final List<PipelineRun> pipelineRuns = pipelineRunDao.loadPipelineRunsActiveInPeriod(SYNC_PERIOD_START,
                                                                                             SYNC_PERIOD_END);
        assertEquals(4, pipelineRuns.size());
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
    public void testUpdateRunTags() {
        final PipelineRun run = createTestPipelineRun();
        final Map<String, String> tags = new HashMap<>();
        loadTagsAndCompareWithExpected(run.getId(), tags);
        tags.put(TAG_KEY_1, TAG_VALUE_1);
        updateTagsAndVerifySaveIsCorrect(run, tags);
        tags.put(TAG_KEY_2, TAG_VALUE_2);
        updateTagsAndVerifySaveIsCorrect(run, tags);
        tags.remove(TAG_KEY_1);
        updateTagsAndVerifySaveIsCorrect(run, tags);
        tags.remove(TAG_KEY_2);
        updateTagsAndVerifySaveIsCorrect(run, tags);
        run.setTags(null);
        loadTagsAndCompareWithExpected(run.getId(), Collections.emptyMap());
    }

    @Test
    public void updateTagsForRuns() {
        final PipelineRun run1 = createTestPipelineRun();
        final Map<String, String> tags1 = Collections.singletonMap(TAG_KEY_1, TAG_VALUE_1);
        run1.setTags(tags1);
        final PipelineRun run2 = createTestPipelineRun();
        final Map<String, String> tags2 = Collections.singletonMap(TAG_KEY_2, TAG_VALUE_2);
        run2.setTags(tags2);

        pipelineRunDao.updateRunsTags(Arrays.asList(run1, run2));
        loadTagsAndCompareWithExpected(run1.getId(), tags1);
        loadTagsAndCompareWithExpected(run2.getId(), tags2);
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

        pipelineRunDao.createPipelineRun(run);

        PipelineRun loadedRun = pipelineRunDao.loadPipelineRun(run.getId());
        assertEquals(loadedRun.getCmdTemplate(), cmdTemplate);
        assertEquals(loadedRun.getActualCmd(), actualCmd);

    }

    @Test
    @Transactional
    public void pipelineRunShouldHaveDockerImageAndActualDockerImage() {
        PipelineRun run = buildPipelineRun(testPipeline.getId(), TEST_SERVICE_URL);
        run.setDockerImage(DOCKER_IMAGE);
        run.setActualDockerImage(ACTUAL_DOCKER_IMAGE);

        pipelineRunDao.createPipelineRun(run);
        
        PipelineRun loadedRun = pipelineRunDao.loadPipelineRun(run.getId());
        assertEquals(loadedRun.getDockerImage(), DOCKER_IMAGE);
        assertEquals(loadedRun.getActualDockerImage(), ACTUAL_DOCKER_IMAGE);
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
        parent.setTags(Collections.singletonMap(TAG_KEY_1, TAG_VALUE_1));
        pipelineRunDao.updateRunTags(parent);
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

        assertThat(runs.get(1).getTags(), CoreMatchers.is(parent.getTags()));
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
        stopped.setStartDate(Date.from(date.atStartOfDay(ZONE_ID).toInstant()));
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
    public void testLoadActiveSharedRunsByOwner() {
        PipelineRun run = createTestPipelineRun();

        PagingRunFilterVO filterVO = new PagingRunFilterVO();
        filterVO.setPage(1);
        filterVO.setPageSize(TEST_PAGE_SIZE);

        PipelineUser user = new PipelineUser();
        user.setUserName(USER);
        List<PipelineRun> runs = pipelineRunDao.loadActiveSharedRuns(filterVO, user);
        assertEquals(1, runs.size());
        assertEquals(run.getId(), runs.get(0).getId());
        assertEquals(1, runs.size());
    }

    @Test
    public void loadSharedRunsShouldNotReturnRunsWithoutServiceUrlForOwner() {
        createTestPipelineRun(testPipeline.getId(), null);
        PagingRunFilterVO filterVO = new PagingRunFilterVO();
        filterVO.setPage(1);
        filterVO.setPageSize(TEST_PAGE_SIZE);

        PipelineUser user = new PipelineUser();
        user.setUserName(USER);
        List<PipelineRun> runs = pipelineRunDao.loadActiveSharedRuns(filterVO, user);
        assertEquals(0, runs.size());
    }

    @Test
    public void testLoadActiveSharedRunsByUserIsPrincipal() {
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
        List<PipelineRun> runs = pipelineRunDao.loadActiveSharedRuns(filterVO, user);
        assertEquals(1, runs.size());
        assertEquals(run.getId(), runs.get(0).getId());
        assertEquals(1, runs.size());

        int servicesCount = pipelineRunDao.countActiveSharedRuns(user);
        assertEquals(runs.size(), servicesCount);
    }

    @Test
    public void testLoadActiveSharedRunsByUserInGroup() {
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
        List<PipelineRun> runs = pipelineRunDao.loadActiveSharedRuns(filterVO, user);
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
        run.setComputePricePerHour(COMPUTE_PRICE_PER_HOUR);
        run.setDiskPricePerHour(DISK_PRICE_PER_HOUR);
        pipelineRunDao.updateRun(run);
        PipelineRun loaded = pipelineRunDao.loadPipelineRun(run.getId());
        assertEquals(run.getId(), loaded.getId());
        assertEquals(PRICE_PER_HOUR, loaded.getPricePerHour());
        assertEquals(COMPUTE_PRICE_PER_HOUR, loaded.getComputePricePerHour());
        assertEquals(DISK_PRICE_PER_HOUR, loaded.getDiskPricePerHour());
    }

    @Test
    public void loadRunShouldReturnInitializedStatus() {
        final PipelineRun run = createTestPipelineRun();
        validateLoadRunBooleanFieldValue(false, run, PipelineRun::getInitialized);

        createLog(run, TaskStatus.RUNNING, initTaskName);
        validateLoadRunBooleanFieldValue(false, run, PipelineRun::getInitialized);

        createLog(run, TaskStatus.SUCCESS, initTaskName);
        validateLoadRunBooleanFieldValue(true, run, PipelineRun::getInitialized);
    }

    @Test
    public void loadRunShouldReturnQueuedStatus() {
        final PipelineRun run = createTestPipelineRun();
        validateLoadRunBooleanFieldValue(true, run, PipelineRun::getQueued);

        createLog(run, TaskStatus.RUNNING, initTaskName);
        validateLoadRunBooleanFieldValue(true, run, PipelineRun::getQueued);

        createLog(run, TaskStatus.RUNNING, nodeUpTaskName);
        validateLoadRunBooleanFieldValue(false, run, PipelineRun::getQueued);
    }

    @Test
    public void shouldLoadRunsByStatuses() {
        final PipelineRun running = buildPipelineRun(null, null);
        running.setStatus(TaskStatus.RUNNING);
        pipelineRunDao.createPipelineRun(running);

        final PipelineRun pausing = buildPipelineRun(null, null);
        pausing.setStatus(TaskStatus.PAUSING);
        pipelineRunDao.createPipelineRun(pausing);

        final PipelineRun resuming = buildPipelineRun(null, null);
        resuming.setStatus(TaskStatus.RESUMING);
        pipelineRunDao.createPipelineRun(resuming);

        final List<PipelineRun> runs = pipelineRunDao.loadRunsByStatuses(
                Arrays.asList(TaskStatus.PAUSING, TaskStatus.RESUMING));
        assertEquals(2, runs.size());
    }

    @Test
    public void shouldDeleteSidsFromRunByPipelineId() {
        final RunSid runSid = new RunSid();
        runSid.setName(GROUP_NAME);
        runSid.setIsPrincipal(false);
        final Pipeline pipeline = getPipeline();
        final PipelineRun run = createRunWithRunSids(pipeline.getId(), null, Collections.singletonList(runSid));

        pipelineRunDao.deleteRunSidsByPipelineId(pipeline.getId());

        final PipelineRun loadedRun = pipelineRunDao.loadPipelineRun(run.getId());
        assertTrue(CollectionUtils.isEmpty(loadedRun.getRunSids()));
    }

    @Test
    public void shouldCreateRunWithCustomInstanceNodeImage() {
        final PipelineRun run = buildPipelineRun(testPipeline.getId(), null);
        run.getInstance().setNodeImage(TEST_NODE_IMAGE);
        pipelineRunDao.createPipelineRun(run);

        final PipelineRun loadedRun = pipelineRunDao.loadPipelineRun(run.getId());
        
        assertNotNull(loadedRun.getInstance());
        assertThat(loadedRun.getInstance().getNodeImage(), CoreMatchers.is(TEST_NODE_IMAGE));
    }

    private PipelineRun createTestPipelineRun() {
        return createTestPipelineRun(testPipeline.getId());
    }

    private PipelineRun createTestPipelineRun(Long pipelineId) {
        return createTestPipelineRun(pipelineId, TEST_SERVICE_URL);
    }

    private PipelineRun createTestPipelineRun(Long pipelineId, String serviceUrl) {
        PipelineRun run = buildPipelineRun(pipelineId, serviceUrl);
        pipelineRunDao.createPipelineRun(run);
        return run;
    }

    private PipelineRun buildPipelineRun(final Long pipelineId, final String serviceUrl) {
        final Date now = Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant());
        return buildPipelineRun(pipelineId, serviceUrl, now, now);
    }

    private PipelineRun buildPipelineRun(final Long pipelineId, final String serviceUrl,
                                         final Date start, final Date end) {
        PipelineRun run = new PipelineRun();
        run.setPipelineId(pipelineId);
        run.setVersion("abcdefg");
        run.setStartDate(start);
        run.setEndDate(end);
        run.setStatus(TaskStatus.RUNNING);
        run.setCommitStatus(CommitStatus.NOT_COMMITTED);
        run.setLastChangeCommitTime(new Date());
        run.setPodId(TEST_POD_ID);
        run.setParams(TEST_PARAMS);
        run.setOwner(USER);
        run.setServiceUrl(serviceUrl);

        Map<SystemParams, String> systemParams = EnvVarsBuilderTest.matchSystemParams();
        PipelineConfiguration configuration = EnvVarsBuilderTest.matchPipeConfig();
        EnvVarsBuilder.buildEnvVars(run, configuration, systemParams, null);
        run.setEnvVars(run.getEnvVars());
        setRunInstance(run);
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

    private Pipeline getPipeline() {
        Pipeline testPipeline2 = new Pipeline();
        testPipeline2.setName("Test");
        testPipeline2.setRepository(TEST_REPO);
        testPipeline2.setRepositorySsh(TEST_REPO_SSH);
        testPipeline2.setOwner(TEST_USER);
        pipelineDao.createPipeline(testPipeline2);
        return testPipeline2;
    }

    private void updateTagsAndVerifySaveIsCorrect(final PipelineRun run, final Map<String, String> tags) {
        run.setTags(tags);
        pipelineRunDao.updateRunTags(run);
        loadTagsAndCompareWithExpected(run.getId(), tags);
    }

    private void loadTagsAndCompareWithExpected(final Long runId, final Map<String, String> tags) {
        final Map<String, String> loadedTags = pipelineRunDao.loadPipelineRun(runId).getTags();
        assertThat(loadedTags, CoreMatchers.is(tags));
    }

    private RunLog createLog(final PipelineRun run,
                             final TaskStatus running,
                             final String taskName) {
        RunLog runLog = new RunLog();
        runLog.setDate(DateUtils.now());
        runLog.setLogText("Log");
        runLog.setStatus(running);
        runLog.setRunId(run.getId());
        runLog.setTaskName(taskName);
        runLog.setTask(new PipelineTask(taskName));
        logDao.createRunLog(runLog);
        return runLog;
    }

    private void validateLoadRunBooleanFieldValue(final boolean expectedFieldValue,
                                                  final PipelineRun run,
                                                  final Function<PipelineRun, Boolean> fieldFunction) {
        assertEquals(expectedFieldValue, fieldFunction.apply(pipelineRunDao.loadPipelineRun(run.getId())));
        assertEquals(expectedFieldValue,
                fieldFunction.apply(pipelineRunDao.loadPipelineRuns(Collections.singletonList(run.getId())).get(0)));
        final PagingRunFilterVO pagingRunFilterVO = new PagingRunFilterVO();
        pagingRunFilterVO.setPage(1);
        pagingRunFilterVO.setPageSize(TEST_PAGE_SIZE);
        pagingRunFilterVO.setPipelineIds(Collections.singletonList(run.getPipelineId()));
        assertEquals(expectedFieldValue,
                fieldFunction.apply(pipelineRunDao.searchPipelineRuns(pagingRunFilterVO).get(0)));
        assertEquals(expectedFieldValue,
                fieldFunction.apply(pipelineRunDao.searchPipelineGroups(pagingRunFilterVO, null).get(0)));
    }

    private void createRunWithStartEndDates(final LocalDateTime startDate, final LocalDateTime endDate) {
        final PipelineRun run = buildPipelineRun(testPipeline.getId(), TEST_SERVICE_URL,
                                                 TestUtils.convertLocalDateTimeToDate(startDate),
                                                 TestUtils.convertLocalDateTimeToDate(endDate));
        if (endDate != null) {
            run.setStatus(TaskStatus.PAUSED);
        } else {
            run.setStatus(TaskStatus.STOPPED);
        }
        pipelineRunDao.createPipelineRun(run);
    }
}
