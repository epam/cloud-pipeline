package com.epam.pipeline.repository.datastorage.security;

import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.entity.datastorage.DataStorageRoot;
import com.epam.pipeline.entity.datastorage.security.StoragePermissionEntity;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionPathType;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionSidType;
import com.epam.pipeline.test.repository.AbstractJpaTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@Transactional
public class StoragePermissionRepositoryTest extends AbstractJpaTest {

    private static final String ROOT_PATH = "root";
    private static final String PARENT_PATH = ROOT_PATH + "/parent";
    private static final String PATH = PARENT_PATH + "/item";
    private static final StoragePermissionPathType TYPE = StoragePermissionPathType.FOLDER;
    private static final String SID_NAME = "SID_NAME";
    private static final String ANOTHER_SID_NAME = "ANOTHER_SID_NAME";
    private static final StoragePermissionSidType SID_TYPE = StoragePermissionSidType.USER;
    private static final int MASK = 0;
    private static final LocalDateTime CREATED = LocalDateTime.now();

    @Autowired
    private StoragePermissionRepository repository;

    @Autowired
    private DataStorageDao dataStorageDao;

    @Test
    public void findPermissionsShouldReturnExactPermissions() {
        final DataStorageRoot root = createRoot();
        create(permission(root));

        final List<StoragePermissionEntity> permissions = find(root);

        assertThat(permissions.size(), is(1));
        assertPermission(root, permissions.get(0));
    }

    @Test
    public void findPermissionsShouldReturnExactPermissionsForAllSids() {
        final DataStorageRoot root = createRoot();
        create(permission(root).toBuilder().sidName(SID_NAME).build(),
                permission(root).toBuilder().sidName(ANOTHER_SID_NAME).build());

        final List<StoragePermissionEntity> permissions = sortedBySid(find(root));

        assertThat(permissions.size(), is(2));
        assertThat(permissions.get(0).getSidName(), is(ANOTHER_SID_NAME));
        assertThat(permissions.get(1).getSidName(), is(SID_NAME));
    }

    @Test
    public void findPermissionsShouldReturnParentPermissionsIfExactPermissionsDoNoExist() {
        final DataStorageRoot root = createRoot();
        create(permission(root).toBuilder().datastoragePath(PARENT_PATH).build());

        final List<StoragePermissionEntity> permissions = find(root);

        assertThat(permissions.size(), is(1));
        assertThat(permissions.get(0).getDatastoragePath(), is(PARENT_PATH));
    }

    @Test
    public void findPermissionsShouldReturnParentPermissionsForAllSidsIfExactPermissionsDoNoExist() {
        final DataStorageRoot root = createRoot();
        create(permission(root).toBuilder().datastoragePath(PARENT_PATH).sidName(SID_NAME).build(),
                permission(root).toBuilder().datastoragePath(PARENT_PATH).sidName(ANOTHER_SID_NAME).build());

        final List<StoragePermissionEntity> dataStoragePermissionEntities = find(root);
        final List<StoragePermissionEntity> permissions = sortedBySid(dataStoragePermissionEntities);

        assertThat(permissions.size(), is(2));
        assertThat(permissions.get(0).getSidName(), is(ANOTHER_SID_NAME));
        assertThat(permissions.get(1).getSidName(), is(SID_NAME));
    }

    private void assertPermission(final DataStorageRoot root, final StoragePermissionEntity permission) {
        assertThat(permission.getDatastorageRootId(), is(root.getId()));
        assertThat(permission.getDatastoragePath(), is(PATH));
        assertThat(permission.getDatastorageType(), is(TYPE));
        assertThat(permission.getSidName(), is(SID_NAME));
        assertThat(permission.getSidType(), is(SID_TYPE));
        assertThat(permission.getMask(), is(MASK));
        assertThat(permission.getCreated(), is(CREATED));
    }

    private List<StoragePermissionEntity> sortedBySid(final List<StoragePermissionEntity> permissions) {
        return permissions
                .stream()
                .sorted(Comparator.comparing(StoragePermissionEntity::getSidName))
                .collect(Collectors.toList());
    }

    private List<StoragePermissionEntity> find(final DataStorageRoot root) {
        return repository.findExactOrParentPermissions(root.getId(), PATH, TYPE.name().toUpperCase());
    }

    private DataStorageRoot createRoot() {
        dataStorageDao.createDataStorageRoot(PATH);
        return dataStorageDao.loadDataStorageRoot(PATH)
                .orElseThrow(() -> new IllegalArgumentException("Data storage root id was not found"));
    }

    private void create(final StoragePermissionEntity... entities) {
        for (final StoragePermissionEntity entity : entities) {
            repository.save(entity);
        }
    }

    private StoragePermissionEntity permission(final DataStorageRoot root) {
        return new StoragePermissionEntity(root.getId(), PATH, TYPE, SID_NAME, SID_TYPE, MASK, CREATED);
    }
}
