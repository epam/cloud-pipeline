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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
        pathPermissionsDao.deleteForStorageAndSid(storageId, sidName, principal);
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
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteByStorageId(final Long storageId) {
        pathPermissionsDao.deleteForStorageId(storageId);
    }

    /**
     * Determines closest permission on given folder path and specified storage and current user.
     *
     * @param storageId storage ID
     * @param path path to file
     * @return permissions if available or throws AccessDeniedException if no permissions exist.
     */
    public void canWriteToFolder(final Long storageId, final String path) {
        orThrowAccessDenied(findFolderPermissions(storageId, path).filter(this::hasWritePermissions));
    }

    public void canReadFolder(final Long storageId, final String path) {
        orThrowAccessDenied(findFolderPermissions(storageId, path));
    }

    /**
     * Determines nearest permission on given file path for specified storage and current user.
     * - If permissions granted directly to file returns permission on this file.
     * - If permissions granted to one of the parent folder, returns permissions on the nearest parent folder if found.
     * - Otherwise, AccessDeniedException
     *
     * @param storageId storage ID
     * @param path path to file
     */
    public void canWriteToFile(final Long storageId, final String path) {
        orThrowAccessDenied(findFilePermissions(storageId, path).filter(this::hasWritePermissions));
    }

    public void canReadFile(final Long storageId, final String path) {
        orThrowAccessDenied(findFilePermissions(storageId, path));
    }

    public void canGetFolder(final Long storageId, final String path) {
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
            return StorageFolderListPermissionsContainer.builder().hasListPermissions(true).build();
        }

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
        sids.add(getSid(user.getUserName(), true));
        sids.addAll(ListUtils.emptyIfNull(user.getRoles()).stream()
                .map(role -> getSid(role.getName(), false))
                .collect(Collectors.toList())); // TODO: do we need to add groups?
        return sids;
    }

    private SidImpl getSid(final String sidName, final boolean isPrincipal) {
        final SidImpl sid = new SidImpl();
        sid.setName(sidName.toUpperCase(Locale.ROOT));
        sid.setPrincipal(isPrincipal);
        return sid;
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
