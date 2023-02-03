/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.test.creator.datastorage;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.controller.vo.GenerateDownloadUrlVO;
import com.epam.pipeline.controller.vo.data.storage.UpdateDataStorageItemVO;
import com.epam.pipeline.controller.vo.security.EntityWithPermissionVO;
import com.epam.pipeline.entity.SecuredEntityWithAction;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.DataStorageWithShareMount;
import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.datastorage.NFSStorageMountStatus;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.datastorage.StorageMountPath;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.datastorage.StorageUsage;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.azure.AzureBlobStorage;
import com.epam.pipeline.entity.datastorage.gcp.GSBucketStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.datastorage.rules.DataStorageRule;
import com.epam.pipeline.entity.datastorage.tag.DataStorageObject;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTag;
import com.epam.pipeline.entity.security.acl.EntityPermission;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.tuple.Pair;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_LOCAL_DATE_TIME;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING_LIST;

public final class DatastorageCreatorUtils {

    public static final TypeReference<Result<List<S3bucketDataStorage>>> S3BUCKET_LIST_TYPE =
            new TypeReference<Result<List<S3bucketDataStorage>>>() { };
    public static final TypeReference<Result<List<DataStorageWithShareMount>>> DS_WITH_SHARE_MOUNT_TYPE =
            new TypeReference<Result<List<DataStorageWithShareMount>>>() { };
    public static final TypeReference<Result<S3bucketDataStorage>> S3_BUCKET_TYPE =
            new TypeReference<Result<S3bucketDataStorage>>() { };
    public static final TypeReference<Result<AzureBlobStorage>> AZURE_BLOB_TYPE =
            new TypeReference<Result<AzureBlobStorage>>() { };
    public static final TypeReference<Result<GSBucketStorage>> GS_BUCKET_TYPE =
            new TypeReference<Result<GSBucketStorage>>() { };
    public static final TypeReference<Result<NFSDataStorage>> NFS_STORAGE_TYPE =
            new TypeReference<Result<NFSDataStorage>>() { };
    public static final TypeReference<Result<List<DataStorageFolder>>> DATA_STORAGE_FOLDER_LIST_TYPE =
            new TypeReference<Result<List<DataStorageFolder>>>() { };
    public static final TypeReference<Result<List<DataStorageFile>>> DATA_STORAGE_FILE_LIST_TYPE =
            new TypeReference<Result<List<DataStorageFile>>>() { };
    public static final TypeReference<Result<DataStorageListing>> DATA_STORAGE_LISTING_TYPE =
            new TypeReference<Result<DataStorageListing>>() { };
    public static final TypeReference<Result<DataStorageFile>> DATA_STORAGE_FILE_TYPE =
            new TypeReference<Result<DataStorageFile>>() { };
    public static final TypeReference<Result<DataStorageDownloadFileUrl>> DOWNLOAD_FILE_URL_TYPE =
            new TypeReference<Result<DataStorageDownloadFileUrl>>() { };
    public static final TypeReference<Result<DataStorageItemContent>> ITEM_CONTENT_URL =
            new TypeReference<Result<DataStorageItemContent>>() { };
    public static final TypeReference<Result<List<DataStorageDownloadFileUrl>>> DOWNLOAD_FILE_URL_LIST_TYPE =
            new TypeReference<Result<List<DataStorageDownloadFileUrl>>>() { };
    public static final TypeReference<Result<SecuredEntityWithAction<S3bucketDataStorage>>> SECURED_S3_BUCKET_TYPE =
            new TypeReference<Result<SecuredEntityWithAction<S3bucketDataStorage>>>() { };
    public static final TypeReference<Result<SecuredEntityWithAction<AzureBlobStorage>>> SECURED_AZURE_BLOB_TYPE =
            new TypeReference<Result<SecuredEntityWithAction<AzureBlobStorage>>>() { };
    public static final TypeReference<Result<SecuredEntityWithAction<GSBucketStorage>>> SECURED_GS_BUCKET_TYPE =
            new TypeReference<Result<SecuredEntityWithAction<GSBucketStorage>>>() { };
    public static final TypeReference<Result<SecuredEntityWithAction<NFSDataStorage>>> SECURED_NFS_STORAGE_TYPE =
            new TypeReference<Result<SecuredEntityWithAction<NFSDataStorage>>>() { };
    public static final TypeReference<Result<DataStorageRule>> DATA_STORAGE_RULE_URL =
            new TypeReference<Result<DataStorageRule>>() { };
    public static final TypeReference<Result<List<DataStorageRule>>> DATA_STORAGE_RULE_LIST_URL =
            new TypeReference<Result<List<DataStorageRule>>>() { };
    public static final TypeReference<Result<DataStorageTag>> DATA_STORAGE_TAG_TYPE =
            new TypeReference<Result<DataStorageTag>>() { };
    public static final TypeReference<Result<List<DataStorageTag>>> DATA_STORAGE_TAG_LIST_TYPE =
            new TypeReference<Result<List<DataStorageTag>>>() { };
    public static final TypeReference<Result<TemporaryCredentials>> TEMP_CREDENTIALS_TYPE =
            new TypeReference<Result<TemporaryCredentials>>() { };
    public static final TypeReference<Result<DataStorageFolder>> DATA_STORAGE_FOLDER_TYPE =
            new TypeReference<Result<DataStorageFolder>>() { };
    public static final TypeReference<Result<EntityWithPermissionVO>> ENTITY_WITH_PERMISSION_VO_TYPE =
            new TypeReference<Result<EntityWithPermissionVO>>() { };
    public static final TypeReference<Result<List<PathDescription>>> PATH_DESCRIPTION_LIST_TYPE =
            new TypeReference<Result<List<PathDescription>>>() { };
    public static final TypeReference<Result<StorageUsage>> STORAGE_USAGE_TYPE =
            new TypeReference<Result<StorageUsage>>() { };
    public static final TypeReference<Result<StorageMountPath>> STORAGE_MOUNT_PATH_TYPE =
            new TypeReference<Result<StorageMountPath>>() { };
    public static final TypeReference<Result<FileShareMount>> FILE_SHARE_MOUNT_TYPE =
            new TypeReference<Result<FileShareMount>>() { };
    private static final String TEST_PATH = "localhost:root/test";

    private DatastorageCreatorUtils() {

    }

    public static S3bucketDataStorage getS3bucketDataStorage() {
        return new S3bucketDataStorage(ID, TEST_STRING, TEST_STRING);
    }

    public static S3bucketDataStorage getS3bucketDataStorage(final Long id, final String owner) {
        final S3bucketDataStorage s3bucket = new S3bucketDataStorage(id, TEST_STRING, TEST_STRING);
        s3bucket.setOwner(owner);
        return s3bucket;
    }

    public static S3bucketDataStorage getS3bucketDataStorage(final Long id, final String owner, final boolean shared) {
        final S3bucketDataStorage s3bucket = new S3bucketDataStorage(id, TEST_STRING, TEST_STRING);
        s3bucket.setOwner(owner);
        s3bucket.setShared(shared);
        return s3bucket;
    }

    public static S3bucketDataStorage getS3bucketDataStorage(final Long id, final String path, final String owner) {
        final S3bucketDataStorage s3bucket = new S3bucketDataStorage(id, TEST_STRING, path);
        s3bucket.setOwner(owner);
        return s3bucket;
    }

    public static AzureBlobStorage getAzureBlobStorage() {
        return new AzureBlobStorage(ID, TEST_STRING, TEST_STRING, new StoragePolicy(), TEST_STRING);
    }

    public static GSBucketStorage getGsBucketStorage() {
        return new GSBucketStorage(ID, TEST_STRING, TEST_STRING, new StoragePolicy(), TEST_STRING);
    }

    public static NFSDataStorage getNfsDataStorage() {
        return new NFSDataStorage(ID, TEST_STRING, TEST_PATH);
    }

    public static NFSDataStorage getNfsDataStorage(final NFSStorageMountStatus status, final String owner) {
        final NFSDataStorage nfsDataStorage = getNfsDataStorage();
        nfsDataStorage.setMountStatus(status);
        nfsDataStorage.setOwner(owner);
        return nfsDataStorage;
    }

    public static NFSDataStorage getNfsDataStorage(final Long id, final String path, final String options,
                                                   final String point, final String owner) {
        final NFSDataStorage storage = new NFSDataStorage(id, TEST_STRING, path);
        storage.setMountOptions(options);
        storage.setMountPoint(point);
        storage.setOwner(owner);
        return storage;
    }

    public static DataStorageWithShareMount getDefaultDataStorageWithShareMount() {
        return new DataStorageWithShareMount(getS3bucketDataStorage(), new FileShareMount());
    }

    public static DataStorageWithShareMount getDataStorageWithShareMount() {
        return new DataStorageWithShareMount(null, new FileShareMount());
    }

    public static DataStorageFolder getDataStorageFolder() {
        return new DataStorageFolder();
    }

    public static DataStorageListing getDataStorageListing() {
        return new DataStorageListing();
    }

    public static DataStorageFile getDataStorageFile() {
        return new DataStorageFile();
    }

    public static UpdateDataStorageItemVO getUpdateDataStorageItemVO() {
        return new UpdateDataStorageItemVO();
    }

    public static DataStorageDownloadFileUrl getDataStorageDownloadFileUrl() {
        final DataStorageDownloadFileUrl url = new DataStorageDownloadFileUrl();
        url.setUrl(TEST_STRING);
        return url;
    }

    public static DataStorageItemContent getDataStorageItemContent() {
        final DataStorageItemContent dataStorageItemContent = new DataStorageItemContent();
        dataStorageItemContent.setContentType(TEST_STRING);
        dataStorageItemContent.setContent(TEST_STRING.getBytes());
        return dataStorageItemContent;
    }

    public static GenerateDownloadUrlVO getGenerateDownloadUrlVO() {
        final GenerateDownloadUrlVO generateDownloadUrlVO = new GenerateDownloadUrlVO();
        generateDownloadUrlVO.setHours(ID);
        generateDownloadUrlVO.setPermissions(TEST_STRING_LIST);
        generateDownloadUrlVO.setPaths(TEST_STRING_LIST);
        return generateDownloadUrlVO;
    }

    public static DataStorageVO getDataStorageVO() {
        final DataStorageVO dataStorageVO = new DataStorageVO();
        dataStorageVO.setId(ID);
        return dataStorageVO;
    }

    public static DataStorageRule getDataStorageRule() {
        final DataStorageRule dataStorageRule = new DataStorageRule();
        dataStorageRule.setPipelineId(ID);
        dataStorageRule.setFileMask(TEST_STRING);
        dataStorageRule.setMoveToSts(true);
        return dataStorageRule;
    }

    public static DataStorageTag getDataStorageTag() {
        return new DataStorageTag(getDataStorageObject(), TEST_STRING, TEST_STRING, TEST_LOCAL_DATE_TIME);
    }

    private static DataStorageObject getDataStorageObject() {
        return new DataStorageObject(TEST_STRING, TEST_STRING);
    }

    public static TemporaryCredentials getTemporaryCredentials() {
        return new TemporaryCredentials(TEST_STRING, TEST_STRING, TEST_STRING, TEST_STRING, TEST_STRING);
    }

    public static EntityWithPermissionVO getEntityWithPermissionVO() {
        final EntityWithPermissionVO entityWithPermissionVO = new EntityWithPermissionVO();
        entityWithPermissionVO.setEntityPermissions(Collections.singletonList(new EntityPermission()));
        entityWithPermissionVO.setTotalCount(TEST_INT);
        return entityWithPermissionVO;
    }

    public static PathDescription getPathDescription() {
        return new PathDescription(TEST_PATH, ID, ID, true);
    }

    public static StorageUsage getStorageUsage() {
        return new StorageUsage(ID, TEST_STRING, DataStorageType.S3, TEST_PATH,
                Collections.emptyMap(), new StorageUsage.StorageUsageStats(ID, ID, ID));
    }

    public static StorageMountPath getDefaultStorageMountPath() {
        return new StorageMountPath(TEST_PATH, getS3bucketDataStorage(), new FileShareMount());
    }

    public static StorageMountPath getStorageMountPath() {
        return new StorageMountPath(TEST_PATH, null, new FileShareMount());
    }

    public static FileShareMount getFileShareMount() {
        final FileShareMount fileShareMount = new FileShareMount();
        fileShareMount.setMountRoot(TEST_STRING);
        fileShareMount.setId(ID);
        fileShareMount.setMountOptions(TEST_STRING);
        fileShareMount.setRegionId(ID);
        return fileShareMount;
    }

    public static DataStorageItemContent getDefaultDataStorageItemContent() {
        return new DataStorageItemContent();
    }

    public static DataStorageStreamingContent getDataStorageStreamingContent() {
        final InputStream inputStream = new ByteArrayInputStream(TEST_STRING.getBytes());
        return new DataStorageStreamingContent(inputStream, TEST_STRING);
    }

    public static DataStorageStreamingContent getDefaultDataStorageStreamingContent(final InputStream inputStream) {
        return new DataStorageStreamingContent(inputStream, TEST_STRING);
    }

    public static List<Pair<AbstractDataStorage, TypeReference>> getRegularTypeStorages() {
        return Arrays.asList(
                Pair.of(getS3bucketDataStorage(), S3_BUCKET_TYPE),
                Pair.of(getAzureBlobStorage(), AZURE_BLOB_TYPE),
                Pair.of(getGsBucketStorage(), GS_BUCKET_TYPE),
                Pair.of(getNfsDataStorage(), NFS_STORAGE_TYPE));
    }

    public static List<Pair<AbstractDataStorage, TypeReference>> getSecuredTypeStorages() {
        return Arrays.asList(
                Pair.of(getS3bucketDataStorage(), SECURED_S3_BUCKET_TYPE),
                Pair.of(getAzureBlobStorage(), SECURED_AZURE_BLOB_TYPE),
                Pair.of(getGsBucketStorage(), SECURED_GS_BUCKET_TYPE),
                Pair.of(getNfsDataStorage(), SECURED_NFS_STORAGE_TYPE));
    }

    public static DataStorageAction getDataStorageAction() {
        final DataStorageAction dataStorageAction = new DataStorageAction();
        dataStorageAction.setId(ID);
        return dataStorageAction;
    }

    public static List<UpdateDataStorageItemVO> getUpdateDataStorageItemVOList() {
        return Collections.singletonList(getUpdateDataStorageItemVO());
    }

    public static List<DataStorageFile> getDataStorageFileList() {
        return Collections.singletonList(getDataStorageFile());
    }

    public static List<DataStorageDownloadFileUrl> getDataStorageDownloadFileUrlList() {
        return Collections.singletonList(getDataStorageDownloadFileUrl());
    }

    public static List<DataStorageRule> getDataStorageRuleList() {
        return Collections.singletonList(getDataStorageRule());
    }

    public static List<PathDescription> getPathDescriptionList() {
        return Collections.singletonList(getPathDescription());
    }

    public static List<DataStorageAction> getDataStorageActionList() {
        return Collections.singletonList(getDataStorageAction());
    }
}
