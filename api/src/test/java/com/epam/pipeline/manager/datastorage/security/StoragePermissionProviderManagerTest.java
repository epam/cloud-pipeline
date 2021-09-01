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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class StoragePermissionProviderManagerTest {

    private static final String STORAGE = "storage";
    private static final String PARENT_PATH = "parent";
    private static final String PATH = PARENT_PATH + "/path";
    private static final String CHILD_PATH = PATH + "/child";
    private static final String ANOTHER_CHILD_PATH = PATH + "/anotherchild";
    private static final String THIRD_CHILD_PATH = PATH + "/thirdchild";
    private static final String FOURTH_CHILD_PATH = PATH + "/fourthchild";
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
    private static final DataStorageListing NO_LISTING = null;
    private static final String MARKER = "MARKER";
    private static final String ANOTHER_MARKER = "ANOTHER_MARKER";
    private static final String NO_MARKER = null;

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

        assertThrows(AccessDeniedException.class, () -> manager.loadExtendedMask(storage, PATH, TYPE));
    }

    @Test
    public void loadMaskShouldReturnOwnerMaskForStorageOwner() {
        mockOwner();
        final SecuredStorageEntity storage = storage();

        assertThat(manager.loadExtendedMask(storage, PATH, TYPE), is(OWNER_MASK));
    }

    @Test
    public void loadMaskShouldReturnOwnerMaskForAdmin() {
        mockAdmin();
        final SecuredStorageEntity storage = storage();

        assertThat(manager.loadExtendedMask(storage, PATH, TYPE), is(OWNER_MASK));
    }

    @Value
    private static class LoadExtendedMaskTestCase {
        String description;
        List<StoragePermission> permissions;
        int storageMask;
        int expectedMask;
    }

    private final List<LoadExtendedMaskTestCase> loadExtendedMaskTestCases = Arrays.asList(
            new LoadExtendedMaskTestCase("storage all = all",
                    permissions(),
                    FULL_MASK,
                    FULL_MASK),
            new LoadExtendedMaskTestCase("storage empty = empty",
                    permissions(),
                    EMPTY_MASK,
                    EMPTY_MASK),
            new LoadExtendedMaskTestCase("storage empty + exact read = read",
                    permissions(permission(PATH, READ_MASK)),
                    EMPTY_MASK,
                    READ_MASK),
            new LoadExtendedMaskTestCase("storage empty + exact no read = no read",
                    permissions(permission(PATH, NO_READ_MASK)),
                    EMPTY_MASK,
                    NO_READ_MASK),
            new LoadExtendedMaskTestCase("storage empty + exact user read + exact group no read = read",
                    permissions(
                            permission(PATH, READ_MASK),
                            permission(PATH, NO_READ_MASK).withSid(groupSid())),
                    EMPTY_MASK,
                    READ_MASK),
            new LoadExtendedMaskTestCase("storage empty + exact group no read + exact user read = read",
                    permissions(
                            permission(PATH, NO_READ_MASK).withSid(groupSid()),
                            permission(PATH, READ_MASK)),
                    EMPTY_MASK,
                    READ_MASK),
            new LoadExtendedMaskTestCase("storage empty + exact user no read + exact group read = no read",
                    permissions(permission(PATH, NO_READ_MASK),
                            permission(PATH, READ_MASK).withSid(groupSid())),
                    EMPTY_MASK,
                    NO_READ_MASK),
            new LoadExtendedMaskTestCase("storage empty + exact group read + exact user no read = no read",
                    permissions(permission(PATH, READ_MASK).withSid(groupSid()),
                            permission(PATH, NO_READ_MASK)),
                    EMPTY_MASK,
                    NO_READ_MASK),
            new LoadExtendedMaskTestCase("storage empty + exact group read + exact another group no read = read",
                    permissions(permission(PATH, READ_MASK).withSid(groupSid()),
                            permission(PATH, NO_READ_MASK).withSid(anotherGroupSid())),
                    EMPTY_MASK,
                    READ_MASK),
            new LoadExtendedMaskTestCase("storage empty + exact group no read + exact another group read = read",
                    permissions(permission(PATH, NO_READ_MASK).withSid(groupSid()),
                            permission(PATH, READ_MASK).withSid(anotherGroupSid())),
                    EMPTY_MASK,
                    READ_MASK),
            new LoadExtendedMaskTestCase("storage empty + parent read = read",
                    permissions(permission(PARENT_PATH, READ_MASK)),
                    EMPTY_MASK,
                    READ_MASK),
            new LoadExtendedMaskTestCase("storage read + exact no read = no read",
                    permissions(permission(PATH, NO_READ_MASK)),
                    READ_MASK,
                    NO_READ_MASK),
            new LoadExtendedMaskTestCase("storage read + parent no read = no read",
                    permissions(permission(PARENT_PATH, NO_READ_MASK)),
                    READ_MASK,
                    NO_READ_MASK),
            new LoadExtendedMaskTestCase("storage nothing + exact read = read + no write + no execute",
                    permissions(permission(PATH, READ_MASK)),
                    DENY_MASK,
                    READ_MASK | NO_WRITE_MASK | NO_EXECUTE_MASK),
            new LoadExtendedMaskTestCase("storage empty + exact read + parent write = read + write",
                    permissions(
                            permission(PARENT_PATH, WRITE_MASK),
                            permission(PATH, READ_MASK)),
                    EMPTY_MASK,
                    READ_MASK | WRITE_MASK),
            new LoadExtendedMaskTestCase("storage no read + exact read + parent write = read + write",
                    permissions(
                            permission(PARENT_PATH, WRITE_MASK),
                            permission(PATH, READ_MASK)),
                    NO_READ_MASK,
                    READ_MASK | WRITE_MASK),
            new LoadExtendedMaskTestCase("storage nothing + exact read + parent write = read + write + no execute",
                    permissions(
                            permission(PARENT_PATH, WRITE_MASK),
                            permission(PATH, READ_MASK)),
                    DENY_MASK,
                    READ_MASK | WRITE_MASK | NO_EXECUTE_MASK));

    @Test
    public void loadExtendedMaskShouldReturnMergedExactAndParentMasksForRegularUser() {
        mockUser();
        final SecuredStorageEntity storage = storage();
        for (final LoadExtendedMaskTestCase testCase : loadExtendedMaskTestCases) {
            System.out.println(testCase.getDescription());
            mockStoragePermissions(storage, testCase.getStorageMask());
            mockPermissions(storage, testCase.getPermissions());

            assertThat(testCase.getDescription(),
                    manager.loadExtendedMask(storage, PATH, TYPE), is(testCase.getExpectedMask()));
        }
    }

    @Value
    @RequiredArgsConstructor
    private static class ApplyTestCase {
        String description;
        List<StoragePermission> permissions;
        List<StoragePermission> childPermissions;
        Set<StoragePermissionRepository.StorageItem> readAllowedChildPermissions;
        int storageMask;
        DataStorageListing inputListing;
        DataStorageListing expectedListing;

        public ApplyTestCase(final String description,
                             final List<StoragePermission> permissions,
                             final int storageMask,
                             final DataStorageListing inputListing,
                             final DataStorageListing expectedListing) {
            this(description, permissions, Collections.emptyList(), Collections.emptySet(), storageMask, inputListing,
                    expectedListing);
        }
    }

    private final List<ApplyTestCase> applyTestCases = Arrays.asList(
            new ApplyTestCase(
                    "storage empty + exact all = all",
                    permissions(permission(PATH, FULL_MASK)),
                    EMPTY_MASK,
                    listing(),
                    listing(FULL_MASK)),
            new ApplyTestCase(
                    "storage empty + exact all = listing all + child all",
                    permissions(permission(PATH, FULL_MASK)),
                    EMPTY_MASK,
                    listing(
                            folder(CHILD_PATH)),
                    listing(FULL_MASK,
                            folder(CHILD_PATH, FULL_MASK))),
            new ApplyTestCase(
                    "storage empty + exact user all + exact group nothing = listing all + child all",
                    permissions(
                            permission(PATH, FULL_MASK),
                            permission(PATH, DENY_MASK).withSid(groupSid())),
                    EMPTY_MASK,
                    listing(
                            folder(CHILD_PATH)),
                    listing(FULL_MASK,
                            folder(CHILD_PATH, FULL_MASK))),
            new ApplyTestCase(
                    "storage empty + exact group nothing + exact user all = listing all + child all",
                    permissions(
                            permission(PATH, DENY_MASK).withSid(groupSid()),
                            permission(PATH, FULL_MASK)),
                    EMPTY_MASK,
                    listing(
                            folder(CHILD_PATH)),
                    listing(FULL_MASK,
                            folder(CHILD_PATH, FULL_MASK))),
            new ApplyTestCase(
                    "storage empty + exact group full + exact user nothing = no listing",
                    permissions(
                            permission(PATH, FULL_MASK).withSid(groupSid()),
                            permission(PATH, DENY_MASK)),
                    EMPTY_MASK,
                    listing(
                            folder(CHILD_PATH)),
                    NO_LISTING),
            new ApplyTestCase(
                    "storage empty + exact user nothing + exact group full = no listing",
                    permissions(
                            permission(PATH, DENY_MASK),
                            permission(PATH, FULL_MASK).withSid(groupSid())),
                    EMPTY_MASK,
                    listing(
                            folder(CHILD_PATH)),
                    NO_LISTING),
            new ApplyTestCase(
                    "storage empty + exact group nothing + exact another group full = listing full + child full",
                    permissions(
                            permission(PATH, DENY_MASK).withSid(groupSid()),
                            permission(PATH, FULL_MASK).withSid(anotherGroupSid())),
                    EMPTY_MASK,
                    listing(
                            folder(CHILD_PATH)),
                    listing(FULL_MASK,
                            folder(CHILD_PATH, FULL_MASK))),
            new ApplyTestCase(
                    "storage empty + exact group full + exact another group nothing = listing full + child full",
                    permissions(
                            permission(PATH, FULL_MASK).withSid(groupSid()),
                            permission(PATH, DENY_MASK).withSid(anotherGroupSid())),
                    EMPTY_MASK,
                    listing(
                            folder(CHILD_PATH)),
                    listing(FULL_MASK,
                            folder(CHILD_PATH, FULL_MASK))),
            new ApplyTestCase(
                    "storage empty + child allowed = listing empty + child read",
                    permissions(),
                    permissions(),
                    allowedChildren(
                            folderItem(CHILD_PATH)),
                    EMPTY_MASK,
                    listing(
                            folder(CHILD_PATH)),
                    listing(EMPTY_MASK,
                            folder(CHILD_PATH, READ_MASK))),
            new ApplyTestCase(
                    "storage empty + child read + child allowed = listing empty + child read",
                    permissions(),
                    permissions(
                            permission(CHILD_PATH, READ_MASK)),
                    allowedChildren(
                            folderItem(CHILD_PATH)),
                    EMPTY_MASK,
                    listing(
                            folder(CHILD_PATH)),
                    listing(EMPTY_MASK,
                            folder(CHILD_PATH, READ_MASK))),
            new ApplyTestCase(
                    "storage empty + child full + child allowed = listing empty + child full",
                    permissions(),
                    permissions(
                            permission(CHILD_PATH, FULL_MASK)),
                    allowedChildren(
                            folderItem(CHILD_PATH)),
                    EMPTY_MASK,
                    listing(
                            folder(CHILD_PATH)),
                    listing(EMPTY_MASK,
                            folder(CHILD_PATH, FULL_MASK))),
            new ApplyTestCase(
                    "storage empty + child allowed + another child not allowed = listing empty + child read",
                    permissions(),
                    permissions(),
                    allowedChildren(
                            folderItem(CHILD_PATH)),
                    EMPTY_MASK,
                    listing(
                            folder(CHILD_PATH),
                            folder(ANOTHER_CHILD_PATH)),
                    listing(EMPTY_MASK,
                            folder(CHILD_PATH, READ_MASK))),
            new ApplyTestCase(
                    "storage empty + child not allowed = no listing",
                    permissions(),
                    permissions(),
                    allowedChildren(),
                    EMPTY_MASK,
                    listing(
                            folder(CHILD_PATH)),
                    NO_LISTING));

    @Test
    public void applyShouldReturnFilteredItemsWithMergedExactAndParentAndChildMasksForRegularUser() {
        mockUser();
        final SecuredStorageEntity storage = storage();
        for (final ApplyTestCase testCase : applyTestCases) {
            System.out.println(testCase.getDescription());
            mockStoragePermissions(storage, testCase.getStorageMask());
            mockPermissions(storage, testCase.getPermissions(), testCase.getChildPermissions(),
                    testCase.getReadAllowedChildPermissions());

            final Optional<DataStorageListing> actualListing = manager.apply(storage, PATH,
                    ignored -> testCase.getInputListing());

            assertListing(testCase.getDescription(), actualListing, testCase.getExpectedListing());
        }
    }

    @Test
    public void applyShouldRequestConsequentDataStorageListingIfSomeChildItemsWereFilteredOut() {
        mockUser();
        final SecuredStorageEntity storage = storage();
        mockStoragePermissions(storage, EMPTY_MASK);
        mockPermissions(storage,
                permissions(),
                permissions(),
                allowedChildren(
                        folderItem(CHILD_PATH),
                        folderItem(THIRD_CHILD_PATH)));
        class DataStorageListingProvider implements Function<String, DataStorageListing> {
            @Override
            public DataStorageListing apply(final String marker) {
                return Objects.equals(marker, NO_MARKER)
                        ? listing(EMPTY_MASK, MARKER,
                        folder(CHILD_PATH),
                        folder(ANOTHER_CHILD_PATH))
                        : listing(EMPTY_MASK, ANOTHER_MARKER,
                        folder(THIRD_CHILD_PATH),
                        folder(FOURTH_CHILD_PATH));
            }
        }
        final DataStorageListingProvider listingProvider = spy(new DataStorageListingProvider());

        final Optional<DataStorageListing> actualListing = manager.apply(storage, PATH, listingProvider);

        verify(listingProvider, times(1)).apply(NO_MARKER);
        verify(listingProvider, times(1)).apply(MARKER);
        verify(listingProvider, never()).apply(ANOTHER_MARKER);
        assertListing(actualListing, listing(EMPTY_MASK, ANOTHER_MARKER,
                folder(CHILD_PATH, READ_MASK),
                folder(THIRD_CHILD_PATH, READ_MASK)));
    }

    private SecuredStorageEntity storage() {
        final S3bucketDataStorage storage = new S3bucketDataStorage();
        storage.setOwner(OWNER);
        storage.setPath(STORAGE);
        return storage;
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
        return groupSid(ANOTHER_GROUP);
    }

    private StoragePermissionSid groupSid(final String group) {
        return StoragePermissionSid.group(group);
    }

    private DataStorageListing listing(final AbstractDataStorageItem... items) {
        return listing(Arrays.asList(items));
    }

    private DataStorageListing listing(final List<AbstractDataStorageItem> items) {
        return listing(EMPTY_MASK, items);
    }

    private DataStorageListing listing(final int mask, final AbstractDataStorageItem... items) {
        return listing(mask, Arrays.asList(items));
    }

    private DataStorageListing listing(final int mask, final List<AbstractDataStorageItem> items) {
        return listing(mask, null, items);
    }

    private DataStorageListing listing(final int mask, final String marker, final AbstractDataStorageItem... items) {
        return listing(mask, marker, Arrays.asList(items));
    }

    private DataStorageListing listing(final int mask, final String marker, final List<AbstractDataStorageItem> items) {
        final DataStorageListing listing = new DataStorageListing(marker, items);
        listing.setMask(permissionsService.mergeMask(mask));
        return listing;
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

    private StoragePermissionRepository.StorageItem folderItem(final String path) {
        return new StoragePermissionRepository.StorageItemImpl(path, StoragePermissionPathType.FOLDER);
    }

    private Set<StoragePermissionRepository.StorageItem> allowedChildren(
            final StoragePermissionRepository.StorageItem... items) {
        return Arrays.stream(items).collect(Collectors.toSet());
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
                                 final Set<StoragePermissionRepository.StorageItem> allowedChildren) {
        Mockito.reset(storagePermissionManager);
        doReturn(permissions)
                .when(storagePermissionManager)
                .load(storage.getRootId(), PATH, TYPE, USER, GROUPS);
        doReturn(childPermissions)
                .when(storagePermissionManager)
                .loadImmediateChildPermissions(storage.getRootId(), PATH, USER, GROUPS);
        doReturn(allowedChildren)
                .when(storagePermissionManager)
                .loadReadAllowedImmediateChildItems(storage.getRootId(), PATH, USER, GROUPS);
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

    private void assertListing(final Optional<DataStorageListing> optionalActual,
                               final DataStorageListing expected) {
        assertListing("", optionalActual, expected);
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
