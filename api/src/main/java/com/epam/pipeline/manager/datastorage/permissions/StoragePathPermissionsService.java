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

import com.epam.pipeline.dao.datastorage.permissions.StoragePathPermissionsDao;
import com.epam.pipeline.dto.datastorage.permissions.StoragePathPermissions;
import com.epam.pipeline.dto.datastorage.permissions.StorageFolderListPermissionsContainer;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.entity.user.SidImpl;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.acl.AclPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoragePathPermissionsService {

    private final StoragePathPermissionsDao pathPermissionsDao;
    private final UserManager userManager;

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
     * Loads all permissions available for storage and current user.
     * @param storageId storage ID
     * @return permissions
     */
    public List<StoragePathPermissions> loadHierarchyForStorage(final Long storageId) {
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
     * Deletes permissions for specified storage. If sids provided permissions shall be
     * deleted for specified users/groups only.
     *
     * @param storageId storage ID
     * @param sids  users or groups to delete
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteByStorageIdAndSids(final Long storageId, final List<SidImpl> sids) {
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
    public List<SidImpl> loadSids(final Long storageId) {
        return pathPermissionsDao.findSids(storageId);
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

    /**
     * Determines if current user can see folder by given path. To have such access to folder user may have
     * permissions to:
     *  - folder directly
     *  - one oth parent folders
     *  - one of the child files or folders
     * That permission does not indicate that user can read folder content.
     * If no permissions provided for user directly or one of user`s group an AccessDeniedException shall be occurred.
     *
     * @param storageId storage ID
     * @param path path to folder
     */
    public void canGetFolder(final Long storageId, final String path) {
        log.debug("Checking current user can get folder '{}' for storage '{}'", path, storageId);
        getFolderListPermissions(storageId, path);
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

        if (hasPermissionsOnFolder(folderPath, storageId, sids)) {
            // has permissions on current folder or it's parents
            log.debug("Current user can list folder '{}' for storage '{}'", path, storageId);
            return StorageFolderListPermissionsContainer.builder().hasListPermissions(true).build();
        }
        log.debug("Current user cannot list folder '{}' for storage '{}'. Checking any permissions on child paths...",
                path, storageId);

        final List<StoragePathPermissions> childPaths = pathPermissionsDao.findByPrefix(storageId, sids, folderPath);
        if (CollectionUtils.isEmpty(childPaths)) {
            throw new AccessDeniedException("Access is denied");
        }
        return buildCurrentFolderPermissionsContainer(childPaths, folderPath);
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
        return pathPermissionsDao.findClosestFolderPermission(storageId, sids, prefixes);
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

    private List<String> getFolderNamesInCurrentFolder(final List<StoragePathPermissions> loadedPaths,
                                                       final String currentFolder) {
        return loadedPaths.stream()
                .map(StoragePathPermissions::getFolderPath)
                .distinct()
                .map(folderPath -> extractFolderName(folderPath, currentFolder))
                .distinct()
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
    }

    private List<String> getFileNamesInCurrentFolder(final List<StoragePathPermissions> loadedPaths,
                                                     final String currentFolder) {
        return loadedPaths.stream()
                .filter(permissions -> currentFolder.equals(permissions.getFolderPath()))
                .map(StoragePathPermissions::getFileName)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
    }

    private StorageFolderListPermissionsContainer buildCurrentFolderPermissionsContainer(
            final List<StoragePathPermissions> loadedPaths, final String currentFolder) {
        final StorageFolderListPermissionsContainer container = StorageFolderListPermissionsContainer.builder()
                .hasListPermissions(false)
                .build();
        final List<String> fileNames = getFileNamesInCurrentFolder(loadedPaths, currentFolder);
        if (CollectionUtils.isNotEmpty(fileNames)) {
            container.setFiles(fileNames);
        }
        final List<String> folderNames = getFolderNamesInCurrentFolder(loadedPaths, currentFolder);
        if (CollectionUtils.isNotEmpty(folderNames)) {
            container.setFolders(folderNames);
        }
        return container;
    }

    private boolean hasPermissionsOnFolder(final String currentFolder, final Long storageId,
                                           final List<SidImpl> sids) {
        final List<String> prefixes = splitPath(currentFolder);
        return pathPermissionsDao.countParentFoldersByStorageAndSids(storageId, sids, prefixes) > 0;
    }

    private boolean hasWritePermissions(final StoragePathPermissions permissions) {
        return (permissions.getMask() & AclPermission.WRITE.getMask()) != 0;
    }

    private void orThrowAccessDenied(final Optional<StoragePathPermissions> permissions) {
        permissions.orElseThrow(() -> new AccessDeniedException("Access is denied"));
    }
}
