package com.epam.pipeline.manager.datastorage.security;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.SecuredStorageEntity;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.dto.datastorage.security.StoragePermission;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionPathType;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionSid;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.security.PermissionsService;
import com.epam.pipeline.repository.datastorage.security.StoragePermissionRepository;
import com.epam.pipeline.security.acl.AclPermission;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.acls.model.Permission;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    private boolean isAllowed(final SecuredStorageEntity storage,
                              final String path,
                              final StoragePermissionPathType type,
                              final Permission... permissions) {
        final String absolutePath = storage.resolveAbsolutePath(path);
        final int mask = getExtendedMask(storage, absolutePath, type);
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

    private boolean isRecursiveAllowed(final SecuredStorageEntity storage,
                                       final String path,
                                       final Permission... permissions) {
        final PipelineUser user = authManager.getCurrentUser();
        if (user == null) {
            return false;
        }
        if (user.isAdmin() || Objects.equals(user.getUserName(), storage.getOwner())) {
            return true;
        }
        final List<String> groups = groupSids(user)
                .map(StoragePermissionSid::getName)
                .collect(Collectors.toList());
        return isAllowed(storage, path, StoragePermissionPathType.FOLDER, permissions)
                && storagePermissionManager.loadRecursiveMask(storage.getRootId(), path, user.getUserName(), groups)
                .map(mask -> isAllowed(mask, permissions))
                .orElse(true);
    }

    public DataStorageListing applyPermissions(final SecuredStorageEntity storage,
                                               final String path,
                                               final DataStorageListing listing) {
        final String absolutePath = Optional.ofNullable(storage.resolveAbsolutePath(path)).orElse(StringUtils.EMPTY);
        final int mask = getExtendedMask(storage, path, StoragePermissionPathType.FOLDER);
        listing.setMask(getSimpleMask(mask));
        final List<AbstractDataStorageItem> items = ListUtils.emptyIfNull(listing.getResults());
        final PipelineUser user = authManager.getCurrentUser();
        if (user == null) {
            listing.setResults(Collections.emptyList());
            return listing;
        }
        if (user.isAdmin() || Objects.equals(user.getUserName(), storage.getOwner())) {
            listing.setResults(items.stream()
                    .map(item -> withMask(item, AbstractSecuredEntity.ALL_PERMISSIONS_MASK_FULL))
                    .collect(Collectors.toList()));
            return listing;
        }
        final List<String> groups = groupSids(user)
                .map(StoragePermissionSid::getName)
                .collect(Collectors.toList());
        final Map<StoragePermissionRepository.StorageItem, Integer> masks = storagePermissionManager
                .loadImmediateChildPermissions(storage.getRootId(), absolutePath, user.getUserName(), groups)
                .stream()
                .collect(Collectors.groupingBy(item -> new StoragePermissionRepository.StorageItemImpl(
                                item.getPath(), item.getType()),
                        Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
                                .sorted(Comparator.comparing(StoragePermission::getSid,
                                        Comparator.comparing(StoragePermissionSid::getType,
                                                Comparator.reverseOrder())))
                                .reduce(0, (mask1, p) -> p.getMask(), permissionsService::mergeItemMask))));
        if (isAllowed(mask, AclPermission.READ)) {
            listing.setResults(items.stream()
                    .map(item -> {
                        int itemMask = masks.getOrDefault(new StoragePermissionRepository.StorageItemImpl(
                                item.getPath(), StoragePermissionPathType.from(item.getType())), 0);
                        itemMask = permissionsService.allPermissionsSet(itemMask, AclPermission.getBasicPermissions())
                                ? itemMask
                                : permissionsService.mergeParentMask(itemMask, mask);
                        return withMask(item, itemMask);
                    })
                    .collect(Collectors.toList()));
        } else {
            final Set<StoragePermissionRepository.StorageItem> readAllowedItems =
                    storagePermissionManager.loadReadAllowedImmediateChildItems(storage.getRootId(), absolutePath,
                            user.getUserName(), groups);
            if (readAllowedItems.isEmpty()) {
                // TODO: 23.08.2021 Move this check to SecuredStorageProvider
                throw new AccessDeniedException(String.format("No recursive read permissions for %s", absolutePath));
            }
            listing.setResults(items.stream()
                    .filter(item -> readAllowedItems.contains(new StoragePermissionRepository.StorageItemImpl(
                            item.getPath(), StoragePermissionPathType.from(item.getType()))))
                    .map(item -> {
                        int itemMask = masks.getOrDefault(new StoragePermissionRepository.StorageItemImpl(
                                item.getPath(), StoragePermissionPathType.from(item.getType())), 0);
                        itemMask = permissionsService.allPermissionsSet(itemMask, AclPermission.getBasicPermissions())
                                ? itemMask
                                : permissionsService.mergeParentMask(itemMask, mask);
                        itemMask |= AclPermission.READ.getMask();
                        itemMask &= ~AclPermission.NO_READ.getMask();
                        return withMask(item, itemMask);
                    })
                    .collect(Collectors.toList()));
        }
        return listing;
    }

    private AbstractDataStorageItem withMask(final AbstractDataStorageItem item, final int mask) {
        item.setMask(getSimpleMask(mask));
        return item;
    }

    public int getMask(final SecuredStorageEntity storage, final String path, final StoragePermissionPathType type) {
        return getSimpleMask(getExtendedMask(storage, path, type));
    }

    private int getSimpleMask(final int extendedMask) {
        return permissionsService.mergeMask(extendedMask);
    }

    private int getExtendedMask(final SecuredStorageEntity storage,
                                final String path,
                                final StoragePermissionPathType type) {
        final PipelineUser user = authManager.getCurrentUser();
        if (user == null) {
            return AbstractSecuredEntity.NO_PERMISSIONS_MASK;
        }
        if (user.isAdmin() || Objects.equals(user.getUserName(), storage.getOwner())) {
            return AbstractSecuredEntity.ALL_PERMISSIONS_MASK_FULL;
        }
        return getExtendedMask(storage, path, type, user);
    }

    private int getExtendedMask(final SecuredStorageEntity storage,
                                final String path,
                                final StoragePermissionPathType type,
                                final PipelineUser user) {
        final String absolutePath = Optional.ofNullable(storage.resolveAbsolutePath(path)).orElse(StringUtils.EMPTY);
        final List<String> groups = groupSids(user)
                .map(StoragePermissionSid::getName)
                .collect(Collectors.toList());
        final Map<StoragePermissionRepository.StorageItem, List<StoragePermission>> permissions =
                storagePermissionManager.load(storage.getRootId(), absolutePath, type, user.getUserName(), groups)
                        .stream()
                        .collect(Collectors.groupingBy(p -> new StoragePermissionRepository.StorageItemImpl(
                                p.getPath(), p.getType()), Collectors.toCollection(ArrayList::new)));
        final Stream<Integer> pathMasks = permissions.keySet().stream()
                .sorted(Comparator.comparing(StoragePermissionRepository.StorageItem::getStoragePath,
                        Comparator.reverseOrder()))
                .map(item -> permissions.get(item)
                        .stream()
                        .sorted(Comparator.comparing(StoragePermission::getSid,
                                Comparator.comparing(StoragePermissionSid::getType, Comparator.reverseOrder())))
                        .reduce(0, (mask, p) -> p.getMask(), permissionsService::mergeItemMask));
        return Stream.concat(pathMasks, Stream.of(getStorageExtendedMask((AbstractSecuredEntity) storage)))
                .reduce((childMask, parentMask) ->
                        permissionsService.allPermissionsSet(childMask, AclPermission.getBasicPermissions())
                                ? childMask
                                : permissionsService.mergeParentMask(childMask, parentMask))
                .orElse(0);
    }

    private Integer getStorageExtendedMask(final AbstractSecuredEntity storage) {
        return grantPermissionManager.getPermissionsMask(storage, false, true);
    }

    private Stream<StoragePermissionSid> groupSids(final PipelineUser user) {
        return Optional.of(user)
                .map(PipelineUser::getRoles)
                .map(List::stream)
                .orElseGet(Stream::empty)
                .map(Role::getName)
                .filter(Objects::nonNull)
                .map(StoragePermissionSid::group);
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
