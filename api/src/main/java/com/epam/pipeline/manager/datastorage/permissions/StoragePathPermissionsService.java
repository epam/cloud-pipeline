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

package com.epam.pipeline.manager.datastorage.permissions;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.dao.datastorage.permissions.StoragePathPermissionsDao;
import com.epam.pipeline.dto.PermissionVO;
import com.epam.pipeline.dto.datastorage.permissions.StoragePathPermissionsVO;
import com.epam.pipeline.dto.datastorage.permissions.StoragePathPermissions;
import com.epam.pipeline.dto.datastorage.permissions.StorageFolderListPermissionsContainer;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageItemType;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.entity.user.SidImpl;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.acl.AclPermission;
import joptsimple.internal.Strings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoragePathPermissionsService {

    private final StoragePathPermissionsDao pathPermissionsDao;
    private final DataStorageDao dataStorageDao;
    private final UserManager userManager;
    private final MessageHelper messageHelper;

    /**
     * Loads all permissions available for storage and current user.
     * @param storageId storage ID
     * @return permissions
     */
    public List<StoragePathPermissions> loadHierarchyForStorage(final Long storageId) {
        checkStorageExistsAndPathPermissionsAllowed(storageId);
        return pathPermissionsDao.findByStorageAndSids(storageId, getSids());
    }

    /**
     * Deletes all permissions for storage across all users/groups.
     *
     * @param storageId storage ID
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteByStorageId(final Long storageId) {
        log.debug("Deleting all path storage permissions for storage '{}'", storageId);
        pathPermissionsDao.deleteForStorageId(storageId);
    }

    /**
     * Updates permissions for specified storage and user/group. If empty list provided - existing permissions
     * will be deleted.
     *
     * @param storageId storage ID
     * @param sidId user or group name
     * @param principal indicated user or group flag
     * @param pathPermissions permissions to insert
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void batchUpdate(final Long storageId, final String sidId, final boolean principal,
                            final List<StoragePathPermissions> pathPermissions) {
        checkStorageExistsAndPathPermissionsAllowed(storageId);
        final String sidName = sidId.toUpperCase(Locale.ROOT);
        final SidImpl sid = new SidImpl();
        sid.setName(sidName);
        sid.setPrincipal(principal);
        pathPermissionsDao.deleteForStorageAndSids(storageId, Collections.singletonList(sid));
        if (CollectionUtils.isEmpty(pathPermissions)) {
            return;
        }
        pathPermissionsDao.batchInsert(pathPermissions.stream()
                .map(this::normalizePermissions)
                .collect(Collectors.toList()), storageId, sidName, principal);
    }

    /**
     * Updated permissions for specified storage and paths.
     * If no permissions provided all permissions will be deleted for certain path.
     *
     * @param storageId storage ID
     * @param rawPermissions the list of permissions for storage item path.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void updateByStorageIdAndPaths(final Long storageId,
                                          final List<StoragePathPermissionsVO> rawPermissions) {
        checkStorageExistsAndPathPermissionsAllowed(storageId);
        if (CollectionUtils.isEmpty(rawPermissions)) {
            log.debug("No permissions found to update for storage '{}'", storageId);
            return;
        }
        final List<StoragePathPermissions> toUpdate = rawPermissions.stream()
                .peek(this::verifyRawPermissionsVO)
                .flatMap(pathPermissions -> ListUtils.emptyIfNull(pathPermissions.getPermissions()).stream()
                        .map(permissionVO -> toEntity(pathPermissions.getPath(),
                                pathPermissions.getType(), permissionVO)))
                .collect(Collectors.toList());
        // collect permissions to delete if permissions were not provided for path
        final List<StoragePathPermissions> toDelete = rawPermissions.stream()
                .filter(vo -> CollectionUtils.isEmpty(vo.getPermissions()))
                .map(vo -> toEntity(vo.getPath(), vo.getType()))
                .collect(Collectors.toList());
        toDelete.addAll(toUpdate);
        if (CollectionUtils.isNotEmpty(toDelete)) {
            log.debug("Deleting '{}' storage path permissions for storage '{}'", toDelete.size(), storageId);
            pathPermissionsDao.batchDeleteByPath(toDelete, storageId);
        }
        if (CollectionUtils.isNotEmpty(toUpdate)) {
            log.debug("Inserting '{}' storage path permissions for storage '{}'", toUpdate.size(), storageId);
            pathPermissionsDao.batchInsert(toUpdate, storageId);
        }
    }

    /**
     * Deletes permissions for specified storage. If sids provided permissions shall be
     * deleted for specified users/groups only.
     *
     * @param storageId storage ID
     * @param sids  users or groups to delete
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteByStorageIdAndSids(final Long storageId, final List<SidImpl> sids) {
        checkStorageExistsAndPathPermissionsAllowed(storageId);
        if (CollectionUtils.isEmpty(sids)) {
            pathPermissionsDao.deleteForStorageId(storageId);
        }
        log.debug("Deleting path storage permissions for storage '{}' for sids: {}", storageId,
                sids.stream()
                        .map(SidImpl::getName)
                        .collect(Collectors.joining(", ")));
        pathPermissionsDao.deleteForStorageAndSids(storageId, normalizeSids(sids));
    }

    /**
     * Load users and groups that contains any path permissions for specified storage.
     *
     * @param storageId storage ID
     * @return users and groups list
     */
    public List<PermissionVO> loadSids(final Long storageId) {
        checkStorageExistsAndPathPermissionsAllowed(storageId);
        return pathPermissionsDao.findSids(storageId).stream()
                .map(sid -> PermissionVO.builder()
                        .name(sid.getName())
                        .principal(sid.isPrincipal())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Returns all user/groups with masks for specified storage item.
     *
     * @param storageId storage ID
     * @param path full path from storage root to item
     * @param type indicated file or folder
     * @return the list of granted permissions
     */
    public List<PermissionVO> loadSidsByPath(final Long storageId, final String path, final DataStorageItemType type) {
        checkStorageExistsAndPathPermissionsAllowed(storageId);
        verifyStorageItemType(type);
        final List<StoragePathPermissions> entities = loadPermissionsForItem(storageId, path, type);
        if (CollectionUtils.isEmpty(entities)) {
            log.debug("No permissions found for {} '{}' for storage '{}'", type.name(), path, storageId);
            return Collections.emptyList();
        }
        return entities.stream()
                .map(entity -> PermissionVO.builder()
                        .name(entity.getSidName())
                        .principal(entity.isPrincipal())
                        .mask(entity.getMask())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Determines if current user can write to folder by given path.
     * If no write permissions provided for user directly or one of user's group an AccessDeniedException
     * shall be occurred.
     *
     * @param storageId storage ID
     * @param path path to folder
     */
    public void canWriteToFolder(final Long storageId, final String path) {
        log.debug("Checking write permissions on folder '{}' for storage '{}'", path, storageId);
        orThrowAccessDenied(findFolderPermissions(storageId, path).filter(this::hasWritePermissions));
    }

    public boolean isWriteAllowedToFolder(final Long storageId, final String path) {
        return findFolderPermissions(storageId, path).filter(this::hasWritePermissions).isPresent();
    }

    /**
     * Determines if current user can read folder by given path.
     * If no permissions provided for user directly or one of user's group an AccessDeniedException
     * shall be occurred.
     *
     * @param storageId storage ID
     * @param path path to folder
     */
    public void canReadFolder(final Long storageId, final String path) {
        log.debug("Checking permissions on folder '{}' for storage '{}'", path, storageId);
        orThrowAccessDenied(findFolderPermissions(storageId, path));
    }

    public boolean isReadAllowedToFolder(final Long storageId, final String path) {
        return findFolderPermissions(storageId, path).isPresent();
    }

    /**
     * Determines if current user can write to file by given path.
     * Write permissions shall be granted directly to file or one of it`s parent folder.
     * If no write permissions provided for user directly or one of user`s group an AccessDeniedException
     * shall be occurred.
     *
     * @param storageId storage ID
     * @param path path to file
     */
    public void canWriteToFile(final Long storageId, final String path) {
        log.debug("Checking write permissions on file '{}' for storage '{}'", path, storageId);
        orThrowAccessDenied(findFilePermissions(storageId, path).filter(this::hasWritePermissions));
    }

    public boolean isWriteAllowedToFile(final Long storageId, final String path) {
        return findFilePermissions(storageId, path).filter(this::hasWritePermissions).isPresent();
    }

    /**
     * Determines if current user can read file by given path.
     * Permissions shall be granted directly to file or one of it`s parent folder.
     * If no permissions provided for user directly or one of user`s group an AccessDeniedException shall be occurred.
     *
     * @param storageId storage ID
     * @param path path to file
     */
    public void canReadFile(final Long storageId, final String path) {
        log.debug("Checking permissions on file '{}' for storage '{}'", path, storageId);
        orThrowAccessDenied(findFilePermissions(storageId, path));
    }

    public boolean isReadAllowedToFile(final Long storageId, final String path) {
        return findFilePermissions(storageId, path).isPresent();
    }

    /**
     * Determines if current user can see folder by given path. To have such access to folder user may have
     * permissions to:
     *  - folder directly
     *  - one of the parent folders
     *  - one of the child files or folders
     * That permission does not indicate that user can read folder content.
     * If no permissions provided for user directly or one of user`s group an AccessDeniedException shall be occurred.
     *
     * @param storageId storage ID
     * @param path path to folder
     */
    public void canGetFolder(final Long storageId, final String path) {
        log.debug("Checking current user can get folder '{}' for storage '{}'", path, storageId);
        final String folderPath = normalizePath(path);
        final List<SidImpl> sids = getSids();
        final List<String> parentFolders = splitPath(folderPath);

        final Optional<StoragePathPermissions> closestFolderPermission = pathPermissionsDao
                .findClosestParentFolderPermission(storageId, sids, parentFolders);
        if (closestFolderPermission.isPresent()) {
            log.debug("Permissions on parent folder found for path '{}' and storage '{}'", path, storageId);
            return;
        }

        log.debug("No permissions on parent folders for path '{}' and storage '{}'. Checking children paths..",
                path, storageId);
        if (CollectionUtils.isEmpty(pathPermissionsDao.findByPrefix(storageId, sids, folderPath))) {
            throw new AccessDeniedException("Access is denied");
        }
    }

    /**
     * Filters input file paths according to specified permission mask.
     *
     * @param storageId storage ID
     * @param files list of files paths to filter
     * @param mask target permission mask (1 - read, 4 - write)
     * @return Filtered files paths
     */
    public List<String> filterFiles(final Long storageId, final List<String> files, final int mask) {
        final List<SidImpl> sids = getSids();
        final Map<String, Integer> dbMasksByPaths = ListUtils.emptyIfNull(
                pathPermissionsDao.findByStorageAndSids(storageId, sids)).stream()
                .collect(Collectors.toMap(permission -> StringUtils.isBlank(permission.getFileName())
                                ? permission.getFolderPath()
                                : permission.getFolderPath() + permission.getFileName(),
                        StoragePathPermissions::getMask));

        return files.stream()
                .filter(filePath -> isMaskMatch(filePath, mask, dbMasksByPaths))
                .collect(Collectors.toList());
    }

    /**
     * Returns container with paths that have read permissions on specified folder.
     *
     * @param storageId storage ID
     * @param path path to file
     * @return container with permissions if available or throws AccessDeniedException if no permissions exist.
     */
    public StorageFolderListPermissionsContainer getFolderListPermissions(final Long storageId, final String path) {
        final String folderPath = normalizePath(path);
        final List<SidImpl> sids = getSids();
        final List<String> parentFolders = splitPath(folderPath);

        final StorageFolderListPermissionsContainer permissionsContainer =
                StorageFolderListPermissionsContainer.builder().build();

        final Integer folderMask = pathPermissionsDao.findClosestParentFolderPermission(storageId, sids, parentFolders)
                .map(StoragePathPermissions::getMask)
                .map(this::toSimpleMask)
                .orElse(null);
        permissionsContainer.setFolderMask(folderMask);

        final List<StoragePathPermissions> childPermissions = pathPermissionsDao
                .findByPrefix(storageId, sids, folderPath);
        if (Objects.isNull(folderMask) && CollectionUtils.isEmpty(childPermissions)) {
            throw new AccessDeniedException("Access is denied");
        }
        if (CollectionUtils.isEmpty(childPermissions)) {
            // permissions shall be inherited from folder
            return permissionsContainer;
        }
        final Map<String, Integer> files = getFilesInCurrentFolder(childPermissions, folderPath);
        if (MapUtils.isNotEmpty(files)) {
            permissionsContainer.setFiles(files);
        }
        final Map<String, Integer> folders = getFoldersInCurrentFolder(childPermissions, folderPath);
        if (MapUtils.isNotEmpty(folders)) {
            permissionsContainer.setFolders(folders);
        }
        return permissionsContainer;
    }

    private Optional<StoragePathPermissions> findFilePermissions(final Long storageId, final String path) {
        final Path pathObject = Paths.get(path);
        final String folderPath = normalizePath(Optional.ofNullable(pathObject.getParent())
                .map(Objects::toString)
                .orElse(ProviderUtils.DELIMITER));
        final String fileName = pathObject.getFileName().toString();
        final List<String> tokens = splitPath(folderPath);
        final List<SidImpl> sids = getSids();
        return pathPermissionsDao.findClosestFilePermission(storageId, sids, tokens, folderPath, fileName);
    }

    private Optional<StoragePathPermissions> findFolderPermissions(final Long storageId, final String path) {
        final String folderPath = normalizePath(path);
        final List<String> prefixes = splitPath(folderPath);
        final List<SidImpl> sids = getSids();
        return pathPermissionsDao.findClosestParentFolderPermission(storageId, sids, prefixes);
    }

    private StoragePathPermissions normalizePermissions(final StoragePathPermissions permissions) {
        final String fileName = StringUtils.isBlank(permissions.getFileName())
                ? null
                : ProviderUtils.withoutLeadingDelimiter(permissions.getFileName());
        final String folderPath = normalizePath(permissions.getFolderPath());
        permissions.setFolderPath(folderPath);
        permissions.setFileName(fileName);
        return permissions;
    }

    private String normalizePath(final String path) {
        if (StringUtils.isBlank(path)) {
            return ProviderUtils.DELIMITER;
        }
        return ProviderUtils.withLeadingDelimiter(ProviderUtils.withTrailingDelimiter(path));
    }

    private List<String> splitPath(final String path) {
        final List<String> paths = new ArrayList<>();
        paths.add(ProviderUtils.DELIMITER);
        if (ProviderUtils.DELIMITER.equals(path)) {
            return paths;
        }
        int index = 0;
        while (index < path.length()) {
            int nextIndex = path.indexOf(ProviderUtils.DELIMITER, index + 1);
            if (nextIndex == -1) {
                break;
            }
            paths.add(path.substring(0, nextIndex + 1));
            index = nextIndex;
        }
        return paths;
    }

    private List<SidImpl> getSids() {
        final PipelineUser user = userManager.getCurrentUser();
        final List<SidImpl> sids = new ArrayList<>();
        sids.add(buildSid(user.getUserName(), true));
        sids.addAll(Stream.concat(
                ListUtils.emptyIfNull(user.getRoles()).stream().map(Role::getName),
                        ListUtils.emptyIfNull(user.getGroups()).stream())
                .map(item -> item.toUpperCase(Locale.ROOT))
                .distinct()
                .map(item -> buildSid(item, false))
                .collect(Collectors.toList()));
        return sids;
    }

    private SidImpl buildSid(final String sidName, final boolean isPrincipal) {
        final SidImpl sid = new SidImpl();
        sid.setName(sidName.toUpperCase(Locale.ROOT));
        sid.setPrincipal(isPrincipal);
        return sid;
    }

    private List<SidImpl> normalizeSids(final List<SidImpl> sids) {
        return ListUtils.emptyIfNull(sids).stream()
                .filter(sid -> StringUtils.isNotBlank(sid.getName()))
                .peek(sid -> sid.setName(sid.getName().toUpperCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }

    private String extractFolderName(final String dbPath, final String rootPath) {
        final String relativePath = dbPath.substring(rootPath.length());
        final int firstSlashIndex = relativePath.indexOf(ProviderUtils.DELIMITER);
        return firstSlashIndex != -1 ? relativePath.substring(0, firstSlashIndex) : relativePath;
    }

    private Map<String, Integer> getFoldersInCurrentFolder(final List<StoragePathPermissions> loadedPaths,
                                                           final String currentFolder) {
        final Map<String, Integer> results = new HashMap<>();
        // Step #1: add permissions that granted on folders directly
        loadedPaths.stream()
                .filter(permission -> StringUtils.isBlank(permission.getFileName()))
                .filter(permission -> !permission.getFolderPath().equals(currentFolder))
                .forEach(permission -> collectPermissionsInFolder(permission, currentFolder, results));
        // Step #2: add the rest folders with read-only access level.
        // If folder already added at Step #1 the lowes permission shall be chosen.
        loadedPaths
                .forEach(permission -> collectPermissionsInFolderRecursively(permission, currentFolder, results));
        return results;
    }

    private Map<String, Integer> getFilesInCurrentFolder(final List<StoragePathPermissions> loadedPaths,
                                                         final String currentFolder) {
        return loadedPaths.stream()
                .filter(permissions -> currentFolder.equals(permissions.getFolderPath()))
                .filter(permissions -> StringUtils.isNotBlank(permissions.getFileName()))
                .collect(Collectors.toMap(StoragePathPermissions::getFileName, p -> toSimpleMask(p.getMask())));
    }

    private boolean hasWritePermissions(final StoragePathPermissions permissions) {
        return (permissions.getMask() & AclPermission.WRITE.getMask()) != 0;
    }

    private void orThrowAccessDenied(final Optional<StoragePathPermissions> permissions) {
        permissions.orElseThrow(() -> new AccessDeniedException("Access is denied"));
    }

    private AbstractDataStorage findStorage(final Long storageId) {
        return Optional.ofNullable(dataStorageDao.loadDataStorage(storageId))
                .orElseThrow(() -> new IllegalStateException(
                        messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_NOT_FOUND, storageId)));
    }

    private boolean pathPermissionsAllowed(final AbstractDataStorage storage) {
        return storage.isPathPermissionsEnabled() && DataStorageType.S3.equals(storage.getType());
    }

    private void checkStorageExistsAndPathPermissionsAllowed(final Long storageId) {
        final AbstractDataStorage storage = findStorage(storageId);
        Assert.state(pathPermissionsAllowed(storage),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_PERMISSIONS_NOT_ALLOWED));
    }

    private void collectPermissionsInFolder(final StoragePathPermissions permission,
                                            final String currentFolder,
                                            final Map<String, Integer> masksByNames) {
        final String folderPath = permission.getFolderPath();
        final String folderName = extractFolderName(folderPath, currentFolder);
        final int mask = permission.getMask();
        if (folderPath.endsWith(folderName + ProviderUtils.DELIMITER)) {
            masksByNames.putIfAbsent(folderName, toSimpleMask(mask));
        }
    }

    private void collectPermissionsInFolderRecursively(final StoragePathPermissions permission,
                                                       final String currentFolder,
                                                       final Map<String, Integer> masksByNames) {
        final String folderName = extractFolderName(permission.getFolderPath(), currentFolder);
        if (StringUtils.isBlank(folderName)) {
            return;
        }
        if (!masksByNames.containsKey(folderName)) {
            masksByNames.put(folderName, new AclPermission(AclPermission.READ.getMask()).getSimpleMask());
            return;
        }
        final int oldMask = Optional.ofNullable(masksByNames.get(folderName)).orElse(0);
        final int newMask = toSimpleMask(permission.getMask());
        // downgrade permission:
        // if at least one child path has lower permissions parent folder shall respect it
        if (newMask < oldMask) {
            masksByNames.put(folderName, newMask);
        }
    }

    private Optional<Integer> findMatchingMask(final String filePath, final Map<String, Integer> masksByPaths) {
        String currentPath = ProviderUtils.withLeadingDelimiter(filePath);

        // Traverse the folder hierarchy from the file path back to the root
        while (currentPath.contains(ProviderUtils.DELIMITER)) {
            final Integer mask = masksByPaths.get(currentPath);
            if (Objects.nonNull(mask)) {
                return Optional.of(mask);
            }
            currentPath = normalizePath(currentPath.substring(0, currentPath.lastIndexOf(ProviderUtils.DELIMITER)));
            if (currentPath.equals(ProviderUtils.DELIMITER)) {
                break;
            }
        }
        return Optional.empty();
    }

    private boolean isMaskMatch(final String filePath, final int mask, final Map<String, Integer> masksByPaths) {
        return findMatchingMask(filePath, masksByPaths)
                .map(actualMask -> (actualMask & mask) != 0)
                .orElse(false);
    }

    private StoragePathPermissions toEntity(final String path, final DataStorageItemType type,
                                            final PermissionVO permission) {
        final StoragePathPermissions entity = toEntity(path, type);

        entity.setMask(permission.getMask());
        entity.setPrincipal(permission.getPrincipal());
        entity.setSidName(permission.getName().toUpperCase(Locale.ROOT));

        return entity;
    }

    private StoragePathPermissions toEntity(final String path, final DataStorageItemType type) {
        final StoragePathPermissions entity = new StoragePathPermissions();
        if (DataStorageItemType.Folder.equals(type)) {
            entity.setFolderPath(normalizePath(path));
        } else {
            final int lastSeparatorIndex = path.lastIndexOf(ProviderUtils.DELIMITER);
            entity.setFolderPath(normalizePath(extractFolderPath(path, lastSeparatorIndex)));
            entity.setFileName(extractFileName(path, lastSeparatorIndex));
        }
        return entity;
    }

    private List<StoragePathPermissions> loadPermissionsForItem(final Long storageId, final String path,
                                                                final DataStorageItemType type) {
        if (DataStorageItemType.Folder.equals(type)) {
            final String folderPath = normalizePath(path);
            return pathPermissionsDao.loadByPath(storageId, folderPath, null);
        } else {
            final int lastSeparatorIndex = path.lastIndexOf(ProviderUtils.DELIMITER);
            final String folderPath = normalizePath(extractFolderPath(path, lastSeparatorIndex));
            final String fileName = extractFileName(path, lastSeparatorIndex);
            return pathPermissionsDao.loadByPath(storageId, folderPath, fileName);
        }
    }

    private void verifyRawPermissionsVO(final StoragePathPermissionsVO vo) {
        Assert.state(Objects.nonNull(vo), "Permissions shall be specified");
        Assert.state(StringUtils.isNotBlank(vo.getPath()), "Item path shall be specified");
        verifyStorageItemType(vo.getType());
    }

    private void verifyStorageItemType(final DataStorageItemType type) {
        Assert.state(Objects.nonNull(type), "Item type shall be specified");
    }

    private String extractFileName(final String path, final int lastSeparatorIndex) {
        return ProviderUtils.withoutLeadingDelimiter(
                lastSeparatorIndex == -1 ? path : path.substring(lastSeparatorIndex));
    }

    private String extractFolderPath(final String path, final int lastSeparatorIndex) {
        return lastSeparatorIndex == -1 ? Strings.EMPTY : path.substring(0, lastSeparatorIndex + 1);
    }

    private int toSimpleMask(final int extendedMask) {
        int simpleMask = 0;
        for (final AclPermission p : AclPermission.getBasicPermissions()) {
            if ((extendedMask & p.getMask()) == p.getMask()) {
                simpleMask = simpleMask | p.getSimpleMask();
            }
        }

        return simpleMask;
    }
}
