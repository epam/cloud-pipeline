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
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.StorageServiceType;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationTemplate;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.user.GroupStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.StorageProviderManager;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.security.acl.JdbcMutableAclServiceImpl;
import com.epam.pipeline.util.TestUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.util.ReflectionTestUtils;
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
    private static final String PARENT_FOLDER_NAME = "parentFolder";
    private static final String STORAGE_TEMPLATE = "{ \n"
                                           + "    \"datastorage\": {\n"
                                           + "        \"parentFolderId\": <folder_id>,\n"
                                           + "        \"name\": \"@@-home\",\n"
                                           + "        \"description\": \"Home folder for user @@\"\n"
                                           + "    },\n"
                                           + "    \"permissions\": [],\n"
                                           + "    \"metadata\": {}\n"
                                           + "}";
    @Autowired
    private UserManager userManager;

    @Autowired
    private CloudRegionManager cloudRegionManager;

    @Autowired
    private MonitoringNotificationDao notificationDao;

    @Autowired
    private FolderManager folderManager;

    @Autowired
    private GrantPermissionManager permissionManager;

    @SpyBean
    private DataStorageManager dataStorageManager;

    @SpyBean
    private StorageProviderManager storageProviderManager;

    @SpyBean
    private PreferenceManager preferenceManager;

    @Before
    public void setUpPreferenceManager() {
        ReflectionTestUtils.setField(userManager, "preferenceManager", preferenceManager);
        Mockito.when(preferenceManager.getPreference(SystemPreferences.DEFAULT_USER_DATA_STORAGE_ENABLED))
            .thenReturn(false);
    }

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
        Assert.assertEquals(1, exported.length);
        Assert.assertTrue(
                Arrays.stream(exported).anyMatch(
                    s -> ("" + newUser.getId() + CSV_SEPARATOR + newUser.getUserName()).equals(s)
                )
        );

        attr.setIncludeHeader(true);
        exported = new String(userManager.exportUsers(attr)).split("\n");
        Assert.assertEquals(2, exported.length);
        Assert.assertEquals(ID.getValue() + CSV_SEPARATOR + USER_NAME.getValue(), exported[0]);

        attr.setIncludeRoles(true);
        exported = new String(userManager.exportUsers(attr)).split("\n");
        Assert.assertEquals(2, exported.length);
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
        Assert.assertEquals(2, exported.length);
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

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createUserAndDefaultStorage() {
        final Folder folder = new Folder();
        folder.setName(PARENT_FOLDER_NAME);
        final Folder parentFolder = folderManager.create(folder);
        final Preference preference = SystemPreferences.DEFAULT_USER_DATA_STORAGE_TEMPLATE.toPreference();
        preference.setValue(STORAGE_TEMPLATE.replace("<folder_id>", parentFolder.getId().toString()));
        preferenceManager.update(Collections.singletonList(preference));
        prepareContextForDefaultUserStorage();
        mockDataStorageManagerToExecuteTryInitDefaultStorage();
        final PipelineUser newUser = createDefaultPipelineUser();
        assertDefaultStorage(newUser, parentFolder.getId());
        restoreDataStorageManager();
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createUserAndDefaultStorageWhenParentFolderDoesntExists() {
        prepareContextForDefaultUserStorage();
        mockDataStorageManagerToExecuteTryInitDefaultStorage();
        final PipelineUser newUser = createDefaultPipelineUser();
        assertDefaultStorage(newUser, null);
        restoreDataStorageManager();
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createUserAndDefaultStorageWhenDefaultStorageIdIsSpecifiedExplicitly() {
        final String expectedDefaultUserStorageName =
            TestUtils.DEFAULT_STORAGE_NAME_PATTERN.replace(TestUtils.TEMPLATE_REPLACE_MARK, TEST_USER);
        createAwsRegion(REGION_NAME, REGION_CODE);
        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(expectedDefaultUserStorageName,
                                                                            null, DataStorageType.S3,
                                                                            expectedDefaultUserStorageName,
                                                                            null, null,
                                                                            null);
        final AbstractDataStorage storage = dataStorageManager.create(storageVO, false, false, false).getEntity();
        prepareContextForDefaultUserStorage();
        final PipelineUser newUser = userManager.createUser(TEST_USER, DEFAULT_USER_ROLES, DEFAULT_USER_GROUPS,
                                                            DEFAULT_USER_ATTRIBUTE, storage.getId());
        Assert.assertEquals(storage.getId(), newUser.getDefaultStorageId());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createUserAndDefaultStorageWhenExplicitDefaultStorageDoesntExist() {
        final String expectedDefaultUserStorageName =
            TestUtils.DEFAULT_STORAGE_NAME_PATTERN.replace(TestUtils.TEMPLATE_REPLACE_MARK, TEST_USER);
        createAwsRegion(REGION_NAME, REGION_CODE);
        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(expectedDefaultUserStorageName,
                                                                            null, DataStorageType.S3,
                                                                            expectedDefaultUserStorageName,
                                                                            null, null,
                                                                            null);
        final Long removedId = dataStorageManager.create(storageVO, false, false, false).getEntity().getId();
        dataStorageManager.delete(removedId, false);
        prepareContextForDefaultUserStorage();
        final PipelineUser newUser = userManager.createUser(TEST_USER, DEFAULT_USER_ROLES, DEFAULT_USER_GROUPS,
                                                            DEFAULT_USER_ATTRIBUTE, removedId);
        Assert.assertNull(newUser.getDefaultStorageId());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createUserAndDefaultStorageWhenCorrespondingStorageExistsAlready() {
        final String expectedDefaultUserStorageName =
            TestUtils.DEFAULT_STORAGE_NAME_PATTERN.replace(TestUtils.TEMPLATE_REPLACE_MARK, TEST_USER);
        createAwsRegion(REGION_NAME, REGION_CODE);
        DataStorageVO storageVO = ObjectCreatorUtils.constructDataStorageVO(expectedDefaultUserStorageName,
                                                                            null, DataStorageType.S3,
                                                                            expectedDefaultUserStorageName,
                                                                            null, null,
                                                                            null);
        dataStorageManager.create(storageVO, false, false, false);
        prepareContextForDefaultUserStorage();
        final PipelineUser newUser = createDefaultPipelineUser();
        Assert.assertNull(newUser.getDefaultStorageId());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createUserErrorAtDefaultStorageCreation() {
        prepareContextForDefaultUserStorage();
        Mockito.doThrow(IllegalArgumentException.class)
            .when(dataStorageManager)
            .createDefaultStorageForUser(Mockito.anyString());
        createDefaultPipelineUser();
        final PipelineUser newUser = userManager.loadUserByName(TEST_USER);
        Assert.assertNotNull(newUser);
        Assert.assertEquals(TEST_USER.toUpperCase(), newUser.getUserName());
        Assert.assertNull(newUser.getDefaultStorageId());
        Assert.assertTrue(CollectionUtils.isEmpty(folderManager.loadAllProjects().getChildFolders()));
    }

    private void prepareContextForDefaultUserStorage() {
        Mockito.when(preferenceManager.getPreference(SystemPreferences.DEFAULT_USER_DATA_STORAGE_ENABLED))
            .thenReturn(true);
        Mockito.doReturn(true).when(storageProviderManager).checkStorage(Mockito.any());
        final JdbcMutableAclServiceImpl aclService = Mockito.mock(JdbcMutableAclServiceImpl.class);
        Mockito.doNothing().when(aclService).changeOwner(Mockito.any(), Mockito.anyString());
        ReflectionTestUtils.setField(permissionManager, "aclService", aclService);
        createAwsRegion(REGION_NAME, REGION_CODE);
        Mockito.doAnswer(invocation -> {
            final Object[] args = invocation.getArguments();
            return dataStorageManager.create((DataStorageVO) args[0], false, (boolean) args[2], (boolean) args[3]);
        }).when(dataStorageManager)
            .create(Mockito.any(), Matchers.eq(true), Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    private void mockDataStorageManagerToExecuteTryInitDefaultStorage() {
        final DataStorageManager dataStorageManagerMock = Mockito.mock(DataStorageManager.class);
        ReflectionTestUtils.setField(userManager, "dataStorageManager", dataStorageManagerMock);
        Mockito.doAnswer(invocation -> {
            final Object[] args = invocation.getArguments();
            final PipelineUser user = (PipelineUser) args[0];
            return dataStorageManager.createDefaultStorageForUser(user.getUserName()).map(AbstractDataStorage::getId);
        }).when(dataStorageManagerMock)
            .tryInitUserDefaultStorage(Mockito.any());
    }

    private void restoreDataStorageManager() {
        ReflectionTestUtils.setField(userManager, "dataStorageManager", dataStorageManager);
    }

    private void assertDefaultStorage(final PipelineUser user, final Long defaultFolderId) {
        final AbstractDataStorage defaultStorage = dataStorageManager.load(user.getDefaultStorageId());
        final String userName = user.getUserName();
        final String expectedStorageName =
            TestUtils.DEFAULT_STORAGE_NAME_PATTERN.replace(TestUtils.TEMPLATE_REPLACE_MARK, userName.toUpperCase());
        Assert.assertEquals(expectedStorageName, defaultStorage.getName());
        Assert.assertEquals(defaultFolderId, defaultStorage.getParentFolderId());
        Assert.assertEquals(userName, defaultStorage.getOwner());
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
        AbstractCloudRegion region = createAwsRegion(REGION_NAME, REGION_CODE);
        dataStorageVO.setRegionId(region.getId());
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

    private AbstractCloudRegion createAwsRegion(final String regionName, final String regionCode) {
        final AWSRegionDTO regionDTO = new AWSRegionDTO();
        regionDTO.setName(regionName);
        regionDTO.setRegionCode(regionCode);
        regionDTO.setProvider(CloudProvider.AWS);
        regionDTO.setDefault(true);
        return cloudRegionManager.create(regionDTO);
    }
}
