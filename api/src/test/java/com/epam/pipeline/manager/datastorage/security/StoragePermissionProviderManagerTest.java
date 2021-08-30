package com.epam.pipeline.manager.datastorage.security;

import com.epam.pipeline.dto.datastorage.security.StoragePermission;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionPathType;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionSid;
import com.epam.pipeline.entity.SecuredStorageEntity;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.security.PermissionsService;
import com.epam.pipeline.repository.datastorage.security.StoragePermissionRepository;
import com.epam.pipeline.security.acl.AclPermission;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static com.epam.pipeline.util.CustomMatchers.matches;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class StoragePermissionProviderManagerTest {

    private static final String STORAGE = "storage";
    private static final String ROOT_PATH = "";
    private static final String PARENT_PATH = "parent";
    private static final String PATH = PARENT_PATH + "/path";
    private static final String CHILD_PATH = PATH + "/child";
    private static final String NESTED_CHILD_PATH = CHILD_PATH + "/nestedchild";
    private static final String USER = "USER";
    private static final String OWNER = "OWNER";
    private static final String GROUP = "GROUP";
    private static final String ANOTHER_GROUP = "ANOTHER_GROUP";
    private static final List<String> GROUPS = Arrays.asList(GROUP, ANOTHER_GROUP);
    private static final StoragePermissionPathType TYPE = StoragePermissionPathType.FOLDER;
    private static final int READ_MASK = AclPermission.READ.getMask();
    private static final int NO_READ_MASK = AclPermission.NO_READ.getMask();
    private static final int WRITE_MASK = AclPermission.WRITE.getMask();
    private static final int NO_WRITE_MASK = AclPermission.NO_WRITE.getMask();
    private static final int EXECUTE_MASK = AclPermission.EXECUTE.getMask();
    private static final int NO_EXECUTE_MASK = AclPermission.NO_EXECUTE.getMask();
    private static final int OWN_MASK = AclPermission.OWNER.getMask();
    private static final int EMPTY_MASK = 0;
    private static final int FULL_MASK = READ_MASK | WRITE_MASK | EXECUTE_MASK;
    private static final int OWNER_MASK = FULL_MASK | OWN_MASK;
    private static final int DENY_MASK = NO_READ_MASK | NO_WRITE_MASK | NO_EXECUTE_MASK;
    private static final LocalDateTime CREATED = LocalDateTime.now();
    private static final DataStorageListing EMPTY_LISTING = new DataStorageListing(null, Collections.emptyList());
    private static final Function<String, DataStorageListing> MARKER_TO_EMPTY_LISTING_FUNCTION =
            ignored -> EMPTY_LISTING;

    private final StoragePermissionManager storagePermissionManager = mock(StoragePermissionManager.class);
    private final PermissionsService permissionsService = new PermissionsService();
    private final GrantPermissionManager grantPermissionManager = mock(GrantPermissionManager.class);
    private final AuthManager authManager = mock(AuthManager.class);
    private final StoragePermissionProviderManager manager = new StoragePermissionProviderManager(
            storagePermissionManager, permissionsService, grantPermissionManager, authManager);

    @Test
    public void loadMaskShouldFailForNonAuthorizedUser() {
        mockNonAuthorizedUser();
        final SecuredStorageEntity storage = storage();

        assertThrows(AccessDeniedException.class, () -> manager.loadMask(storage, PATH, TYPE));
    }

    @Test
    public void loadMaskShouldReturnOwnerMaskForStorageOwner() {
        mockOwner();
        final SecuredStorageEntity storage = storage();

        assertThat(manager.loadMask(storage, PATH, TYPE), is(OWNER_MASK));
    }

    @Test
    public void loadMaskShouldReturnOwnerMaskForAdmin() {
        mockAdmin();
        final SecuredStorageEntity storage = storage();

        assertThat(manager.loadMask(storage, PATH, TYPE), is(OWNER_MASK));
    }

    @Value
    private static class LoadMaskTestScenario {
        String description;
        List<StoragePermission> permissions;
        int storageMask;
        int expectedMask;
    }

    private final List<LoadMaskTestScenario> loadMaskTestScenarios = Arrays.asList(
            new LoadMaskTestScenario("storage all = all",
                    permissions(),
                    FULL_MASK,
                    FULL_MASK),
            new LoadMaskTestScenario("storage empty = empty",
                    permissions(),
                    EMPTY_MASK,
                    EMPTY_MASK),
            new LoadMaskTestScenario("storage empty + exact read = read",
                    permissions(permission(PATH, READ_MASK)),
                    EMPTY_MASK,
                    READ_MASK),
            new LoadMaskTestScenario("storage empty + exact user read + exact group no read = read",
                    permissions(
                            permission(PATH, READ_MASK),
                            permission(PATH, NO_READ_MASK).withSid(groupSid())),
                    EMPTY_MASK,
                    READ_MASK),
            new LoadMaskTestScenario("storage empty + exact group no read + exact user read = read",
                    permissions(
                            permission(PATH, NO_READ_MASK).withSid(groupSid()),
                            permission(PATH, READ_MASK)),
                    EMPTY_MASK,
                    READ_MASK),
            new LoadMaskTestScenario("storage empty + exact user no read + exact group read = no read",
                    permissions(permission(PATH, NO_READ_MASK),
                            permission(PATH, READ_MASK).withSid(groupSid())),
                    EMPTY_MASK,
                    NO_READ_MASK),
            new LoadMaskTestScenario("storage empty + exact group read + exact user no read = no read",
                    permissions(permission(PATH, READ_MASK).withSid(groupSid()),
                            permission(PATH, NO_READ_MASK)),
                    EMPTY_MASK,
                    NO_READ_MASK),
            new LoadMaskTestScenario("storage empty + exact group read + exact another group no read = read",
                    permissions(permission(PATH, READ_MASK).withSid(groupSid()),
                            permission(PATH, NO_READ_MASK).withSid(anotherGroupSid())),
                    EMPTY_MASK,
                    READ_MASK),
            new LoadMaskTestScenario("storage empty + exact group no read + exact another group read = read",
                    permissions(permission(PATH, NO_READ_MASK).withSid(groupSid()),
                            permission(PATH, READ_MASK).withSid(anotherGroupSid())),
                    EMPTY_MASK,
                    READ_MASK),
            new LoadMaskTestScenario("storage empty + parent read = read",
                    permissions(permission(PARENT_PATH, READ_MASK)),
                    EMPTY_MASK,
                    READ_MASK),
            new LoadMaskTestScenario("storage empty + exact no read = no read",
                    permissions(permission(PATH, NO_READ_MASK)),
                    EMPTY_MASK,
                    NO_READ_MASK),
            new LoadMaskTestScenario("storage read + exact no read = no read",
                    permissions(permission(PATH, NO_READ_MASK)),
                    READ_MASK,
                    NO_READ_MASK),
            new LoadMaskTestScenario("storage read + path no read = no read",
                    permissions(permission(PARENT_PATH, NO_READ_MASK)),
                    READ_MASK,
                    NO_READ_MASK),
            new LoadMaskTestScenario("storage nothing + exact read = read + no write + no execute",
                    permissions(permission(PATH, READ_MASK)),
                    NO_READ_MASK | NO_WRITE_MASK | NO_EXECUTE_MASK,
                    READ_MASK | NO_WRITE_MASK | NO_EXECUTE_MASK),
            new LoadMaskTestScenario("storage empty + exact read + parent write = read + write",
                    permissions(
                            permission(PARENT_PATH, WRITE_MASK),
                            permission(PATH, READ_MASK)),
                    EMPTY_MASK,
                    READ_MASK | WRITE_MASK),
            new LoadMaskTestScenario("storage no read + exact read + parent write = read + write",
                    permissions(
                            permission(PARENT_PATH, WRITE_MASK),
                            permission(PATH, READ_MASK)),
                    NO_READ_MASK,
                    READ_MASK | WRITE_MASK));

    @Test
    public void loadMaskShouldReturnIntermediatelyMergedMaskForRegularUser() {
        mockUser();
        final SecuredStorageEntity storage = storage();
        for (final LoadMaskTestScenario scenario : loadMaskTestScenarios) {
            System.out.println(scenario.getDescription());
            mockStoragePermissions(storage, scenario.getStorageMask());
            mockPermissions(storage, scenario.getPermissions());

            assertThat(scenario.getDescription(), manager.loadMask(storage, PATH, TYPE), is(scenario.getExpectedMask()));
        }
    }

    @Value
    @RequiredArgsConstructor
    private static class ApplyTestScenario {
        String description;
        List<StoragePermission> permissions;
        List<StoragePermission> childPermissions;
        Set<StoragePermissionRepository.StorageItem> readAllowedChildPermissions;
        int storageMask;
        DataStorageListing inputListing;
        DataStorageListing expectedListing;

        public ApplyTestScenario(final String description,
                                 final List<StoragePermission> permissions,
                                 final int storageMask,
                                 final DataStorageListing inputListing,
                                 final DataStorageListing expectedListing) {
            this(description, permissions, Collections.emptyList(), Collections.emptySet(), storageMask, inputListing,
                    expectedListing);
        }
    }

    private final List<ApplyTestScenario> applyTestScenarios = Arrays.asList(
            new ApplyTestScenario(
                    "storage empty + exact all = all",
                    permissions(permission(PATH, FULL_MASK)),
                    EMPTY_MASK,
                    listing(Collections.emptyList()),
                    listing(FULL_MASK, Collections.emptyList())),
            new ApplyTestScenario(
                    "storage empty + exact all = listing all + child all",
                    permissions(permission(PATH, FULL_MASK)),
                    EMPTY_MASK,
                    listing(Collections.singletonList(
                            folder(CHILD_PATH))),
                    listing(FULL_MASK, Collections.singletonList(
                            folder(CHILD_PATH, FULL_MASK)))),
            new ApplyTestScenario(
                    "storage empty + exact user all + exact group nothing = listing all + child all",
                    permissions(
                            permission(PATH, FULL_MASK),
                            permission(PATH, DENY_MASK).withSid(groupSid())),
                    EMPTY_MASK,
                    listing(Collections.singletonList(
                            folder(CHILD_PATH))),
                    listing(FULL_MASK, Collections.singletonList(
                            folder(CHILD_PATH, FULL_MASK)))),
            new ApplyTestScenario(
                    "storage empty + exact group nothing + exact user all = listing all + child all",
                    permissions(
                            permission(PATH, DENY_MASK).withSid(groupSid()),
                            permission(PATH, FULL_MASK)),
                    EMPTY_MASK,
                    listing(Collections.singletonList(
                            folder(CHILD_PATH))),
                    listing(FULL_MASK, Collections.singletonList(
                            folder(CHILD_PATH, FULL_MASK)))),
            new ApplyTestScenario(
                    "storage empty + exact group full + exact user nothing = forbidden listing",
                    permissions(
                            permission(PATH, FULL_MASK).withSid(groupSid()),
                            permission(PATH, DENY_MASK)),
                    EMPTY_MASK,
                    listing(Collections.singletonList(
                            folder(CHILD_PATH))),
                    forbiddenListing()),
            new ApplyTestScenario(
                    "storage empty + exact user nothing + exact group full = forbidden listing",
                    permissions(
                            permission(PATH, DENY_MASK),
                            permission(PATH, FULL_MASK).withSid(groupSid())),
                    EMPTY_MASK,
                    listing(Collections.singletonList(
                            folder(CHILD_PATH))),
                    forbiddenListing()),
            new ApplyTestScenario(
                    "storage empty + exact group nothing + exact another group full = listing full + child full",
                    permissions(
                            permission(PATH, DENY_MASK).withSid(groupSid()),
                            permission(PATH, FULL_MASK).withSid(anotherGroupSid())),
                    EMPTY_MASK,
                    listing(Collections.singletonList(
                            folder(CHILD_PATH))),
                    listing(FULL_MASK, Collections.singletonList(
                            folder(CHILD_PATH, FULL_MASK)))),
            new ApplyTestScenario(
                    "storage empty + exact group full + exact another group nothing = listing full + child full",
                    permissions(
                            permission(PATH, FULL_MASK).withSid(groupSid()),
                            permission(PATH, DENY_MASK).withSid(anotherGroupSid())),
                    EMPTY_MASK,
                    listing(Collections.singletonList(
                            folder(CHILD_PATH))),
                    listing(FULL_MASK, Collections.singletonList(
                            folder(CHILD_PATH, FULL_MASK)))));

    @Test
    public void applyShouldApplyPermissionMaskAndFilterNonReadAllowedItemsForRegularUser() {
        mockUser();
        final SecuredStorageEntity storage = storage();
        for (final ApplyTestScenario scenario : applyTestScenarios) {
            System.out.println(scenario.getDescription());
            mockStoragePermissions(storage, scenario.getStorageMask());
            mockPermissions(storage, scenario.getPermissions(), scenario.getChildPermissions(),
                    scenario.getReadAllowedChildPermissions());

            final Optional<DataStorageListing> actualListing = manager.apply(storage, PATH,
                    ignored -> scenario.getInputListing());

            assertListing(scenario.getDescription(), actualListing, scenario.getExpectedListing());
        }
    }

    // TODO: 27.08.2021 Add negative test case scenarios

    private DataStorageListing listing(final List<AbstractDataStorageItem> items) {
        return listing(EMPTY_MASK, items);
    }

    private DataStorageListing listing(final int mask, final List<AbstractDataStorageItem> items) {
        final DataStorageListing listing = new DataStorageListing(null, items);
        listing.setMask(permissionsService.mergeMask(mask));
        return listing;
    }

    private DataStorageListing forbiddenListing() {
        return null;
    }

    private AbstractDataStorageItem folder(final String path) {
        return folder(path, EMPTY_MASK);
    }

    private AbstractDataStorageItem folder(final String path, final int mask) {
        final AbstractDataStorageItem item = new DataStorageFolder();
        item.setPath(path);
        item.setMask(permissionsService.mergeMask(mask));
        return item;
    }

    private void mockStoragePermissions(final SecuredStorageEntity storage, final int mask) {
        Mockito.reset(grantPermissionManager);
        doReturn(mask)
                .when(grantPermissionManager)
                .getPermissionsMask(argThat(matches(storage::equals)), eq(false), eq(true));
    }

    private void mockPermissions(final SecuredStorageEntity storage,
                                 final List<StoragePermission> permissions) {
        mockPermissions(storage, permissions, Collections.emptyList());
    }

    private void mockPermissions(final SecuredStorageEntity storage,
                                 final List<StoragePermission> permissions,
                                 final List<StoragePermission> childPermissions) {
        mockPermissions(storage, permissions, childPermissions, Collections.emptySet());
    }

    private void mockPermissions(final SecuredStorageEntity storage,
                                 final List<StoragePermission> permissions,
                                 final List<StoragePermission> childPermissions,
                                 final Set<StoragePermissionRepository.StorageItem> readAllowedChildPermissions) {
        Mockito.reset(storagePermissionManager);
        doReturn(permissions)
                .when(storagePermissionManager)
                .load(storage.getRootId(), PATH, TYPE, USER, GROUPS);
        doReturn(childPermissions)
                .when(storagePermissionManager)
                .loadImmediateChildPermissions(storage.getRootId(), PATH, USER, GROUPS);
        doReturn(readAllowedChildPermissions)
                .when(storagePermissionManager)
                .loadReadAllowedImmediateChildItems(storage.getRootId(), PATH, USER, GROUPS);
    }

    private List<StoragePermission> permissions(final StoragePermission... permissions) {
        return Arrays.asList(permissions);
    }

    private StoragePermission permission(final String path, final int mask) {
        return new StoragePermission(path, TYPE, userSid(), mask, CREATED);
    }

    private StoragePermissionSid userSid() {
        return StoragePermissionSid.user(USER);
    }

    private StoragePermissionSid groupSid() {
        return groupSid(GROUP);
    }

    private StoragePermissionSid anotherGroupSid() {
        return groupSid(GROUP);
    }

    private StoragePermissionSid groupSid(final String group) {
        return StoragePermissionSid.group(group);
    }

    private void mockNonAuthorizedUser() {
        doThrow(AccessDeniedException.class).when(authManager).getCurrentUserOrFail();
        doReturn(null).when(authManager).getCurrentUser();
    }

    private void mockOwner() {
        mockUser(new PipelineUser(OWNER));
    }

    private void mockAdmin() {
        final PipelineUser user = new PipelineUser(USER);
        user.setAdmin(true);
        mockUser(user);
    }

    private void mockUser() {
        final PipelineUser user = new PipelineUser(USER);
        user.setRoles(Collections.singletonList(new Role(GROUP)));
        user.setGroups(Collections.singletonList(ANOTHER_GROUP));
        mockUser(user);
    }

    private void mockUser(final PipelineUser user) {
        doReturn(user).when(authManager).getCurrentUserOrFail();
        doReturn(user).when(authManager).getCurrentUser();
    }

    private SecuredStorageEntity storage() {
        final S3bucketDataStorage storage = new S3bucketDataStorage();
        storage.setOwner(OWNER);
        storage.setPath(STORAGE);
        return storage;
    }

    private void assertListing(final String reason,
                               final Optional<DataStorageListing> optionalActual,
                               final DataStorageListing expected) {
        if (expected == null) {
            assertFalse(reason, optionalActual.isPresent());
        } else {
            assertTrue(reason, optionalActual.isPresent());
            final DataStorageListing actual = optionalActual.get();
            assertThat(reason, actual.getMask(), is(expected.getMask()));
            assertThat(reason, actual.getNextPageMarker(), is(expected.getNextPageMarker()));
            assertThat(reason, actual.getResults().size(), is(expected.getResults().size()));
            for (int i = 0; i < actual.getResults().size(); i++) {
                assertThat(reason, actual.getResults().get(i).getPath(), is(expected.getResults().get(i).getPath()));
                assertThat(reason, actual.getResults().get(i).getType(), is(expected.getResults().get(i).getType()));
                assertThat(reason, actual.getResults().get(i).getMask(), is(expected.getResults().get(i).getMask()));
            }
        }
    }
}
