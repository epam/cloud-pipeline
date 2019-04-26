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

package com.epam.pipeline.manager.notification;

import static com.epam.pipeline.entity.notification.NotificationSettings.NotificationType;
import static com.epam.pipeline.entity.notification.NotificationSettings.NotificationType.*;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.*;

import com.epam.pipeline.controller.vo.notification.NotificationMessageVO;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.*;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.execution.EnvVarsBuilder;
import com.epam.pipeline.manager.execution.EnvVarsBuilderTest;
import com.epam.pipeline.manager.execution.SystemParams;
import com.epam.pipeline.manager.pipeline.PipelineManager;
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

    @Autowired
    private NotificationManager notificationManager;

    @Autowired
    private PodMonitor podMonitor;

    @MockBean
    private PipelineRunManager pipelineRunManager;

    @Autowired
    private PipelineRunDao pipelineRunDao;

    @Autowired
    private PipelineManager pipelineManager;

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

    private PipelineRun longRunnging;
    private PipelineUser admin;
    private PipelineUser testOwner;
    private PipelineUser testUser1;
    private PipelineUser testUser2;
    private NotificationTemplate longRunningTemplate;
    private NotificationTemplate issueTemplate;
    private NotificationTemplate issueCommentTemplate;
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
        issueCommentSettings = createSettings(NEW_ISSUE_COMMENT, issueCommentTemplate.getId(), -1L,
                                              -1L);
        createTemplate(IDLE_RUN.getId(), "idle-run-template");
        createSettings(IDLE_RUN, IDLE_RUN.getId(), -1, -1);

        createTemplate(HIGH_CONSUMED_RESOURCES.getId(), "idle-run-template");
        highConsuming = createSettings(HIGH_CONSUMED_RESOURCES, HIGH_CONSUMED_RESOURCES.getId(),
                HIGH_CONSUMED_RESOURCES.getDefaultThreshold(), HIGH_CONSUMED_RESOURCES.getDefaultResendDelay());

        longRunnging = new PipelineRun();
        DateTime date = DateTime.now(DateTimeZone.UTC).minus(Duration.standardMinutes(6));
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
        PodStatus podStatus = new PodStatus(null, null, "hostIp", "", "",
                                            "podIp", "bla-bla", "5 o'clock");
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
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
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
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testDoesntNotifyIfNotConfigure() {
        notificationSettingsDao.deleteNotificationSettingsById(longRunningSettings.getId());
        notificationTemplateDao.deleteNotificationTemplate(longRunningTemplate.getId());
        podMonitor.updateStatus();
        List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(0, messages.size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testNotifyNoOwner() {
        updateKeepInformedOwner(longRunningSettings, false);

        podMonitor.updateStatus();
        List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(1, messages.size());
        Assert.assertNull(messages.get(0).getToUserId());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
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
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testWontNotifyClusterNode() {
        podMetadata.setLabels(Collections.singletonMap("cluster_id", "some_id"));
        podMonitor.updateStatus();

        List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertTrue(messages.isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testWontNotifyAdminsIfConfigured() {
        NotificationSettings settings = notificationSettingsDao.loadNotificationSettings(1L);
        settings.setKeepInformedAdmins(false);
        notificationSettingsDao.updateNotificationSettings(settings);
        notificationManager.notifyLongRunningTask(longRunnging, settings);

        List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(1, messages.size());
        Assert.assertEquals(admin.getId(), messages.get(0).getToUserId());
        Assert.assertTrue(messages.get(0).getCopyUserIds().isEmpty());
        Assert.assertEquals(longRunningTemplate.getId(), messages.get(0).getTemplate().getId());

        settings.setKeepInformedAdmins(false);
        settings.setInformedUserIds(Collections.singletonList(userDao.loadUserByName("admin").getId()));
        notificationSettingsDao.updateNotificationSettings(settings);
        notificationManager.notifyLongRunningTask(longRunnging, settings);
        messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertTrue(messages.get(messages.size() - 1).getCopyUserIds().contains(admin.getId()));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
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

    private Pipeline createPipeline(PipelineUser testOwner) {
        Pipeline pipeline = new Pipeline();
        pipeline.setName("testPipeline");
        pipeline.setRepository("testRepo");
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

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
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
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testNotifyIdleRun() {
        PipelineRun run1 = new PipelineRun();
        run1.setOwner(testUser1.getUserName());
        run1.setStartDate(DateUtils.now());
        run1.setId(1L);
        PipelineRun run2 = new PipelineRun();
        run2.setStartDate(DateUtils.now());
        run2.setOwner(testUser2.getUserName());
        run2.setId(2L);

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
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testNotifyHighConsumingRun() {
        PipelineRun run1 = createTestPipelineRun();
        PipelineRun run2 = createTestPipelineRun();

        List<Pair<PipelineRun, Map<String, Double>>> pipelinesMetrics = Arrays.asList(
                new ImmutablePair<>(run1, Collections.singletonMap(ELKUsageMetric.MEM.getName(), TEST_MEMORY_RATE)),
                new ImmutablePair<>(run2, Collections.singletonMap(ELKUsageMetric.FS.getName(), TEST_DISK_RATE)));

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
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testRemoveNotificationTimestampWhenDelete() {
        Pipeline pipeline = createPipeline(testOwner);
        PipelineRun run1 = createTestPipelineRun(pipeline.getId());
        notificationManager.notifyHighResourceConsumingRuns(Collections.singletonList(
                new ImmutablePair<>(run1, Collections.singletonMap("memoryRate", TEST_MEMORY_RATE))),
                HIGH_CONSUMED_RESOURCES);

        Assert.assertNotNull(notificationManager.loadLastNotificationTimestamp(run1.getId(), HIGH_CONSUMED_RESOURCES));

        pipelineManager.delete(pipeline.getId(), true);

        Assert.assertNull(notificationManager.loadLastNotificationTimestamp(run1.getId(), HIGH_CONSUMED_RESOURCES));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void notifyHighConsumingRunOnlyOnceIfItIsSetup() {
        highConsuming.setResendDelay(-1L);
        notificationSettingsDao.updateNotificationSettings(highConsuming);

        PipelineRun run1 = createTestPipelineRun();
        List<Pair<PipelineRun, Map<String, Double>>> pipelinesMetrics = Collections.singletonList(
                new ImmutablePair<>(run1, Collections.singletonMap(ELKUsageMetric.MEM.getName(), TEST_MEMORY_RATE)));

        notificationManager.notifyHighResourceConsumingRuns(pipelinesMetrics, HIGH_CONSUMED_RESOURCES);

        List<NotificationMessage> messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(1, messages.size());

        monitoringNotificationDao.deleteNotificationsByTemplateId(HIGH_CONSUMED_RESOURCES.getId());

        notificationManager.notifyHighResourceConsumingRuns(pipelinesMetrics, HIGH_CONSUMED_RESOURCES);

        messages = monitoringNotificationDao.loadAllNotifications();
        Assert.assertEquals(0, messages.size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testCreateNotificationFailsIfSubjectIsNotSpecified() {
        final String userName = testUser1.getUserName();
        final NotificationMessageVO messageWithoutSubject = new NotificationMessageVO();
        messageWithoutSubject.setBody(BODY);
        messageWithoutSubject.setToUser(userName);

        assertThrows(IllegalArgumentException.class,
            () -> notificationManager.createNotification(messageWithoutSubject));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
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
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testCreateNotificationsFailsIfReceiverUserDoesNotExist() {
        final NotificationMessageVO messageWithNonExistingUser = new NotificationMessageVO();
        messageWithNonExistingUser.setBody(BODY);
        messageWithNonExistingUser.setSubject(SUBJECT);
        messageWithNonExistingUser.setToUser(NON_EXISTING_USER);

        assertThrows(IllegalArgumentException.class,
            () -> notificationManager.createNotification(messageWithNonExistingUser));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
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
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
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
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
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
        runInstance.setCloudRegionId(1L);
        run.setInstance(runInstance);
    }
}