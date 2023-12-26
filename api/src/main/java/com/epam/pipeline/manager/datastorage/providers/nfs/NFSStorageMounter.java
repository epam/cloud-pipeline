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
import com.epam.pipeline.entity.user.PipelineUser;
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
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    private final CmdExecutor cmdExecutor = new CmdExecutor();
    private final MessageHelper messageHelper;
    private final DataStorageDao dataStorageDao;
    private final CloudRegionManager regionManager;
    private final FileShareMountManager shareMountManager;
    private final String rootMountPoint;

    public NFSStorageMounter(final MessageHelper messageHelper,
                             final DataStorageDao dataStorageDao,
                             final CloudRegionManager regionManager,
                             final FileShareMountManager shareMountManager,
                             @Value("${data.storage.nfs.root.mount.point}")
                             final String rootMountPoint) {
        this.messageHelper = messageHelper;
        this.dataStorageDao = dataStorageDao;
        this.regionManager = regionManager;
        this.shareMountManager = shareMountManager;
        this.rootMountPoint = rootMountPoint;
    }

    public synchronized File mount(final NFSDataStorage dataStorage) {
        try {
            final FileShareMount fileShareMount = shareMountManager.load(dataStorage.getFileShareMountId());
            final File mntDir = getStorageMountRoot(dataStorage, fileShareMount);
            final File rootMount = getShareRootMount(fileShareMount);
            if (!rootMount.exists()) {
                Assert.isTrue(rootMount.mkdirs(), messageHelper.getMessage(
                        MessageConstants.ERROR_DATASTORAGE_NFS_MOUNT_DIRECTORY_NOT_CREATED));

                final AbstractCloudRegion cloudRegion = regionManager.load(fileShareMount.getRegionId());
                final String protocol = fileShareMount.getMountType().getProtocol();
                final AbstractCloudRegionCredentials credentials = cloudRegion.getProvider() == CloudProvider.AZURE ?
                        regionManager.loadCredentials(cloudRegion) : null;

                final String defaultMountOptions = StringUtils.isNotBlank(dataStorage.getMountOptions()) ?
                        dataStorage.getMountOptions() : fileShareMount.getMountOptions();

                final String mountOptions = NFSHelper.getNFSMountOption(cloudRegion, credentials,
                        defaultMountOptions, protocol);

                final String rootNfsPath = formatNfsPath(fileShareMount.getMountRoot(), protocol);

                final String mountCmd = String.format(NFS_MOUNT_CMD_PATTERN, protocol, mountOptions,
                        rootNfsPath, rootMount.getAbsolutePath());
                try {
                    cmdExecutor.executeCommand(mountCmd);
                } catch (CmdExecutionException e) {
                    NFSHelper.deleteFolderIfEmpty(rootMount);
                    LOGGER.error(messageHelper.getMessage(
                            MessageConstants.ERROR_DATASTORAGE_NFS_MOUNT_2, mountCmd, e.getMessage()));
                    throw new DataStorageException(messageHelper.getMessage(
                            MessageConstants.ERROR_DATASTORAGE_NFS_MOUNT, dataStorage.getName(),
                            dataStorage.getPath()), e);
                }
            }
            String storageName = getStorageName(dataStorage.getPath());
            return new File(mntDir, storageName);
        } catch (IOException e) {
            throw new DataStorageException(messageHelper.getMessage(
                    messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_NFS_MOUNT, dataStorage.getName(),
                            dataStorage.getPath())), e);
        }
    }

    public synchronized void unmountNFSIfEmpty(AbstractDataStorage storage) {
        final FileShareMount fileShareMount = shareMountManager.load(storage.getFileShareMountId());
        final File rootMount = getShareRootMount(fileShareMount);
        final List<AbstractDataStorage> remaining = dataStorageDao.loadDataStoragesByFileShareMountID(
                storage.getFileShareMountId());
        LOGGER.debug("Remaining NFS: " + remaining.stream().map(AbstractDataStorage::getPath)
                .collect(Collectors.joining(";")) + " related with current file share mount");

        if (rootMount.exists() && isStorageOnlyOnNFS(storage, remaining)) {
            try {
                final String umountCmd = String.format(NFS_UNMOUNT_CMD_PATTERN, rootMount.getAbsolutePath());
                cmdExecutor.executeCommand(umountCmd);
                FileUtils.deleteDirectory(rootMount);
            } catch (IOException e) {
                throw new DataStorageException(e);
            }
        }
    }

    public void chown(final File file, final PipelineUser user, final Integer seed, final Integer groupUID) {
        final Long userUID = user.getId() + seed;
        final Long resolvedGroupUID = Optional.ofNullable(groupUID).map(Integer::longValue).orElse(userUID);
        final String path = file.getAbsoluteFile().getPath();
        final String cmd = String.format(CHOWN_CMD_PATTERN, userUID, resolvedGroupUID, path);
        try {
            cmdExecutor.executeCommand(cmd);
        } catch (CmdExecutionException e) {
            LOGGER.error("Failed to change owner for path {}:", path);
            LOGGER.error(e.getMessage(), e);
        }
    }

    private File getStorageMountRoot(final NFSDataStorage dataStorage, final FileShareMount fileShareMount) {
        final String storageMountPath = normalizeMountPath(fileShareMount.getMountType(),
                getNfsRootPath(dataStorage.getPath()));
        return Paths.get(rootMountPoint, storageMountPath).toFile();
    }

    private File getShareRootMount(final FileShareMount fileShareMount) {
        final String shareMountPath = normalizeMountPath(fileShareMount.getMountType(), fileShareMount.getMountRoot());
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
