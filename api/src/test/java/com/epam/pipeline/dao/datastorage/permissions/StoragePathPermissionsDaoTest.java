/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dao.datastorage.permissions;

import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.dto.datastorage.permissions.StoragePathPermissions;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.user.SidImpl;
import com.epam.pipeline.test.creator.region.RegionCreatorUtils;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class StoragePathPermissionsDaoTest extends AbstractJdbcTest {
    private static final String TEST_STORAGE_NAME = "test-storage-name";
    private static final String TEST_STORAGE_PATH = "test-storage-path";
    private static final String PATH0 = "/A/";
    private static final String PATH1 = "/A/B/C/D/";
    private static final String PATH2 = "/D/B/C/A/";
    private static final String PATH3 = "/A/B/C/D/E/F/";
    private static final String FILENAME = "file.txt";
    private static final int READ = 1;
    private static final String USER = "User";
    private static final String GROUP = "Group";
    private static final List<String> NO_PERMISSIONS_PATHS = Arrays.asList("/", "/Y/", "/Y/X/");
    private static final List<String> PATHS_WITH_PERMISSIONS = Arrays.asList(
            "/", PATH0, "/A/B/", "/A/B/C/", PATH1, "/A/B/C/D/G/");

    private S3bucketDataStorage objectStorage;

    @Autowired
    private CloudRegionDao cloudRegionDao;
    @Autowired
    private DataStorageDao dataStorageDao;
    @Autowired
    private StoragePathPermissionsDao storagePathPermissionsDao;

    @Before
    public void setUp() throws Exception {
        final AwsRegion awsRegion = RegionCreatorUtils.getDefaultAwsRegion();
        cloudRegionDao.create(awsRegion);

        objectStorage = new S3bucketDataStorage(null, TEST_STORAGE_NAME, TEST_STORAGE_PATH);
        objectStorage.setRegionId(awsRegion.getId());
        objectStorage.setOwner(TEST_STRING);
        dataStorageDao.createDataStorage(objectStorage);
    }

    @Test
    @Transactional
    public void shouldCRUDPathPermissionsForUser() {
        final List<StoragePathPermissions> permissions = permissions();
        final Long storageId = objectStorage.getId();
        final SidImpl user = userSid();

        storagePathPermissionsDao.batchInsert(permissions, storageId, USER, true);

        final List<StoragePathPermissions> actual = storagePathPermissionsDao
                .findByStorageAndSids(storageId, Collections.singletonList(user));
        assertThat(actual).hasSize(permissions.size());

        storagePathPermissionsDao.deleteForStorageAndSid(storageId, USER, true);

        final List<StoragePathPermissions> empty = storagePathPermissionsDao
                .findByStorageAndSids(storageId, Collections.singletonList(user));
        assertThat(empty).isEmpty();
    }

    @Test
    @Transactional
    public void shouldCRUDPathPermissionsForUserAndGroup() {
        final List<StoragePathPermissions> permissions = permissions();
        final Long storageId = objectStorage.getId();
        final SidImpl user = userSid();
        final SidImpl group = groupSid();

        storagePathPermissionsDao.batchInsert(permissions, storageId, USER, true);
        storagePathPermissionsDao.batchInsert(permissions, storageId, GROUP, false);

        assertThat(storagePathPermissionsDao
                .findByStorageAndSids(storageId, Arrays.asList(user, group))).hasSize(permissions.size() * 2);
        assertThat(storagePathPermissionsDao
                .findByStorageAndSids(storageId, Collections.singletonList(user))).hasSize(permissions.size());
        assertThat(storagePathPermissionsDao
                .findByStorageAndSids(storageId, Collections.singletonList(group))).hasSize(permissions.size());

        storagePathPermissionsDao.deleteForStorageAndSid(storageId, USER, true);

        assertThat(storagePathPermissionsDao
                .findByStorageAndSids(storageId, Collections.singletonList(user))).isEmpty();
        assertThat(storagePathPermissionsDao
                .findByStorageAndSids(storageId, Arrays.asList(user, group))).hasSize(permissions.size());

        storagePathPermissionsDao.deleteForStorageAndSid(storageId, GROUP, false);
        assertThat(storagePathPermissionsDao
                .findByStorageAndSids(storageId, Arrays.asList(user, group))).isEmpty();
    }

    @Test
    @Transactional
    public void shouldDeletePathPermissionsForStorage() {
        final List<StoragePathPermissions> permissions = permissions();
        final Long storageId = objectStorage.getId();
        final SidImpl user = userSid();

        storagePathPermissionsDao.batchInsert(permissions, storageId, USER, true);
        assertThat(storagePathPermissionsDao
                .findByStorageAndSids(storageId, Collections.singletonList(user)))
                .hasSize(permissions.size());

        storagePathPermissionsDao.deleteForStorageId(storageId);
        assertThat(storagePathPermissionsDao
                .findByStorageAndSids(storageId, Collections.singletonList(user)))
                .isEmpty();

        // do not fail if no records available
        storagePathPermissionsDao.deleteForStorageId(storageId);
    }

    @Test
    @Transactional
    public void shouldLoadStoragePathsStartWithPrefix() {
        final List<StoragePathPermissions> permissions = permissions();
        final Long storageId = objectStorage.getId();
        final SidImpl user = userSid();

        storagePathPermissionsDao.batchInsert(permissions, storageId, USER, true);
        storagePathPermissionsDao.batchInsert(permissions, storageId, GROUP, false);

        assertThat(storagePathPermissionsDao
                .findByPrefix(storageId, Collections.singletonList(user), "/A/B/"))
                .hasSize(3);

        assertThat(storagePathPermissionsDao
                .findByPrefix(storageId, Collections.singletonList(user), "/D/"))
                .hasSize(1);

        assertThat(storagePathPermissionsDao
                .findByPrefix(storageId, Collections.singletonList(user), "/F/"))
                .hasSize(0);

        assertThat(storagePathPermissionsDao
                .findByPrefix(storageId, Collections.singletonList(user), PATH2))
                .hasSize(1);

        assertThat(storagePathPermissionsDao
                .findByPrefix(storageId, Collections.singletonList(user), "/D/B/C/A/B/"))
                .hasSize(0);
    }

    @Test
    @Transactional
    public void shouldLoadClosestFilePermissions() {
        final List<StoragePathPermissions> permissions = new ArrayList<>(permissions());
        permissions.add(StoragePathPermissions.builder()
                .folderPath(PATH0)
                .mask(READ)
                .build());
        final Long storageId = objectStorage.getId();
        final SidImpl user = userSid();

        storagePathPermissionsDao.batchInsert(permissions, storageId, USER, true);
        storagePathPermissionsDao.batchInsert(permissions, storageId, GROUP, false);

        final Optional<StoragePathPermissions> actual = storagePathPermissionsDao
                .findClosestFilePermission(storageId, Collections.singletonList(user),
                        PATHS_WITH_PERMISSIONS, "/A/B/C/D/G/", FILENAME);
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get().getFolderPath()).isEqualTo(PATH1);

        final Optional<StoragePathPermissions> noPermissionsResult = storagePathPermissionsDao
                .findClosestFilePermission(storageId, Collections.singletonList(user),
                        NO_PERMISSIONS_PATHS, "/Y/X/", FILENAME);
        assertThat(noPermissionsResult.isPresent()).isFalse();
    }

    @Test
    @Transactional
    public void shouldLoadClosestFolderPermissions() {
        final List<StoragePathPermissions> permissions = new ArrayList<>(permissions());
        permissions.add(StoragePathPermissions.builder()
                .folderPath(PATH0)
                .mask(READ)
                .build());
        final Long storageId = objectStorage.getId();
        final SidImpl user = userSid();

        storagePathPermissionsDao.batchInsert(permissions, storageId, USER, true);
        storagePathPermissionsDao.batchInsert(permissions, storageId, GROUP, false);

        final Optional<StoragePathPermissions> actual = storagePathPermissionsDao
                .findClosestFolderPermission(storageId, Collections.singletonList(user),
                        PATHS_WITH_PERMISSIONS);
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get().getFolderPath()).isEqualTo(PATH1);
        assertThat(actual.get().getFileName()).isNull();

        final Optional<StoragePathPermissions> noPermissionsResult = storagePathPermissionsDao
                .findClosestFolderPermission(storageId, Collections.singletonList(user),
                        NO_PERMISSIONS_PATHS);
        assertThat(noPermissionsResult.isPresent()).isFalse();
    }

    @Test
    @Transactional
    public void shouldCountPermissions() {
        final List<StoragePathPermissions> permissions = new ArrayList<>(permissions());
        permissions.add(StoragePathPermissions.builder()
                .folderPath(PATH0)
                .mask(READ)
                .build());
        final Long storageId = objectStorage.getId();
        final SidImpl user = userSid();

        storagePathPermissionsDao.batchInsert(permissions, storageId, USER, true);
        storagePathPermissionsDao.batchInsert(permissions, storageId, GROUP, false);

        final int permissionsCountWhenGranted = storagePathPermissionsDao
                .countParentFoldersByStorageAndSids(storageId, Collections.singletonList(user),
                        PATHS_WITH_PERMISSIONS);
        assertThat(permissionsCountWhenGranted).isEqualTo(2);

        final int permissionsCountWhenNotGranted = storagePathPermissionsDao
                .countParentFoldersByStorageAndSids(storageId, Collections.singletonList(user),
                        NO_PERMISSIONS_PATHS);
        assertThat(permissionsCountWhenNotGranted).isEqualTo(0);
    }

    //TODO: bucket root cases?

    private static List<StoragePathPermissions> permissions() {
        return Arrays.asList(StoragePathPermissions.builder()
                        .folderPath(PATH1)
                        .mask(READ)
                        .build(),
                StoragePathPermissions.builder()
                        .folderPath(PATH3)
                        .mask(READ)
                        .build(),
                StoragePathPermissions.builder()
                        .folderPath(PATH1)
                        .fileName(FILENAME)
                        .mask(READ)
                        .build(),
                StoragePathPermissions.builder()
                        .folderPath(PATH2)
                        .mask(READ)
                        .build());
    }

    private static SidImpl userSid() {
        final SidImpl user = new SidImpl();
        user.setName(USER);
        user.setPrincipal(true);
        return user;
    }

    private static SidImpl groupSid() {
        final SidImpl group = new SidImpl();
        group.setName(GROUP);
        group.setPrincipal(false);
        return group;
    }
}
