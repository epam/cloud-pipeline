/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.manager.datastorage.providers.nfs;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AbstractCloudRegionCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.exception.CmdExecutionException;
import com.epam.pipeline.manager.CmdExecutor;
import com.epam.pipeline.manager.datastorage.FileShareMountManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.manager.datastorage.providers.nfs.NFSHelper.formatNfsPath;
import static com.epam.pipeline.manager.datastorage.providers.nfs.NFSHelper.getNfsRootPath;
import static com.epam.pipeline.manager.datastorage.providers.nfs.NFSHelper.normalizeMountPath;

@Service
public class NFSStorageMounter {

    private static final Logger LOGGER = LoggerFactory.getLogger(NFSStorageMounter.class);
    private static final String NFS_MOUNT_CMD_PATTERN = "sudo mount -t %s %s %s %s";
    private static final String CHOWN_CMD_PATTERN = "sudo chown %d:%d %s";
    /**
     * -l is for "lazy" unmounting: Detach the filesystem from the filesystem hierarchy now, and cleanup all references
     * to the filesystem as soon as it is not busy anymore
     * -f is for "force": in case of an unreachable NFS system
     */
    private static final String NFS_UNMOUNT_CMD_PATTERN = "sudo umount -l -f %s";
    private static final String NFS_CHECK_CMD_PATTERN = "stat -t %s";
    private static final String PROC_MOUNTS = "/proc/mounts";
    private static final long DEFAULT_MOUNT_CHECK_TIMEOUT_MILLS = 30000L;

    private final CmdExecutor cmdExecutor = new CmdExecutor();
    private final MessageHelper messageHelper;
    private final DataStorageDao dataStorageDao;
    private final CloudRegionManager regionManager;
    private final FileShareMountManager shareMountManager;
    private final String rootMountPoint;
    private final long timeoutMills;
    private final ConcurrentHashMap<String, ReentrantLock> rootMountPointLocks;

    public NFSStorageMounter(final MessageHelper messageHelper,
                             final DataStorageDao dataStorageDao,
                             final CloudRegionManager regionManager,
                             final FileShareMountManager shareMountManager,
                             @Value("${data.storage.nfs.root.mount.point}")
                             final String rootMountPoint,
                             @Value("${data.storage.nfs.mount.timeout.mills:"
                                     + DEFAULT_MOUNT_CHECK_TIMEOUT_MILLS + "}")
                             final long timeoutMills) {
        this.messageHelper = messageHelper;
        this.dataStorageDao = dataStorageDao;
        this.regionManager = regionManager;
        this.shareMountManager = shareMountManager;
        this.rootMountPoint = rootMountPoint;
        this.timeoutMills = timeoutMills;
        this.rootMountPointLocks = new ConcurrentHashMap<>();
    }

    public File mount(final NFSDataStorage dataStorage) {
        final FileShareMount fileShareMount = shareMountManager.load(dataStorage.getFileShareMountId());
        final File rootMount = getShareRootMount(dataStorage, fileShareMount);
        final ReentrantLock lock = getMountPointLock(rootMount);
        lock.lock();
        try {
            if (isMounted(rootMount)) {
                verifyMountIsResponsive(rootMount);
            } else {
                if (!rootMount.exists()) {
                    Assert.isTrue(rootMount.mkdirs(), messageHelper.getMessage(
                            MessageConstants.ERROR_DATASTORAGE_NFS_MOUNT_DIRECTORY_NOT_CREATED));
                }

                final AbstractCloudRegion cloudRegion = regionManager.load(fileShareMount.getRegionId());
                final String protocol = fileShareMount.getMountType().getProtocol();
                final AbstractCloudRegionCredentials credentials = cloudRegion.getProvider() == CloudProvider.AZURE ?
                        regionManager.loadCredentials(cloudRegion) : null;

                final String defaultMountOptions = StringUtils.isNotBlank(dataStorage.getMountOptions()) ?
                        dataStorage.getMountOptions() : fileShareMount.getMountOptions();

                final String mountOptions = NFSHelper.getNFSMountOption(cloudRegion, credentials,
                        defaultMountOptions, protocol);

                final String rootNfsPath = formatNfsPath(
                        dataStorage.isMountExactPath() ? dataStorage.getPath() : fileShareMount.getMountRoot(),
                        protocol
                );

                final String mountCmd = String.format(NFS_MOUNT_CMD_PATTERN, protocol, mountOptions,
                        rootNfsPath, rootMount.getAbsolutePath());
                try {
                    cmdExecutor.executeCommand(mountCmd, timeoutMills);
                } catch (CmdExecutionException e) {
                    NFSHelper.deleteFolderIfEmpty(rootMount);
                    LOGGER.error(messageHelper.getMessage(
                            MessageConstants.ERROR_DATASTORAGE_NFS_MOUNT_2, mountCmd, e.getMessage()));
                    throw new DataStorageException(messageHelper.getMessage(
                            MessageConstants.ERROR_DATASTORAGE_NFS_MOUNT, dataStorage.getName(),
                            dataStorage.getPath()), e);
                }
            }
            return getStorageMountPath(dataStorage, fileShareMount);
        } catch (IOException e) {
            throw new DataStorageException(messageHelper.getMessage(
                    messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_NFS_MOUNT, dataStorage.getName(),
                            dataStorage.getPath())), e);
        } finally {
            lock.unlock();
        }
    }

    public void unmountNFSIfEmpty(final NFSDataStorage storage) {
        final FileShareMount fileShareMount = shareMountManager.load(storage.getFileShareMountId());
        final File rootMount = getShareRootMount(storage, fileShareMount);
        final ReentrantLock lock = getMountPointLock(rootMount);
        lock.lock();
        try {
            // if mount exact path, storage will be mounted to the unique dir
            final List<AbstractDataStorage> remaining = storage.isMountExactPath()
                    ? Collections.singletonList(storage)
                    : dataStorageDao.loadDataStoragesByFileShareMountID(storage.getFileShareMountId());
            LOGGER.debug("Remaining NFS: " + remaining.stream().map(AbstractDataStorage::getPath)
                    .collect(Collectors.joining(";")) + " related with current file share mount");

            if (isMounted(rootMount) && isStorageOnlyOnNFS(storage, remaining)) {
                try {
                    final String umountCmd = String.format(NFS_UNMOUNT_CMD_PATTERN, rootMount.getAbsolutePath());
                    cmdExecutor.executeCommand(umountCmd);
                    FileUtils.deleteDirectory(rootMount);
                } catch (IOException e) {
                    throw new DataStorageException(e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void chown(final File file, final Long userUID, final Long groupUID) {
        final Long resolvedGroupUID = Optional.ofNullable(groupUID).orElse(userUID);
        final String path = file.getAbsoluteFile().getPath();
        final String cmd = String.format(CHOWN_CMD_PATTERN, userUID, resolvedGroupUID, path);
        try {
            cmdExecutor.executeCommand(cmd);
        } catch (CmdExecutionException e) {
            LOGGER.error("Failed to change owner for path {}:", path);
            LOGGER.error(e.getMessage(), e);
        }
    }

    /**
     * Checks whether the given path is an active mount point by reading {@code /proc/mounts}.
     * This avoids stat-ing the filesystem, which would block if the NFS server is unresponsive.
     * Falls back to {@link File#exists()} if {@code /proc/mounts} cannot be read.
     */
    private boolean isMounted(final File mountPoint) {
        final String procMounts = getProcMountsPath();
        final String path = mountPoint.getAbsolutePath();
        try (Stream<String> lines = Files.lines(Paths.get(procMounts))) {
            return lines.anyMatch(line -> {
                final String[] parts = line.split("\\s+");
                return parts.length > 1 && parts[1].equals(path);
            });
        } catch (IOException e) {
            LOGGER.warn("Failed to read {}, falling back to File.exists() for: {}",
                    procMounts, path, e);
            return mountPoint.exists();
        }
    }

    // We have it to be able to mock in tests
    String getProcMountsPath() {
        return PROC_MOUNTS;
    }

    /**
     * Verifies that a mounted NFS path is responsive by running {@code timeout <sec> ls <path>}.
     * If the NFS server is unresponsive, the {@code timeout} command will kill {@code ls}
     * and return exit code 124, causing a {@link CmdExecutionException}.
     */
    private void verifyMountIsResponsive(final File mountPoint) {
        final String checkCmd = String.format(NFS_CHECK_CMD_PATTERN, mountPoint.getAbsolutePath());
        try {
            cmdExecutor.executeCommand(checkCmd, timeoutMills);
        } catch (CmdExecutionException e) {
            throw new DataStorageException(String.format(
                    "NFS mount point is not responsive (check timed out after %d ms): %s",
                    timeoutMills, mountPoint.getAbsolutePath()), e);
        }
    }

    private ReentrantLock getMountPointLock(final File rootMount) {
        return rootMountPointLocks.computeIfAbsent(rootMount.getAbsolutePath(), k -> new ReentrantLock());
    }

    private File getStorageMountPath(final NFSDataStorage storage, final FileShareMount fileShareMount) {
        if (storage.isMountExactPath()) {
            final String flatStorageMountPAth =
                    normalizeMountPath(fileShareMount.getMountType(), storage.getPath(), true);
            return Paths.get(rootMountPoint, flatStorageMountPAth).toFile();
        } else {
            final String storageMountPath = normalizeMountPath(fileShareMount.getMountType(),
                    getNfsRootPath(storage.getPath()));
            return Paths.get(rootMountPoint, storageMountPath, getStorageName(storage.getPath())).toFile();
        }
    }

    private File getShareRootMount(final NFSDataStorage storage, final FileShareMount fileShareMount) {
        final String shareMountPath;
        if (storage.isMountExactPath()) {
            shareMountPath = normalizeMountPath(
                    fileShareMount.getMountType(), storage.getPath(), true);
        } else {
            shareMountPath = normalizeMountPath(
                    fileShareMount.getMountType(), fileShareMount.getMountRoot());
        }
        return Paths.get(rootMountPoint, shareMountPath).toFile();
    }

    private boolean isStorageOnlyOnNFS(AbstractDataStorage storage, List<AbstractDataStorage> remaining) {
        if (remaining.size() > 1) {
            return false;
        } else if (remaining.size() == 0) {
            throw new IllegalArgumentException("There are should be at least one storage with root path: "
                    + getNfsRootPath(storage.getPath()));
        }
        return remaining.get(0).getId().equals(storage.getId());
    }

    private String getStorageName(String path) {
        return path.replace(getNfsRootPath(path), "");
    }
}
