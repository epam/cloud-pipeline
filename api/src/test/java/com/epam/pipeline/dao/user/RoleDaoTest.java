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
import com.epam.pipeline.entity.user.ExtendedRole;
import com.epam.pipeline.entity.user.GroupStatus;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.Assert.*;

@Transactional
public class RoleDaoTest extends AbstractSpringTest {

    private static final int EXPECTED_DEFAULT_ROLES_NUMBER = 8;
    private static final String TEST_USER1 = "test_user1";
    private static final String TEST_ROLE = "ROLE_TEST";
    private static final String TEST_ROLE_UPDATED = "NEW_ROLE";
    private static final String TEST_STORAGE_PATH = "test";

    @Autowired
    private RoleDao roleDao;

    @Autowired
    private DataStorageDao dataStorageDao;

    @Autowired
    private GroupStatusDao groupStatusDao;

    @Test
    public void testLoadRolesWithUsers() {
        roleDao.createRole(TEST_ROLE);
        Collection<Role> roles = roleDao.loadAllRoles(true);
        assertEquals(EXPECTED_DEFAULT_ROLES_NUMBER + 1, roles.size());
        assertTrue(roles.stream().anyMatch(role -> role.getName().equals(TEST_ROLE)));
        roles.forEach(role -> {
            ExtendedRole extendedRole = (ExtendedRole)role;
            if (extendedRole.equals(DefaultRoles.ROLE_ADMIN.getRole())) {
                assertEquals(1, extendedRole.getUsers().size());
            } else if (extendedRole.equals(DefaultRoles.ROLE_USER.getRole())) {
                assertTrue(CollectionUtils.isEmpty(extendedRole.getUsers()));
            } else if (extendedRole.getName().equals(TEST_ROLE)) {
                assertTrue(CollectionUtils.isEmpty(extendedRole.getUsers()));
            }
        });
    }

    @Test
    public void testRoleCRUD() {
        Role testRole = roleDao.createRole(TEST_ROLE);
        assertNotNull(testRole);
        assertNotNull(testRole.getId());
        assertEquals(TEST_ROLE, testRole.getName());

        Role loadedRole = roleDao.loadRole(testRole.getId()).get();
        assertEquals(testRole.getName(), loadedRole.getName());
        assertEquals(testRole.getId(), loadedRole.getId());

        Collection<Role> allRoles = roleDao.loadAllRoles(false);
        assertFalse(allRoles.isEmpty());
        assertTrue(isRolePresent(testRole, allRoles));

        List<Role> rolesByList = roleDao.loadRolesList(
                Arrays.asList(testRole.getId(), DefaultRoles.ROLE_USER.getId()));
        assertEquals(2, rolesByList.size());
        assertTrue(isRolePresent(testRole, rolesByList));
        assertTrue(isRolePresent(DefaultRoles.ROLE_USER.getRole(), rolesByList));

        roleDao.deleteRole(testRole.getId());
        assertTrue(!roleDao.loadRole(testRole.getId()).isPresent());
        assertTrue(roleDao.loadAllRoles(false).stream().noneMatch(r -> r.equals(testRole)));

    }

    @Test
    public void shouldReturnRoleByStorageId() {
        S3bucketDataStorage s3bucketDataStorage = ObjectCreatorUtils
                .createS3Bucket(null, TEST_STORAGE_PATH, TEST_STORAGE_PATH, TEST_USER1);
        dataStorageDao.createDataStorage(s3bucketDataStorage);
        roleDao.createRole(TEST_ROLE, false, false, s3bucketDataStorage.getId());
        assertThat(roleDao.loadRolesByStorageId(s3bucketDataStorage.getId()), hasSize(1));
    }

    @Test
    public void shouldUpdateStorage() {
        Role testRole = roleDao.createRole(TEST_ROLE);
        S3bucketDataStorage s3bucketDataStorage = ObjectCreatorUtils
                .createS3Bucket(null, TEST_STORAGE_PATH, TEST_STORAGE_PATH, TEST_USER1);
        dataStorageDao.createDataStorage(s3bucketDataStorage);
        testRole.setUserDefault(true);
        testRole.setDefaultStorageId(s3bucketDataStorage.getId());
        testRole.setName(TEST_ROLE_UPDATED);
        roleDao.updateRole(testRole);
        Optional<Role> loaded = roleDao.loadRole(testRole.getId());
        assertThat(loaded.isPresent(), equalTo(true));
        Role role = loaded.get();
        assertThat(role.getName(), equalTo(TEST_ROLE_UPDATED));
        assertThat(role.getDefaultStorageId(), equalTo(s3bucketDataStorage.getId()));
        assertThat(role.isUserDefault(), equalTo(true));
    }

    @Test
    public void shouldLoadRolesWithGroupStatus() {
        final Role blockedRole = roleDao.createRole(TEST_ROLE);
        blockedRole.setBlocked(true);
        final Role notBlockedRole = roleDao.createRole(TEST_ROLE_UPDATED);
        notBlockedRole.setBlocked(false);

        groupStatusDao.upsertGroupBlockingStatusQuery(new GroupStatus(TEST_ROLE, true));
        groupStatusDao.upsertGroupBlockingStatusQuery(new GroupStatus(TEST_USER1, false));

        final Collection<Role> loadedRoles = roleDao.loadAllRoles(false);
        assertFalse(loadedRoles.stream().anyMatch(role -> role.getName().equals(TEST_USER1)));
        assertEquals(2, loadedRoles
                .stream()
                .filter(role -> role.getName().equals(TEST_ROLE) || role.getName().equals(TEST_ROLE_UPDATED))
                .count());
        loadedRoles.forEach(role -> {
            if (role.getName().equals(TEST_ROLE)) {
                assertEquals(blockedRole, role);
            } else if (role.getName().equals(TEST_ROLE_UPDATED)) {
                assertEquals(notBlockedRole, role);
            }
        });
    }

    @Test
    public void shouldLoadRolesWithGroupStatusAndWithUsers() {
        final ExtendedRole blockedRole = new ExtendedRole();
        blockedRole.setName(TEST_ROLE);
        blockedRole.setBlocked(true);


        final ExtendedRole notBlockedRole = new ExtendedRole();
        notBlockedRole.setName(TEST_ROLE_UPDATED);
        notBlockedRole.setBlocked(false);

        groupStatusDao.upsertGroupBlockingStatusQuery(new GroupStatus(TEST_ROLE, true));
        groupStatusDao.upsertGroupBlockingStatusQuery(new GroupStatus(TEST_USER1, false));

        roleDao.createRole(blockedRole.getName());
        roleDao.createRole(notBlockedRole.getName());

        final String roleAdminName = DefaultRoles.ROLE_ADMIN.getName();
        groupStatusDao.upsertGroupBlockingStatusQuery(new GroupStatus(roleAdminName, true));

        final Collection<Role> loadedRoles = roleDao.loadAllRoles(true);
        assertFalse(loadedRoles.stream().anyMatch(role -> role.getName().equals(TEST_USER1)));
        assertEquals(3, loadedRoles
                .stream()
                .filter(role -> role.getName().equals(TEST_ROLE) || role.getName().equals(TEST_ROLE_UPDATED)
                        || role.getName().equals(roleAdminName))
                .count());
        loadedRoles.forEach(role -> {
            final ExtendedRole extendedRole = (ExtendedRole) role;
            if (role.getName().equals(TEST_ROLE)) {
                assertTrue(CollectionUtils.isEmpty(extendedRole.getUsers()));
                assertTrue(extendedRole.getBlocked());
            } else if (role.getName().equals(TEST_ROLE_UPDATED)) {
                assertFalse(extendedRole.getBlocked());
                assertTrue(CollectionUtils.isEmpty(extendedRole.getUsers()));
            } else if (role.getName().equals(roleAdminName)) {
                assertEquals(1, extendedRole.getUsers().size());
                assertTrue(extendedRole.getBlocked());
            }
        });
    }

    private boolean isRolePresent(Role roleToFind, Collection<Role> roles) {
        return roles.stream().anyMatch(r -> r.getName().equals(roleToFind.getName()));
    }
}
