/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.dao.JdbcTemplateReadOnlyWrapper;
import com.epam.pipeline.dao.metadata.MetadataDao;
import com.epam.pipeline.dao.pipeline.ArchiveRunDao;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.dao.pipeline.RestartRunDao;
import com.epam.pipeline.dao.pipeline.RunLogDao;
import com.epam.pipeline.dao.pipeline.RunStatusDao;
import com.epam.pipeline.dao.pipeline.StopServerlessRunDao;
import com.epam.pipeline.dao.run.RunServiceUrlDao;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.StopServerlessRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.PipelineRunServiceUrl;
import com.epam.pipeline.entity.pipeline.run.RestartRun;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.pipeline.run.parameter.RunAccessType;
import com.epam.pipeline.entity.pipeline.run.parameter.RunSid;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.ExtendedRole;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.cluster.PodMonitor;
import com.epam.pipeline.manager.cluster.autoscale.AutoscaleManager;
import com.epam.pipeline.manager.cluster.costs.ClusterCostsMonitoringService;
import com.epam.pipeline.manager.cluster.performancemonitoring.ResourceMonitoringManager;
import com.epam.pipeline.manager.cluster.pool.NodePoolMonitoringService;
import com.epam.pipeline.manager.docker.scan.AggregatingToolScanManager;
import com.epam.pipeline.manager.docker.scan.ToolScanScheduler;
import com.epam.pipeline.manager.dts.DtsMonitoringManager;
import com.epam.pipeline.manager.firecloud.FirecloudApiService;
import com.epam.pipeline.manager.firecloud.FirecloudManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.quota.BillingQuotasScheduler;
import com.epam.pipeline.manager.user.BlockedUsersMonitoringService;
import com.epam.pipeline.manager.user.InactiveUsersMonitoringService;
import com.epam.pipeline.manager.user.RoleManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.util.TestUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SuppressWarnings("unused")
@Transactional
public class ArchiveRunServiceSpringTest extends AbstractManagerTest {
    private static final String TEST = "test";
    private static final String OWNER = "OWNER";
    private static final String POD = "pod-id";
    private static final int DAYS = 5;
    private static final Long USER_ID = 1L;
    private static final Date TEST_DATE = nowMinusDays(DAYS + 1);
    private static final Date AFTER_TEST_DATE = nowMinusDays(DAYS - 1);
    private static final String GROUP1 = "group1";
    private static final String GROUP2 = "group2";
    private static final Long GROUP_ID1 = 1L;
    private static final Long GROUP_ID2 = 2L;
    private static final String USER1 = "user1";
    private static final String USER2 = "user2";
    private static final String USER3 = "user3";
    private static final Long USER_ID1 = 2L;
    private static final Long USER_ID2 = 3L;
    private static final Long USER_ID3 = 4L;
    private static final int GROUP_DAYS_1 = 10;
    private static final int GROUP_DAYS_2 = 15;
    private static final String STRING = "string";
    private static final int CHUNK_SIZE = 2;

    @MockBean
    private EntityManager entityManager;
    @MockBean
    private AggregatingToolScanManager aggregatingToolScanManager;
    @MockBean
    private PodMonitor podMonitor;
    @MockBean
    private AutoscaleManager autoscaleManager;
    @MockBean
    private ClusterCostsMonitoringService clusterCostsMonitoringService;
    @MockBean
    private ResourceMonitoringManager resourceMonitoringManager;
    @MockBean
    private NodePoolMonitoringService nodePoolMonitoringService;
    @MockBean
    private ToolScanScheduler toolScanScheduler;
    @MockBean
    private DtsMonitoringManager dtsMonitoringManager;
    @MockBean
    private FirecloudApiService firecloudApiService;
    @MockBean
    private FirecloudManager firecloudManager;
    @MockBean
    private RunScheduleMonitoringManager runScheduleMonitoringManager;
    @MockBean
    private BillingQuotasScheduler billingQuotasScheduler;
    @MockBean
    private BlockedUsersMonitoringService blockedUsersMonitoringService;
    @MockBean
    private InactiveUsersMonitoringService inactiveUsersMonitoringService;

    @SpyBean
    private PipelineRunDao pipelineRunDao;
    @SpyBean
    private JdbcTemplateReadOnlyWrapper jdbcTemplateReadOnlyWrapper;
    @Autowired
    private ArchiveRunDao archiveRunDao;
    @Autowired
    private RunLogDao runLogDao;
    @Autowired
    private RestartRunDao restartRunDao;
    @Autowired
    private RunServiceUrlDao runServiceUrlDao;
    @Autowired
    private RunStatusDao runStatusDao;
    @Autowired
    private StopServerlessRunDao stopServerlessRunDao;
    @MockBean
    private MetadataDao metadataDao;
    @MockBean
    private UserManager userManager;
    @MockBean
    private RoleManager roleManager;
    @MockBean
    private PreferenceManager preferenceManager;
    @Autowired
    private ArchiveRunService archiveRunService;

    @Before
    public void setUp() {
        doReturn(TEST).when(preferenceManager).getPreference(SystemPreferences.SYSTEM_ARCHIVE_RUN_METADATA_KEY);
        doReturn(CHUNK_SIZE).when(preferenceManager)
                .getPreference(SystemPreferences.SYSTEM_ARCHIVE_RUN_RUNS_CHUNK_SIZE);
        doReturn(CHUNK_SIZE).when(preferenceManager)
                .getPreference(SystemPreferences.SYSTEM_ARCHIVE_RUN_OWNERS_CHUNK_SIZE);
        doReturn(false).when(preferenceManager)
                .getPreference(SystemPreferences.SYSTEM_ARCHIVE_RUN_DRY_RUN_REGIME);
    }

    @Test
    @Transactional
    public void shouldArchiveClusterRun() {
        doReturn(usersMetadata()).when(metadataDao).searchMetadataEntriesByClassAndKey(AclClass.PIPELINE_USER, TEST);
        doReturn(Collections.singletonList(user(USER_ID, OWNER))).when(userManager)
                .loadUsersById(Collections.singletonList(USER_ID));

        final PipelineRun run = run();
        run.setEndDate(TEST_DATE);
        pipelineRunDao.createPipelineRun(run);

        final PipelineRun childRun1 = run();
        childRun1.setParentRunId(run.getId());
        pipelineRunDao.createPipelineRun(childRun1);

        final PipelineRun childRun2 = run();
        childRun2.setParentRunId(run.getId());
        pipelineRunDao.createPipelineRun(childRun2);

        final PipelineRun runAfterTestDate = run();
        runAfterTestDate.setEndDate(AFTER_TEST_DATE);
        pipelineRunDao.createPipelineRun(runAfterTestDate);

        archiveRunService.archiveRuns();

        assertThat(pipelineRunDao.loadPipelineRuns(Arrays.asList(run.getId(), childRun1.getId(), childRun2.getId())))
                .isNullOrEmpty();
        assertThat(pipelineRunDao.loadPipelineRun(runAfterTestDate.getId())).isNotNull();
    }

    @Test
    @Transactional
    public void shouldArchiveRestartedRunsPartially() {
        doReturn(usersMetadata()).when(metadataDao).searchMetadataEntriesByClassAndKey(AclClass.PIPELINE_USER, TEST);
        doReturn(Collections.singletonList(user(USER_ID, OWNER))).when(userManager)
                .loadUsersById(Collections.singletonList(USER_ID));

        final PipelineRun run = run();
        run.setEndDate(TEST_DATE);
        pipelineRunDao.createPipelineRun(run);

        final PipelineRun restart1 = run();
        restart1.setEndDate(TEST_DATE);
        pipelineRunDao.createPipelineRun(restart1);
        final RestartRun restartRun1 = restartRun(run, restart1, TEST_DATE);
        restartRunDao.createPipelineRestartRun(restartRun1);

        final PipelineRun restart2 = run();
        restart2.setEndDate(AFTER_TEST_DATE);
        pipelineRunDao.createPipelineRun(restart2);
        final RestartRun restartRun2 = restartRun(restart1, restart2, AFTER_TEST_DATE);
        restartRunDao.createPipelineRestartRun(restartRun2);

        final PipelineRun runAfterTestDate = run();
        runAfterTestDate.setEndDate(AFTER_TEST_DATE);
        pipelineRunDao.createPipelineRun(runAfterTestDate);

        archiveRunService.archiveRuns();

        assertThat(pipelineRunDao.loadPipelineRuns(Arrays.asList(run.getId(), restart1.getId()))).isNullOrEmpty();
        assertThat(pipelineRunDao.loadPipelineRun(runAfterTestDate.getId())).isNotNull();
        assertThat(pipelineRunDao.loadPipelineRun(restart2.getId())).isNotNull();
        assertThat(restartRunDao.loadRestartedRunById(restartRun1.getRestartedRunId())).isEqualTo(Optional.empty());
        assertThat(restartRunDao.loadRestartedRunById(restartRun2.getRestartedRunId())).isNotNull();
    }

    @Test
    @Transactional
    public void shouldArchiveRunsWithLogs() {
        doReturn(usersMetadata()).when(metadataDao).searchMetadataEntriesByClassAndKey(AclClass.PIPELINE_USER, TEST);
        doReturn(Collections.singletonList(user(USER_ID, OWNER))).when(userManager)
                .loadUsersById(Collections.singletonList(USER_ID));

        final PipelineRun run1 = run();
        run1.setEndDate(TEST_DATE);
        pipelineRunDao.createPipelineRun(run1);
        final RunLog runLog1 = runLog(run1.getId());
        runLogDao.createRunLog(runLog1);

        final PipelineRun run2 = run();
        run2.setEndDate(TEST_DATE);
        pipelineRunDao.createPipelineRun(run2);
        final RunLog runLog2 = runLog(run2.getId());
        runLogDao.createRunLog(runLog2);

        final PipelineRun runAfterTestDate = run();
        runAfterTestDate.setEndDate(AFTER_TEST_DATE);
        pipelineRunDao.createPipelineRun(runAfterTestDate);

        archiveRunService.archiveRuns();

        assertThat(pipelineRunDao.loadPipelineRuns(Arrays.asList(run1.getId(), run2.getId()))).isNullOrEmpty();
        assertThat(pipelineRunDao.loadPipelineRun(runAfterTestDate.getId())).isNotNull();
        assertThat(runLogDao.loadTasksForRun(run1.getId())).isNullOrEmpty();
        assertThat(runLogDao.loadTasksForRun(run2.getId())).isNullOrEmpty();
    }

    @Test
    @Transactional
    public void shouldArchiveRunsWithRunSids() {
        doReturn(usersMetadata()).when(metadataDao).searchMetadataEntriesByClassAndKey(AclClass.PIPELINE_USER, TEST);
        doReturn(Collections.singletonList(user(USER_ID, OWNER))).when(userManager)
                .loadUsersById(Collections.singletonList(USER_ID));

        final PipelineRun run1 = run();
        run1.setEndDate(TEST_DATE);
        run1.setRunSids(Collections.singletonList(userSid()));
        pipelineRunDao.createPipelineRun(run1);

        final PipelineRun run2 = run();
        run2.setEndDate(TEST_DATE);
        run2.setRunSids(Collections.singletonList(userSid()));
        pipelineRunDao.createPipelineRun(run2);

        final PipelineRun runAfterTestDate = run();
        runAfterTestDate.setEndDate(AFTER_TEST_DATE);
        pipelineRunDao.createPipelineRun(runAfterTestDate);

        archiveRunService.archiveRuns();

        assertThat(pipelineRunDao.loadPipelineRuns(Arrays.asList(run1.getId(), run2.getId()))).isNullOrEmpty();
        assertThat(pipelineRunDao.loadPipelineRun(runAfterTestDate.getId())).isNotNull();
    }

    @Test
    @Transactional
    public void shouldArchiveRunsWithServiceUrls() {
        doReturn(usersMetadata()).when(metadataDao).searchMetadataEntriesByClassAndKey(AclClass.PIPELINE_USER, TEST);
        doReturn(Collections.singletonList(user(USER_ID, OWNER))).when(userManager)
                .loadUsersById(Collections.singletonList(USER_ID));

        final PipelineRun run1 = run();
        run1.setEndDate(TEST_DATE);
        pipelineRunDao.createPipelineRun(run1);
        runServiceUrlDao.save(serviceUrl(run1.getId()));

        final PipelineRun run2 = run();
        run2.setEndDate(TEST_DATE);
        pipelineRunDao.createPipelineRun(run2);
        runServiceUrlDao.save(serviceUrl(run2.getId()));

        final PipelineRun runAfterTestDate = run();
        runAfterTestDate.setEndDate(AFTER_TEST_DATE);
        pipelineRunDao.createPipelineRun(runAfterTestDate);
        runServiceUrlDao.save(serviceUrl(runAfterTestDate.getId()));

        archiveRunService.archiveRuns();

        assertThat(runServiceUrlDao.findByRunId(run1.getId())).isNullOrEmpty();
        assertThat(runServiceUrlDao.findByRunId(run2.getId())).isNullOrEmpty();
        assertThat(runServiceUrlDao.findByRunId(runAfterTestDate.getId())).isNotNull();
        assertThat(pipelineRunDao.loadPipelineRuns(Arrays.asList(run1.getId(), run2.getId()))).isNullOrEmpty();
        assertThat(pipelineRunDao.loadPipelineRun(runAfterTestDate.getId())).isNotNull();
    }

    @Test
    @Transactional
    public void shouldArchiveRunsWithRunStatuses() {
        doReturn(usersMetadata()).when(metadataDao).searchMetadataEntriesByClassAndKey(AclClass.PIPELINE_USER, TEST);
        doReturn(Collections.singletonList(user(USER_ID, OWNER))).when(userManager)
                .loadUsersById(Collections.singletonList(USER_ID));

        final PipelineRun run1 = run();
        run1.setEndDate(TEST_DATE);
        pipelineRunDao.createPipelineRun(run1);
        runStatusDao.saveStatus(runStatus(run1.getId()));

        final PipelineRun run2 = run();
        run2.setEndDate(TEST_DATE);
        pipelineRunDao.createPipelineRun(run2);
        runStatusDao.saveStatus(runStatus(run2.getId()));

        final PipelineRun runAfterTestDate = run();
        runAfterTestDate.setEndDate(AFTER_TEST_DATE);
        pipelineRunDao.createPipelineRun(runAfterTestDate);
        runStatusDao.saveStatus(runStatus(runAfterTestDate.getId()));

        archiveRunService.archiveRuns();

        assertThat(runStatusDao.loadRunStatus(run1.getId())).isNullOrEmpty();
        assertThat(runStatusDao.loadRunStatus(run2.getId())).isNullOrEmpty();
        assertThat(runStatusDao.loadRunStatus(runAfterTestDate.getId())).isNotNull();
        assertThat(pipelineRunDao.loadPipelineRuns(Arrays.asList(run1.getId(), run2.getId()))).isNullOrEmpty();
        assertThat(pipelineRunDao.loadPipelineRun(runAfterTestDate.getId())).isNotNull();
    }

    @Test
    @Transactional
    public void shouldArchiveRunsWithServerlessRun() {
        doReturn(usersMetadata()).when(metadataDao).searchMetadataEntriesByClassAndKey(AclClass.PIPELINE_USER, TEST);
        doReturn(Collections.singletonList(user(USER_ID, OWNER))).when(userManager)
                .loadUsersById(Collections.singletonList(USER_ID));

        final PipelineRun run1 = run();
        run1.setEndDate(TEST_DATE);
        pipelineRunDao.createPipelineRun(run1);
        stopServerlessRunDao.createServerlessRun(serverlessRun(run1.getId()));

        final PipelineRun run2 = run();
        run2.setEndDate(TEST_DATE);
        pipelineRunDao.createPipelineRun(run2);
        stopServerlessRunDao.createServerlessRun(serverlessRun(run2.getId()));

        final PipelineRun runAfterTestDate = run();
        runAfterTestDate.setEndDate(AFTER_TEST_DATE);
        pipelineRunDao.createPipelineRun(runAfterTestDate);
        stopServerlessRunDao.createServerlessRun(serverlessRun(runAfterTestDate.getId()));

        archiveRunService.archiveRuns();

        assertThat(pipelineRunDao.loadPipelineRuns(Arrays.asList(run1.getId(), run2.getId()))).isNullOrEmpty();
        assertThat(pipelineRunDao.loadPipelineRun(runAfterTestDate.getId())).isNotNull();
        assertThat(stopServerlessRunDao.loadByRunId(run1.getId())).isEqualTo(Optional.empty());
        assertThat(stopServerlessRunDao.loadByRunId(run2.getId())).isEqualTo(Optional.empty());
        assertThat(stopServerlessRunDao.loadByRunId(runAfterTestDate.getId()).get()).isNotNull();
    }

    @Test
    @Transactional
    public void shouldArchiveRunsByGroups() {
        doReturn(usersMetadata(USER_ID1)).when(metadataDao)
                .searchMetadataEntriesByClassAndKey(AclClass.PIPELINE_USER, TEST);
        doReturn(groupsMetadata()).when(metadataDao).searchMetadataEntriesByClassAndKey(AclClass.ROLE, TEST);
        final PipelineUser user1 = user(USER_ID1, USER1);
        final PipelineUser user2 = user(USER_ID2, USER2);
        final PipelineUser user3 = user(USER_ID3, USER3);
        doReturn(Arrays.asList(user1, user2, user3)).when(userManager)
                .loadUsersById(Arrays.asList(USER_ID1, USER_ID2, USER_ID3));
        final Role role1 = role(GROUP_ID1, GROUP1, Arrays.asList(user1, user2));
        final Role role2 = role(GROUP_ID2, GROUP2, Arrays.asList(user2, user3));
        doReturn(Arrays.asList(role1, role2)).when(roleManager).loadAllRoles(true);

        final PipelineRun run1 = run();
        run1.setEndDate(TEST_DATE);
        run1.setOwner(USER1);
        pipelineRunDao.createPipelineRun(run1);

        final PipelineRun run2 = run();
        run2.setEndDate(TEST_DATE);
        run2.setOwner(USER2);
        pipelineRunDao.createPipelineRun(run2);

        final PipelineRun run3 = run();
        run3.setEndDate(TEST_DATE);
        run3.setOwner(USER3);
        pipelineRunDao.createPipelineRun(run3);

        final PipelineRun runAfterTestDate = run();
        runAfterTestDate.setEndDate(AFTER_TEST_DATE);
        runAfterTestDate.setOwner(USER1);
        pipelineRunDao.createPipelineRun(runAfterTestDate);

        archiveRunService.archiveRuns();

        assertThat(pipelineRunDao.loadPipelineRuns(Collections.singletonList(run1.getId()))).isNullOrEmpty();
        assertThat(pipelineRunDao.loadPipelineRuns(Arrays.asList(run2.getId(), run3.getId(), runAfterTestDate.getId())))
                .hasSize(3);

        final ArgumentCaptor<Map<String, Date>> argument = ArgumentCaptor.forClass((Class) Map.class);
        verify(pipelineRunDao, times(3)).loadRunsByOwnerAndEndDateBeforeAndStatusIn(
                argument.capture(), any(), anyInt(), anyBoolean(), anyInt());
        final Map<String, Date> firstChunkResults = argument.getAllValues().get(0);
        assertThat(firstChunkResults).hasSize(2);
        assertDays(firstChunkResults.get(USER1), DAYS);
        assertDays(firstChunkResults.get(USER2), GROUP_DAYS_1);
        final Map<String, Date> thirdChunkResults = argument.getAllValues().get(2);
        assertThat(thirdChunkResults).hasSize(1);
        assertDays(thirdChunkResults.get(USER3), GROUP_DAYS_2);
    }

    @Test
    @Transactional
    public void shouldNotArchiveRunsChunksIfDryRun() {
        doReturn(true).when(preferenceManager)
                .getPreference(SystemPreferences.SYSTEM_ARCHIVE_RUN_DRY_RUN_REGIME);
        doReturn(usersMetadata()).when(metadataDao).searchMetadataEntriesByClassAndKey(AclClass.PIPELINE_USER, TEST);
        doReturn(Collections.singletonList(user(USER_ID, OWNER))).when(userManager)
                .loadUsersById(Collections.singletonList(USER_ID));

        final PipelineRun run1 = run();
        run1.setEndDate(TEST_DATE);
        pipelineRunDao.createPipelineRun(run1);

        final PipelineRun run2 = run();
        run2.setEndDate(TEST_DATE);
        pipelineRunDao.createPipelineRun(run2);

        final PipelineRun run3 = run();
        run3.setEndDate(TEST_DATE);
        pipelineRunDao.createPipelineRun(run3);

        final PipelineRun runAfterTestDate = run();
        runAfterTestDate.setEndDate(AFTER_TEST_DATE);
        pipelineRunDao.createPipelineRun(runAfterTestDate);

        archiveRunService.archiveRuns();

        assertThat(pipelineRunDao.loadPipelineRuns(
                Arrays.asList(run1.getId(), run2.getId(), run3.getId(), runAfterTestDate.getId())))
                .hasSize(4);

        verifyDryRunInvoked(7, 6 * 2);
    }

    private PipelineRun run() {
        return TestUtils.createPipelineRun(null, null, TaskStatus.STOPPED, OWNER,
                null, null, true, null, null, POD, 1L);
    }

    private List<MetadataEntry> usersMetadata() {
        return usersMetadata(USER_ID);
    }

    private List<MetadataEntry> usersMetadata(final Long userId) {
        final EntityVO entityVO = new EntityVO(userId, AclClass.PIPELINE_USER);
        final Map<String, PipeConfValue> data = Collections.singletonMap(
                TEST, new PipeConfValue(STRING, String.valueOf(DAYS)));
        final MetadataEntry metadataEntry = new MetadataEntry();
        metadataEntry.setEntity(entityVO);
        metadataEntry.setData(data);
        return Collections.singletonList(metadataEntry);
    }

    private List<MetadataEntry> groupsMetadata() {
        final EntityVO entityVO1 = new EntityVO(GROUP_ID1, AclClass.ROLE);
        final MetadataEntry metadataEntry1 = new MetadataEntry();
        metadataEntry1.setEntity(entityVO1);
        metadataEntry1.setData(Collections.singletonMap(TEST, new PipeConfValue(STRING, String.valueOf(GROUP_DAYS_1))));

        final EntityVO entityVO2 = new EntityVO(GROUP_ID2, AclClass.ROLE);
        final MetadataEntry metadataEntry2 = new MetadataEntry();
        metadataEntry2.setEntity(entityVO2);
        metadataEntry2.setData(Collections.singletonMap(TEST, new PipeConfValue(STRING, String.valueOf(GROUP_DAYS_2))));

        return Arrays.asList(metadataEntry1, metadataEntry2);
    }

    private PipelineUser user(final Long id, final String owner) {
        final PipelineUser pipelineUser = new PipelineUser();
        pipelineUser.setId(id);
        pipelineUser.setUserName(owner);
        return pipelineUser;
    }

    private Role role(final Long roleId, final String roleName, final List<PipelineUser> users) {
        final ExtendedRole role = new ExtendedRole();
        role.setId(roleId);
        role.setName(roleName);
        role.setUsers(users);
        return role;
    }

    private RestartRun restartRun(final PipelineRun parent, final PipelineRun restarted, final Date date) {
        final RestartRun restartRun = new RestartRun();
        restartRun.setRestartedRunId(restarted.getId());
        restartRun.setParentRunId(parent.getId());
        restartRun.setDate(date);
        return restartRun;
    }

    private RunLog runLog(final Long runId) {
        return RunLog.builder()
                .runId(runId)
                .logText(TEST)
                .status(TaskStatus.FAILURE)
                .date(TEST_DATE)
                .build();
    }

    private RunSid userSid() {
        final RunSid runSid = new RunSid();
        runSid.setName(OWNER);
        runSid.setAccessType(RunAccessType.SSH);
        runSid.setIsPrincipal(true);
        return runSid;
    }

    private PipelineRunServiceUrl serviceUrl(final Long runId) {
        final PipelineRunServiceUrl pipelineRunServiceUrl = new PipelineRunServiceUrl();
        pipelineRunServiceUrl.setPipelineRunId(runId);
        pipelineRunServiceUrl.setServiceUrl(TEST);
        return pipelineRunServiceUrl;
    }

    private RunStatus runStatus(final Long runId) {
        return RunStatus.builder()
                .runId(runId)
                .status(TaskStatus.FAILURE)
                .timestamp(LocalDateTime.now())
                .reason(TEST)
                .build();
    }

    private StopServerlessRun serverlessRun(final Long runId) {
        return StopServerlessRun.builder()
                .runId(runId)
                .lastUpdate(LocalDateTime.now())
                .stopAfter(1L)
                .build();
    }

    private void assertDays(final Date resultDate, final int expectedDays) {
        assertThat(Duration.between(resultDate.toInstant(), DateUtils.now().toInstant()).toDays())
                .isEqualTo(expectedDays);
    }

    private static Date nowMinusDays(final int days) {
        return DateUtils.convertLocalDateTimeToDate(DateUtils.nowUTC().minusDays(days));
    }

    private void verifyDryRunInvoked(final int queryTimes, final int updateTimes) {
        verify(jdbcTemplateReadOnlyWrapper, times(queryTimes))
                .query(anyString(), (SqlParameterSource) any(), (RowMapper<Object>) any());
        verify(jdbcTemplateReadOnlyWrapper, times(updateTimes)).update(anyString(), (SqlParameterSource) any());
    }
}
