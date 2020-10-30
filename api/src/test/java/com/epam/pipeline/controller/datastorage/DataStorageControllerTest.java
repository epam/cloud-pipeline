/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.datastorage;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.controller.vo.UploadFileMetadata;
import com.epam.pipeline.controller.vo.security.EntityWithPermissionVO;
import com.epam.pipeline.entity.SecuredEntityWithAction;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.DataStorageWithShareMount;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.datastorage.StorageMountPath;
import com.epam.pipeline.entity.datastorage.StorageUsage;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.rules.DataStorageRule;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.OBJECT_TYPE;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.STRING_STRING_MAP_TYPE;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.STRING_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class DataStorageControllerTest extends AbstractDataStorageControllerTest {

    @Test
    public void shouldFaiGetDataStoragesForUnauthorizedUser() {
        performUnauthorizedRequest(get(LOAD_ALL_URL));
    }

    @Test
    @WithMockUser
    public void shouldGetDataStorages() {
        final List<S3bucketDataStorage> dataStorages = Collections.singletonList(s3Bucket);
        Mockito.doReturn(dataStorages).when(mockStorageApiService).getDataStorages();

        final MvcResult mvcResult = performRequest(get(LOAD_ALL_URL));

        Mockito.verify(mockStorageApiService).getDataStorages();
        assertResponse(mvcResult, dataStorages, DatastorageCreatorUtils.S3BUCKET_LIST_TYPE);
    }

    @Test
    public void shouldFailGetAvailableStoragesForUnauthorizedUser() {
        performUnauthorizedRequest(get(LOAD_AVAILABLE_URL));
    }

    @Test
    @WithMockUser
    public void shouldGetAvailableStorages() {
        final List<S3bucketDataStorage> dataStorages = Collections.singletonList(s3Bucket);
        Mockito.doReturn(dataStorages).when(mockStorageApiService).getAvailableStorages();

        final MvcResult mvcResult = performRequest(get(LOAD_AVAILABLE_URL));

        Mockito.verify(mockStorageApiService).getAvailableStorages();
        assertResponse(mvcResult, dataStorages, DatastorageCreatorUtils.S3BUCKET_LIST_TYPE);
    }

    @Test
    public void shouldFailGetAvailableStoragesWithMountObjectsForUnauthorizedUser() {
        performUnauthorizedRequest(get(LOAD_AVAILABLE_WITH_MOUNTS));
    }

    @Test
    @WithMockUser
    public void shouldGetAvailableStoragesWithMountObjects() {
        final List<DataStorageWithShareMount> storagesWithShareMounts =
                Collections.singletonList(DatastorageCreatorUtils.getDataStorageWithShareMount());
        Mockito.doReturn(storagesWithShareMounts).when(mockStorageApiService).getAvailableStoragesWithShareMount(ID);

        final MvcResult mvcResult = performRequest(get(LOAD_AVAILABLE_WITH_MOUNTS).param(FROM_REGION, ID_AS_STRING));

        Mockito.verify(mockStorageApiService).getAvailableStoragesWithShareMount(ID);
        assertResponse(mvcResult, storagesWithShareMounts, DatastorageCreatorUtils.DS_WITH_SHARE_MOUNT_TYPE);
    }

    @Test
    public void shouldFailGetWritableDataStoragesForUnauthorizedUser() {
        performUnauthorizedRequest(get(LOAD_WRITABLE_URL));
    }

    @Test
    @WithMockUser
    public void shouldGetWritableDataStorages() {
        final List<S3bucketDataStorage> dataStorages = Collections.singletonList(s3Bucket);
        Mockito.doReturn(dataStorages).when(mockStorageApiService).getWritableStorages();

        final MvcResult mvcResult = performRequest(get(LOAD_WRITABLE_URL));

        Mockito.verify(mockStorageApiService).getWritableStorages();
        assertResponse(mvcResult, dataStorages, DatastorageCreatorUtils.S3BUCKET_LIST_TYPE);
    }

    @Test
    public void shouldFailLoadDataStorageForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(LOAD_DATASTORAGE, ID)));
    }

    @Test
    public void shouldFailFindDataStorageForUnauthorizedUser() {
        performUnauthorizedRequest(get(FIND_URL));
    }

    @Test
    public void shouldFailFindDataStorageByPathForUnauthorizedUser() {
        performUnauthorizedRequest(get(FIND_BY_PATH_URL));
    }

    @Test
    public void shouldFailUploadFileForUnauthorizedUser() {
        performUnauthorizedRequest(post(String.format(DATASTORAGE_ITEMS_UPLOAD_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldUploadFile() throws Exception {
        final UploadFileMetadata uploadFileMetadata = new UploadFileMetadata();
        uploadFileMetadata.setFileName(FILE_NAME);
        uploadFileMetadata.setFileSize(FILE_SIZE);
        uploadFileMetadata.setFileType(OCTET_STREAM_CONTENT_TYPE);
        final List<UploadFileMetadata> uploadFileMetadataList = Collections.singletonList(uploadFileMetadata);

        final MvcResult mvcResult = performRequest(
                post(String.format(DATASTORAGE_ITEMS_UPLOAD_URL, ID)).content(MULTIPART_CONTENT).param(PATH, TEST),
                MULTIPART_CONTENT_TYPE, EXPECTED_CONTENT_TYPE
        );

        Mockito.verify(mockStorageApiService).createDataStorageFile(ID, TEST, FILE_NAME, TEST.getBytes());
        final List<UploadFileMetadata> actualResult = JsonMapper
                .parseData(mvcResult.getResponse().getContentAsString(),
                        new TypeReference<List<UploadFileMetadata>>() { });
        Assert.assertEquals(uploadFileMetadataList, actualResult);
    }

    @Test
    public void shouldFailUploadStreamForUnauthorizedUser() {
        performUnauthorizedRequest(post(String.format(DATASTORAGE_UPLOAD_STREAM_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldUploadStream() {
        final List<DataStorageFile> dataStorageFiles = Collections.singletonList(file);
        Mockito.doReturn(file).when(mockStorageApiService)
                .createDataStorageFile(Mockito.eq(ID), Mockito.eq(TEST),
                        Mockito.eq(FILE_NAME), (InputStream) Mockito.any());

        final MvcResult mvcResult = performRequest(post(String.format(DATASTORAGE_UPLOAD_STREAM_URL, ID))
                .content(MULTIPART_CONTENT).param(PATH, TEST), MULTIPART_CONTENT_TYPE, EXPECTED_CONTENT_TYPE);

        Mockito.verify(mockStorageApiService)
                .createDataStorageFile(Mockito.eq(ID), Mockito.eq(TEST),
                        Mockito.eq(FILE_NAME), (InputStream) Mockito.any());
        assertResponse(mvcResult, dataStorageFiles, new TypeReference<Result<List<DataStorageFile>>>() { });
    }

    @Test
    public void shouldFailDownloadStreamForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(DATASTORAGE_DOWNLOAD_STREAM_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDownloadStream() throws Exception {
        final DataStorageStreamingContent dataStorageStreamingContent =
                DatastorageCreatorUtils.getDataStorageStreamingContent();
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PATH, TEST);
        params.add(VERSION, TEST);
        Mockito.doReturn(dataStorageStreamingContent).when(mockStorageApiService).getStreamingContent(ID, TEST, TEST);

        final MvcResult mvcResult = performRequest(get(String.format(DATASTORAGE_DOWNLOAD_STREAM_URL, ID))
                .params(params), OCTET_STREAM_CONTENT_TYPE);

        Mockito.verify(mockStorageApiService).getStreamingContent(ID, TEST, TEST);

        final String actualData = mvcResult.getResponse().getContentAsString();
        final String contentDispositionHeader = mvcResult.getResponse().getHeader(CONTENT_DISPOSITION_HEADER);
        Assert.assertEquals(TEST, actualData);
        assertThat(contentDispositionHeader).contains(TEST);
    }

    @Test
    @WithMockUser
    public void shouldRestoreFileVersion() {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PATH, TEST);
        params.add(VERSION, TEST);

        final MvcResult mvcResult = performRequest(post(String.format(RESTORE_VERSION_URL, ID)).params(params));

        Mockito.verify(mockStorageApiService).restoreFileVersion(ID, TEST, TEST);
        assertResponse(mvcResult, null, OBJECT_TYPE);
    }

    @Test
    public void shouldFailRegisterDataStorage() {
        performUnauthorizedRequest(post(DATASTORAGE_SAVE_URL));
    }

    @Test
    public void shouldFailUpdateDataStorage() {
        performUnauthorizedRequest(post(DATASTORAGE_UPDATE_URL));
    }

    @Test
    public void shouldFailUpdateStoragePolicyForUnauthorizedUser() {
        performUnauthorizedRequest(post(DATASTORAGE_POLICY_URL));
    }

    @Test
    public void shouldDeleteDataStorageForUnauthorizedUser() {
        performUnauthorizedRequest(delete(DATASTORAGE_DELETE_URL, ID));
    }

    @Test
    public void shouldFailSaveDataStorageRuleForUnauthorizedUser() {
        performUnauthorizedRequest(post(SAVE_RULE_URL));
    }

    @Test
    @WithMockUser
    public void shouldSaveDataStorageRule() throws Exception {
        final String content = getObjectMapper().writeValueAsString(dataStorageRule);
        Mockito.doReturn(dataStorageRule).when(mockStorageApiService).createRule(dataStorageRule);

        final MvcResult mvcResult = performRequest(post(SAVE_RULE_URL).content(content));

        Mockito.verify(mockStorageApiService).createRule(dataStorageRule);
        assertResponse(mvcResult, dataStorageRule, DatastorageCreatorUtils.DATA_STORAGE_RULE_URL);
    }

    @Test
    public void shouldFailLoadDataStorageRuleForUnauthorizedUser() {
        performUnauthorizedRequest(get(LOAD_RULES_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadDataStorageRule() {
        final List<DataStorageRule> dataStorageRules = Collections.singletonList(dataStorageRule);
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PIPELINE_ID, ID_AS_STRING);
        params.add(FILE_MASK, TEST);
        Mockito.doReturn(dataStorageRules).when(mockStorageApiService).loadRules(ID, TEST);

        final MvcResult mvcResult = performRequest(get(LOAD_RULES_URL).params(params));

        Mockito.verify(mockStorageApiService).loadRules(ID, TEST);
        assertResponse(mvcResult, dataStorageRules, DatastorageCreatorUtils.DATA_STORAGE_RULE_LIST_URL);
    }

    @Test
    public void shouldFailDeleteDataStorageRuleForUnauthorizedUser() {
        performUnauthorizedRequest(delete(DELETE_RULES_URL));
    }

    @Test
    @WithMockUser
    public void shouldDeleteDataStorageRule() {
        Mockito.doReturn(dataStorageRule).when(mockStorageApiService).deleteRule(ID, TEST);
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(ID_PARAM, ID_AS_STRING);
        params.add(FILE_MASK, TEST);

        final MvcResult mvcResult = performRequest(delete(DELETE_RULES_URL).params(params));

        Mockito.verify(mockStorageApiService).deleteRule(ID, TEST);
        assertResponse(mvcResult, dataStorageRule, DatastorageCreatorUtils.DATA_STORAGE_RULE_URL);
    }

    @Test
    public void shouldFailGenerateTemporaryCredentialsForUnauthorizedUser() {
        performUnauthorizedRequest(post(TEMP_CREDENTIALS_URL));
    }

    @Test
    @WithMockUser
    public void shouldGenerateTemporaryCredentials() throws Exception {
        final TemporaryCredentials temporaryCredentials = DatastorageCreatorUtils.getTemporaryCredentials();
        final List<DataStorageAction> dataStorageActions = Collections.singletonList(new DataStorageAction());
        final String content = getObjectMapper().writeValueAsString(dataStorageActions);
        Mockito.doReturn(temporaryCredentials).when(mockStorageApiService).generateCredentials(dataStorageActions);

        final MvcResult mvcResult = performRequest(post(TEMP_CREDENTIALS_URL).content(content));

        Mockito.verify(mockStorageApiService).validateOperation(dataStorageActions);
        Mockito.verify(mockStorageApiService).generateCredentials(dataStorageActions);
        assertResponse(mvcResult, temporaryCredentials, DatastorageCreatorUtils.TEMP_CREDENTIALS_TYPE);
    }

    @Test
    public void shouldFailUpdateTagsForUnauthorizedUser() {
        performUnauthorizedRequest(post(String.format(TAGS_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldUpdateTags() throws Exception {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PATH, TEST);
        params.add(VERSION, TEST);
        params.add(REWRITE, TRUE_AS_STRING);
        final String content = getObjectMapper().writeValueAsString(TAGS);
        Mockito.doReturn(TAGS).when(mockStorageApiService).updateDataStorageObjectTags(ID, TEST, TAGS, TEST, true);

        final MvcResult mvcResult = performRequest(post(String.format(TAGS_URL, ID)).params(params).content(content));

        Mockito.verify(mockStorageApiService).updateDataStorageObjectTags(ID, TEST, TAGS, TEST, true);
        assertResponse(mvcResult, TAGS, STRING_STRING_MAP_TYPE);
    }

    @Test
    public void shouldFailLoadTagsByIdForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(TAGS_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadTagsByIdOwner() {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PATH, TEST);
        params.add(VERSION, TEST);
        Mockito.doReturn(TAGS).when(mockStorageApiService).loadDataStorageObjectTagsOwner(ID, TEST, TEST);

        final MvcResult mvcResult = performRequest(get(String.format(TAGS_URL, ID)).params(params));

        Mockito.verify(mockStorageApiService).loadDataStorageObjectTagsOwner(ID, TEST, TEST);
        assertResponse(mvcResult, TAGS, STRING_STRING_MAP_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldLoadTagsById() {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PATH, TEST);
        params.add(VERSION, EMPTY_STRING);
        Mockito.doReturn(TAGS).when(mockStorageApiService).loadDataStorageObjectTags(ID, TEST, EMPTY_STRING);

        final MvcResult mvcResult = performRequest(get(String.format(TAGS_URL, ID)).params(params));

        Mockito.verify(mockStorageApiService).loadDataStorageObjectTags(ID, TEST, EMPTY_STRING);
        assertResponse(mvcResult, TAGS, STRING_STRING_MAP_TYPE);
    }

    @Test
    public void shouldFailDeleteTagsByIdForUnauthorizedUser() {
        performUnauthorizedRequest(delete(String.format(TAGS_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDeleteTagsById() throws Exception {
        final Set<String> setTags = Collections.singleton(TEST);
        final String content = getObjectMapper().writeValueAsString(setTags);
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PATH, TEST);
        params.add(VERSION, TEST);
        Mockito.doReturn(TAGS).when(mockStorageApiService).deleteDataStorageObjectTags(ID, TEST, setTags, TEST);

        final MvcResult mvcResult = performRequest(delete(String.format(TAGS_URL, ID)).params(params).content(content));

        Mockito.verify(mockStorageApiService).deleteDataStorageObjectTags(ID, TEST, setTags, TEST);
        assertResponse(mvcResult, TAGS, STRING_STRING_MAP_TYPE);
    }

    @Test
    public void shouldFailGetDataStorageSharedLinkForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(SHARED_LINK_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGetDataStorageSharedLink() {
        Mockito.doReturn(TEST).when(mockStorageApiService).getDataStorageSharedLink(ID);

        final MvcResult mvcResult = performRequest(get(String.format(SHARED_LINK_URL, ID)));

        Mockito.verify(mockStorageApiService).getDataStorageSharedLink(ID);
        assertResponse(mvcResult, TEST, STRING_TYPE);
    }

    @Test
    public void shouldFailGetDataStoragePermissionsForUnauthorizedUser() {
        performUnauthorizedRequest(get(PERMISSION_URL));
    }

    @Test
    @WithMockUser
    public void shouldGetStoragePermissions() {
        final EntityWithPermissionVO entityWithPermissionVO = DatastorageCreatorUtils.getEntityWithPermissionVO();
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PAGE, ID_AS_STRING);
        params.add(PAGE_SIZE, ID_AS_STRING);
        params.add(FILTER_MASK, ID_AS_STRING);
        Mockito.doReturn(entityWithPermissionVO)
                .when(mockStorageApiService).getStoragePermission(TEST_INT, TEST_INT, TEST_INT);

        final MvcResult mvcResult = performRequest(get(PERMISSION_URL).params(params));

        Mockito.verify(mockStorageApiService).getStoragePermission(TEST_INT, TEST_INT, TEST_INT);
        assertResponse(mvcResult, entityWithPermissionVO, DatastorageCreatorUtils.ENTITY_WITH_PERMISSION_VO_TYPE);
    }

    @Test
    public void shouldFailGetDataSizesForUnauthorizedUser() {
        performUnauthorizedRequest(post(PATH_SIZE_URL));
    }

    @Test
    @WithMockUser
    public void shouldGetDataSizes() throws Exception {
        final List<PathDescription> pathDescriptions =
                Collections.singletonList(DatastorageCreatorUtils.getPathDescription());
        final List<String> paths = Collections.singletonList(TEST);
        final String content = getObjectMapper().writeValueAsString(paths);
        Mockito.doReturn(pathDescriptions).when(mockStorageApiService).getDataSizes(paths);

        final MvcResult mvcResult = performRequest(post(PATH_SIZE_URL).content(content));

        Mockito.verify(mockStorageApiService).getDataSizes(paths);
        assertResponse(mvcResult, pathDescriptions, DatastorageCreatorUtils.PATH_DESCRIPTION_LIST_TYPE);
    }

    @Test
    public void shouldFailGetDataStorageUsageForUnauthorizedUser() {
        performUnauthorizedRequest(get(PATH_USAGE_URL));
    }

    @Test
    @WithMockUser
    public void shouldGetStorageUsage() {
        final StorageUsage storageUsage = DatastorageCreatorUtils.getStorageUsage();
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(ID_PARAM, TEST);
        params.add(PATH, TEST);
        Mockito.doReturn(storageUsage).when(mockStorageApiService).getStorageUsage(TEST, TEST);

        final MvcResult mvcResult = performRequest(get(PATH_USAGE_URL).params(params));

        Mockito.verify(mockStorageApiService).getStorageUsage(TEST, TEST);
        assertResponse(mvcResult, storageUsage, DatastorageCreatorUtils.STORAGE_USAGE_TYPE);
    }

    @Test
    public void shouldFailCreateSharedFSSPathForRunForUnauthorizedUser() {
        performUnauthorizedRequest(post(SHARED_STORAGE_URL));
    }

    @Test
    @WithMockUser
    public void shouldCreateSharedFSSPathForRun() {
        final StorageMountPath storageMountPath = DatastorageCreatorUtils.getStorageMountPath();
        Mockito.doReturn(storageMountPath).when(mockStorageApiService).getSharedFSSPathForRun(ID, true);

        final MvcResult mvcResult = performRequest(post(SHARED_STORAGE_URL).param(RUN_ID, ID_AS_STRING));

        Mockito.verify(mockStorageApiService).getSharedFSSPathForRun(ID, true);
        assertResponse(mvcResult, storageMountPath, DatastorageCreatorUtils.STORAGE_MOUNT_PATH_TYPE);
    }

    @Test
    public void shouldFailGetSharedFSSPathForRunForUnauthorizedUser() {
        performUnauthorizedRequest(get(SHARED_STORAGE_URL));
    }

    @Test
    @WithMockUser
    public void shouldGetSharedFSSPathForRun() {
        final StorageMountPath storageMountPath = DatastorageCreatorUtils.getStorageMountPath();
        Mockito.doReturn(storageMountPath).when(mockStorageApiService).getSharedFSSPathForRun(ID, false);

        final MvcResult mvcResult = performRequest(get(SHARED_STORAGE_URL).param(RUN_ID, ID_AS_STRING));

        Mockito.verify(mockStorageApiService).getSharedFSSPathForRun(ID, false);
        assertResponse(mvcResult, storageMountPath, DatastorageCreatorUtils.STORAGE_MOUNT_PATH_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldFindAnyDataStorage() {
        storageTypeReferenceList.forEach(dataStoragePair -> {
            Mockito.doReturn(dataStoragePair.getLeft()).when(mockStorageApiService).loadByNameOrId(TEST);
            final MvcResult mvcResult = performRequest(get(FIND_URL).param(ID_PARAM, TEST));

            Assert.assertNotNull(mvcResult);
            assertResponse(mvcResult, dataStoragePair.getLeft(), dataStoragePair.getRight());
        });
        Mockito.verify(mockStorageApiService, Mockito.times(storageTypeReferenceList.size())).loadByNameOrId(TEST);
    }

    @Test
    @WithMockUser
    public void shouldRegisterAnyDataStorage() throws Exception {
        final DataStorageVO dataStorageVO = DatastorageCreatorUtils.getDataStorageVO();
        final String content = getObjectMapper().writeValueAsString(dataStorageVO);
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(CLOUD, TRUE_AS_STRING);
        params.add(SKIP_POLICY, TRUE_AS_STRING);
        securedStorageTypeReferenceList.forEach(dataStoragePair -> {
            final SecuredEntityWithAction<AbstractDataStorage> securedDataStorage = new SecuredEntityWithAction<>();
            securedDataStorage.setEntity(dataStoragePair.getLeft());
            Mockito.doReturn(securedDataStorage).when(mockStorageApiService)
                    .create(Mockito.refEq(dataStorageVO), Mockito.eq(true), Mockito.eq(true));

            final MvcResult mvcResult = performRequest(post(DATASTORAGE_SAVE_URL).params(params).content(content));

            assertResponse(mvcResult, securedDataStorage, dataStoragePair.getRight());
        });

        Mockito.verify(mockStorageApiService, Mockito.times(securedStorageTypeReferenceList.size()))
                .create(Mockito.refEq(dataStorageVO), Mockito.eq(true), Mockito.eq(true));
    }

    @Test
    @WithMockUser
    public void shouldUpdateAnyDataStorage() throws Exception {
        final DataStorageVO dataStorageVO = DatastorageCreatorUtils.getDataStorageVO();
        final String content = getObjectMapper().writeValueAsString(dataStorageVO);

        storageTypeReferenceList.forEach(dataStoragePair -> {
            Mockito.doReturn(dataStoragePair.getLeft()).when(mockStorageApiService)
                    .update(Mockito.refEq(dataStorageVO));
            final MvcResult mvcResult = performRequest(post(DATASTORAGE_UPDATE_URL).content(content));

            assertResponse(mvcResult, dataStoragePair.getLeft(), dataStoragePair.getRight());
        });

        Mockito.verify(mockStorageApiService, Mockito.times(storageTypeReferenceList.size()))
                .update(Mockito.refEq(dataStorageVO));
    }

    @Test
    @WithMockUser
    public void shouldDeleteAnyDataStorage() {
        storageTypeReferenceList.forEach(dataStoragePair -> {
            Mockito.doReturn(dataStoragePair.getLeft()).when(mockStorageApiService).delete(ID, true);
            final MvcResult mvcResult = performRequest(delete(String.format(DATASTORAGE_DELETE_URL, ID))
                    .param(CLOUD, TRUE_AS_STRING));

            assertResponse(mvcResult, dataStoragePair.getLeft(), dataStoragePair.getRight());
        });
        Mockito.verify(mockStorageApiService, Mockito.times(storageTypeReferenceList.size())).delete(ID, true);
    }

    @Test
    @WithMockUser
    public void shouldLoadAnyDataStorage() {
        storageTypeReferenceList.forEach(dataStoragePair -> {
            Mockito.doReturn(dataStoragePair.getLeft()).when(mockStorageApiService).load(ID);
            final MvcResult mvcResult = performRequest(get(String.format(LOAD_DATASTORAGE, ID)));

            assertResponse(mvcResult, dataStoragePair.getLeft(), dataStoragePair.getRight());
        });

        Mockito.verify(mockStorageApiService, Mockito.times(storageTypeReferenceList.size())).load(ID);
    }

    @Test
    @WithMockUser
    public void shouldFindAnyStorageByPath() {
        storageTypeReferenceList.forEach(dataStoragePair -> {
            Mockito.doReturn(dataStoragePair.getLeft()).when(mockStorageApiService).loadByPathOrId(TEST);
            final MvcResult mvcResult = performRequest(get(FIND_BY_PATH_URL).param(ID_PARAM, TEST));

            assertResponse(mvcResult, dataStoragePair.getLeft(), dataStoragePair.getRight());
        });

        Mockito.verify(mockStorageApiService, Mockito.times(storageTypeReferenceList.size())).loadByPathOrId(TEST);
    }

    @Test
    @WithMockUser
    public void shouldUpdateAnyDataStoragePolicy() throws Exception {
        final DataStorageVO dataStorageVO = DatastorageCreatorUtils.getDataStorageVO();
        final String content = getObjectMapper().writeValueAsString(dataStorageVO);
        storageTypeReferenceList.forEach(dataStoragePair -> {
            Mockito.doReturn(dataStoragePair.getLeft()).when(mockStorageApiService)
                    .updatePolicy(Mockito.refEq(dataStorageVO));
            final MvcResult mvcResult = performRequest(post(DATASTORAGE_POLICY_URL).content(content));

            assertResponse(mvcResult, dataStoragePair.getLeft(), dataStoragePair.getRight());
        });

        Mockito.verify(mockStorageApiService, Mockito.times(storageTypeReferenceList.size()))
                .updatePolicy(Mockito.refEq(dataStorageVO));
    }
}
