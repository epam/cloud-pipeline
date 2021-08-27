package com.epam.pipeline.manager.datastorage.security;

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
        final PipelineUser user = authManager.getCurrentUser();
        if (user == null) {
            throw new AccessDeniedException("Unauthorized user access");
        }
        if (user.isAdmin() || Objects.equals(user.getUserName(), storage.getOwner())) {
            return true;
        }
        final List<String> groups = groupSidsOf(user)
                .map(StoragePermissionSid::getName)
                .collect(Collectors.toList());
        return isAllowed(storage, path, StoragePermissionPathType.FOLDER, permissions)
                && storagePermissionManager.loadRecursiveMask(storage.getRootId(), path, user.getUserName(), groups)
                .map(mask -> isAllowed(mask, permissions))
                .orElse(true);
    }

    public DataStorageListing apply(final SecuredStorageEntity storage,
                                    final String path,
                                    final DataStorageListing listing,
                                    final Function<String, DataStorageListing> markerToListingFunction) {
        final String absolutePath = Optional.ofNullable(storage.resolveAbsolutePath(path)).orElse(StringUtils.EMPTY);
        final int folderMask = getMask(storage, absolutePath, StoragePermissionPathType.FOLDER);
        final DataStorageListing result = new DataStorageListing();
        result.setMask(getSimpleMask(folderMask));
        final PipelineUser user = authManager.getCurrentUser();
        if (user == null) {
            throw new AccessDeniedException("Unauthorized user access");
        }
        if (user.isAdmin() || Objects.equals(user.getUserName(), storage.getOwner())) {
            result.setNextPageMarker(listing.getNextPageMarker());
            result.setResults(ListUtils.emptyIfNull(listing.getResults()).stream()
                    .map(item -> withMask(item, AbstractSecuredEntity.ALL_PERMISSIONS_MASK_FULL))
                    .collect(Collectors.toList()));
            return result;
        }
        final List<String> groups = groupSidsOf(user)
                .map(StoragePermissionSid::getName)
                .collect(Collectors.toList());
        // TODO: 27.08.2021 Allow/deny permissions for a user should override permissions for any group
        // TODO: 27.08.2021 Allow permission for any group should override read deny for any group
        final Map<StoragePermissionRepository.StorageItem, Integer> masks = storagePermissionManager
                .loadImmediateChildPermissions(storage.getRootId(), absolutePath, user.getUserName(), groups)
                .stream()
                .collect(Collectors.groupingBy(this::getStorageItem,
                        Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
                                .sorted(Comparator.comparing(StoragePermission::getSid,
                                        Comparator.comparing(StoragePermissionSid::getType,
                                                Comparator.reverseOrder())))
                                .reduce(0, (mask, p) -> permissionsService.mergeItemMask(mask, p.getMask()),
                                        permissionsService::mergeItemMask))));
        if (isAllowed(folderMask, AclPermission.READ)) {
            result.setNextPageMarker(listing.getNextPageMarker());
            result.setResults(ListUtils.emptyIfNull(listing.getResults()).stream()
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
                throw new AccessDeniedException(String.format("No recursive read permissions for %s", absolutePath));
            }
            int filteredOutItemsNumber = 0;
            final List<AbstractDataStorageItem> items = new ArrayList<>();
            for (final AbstractDataStorageItem item : ListUtils.emptyIfNull(listing.getResults())) {
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
            DataStorageListing currentListing = listing;
            while (filteredOutItemsNumber > 0 && currentListing.getNextPageMarker() != null) {
                currentListing = markerToListingFunction.apply(currentListing.getNextPageMarker());
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
            result.setNextPageMarker(currentListing.getNextPageMarker());
            result.setResults(items);
        }
        return result;
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
        final PipelineUser user = authManager.getCurrentUser();
        if (user == null) {
            throw new AccessDeniedException("Unauthorized user access");
        }
        if (user.isAdmin() || Objects.equals(user.getUserName(), storage.getOwner())) {
            return AbstractSecuredEntity.ALL_PERMISSIONS_MASK_FULL;
        }
        return getMask(storage, path, type, user);
    }

    private int getMask(final SecuredStorageEntity storage,
                        final String path,
                        final StoragePermissionPathType type,
                        final PipelineUser user) {
        final List<String> groups = groupSidsOf(user)
                .map(StoragePermissionSid::getName)
                .collect(Collectors.toList());
        final Map<StoragePermissionRepository.StorageItem, List<StoragePermission>> permissions =
                storagePermissionManager.load(storage.getRootId(), path, type, user.getUserName(), groups)
                        .stream()
                        .collect(Collectors.groupingBy(this::getStorageItem, Collectors.toCollection(ArrayList::new)));
        final Stream<Integer> pathMasks = permissions.keySet().stream()
                .sorted(Comparator.comparing(StoragePermissionRepository.StorageItem::getStoragePath,
                        Comparator.reverseOrder()))
                .map(item -> permissions.get(item)
                        .stream()
                        .sorted(Comparator.comparing(StoragePermission::getSid,
                                Comparator.comparing(StoragePermissionSid::getType, Comparator.reverseOrder())))
                        .reduce(0, (mask, p) -> permissionsService.mergeItemMask(mask, p.getMask()), permissionsService::mergeItemMask));
        return Stream.concat(pathMasks, Stream.of(getStorageMask((AbstractSecuredEntity) storage)))
                .reduce(this::mergeParentMask)
                .orElse(0);
    }

    private StoragePermissionRepository.StorageItemImpl getStorageItem(final StoragePermission item) {
        return new StoragePermissionRepository.StorageItemImpl(item.getPath(), item.getType());
    }

    private int getStorageMask(final AbstractSecuredEntity storage) {
        return grantPermissionManager.getPermissionsMask(storage, false, true);
    }

    private int mergeParentMask(final int childMask, final int parentMask) {
        return permissionsService.allPermissionsSet(childMask)
                ? childMask
                : permissionsService.mergeParentMask(childMask, parentMask);
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

    private StoragePermissionRepository.StorageItemImpl getStorageItem(final AbstractDataStorageItem item) {
        return new StoragePermissionRepository.StorageItemImpl(item.getPath(),
                StoragePermissionPathType.from(item.getType()));
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
}
