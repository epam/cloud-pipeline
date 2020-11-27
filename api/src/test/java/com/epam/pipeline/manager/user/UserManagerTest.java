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

package com.epam.pipeline.manager.user;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.controller.vo.PipelineUserExportVO;
import com.epam.pipeline.controller.vo.region.AWSRegionDTO;
import com.epam.pipeline.dao.notification.MonitoringNotificationDao;
import com.epam.pipeline.entity.SecuredEntityWithAction;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.StorageServiceType;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationTemplate;
import com.epam.pipeline.entity.user.GroupStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.epam.pipeline.entity.user.PipelineUserWithStoragePath.PipelineUserFields.*;

public class UserManagerTest extends AbstractSpringTest {

    private static final String TEST_USER = "TestUser";
    private static final String SUBJECT = "Subject";
    private static final String BODY = "Body";
    private static final List<Long> DEFAULT_USER_ROLES = Collections.singletonList(2L);
    private static final String TEST_GROUP_NAME_1 = "test_group_1";
    private static final String TEST_GROUP_NAME_2 = "test_group_2";
    private static final List<String> DEFAULT_USER_GROUPS = Collections.singletonList(TEST_GROUP_NAME_1);
    private static final Map<String, String> DEFAULT_USER_ATTRIBUTE = Collections.emptyMap();
    private static final String ROLE_USER = "ROLE_USER";
    private static final String CSV_SEPARATOR = ",";
    private static final String USER_DEFAULT_DS = "user-default-ds";
    private static final String REGION_CODE = "eu-central-1";
    private static final String REGION_NAME = "aws_region";

    @Autowired
    private UserManager userManager;

    @Autowired
    private DataStorageManager dataStorageManager;

    @Autowired
    private CloudRegionManager cloudRegionManager;

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
        Assert.assertNull(newUser.getDefaultStorageId());
        Assert.assertNotNull(newUser.getRegistrationDate());
        Assert.assertNull(newUser.getFirstLoginDate());
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
    public void exportUsers() {
        final PipelineUser newUser = createDefaultPipelineUser();
        PipelineUserExportVO attr = new PipelineUserExportVO();
        attr.setIncludeId(true);
        attr.setIncludeUserName(true);

        String[] exported = new String(userManager.exportUsers(attr)).split("\n");
        Assert.assertEquals(2, exported.length);
        Assert.assertTrue(
                Arrays.stream(exported).anyMatch(
                    s -> ("" + newUser.getId() + CSV_SEPARATOR + newUser.getUserName()).equals(s)
                )
        );

        attr.setIncludeHeader(true);
        exported = new String(userManager.exportUsers(attr)).split("\n");
        Assert.assertEquals(3, exported.length);
        Assert.assertEquals(ID.getValue() + CSV_SEPARATOR + USER_NAME.getValue(), exported[0]);

        attr.setIncludeRoles(true);
        exported = new String(userManager.exportUsers(attr)).split("\n");
        Assert.assertEquals(3, exported.length);
        Assert.assertEquals(
                ID.getValue() + CSV_SEPARATOR + USER_NAME.getValue() + CSV_SEPARATOR + ROLES.getValue(),
                exported[0]
        );
        Assert.assertTrue(Arrays.stream(exported).anyMatch(s -> s.contains(ROLE_USER)));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void exportUsersWithDataStorage() {
        final PipelineUser userWithDS = createPipelineUserWithDataStorage();
        PipelineUserExportVO attr = new PipelineUserExportVO();
        attr.setIncludeDataStorage(true);
        attr.setIncludeHeader(true);
        attr.setIncludeId(true);
        final String[] exported = new String(userManager.exportUsers(attr)).split("\n");
        Assert.assertEquals(3, exported.length);
        Assert.assertEquals(
                ID.getValue() + CSV_SEPARATOR +
                        DEFAULT_STORAGE_ID.getValue() + CSV_SEPARATOR +
                        DEFAULT_STORAGE_PATH.getValue(),
                exported[0]);
        Assert.assertTrue(Arrays.stream(exported).anyMatch(
            s -> s.contains("" + userWithDS.getDefaultStorageId() + CSV_SEPARATOR + USER_DEFAULT_DS))
        );
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
    public void updateUserLoginDate() {
        final PipelineUser user = createDefaultPipelineUser();
        Assert.assertNull(user.getFirstLoginDate());

        userManager.updateUserFirstLoginDate(user.getId(), DateUtils.nowUTC());
        final PipelineUser loaded = userManager.loadUserById(user.getId());

        Assert.assertNotNull(loaded.getFirstLoginDate());
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
        Assert.assertNotNull(userManager.upsertGroupBlockingStatus(TEST_GROUP_NAME_1, false));
        Assert.assertFalse(getGroupStatus(TEST_GROUP_NAME_1).isBlocked());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateGroupStatus() {
        userManager.upsertGroupBlockingStatus(TEST_GROUP_NAME_1, false);
        Assert.assertFalse(getGroupStatus(TEST_GROUP_NAME_1).isBlocked());
        userManager.upsertGroupBlockingStatus(TEST_GROUP_NAME_1, true);
        Assert.assertTrue(getGroupStatus(TEST_GROUP_NAME_1).isBlocked());
        userManager.upsertGroupBlockingStatus(TEST_GROUP_NAME_1, false);
        Assert.assertFalse(getGroupStatus(TEST_GROUP_NAME_1).isBlocked());
    }

    private GroupStatus getGroupStatus(final String groupName) {
        return userManager.loadGroupBlockingStatus(Collections.singletonList(groupName))
                          .stream()
                          .findFirst()
                          .orElse(null);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteGroupStatus() {
        userManager.upsertGroupBlockingStatus(TEST_GROUP_NAME_1, false);
        Assert.assertFalse(getGroupStatus(TEST_GROUP_NAME_1).isBlocked());
        userManager.deleteGroupBlockingStatus(TEST_GROUP_NAME_1);
        Assert.assertNull(getGroupStatus(TEST_GROUP_NAME_1));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void loadAllGroupsStatuses() {
        userManager.upsertGroupBlockingStatus(TEST_GROUP_NAME_1, false);
        userManager.upsertGroupBlockingStatus(TEST_GROUP_NAME_2, true);
        final Map<String, Boolean> groupsStatuses = userManager.loadAllGroupsBlockingStatuses()
            .stream()
            .collect(Collectors.toMap(GroupStatus::getGroupName, GroupStatus::isBlocked));
        Assert.assertEquals(2, groupsStatuses.size());
        Assert.assertFalse(groupsStatuses.get(TEST_GROUP_NAME_1));
        Assert.assertTrue(groupsStatuses.get(TEST_GROUP_NAME_2));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void loadGroupStatusForNonexistentGroup() {
        Assert.assertNull(getGroupStatus(TEST_GROUP_NAME_1));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void loadGroupStatusForEmptyGroupList() {
        Assert.assertTrue(userManager.loadGroupBlockingStatus(Collections.emptyList()).isEmpty());
        Assert.assertTrue(userManager.loadGroupBlockingStatus(null).isEmpty());
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
                                      null);
    }

    private PipelineUser createPipelineUserWithDataStorage() {
        DataStorageVO dataStorageVO = new DataStorageVO();
        dataStorageVO.setServiceType(StorageServiceType.OBJECT_STORAGE);
        AWSRegionDTO regionDTO = new AWSRegionDTO();
        regionDTO.setRegionCode(REGION_CODE);
        regionDTO.setName(REGION_NAME);
        dataStorageVO.setRegionId(cloudRegionManager.create(regionDTO).getId());
        dataStorageVO.setPath(USER_DEFAULT_DS);
        dataStorageVO.setName(USER_DEFAULT_DS);
        SecuredEntityWithAction<AbstractDataStorage> ds =
                dataStorageManager.create(dataStorageVO, false, false, false);
        return userManager.createUser(TEST_USER,
                DEFAULT_USER_ROLES,
                DEFAULT_USER_GROUPS,
                DEFAULT_USER_ATTRIBUTE,
                ds.getEntity().getId());
    }
}
