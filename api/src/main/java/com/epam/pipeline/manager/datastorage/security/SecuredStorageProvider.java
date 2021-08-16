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
import com.epam.pipeline.manager.datastorage.providers.StorageProvider;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
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
        assertFolderReadAccess(storage, path);
        final DataStorageListing listing = provider.getItems(storage, path, showVersion, pageSize, marker);
        listing.setResults(permissionProviderManager.process(storage, ListUtils.emptyIfNull(listing.getResults())));
        return listing;
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
        assertFileWriteAccess(storage, path);
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
        // TODO: 12.08.2021 Delete permissions
        assertFileWriteAccess(storage, path);
        provider.deleteFile(storage, path, version, totally);
    }

    public void deleteFolder(T storage, String path, Boolean totally) {
        // TODO: 12.08.2021 Assert all child item permissions
        // TODO: 12.08.2021 Delete permissions
        assertFolderWriteAccess(storage, path);
        provider.deleteFolder(storage, path, totally);
    }

    public DataStorageFile moveFile(T storage, String oldPath, String newPath) {
        // TODO: 12.08.2021 Move permissions
        assertFileReadAccess(storage, oldPath);
        assertFileWriteAccess(storage, oldPath);
        assertFileWriteAccess(storage, newPath);
        return provider.moveFile(storage, oldPath, newPath);
    }

    public DataStorageFolder moveFolder(T storage, String oldPath, String newPath) {
        // TODO: 12.08.2021 Assert all child item permissions
        // TODO: 12.08.2021 Move permissions
        assertFolderReadAccess(storage, oldPath);
        assertFolderWriteAccess(storage, oldPath);
        assertFolderWriteAccess(storage, newPath);
        return provider.moveFolder(storage, oldPath, newPath);
    }

    public DataStorageFile copyFile(T storage, String oldPath, String newPath) {
        // TODO: 12.08.2021 Copy permissions
        assertFileReadAccess(storage, oldPath);
        assertFileWriteAccess(storage, newPath);
        return provider.copyFile(storage, oldPath, newPath);
    }

    public DataStorageFolder copyFolder(T storage, String oldPath, String newPath) {
        // TODO: 12.08.2021 Assert all child item permissions
        // TODO: 12.08.2021 Copy permissions
        assertFolderReadAccess(storage, oldPath);
        assertFolderWriteAccess(storage, newPath);
        return provider.copyFolder(storage, oldPath, newPath);
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
        // TODO: 12.08.2021 Assert all child item permissions
        assertFolderReadAccess(storage, path);
        return provider.getDataSize(storage, path, pathDescription);
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

}
