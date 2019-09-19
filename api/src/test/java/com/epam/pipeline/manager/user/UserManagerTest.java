/*
 *
 *  * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.epam.pipeline.manager.user;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.dao.notification.MonitoringNotificationDao;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationTemplate;
import com.epam.pipeline.entity.user.GroupStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class UserManagerTest extends AbstractSpringTest {

    private static final String TEST_USER = "TestUser";
    private static final String SUBJECT = "Subject";
    private static final String BODY = "Body";
    private static final Long DEFAULT_STORAGE_ID = null;
    private static final List<Long> DEFAULT_USER_ROLES = Collections.singletonList(2L);
    private static final String TEST_GROUP_NAME_1 = "test_group_1";
    private static final String NOT_EXISTENT_GROUP = "not_exists";
    private static final List<String> DEFAULT_USER_GROUPS = Collections.singletonList(TEST_GROUP_NAME_1);
    private static final Map<String, String> DEFAULT_USER_ATTRIBUTE = Collections.emptyMap();

    @Autowired
    private UserManager userManager;

    @Autowired
    private MonitoringNotificationDao notificationDao;

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createUser() {
        Assert.assertNull(userManager.loadUserByName(TEST_USER));
        final PipelineUser newUser = createDefaultPipelineUser();
        Assert.assertEquals(TEST_USER.toUpperCase(), newUser.getUserName());
        Assert.assertEquals(DEFAULT_USER_GROUPS, newUser.getGroups());
        Assert.assertEquals(DEFAULT_USER_ATTRIBUTE, newUser.getAttributes());
        Assert.assertEquals(DEFAULT_USER_ROLES, newUser.getRoles()
                                                       .stream()
                                                       .map(Role::getId)
                                                       .collect(Collectors.toList()));
        Assert.assertEquals(DEFAULT_STORAGE_ID, newUser.getDefaultStorageId());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void readUser() {
        Assert.assertNull(userManager.loadUserByName(TEST_USER));
        final PipelineUser newUser = createDefaultPipelineUser();
        final PipelineUser loadedUser = userManager.loadUserById(newUser.getId());
        compareAllFieldOfUsers(newUser, loadedUser);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateUser() {
        final PipelineUser user = createDefaultPipelineUser();
        Assert.assertFalse(user.isBlocked());

        userManager.updateUserBlockingStatus(user.getId(), true);
        final PipelineUser blockedPipelineUser = userManager.loadUserById(user.getId());
        Assert.assertTrue(blockedPipelineUser.isBlocked());

        userManager.updateUserBlockingStatus(user.getId(), false);
        final PipelineUser unblockedPipelineUser = userManager.loadUserById(user.getId());
        Assert.assertFalse(unblockedPipelineUser.isBlocked());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteUser() {
        final PipelineUser user = createDefaultPipelineUser();

        final NotificationMessage message = new NotificationMessage();
        final NotificationTemplate template = new NotificationTemplate();
        template.setSubject(SUBJECT);
        template.setBody(BODY);
        message.setTemplate(template);
        message.setTemplateParameters(Collections.emptyMap());
        message.setToUserId(user.getId());
        message.setCopyUserIds(Collections.singletonList(user.getId()));

        notificationDao.createMonitoringNotification(message);
        Assert.assertFalse(notificationDao.loadAllNotifications().isEmpty());
        userManager.deleteUser(user.getId());
        Assert.assertTrue(notificationDao.loadAllNotifications().isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createGroupStatus() {
        Assert.assertNotNull(userManager.upsertGroupBlockingStatus(TEST_GROUP_NAME_1, false, true));
        Assert.assertFalse(getGroupStatus(TEST_GROUP_NAME_1).isBlocked());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateGroupStatus() {
        userManager.upsertGroupBlockingStatus(TEST_GROUP_NAME_1, false, true);
        Assert.assertFalse(getGroupStatus(TEST_GROUP_NAME_1).isBlocked());
        userManager.upsertGroupBlockingStatus(TEST_GROUP_NAME_1, true, true);
        Assert.assertTrue(getGroupStatus(TEST_GROUP_NAME_1).isBlocked());
        userManager.upsertGroupBlockingStatus(TEST_GROUP_NAME_1, false, true);
        Assert.assertFalse(getGroupStatus(TEST_GROUP_NAME_1).isBlocked());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteGroupStatus() {
        userManager.upsertGroupBlockingStatus(TEST_GROUP_NAME_1, false, true);
        Assert.assertFalse(getGroupStatus(TEST_GROUP_NAME_1).isBlocked());
        userManager.deleteGroupBlockingStatus(TEST_GROUP_NAME_1, true);
        Assert.assertNull(getGroupStatus(TEST_GROUP_NAME_1));
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteShouldThrowWhenGroupNotExists() {
        userManager.deleteGroupBlockingStatus(NOT_EXISTENT_GROUP, true);
    }

    @Test(expected = IllegalStateException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteShouldThrowWhenGroupIsEmpty() {
        userManager.deleteGroupBlockingStatus("", true);
    }


    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void loadGroupStatusForNonexistentGroup() {
        Assert.assertNull(getGroupStatus(NOT_EXISTENT_GROUP));
    }

    private GroupStatus getGroupStatus(final String groupName) {
        return userManager.loadGroupsBlockingStatus(Collections.singletonList(groupName))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private void compareAllFieldOfUsers(PipelineUser firstUser, PipelineUser secondUser) {
        Assert.assertEquals(firstUser.getUserName(), secondUser.getUserName());
        Assert.assertEquals(firstUser.getEmail(), secondUser.getEmail());
        Assert.assertEquals(firstUser.getId(), secondUser.getId());
        Assert.assertEquals(firstUser.getGroups(), secondUser.getGroups());
        Assert.assertEquals(firstUser.getAttributes(), secondUser.getAttributes());
        Assert.assertEquals(firstUser.getAuthorities(), secondUser.getAuthorities());
        Assert.assertEquals(firstUser.getRoles(), secondUser.getRoles());
        Assert.assertEquals(firstUser.getDefaultStorageId(), secondUser.getDefaultStorageId());
    }

    private PipelineUser createDefaultPipelineUser() {
        return userManager.createUser(TEST_USER,
                                      DEFAULT_USER_ROLES,
                                      DEFAULT_USER_GROUPS,
                                      DEFAULT_USER_ATTRIBUTE,
                                      DEFAULT_STORAGE_ID);
    }
}
