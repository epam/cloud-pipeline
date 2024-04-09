package com.epam.pipeline.manager.datastorage.providers.aws.omics;

import com.amazonaws.services.omics.model.FileInformation;
import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRule;
import com.epam.pipeline.dto.datastorage.lifecycle.execution.StorageLifecycleRuleExecution;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreActionRequest;
import com.epam.pipeline.entity.datastorage.*;
import com.epam.pipeline.entity.datastorage.aws.AbstractAWSDataStorage;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.AwsRegionCredentials;
import com.epam.pipeline.manager.datastorage.lifecycle.DataStorageLifecycleRestoredListingContainer;
import com.epam.pipeline.manager.datastorage.providers.StorageProvider;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@AllArgsConstructor
public abstract class AbstractOmicsStorageProvider<T extends AbstractDataStorage> implements StorageProvider<T> {

    public static final Pattern AWS_OMICS_STORE_FILE_ID_PATTERN = Pattern.compile("^(((\\d+)/(source|source1|source2|index))|(\\d+))$");

    public static final String AWS_OMICS_STORE_PATH_TEMPLATE = "%s.storage.%s.amazonaws.com/%s";
    public static final String REFERENCE_STORE_ID = "referenceStoreId";
    public static final String SEQUENCE_STORE_ID = "sequenceStoreId";

    public static final String FILE_TYPE = "fileType";
    public static final String REFERENCE_FILE_TYPE = "REFERENCE";

    public static final String ACCOUNT = "account";
    public static final String REGION = "region";

    public static final String SOURCE = "source";
    public static final String SOURCE_1 = "source1";
    public static final String SOURCE_2 = "source2";
    public static final String INDEX = "index";

    protected final MessageHelper messageHelper;
    protected final CloudRegionManager cloudRegionManager;
    protected final AuthManager authManager;

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
        return StringUtils.EMPTY;
    }

    @Override
    public DataStorageListing getItems(final T dataStorage, final String path,
                                       final Boolean showVersion, final Integer pageSize, final String marker) {
        if (BooleanUtils.isTrue(showVersion)) {
            log.warn(messageHelper.getMessage(MessageConstants.AWS_OMICS_STORE_DOESNT_SUPPORT_VERSIONING));
        }
        if (StringUtils.isNotBlank(path)) {
            if (!AWS_OMICS_STORE_FILE_ID_PATTERN.matcher(path).find()) {
                log.warn(messageHelper.getMessage(MessageConstants.AWS_OMICS_STORE_INCORRECT_FILE_PATH));
            }
            return listOmicsFileSources(dataStorage, path);
        } else {
            return listOmicsFiles(dataStorage, pageSize, marker);
        }
    }

    @Override
    public DataStorageListing getItems(final T dataStorage, final String path,
                                       final Boolean showVersion, final Integer pageSize, final String marker,
                                       final DataStorageLifecycleRestoredListingContainer restoredListing) {
        return getItems(dataStorage, path, showVersion, pageSize, marker);
    }

    @Override
    public void deleteFolder(final T dataStorage, String path, final Boolean totally) throws DataStorageException {
        deleteFile(dataStorage, path, null, totally);
    }

    abstract DataStorageListing listOmicsFileSources(T dataStorage, String path);

    abstract DataStorageListing listOmicsFiles(T dataStorage, Integer pageSize, String marker);

    @Override
    public void verifyStorageLifecyclePolicyRule(final StorageLifecycleRule rule) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_MECHANISM_ISNT_SUPPORTED_FOR_THIS_PROVIDER));
    }

    @Override
    public void verifyStorageLifecycleRuleExecution(final StorageLifecycleRuleExecution execution) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_MECHANISM_ISNT_SUPPORTED_FOR_THIS_PROVIDER));
    }

    @Override
    public void verifyRestoreActionSupported() {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_MECHANISM_ISNT_SUPPORTED_FOR_THIS_PROVIDER));
    }

    @Override
    public String verifyOrDefaultRestoreMode(final StorageRestoreActionRequest restoreMode) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_MECHANISM_ISNT_SUPPORTED_FOR_THIS_PROVIDER));
    }

    @Override
    public void applyStoragePolicy(final T dataStorage) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_MECHANISM_ISNT_SUPPORTED_FOR_THIS_PROVIDER));
    }

    @Override
    public void restoreFileVersion(final T dataStorage, final String path,
                                   final String version) throws DataStorageException {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_MECHANISM_ISNT_SUPPORTED_FOR_THIS_PROVIDER));
    }

    @Override
    public DataStorageDownloadFileUrl generateDownloadURL(final T dataStorage, final String path, final String version,
                                                          final ContentDisposition contentDisposition) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_MECHANISM_ISNT_SUPPORTED_FOR_THIS_PROVIDER));
    }

    @Override
    public DataStorageDownloadFileUrl generateDataStorageItemUploadUrl(final T dataStorage, final String path) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_MECHANISM_ISNT_SUPPORTED_FOR_THIS_PROVIDER));
    }

    @Override
    public DataStorageDownloadFileUrl generateUrl(final T dataStorage, final String path,
                                                  final List<String> permissions, final Duration duration) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_MECHANISM_ISNT_SUPPORTED_FOR_THIS_PROVIDER));
    }

    @Override
    public DataStorageFolder createFolder(final T dataStorage, final String path) throws DataStorageException {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_MECHANISM_ISNT_SUPPORTED_FOR_THIS_PROVIDER));
    }

    @Override
    public DataStorageFile moveFile(final T dataStorage, final String oldPath,
                                    final String newPath) throws DataStorageException {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_MECHANISM_ISNT_SUPPORTED_FOR_THIS_PROVIDER));
    }

    @Override
    public DataStorageFolder moveFolder(final T dataStorage, final String oldPath,
                                        final String newPath) throws DataStorageException {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_MECHANISM_ISNT_SUPPORTED_FOR_THIS_PROVIDER));
    }

    @Override
    public DataStorageFile copyFile(final T dataStorage, final String oldPath, final String newPath) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_MECHANISM_ISNT_SUPPORTED_FOR_THIS_PROVIDER));
    }

    @Override
    public DataStorageFolder copyFolder(final T dataStorage, final String oldPath, final String newPath) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_MECHANISM_ISNT_SUPPORTED_FOR_THIS_PROVIDER));
    }

    @Override
    public Map<String, String> updateObjectTags(final T dataStorage, final String path,
                                                final Map<String, String> tags, final String version) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_MECHANISM_ISNT_SUPPORTED_FOR_THIS_PROVIDER));
    }

    @Override
    public Map<String, String> listObjectTags(final T dataStorage, final String path, final String version) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_MECHANISM_ISNT_SUPPORTED_FOR_THIS_PROVIDER));
    }

    @Override
    public Map<String, String> deleteObjectTags(final T dataStorage, final String path,
                                                final Set<String> tagsToDelete, final String version) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_MECHANISM_ISNT_SUPPORTED_FOR_THIS_PROVIDER));
    }

    @Override
    public DataStorageStreamingContent getStream(final T dataStorage, final String path, final String version) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_MECHANISM_ISNT_SUPPORTED_FOR_THIS_PROVIDER));
    }

    @Override
    public DataStorageItemContent getFile(final T dataStorage, final String path, final String version,
                                          final Long maxDownloadSize) {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_MECHANISM_ISNT_SUPPORTED_FOR_THIS_PROVIDER));
    }

    @Override
    public DataStorageFile createFile(final T dataStorage, final String path,
                                      final InputStream dataStream) throws DataStorageException {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_MECHANISM_ISNT_SUPPORTED_FOR_THIS_PROVIDER));
    }

    @Override
    public DataStorageFile createFile(final T dataStorage, final String path,
                                      final byte[] contents) throws DataStorageException {
        throw new UnsupportedOperationException(
                messageHelper.getMessage(MessageConstants.ERROR_MECHANISM_ISNT_SUPPORTED_FOR_THIS_PROVIDER));
    }

    protected OmicsHelper getOmicsHelper(AbstractAWSDataStorage dataStorage) {
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

    private AwsRegion getAwsRegion(final AbstractAWSDataStorage dataStorage) {
        return cloudRegionManager.getAwsRegion(dataStorage);
    }

    private AwsRegionCredentials getAwsCredentials(final AwsRegion region) {
        return cloudRegionManager.loadCredentials(region);
    }
}
