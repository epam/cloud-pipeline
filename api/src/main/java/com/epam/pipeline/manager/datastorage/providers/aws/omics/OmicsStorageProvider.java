package com.epam.pipeline.manager.datastorage.providers.aws.omics;

import com.amazonaws.services.omics.model.FileInformation;
import com.amazonaws.services.omics.model.GetReadSetMetadataResult;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRule;
import com.epam.pipeline.dto.datastorage.lifecycle.execution.StorageLifecycleRuleExecution;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreActionRequest;
import com.epam.pipeline.entity.datastorage.*;
import com.epam.pipeline.entity.datastorage.aws.AWSDataStorage;
import com.epam.pipeline.entity.datastorage.aws.AWSOmicsReferenceDataStorage;
import com.epam.pipeline.entity.datastorage.aws.AWSOmicsSequenceDataStorage;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.AwsRegionCredentials;
import com.epam.pipeline.manager.datastorage.providers.StorageProvider;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3Constants;
import com.epam.pipeline.manager.region.CloudRegionManager;
import lombok.AllArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@AllArgsConstructor
public abstract class OmicsStorageProvider<T extends AbstractDataStorage> implements StorageProvider<T> {

    public static final String AWS_OMICS_STORE_PATH_TEMPLATE = "%s.storage.%s.amazonaws.com/%s";
    public static final String EMPTY_MO = "";

    private final MessageHelper messageHelper;
    private final CloudRegionManager cloudRegionManager;

    @Override
    public String buildFullStoragePath(final T dataStorage, final String name) {
        return name;
    }

    @Override
    public boolean checkStorage(final T dataStorage) {
        return false;
    }

    @Override
    public ActionStatus postCreationProcessing(final T storage) {
        return ActionStatus.notSupported();
    }

    @Override
    public DataStorageItemType getItemType(final T dataStorage,
                                           final String path, final String version) {
        return DataStorageItemType.File;
    }

    @Override
    public String getDefaultMountOptions(final T dataStorage) {
        return EMPTY_MO;
    }

    @Override
    public void verifyStorageLifecyclePolicyRule(final StorageLifecycleRule rule) {
        throw new UnsupportedOperationException("Restore mechanism isn't supported for this provider.");
    }

    @Override
    public void verifyStorageLifecycleRuleExecution(final StorageLifecycleRuleExecution execution) {
        throw new UnsupportedOperationException("Restore mechanism isn't supported for this provider.");
    }

    @Override
    public void verifyRestoreActionSupported() {
        throw new UnsupportedOperationException("Restore mechanism isn't supported for this provider.");
    }

    @Override
    public String verifyOrDefaultRestoreMode(final StorageRestoreActionRequest restoreMode) {
        throw new UnsupportedOperationException("Restore mechanism isn't supported for this provider.");
    }

    @Override
    public void applyStoragePolicy(final T dataStorage) {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public void restoreFileVersion(final T dataStorage, final String path,
                                   final String version) throws DataStorageException {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageDownloadFileUrl generateDownloadURL(final T dataStorage, final String path, final String version,
                                                          final ContentDisposition contentDisposition) {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageDownloadFileUrl generateDataStorageItemUploadUrl(final T dataStorage, final String path) {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageDownloadFileUrl generateUrl(final T dataStorage, final String path,
                                                  final List<String> permissions, final Duration duration) {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageFolder createFolder(final T dataStorage, final String path) throws DataStorageException {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public void deleteFolder(final T dataStorage, String path, final Boolean totally) throws DataStorageException {
        deleteFile(dataStorage, path, null, totally);
    }

    @Override
    public DataStorageFile moveFile(final T dataStorage, final String oldPath,
                                    final String newPath) throws DataStorageException {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageFolder moveFolder(final T dataStorage, final String oldPath,
                                        final String newPath) throws DataStorageException {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageFile copyFile(final T dataStorage, final String oldPath, final String newPath) {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageFolder copyFolder(final T dataStorage, final String oldPath, final String newPath) {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public Map<String, String> updateObjectTags(final T dataStorage, final String path,
                                                final Map<String, String> tags, final String version) {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public Map<String, String> listObjectTags(final T dataStorage, final String path, final String version) {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public Map<String, String> deleteObjectTags(final T dataStorage, final String path,
                                                final Set<String> tagsToDelete, final String version) {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageStreamingContent getStream(final T dataStorage, final String path, final String version) {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageItemContent getFile(final T dataStorage, final String path, final String version,
                                          final Long maxDownloadSize) {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageFile createFile(final T dataStorage, final String path,
                                      final InputStream dataStream) throws DataStorageException {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    @Override
    public DataStorageFile createFile(final T dataStorage, final String path,
                                      final byte[] contents) throws DataStorageException {
        throw new UnsupportedOperationException("Mechanism isn't supported for this provider.");
    }

    protected OmicsHelper getOmicsHelper(AWSDataStorage dataStorage) {
        final AwsRegion region = getAwsRegion(dataStorage);
        if (dataStorage.isUseAssumedCredentials()) {
            final String roleArn = Optional.ofNullable(dataStorage.getTempCredentialsRole())
                    .orElse(region.getTempCredentialsRole());
            return new OmicsHelper(region, roleArn);
        }
        if (StringUtils.isNotBlank(region.getIamRole())) {
            return new OmicsHelper(region, region.getIamRole());
        }
        return new OmicsHelper(region, getAwsCredentials(region));
    }

    protected Long getSizeFromFileInformation(final FileInformation file) {
        return Optional.ofNullable(file).map(FileInformation::getContentLength).orElse(0L);
    }

    protected DataStorageFile mapOmicsFileToDataStorageFile(final FileInformation fileInformation,
                                                            final String path, final String source) {
        if (fileInformation != null) {
            final DataStorageFile file = new DataStorageFile();
            file.setPath(FilenameUtils.concat(path, source));
            file.setName(source);
            file.setSize(fileInformation.getContentLength());
            return file;
        }
        return null;
    }

    private AwsRegion getAwsRegion(final AWSDataStorage dataStorage) {
        return cloudRegionManager.getAwsRegion(dataStorage);
    }

    private AwsRegionCredentials getAwsCredentials(final AwsRegion region) {
        return cloudRegionManager.loadCredentials(region);
    }
}
