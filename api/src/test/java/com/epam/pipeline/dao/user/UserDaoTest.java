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

package com.epam.pipeline.dao.user;

import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.test.creator.user.UserCreatorUtils;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.apache.commons.collections4.CollectionUtils;
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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.Assert.*;

@Transactional
public class UserDaoTest extends AbstractJdbcTest {

    private static final String TEST_USER1 = "test_user1";
    private static final String TEST_USER2 = "test_user2";
    private static final String TEST_USER3 = "test_user3";
    public static final String USER_NAME = "userName";
    private static final String TEST_GROUP_1 = "test_group_1";
    private static final String TEST_GROUP_2 = "test_group_2";
    private static final String ATTRIBUTES_KEY = "email";
    private static final String ATTRIBUTES_VALUE = "test_email";
    private static final String ATTRIBUTES_VALUE2 = "Mail@epam.com";
    private static final int EXPECTED_DEFAULT_ROLES_NUMBER = 21;
    private static final String TEST_ROLE = "ROLE_TEST";

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
        PipelineUser user = UserCreatorUtils.getPipelineUser(TEST_USER1);

        user.getAttributes().put(ATTRIBUTES_KEY, ATTRIBUTES_VALUE2);
        PipelineUser savedUser = userDao.createUser(user, Collections.emptyList());

        Collection<PipelineUser> userByNamePrefix = userDao.findUsers(TEST_USER1.substring(0, 2));
        assertEquals(1, userByNamePrefix.size());
        assertTrue(userByNamePrefix.stream().anyMatch(u -> u.getId().equals(savedUser.getId())));

        Collection<PipelineUser> userByAttrPrefix = userDao.findUsers(ATTRIBUTES_VALUE2.substring(0, 2));
        assertEquals(1, userByNamePrefix.size());
        assertTrue(userByAttrPrefix.stream().anyMatch(u -> u.getId().equals(savedUser.getId())));
    }

    @Test
    public void testUserCRUD() {
        PipelineUser user = UserCreatorUtils.getPipelineUser(TEST_USER1);
        PipelineUser savedUser = userDao.createUser(user,
                Arrays.asList(DefaultRoles.ROLE_ADMIN.getId(), DefaultRoles.ROLE_USER.getId()));
        assertNotNull(savedUser);
        assertNotNull(savedUser.getId());

        Collection<PipelineUser> users = userDao.loadAllUsers();
        assertFalse(users.isEmpty());
        assertTrue(users.stream().anyMatch(u -> u.getId().equals(savedUser.getId())));

        PipelineUser userById = userDao.loadUserById(savedUser.getId());
        assertEquals(savedUser.getId(), userById.getId());

        PipelineUser userByName = userDao.loadUserByName(TEST_USER1.toUpperCase());
        assertEquals(savedUser.getId(), userByName.getId());

        savedUser.setUserName(TEST_USER2);
        userDao.updateUser(savedUser);
        PipelineUser userByChangedName = userDao.loadUserByName(TEST_USER2);
        assertEquals(savedUser.getId(), userByChangedName.getId());

        List<PipelineUser> loadedUsers = userDao.loadUsersByNames(Collections.singletonList(TEST_USER2));
        assertFalse(loadedUsers.isEmpty());

        userDao.updateUserRoles(savedUser, Collections.singletonList(DefaultRoles.ROLE_USER.getId()));
        PipelineUser userUpdatedRoles = userDao.loadUserByName(TEST_USER2);
        assertEquals(1, userUpdatedRoles.getRoles().size());
        assertEquals(DefaultRoles.ROLE_USER.name(),  userUpdatedRoles.getRoles().get(0).getName());

        deleteUserAndHisRoles(savedUser);

        assertNull(userDao.loadUserById(savedUser.getId()));
        Collection<PipelineUser> usersAfterDeletion = userDao.loadAllUsers();
        assertTrue(usersAfterDeletion.stream().noneMatch(u -> u.getId().equals(savedUser.getId())));
    }

    @Test
    public void testUserCRUDWithBlockingStatus() {
        final PipelineUser user = UserCreatorUtils.getPipelineUser(TEST_USER1);
        final PipelineUser savedUser = userDao.createUser(user,
                                                    Arrays.asList(DefaultRoles.ROLE_ADMIN.getId(),
                                                                  DefaultRoles.ROLE_USER.getId()));
        assertFalse(savedUser.isBlocked());

        final PipelineUser userById = userDao.loadUserById(savedUser.getId());
        assertEquals(savedUser.getId(), userById.getId());
        assertFalse(userById.isBlocked());

        savedUser.setBlocked(true);
        userDao.updateUser(savedUser);
        final PipelineUser userByNameBlocked = userDao.loadUserByName(TEST_USER1.toUpperCase());
        assertEquals(savedUser.getId(), userByNameBlocked.getId());
        assertTrue(userByNameBlocked.isBlocked());

        deleteUserAndHisRoles(savedUser);
        assertNull(userDao.loadUserById(savedUser.getId()));

        final Collection<PipelineUser> usersAfterDeletion = userDao.loadAllUsers();
        assertTrue(usersAfterDeletion.stream().noneMatch(u -> u.getId().equals(savedUser.getId())));
    }

    @Test
    public void testUpdateUserExternalBlockDate() {
        final PipelineUser user = UserCreatorUtils.getPipelineUser(TEST_USER1);
        final PipelineUser savedUser = userDao.createUser(user, Collections.singletonList(
                DefaultRoles.ROLE_USER.getId()));

        final PipelineUser userById = userDao.loadUserById(savedUser.getId());
        assertNull(userById.getExternalBlockDate());

        savedUser.setExternalBlockDate(DateUtils.nowUTC());
        userDao.updateUser(savedUser);
        final PipelineUser loadedUser1 = userDao.loadUserById(savedUser.getId());
        assertEquals(savedUser.getExternalBlockDate(), loadedUser1.getExternalBlockDate());

        savedUser.setExternalBlockDate(null);
        userDao.updateUser(savedUser);
        final PipelineUser loadedUser2 = userDao.loadUserById(savedUser.getId());
        assertEquals(savedUser.getExternalBlockDate(), loadedUser2.getExternalBlockDate());
    }

    private void deleteUserAndHisRoles(final PipelineUser savedUser) {
        userDao.deleteUserRoles(savedUser.getId());
        userDao.deleteUser(savedUser.getId());
    }

    @Test
    public void testUserGroups() {
        PipelineUser savedUser = createUser(TEST_USER1,
                Collections.singletonList(TEST_GROUP_1),
                Collections.singletonList(DefaultRoles.ROLE_USER.getId()));
        assertNotNull(savedUser);

        final List<String> allGroups = Arrays.asList(TEST_GROUP_1, TEST_GROUP_2);
        PipelineUser savedUser2 = createUser(TEST_USER2,
                allGroups,
                Collections.singletonList(DefaultRoles.ROLE_USER.getId()));
        assertNotNull(savedUser2);

        PipelineUser savedUser3 = createUser(TEST_USER3,
                Collections.singletonList(TEST_GROUP_2),
                Arrays.asList(DefaultRoles.ROLE_ADMIN.getId(), DefaultRoles.ROLE_USER.getId()));
        assertNotNull(savedUser3);

        Collection<PipelineUser> userByGroup = userDao.loadUsersByGroup(TEST_GROUP_1);
        assertEquals(2, userByGroup.size());
        assertTrue(userByGroup.stream().anyMatch(u ->
                u.getUserName().equals(TEST_USER1)));
        assertTrue(userByGroup.stream().noneMatch(u ->
                u.getUserName().equals(TEST_USER3)));

        List<String> foundGroups = userDao.findGroups("TEST_");
        assertEquals(allGroups.size(), foundGroups.size());
        assertTrue(allGroups.containsAll(foundGroups));

        assertFalse(userDao.isUserInGroup(savedUser.getUserName(), "TEST_GROUP_5"));
        assertTrue(userDao.isUserInGroup(savedUser.getUserName(), TEST_GROUP_1));

        List<String> allLoadedGroups = userDao.loadAllGroups();
        Collections.sort(allLoadedGroups);
        assertEquals(allGroups, allLoadedGroups);
    }

    @Test
    public void testDefaultAdmin() {
        PipelineUser admin = userDao.loadUserByName(defaultAdmin);
        assertNotNull(admin);
        assertEquals(defaultAdmin, admin.getUserName());
        assertTrue(admin.getId().equals(1L));
        assertEquals(1, admin.getRoles().size());
        assertTrue(isRolePresent(DefaultRoles.ROLE_ADMIN.getRole(), admin.getRoles()));

        Collection<Role> allRoles = roleDao.loadAllRoles(false);
        assertEquals(EXPECTED_DEFAULT_ROLES_NUMBER, allRoles.size());
        assertTrue(isRolePresent(DefaultRoles.ROLE_ADMIN.getRole(), allRoles));
        assertTrue(isRolePresent(DefaultRoles.ROLE_USER.getRole(), allRoles));
    }

    @Test
    public void testUserCRUDWithAttributes() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(ATTRIBUTES_KEY, ATTRIBUTES_VALUE);
        PipelineUser user = UserCreatorUtils.getPipelineUser(TEST_USER1);
        user.setAttributes(attributes);
        PipelineUser savedUser = userDao.createUser(user,
                Arrays.asList(DefaultRoles.ROLE_ADMIN.getId(), DefaultRoles.ROLE_USER.getId()));
        assertNotNull(savedUser);
        assertNotNull(savedUser.getId());
        assertNotNull(savedUser.getAttributes());

        Collection<PipelineUser> users = userDao.loadAllUsers();
        assertFalse(users.isEmpty());
        assertTrue(users.stream()
                .anyMatch(u -> u.getId().equals(savedUser.getId())
                        && assertUserAttributes(attributes, u.getAttributes())));

        PipelineUser userById = userDao.loadUserById(savedUser.getId());
        assertEquals(savedUser.getId(), userById.getId());
        assertTrue(assertUserAttributes(attributes, userById.getAttributes()));

        PipelineUser userByName = userDao.loadUserByName(TEST_USER1.toUpperCase());
        assertEquals(savedUser.getId(), userByName.getId());
        assertTrue(assertUserAttributes(attributes, userByName.getAttributes()));

        savedUser.setUserName(TEST_USER2);
        userDao.updateUser(savedUser);
        PipelineUser userByChangedName = userDao.loadUserByName(TEST_USER2);
        assertEquals(savedUser.getId(), userByChangedName.getId());
        assertTrue(assertUserAttributes(attributes, userByChangedName.getAttributes()));

        deleteUserAndHisRoles(savedUser);

        assertNull(userDao.loadUserById(savedUser.getId()));
        Collection<PipelineUser> usersAfterDeletion = userDao.loadAllUsers();
        assertTrue(usersAfterDeletion.stream().noneMatch(u -> u.getId().equals(savedUser.getId())));
    }

    @Test
    public void shouldReturnUserByStorageId() {
        S3bucketDataStorage s3bucketDataStorage = ObjectCreatorUtils
                .createS3Bucket(null, "test", "test", TEST_USER1);
        dataStorageDao.createDataStorage(s3bucketDataStorage);
        PipelineUser user = UserCreatorUtils.getPipelineUser(TEST_USER1);
        user.setDefaultStorageId(s3bucketDataStorage.getId());
        userDao.createUser(user,
                Arrays.asList(DefaultRoles.ROLE_ADMIN.getId(), DefaultRoles.ROLE_USER.getId()));
        assertThat(userDao.loadUsersByStorageId(s3bucketDataStorage.getId()), hasSize(1));

    }

    @Test
    public void shouldLoadUsersByGroupOrRole() {
        final Role testRole = roleDao.createRole(TEST_ROLE);
        createUser(TEST_USER1,
                Collections.singletonList(TEST_GROUP_1),
                Collections.singletonList(testRole.getId()));
        createUser(TEST_USER2,
                Collections.singletonList(TEST_GROUP_1),
                Collections.singletonList(DefaultRoles.ROLE_USER.getId()));
        final Collection<PipelineUser> usersByRole =
                userDao.loadUsersByGroupOrRole(TEST_ROLE);
        assertThat(usersByRole, hasSize(1));
        assertThat(usersByRole, contains(hasProperty(USER_NAME, equalTo(TEST_USER1))));

        final Collection<PipelineUser> usersByGroup = userDao.loadUsersByGroupOrRole(TEST_GROUP_1);
        assertThat(usersByGroup, hasSize(2));
        assertThat(usersByGroup, hasItem(hasProperty(USER_NAME, equalTo(TEST_USER1))));
        assertThat(usersByGroup, hasItem(hasProperty(USER_NAME, equalTo(TEST_USER2))));
    }

    private PipelineUser createUser(final String name,
                                    final Collection<String> groups,
                                    final Collection<Long> roleIds) {
        final PipelineUser user = PipelineUser.builder()
                .userName(name)
                .groups(new ArrayList<>(groups))
                .build();
        user.setOwner("ADMIN");
        return userDao.createUser(user, new ArrayList<>(roleIds));
    }

    private boolean isRolePresent(Role roleToFind, Collection<Role> roles) {
        return roles.stream().anyMatch(r -> r.getName().equals(roleToFind.getName()));
    }

    private boolean assertUserAttributes(Map<String, String> expectedAttributes, Map<String, String> actualAttributes) {
        return CollectionUtils.isEqualCollection(expectedAttributes.entrySet(), actualAttributes.entrySet());
    }
}
