package com.epam.pipeline.manager.datastorage.security;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.SecuredStorageEntity;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.dto.datastorage.security.StoragePermission;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionPathType;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionSid;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionSidType;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.PermissionsService;
import com.epam.pipeline.security.acl.AclPermission;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.acls.model.Permission;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class StoragePermissionProviderManager {

    private final StoragePermissionManager storagePermissionManager;
    private final PermissionsService permissionsService;
    private final AuthManager authManager;

    public boolean isReadAllowed(final SecuredStorageEntity storage,
                                 final String path,
                                 final StoragePermissionPathType type) {
        // TODO: 16.08.2021 Allow owner or admins
        return isOverallAllowed(storage, path, type)
                && isAllowed(storage, path, type, AclPermission.READ);
    }

    public boolean isReadNotAllowed(final SecuredStorageEntity storage,
                                    final String path,
                                    final StoragePermissionPathType type) {
        return !isReadAllowed(storage, path, type);
    }

    public boolean isWriteAllowed(final SecuredStorageEntity storage, final String path,
                                  final StoragePermissionPathType type) {
        // TODO: 16.08.2021 Allow owner or admins
        return isOverallAllowed(storage, path, type)
                && isAllowed(storage, path, type, AclPermission.WRITE);
    }

    public boolean isWriteNotAllowed(final SecuredStorageEntity storage,
                                     final String path,
                                     final StoragePermissionPathType type) {
        return !isWriteAllowed(storage, path, type);
    }

    private boolean isOverallAllowed(final SecuredStorageEntity storage, final AbstractDataStorageItem item) {
        return isOverallAllowed(storage, item.getPath(), StoragePermissionPathType.from(item.getType()));
    }

    private boolean isOverallAllowed(final SecuredStorageEntity storage,
                                     final String path,
                                     final StoragePermissionPathType type) {
        // TODO: 11.08.2021 Think out which items should not be allowed to even process permissions.
        //  Probably ones which lies in folders without read access and has no explicit permissions set.
        return true;
    }

    private boolean isAllowed(final SecuredStorageEntity storage,
                              final String path,
                              final StoragePermissionPathType type,
                              final Permission permission) {
        final String absolutePath = storage.resolveAbsolutePath(path);
        final int mask = getMask(storage, absolutePath, type);
        return permission instanceof AclPermission
                && (permission.getMask() & mask) == permission.getMask()
                && (((AclPermission) permission).getDenyPermission().getMask() & mask) == 0;
    }


    public List<AbstractDataStorageItem> process(final SecuredStorageEntity storage,
                                                 final List<AbstractDataStorageItem> items) {
        // TODO: 12.08.2021 Optimize multiple files processing
        return items.stream()
                .map(item -> process(storage, item))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    public Optional<AbstractDataStorageItem> process(final SecuredStorageEntity storage,
                                                     final AbstractDataStorageItem item) {
        return Optional.of(item)
                .filter(i -> isOverallAllowed(storage, i))
                .map(i -> withMask(storage, i));
    }

    private AbstractDataStorageItem withMask(final SecuredStorageEntity storage, final AbstractDataStorageItem item) {
        final int mask = getMask(storage, item);
        item.setMask(permissionsService.mergeMask(mask));
        return item;
    }

    private int getMask(final SecuredStorageEntity storage, final AbstractDataStorageItem item) {
        return getMask(storage, item.getPath(), StoragePermissionPathType.from(item.getType()));
    }

    private int getMask(final SecuredStorageEntity storage,
                        final String path,
                        final StoragePermissionPathType type) {
        // TODO: 16.08.2021 Return full permissions mask for owner or admins
        final String absolutePath = Optional.ofNullable(storage.resolveAbsolutePath(path)).orElse(StringUtils.EMPTY);
        final List<StoragePermissionSid> sids = getSids();
        final List<StoragePermission> permissions = storagePermissionManager.load(storage.getRootId(), absolutePath,
                type);
        final List<StoragePermission> applicablePermissions = permissions.stream()
                .filter(it -> sids.contains(it.getSid()))
                .collect(Collectors.toList());
        final List<StoragePermission> directPermissions = applicablePermissions.stream()
                .filter(it -> Objects.equals(it.getPath(), absolutePath))
                .collect(Collectors.toList());
        return directPermissions.isEmpty()
                ? getMask(applicablePermissions)
                : getMask(directPermissions);
    }

    private int getMask(final List<StoragePermission> permissions) {
        return permissions.isEmpty()
                ? AbstractSecuredEntity.ALL_PERMISSIONS_MASK_FULL
                : getMergedUserPrioritisedMask(permissions);
    }

    private int getMergedUserPrioritisedMask(final List<StoragePermission> permissions) {
        return permissions.stream()
                .filter(perm -> perm.getSid().getType() == StoragePermissionSidType.USER)
                .findFirst()
                .map(StoragePermission::getMask)
                .orElseGet(() -> getMergedMask(permissions));
    }

    private Integer getMergedMask(final List<StoragePermission> permissions) {
        return permissions.stream()
                .map(StoragePermission::getMask)
                .reduce(0, (x, y) -> x | y);
    }

    private List<StoragePermissionSid> getSids() {
        return Optional.ofNullable(authManager.getCurrentUser())
                .map(user -> Stream.concat(userSids(user), groupSids(user)).collect(Collectors.toList()))
                .orElseGet(Collections::emptyList);
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

    private Stream<StoragePermissionSid> userSids(final PipelineUser user) {
        return Optional.of(user)
                .map(PipelineUser::getUserName)
                .map(StoragePermissionSid::user)
                .map(Stream::of)
                .orElseGet(Stream::empty);
    }
}
