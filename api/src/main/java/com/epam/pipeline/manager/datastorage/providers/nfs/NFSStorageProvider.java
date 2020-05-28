/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.ActionStatus;
import com.epam.pipeline.entity.datastorage.ContentDisposition;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AbstractCloudRegionCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.exception.CmdExecutionException;
import com.epam.pipeline.manager.CmdExecutor;
import com.epam.pipeline.manager.datastorage.FileShareMountManager;
import com.epam.pipeline.manager.datastorage.providers.StorageProvider;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3Constants;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.utils.FileContentUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.manager.datastorage.providers.nfs.NFSHelper.formatNfsPath;
import static com.epam.pipeline.manager.datastorage.providers.nfs.NFSHelper.getNfsRootPath;

/**
 * A {@link StorageProvider}, that integrates with NFS file systems. For browsing the filesystem, mounts it to the host
 * filesystem. Uses {@link CmdExecutor} for executing shell commands.
 */
@Service
public class NFSStorageProvider implements StorageProvider<NFSDataStorage> {
    private static final Logger LOGGER = LoggerFactory.getLogger(NFSStorageProvider.class);
    private static final String NFS_MOUNT_CMD_PATTERN = "sudo mount -t %s %s %s %s";

    /**
     * -l is for "lazy" unmounting: Detach the filesystem from the filesystem hierarchy now, and cleanup all references
     *    to the filesystem as soon as it is not busy anymore
     * -f is for "force": in case of an unreachable NFS system
     */
    private static final String NFS_UNMOUNT_CMD_PATTERN = "sudo umount -l -f %s";

    private static final Set<PosixFilePermission> PERMISSIONS = Arrays.stream(PosixFilePermission.values())
                                                                      .filter(p -> !p.name().startsWith("OTHERS"))
                                                                      .collect(Collectors.toSet());

    private CmdExecutor cmdExecutor;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private DataStorageDao dataStorageDao;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private CloudRegionManager regionManager;

    @Autowired
    private FileShareMountManager shareMountManager;

    @Value("${data.storage.nfs.root.mount.point}")
    private String rootMountPoint;

    public NFSStorageProvider() {
        this.cmdExecutor = new CmdExecutor();
    }

    public NFSStorageProvider(CmdExecutor cmdExecutor) {
        this.cmdExecutor = cmdExecutor;
    }

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
        File dataStorageRoot = mount(storage);
        if (!dataStorageRoot.exists()) {
            boolean created = dataStorageRoot.mkdirs();
            if (!created) {
                unmountNFSIfEmpty(storage);

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

    private synchronized File mount(NFSDataStorage dataStorage) {
        File mntDir = Paths.get(rootMountPoint, getMountDirName(dataStorage.getPath())).toFile();

        try {
            if(!mntDir.exists()) {
                Assert.isTrue(mntDir.mkdirs(), messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_NFS_MOUNT_DIRECTORY_NOT_CREATED));

                FileShareMount fileShareMount = shareMountManager.load(dataStorage.getFileShareMountId());

                String protocol = fileShareMount.getMountType().getProtocol();

                AbstractCloudRegion cloudRegion = regionManager.load(fileShareMount.getRegionId());
                AbstractCloudRegionCredentials credentials = cloudRegion.getProvider() == CloudProvider.AZURE ?
                         regionManager.loadCredentials(cloudRegion) : null;

                String mountOptions = NFSHelper.getNFSMountOption(cloudRegion, credentials,
                        dataStorage.getMountOptions(), protocol);

                String rootNfsPath = formatNfsPath(getNfsRootPath(dataStorage.getPath()), protocol);

                String mountCmd = String.format(NFS_MOUNT_CMD_PATTERN, protocol, mountOptions,
                                                rootNfsPath, mntDir.getAbsolutePath());
                try {
                    cmdExecutor.executeCommand(mountCmd);
                } catch (CmdExecutionException e) {
                    FileUtils.deleteDirectory(mntDir);
                    LOGGER.error(messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_NFS_MOUNT_2, mountCmd, e.getMessage()));
                    throw new DataStorageException(messageHelper.getMessage(
                            MessageConstants.ERROR_DATASTORAGE_NFS_MOUNT, dataStorage.getName(),
                            dataStorage.getPath()), e);
                }
            }
        } catch (IOException e) {
            throw new DataStorageException(messageHelper.getMessage(
                    messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_NFS_MOUNT, dataStorage.getName(),
                                             dataStorage.getPath())), e);
        }

        String storageName = getStorageName(dataStorage.getPath());
        return new File(mntDir, storageName);
    }

    private synchronized void unmountNFSIfEmpty(AbstractDataStorage storage) {
        String storagePath = storage.getPath();
        File mntDir = Paths.get(rootMountPoint, getMountDirName(storagePath)).toFile();
        List<AbstractDataStorage> remaining = dataStorageDao.loadDataStoragesByNFSPath(getNfsRootPath(storagePath));
        LOGGER.debug("Remaining NFS: " + remaining.stream().map(AbstractDataStorage::getPath)
                .collect(Collectors.joining(";")) + " related with current root path");
        if (mntDir.exists() && isStorageOnlyOnNFS(storage, remaining)) {
            try {
                String umountCmd = String.format(NFS_UNMOUNT_CMD_PATTERN, mntDir.getAbsolutePath());
                cmdExecutor.executeCommand(umountCmd);
                FileUtils.deleteDirectory(mntDir);
            } catch (IOException e) {
                throw new DataStorageException(e);
            }
        }
    }

    private boolean isStorageOnlyOnNFS(AbstractDataStorage storage, List<AbstractDataStorage> remaining) {
        if (remaining.size() > 1) {
            return false;
        } else if (remaining.size() == 0){
            throw new IllegalArgumentException("There are should be at least one storage with root path: "
                    + getNfsRootPath(storage.getPath()));
        }
        return remaining.get(0).getId().equals(storage.getId());
    }

    /**
     * Deletes NFS storage from the filesystem.
     * @param dataStorage a storage to delete
     * @throws DataStorageException if datastorage cannot be deleted
     */
    @Override
    public void deleteStorage(NFSDataStorage dataStorage) throws DataStorageException {
        File dataStorageRoot = mount(dataStorage);
        if (dataStorageRoot.exists()) {
            try {
                FileUtils.deleteDirectory(dataStorageRoot);
                LOGGER.debug("Storage: " + dataStorage.getPath() +
                        " with local path: " + dataStorageRoot + " was successfully deleted");
            } catch (IOException e) {
                unmountNFSIfEmpty(dataStorage);
                throw new DataStorageException(messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_NFS_DELETE_DIRECTORY), e);
            }
        }

        unmountNFSIfEmpty(dataStorage);
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
    public DataStorageListing getItems(NFSDataStorage dataStorage, String path, Boolean showVersion,
                                       Integer pageSize, String marker) {
        File dataStorageRoot = mount(dataStorage);
        File dir = path != null ? new File(dataStorageRoot, path) : dataStorageRoot;

        long offset = StringUtils.isNumeric(marker) ? Long.parseLong(marker) : 1;
        try (Stream<Path> dirStream = Files.walk(dir.toPath(), 1)) {
            List<AbstractDataStorageItem> dataStorageItems = dirStream
                .sorted()
                .skip(offset) // First element is a directory itself
                .limit(pageSize)
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

            Long nextOffset = offset + pageSize;
            try (Stream<Path> nextStream = Files.walk(dir.toPath(), 1)) {
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
        url.setUrl(baseApiHost + "datastorage/" + dataStorage.getId() + "/download?path=" + path);
        return url;
    }

    @Override
    public DataStorageDownloadFileUrl generateDataStorageItemUploadUrl(NFSDataStorage dataStorage, String path) {
        throw new UnsupportedOperationException();
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
        File dataStorageDir = mount(dataStorage);
        File file = new File(dataStorageDir, path);

        try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file))) {
            IOUtils.copy(dataStream, outputStream);
            setUmask(file);
        } catch (IOException e) {
            throw new DataStorageException(e);
        }

        return new DataStorageFile(path, file);
    }

    private String getStorageName(String path) {
        return  path.replace(getNfsRootPath(path), "");
    }

    private String getMountDirName(String nfsPath) {
        return getNfsRootPath(nfsPath).replace(":", "/");
    }

    @Override
    public DataStorageFolder createFolder(NFSDataStorage dataStorage, String path) throws DataStorageException {
        File dataStorageDir = mount(dataStorage);
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

    @Override
    public void deleteFile(NFSDataStorage dataStorage, String path, String version, Boolean totally)
        throws DataStorageException {
        File dataStorageDir = mount(dataStorage);
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
        File dataStorageDir = mount(dataStorage);
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

    private void setUmask(File file) throws IOException {
        if (!SystemUtils.IS_OS_WINDOWS) {
            Files.setPosixFilePermissions(file.toPath(), PERMISSIONS);
        }
    }

    private File move(NFSDataStorage dataStorage, String oldPath, String newPath) {
        File dataStorageDir = mount(dataStorage);
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
    public DataStorageFolder moveFolder(NFSDataStorage dataStorage, String oldPath, String newPath)
        throws DataStorageException {
        File newFolder = move(dataStorage, oldPath, newPath);
        return new DataStorageFolder(newPath, newFolder);
    }

    @Override
    public boolean checkStorage(NFSDataStorage dataStorage) {
        try {
            File storageRoot = mount(dataStorage);
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
        File mntDir = mount(dataStorage);
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
        File mntDir = mount(dataStorage);
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
}
