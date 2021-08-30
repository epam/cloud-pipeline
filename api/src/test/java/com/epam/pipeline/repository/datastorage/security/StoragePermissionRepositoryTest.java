package com.epam.pipeline.repository.datastorage.security;

import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.dto.datastorage.security.StorageKind;
import com.epam.pipeline.entity.SecuredStorageEntity;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageRoot;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.security.StoragePermissionEntity;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionPathType;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionSidType;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.repository.AbstractJpaTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@Transactional
public class StoragePermissionRepositoryTest extends AbstractJpaTest {

    private static final String ROOT_PATH = "";
    private static final String PARENT_PATH = "parent";
    private static final String PATH = PARENT_PATH + "/path";
    private static final String CHILD_PATH = PATH + "/child";
    private static final String NESTED_CHILD_PATH = CHILD_PATH + "/nestedchild";
    private static final String ANOTHER_PATH = PARENT_PATH + "/anotherpath";
    private static final String ANOTHER_CHILD_PATH = ANOTHER_PATH + "/child";
    private static final String ANOTHER_NESTED_CHILD_PATH = ANOTHER_CHILD_PATH + "/nestedchild";
    private static final StoragePermissionPathType FILE_TYPE = StoragePermissionPathType.FILE;
    private static final StoragePermissionPathType FOLDER_TYPE = StoragePermissionPathType.FOLDER;
    private static final String USER_SID_NAME = "USER_SID_NAME";
    private static final String ANOTHER_USER_SID_NAME = "ANOTHER_USER_SID_NAME";
    private static final String GROUP_SID_NAME = "GROUP_SID_NAME";
    private static final String ANOTHER_GROUP_SID_NAME = "ANOTHER_GROUP_SID_NAME";
    private static final List<String> GROUP_SID_NAMES = Collections.singletonList(GROUP_SID_NAME);
    private static final StoragePermissionSidType USER_SID_TYPE = StoragePermissionSidType.USER;
    private static final StoragePermissionSidType GROUP_SID_TYPE = StoragePermissionSidType.GROUP;
    private static final int READ_MASK = AclPermission.READ.getMask();
    private static final int NO_READ_MASK = AclPermission.NO_READ.getMask();
    private static final int WRITE_MASK = AclPermission.WRITE.getMask();
    private static final int NO_WRITE_MASK = AclPermission.NO_WRITE.getMask();
    private static final int EXECUTE_MASK = AclPermission.EXECUTE.getMask();
    private static final int EMPTY_MASK = 0;
    private static final LocalDateTime CREATED = LocalDateTime.now();

    @Autowired
    private StoragePermissionRepository repository;

    @Autowired
    private DataStorageDao dataStorageDao;

    @Test
    public void findByRootAndPathAndTypeShouldReturnMatchingExactPermissions() {
        final DataStorageRoot root = createRoot();
        final List<StoragePermissionEntity.StoragePermissionEntityBuilder> permissionBuilders = Arrays.asList(
                permission(root).toBuilder().datastoragePath(PATH)
                        .sidName(USER_SID_NAME).sidType(USER_SID_TYPE),
                permission(root).toBuilder().datastoragePath(PATH)
                        .sidName(GROUP_SID_NAME).sidType(GROUP_SID_TYPE),
                permission(root).toBuilder().datastoragePath(PATH)
                        .sidName(ANOTHER_USER_SID_NAME).sidType(USER_SID_TYPE),
                permission(root).toBuilder().datastoragePath(PATH)
                        .sidName(ANOTHER_GROUP_SID_NAME).sidType(GROUP_SID_TYPE));
        create(permissionBuilders.stream()
                .map(StoragePermissionEntity.StoragePermissionEntityBuilder::build)
                .toArray(StoragePermissionEntity[]::new));
        create(permission(root).toBuilder().datastoragePath(NESTED_CHILD_PATH).build(),
                permission(root).toBuilder().datastoragePath(CHILD_PATH).build(),
                permission(root).toBuilder().datastoragePath(PARENT_PATH).build(),
                permission(root).toBuilder().datastoragePath(ROOT_PATH).build());

        final List<StoragePermissionEntity> permissions = sortedBySid(repository.findByRootAndPathAndType(root.getId(),
                PATH, FOLDER_TYPE));

        assertThat(permissions.size(), is(4));
        IntStream.range(0, permissions.size()).forEach(i ->
                assertPermission(permissions.get(i), permissionBuilders.get(i).build()));
    }

    @Test
    public void findPermissionsShouldReturnExactUserPermissions() {
        final DataStorageRoot root = createRoot();
        create(permission(root));

        final List<StoragePermissionEntity> permissions = findPermissions(root);

        assertThat(permissions.size(), is(1));
        assertPermission(permissions.get(0), permission(root));
    }

    @Test
    public void findPermissionsShouldReturnExactGroupPermissions() {
        final DataStorageRoot root = createRoot();
        final StoragePermissionEntity.StoragePermissionEntityBuilder permissionBuilder =
                permission(root).toBuilder().sidName(GROUP_SID_NAME).sidType(GROUP_SID_TYPE);
        create(permissionBuilder.build());

        final List<StoragePermissionEntity> permissions = findPermissions(root);

        assertThat(permissions.size(), is(1));
        assertPermission(permissions.get(0), permissionBuilder.build());
    }

    @Test
    public void findPermissionsShouldReturnParentUserPermissions() {
        final DataStorageRoot root = createRoot();
        final StoragePermissionEntity.StoragePermissionEntityBuilder permissionBuilder =
                permission(root).toBuilder().datastoragePath(PARENT_PATH);
        create(permissionBuilder.build());

        final List<StoragePermissionEntity> permissions = findPermissions(root);

        assertThat(permissions.size(), is(1));
        assertPermission(permissions.get(0), permissionBuilder.build());
    }

    @Test
    public void findPermissionsShouldReturnParentGroupPermissions() {
        final DataStorageRoot root = createRoot();
        final StoragePermissionEntity.StoragePermissionEntityBuilder permissionBuilder =
                permission(root).toBuilder().datastoragePath(PARENT_PATH).sidName(GROUP_SID_NAME)
                        .sidType(GROUP_SID_TYPE);
        create(permissionBuilder.build());

        final List<StoragePermissionEntity> permissions = findPermissions(root);

        assertThat(permissions.size(), is(1));
        assertPermission(permissions.get(0), permissionBuilder.build());
    }

    @Test
    public void findPermissionsShouldReturnRootParentUserPermissions() {
        final DataStorageRoot root = createRoot();
        final StoragePermissionEntity.StoragePermissionEntityBuilder permissionBuilder =
                permission(root).toBuilder().datastoragePath(ROOT_PATH);
        create(permissionBuilder.build());

        final List<StoragePermissionEntity> permissions = findPermissions(root);

        assertThat(permissions.size(), is(1));
        assertPermission(permissions.get(0), permissionBuilder.build());
    }

    @Test
    public void findPermissionsShouldReturnRootParentGroupPermissions() {
        final DataStorageRoot root = createRoot();
        final StoragePermissionEntity.StoragePermissionEntityBuilder permissionBuilder =
                permission(root).toBuilder().datastoragePath(ROOT_PATH).sidName(GROUP_SID_NAME)
                        .sidType(GROUP_SID_TYPE);
        create(permissionBuilder.build());

        final List<StoragePermissionEntity> permissions = findPermissions(root);

        assertThat(permissions.size(), is(1));
        assertPermission(permissions.get(0), permissionBuilder.build());
    }

    @Test
    public void findPermissionsShouldReturnMultipleSidPermissions() {
        final DataStorageRoot root = createRoot();
        final List<StoragePermissionEntity.StoragePermissionEntityBuilder> permissionBuilders = Arrays.asList(
                permission(root).toBuilder().sidName(USER_SID_NAME).sidType(USER_SID_TYPE),
                permission(root).toBuilder().sidName(GROUP_SID_NAME).sidType(GROUP_SID_TYPE));
        create(permissionBuilders.stream()
                .map(StoragePermissionEntity.StoragePermissionEntityBuilder::build)
                .toArray(StoragePermissionEntity[]::new));

        final List<StoragePermissionEntity> permissions = sortedBySid(findPermissions(root));

        IntStream.range(0, permissions.size()).forEach(i ->
                assertPermission(permissions.get(i), permissionBuilders.get(i).build()));
    }

    @Test
    public void findPermissionsShouldReturnBothExactAndAllParentPermissions() {
        final DataStorageRoot root = createRoot();
        final List<StoragePermissionEntity.StoragePermissionEntityBuilder> permissionBuilders = Arrays.asList(
                permission(root).toBuilder().datastoragePath(PATH),
                permission(root).toBuilder().datastoragePath(PARENT_PATH),
                permission(root).toBuilder().datastoragePath(ROOT_PATH));
        create(permissionBuilders.stream()
                .map(StoragePermissionEntity.StoragePermissionEntityBuilder::build)
                .toArray(StoragePermissionEntity[]::new));

        final List<StoragePermissionEntity> permissions = sortedByPath(findPermissions(root));

        IntStream.range(0, permissions.size()).forEach(i ->
                assertPermission(permissions.get(i), permissionBuilders.get(i).build()));
    }

    @Test
    public void findImmediateChildPermissionsShouldReturnChildUserPermissions() {
        final DataStorageRoot root = createRoot();
        final StoragePermissionEntity.StoragePermissionEntityBuilder permissionBuilder =
                permission(root).toBuilder().datastoragePath(CHILD_PATH);
        create(permissionBuilder.build());

        final List<StoragePermissionEntity> permissions = findImmediateChildPermissions(root);

        assertThat(permissions.size(), is(1));
        assertPermission(permissions.get(0), permissionBuilder.build());
    }

    @Test
    public void findImmediateChildPermissionsShouldReturnChildGroupPermissions() {
        final DataStorageRoot root = createRoot();
        final StoragePermissionEntity.StoragePermissionEntityBuilder permissionBuilder =
                permission(root).toBuilder().datastoragePath(CHILD_PATH).sidName(GROUP_SID_NAME)
                        .sidType(GROUP_SID_TYPE);
        create(permissionBuilder.build());

        final List<StoragePermissionEntity> permissions = findImmediateChildPermissions(root);

        assertThat(permissions.size(), is(1));
        assertPermission(permissions.get(0), permissionBuilder.build());
    }

    @Test
    public void findImmediateChildPermissionsShouldNotReturnNestedChildPermissions() {
        final DataStorageRoot root = createRoot();
        final StoragePermissionEntity.StoragePermissionEntityBuilder permissionBuilder =
                permission(root).toBuilder().datastoragePath(NESTED_CHILD_PATH);
        create(permissionBuilder.build());

        final List<StoragePermissionEntity> permissions = findImmediateChildPermissions(root);

        assertThat(permissions.size(), is(0));
    }

    @Test
    public void findReadAllowedImmediateChildItemsShouldReturnNothingIfThereAreNoChildPermissions() {
        final DataStorageRoot root = createRoot();

        final List<StoragePermissionRepository.StorageItem> permissions = findReadAllowedImmediateChildItems(root);

        assertThat(permissions.size(), is(0));
    }

    @Test
    public void findReadAllowedImmediateChildItemsShouldReturnChildFolderWithReadPermission() {
        final DataStorageRoot root = createRoot();
        create(permission(root).toBuilder().datastoragePath(CHILD_PATH).build());

        final List<StoragePermissionRepository.StorageItem> items = findReadAllowedImmediateChildItems(root);

        assertThat(items.size(), is(1));
        assertThat(items.get(0).getStoragePath(), is(CHILD_PATH));
        assertThat(items.get(0).getStoragePathType(), is(StoragePermissionPathType.FOLDER));
    }

    @Test
    public void findReadAllowedImmediateChildItemsShouldReturnChildFolderWithNestedFolderReadPermission() {
        final DataStorageRoot root = createRoot();
        create(permission(root).toBuilder().datastoragePath(NESTED_CHILD_PATH).build());

        final List<StoragePermissionRepository.StorageItem> items = findReadAllowedImmediateChildItems(root);

        assertThat(items.size(), is(1));
        assertThat(items.get(0).getStoragePath(), is(CHILD_PATH));
        assertThat(items.get(0).getStoragePathType(), is(StoragePermissionPathType.FOLDER));
    }

    @Test
    public void findReadAllowedImmediateChildItemsShouldReturnChildFolderWithNestedFileReadPermission() {
        final DataStorageRoot root = createRoot();
        create(permission(root).toBuilder().datastoragePath(NESTED_CHILD_PATH).datastorageType(FILE_TYPE).build());

        final List<StoragePermissionRepository.StorageItem> items = findReadAllowedImmediateChildItems(root);

        assertThat(items.size(), is(1));
        assertThat(items.get(0).getStoragePath(), is(CHILD_PATH));
        assertThat(items.get(0).getStoragePathType(), is(StoragePermissionPathType.FOLDER));
    }

    @Test
    public void findReadAllowedImmediateChildItemsShouldReturnChildFileWithReadPermission() {
        final DataStorageRoot root = createRoot();
        create(permission(root).toBuilder().datastoragePath(CHILD_PATH).datastorageType(FILE_TYPE).build());

        final List<StoragePermissionRepository.StorageItem> items = findReadAllowedImmediateChildItems(root);

        assertThat(items.size(), is(1));
        assertThat(items.get(0).getStoragePath(), is(CHILD_PATH));
        assertThat(items.get(0).getStoragePathType(), is(StoragePermissionPathType.FILE));
    }

    @Test
    public void findReadAllowedStoragesShouldNotReturnStorageWithoutPathReadPermissions() {
        createRoot();
        createDataStorage();

        final List<StoragePermissionRepository.Storage> storages = findReadAllowedStorages();

        assertThat(storages.size(), is(0));
    }

    @Test
    public void findReadAllowedStoragesShouldReturnStorageWithRootWithReadPermission() {
        final DataStorageRoot root = createRoot();
        final AbstractDataStorage storage = createDataStorage();
        create(permission(root).toBuilder().datastoragePath(ROOT_PATH).build());

        final List<StoragePermissionRepository.Storage> storages = findReadAllowedStorages();

        assertThat(storages.size(), is(1));
        assertThat(storages.get(0).getStorageId(), is(storage.getId()));
        assertThat(storages.get(0).getStorageType(), is(StorageKind.DATA_STORAGE));
    }

    @Test
    public void findReadAllowedStoragesShouldNotReturnStorageWithRootWithWritePermission() {
        final DataStorageRoot root = createRoot();
        createDataStorage();
        create(permission(root).toBuilder().datastoragePath(NESTED_CHILD_PATH).datastorageType(FILE_TYPE)
                .mask(WRITE_MASK).build());

        final List<StoragePermissionRepository.Storage> storages = findReadAllowedStorages();

        assertThat(storages.size(), is(0));
    }

    @Test
    public void findReadAllowedStoragesShouldReturnStorageWithRootFolderWithReadPermission() {
        final DataStorageRoot root = createRoot();
        final AbstractDataStorage storage = createDataStorage();
        create(permission(root).toBuilder().datastoragePath(PARENT_PATH).build());

        final List<StoragePermissionRepository.Storage> storages = findReadAllowedStorages();

        assertThat(storages.size(), is(1));
        assertThat(storages.get(0).getStorageId(), is(storage.getId()));
        assertThat(storages.get(0).getStorageType(), is(StorageKind.DATA_STORAGE));
    }

    @Test
    public void findReadAllowedStoragesShouldReturnStorageWithRootFileWithReadPermission() {
        final DataStorageRoot root = createRoot();
        final AbstractDataStorage storage = createDataStorage();
        create(permission(root).toBuilder().datastoragePath(PARENT_PATH).datastorageType(FILE_TYPE).build());

        final List<StoragePermissionRepository.Storage> storages = findReadAllowedStorages();

        assertThat(storages.size(), is(1));
        assertThat(storages.get(0).getStorageId(), is(storage.getId()));
        assertThat(storages.get(0).getStorageType(), is(StorageKind.DATA_STORAGE));
    }

    @Test
    public void findReadAllowedStoragesShouldReturnStorageWithNestedChildFolderWithReadPermission() {
        final DataStorageRoot root = createRoot();
        final AbstractDataStorage storage = createDataStorage();
        create(permission(root).toBuilder().datastoragePath(NESTED_CHILD_PATH).build());

        final List<StoragePermissionRepository.Storage> storages = findReadAllowedStorages();

        assertThat(storages.size(), is(1));
        assertThat(storages.get(0).getStorageId(), is(storage.getId()));
        assertThat(storages.get(0).getStorageType(), is(StorageKind.DATA_STORAGE));
    }

    @Test
    public void findReadAllowedStoragesShouldReturnStorageWithNestedChildFileWithReadPermission() {
        final DataStorageRoot root = createRoot();
        final AbstractDataStorage storage = createDataStorage();
        create(permission(root).toBuilder().datastoragePath(NESTED_CHILD_PATH).datastorageType(FILE_TYPE).build());

        final List<StoragePermissionRepository.Storage> storages = findReadAllowedStorages();

        assertThat(storages.size(), is(1));
        assertThat(storages.get(0).getStorageId(), is(storage.getId()));
        assertThat(storages.get(0).getStorageType(), is(StorageKind.DATA_STORAGE));
    }

    @Test
    public void findReadAllowedStorageShouldNotReturnStorageWithoutPathReadPermissions() {
        final DataStorageRoot root = createRoot();
        final AbstractDataStorage storage = createDataStorage();

        final List<StoragePermissionRepository.Storage> storages = findReadAllowedStorage(root, storage);

        assertThat(storages.size(), is(0));
    }

    @Test
    public void findReadAllowedStorageShouldReturnStorageWithRootWithReadPermission() {
        final DataStorageRoot root = createRoot();
        final AbstractDataStorage storage = createDataStorage();
        create(permission(root).toBuilder().datastoragePath(ROOT_PATH).build());

        final List<StoragePermissionRepository.Storage> storages = findReadAllowedStorage(root, storage);

        assertThat(storages.size(), is(1));
        assertThat(storages.get(0).getStorageId(), is(storage.getId()));
        assertThat(storages.get(0).getStorageType(), is(StorageKind.DATA_STORAGE));
    }

    @Test
    public void findReadAllowedStorageShouldNotReturnStorageWithRootWithWritePermission() {
        final DataStorageRoot root = createRoot();
        final AbstractDataStorage storage = createDataStorage();
        create(permission(root).toBuilder().datastoragePath(NESTED_CHILD_PATH).datastorageType(FILE_TYPE)
                .mask(WRITE_MASK).build());

        final List<StoragePermissionRepository.Storage> storages = findReadAllowedStorage(root, storage);

        assertThat(storages.size(), is(0));
    }

    @Test
    public void findReadAllowedStorageShouldReturnStorageWithRootFolderWithReadPermission() {
        final DataStorageRoot root = createRoot();
        final AbstractDataStorage storage = createDataStorage();
        create(permission(root).toBuilder().datastoragePath(PARENT_PATH).build());

        final List<StoragePermissionRepository.Storage> storages = findReadAllowedStorage(root, storage);

        assertThat(storages.size(), is(1));
        assertThat(storages.get(0).getStorageId(), is(storage.getId()));
        assertThat(storages.get(0).getStorageType(), is(StorageKind.DATA_STORAGE));
    }

    @Test
    public void findReadAllowedStorageShouldReturnStorageWithRootFileWithReadPermission() {
        final DataStorageRoot root = createRoot();
        final AbstractDataStorage storage = createDataStorage();
        create(permission(root).toBuilder().datastoragePath(PARENT_PATH).datastorageType(FILE_TYPE).build());

        final List<StoragePermissionRepository.Storage> storages = findReadAllowedStorage(root, storage);

        assertThat(storages.size(), is(1));
        assertThat(storages.get(0).getStorageId(), is(storage.getId()));
        assertThat(storages.get(0).getStorageType(), is(StorageKind.DATA_STORAGE));
    }

    @Test
    public void findReadAllowedStorageShouldReturnStorageWithNestedChildFolderWithReadPermission() {
        final DataStorageRoot root = createRoot();
        final AbstractDataStorage storage = createDataStorage();
        create(permission(root).toBuilder().datastoragePath(NESTED_CHILD_PATH).build());

        final List<StoragePermissionRepository.Storage> storages = findReadAllowedStorage(root, storage);

        assertThat(storages.size(), is(1));
        assertThat(storages.get(0).getStorageId(), is(storage.getId()));
        assertThat(storages.get(0).getStorageType(), is(StorageKind.DATA_STORAGE));
    }

    @Test
    public void findReadAllowedStorageShouldReturnStorageWithNestedChildFileWithReadPermission() {
        final DataStorageRoot root = createRoot();
        final AbstractDataStorage storage = createDataStorage();
        create(permission(root).toBuilder().datastoragePath(NESTED_CHILD_PATH).datastorageType(FILE_TYPE).build());

        final List<StoragePermissionRepository.Storage> storages = findReadAllowedStorage(root, storage);

        assertThat(storages.size(), is(1));
        assertThat(storages.get(0).getStorageId(), is(storage.getId()));
        assertThat(storages.get(0).getStorageType(), is(StorageKind.DATA_STORAGE));
    }

    @Test
    public void findRecursiveMaskShouldReturnFullPermissionsMaskIfThereAreNoPermissionsUnderGivenPath() {
        final DataStorageRoot root = createRoot();

        final Optional<Integer> mask = findRecursiveMask(root);

        assertTrue(mask.isPresent());
        assertThat(mask.get(), is(READ_MASK | WRITE_MASK | EXECUTE_MASK));
    }

    @Test
    public void findRecursiveMaskShouldReturnReadAllowedMaskIfThereAreNoReadPermissionsUnderGivenPath() {
        final DataStorageRoot root = createRoot();
        create(permission(root).toBuilder().mask(EMPTY_MASK).build(),
                permission(root).toBuilder().mask(EMPTY_MASK).datastoragePath(CHILD_PATH).build(),
                permission(root).toBuilder().mask(EMPTY_MASK).datastoragePath(NESTED_CHILD_PATH).build());

        final Optional<Integer> mask = findRecursiveMask(root);

        assertTrue(mask.isPresent());
        assertThat(mask.get() & READ_MASK, is(READ_MASK));
    }

    @Test
    public void findRecursiveMaskShouldReturnReadAllowedMaskIfThereAreOnlyReadAllowedPermissionsUnderGivenPath() {
        final DataStorageRoot root = createRoot();
        create(permission(root),
                permission(root).toBuilder().datastoragePath(CHILD_PATH).build(),
                permission(root).toBuilder().datastoragePath(NESTED_CHILD_PATH).build());

        final Optional<Integer> mask = findRecursiveMask(root);

        assertTrue(mask.isPresent());
        assertThat(mask.get() & READ_MASK, is(READ_MASK));
    }

    @Test
    public void findRecursiveMaskShouldReturnReadDeniedMaskIfThereIsExactReadDeniedPermissionsUnderGivenPath() {
        final DataStorageRoot root = createRoot();
        create(permission(root).toBuilder().mask(NO_READ_MASK).build(),
                permission(root).toBuilder().datastoragePath(CHILD_PATH).build(),
                permission(root).toBuilder().datastoragePath(NESTED_CHILD_PATH).build());

        final Optional<Integer> mask = findRecursiveMask(root);

        assertTrue(mask.isPresent());
        assertThat(mask.get() & NO_READ_MASK, is(NO_READ_MASK));
    }

    @Test
    public void findRecursiveMaskShouldReturnReadDeniedMaskIfThereIsChildReadDeniedPermissionsUnderGivenPath() {
        final DataStorageRoot root = createRoot();
        create(permission(root),
                permission(root).toBuilder().datastoragePath(CHILD_PATH).mask(NO_READ_MASK).build(),
                permission(root).toBuilder().datastoragePath(NESTED_CHILD_PATH).build());

        final Optional<Integer> mask = findRecursiveMask(root);

        assertTrue(mask.isPresent());
        assertThat(mask.get() & NO_READ_MASK, is(NO_READ_MASK));
    }

    @Test
    public void findRecursiveMaskShouldReturnReadDeniedMaskIfThereIsNestedChildReadDeniedPermissionsUnderGivenPath() {
        final DataStorageRoot root = createRoot();
        create(permission(root),
                permission(root).toBuilder().datastoragePath(CHILD_PATH).build(),
                permission(root).toBuilder().datastoragePath(NESTED_CHILD_PATH).mask(NO_READ_MASK).build());

        final Optional<Integer> mask = findRecursiveMask(root);

        assertTrue(mask.isPresent());
        assertThat(mask.get() & NO_READ_MASK, is(NO_READ_MASK));
    }

    @Test
    public void findRecursiveMaskShouldReturnWriteAllowedMaskIfThereAreNoWritePermissionsUnderGivenPath() {
        final DataStorageRoot root = createRoot();
        create(permission(root).toBuilder().mask(EMPTY_MASK).build(),
                permission(root).toBuilder().mask(EMPTY_MASK).datastoragePath(CHILD_PATH).build(),
                permission(root).toBuilder().mask(EMPTY_MASK).datastoragePath(NESTED_CHILD_PATH).build());

        final Optional<Integer> mask = findRecursiveMask(root);

        assertTrue(mask.isPresent());
        assertThat(mask.get() & WRITE_MASK, is(WRITE_MASK));
    }

    @Test
    public void findRecursiveMaskShouldReturnWriteAllowedMaskIfThereAreOnlyWriteAllowedPermissionsUnderGivenPath() {
        final DataStorageRoot root = createRoot();
        create(permission(root).toBuilder().mask(WRITE_MASK).build(),
                permission(root).toBuilder().mask(WRITE_MASK).datastoragePath(CHILD_PATH).build(),
                permission(root).toBuilder().mask(WRITE_MASK).datastoragePath(NESTED_CHILD_PATH).build());

        final Optional<Integer> mask = findRecursiveMask(root);

        assertTrue(mask.isPresent());
        assertThat(mask.get() & WRITE_MASK, is(WRITE_MASK));
    }

    @Test
    public void findRecursiveMaskShouldReturnWriteDeniedMaskIfThereIsExactWriteDeniedPermissionsUnderGivenPath() {
        final DataStorageRoot root = createRoot();
        create(permission(root).toBuilder().mask(NO_WRITE_MASK).build(),
                permission(root).toBuilder().datastoragePath(CHILD_PATH).build(),
                permission(root).toBuilder().datastoragePath(NESTED_CHILD_PATH).build());

        final Optional<Integer> mask = findRecursiveMask(root);

        assertTrue(mask.isPresent());
        assertThat(mask.get() & NO_WRITE_MASK, is(NO_WRITE_MASK));
    }

    @Test
    public void findRecursiveMaskShouldReturnWriteDeniedMaskIfThereIsChildWriteDeniedPermissionsUnderGivenPath() {
        final DataStorageRoot root = createRoot();
        create(permission(root),
                permission(root).toBuilder().datastoragePath(CHILD_PATH).mask(NO_WRITE_MASK).build(),
                permission(root).toBuilder().datastoragePath(NESTED_CHILD_PATH).build());

        final Optional<Integer> mask = findRecursiveMask(root);

        assertTrue(mask.isPresent());
        assertThat(mask.get() & NO_WRITE_MASK, is(NO_WRITE_MASK));
    }

    @Test
    public void findRecursiveMaskShouldReturnWriteDeniedMaskIfThereIsNestedChildWriteDeniedPermissionsUnderGivenPath() {
        final DataStorageRoot root = createRoot();
        create(permission(root),
                permission(root).toBuilder().datastoragePath(CHILD_PATH).build(),
                permission(root).toBuilder().datastoragePath(NESTED_CHILD_PATH).mask(NO_WRITE_MASK).build());

        final Optional<Integer> mask = findRecursiveMask(root);

        assertTrue(mask.isPresent());
        assertThat(mask.get() & NO_WRITE_MASK, is(NO_WRITE_MASK));
    }

    @Test
    public void copyFilePermissionsShouldNotCopyPermissionsIfThereAreNoSourcePermissions() {
        final DataStorageRoot root = createRoot();

        repository.copyFilePermissions(root.getId(), PATH, ANOTHER_PATH);

        final List<StoragePermissionEntity> permissions = repository.findByRootAndPathAndType(root.getId(),
                ANOTHER_PATH, FOLDER_TYPE);
        assertThat(permissions.size(), is(0));
    }

    @Test
    public void copyFilePermissionsShouldCopyAllSourcePermissionsToDestinationPermissions() {
        final DataStorageRoot root = createRoot();
        final List<StoragePermissionEntity.StoragePermissionEntityBuilder> permissionBuilders = Arrays.asList(
                permission(root).toBuilder().datastorageType(FILE_TYPE),
                permission(root).toBuilder().datastorageType(FILE_TYPE)
                        .sidName(GROUP_SID_NAME).sidType(GROUP_SID_TYPE));
        create(permissionBuilders.stream()
                .map(StoragePermissionEntity.StoragePermissionEntityBuilder::build)
                .toArray(StoragePermissionEntity[]::new));

        repository.copyFilePermissions(root.getId(), PATH, ANOTHER_PATH);

        final List<StoragePermissionEntity> permissions = sortedBySid(repository.findByRootAndPathAndType(
                        root.getId(), ANOTHER_PATH, FILE_TYPE));
        IntStream.range(0, permissions.size()).forEach(i ->
                assertPermission(permissions.get(i), permissionBuilders.get(i).datastoragePath(ANOTHER_PATH).build()));
    }

    @Test
    public void copyFolderPermissionsShouldNotCopyPermissionsIfThereAreNoSourcePermissions() {
        final DataStorageRoot root = createRoot();

        repository.copyFolderPermissions(root.getId(), PATH, ANOTHER_PATH);

        final List<StoragePermissionEntity> permissions = repository.findByRootAndPathAndType(root.getId(),
                 ANOTHER_PATH, FOLDER_TYPE);
        assertThat(permissions.size(), is(0));
    }

    @Test
    public void copyFolderPermissionsShouldCopyAllSourcePermissionsToDestinationPermissions() {
        final DataStorageRoot root = createRoot();
        final List<StoragePermissionEntity.StoragePermissionEntityBuilder> permissionBuilders = Arrays.asList(
                permission(root).toBuilder(),
                permission(root).toBuilder()
                        .sidName(GROUP_SID_NAME).sidType(GROUP_SID_TYPE),
                permission(root).toBuilder().datastoragePath(CHILD_PATH),
                permission(root).toBuilder().datastoragePath(CHILD_PATH)
                        .sidName(GROUP_SID_NAME).sidType(GROUP_SID_TYPE),
                permission(root).toBuilder().datastoragePath(NESTED_CHILD_PATH),
                permission(root).toBuilder().datastoragePath(NESTED_CHILD_PATH)
                        .sidName(GROUP_SID_NAME).sidType(GROUP_SID_TYPE));
        create(permissionBuilders.stream()
                .map(StoragePermissionEntity.StoragePermissionEntityBuilder::build)
                .toArray(StoragePermissionEntity[]::new));

        repository.copyFolderPermissions(root.getId(), PATH, ANOTHER_PATH);

        final List<String> paths = Arrays.asList(ANOTHER_PATH, ANOTHER_CHILD_PATH, ANOTHER_NESTED_CHILD_PATH);
        IntStream.range(0, 2)
                .forEach(i -> {
                    final String path = paths.get(i);
                    final List<StoragePermissionEntity> permissions = sortedBySid(repository.findByRootAndPathAndType(
                            root.getId(), path, FOLDER_TYPE));
                    assertThat(permissions.size(), is(2));
                    IntStream.range(0, permissions.size()).forEach(j -> assertPermission(permissions.get(j),
                            permissionBuilders.get(j + i * 2).datastoragePath(path).build()));
                });
    }

    @Test
    public void deleteFilePermissionsShouldNotFailIfThereAreNoPermissions() {
        final DataStorageRoot root = createRoot();

        repository.deleteFilePermissions(root.getId(), PATH);
    }

    @Test
    public void deleteFilePermissionsShouldDeleteAllExactPermissions() {
        final DataStorageRoot root = createRoot();
        create(permission(root).toBuilder().datastorageType(FILE_TYPE).build(),
                permission(root).toBuilder().datastorageType(FILE_TYPE)
                        .sidName(GROUP_SID_NAME).sidType(GROUP_SID_TYPE).build());

        repository.deleteFilePermissions(root.getId(), PATH);

        assertThat(repository.findByRootAndPathAndType(root.getId(), PATH, FOLDER_TYPE).size(), is(0));
    }

    @Test
    public void deleteFolderPermissionsShouldNotFailIfThereAreNoPermissions() {
        final DataStorageRoot root = createRoot();

        repository.deleteFolderPermissions(root.getId(), PATH);
    }

    @Test
    public void deleteFolderPermissionsShouldDeleteAllExactPermissions() {
        final DataStorageRoot root = createRoot();
        create(permission(root).toBuilder().datastorageType(FILE_TYPE).build(),
                permission(root).toBuilder().datastorageType(FILE_TYPE)
                        .sidName(GROUP_SID_NAME).sidType(GROUP_SID_TYPE).build());

        repository.deleteFolderPermissions(root.getId(), PATH);

        assertThat(repository.findByRootAndPathAndType(root.getId(), PATH, FOLDER_TYPE).size(), is(0));
    }

    @Test
    public void deleteFolderPermissionsShouldDeleteChildPermissions() {
        final DataStorageRoot root = createRoot();
        create(permission(root).toBuilder().datastoragePath(CHILD_PATH).build(),
                permission(root).toBuilder().datastoragePath(CHILD_PATH)
                        .sidName(GROUP_SID_NAME).sidType(GROUP_SID_TYPE).build());

        repository.deleteFolderPermissions(root.getId(), PATH);

        assertThat(repository.findByRootAndPathAndType(root.getId(), CHILD_PATH, FOLDER_TYPE).size(), is(0));
    }

    @Test
    public void deleteFolderPermissionsShouldDeleteNestedChildPermissions() {
        final DataStorageRoot root = createRoot();
        create(permission(root).toBuilder().datastoragePath(NESTED_CHILD_PATH).build(),
                permission(root).toBuilder().datastoragePath(NESTED_CHILD_PATH)
                        .sidName(GROUP_SID_NAME).sidType(GROUP_SID_TYPE).build());

        repository.deleteFolderPermissions(root.getId(), PATH);

        assertThat(repository.findByRootAndPathAndType(root.getId(), NESTED_CHILD_PATH, FOLDER_TYPE).size(), is(0));
    }

    private DataStorageRoot createRoot() {
        dataStorageDao.createDataStorageRoot(ROOT_PATH);
        return dataStorageDao.loadDataStorageRoot(ROOT_PATH)
                .orElseThrow(() -> new IllegalArgumentException("Data storage root id was not found"));
    }

    private AbstractDataStorage createDataStorage() {
        final S3bucketDataStorage storage = new S3bucketDataStorage();
        storage.setName(ROOT_PATH);
        storage.setPath(ROOT_PATH);
        storage.setOwner(USER_SID_NAME);
        storage.setType(DataStorageType.S3);
        dataStorageDao.createDataStorage(storage);
        return storage;
    }

    private void create(final StoragePermissionEntity... entities) {
        for (StoragePermissionEntity entity : entities) {
            repository.save(entity);
        }
        repository.findAll();
    }

    private StoragePermissionEntity permission(final DataStorageRoot root) {
        return new StoragePermissionEntity(root.getId(), PATH, FOLDER_TYPE, USER_SID_NAME, USER_SID_TYPE, READ_MASK,
                CREATED);
    }

    private List<StoragePermissionEntity> findPermissions(final DataStorageRoot root) {
        return repository.findPermissions(root.getId(), PATH, FOLDER_TYPE.name().toUpperCase(),
                Arrays.asList(ROOT_PATH, PARENT_PATH), USER_SID_NAME, GROUP_SID_NAMES);
    }

    private List<StoragePermissionEntity> findImmediateChildPermissions(final DataStorageRoot root) {
        return repository.findImmediateChildPermissions(root.getId(), PATH, USER_SID_NAME, GROUP_SID_NAMES);
    }

    private List<StoragePermissionRepository.StorageItem> findReadAllowedImmediateChildItems(
            final DataStorageRoot root) {
        return repository.findReadAllowedImmediateChildItems(root.getId(), PATH, USER_SID_NAME, GROUP_SID_NAMES);
    }

    private List<StoragePermissionRepository.Storage> findReadAllowedStorages() {
        return repository.findReadAllowedStorages(USER_SID_NAME, GROUP_SID_NAMES);
    }

    private List<StoragePermissionRepository.Storage> findReadAllowedStorage(final DataStorageRoot root,
                                                                             final SecuredStorageEntity storage) {
        return repository.findReadAllowedStorage(root.getId(), storage.getId(), USER_SID_NAME, GROUP_SID_NAMES);
    }

    private Optional<Integer> findRecursiveMask(final DataStorageRoot root) {
        return repository.findRecursiveMask(root.getId(), PATH, USER_SID_NAME, GROUP_SID_NAMES);
    }

    private List<StoragePermissionEntity> sortedByPath(final List<StoragePermissionEntity> permissions) {
        return sortedBy(permissions, Comparator.comparing(StoragePermissionEntity::getDatastoragePath,
                Comparator.reverseOrder()));
    }

    private List<StoragePermissionEntity> sortedBySid(final List<StoragePermissionEntity> permissions) {
        return sortedBy(permissions, Comparator.comparing(StoragePermissionEntity::getSidName,
                Comparator.reverseOrder()));
    }

    private List<StoragePermissionEntity> sortedBy(final List<StoragePermissionEntity> permissions,
                                                   final Comparator<StoragePermissionEntity> comparator) {
        return permissions.stream().sorted(comparator).collect(Collectors.toList());
    }

    private void assertPermission(final StoragePermissionEntity actual, final StoragePermissionEntity expected) {
        assertThat(actual.getDatastorageRootId(), is(expected.getDatastorageRootId()));
        assertThat(actual.getDatastoragePath(), is(expected.getDatastoragePath()));
        assertThat(actual.getDatastorageType(), is(expected.getDatastorageType()));
        assertThat(actual.getSidName(), is(expected.getSidName()));
        assertThat(actual.getSidType(), is(expected.getSidType()));
        assertThat(actual.getMask(), is(expected.getMask()));
        assertThat(actual.getCreated(), is(expected.getCreated()));
    }
}
