package com.epam.pipeline.manager.datastorage.security;

import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.ActionStatus;
import com.epam.pipeline.entity.datastorage.ContentDisposition;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageItemType;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionPathType;
import com.epam.pipeline.entity.region.VersioningAwareRegion;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.datastorage.providers.StorageProvider;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.AccessDeniedException;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class SecuredStorageProvider<T extends AbstractDataStorage> implements StorageProvider<T> {

    private final StorageProvider<? super T> provider;
    private final StoragePermissionProviderManager permissionProviderManager;

    public DataStorageType getStorageType() {
        return provider.getStorageType();
    }

    public String createStorage(T storage) {
        return provider.createStorage(storage);
    }

    public ActionStatus postCreationProcessing(T storage) {
        return provider.postCreationProcessing(storage);
    }

    public void deleteStorage(T storage) {
        provider.deleteStorage(storage);
    }

    public void applyStoragePolicy(T storage) {
        provider.applyStoragePolicy(storage);
    }

    public void restoreFileVersion(T storage, String path, String version) {
        assertFileWriteAccess(storage, path);
        provider.restoreFileVersion(storage, path, version);
    }

    public Stream<DataStorageFile> listDataStorageFiles(T storage, String path) {
        assertFolderReadAccess(storage, path);
        return provider.listDataStorageFiles(storage, path);
    }

    public DataStorageListing getItems(T storage, String path, Boolean showVersion,
                                       Integer pageSize, String marker) {
        return permissionProviderManager.apply(storage, path, nextMarker ->
                provider.getItems(storage, path, showVersion, pageSize, nextMarker))
                .orElseThrow(() -> new AccessDeniedException(
                        String.format("Data storage path %s listing is not allowed.", path)));
    }

    public Optional<DataStorageFile> findFile(T storage, String path, String version) {
        assertFileReadAccess(storage, path);
        return provider.findFile(storage, path, version);
    }

    public DataStorageDownloadFileUrl generateDownloadURL(T storage, String path, String version,
                                                          ContentDisposition contentDisposition) {
        assertFileReadAccess(storage, path);
        return provider.generateDownloadURL(storage, path, version, contentDisposition);
    }

    public DataStorageDownloadFileUrl generateDataStorageItemUploadUrl(T storage, String path) {
        assertFileWriteAccess(storage, path);
        return provider.generateDataStorageItemUploadUrl(storage, path);
    }

    public DataStorageDownloadFileUrl generateUrl(T storage, String path,
                                                  List<String> permissions, Duration duration) {
        assertAccess(storage, path, permissions);
        return provider.generateUrl(storage, path, permissions, duration);
    }

    public DataStorageFile createFile(T storage, String path, byte[] contents) {
        assertFileWriteAccess(storage, path);
        return provider.createFile(storage, path, contents);
    }

    public DataStorageFile createFile(T storage, String path, InputStream dataStream) {
        assertFileWriteAccess(storage, path);
        return provider.createFile(storage, path, dataStream);
    }

    public DataStorageFolder createFolder(T storage, String path) {
        assertFolderWriteAccess(storage, path);
        return provider.createFolder(storage, path);
    }

    public void deleteFile(T storage, String path, String version, Boolean totally) {
        assertFileWriteAccess(storage, path);
        provider.deleteFile(storage, path, version, totally);
        permissionProviderManager.deleteFilePermissions(storage, path, version, totally);
    }

    public void deleteFolder(T storage, String path, Boolean totally) {
        assertFolderRecursiveWriteAccess(storage, path);
        provider.deleteFolder(storage, path, totally);
        permissionProviderManager.deleteFolderPermissions(storage, path, totally);
    }

    public DataStorageFile moveFile(T storage, String oldPath, String newPath) {
        assertFileReadAccess(storage, oldPath);
        assertFileWriteAccess(storage, oldPath);
        assertFileWriteAccess(storage, newPath);
        final DataStorageFile file = provider.moveFile(storage, oldPath, newPath);
        permissionProviderManager.moveFilePermissions(storage, oldPath, newPath);
        return file;
    }

    public DataStorageFolder moveFolder(T storage, String oldPath, String newPath) {
        assertFolderRecursiveReadWriteAccess(storage, oldPath);
        assertFolderWriteAccess(storage, newPath);
        final DataStorageFolder folder = provider.moveFolder(storage, oldPath, newPath);
        permissionProviderManager.moveFolderPermissions(storage, oldPath, newPath);
        return folder;
    }

    public DataStorageFile copyFile(T storage, String oldPath, String newPath) {
        assertFileReadAccess(storage, oldPath);
        assertFileWriteAccess(storage, newPath);
        final DataStorageFile file = provider.copyFile(storage, oldPath, newPath);
        permissionProviderManager.copyFilePermissions(storage, oldPath, newPath);
        return file;
    }

    public DataStorageFolder copyFolder(T storage, String oldPath, String newPath) {
        assertFolderRecursiveReadAccess(storage, oldPath);
        assertFolderWriteAccess(storage, newPath);
        final DataStorageFolder folder = provider.copyFolder(storage, oldPath, newPath);
        permissionProviderManager.copyFolderPermissions(storage, oldPath, newPath);
        return folder;
    }

    public boolean checkStorage(T storage) {
        return provider.checkStorage(storage);
    }

    public Map<String, String> updateObjectTags(T storage, String path, Map<String, String> tags,
                                                String version) {
        assertFileWriteAccess(storage, path);
        return provider.updateObjectTags(storage, path, tags, version);
    }

    public Map<String, String> listObjectTags(T storage, String path, String version) {
        assertFileReadAccess(storage, path);
        return provider.listObjectTags(storage, path, version);
    }

    public Map<String, String> deleteObjectTags(T storage, String path, Set<String> tagsToDelete,
                                                String version) {
        assertFileWriteAccess(storage, path);
        return provider.deleteObjectTags(storage, path, tagsToDelete, version);
    }

    public DataStorageItemContent getFile(T storage, String path, String version,
                                          Long maxDownloadSize) {
        assertFileReadAccess(storage, path);
        return provider.getFile(storage, path, version, maxDownloadSize);
    }

    public DataStorageStreamingContent getStream(T storage, String path, String version) {
        assertFileReadAccess(storage, path);
        return provider.getStream(storage, path, version);
    }

    public String buildFullStoragePath(T storage, String name) {
        return provider.buildFullStoragePath(storage, name);
    }

    public String getDefaultMountOptions(T storage) {
        return provider.getDefaultMountOptions(storage);
    }

    public StoragePolicy buildPolicy(VersioningAwareRegion region, StoragePolicy storagePolicy) {
        return provider.buildPolicy(region, storagePolicy);
    }

    public PathDescription getDataSize(T storage, String path, PathDescription pathDescription) {
        assertFolderRecursiveReadAccess(storage, path);
        return provider.getDataSize(storage, path, pathDescription);
    }

    private void assertAccess(final T storage, final String path, final List<String> permissions) {
        if (StringUtils.endsWith(path, ProviderUtils.DELIMITER)) {
            assertFolderAccess(storage, path, permissions);
        } else {
            assertFileAccess(storage, path, permissions);
        }
    }

    private void assertFileAccess(final T storage, final String path, final List<String> permissions) {
        if (permissionProviderManager.isNotAllowed(storage, path, StoragePermissionPathType.FILE, permissions)) {
            throw new AccessDeniedException(String.format(
                    "Data storage path %s %s operations are not allowed.", path, permissions));
        }
    }

    private void assertFolderAccess(final T storage, final String path, final List<String> permissions) {
        if (permissionProviderManager.isNotRecursiveAllowed(storage, path, permissions)) {
            throw new AccessDeniedException(String.format(
                    "Data storage path %s recursive %s operations are not allowed.", path, permissions));
        }
    }

    private void assertFileReadAccess(T storage, String path) {
        assertReadAccess(storage, path, DataStorageItemType.File);
    }

    private void assertFolderReadAccess(T storage, String path) {
        assertReadAccess(storage, path, DataStorageItemType.Folder);
    }

    private void assertReadAccess(T storage,
                                  String path,
                                  DataStorageItemType type) {
        if (permissionProviderManager.isReadNotAllowed(storage, path, StoragePermissionPathType.from(type))) {
            throw new AccessDeniedException(String.format("Data storage path %s read is not allowed.", path));
        }
    }

    private void assertFileWriteAccess(T storage, String path) {
        assertWriteAccess(storage, path, DataStorageItemType.File);
    }

    private void assertFolderWriteAccess(T storage, String path) {
        assertWriteAccess(storage, path, DataStorageItemType.Folder);
    }

    private void assertWriteAccess(T storage,
                                   String path,
                                   DataStorageItemType type) {
        if (permissionProviderManager.isWriteNotAllowed(storage, path, StoragePermissionPathType.from(type))) {
            throw new AccessDeniedException(String.format("Data storage path %s write is not allowed.", path));
        }
    }

    private void assertFolderRecursiveReadAccess(T storage, String path) {
        if (permissionProviderManager.isRecursiveReadNotAllowed(storage, path)) {
            throw new AccessDeniedException(String.format("Data storage path %s recursive read is not allowed.",
                    path));
        }
    }

    private void assertFolderRecursiveWriteAccess(T storage, String path) {
        if (permissionProviderManager.isRecursiveWriteNotAllowed(storage, path)) {
            throw new AccessDeniedException(String.format("Data storage path %s recursive write is not allowed.",
                    path));
        }
    }

    private void assertFolderRecursiveReadWriteAccess(T storage, String path) {
        if (permissionProviderManager.isRecursiveReadWriteNotAllowed(storage, path)) {
            throw new AccessDeniedException(String.format("Data storage path %s recursive read/write is not allowed.",
                    path));
        }
    }

}
