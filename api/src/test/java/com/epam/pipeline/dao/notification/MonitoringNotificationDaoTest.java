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

package com.epam.pipeline.dao.notification;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.dao.pipeline.PipelineDao;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.dao.user.UserDao;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationTemplate;
import com.epam.pipeline.entity.pipeline.CommitStatus;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.mapper.PipelineRunMapper;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Transactional
public class MonitoringNotificationDaoTest extends AbstractSpringTest {

    private static final String TEST_USER1 = "test_user1";
    private static final String TEST_POD_ID = "pod1";
    private static final String TEST_PARAMS = "123 321";
    private static final String SUBJECT_STRING = "Subject";
    private static final String BODY_STRING = "Body";
    private static final long TEST_THRESHOLD = 10L;
    private static final String TEMPLATE_PARAMETER = "template_parameter";
    private static final String TEMPLATE_PARAMETER_VALUE = "template_parameter_value";

    @Autowired
    private MonitoringNotificationDao monitoringNotificationDao;

    @Autowired
    private NotificationTemplateDao notificationTemplateDao;

    @Autowired
    private UserDao userDao;

    @Autowired
    private PipelineDao pipelineDao;

    @Autowired
    private PipelineRunDao pipelineRunDao;

    @Autowired
    private CloudRegionDao cloudRegionDao;

    private PipelineUser user;
    private Pipeline testPipeline;
    private NotificationMessage notificationMessage;
    private NotificationTemplate template;
    private AbstractCloudRegion region;


    @Before
    public void setUp() {
        user = new PipelineUser();
        user.setUserName(TEST_USER1);
        userDao.createUser(user,
                Arrays.asList(DefaultRoles.ROLE_ADMIN.getId(), DefaultRoles.ROLE_USER.getId()));

        testPipeline = new Pipeline();
        testPipeline.setName("Test");
        testPipeline.setRepository("///");
        testPipeline.setOwner(TEST_USER1);
        pipelineDao.createPipeline(testPipeline);

        template = new NotificationTemplate();
        template.setId(1L);
        template.setSubject(SUBJECT_STRING);
        template.setBody(BODY_STRING);
        region = ObjectCreatorUtils.getDefaultAwsRegion();
        cloudRegionDao.create(region);
    }

    @Test
    public void testLoadMonitoringNotification() {
        PipelineRun run1 = createTestPipelineRun();
        List<PipelineRun> pipelineRuns =
                pipelineRunDao.loadPipelineRuns(Collections.singletonList(run1.getId()));

        NotificationTemplate notificationTemplate = notificationTemplateDao.createNotificationTemplate(template);

        notificationMessage = new NotificationMessage();
        notificationMessage.setTemplate(notificationTemplate);

        PipelineUser pipelineUser = userDao.loadUserByName(TEST_USER1);
        notificationMessage.setToUserId(pipelineUser.getId());
        notificationMessage.setCopyUserIds(Collections.singletonList(pipelineUser.getId()));
        notificationMessage.setTemplateParameters(PipelineRunMapper.map(pipelineRuns.get(0), TEST_THRESHOLD));

        monitoringNotificationDao.createMonitoringNotification(notificationMessage);

        NotificationMessage actualMessage = monitoringNotificationDao
                .loadMonitoringNotification(notificationMessage.getId());
        assertEquals(notificationMessage.getId(), actualMessage.getId());
        assertEquals(notificationMessage.getTemplate().getId(), actualMessage.getTemplate().getId());
        assertEquals(user.getId(), notificationMessage.getToUserId());
        assertFalse(notificationMessage.getCopyUserIds().isEmpty());

        notificationMessage.getTemplateParameters().values().removeIf(Objects::isNull);
        notificationMessage.getTemplateParameters()
            .forEach((k, v) -> {
                assertTrue(actualMessage.getTemplateParameters().containsKey(k));
                assertEquals(v.toString(), actualMessage.getTemplateParameters().get(k).toString());
            });
    }

    private PipelineRun createTestPipelineRun() {
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
        run.setOwner(TEST_USER1);
        run.setParentRunId(null);
        run.setRunSids(null);
        run.setChildRuns(null);

        RunInstance instance = new RunInstance();
        instance.setSpot(true);
        instance.setNodeId("1");
        instance.setCloudProvider(CloudProvider.AWS);
        instance.setCloudRegionId(region.getId());
        run.setInstance(instance);
        run.setEntitiesIds(null);
        run.setConfigurationId(null);

        pipelineRunDao.createPipelineRun(run);
        return run;
    }

    @Test
    public void testCreateMonitoringNotifications() {
        NotificationTemplate notificationTemplate = notificationTemplateDao.createNotificationTemplate(template);
        PipelineUser pipelineUser = userDao.loadUserByName(TEST_USER1);

        List<NotificationMessage> messages = IntStream.range(0, 5).mapToObj(i -> {
            NotificationMessage notificationMessage = new NotificationMessage();
            notificationMessage.setTemplate(notificationTemplate);

            notificationMessage.setToUserId(pipelineUser.getId());
            notificationMessage.setCopyUserIds(Collections.singletonList(pipelineUser.getId()));
            notificationMessage.setTemplateParameters(Collections.singletonMap("test", i));

            return notificationMessage;
        }).collect(Collectors.toList());

        monitoringNotificationDao.createMonitoringNotifications(messages);

        List<NotificationMessage> loaded = monitoringNotificationDao.loadAllNotifications();
        assertTrue(messages.size() <= loaded.size());
        assertTrue(loaded.stream()
                              .allMatch(l -> messages.stream()
                                  .anyMatch(m -> m.getTemplateParameters().equals(l.getTemplateParameters()))));
    }

    @Test
    public void testCreateAndLoadCustomMonitoringNotifications() {
        PipelineUser pipelineUser = userDao.loadUserByName(TEST_USER1);

        NotificationMessage message = new NotificationMessage();
        message.setBody(BODY_STRING);
        message.setSubject(SUBJECT_STRING);
        message.setToUserId(pipelineUser.getId());
        message.setCopyUserIds(Collections.emptyList());
        message.setTemplateParameters(Collections.singletonMap(TEMPLATE_PARAMETER, TEMPLATE_PARAMETER_VALUE));

        monitoringNotificationDao.createMonitoringNotification(message);

        NotificationMessage singleLoadedMessage = monitoringNotificationDao.loadMonitoringNotification(message.getId());
        assertEquals(singleLoadedMessage.getBody(), BODY_STRING);
        assertEquals(singleLoadedMessage.getSubject(), SUBJECT_STRING);
        assertEquals(singleLoadedMessage.getToUserId(), pipelineUser.getId());
        assertEquals(singleLoadedMessage.getCopyUserIds(), Collections.emptyList());
        assertNull(singleLoadedMessage.getTemplate());

        NotificationMessage batchLoadedMessage = monitoringNotificationDao.loadAllNotifications().stream()
                .filter(m -> m.getId().equals(message.getId()))
                .findFirst()
                .get();
        assertEquals(singleLoadedMessage, batchLoadedMessage);
    }
}
