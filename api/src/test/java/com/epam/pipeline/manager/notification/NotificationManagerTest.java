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

package com.epam.pipeline.manager.notification;

import com.epam.pipeline.entity.notification.NotificationType;
import static com.epam.pipeline.entity.notification.NotificationType.*;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.epam.pipeline.controller.vo.notification.NotificationMessageVO;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.CommitStatus;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.execution.EnvVarsBuilder;
import com.epam.pipeline.manager.execution.EnvVarsBuilderTest;
import com.epam.pipeline.manager.execution.SystemParams;
import com.epam.pipeline.manager.preference.PreferenceManager;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.Matchers;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.epam.pipeline.app.TestApplication;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.dao.issue.IssueDao;
import com.epam.pipeline.dao.notification.MonitoringNotificationDao;
import com.epam.pipeline.dao.notification.NotificationSettingsDao;
import com.epam.pipeline.dao.notification.NotificationTemplateDao;
import com.epam.pipeline.dao.pipeline.PipelineDao;
import com.epam.pipeline.dao.user.UserDao;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.issue.IssueComment;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationSettings;
import com.epam.pipeline.entity.notification.NotificationTemplate;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.ExtendedRole;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.cluster.PodMonitor;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.util.KubernetesTestUtils;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;

@ContextConfiguration(classes = TestApplication.class)
@Transactional
public class NotificationManagerTest extends AbstractManagerTest {
    private static final double TEST_CPU_RATE1 = 0.123;
    private static final double TEST_CPU_RATE2 = 0.456;
    private static final double TEST_MEMORY_RATE = 0.95;
    private static final double TEST_DISK_RATE = 0.99;
    private static final double PERCENT = 100.0;
    private static final String SUBJECT = "subject";
    private static final String BODY = "body";
    private static final String NON_EXISTING_USER = "not_existing_user";
    private static final Map<String, Object> PARAMETERS = Collections.singletonMap("key", "value");
    private static final int LONG_STATUS_THRESHOLD = 100;
    private static final Duration LONG_RUNNING_DURATION = Duration.standardMinutes(6);
    private static final Long LONG_PAUSED_SECONDS = 10L;
    private static final String TEST_PLATFORM = "linux";

    @Autowired
    private NotificationManager notificationManager;

    @Autowired
    private PodMonitor podMonitor;

    @MockBean
    private PipelineRunManager pipelineRunManager;

    @Autowired
    private PipelineRunDao pipelineRunDao;

    @MockBean
    private KubernetesManager kubernetesManager;

    @Autowired
    private UserDao userDao;

    @Autowired
    private NotificationTemplateDao notificationTemplateDao;

    @Autowired
    private MonitoringNotificationDao monitoringNotificationDao;

    @Autowired
    private NotificationSettingsDao notificationSettingsDao;

    @Autowired
    private PipelineDao pipelineDao;

    @Autowired
    private IssueDao issueDao;

    @Autowired
    private PreferenceManager preferenceManager;

    private PipelineRun longRunnging;
    private PipelineUser admin;
    private PipelineUser testOwner;
    private PipelineUser testUser1;
    private PipelineUser testUser2;
    private NotificationTemplate longRunningTemplate;
    private NotificationTemplate issueTemplate;
    private NotificationTemplate issueCommentTemplate;
    private NotificationTemplate longPausedTemplate;
    private NotificationTemplate stopLongPausedTemplate;
    private ObjectMeta podMetadata;
    private NotificationSettings longRunningSettings;
    private NotificationSettings issueSettings;
    private NotificationSettings issueCommentSettings;
    private NotificationSettings highConsuming;

    @Mock
    private KubernetesClient mockClient;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        admin = new PipelineUser("admin");
        userDao.createUser(admin, Collections.singletonList(DefaultRoles.ROLE_ADMIN.getId()));
        testOwner = new PipelineUser("testOwner");
        userDao.createUser(testOwner, Collections.emptyList());
        testUser1 = new PipelineUser("TestUser1");
        userDao.createUser(testUser1, Collections.emptyList());
        testUser2 = new PipelineUser("TestUser2");
        userDao.createUser(testUser2, Collections.emptyList());

        longRunningTemplate = createTemplate(1L, "testTemplate");
        longRunningSettings = createSettings(LONG_RUNNING, longRunningTemplate.getId(), 1L, 1L);
        issueTemplate = createTemplate(3L, "issueTemplate");
        issueSettings = createSettings(NEW_ISSUE, issueTemplate.getId(), -1L, -1L);
        issueCommentTemplate = createTemplate(4L, "issueCommentTemplate");
        issueCommentSettings = createSettings(NEW_ISSUE_COMMENT, issueCommentTemplate.getId(), -1L, -1L);
        createTemplate(IDLE_RUN.getId(), "idle-run-template");
        createSettings(IDLE_RUN, IDLE_RUN.getId(), 1, 1);
        longPausedTemplate = createTemplate(LONG_PAUSED.getId(), "longPausedTemplate");
        createSettings(LONG_PAUSED, longPausedTemplate.getId(), LONG_PAUSED_SECONDS,
                LONG_PAUSED_SECONDS);
        stopLongPausedTemplate = createTemplate(LONG_PAUSED_STOPPED.getId(), "stopLongPausedTemplate");
        createSettings(LONG_PAUSED_STOPPED, stopLongPausedTemplate.getId(), LONG_PAUSED_SECONDS,
                LONG_PAUSED_SECONDS);

        createTemplate(HIGH_CONSUMED_RESOURCES.getId(), "idle-run-template");
        highConsuming = createSettings(HIGH_CONSUMED_RESOURCES, HIGH_CONSUMED_RESOURCES.getId(),
                HIGH_CONSUMED_RESOURCES.getDefaultThreshold(), HIGH_CONSUMED_RESOURCES.getDefaultResendDelay());

        longRunnging = new PipelineRun();
        DateTime date = DateTime.now(DateTimeZone.UTC).minus(LONG_RUNNING_DURATION);
        longRunnging.setStartDate(date.toDate());
        longRunnging.setStatus(TaskStatus.RUNNING);
        longRunnging.setOwner(admin.getUserName());
        longRunnging.setPodId("longRunning");

        when(pipelineRunManager.loadRunningAndTerminatedPipelineRuns())
            .thenReturn(Collections.singletonList(longRunnging));
        when(pipelineRunManager.loadPipelineRun(org.mockito.Matchers.any())).thenReturn(longRunnging);
        when(kubernetesManager.getKubernetesClient()).thenReturn(mockClient);

        ExtendedRole noAdmins = new ExtendedRole();
        noAdmins.setUsers(Collections.emptyList());

        Pod mockPod = mock(Pod.class);
        PodStatus podStatus = new PodStatus(null, null, "hostIp", null, "", "",
                                            "podIp", "bla-bla", "5 o'clock",  "");
        podMetadata = new ObjectMeta();
        podMetadata.setLabels(Collections.emptyMap());

        when(mockPod.getStatus()).thenReturn(podStatus);
        when(mockPod.getMetadata()).thenReturn(podMetadata);

        MixedOperation<Pod, PodList, DoneablePod, PodResource<Pod, DoneablePod>> mockPods =
            new KubernetesTestUtils.MockPods()
                .mockNamespace(Matchers.any(String.class))
                    .mockWithName(Matchers.any(String.class))
                    .mockPod(mockPod)
                .and()
                .getMockedEntity();

        when(mockClient.pods()).thenReturn(mockPods);
    }

    @Test
    public void testNotifyLongRunning() {
        podMonitor.updateStatus();
        List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(1, messages.size());
        Assert.assertEquals(admin.getId(), messages.get(0).getToUserId());
        Assert.assertTrue(messages.get(0).getCopyUserIds().contains(admin.getId()));
        Assert.assertEquals(longRunningTemplate.getId(), messages.get(0).getTemplate().getId());

        ArgumentCaptor<PipelineRun> runCaptor = ArgumentCaptor.forClass(PipelineRun.class);
        verify(pipelineRunManager).updatePipelineRunLastNotification(runCaptor.capture());

        PipelineRun capturedRun = runCaptor.getValue();
        Assert.assertNotNull(capturedRun.getLastNotificationTime());
    }

    @Test
    public void testDoesntNotifyIfNotConfigure() {
        notificationSettingsDao.deleteNotificationSettingsById(longRunningSettings.getId());
        notificationTemplateDao.deleteNotificationTemplate(longRunningTemplate.getId());
        podMonitor.updateStatus();
        List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(0, messages.size());
    }

    @Test
    public void testNotifyNoOwner() {
        updateKeepInformedOwner(longRunningSettings, false);

        podMonitor.updateStatus();
        List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(1, messages.size());
        Assert.assertNull(messages.get(0).getToUserId());
    }

    @Test
    public void testReNotifyLongRunning() {
        longRunnging.setStartDate(DateTime.now(DateTimeZone.UTC).minusMinutes(9).toDate());
        longRunnging.setLastNotificationTime(DateTime.now(DateTimeZone.UTC).minusMinutes(6).toDate());

        podMonitor.updateStatus();
        List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(1, messages.size());
        Assert.assertEquals(admin.getId(), messages.get(0).getToUserId());
        Assert.assertTrue(messages.get(0).getCopyUserIds().contains(admin.getId()));
        Assert.assertEquals(longRunningTemplate.getId(), messages.get(0).getTemplate().getId());
    }

    @Test
    public void testWontNotifyClusterNode() {
        podMetadata.setLabels(Collections.singletonMap("cluster_id", "some_id"));
        podMonitor.updateStatus();

        List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertTrue(messages.isEmpty());
    }

    @Test
    public void testWontNotifyAdminsIfConfigured() {
        NotificationSettings settings = notificationSettingsDao.loadNotificationSettings(1L);
        settings.setKeepInformedAdmins(false);
        notificationSettingsDao.updateNotificationSettings(settings);
        notificationManager.notifyLongRunningTask(longRunnging, LONG_RUNNING_DURATION.getStandardSeconds(), settings);

        List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(1, messages.size());
        Assert.assertEquals(admin.getId(), messages.get(0).getToUserId());
        Assert.assertTrue(messages.get(0).getCopyUserIds().isEmpty());
        Assert.assertEquals(longRunningTemplate.getId(), messages.get(0).getTemplate().getId());

        settings.setKeepInformedAdmins(false);
        settings.setInformedUserIds(Collections.singletonList(userDao.loadUserByName("admin").getId()));
        notificationSettingsDao.updateNotificationSettings(settings);
        notificationManager.notifyLongRunningTask(longRunnging, LONG_RUNNING_DURATION.getStandardSeconds(), settings);
        messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertTrue(messages.get(messages.size() - 1).getCopyUserIds().contains(admin.getId()));
    }

    @Test
    public void testNotifyIssue() {
        Pipeline pipeline = createPipeline(testOwner);
        Issue issue = createIssue(testUser2, pipeline);

        notificationManager.notifyIssue(issue, pipeline, issue.getText());

        List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(1, messages.size());
        NotificationMessage message = messages.get(0);

        Assert.assertEquals(testOwner.getId(), message.getToUserId());
        Assert.assertTrue(message.getCopyUserIds().stream().anyMatch(id -> id.equals(testUser1.getId())));
        Assert.assertTrue(message.getCopyUserIds().stream().anyMatch(id -> id.equals(testUser2.getId())));
        Assert.assertTrue(message.getCopyUserIds().stream().anyMatch(id -> id.equals(admin.getId())));

        updateKeepInformedOwner(issueSettings, false);
        notificationManager.notifyIssue(issue, pipeline, issue.getText());
        messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(2, messages.size());
        Assert.assertNull(messages.get(1).getToUserId());
    }

    @Test
    public void shouldNotifyRunsStuckInStatus() {
        final PipelineRun notified = new PipelineRun();
        notified.setId(1L);
        notified.setStatus(TaskStatus.PAUSING);
        notified.setOwner(testUser1.getUserName());
        notified.setStartDate(new Date());
        notified.setRunStatuses(Collections.singletonList(
                RunStatus.builder()
                        .runId(notified.getId())
                        .status(TaskStatus.PAUSING)
                        .timestamp(DateUtils.nowUTC().minusDays(1))
                        .build()));

        final PipelineRun skipped = new PipelineRun();
        skipped.setId(1L);
        skipped.setStartDate(new Date());
        skipped.setStatus(TaskStatus.PAUSING);
        skipped.setOwner(testUser2.getUserName());
        skipped.setRunStatuses(Collections.singletonList(
                RunStatus.builder()
                        .runId(skipped.getId())
                        .status(TaskStatus.PAUSING)
                        .timestamp(DateUtils.nowUTC())
                        .build()));

        createSettings(LONG_STATUS, createTemplate(5L, "stuckStatusTemplate").getId(),
                LONG_STATUS_THRESHOLD, LONG_STATUS_THRESHOLD);

        notificationManager.notifyStuckInStatusRuns(Arrays.asList(notified, skipped));
        final List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(1, messages.size());
        Assert.assertEquals(testUser1.getId(), messages.get(0).getToUserId());

    }

    @Test
    public void testNotifyIssueComment() {
        Pipeline pipeline = createPipeline(testOwner);
        Issue issue = createIssue(testUser2, pipeline);
        issueDao.createIssue(issue);

        IssueComment comment = new IssueComment();
        comment.setIssueId(issue.getId());
        comment.setText("Notify @TestUser1");
        comment.setAuthor(testUser2.getUserName());

        notificationManager.notifyIssueComment(comment, issue, comment.getText());

        List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(1, messages.size());
        NotificationMessage message = messages.get(0);

        Assert.assertEquals(testUser2.getId(), message.getToUserId());
        Assert.assertEquals(issueCommentTemplate.getId(), message.getTemplate().getId());
        Assert.assertTrue(message.getCopyUserIds().stream().anyMatch(id -> id.equals(testUser1.getId())));
        Assert.assertTrue(message.getCopyUserIds().stream().anyMatch(id -> id.equals(testOwner.getId())));

        updateKeepInformedOwner(issueCommentSettings, false);
        notificationManager.notifyIssueComment(comment, issue, comment.getText());
        messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(2, messages.size());
        Assert.assertNull(messages.get(1).getToUserId());
    }

    @Test
    public void testNotifyIdleRun() {
        final PipelineRun run1 = createTestPipelineRun();
        run1.setOwner(testUser1.getUserName());
        pipelineRunDao.updateRun(run1);
        final PipelineRun run2 = createTestPipelineRun();
        run2.setOwner(testUser2.getUserName());
        pipelineRunDao.updateRun(run2);

        notificationManager.notifyIdleRuns(Arrays.asList(
            new ImmutablePair<>(run1, TEST_CPU_RATE1),
            new ImmutablePair<>(run2, TEST_CPU_RATE2)), IDLE_RUN);

        List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(2, messages.size());
        messages.forEach(m -> Assert.assertEquals(
            SystemPreferences.SYSTEM_IDLE_CPU_THRESHOLD_PERCENT.getDefaultValue().doubleValue(),
            m.getTemplateParameters().get("idleCpuLevel")));

        NotificationMessage run1Message = messages.stream()
            .filter(m -> m.getToUserId().equals(testUser1.getId()))
            .findFirst().get();

        Assert.assertEquals(TEST_CPU_RATE1 * PERCENT, run1Message.getTemplateParameters().get("cpuRate"));
        Assert.assertEquals(run1.getId().intValue(), run1Message.getTemplateParameters().get("id"));

        NotificationMessage run2Message = messages.stream()
            .filter(m -> m.getToUserId().equals(testUser2.getId()))
            .findFirst().get();

        Assert.assertEquals(TEST_CPU_RATE2 * PERCENT, run2Message.getTemplateParameters().get("cpuRate"));
        Assert.assertEquals(run2.getId().intValue(), run2Message.getTemplateParameters().get("id"));
    }

    @Test
    public void testNotifyHighConsumingRun() {
        PipelineRun run1 = createTestPipelineRun();
        PipelineRun run2 = createTestPipelineRun();

        List<Pair<PipelineRun, Map<ELKUsageMetric, Double>>> pipelinesMetrics = Arrays.asList(
                new ImmutablePair<>(run1, Collections.singletonMap(ELKUsageMetric.MEM, TEST_MEMORY_RATE)),
                new ImmutablePair<>(run2, Collections.singletonMap(ELKUsageMetric.FS, TEST_DISK_RATE)));

        notificationManager.notifyHighResourceConsumingRuns(pipelinesMetrics, HIGH_CONSUMED_RESOURCES);

        List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(2, messages.size());

        Assert.assertEquals(TEST_MEMORY_RATE * PERCENT, messages.get(0).getTemplateParameters().get("memoryRate"));
        Assert.assertEquals(0.0, messages.get(0).getTemplateParameters().get("diskRate"));

        Assert.assertEquals(0.0, messages.get(1).getTemplateParameters().get("memoryRate"));
        Assert.assertEquals(TEST_DISK_RATE * PERCENT, messages.get(1).getTemplateParameters().get("diskRate"));

        Assert.assertNotNull(
                monitoringNotificationDao.loadNotificationTimestamp(run1.getId(), HIGH_CONSUMED_RESOURCES));
        Assert.assertNotNull(
                monitoringNotificationDao.loadNotificationTimestamp(run2.getId(), HIGH_CONSUMED_RESOURCES));

        monitoringNotificationDao.deleteNotificationsByTemplateId(HIGH_CONSUMED_RESOURCES.getId());
        notificationManager.notifyHighResourceConsumingRuns(pipelinesMetrics, HIGH_CONSUMED_RESOURCES);

        messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(0, messages.size());
    }

    @Test
    public void notifyHighConsumingRunOnlyOnceIfItIsSetup() {
        highConsuming.setResendDelay(-1L);
        notificationSettingsDao.updateNotificationSettings(highConsuming);

        PipelineRun run1 = createTestPipelineRun();
        List<Pair<PipelineRun, Map<ELKUsageMetric, Double>>> pipelinesMetrics = Collections.singletonList(
                new ImmutablePair<>(run1, Collections.singletonMap(ELKUsageMetric.MEM, TEST_MEMORY_RATE)));

        notificationManager.notifyHighResourceConsumingRuns(pipelinesMetrics, HIGH_CONSUMED_RESOURCES);

        List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(1, messages.size());

        monitoringNotificationDao.deleteNotificationsByTemplateId(HIGH_CONSUMED_RESOURCES.getId());

        notificationManager.notifyHighResourceConsumingRuns(pipelinesMetrics, HIGH_CONSUMED_RESOURCES);

        messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(0, messages.size());
    }

    @Test
    public void testCreateNotificationFailsIfSubjectIsNotSpecified() {
        final String userName = testUser1.getUserName();
        final NotificationMessageVO messageWithoutSubject = new NotificationMessageVO();
        messageWithoutSubject.setBody(BODY);
        messageWithoutSubject.setToUser(userName);

        assertThrows(IllegalArgumentException.class,
            () -> notificationManager.createNotification(messageWithoutSubject));
    }

    @Test
    public void testCreateNotificationFailsIfBodyIsNotSpecified() {
        final String userName = testUser1.getUserName();
        final NotificationMessageVO messageWithoutBody = new NotificationMessageVO();
        messageWithoutBody.setSubject(SUBJECT);
        messageWithoutBody.setToUser(userName);

        assertThrows(IllegalArgumentException.class, () -> notificationManager.createNotification(messageWithoutBody));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testCreateNotificationFailsIfReceiverIsNotSpecified() {
        final NotificationMessageVO messageWithoutUserNames = new NotificationMessageVO();
        messageWithoutUserNames.setBody(BODY);
        messageWithoutUserNames.setSubject(SUBJECT);

        assertThrows(IllegalArgumentException.class,
            () -> notificationManager.createNotification(messageWithoutUserNames));
    }

    @Test
    public void testCreateNotificationsFailsIfReceiverUserDoesNotExist() {
        final NotificationMessageVO messageWithNonExistingUser = new NotificationMessageVO();
        messageWithNonExistingUser.setBody(BODY);
        messageWithNonExistingUser.setSubject(SUBJECT);
        messageWithNonExistingUser.setToUser(NON_EXISTING_USER);

        assertThrows(IllegalArgumentException.class,
            () -> notificationManager.createNotification(messageWithNonExistingUser));
    }

    @Test
    public void testCreateNotificationFailsIfOneOfCopyUsersDoesNotExist() {
        final NotificationMessageVO message = new NotificationMessageVO();
        message.setBody(BODY);
        message.setSubject(SUBJECT);
        message.setParameters(PARAMETERS);
        message.setToUser(testUser1.getUserName());
        message.setCopyUsers(Arrays.asList(testUser2.getUserName(), NON_EXISTING_USER));

        assertThrows(IllegalArgumentException.class, () -> notificationManager.createNotification(message));
    }

    @Test
    public void testCreateNotification() {
        final NotificationMessageVO message = new NotificationMessageVO();
        message.setBody(BODY);
        message.setSubject(SUBJECT);
        message.setParameters(PARAMETERS);
        message.setToUser(testUser1.getUserName());

        final NotificationMessage savedMessage = notificationManager.createNotification(message);

        final NotificationMessage loadedMessage =
                monitoringNotificationDao.loadMonitoringNotification(savedMessage.getId());
        Assert.assertEquals(BODY, loadedMessage.getBody());
        Assert.assertEquals(SUBJECT, loadedMessage.getSubject());
        Assert.assertEquals(PARAMETERS, loadedMessage.getTemplateParameters());
        Assert.assertEquals(testUser1.getId(), loadedMessage.getToUserId());
    }

    @Test
    public void testCreateNotificationWithExistingCopyUser() {
        final NotificationMessageVO message = new NotificationMessageVO();
        message.setBody(BODY);
        message.setSubject(SUBJECT);
        message.setParameters(PARAMETERS);
        message.setToUser(testUser1.getUserName());
        message.setCopyUsers(Collections.singletonList(testUser2.getUserName()));

        final NotificationMessage savedMessage = notificationManager.createNotification(message);

        final NotificationMessage loadedMessage =
                monitoringNotificationDao.loadMonitoringNotification(savedMessage.getId());
        Assert.assertEquals(BODY, loadedMessage.getBody());
        Assert.assertEquals(SUBJECT, loadedMessage.getSubject());
        Assert.assertEquals(PARAMETERS, loadedMessage.getTemplateParameters());
        Assert.assertEquals(testUser1.getId(), loadedMessage.getToUserId());
        Assert.assertEquals(Collections.singletonList(testUser2.getId()), loadedMessage.getCopyUserIds());
    }

    @Test
    public void shouldCreateNotificationForLongPausedRuns() {
        final PipelineRun runningRun = createTestPipelineRun(); // shall be skipped
        final PipelineRun pausedRun = buildPausedRun(DateUtils.nowUTC().minusSeconds(LONG_PAUSED_SECONDS));

        notificationManager.notifyLongPausedRuns(Arrays.asList(runningRun, pausedRun));

        final List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(1, messages.size());
        Assert.assertEquals(messages.get(0).getTemplate().getId(), longPausedTemplate.getId());

        Assert.assertTrue(notificationManager.loadLastNotificationTimestamp(pausedRun.getId(), LONG_PAUSED)
                .isPresent());
    }

    @Test
    public void shouldNotCreateNotificationForLongPausedRunsIfNotTimeout() {
        final PipelineRun pausedRun = buildPausedRun(DateUtils.nowUTC().plusSeconds(LONG_PAUSED_SECONDS));

        notificationManager.notifyLongPausedRuns(Collections.singletonList(pausedRun));

        final List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(0, messages.size());
    }

    @Test
    public void shouldCreateNotificationForStopLongPausedRuns() {
        final PipelineRun runningRun = createTestPipelineRun(); // shall be skipped
        final PipelineRun pausedRun = buildPausedRun(DateUtils.nowUTC().minusSeconds(LONG_PAUSED_SECONDS));

        notificationManager.notifyLongPausedRunsBeforeStop(Arrays.asList(runningRun, pausedRun));

        final List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(1, messages.size());
        Assert.assertEquals(messages.get(0).getTemplate().getId(), stopLongPausedTemplate.getId());
    }

    @Test
    public void shouldNotCreateNotificationForStopLongPausedRunsIfNotTimeout() {
        final PipelineRun pausedRun = buildPausedRun(DateUtils.nowUTC().plusSeconds(LONG_PAUSED_SECONDS));

        notificationManager.notifyLongPausedRunsBeforeStop(Collections.singletonList(pausedRun));

        final List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(0, messages.size());
    }

    @Test
    public void shouldNotNotifyIfInstanceTypeShouldExcluded() {
        final Preference preference = new Preference();
        preference.setName(SystemPreferences.SYSTEM_NOTIFICATIONS_EXCLUDE_INSTANCE_TYPES.getKey());
        preference.setValue("r5.*,m5.large");
        preferenceManager.update(Collections.singletonList(preference));

        final LocalDateTime statusUpdateTime = DateUtils.nowUTC().minusSeconds(LONG_PAUSED_SECONDS);
        final PipelineRun pausedRunToExclude1 = buildPausedRunWithNodeType(statusUpdateTime, "m5.large");
        final PipelineRun pausedRunToExclude2 = buildPausedRunWithNodeType(statusUpdateTime, "r5.large");
        final PipelineRun pausedRunToInclude = buildPausedRunWithNodeType(statusUpdateTime, "c5.xlarge");

        final List<PipelineRun> filtered = notificationManager.notifyLongPausedRunsBeforeStop(Arrays.asList(
                pausedRunToExclude1,
                pausedRunToExclude2,
                pausedRunToInclude));

        Assert.assertEquals(1, filtered.size());
        Assert.assertEquals(pausedRunToInclude.getId(), filtered.get(0).getId());
    }

    private PipelineRun buildPausedRun(final LocalDateTime statusUpdateTime) {
        final PipelineRun pausedRun = createTestPipelineRun();
        pausedRun.setStatus(TaskStatus.PAUSED);
        pausedRun.setRunStatuses(Collections.singletonList(RunStatus.builder()
                .runId(pausedRun.getId())
                .status(TaskStatus.PAUSED)
                .timestamp(statusUpdateTime)
                .build()));
        return pausedRun;
    }

    private PipelineRun buildPausedRunWithNodeType(final LocalDateTime statusUpdateTime, final String nodeType) {
        final PipelineRun pausedRun = buildPausedRun(statusUpdateTime);
        final RunInstance instance = buildRunInstance();
        instance.setNodeType(nodeType);
        pausedRun.setInstance(instance);
        return pausedRun;
    }

    private NotificationSettings createSettings(NotificationType type, long templateId, long threshold, long delay) {
        NotificationSettings settings = new NotificationSettings();
        settings.setType(type);
        settings.setKeepInformedAdmins(true);
        settings.setInformedUserIds(Collections.emptyList());
        settings.setTemplateId(templateId);
        settings.setThreshold(threshold);
        settings.setResendDelay(delay);
        settings.setEnabled(true);
        settings.setKeepInformedOwner(true);
        notificationSettingsDao.createNotificationSettings(settings);
        return settings;
    }

    private void updateKeepInformedOwner(NotificationSettings settings, boolean keepInformedOwner) {
        settings.setKeepInformedOwner(keepInformedOwner);
        notificationSettingsDao.updateNotificationSettings(settings);
    }

    private NotificationTemplate createTemplate(Long id, String name) {
        NotificationTemplate template = new NotificationTemplate(id);
        template.setName(name);
        template.setBody("//");
        template.setSubject("//");
        notificationTemplateDao.createNotificationTemplate(template);

        return template;
    }

    private PipelineRun createTestPipelineRun() {
        return createTestPipelineRun(null);
    }

    private PipelineRun createTestPipelineRun(Long pipelineId) {
        PipelineRun run = new PipelineRun();
        if (pipelineId != null) {
            run.setPipelineId(pipelineId);
        }
        run.setVersion("abcdefg");
        run.setStartDate(new Date());
        run.setEndDate(run.getStartDate());
        run.setStatus(TaskStatus.RUNNING);
        run.setCommitStatus(CommitStatus.NOT_COMMITTED);
        run.setLastChangeCommitTime(new Date());
        run.setPodId("pod");
        run.setOwner(testOwner.getUserName());
        run.setPlatform(TEST_PLATFORM);

        Map<SystemParams, String> systemParams = EnvVarsBuilderTest.matchSystemParams();
        PipelineConfiguration configuration = EnvVarsBuilderTest.matchPipeConfig();
        EnvVarsBuilder.buildEnvVars(run, configuration, systemParams, null);
        run.setEnvVars(run.getEnvVars());
        setRunInstance(run);
        pipelineRunDao.createPipelineRun(run);
        return run;
    }

    private void setRunInstance(final PipelineRun run) {
        run.setInstance(buildRunInstance());
    }

    private RunInstance buildRunInstance() {
        final RunInstance runInstance = new RunInstance();
        runInstance.setCloudProvider(CloudProvider.AWS);
        runInstance.setCloudRegionId(1L);
        runInstance.setNodePlatform(TEST_PLATFORM);
        return runInstance;
    }

    private Pipeline createPipeline(PipelineUser testOwner) {
        Pipeline pipeline = new Pipeline();
        pipeline.setName("testPipeline");
        pipeline.setRepository("testRepo");
        pipeline.setRepositorySsh("testRepoSsh");
        pipeline.setOwner(testOwner.getUserName());
        pipelineDao.createPipeline(pipeline);
        return pipeline;
    }

    private Issue createIssue(PipelineUser author, AbstractSecuredEntity entity) {
        Issue issue = new Issue();
        issue.setName("testIssue");
        issue.setText("Notifying @TestUser1, @TestUser2, also add @admin here");
        issue.setAuthor(author.getUserName());
        issue.setEntity(new EntityVO(entity.getId(), entity.getAclClass()));
        return issue;
    }
}
