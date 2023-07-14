/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.datastorage.providers.nfs;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRule;
import com.epam.pipeline.dto.datastorage.lifecycle.execution.StorageLifecycleRuleExecution;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreActionRequest;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.ActionStatus;
import com.epam.pipeline.entity.datastorage.ContentDisposition;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageItemType;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.exception.ObjectNotFoundException;
import com.epam.pipeline.manager.datastorage.FileShareMountManager;
import com.epam.pipeline.manager.datastorage.lifecycle.DataStorageLifecycleRestoredListingContainer;
import com.epam.pipeline.manager.datastorage.providers.StorageProvider;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3Constants;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.utils.FileContentUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.manager.datastorage.providers.nfs.NFSHelper.getNfsRootPath;

/**
 * A {@link StorageProvider}, that integrates with NFS file systems. For browsing the filesystem, mounts it to the host
 * filesystem using {@link NFSStorageMounter}.
 */
@Service
@RequiredArgsConstructor
public class NFSStorageProvider implements StorageProvider<NFSDataStorage> {

    private static final Logger LOGGER = LoggerFactory.getLogger(NFSStorageProvider.class);
    private static final Set<PosixFilePermission> PERMISSIONS = Arrays.stream(PosixFilePermission.values())
                                                                      .filter(p -> !p.name().startsWith("OTHERS"))
                                                                      .collect(Collectors.toSet());
    private static final int DEFAULT_PAGE_SIZE = 1000;

    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;
    private final FileShareMountManager shareMountManager;
    private final NFSStorageMounter nfsStorageMounter;

    @Override
    public DataStorageType getStorageType() {
        return DataStorageType.NFS;
    }

    /**
     * Mounts an NFS to the filesystem and registers it in the application. The NFS share creation is not in the
     * application's responsibilities
     * @param storage a storage to mount
     * @return the path to already created NFS storage
     * @throws DataStorageException
     */
    @Override
    public String createStorage(NFSDataStorage storage) throws DataStorageException {
        File dataStorageRoot = nfsStorageMounter.mount(storage);
        if (!dataStorageRoot.exists()) {
            boolean created = dataStorageRoot.mkdirs();
            if (!created) {
                nfsStorageMounter.unmountNFSIfEmpty(storage);

                throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_NFS_CREATE_FOLDER, storage.getPath()));
            }
        }

        return storage.getPath();
    }

    @Override
    public ActionStatus postCreationProcessing(final NFSDataStorage storage) {
        return ActionStatus.notSupported();
    }

    /**
     * Deletes NFS storage from the filesystem.
     * @param dataStorage a storage to delete
     * @throws DataStorageException if datastorage cannot be deleted
     */
    @Override
    public void deleteStorage(NFSDataStorage dataStorage) throws DataStorageException {
        File dataStorageRoot = nfsStorageMounter.mount(dataStorage);
        if (dataStorageRoot.exists()) {
            try {
                FileUtils.deleteDirectory(dataStorageRoot);
                LOGGER.debug("Storage: " + dataStorage.getPath() +
                        " with local path: " + dataStorageRoot + " was successfully deleted");
            } catch (IOException e) {
                nfsStorageMounter.unmountNFSIfEmpty(dataStorage);
                throw new DataStorageException(messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_NFS_DELETE_DIRECTORY), e);
            }
        }

        nfsStorageMounter.unmountNFSIfEmpty(dataStorage);
    }

    @Override
    public void applyStoragePolicy(NFSDataStorage dataStorage) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void restoreFileVersion(NFSDataStorage dataStorage, String path, String version)
        throws DataStorageException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<DataStorageFile> listDataStorageFiles(final NFSDataStorage dataStorage, final String path) {
        final File dataStorageRoot = nfsStorageMounter.mount(dataStorage);
        final File dir = path != null ? new File(dataStorageRoot, path) : dataStorageRoot;
        try (Stream<Path> dirStream = Files.walk(dir.toPath(), 1)) {
            return dirStream
                    .map(p -> {
                        File file = p.toFile();

                        AbstractDataStorageItem item;
                        if (file.isDirectory()) {
                            item = new DataStorageFolder();
                        } else {
                            //set size if it's a file
                            DataStorageFile dataStorageFile = new DataStorageFile();
                            dataStorageFile.setSize(file.length());
                            dataStorageFile.setChanged(S3Constants.getAwsDateFormat()
                                    .format(new Date(file.lastModified())));
                            item = dataStorageFile;
                        }

                        item.setName(file.getName());
                        item.setPath(dataStorageRoot.toURI().relativize(file.toURI()).getPath());

                        return item;
                    })
                    .filter(DataStorageFile.class::isInstance)
                    .map(DataStorageFile.class::cast);
        } catch (IOException e) {
            throw new DataStorageException(e);
        }
    }

    @Override
    public DataStorageListing getItems(final NFSDataStorage dataStorage, final String path,
                                       final Boolean showVersion, final Integer pageSize, final String marker) {
        final File dataStorageRoot = nfsStorageMounter.mount(dataStorage);
        final File startingPath = path != null ? new File(dataStorageRoot, path) : dataStorageRoot;

        // If we list file - just return it as result
        if (startingPath.isFile()) {
            return new DataStorageListing(
                    null,
                    Collections.singletonList(mapFileToDataStorageFile(dataStorageRoot, startingPath))
            );
        }

        long offset = StringUtils.isNumeric(marker) ? Long.parseLong(marker) : 1;
        try (Stream<Path> dirStream = Files.walk(startingPath.toPath(), 1)) {
            final int effectivePageSize = Optional.ofNullable(pageSize).orElse(DEFAULT_PAGE_SIZE);
            List<AbstractDataStorageItem> dataStorageItems = dirStream
                .sorted()
                .skip(offset) // First element is a directory itself
                .limit(effectivePageSize)
                .map(p -> {
                    File file = p.toFile();

                    AbstractDataStorageItem item;
                    if (file.isDirectory()) {
                        item = new DataStorageFolder();
                    } else {
                        //set size if it's a file
                        DataStorageFile dataStorageFile = new DataStorageFile();
                        dataStorageFile.setSize(file.length());
                        dataStorageFile.setChanged(S3Constants.getAwsDateFormat()
                                .format(new Date(file.lastModified())));
                        item = dataStorageFile;
                    }

                    item.setName(file.getName());
                    item.setPath(dataStorageRoot.toURI().relativize(file.toURI()).getPath());

                    return item;
                })
                .collect(Collectors.toList());

            DataStorageListing listing = new DataStorageListing();
            listing.setResults(dataStorageItems);

            Long nextOffset = offset + effectivePageSize;
            try (Stream<Path> nextStream = Files.walk(startingPath.toPath(), 1)) {
                if (nextStream.skip(nextOffset).findFirst().isPresent()) {
                    listing.setNextPageMarker(nextOffset.toString());
                }
            }

            return listing;
        } catch (IOException e) {
            throw new DataStorageException(e);
        }
    }

    @Override
    public DataStorageListing getItems(final NFSDataStorage dataStorage, final String path,
                                       final Boolean showVersion, final Integer pageSize, final String marker,
                                       final DataStorageLifecycleRestoredListingContainer restoredListing) {
        if (Objects.nonNull(restoredListing)) {
            throw new UnsupportedOperationException("Restore mechanism isn't supported for this provider.");
        }
        return getItems(dataStorage, path, showVersion, pageSize, marker);
    }

    @Override
    public Optional<DataStorageFile> findFile(final NFSDataStorage dataStorage,
                                              final String path,
                                              final String version) {
        final File dataStorageRoot = nfsStorageMounter.mount(dataStorage);
        return Optional.of(new File(dataStorageRoot, path))
                .filter(File::exists)
                .map(file -> mapFileToDataStorageFile(dataStorageRoot, file));
    }

    @Override
    public DataStorageDownloadFileUrl generateDownloadURL(NFSDataStorage dataStorage, String path,
                                                          String version, ContentDisposition contentDisposition) {

        String baseApiHostExternal = preferenceManager.getPreference(SystemPreferences.BASE_API_HOST_EXTERNAL);
        String baseApiHost = StringUtils.isNotBlank(baseApiHostExternal) ? baseApiHostExternal :
                             preferenceManager.getPreference(SystemPreferences.BASE_API_HOST);
        if (StringUtils.isBlank(baseApiHost)) {
            throw new IllegalArgumentException(String.format("Cannot generate URL: preference %s or %s is not set",
                                                             SystemPreferences.BASE_API_HOST.getKey(),
                                                             SystemPreferences.BASE_API_HOST_EXTERNAL.getKey()));
        }

        DataStorageDownloadFileUrl url = new DataStorageDownloadFileUrl();
        url.setUrl(baseApiHost + "datastorage/" + dataStorage.getId() + "/download?path=" + encodeUrl(path));
        return url;
    }

    @Override
    public DataStorageDownloadFileUrl generateDataStorageItemUploadUrl(NFSDataStorage dataStorage, String path) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataStorageDownloadFileUrl generateUrl(final NFSDataStorage dataStorage,
                                                  final String path,
                                                  final List<String> permissions,
                                                  final Duration duration) {
        return generateDownloadURL(dataStorage, path, null, null);
    }

    @Override
    public DataStorageFile createFile(NFSDataStorage dataStorage, String path, byte[] contents)
        throws DataStorageException {
        try (ByteArrayInputStream dataStream = new ByteArrayInputStream(contents)) {
            return createFile(dataStorage, path, dataStream);
        } catch (IOException e) {
            throw new DataStorageException(e);
        }
    }

    @Override
    public DataStorageFile createFile(NFSDataStorage dataStorage, String path, InputStream dataStream)
        throws DataStorageException {
        File dataStorageDir = nfsStorageMounter.mount(dataStorage);
        File file = new File(dataStorageDir, path);

        try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file))) {
            IOUtils.copy(dataStream, outputStream);
            setUmask(file);
        } catch (IOException e) {
            throw new DataStorageException(e);
        }

        return new DataStorageFile(path, file);
    }

    @Override
    public DataStorageFolder createFolder(NFSDataStorage dataStorage, String path) throws DataStorageException {
        File dataStorageDir = nfsStorageMounter.mount(dataStorage);
        File folder = new File(dataStorageDir, path);
        if (!folder.mkdirs()) {
            throw new DataStorageException(messageHelper.getMessage(
                MessageConstants.ERROR_DATASTORAGE_NFS_CREATE_FOLDER, dataStorage.getPath()));
        }
        try {
            setUmask(folder);
        } catch (IOException e) {
            throw new DataStorageException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_CANNOT_CREATE_FILE, folder.getPath()), e);
        }
        return new DataStorageFolder(path, folder);
    }

    private void setUmask(File file) throws IOException {
        if (!SystemUtils.IS_OS_WINDOWS) {
            Files.setPosixFilePermissions(file.toPath(), PERMISSIONS);
        }
    }

    @Override
    public void deleteFile(NFSDataStorage dataStorage, String path, String version, Boolean totally)
        throws DataStorageException {
        File dataStorageDir = nfsStorageMounter.mount(dataStorage);
        File file = new File(dataStorageDir, path);

        try {
            Files.delete(file.toPath());
        } catch (IOException e) {
            throw new DataStorageException(e);
        }
    }

    @Override
    public void deleteFolder(NFSDataStorage dataStorage, String path, Boolean totally)
        throws DataStorageException {
        File dataStorageDir = nfsStorageMounter.mount(dataStorage);
        File folder = new File(dataStorageDir, path);

        try {
            FileUtils.deleteDirectory(folder);
        } catch (IOException e) {
            throw new DataStorageException(e);
        }
    }

    @Override
    public DataStorageFile moveFile(NFSDataStorage dataStorage, String oldPath, String newPath)
        throws DataStorageException {
        File newFile = move(dataStorage, oldPath, newPath);
        return new DataStorageFile(newPath, newFile);
    }

    @Override
    public DataStorageFolder moveFolder(NFSDataStorage dataStorage, String oldPath, String newPath)
            throws DataStorageException {
        File newFolder = move(dataStorage, oldPath, newPath);
        return new DataStorageFolder(newPath, newFolder);
    }

    private File move(NFSDataStorage dataStorage, String oldPath, String newPath) {
        File dataStorageDir = nfsStorageMounter.mount(dataStorage);
        File file = new File(dataStorageDir, oldPath);
        File newFile = new File(dataStorageDir, newPath);

        try {
            if (file.isDirectory()) {
                FileUtils.moveDirectory(file, newFile);
            } else {
                FileUtils.moveFile(file, newFile);
            }
        } catch (IOException e) {
            throw new DataStorageException(e);
        }

        return newFile;
    }

    @Override
    public DataStorageFile copyFile(final NFSDataStorage dataStorage, final String oldPath, final String newPath) {
        File newFile = copy(dataStorage, oldPath, newPath);
        return new DataStorageFile(newPath, newFile);
    }

    @Override
    public DataStorageFolder copyFolder(final NFSDataStorage dataStorage, final String oldPath, final String newPath) {
        File newFolder = copy(dataStorage, oldPath, newPath);
        return new DataStorageFolder(newPath, newFolder);
    }

    private File copy(final NFSDataStorage dataStorage, final String oldPath, final String newPath) {
        final File dataStorageDir = nfsStorageMounter.mount(dataStorage);
        final File oldFile = new File(dataStorageDir, oldPath);
        final File newFile = new File(dataStorageDir, newPath);
        copy(oldFile, newFile);
        return newFile;
    }

    private void copy(final File oldFile, final File newFile) {
        try {
            if (oldFile.isDirectory()) {
                FileUtils.copyDirectory(oldFile, newFile);
            } else {
                FileUtils.copyFile(oldFile, newFile);
            }
        } catch (IOException e) {
            throw new DataStorageException(e);
        }
    }

    @Override
    public boolean checkStorage(NFSDataStorage dataStorage) {
        try {
            File storageRoot = nfsStorageMounter.mount(dataStorage);
            return storageRoot.exists() && storageRoot.isDirectory();
        } catch (DataStorageException e) {
            return false;
        }
    }

    @Override
    public Map<String, String> updateObjectTags(NFSDataStorage dataStorage, String path, Map<String, String> tags,
                                                String version) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> listObjectTags(NFSDataStorage dataStorage, String path, String version) {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, String> deleteObjectTags(NFSDataStorage dataStorage, String path, Set<String> tagsToDelete,
                                                String version) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataStorageItemContent getFile(NFSDataStorage dataStorage, String path, String version,
                                          Long maxDownloadSize) {
        File mntDir = nfsStorageMounter.mount(dataStorage);
        File file = new File(mntDir, path);

        try (FileInputStream fis = new FileInputStream(file)) {
            DataStorageItemContent content = new DataStorageItemContent();

            long bytesToRead = file.length();
            if (file.length() > maxDownloadSize) {
                content.setTruncated(true);
                bytesToRead = maxDownloadSize;
            }

            byte[] contentBytes = IOUtils.toByteArray(fis, bytesToRead);

            if (FileContentUtils.isBinaryContent(contentBytes)) {
                content.setMayBeBinary(true);
            } else {
                content.setContent(contentBytes);
            }

            return content;
        } catch (IOException e) {
            throw new DataStorageException(e);
        }
    }

    @Override
    public DataStorageStreamingContent getStream(NFSDataStorage dataStorage, String path, String version) {
        File mntDir = nfsStorageMounter.mount(dataStorage);
        File file = new File(mntDir, path);

        try {
            return new DataStorageStreamingContent(file);
        } catch (FileNotFoundException e) {
            throw new DataStorageException(e);
        }
    }

    @Override
    public String buildFullStoragePath(NFSDataStorage dataStorage, String name) {
        return getNfsRootPath(dataStorage.getPath()) + name;
    }

    @Override
    public String getDefaultMountOptions(NFSDataStorage dataStorage) {
        return shareMountManager.load(dataStorage.getFileShareMountId()).getMountOptions();
    }

    @Override
    public PathDescription getDataSize(final NFSDataStorage dataStorage, final String path,
                                       final PathDescription pathDescription) {
        throw new UnsupportedOperationException("Getting item size info is not implemented for NFS storages");
    }

    @Override
    public void verifyStorageLifecyclePolicyRule(final StorageLifecycleRule rule) {
        throw new UnsupportedOperationException("Lifecycle policy mechanism isn't supported for this provider.");
    }

    @Override
    public void verifyStorageLifecycleRuleExecution(final StorageLifecycleRuleExecution execution) {
        throw new UnsupportedOperationException("Lifecycle policy mechanism isn't supported for this provider.");
    }

    @Override
    public void verifyRestoreActionSupported() {
        throw new UnsupportedOperationException("Restore mechanism isn't supported for this provider.");
    }

    @Override
    public String verifyOrDefaultRestoreMode(final StorageRestoreActionRequest actionRequest) {
        throw new UnsupportedOperationException("Restore mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageItemType getItemType(final NFSDataStorage dataStorage,
                                           final String path,
                                           final String version) {
        final File mntDir = nfsStorageMounter.mount(dataStorage);
        final File file = new File(mntDir, path);
        if (!file.exists()) {
            throw new ObjectNotFoundException(messageHelper
                    .getMessage(MessageConstants.ERROR_DATASTORAGE_PATH_NOT_FOUND, path, dataStorage.getName()));
        }
        return file.isFile() ? DataStorageItemType.File : DataStorageItemType.Folder;
    }

    private String encodeUrl(final String path) {
        try {
            return URLEncoder.encode(path, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static DataStorageFile mapFileToDataStorageFile(final File dataStorageRoot, final File file) {
        final DataStorageFile dataStorageFile = new DataStorageFile();
        dataStorageFile.setSize(file.length());
        dataStorageFile.setChanged(S3Constants.getAwsDateFormat().format(new Date(file.lastModified())));
        dataStorageFile.setName(file.getName());
        dataStorageFile.setPath(dataStorageRoot.toURI().relativize(file.toURI()).getPath());
        return dataStorageFile;
    }
}
