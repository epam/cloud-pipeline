package com.epam.pipeline.manager.datastorage.security;

import com.epam.pipeline.dto.datastorage.security.StoragePermissionSidType;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.SecuredStorageEntity;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.dto.datastorage.security.StoragePermission;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionPathType;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionSid;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageItemType;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.security.PermissionsService;
import com.epam.pipeline.repository.datastorage.security.StoragePermissionRepository;
import com.epam.pipeline.security.acl.AclPermission;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.acls.model.Permission;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class StoragePermissionProviderManager {

    private final StoragePermissionManager storagePermissionManager;
    private final PermissionsService permissionsService;
    private final GrantPermissionManager grantPermissionManager;
    private final AuthManager authManager;

    public boolean isReadAllowed(final SecuredStorageEntity storage,
                                 final String path,
                                 final StoragePermissionPathType type) {
        return isAllowed(storage, path, type, AclPermission.READ);
    }

    public boolean isReadNotAllowed(final SecuredStorageEntity storage,
                                    final String path,
                                    final StoragePermissionPathType type) {
        return !isReadAllowed(storage, path, type);
    }

    public boolean isWriteAllowed(final SecuredStorageEntity storage, final String path,
                                  final StoragePermissionPathType type) {
        return isAllowed(storage, path, type, AclPermission.WRITE);
    }

    public boolean isWriteNotAllowed(final SecuredStorageEntity storage,
                                     final String path,
                                     final StoragePermissionPathType type) {
        return !isWriteAllowed(storage, path, type);
    }

    public boolean isAllowed(final SecuredStorageEntity storage,
                             final String path,
                             final StoragePermissionPathType type,
                             final Permission... permissions) {
        final String absolutePath = Optional.ofNullable(storage.resolveAbsolutePath(path)).orElse(StringUtils.EMPTY);
        final int mask = getMask(storage, absolutePath, type);
        return isAllowed(mask, permissions);
    }

    private boolean isAllowed(final int mask, final Permission... permissions) {
        return Arrays.stream(permissions).allMatch(permission -> isAllowed(mask, permission));
    }

    private boolean isAllowed(final int mask, final Permission permission) {
        return permission instanceof AclPermission
                && (permission.getMask() & mask) == permission.getMask()
                && (((AclPermission) permission).getDenyPermission().getMask() & mask) == 0;
    }

    public boolean isRecursiveReadAllowed(final SecuredStorageEntity storage, final String path) {
        return isRecursiveAllowed(storage, path, AclPermission.READ);
    }

    public boolean isRecursiveReadNotAllowed(final SecuredStorageEntity storage, final String path) {
        return !isRecursiveReadAllowed(storage, path);
    }

    public boolean isRecursiveWriteAllowed(final SecuredStorageEntity storage, final String path) {
        return isRecursiveAllowed(storage, path, AclPermission.WRITE);
    }

    public boolean isRecursiveWriteNotAllowed(final SecuredStorageEntity storage, final String path) {
        return !isRecursiveWriteAllowed(storage, path);
    }

    public boolean isRecursiveReadWriteAllowed(final SecuredStorageEntity storage, final String path) {
        return isRecursiveAllowed(storage, path, AclPermission.READ, AclPermission.WRITE);
    }

    public boolean isRecursiveReadWriteNotAllowed(final SecuredStorageEntity storage, final String path) {
        return !isRecursiveReadWriteAllowed(storage, path);
    }

    public boolean isRecursiveAllowed(final SecuredStorageEntity storage,
                                      final String path,
                                      final Permission... permissions) {
        final PipelineUser user = loadUser();
        return isAdminOrOwner(storage, user)
                || isAllowed(storage, path, StoragePermissionPathType.FOLDER, permissions)
                && storagePermissionManager.loadRecursiveMask(storage.getRootId(), path, user.getUserName(), groups(user))
                .map(mask -> isAllowed(mask, permissions))
                .orElse(true);
    }

    public Optional<DataStorageListing> apply(final SecuredStorageEntity storage,
                                              final String path,
                                              final Function<String, DataStorageListing> getListingByMarker) {
        final String absolutePath = Optional.ofNullable(storage.resolveAbsolutePath(path)).orElse(StringUtils.EMPTY);
        final int folderMask = getMask(storage, absolutePath, StoragePermissionPathType.FOLDER);
        final DataStorageListing listing = new DataStorageListing();
        listing.setMask(getSimpleMask(folderMask));
        final PipelineUser user = authManager.getCurrentUser();
        if (user == null) {
            return Optional.empty();
        }
        if (isAdminOrOwner(storage, user)) {
            final DataStorageListing currentListing = getListingByMarker.apply(null);
            listing.setNextPageMarker(currentListing.getNextPageMarker());
            listing.setResults(ListUtils.emptyIfNull(currentListing.getResults()).stream()
                    .map(item -> withMask(item, AbstractSecuredEntity.ALL_PERMISSIONS_MASK_FULL))
                    .collect(Collectors.toList()));
            return Optional.of(listing);
        }
        final List<String> groups = groups(user);
        final Map<StoragePermissionRepository.StorageItem, Integer> masks = storagePermissionManager
                .loadImmediateChildPermissions(storage.getRootId(), absolutePath, user.getUserName(), groups)
                .stream()
                .collect(Collectors.groupingBy(this::getStorageItem,
                        Collectors.collectingAndThen(Collectors.toList(), this::mergeExactMask)));
        if (isAllowed(folderMask, AclPermission.READ)) {
            final DataStorageListing currentListing = getListingByMarker.apply(null);
            listing.setNextPageMarker(currentListing.getNextPageMarker());
            listing.setResults(ListUtils.emptyIfNull(currentListing.getResults()).stream()
                    .map(item -> {
                        int mask = masks.getOrDefault(getStorageItem(item), 0);
                        mask = mergeParentMask(mask, folderMask);
                        return withMask(item, mask);
                    })
                    .collect(Collectors.toList()));
        } else {
            final Set<StoragePermissionRepository.StorageItem> readAllowedItems =
                    storagePermissionManager.loadReadAllowedImmediateChildItems(storage.getRootId(), absolutePath,
                            user.getUserName(), groups);
            if (readAllowedItems.isEmpty()) {
                return Optional.empty();
            }
            DataStorageListing currentListing = getListingByMarker.apply(null);
            int filteredOutItemsNumber = 0;
            final List<AbstractDataStorageItem> items = new ArrayList<>();
            for (final AbstractDataStorageItem item : ListUtils.emptyIfNull(currentListing.getResults())) {
                final StoragePermissionRepository.StorageItemImpl storageItem = getStorageItem(item);
                if (readAllowedItems.contains(storageItem)) {
                    int mask = masks.getOrDefault(storageItem, 0);
                    mask = mergeParentMask(mask, folderMask);
                    mask |= AclPermission.READ.getMask();
                    mask &= ~AclPermission.NO_READ.getMask();
                    items.add(withMask(item, mask));
                } else {
                    filteredOutItemsNumber += item.getType() == DataStorageItemType.File
                            ? Math.max(CollectionUtils.size(((DataStorageFile) item).getVersions()), 1) : 1;
                }
            }
            while (filteredOutItemsNumber > 0 && currentListing.getNextPageMarker() != null) {
                currentListing = getListingByMarker.apply(currentListing.getNextPageMarker());
                for (final AbstractDataStorageItem item : ListUtils.emptyIfNull(currentListing.getResults())) {
                    final StoragePermissionRepository.StorageItemImpl storageItem = getStorageItem(item);
                    if (readAllowedItems.contains(storageItem)) {
                        int mask = masks.getOrDefault(storageItem, 0);
                        mask = mergeParentMask(mask, folderMask);
                        mask |= AclPermission.READ.getMask();
                        mask &= ~AclPermission.NO_READ.getMask();
                        items.add(withMask(item, mask));
                        filteredOutItemsNumber -= item.getType() == DataStorageItemType.File
                                ? Math.max(CollectionUtils.size(((DataStorageFile) item).getVersions()), 1) : 1;
                    }
                }
            }
            listing.setNextPageMarker(currentListing.getNextPageMarker());
            listing.setResults(items);
        }
        return Optional.of(listing);
    }

    private AbstractDataStorageItem withMask(final AbstractDataStorageItem item, final int mask) {
        item.setMask(getSimpleMask(mask));
        return item;
    }

    private int getSimpleMask(final int mask) {
        return permissionsService.mergeMask(mask);
    }

    public int loadMask(final SecuredStorageEntity storage,
                        final String path,
                        final StoragePermissionPathType type) {
        final String absolutePath = Optional.ofNullable(storage.resolveAbsolutePath(path)).orElse(StringUtils.EMPTY);
        return getMask(storage, absolutePath, type);
    }

    private int getMask(final SecuredStorageEntity storage,
                        final String path,
                        final StoragePermissionPathType type) {
        final PipelineUser user = loadUser();
        return isAdminOrOwner(storage, user)
                ? AbstractSecuredEntity.ALL_PERMISSIONS_MASK_FULL
                : getMask(storage, path, type, user);
    }

    private int getMask(final SecuredStorageEntity storage,
                        final String path,
                        final StoragePermissionPathType type,
                        final PipelineUser user) {
        final List<String> groups = groups(user);
        final Map<StoragePermissionRepository.StorageItem, List<StoragePermission>> permissions =
                storagePermissionManager.load(storage.getRootId(), path, type, user.getUserName(), groups)
                        .stream()
                        .collect(Collectors.groupingBy(this::getStorageItem, Collectors.toCollection(ArrayList::new)));
        final Stream<Integer> pathMasks = permissions.keySet().stream()
                .sorted(Comparator.comparing(StoragePermissionRepository.StorageItem::getStoragePath,
                        Comparator.reverseOrder()))
                .map(permissions::get)
                .map(this::mergeExactMask);
        return Stream.concat(pathMasks, Stream.of(getStorageMask((AbstractSecuredEntity) storage)))
                .reduce(this::mergeParentMask)
                .orElse(0);
    }

    private StoragePermissionRepository.StorageItemImpl getStorageItem(final AbstractDataStorageItem item) {
        return new StoragePermissionRepository.StorageItemImpl(item.getPath(),
                StoragePermissionPathType.from(item.getType()));
    }

    private StoragePermissionRepository.StorageItemImpl getStorageItem(final StoragePermission item) {
        return new StoragePermissionRepository.StorageItemImpl(item.getPath(), item.getType());
    }

    private int getStorageMask(final AbstractSecuredEntity storage) {
        return grantPermissionManager.getPermissionsMask(storage, false, true);
    }

    private int mergeExactMask(final List<StoragePermission> permissions) {
        return permissions.stream()
                .sorted(Comparator.comparing(StoragePermission::getSid,
                        Comparator.comparing(StoragePermissionSid::getType, Comparator.reverseOrder())))
                .reduce(0, this::mergeExactMask, (mask, ignored) -> mask);
    }

    private int mergeExactMask(final int currentMask, final StoragePermission permission) {
        return permission.getSid().getType() == StoragePermissionSidType.USER
                ? permissionsService.mergeExactUserMask(currentMask, permission.getMask())
                : permissionsService.mergeExactGroupMask(currentMask, permission.getMask());
    }

    private int mergeParentMask(final int childMask, final int parentMask) {
        return permissionsService.allPermissionsSet(childMask)
                ? childMask
                : permissionsService.mergeParentMask(childMask, parentMask);
    }

    public void deleteFilePermissions(final SecuredStorageEntity storage,
                                      final String path,
                                      final String version,
                                      final boolean totally) {
        if (!storage.isVersioningEnabled() || version == null || totally) {
            storagePermissionManager.delete(storage.getRootId(), path, StoragePermissionPathType.FILE);
        }
    }

    public void deleteFolderPermissions(final SecuredStorageEntity storage, final String path) {
        storagePermissionManager.delete(storage.getRootId(), path, StoragePermissionPathType.FOLDER);
    }

    public void moveFilePermissions(final SecuredStorageEntity storage, final String oldPath, final String newPath) {
        storagePermissionManager.copy(storage.getRootId(), oldPath, newPath, StoragePermissionPathType.FILE);
        storagePermissionManager.delete(storage.getRootId(), oldPath, StoragePermissionPathType.FILE);
    }

    public void moveFolderPermissions(final SecuredStorageEntity storage, final String oldPath, final String newPath) {
        storagePermissionManager.copy(storage.getRootId(), oldPath, newPath, StoragePermissionPathType.FOLDER);
        storagePermissionManager.delete(storage.getRootId(), oldPath, StoragePermissionPathType.FOLDER);
    }

    public void copyFilePermissions(final SecuredStorageEntity storage, final String oldPath, final String newPath) {
        storagePermissionManager.copy(storage.getRootId(), oldPath, newPath, StoragePermissionPathType.FILE);
    }

    public void copyFolderPermissions(final SecuredStorageEntity storage, final String oldPath, final String newPath) {
        storagePermissionManager.copy(storage.getRootId(), oldPath, newPath, StoragePermissionPathType.FOLDER);
    }

    public Set<StoragePermissionRepository.Storage> loadReadAllowedStorages() {
        final PipelineUser user = loadUser();
        final List<String> groups = groups(user);
        return storagePermissionManager.loadReadAllowedStorages(user.getUserName(), groups);
    }

    public boolean isReadAllowed(final SecuredStorageEntity storage) {
        final PipelineUser user = loadUser();
        final List<String> groups = groups(user);
        return storagePermissionManager.isReadAllowed(storage.getRootId(), storage.getId(), user.getUserName(), groups);
    }

    private PipelineUser loadUser() {
        final PipelineUser user = authManager.getCurrentUser();
        if (user == null) {
            throw new AccessDeniedException("Unauthorized user access");
        }
        return user;
    }

    private List<String> groups(final PipelineUser user) {
        return groupSidsOf(user)
                .map(StoragePermissionSid::getName)
                .collect(Collectors.toList());
    }

    private Stream<StoragePermissionSid> groupSidsOf(final PipelineUser user) {
        return Stream.concat(rolesOf(user), groupsOf(user))
                .filter(Objects::nonNull)
                .distinct()
                .map(StoragePermissionSid::group);
    }

    private Stream<String> groupsOf(final PipelineUser user) {
        return Optional.of(user)
                .map(PipelineUser::getGroups)
                .map(List::stream)
                .orElseGet(Stream::empty);
    }

    private Stream<String> rolesOf(final PipelineUser user) {
        return Optional.of(user)
                .map(PipelineUser::getRoles)
                .map(List::stream)
                .orElseGet(Stream::empty)
                .map(Role::getName);
    }

    private boolean isAdminOrOwner(final SecuredStorageEntity storage, final PipelineUser user) {
        return user.isAdmin() || Objects.equals(user.getUserName(), storage.getOwner());
    }

}
