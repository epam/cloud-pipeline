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

package com.epam.pipeline.dao.user;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

@Transactional
public class UserDaoTest extends AbstractSpringTest {

    private static final String TEST_USER1 = "test_user1";
    private static final String TEST_USER2 = "test_user2";
    private static final String TEST_USER3 = "test_user3";
    private static final List<String> TEST_GROUPS_1 = new ArrayList<>();
    private static final List<String> TEST_GROUPS_2 = new ArrayList<>();
    private static final String TEST_GROUP_1 = "test_group_1";
    private static final String TEST_GROUP_2 = "test_group_2";
    private static final String ATTRIBUTES_KEY = "email";
    private static final String ATTRIBUTES_VALUE = "test_email";
    private static final String ATTRIBUTES_VALUE2 = "Mail@epam.com";
    private static final int EXPECTED_DEFAULT_ROLES_NUMBER = 9;

    @Autowired
    private UserDao userDao;

    @Autowired
    private RoleDao roleDao;

    @Autowired
    private DataStorageDao dataStorageDao;

    @Value("${flyway.placeholders.default.admin}")
    private String defaultAdmin;

    @Test
    public void testSearchUserByPrefix() {
        PipelineUser user = new PipelineUser();
        user.setUserName(TEST_USER1);
        user.getAttributes().put(ATTRIBUTES_KEY, ATTRIBUTES_VALUE2);
        PipelineUser savedUser = userDao.createUser(user, Collections.emptyList());

        Collection<PipelineUser> userByNamePrefix = userDao.findUsers(TEST_USER1.substring(0, 2));
        Assert.assertEquals(1, userByNamePrefix.size());
        Assert.assertTrue(userByNamePrefix.stream().anyMatch(u -> u.getId().equals(savedUser.getId())));

        Collection<PipelineUser> userByAttrPrefix = userDao.findUsers(ATTRIBUTES_VALUE2.substring(0, 2));
        Assert.assertEquals(1, userByNamePrefix.size());
        Assert.assertTrue(userByAttrPrefix.stream().anyMatch(u -> u.getId().equals(savedUser.getId())));
    }

    @Test
    public void testUserCRUD() {
        PipelineUser user = new PipelineUser();
        user.setUserName(TEST_USER1);
        PipelineUser savedUser = userDao.createUser(user,
                Arrays.asList(DefaultRoles.ROLE_ADMIN.getId(), DefaultRoles.ROLE_USER.getId()));
        Assert.assertNotNull(savedUser);
        Assert.assertNotNull(savedUser.getId());

        Collection<PipelineUser> users = userDao.loadAllUsers();
        Assert.assertFalse(users.isEmpty());
        Assert.assertTrue(users.stream().anyMatch(u -> u.getId().equals(savedUser.getId())));

        PipelineUser userById = userDao.loadUserById(savedUser.getId());
        Assert.assertEquals(savedUser.getId(), userById.getId());

        PipelineUser userByName = userDao.loadUserByName(TEST_USER1.toUpperCase());
        Assert.assertEquals(savedUser.getId(), userByName.getId());

        savedUser.setUserName(TEST_USER2);
        userDao.updateUser(savedUser);
        PipelineUser userByChangedName = userDao.loadUserByName(TEST_USER2);
        Assert.assertEquals(savedUser.getId(), userByChangedName.getId());

        List<PipelineUser> loadedUsers = userDao.loadUsersByNames(Collections.singletonList(TEST_USER2));
        Assert.assertFalse(loadedUsers.isEmpty());

        userDao.updateUserRoles(savedUser, Collections.singletonList(DefaultRoles.ROLE_USER.getId()));
        PipelineUser userUpdatedRoles = userDao.loadUserByName(TEST_USER2);
        Assert.assertEquals(1, userUpdatedRoles.getRoles().size());
        Assert.assertEquals(DefaultRoles.ROLE_USER.name(),  userUpdatedRoles.getRoles().get(0).getName());

        deleteUserAndHisRoles(savedUser);

        Assert.assertNull(userDao.loadUserById(savedUser.getId()));
        Collection<PipelineUser> usersAfterDeletion = userDao.loadAllUsers();
        Assert.assertTrue(usersAfterDeletion.stream().noneMatch(u -> u.getId().equals(savedUser.getId())));
    }

    @Test
    public void testUserCRUDWithBlockingStatus() {
        final PipelineUser user = new PipelineUser();
        user.setUserName(TEST_USER1);
        final PipelineUser savedUser = userDao.createUser(user,
                                                    Arrays.asList(DefaultRoles.ROLE_ADMIN.getId(),
                                                                  DefaultRoles.ROLE_USER.getId()));
        Assert.assertFalse(savedUser.isBlocked());

        final PipelineUser userById = userDao.loadUserById(savedUser.getId());
        Assert.assertEquals(savedUser.getId(), userById.getId());
        Assert.assertFalse(userById.isBlocked());

        savedUser.setBlocked(true);
        userDao.updateUser(savedUser);
        final PipelineUser userByNameBlocked = userDao.loadUserByName(TEST_USER1.toUpperCase());
        Assert.assertEquals(savedUser.getId(), userByNameBlocked.getId());
        Assert.assertTrue(userByNameBlocked.isBlocked());

        deleteUserAndHisRoles(savedUser);
        Assert.assertNull(userDao.loadUserById(savedUser.getId()));

        final Collection<PipelineUser> usersAfterDeletion = userDao.loadAllUsers();
        Assert.assertTrue(usersAfterDeletion.stream().noneMatch(u -> u.getId().equals(savedUser.getId())));
    }

    private void deleteUserAndHisRoles(final PipelineUser savedUser) {
        userDao.deleteUserRoles(savedUser.getId());
        userDao.deleteUser(savedUser.getId());
    }

    @Test
    public void testUserGroups() {
        PipelineUser user1 = new PipelineUser();
        user1.setUserName(TEST_USER1);
        TEST_GROUPS_1.add(TEST_GROUP_1);
        user1.setGroups(TEST_GROUPS_1);
        PipelineUser savedUser = userDao.createUser(user1,
                Collections.singletonList(DefaultRoles.ROLE_USER.getId()));
        Assert.assertNotNull(savedUser);

        PipelineUser user2 = new PipelineUser();
        user2.setUserName(TEST_USER2);
        TEST_GROUPS_1.add(TEST_GROUP_2);
        user2.setGroups(TEST_GROUPS_1);
        PipelineUser savedUser2 = userDao.createUser(user2,
                Collections.singletonList(DefaultRoles.ROLE_USER.getId()));
        Assert.assertNotNull(savedUser2);

        PipelineUser user3 = new PipelineUser();
        user3.setUserName(TEST_USER3);
        TEST_GROUPS_2.add(TEST_GROUP_2);
        user3.setGroups(TEST_GROUPS_2);
        PipelineUser savedUser3 = userDao.createUser(user3,
                Arrays.asList(DefaultRoles.ROLE_ADMIN.getId(), DefaultRoles.ROLE_USER.getId()));
        Assert.assertNotNull(savedUser3);

        Collection<PipelineUser> userByGroup = userDao.loadUsersByGroup(TEST_GROUP_1);
        Assert.assertTrue(userByGroup.size() == 2);
        Assert.assertTrue(userByGroup.stream().anyMatch(u ->
                u.getUserName().equals(TEST_USER1)));
        Assert.assertTrue(userByGroup.stream().noneMatch(u ->
                u.getUserName().equals(TEST_USER3)));

        List<String> foundGroups = userDao.findGroups("TEST_");
        Assert.assertTrue(TEST_GROUPS_1.size() == foundGroups.size());
        Assert.assertTrue(TEST_GROUPS_1.containsAll(foundGroups));

        Assert.assertFalse(userDao.isUserInGroup(user1.getUserName(), "TEST_GROUP_5"));
        Assert.assertTrue(userDao.isUserInGroup(user1.getUserName(), TEST_GROUP_1));

        List<String> allLoadedGroups = userDao.loadAllGroups();
        Collections.sort(allLoadedGroups);
        Assert.assertEquals(TEST_GROUPS_1, allLoadedGroups);
    }

    @Test
    public void testDefaultAdmin() {
        PipelineUser admin = userDao.loadUserByName(defaultAdmin);
        Assert.assertNotNull(admin);
        Assert.assertEquals(defaultAdmin, admin.getUserName());
        Assert.assertTrue(admin.getId().equals(1L));
        Assert.assertEquals(1, admin.getRoles().size());
        Assert.assertTrue(isRolePresent(DefaultRoles.ROLE_ADMIN.getRole(), admin.getRoles()));

        Collection<Role> allRoles = roleDao.loadAllRoles(false);
        Assert.assertEquals(EXPECTED_DEFAULT_ROLES_NUMBER, allRoles.size());
        Assert.assertTrue(isRolePresent(DefaultRoles.ROLE_ADMIN.getRole(), allRoles));
        Assert.assertTrue(isRolePresent(DefaultRoles.ROLE_USER.getRole(), allRoles));
    }

    @Test
    public void testUserCRUDWithAttributes() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(ATTRIBUTES_KEY, ATTRIBUTES_VALUE);
        PipelineUser user = new PipelineUser();
        user.setUserName(TEST_USER1);
        user.setAttributes(attributes);
        PipelineUser savedUser = userDao.createUser(user,
                Arrays.asList(DefaultRoles.ROLE_ADMIN.getId(), DefaultRoles.ROLE_USER.getId()));
        Assert.assertNotNull(savedUser);
        Assert.assertNotNull(savedUser.getId());
        Assert.assertNotNull(savedUser.getAttributes());

        Collection<PipelineUser> users = userDao.loadAllUsers();
        Assert.assertFalse(users.isEmpty());
        Assert.assertTrue(users.stream()
                .anyMatch(u -> u.getId().equals(savedUser.getId())
                        && assertUserAttributes(attributes, u.getAttributes())));

        PipelineUser userById = userDao.loadUserById(savedUser.getId());
        Assert.assertEquals(savedUser.getId(), userById.getId());
        Assert.assertTrue(assertUserAttributes(attributes, userById.getAttributes()));

        PipelineUser userByName = userDao.loadUserByName(TEST_USER1.toUpperCase());
        Assert.assertEquals(savedUser.getId(), userByName.getId());
        Assert.assertTrue(assertUserAttributes(attributes, userByName.getAttributes()));

        savedUser.setUserName(TEST_USER2);
        userDao.updateUser(savedUser);
        PipelineUser userByChangedName = userDao.loadUserByName(TEST_USER2);
        Assert.assertEquals(savedUser.getId(), userByChangedName.getId());
        Assert.assertTrue(assertUserAttributes(attributes, userByChangedName.getAttributes()));

        deleteUserAndHisRoles(savedUser);

        Assert.assertNull(userDao.loadUserById(savedUser.getId()));
        Collection<PipelineUser> usersAfterDeletion = userDao.loadAllUsers();
        Assert.assertTrue(usersAfterDeletion.stream().noneMatch(u -> u.getId().equals(savedUser.getId())));
    }

    @Test
    public void shouldReturnUserByStorageId() {
        S3bucketDataStorage s3bucketDataStorage = ObjectCreatorUtils
                .createS3Bucket(null, "test", "test", TEST_USER1);
        dataStorageDao.createDataStorage(s3bucketDataStorage);
        PipelineUser user = new PipelineUser();
        user.setUserName(TEST_USER1);
        user.setDefaultStorageId(s3bucketDataStorage.getId());
        userDao.createUser(user,
                Arrays.asList(DefaultRoles.ROLE_ADMIN.getId(), DefaultRoles.ROLE_USER.getId()));
        Assert.assertThat(userDao.loadUsersByStorageId(s3bucketDataStorage.getId()), hasSize(1));

    }

    private boolean isRolePresent(Role roleToFind, Collection<Role> roles) {
        return roles.stream().anyMatch(r -> r.getName().equals(roleToFind.getName()));
    }

    private boolean assertUserAttributes(Map<String, String> expectedAttributes, Map<String, String> actualAttributes) {
        return CollectionUtils.isEqualCollection(expectedAttributes.entrySet(), actualAttributes.entrySet());
    }
}
