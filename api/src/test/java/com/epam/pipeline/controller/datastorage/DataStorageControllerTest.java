/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.epam.pipeline.controller.datastorage;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.controller.vo.GenerateDownloadUrlVO;
import com.epam.pipeline.controller.vo.UploadFileMetadata;
import com.epam.pipeline.controller.vo.data.storage.UpdateDataStorageItemVO;
import com.epam.pipeline.controller.vo.security.EntityWithPermissionVO;
import com.epam.pipeline.entity.SecuredEntityWithAction;
import com.epam.pipeline.entity.datastorage.ContentDisposition;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.DataStorageWithShareMount;
import com.epam.pipeline.entity.datastorage.PathDescription;
import com.epam.pipeline.entity.datastorage.StorageMountPath;
import com.epam.pipeline.entity.datastorage.StorageUsage;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.azure.AzureBlobStorage;
import com.epam.pipeline.entity.datastorage.gcp.GSBucketStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.datastorage.rules.DataStorageRule;
import com.epam.pipeline.manager.datastorage.DataStorageApiService;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.fileUpload;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DataStorageController.class)
public class DataStorageControllerTest extends AbstractControllerTest {

    private static final long ID = 1L;
    private static final int TEST_INT = 1;
    private static final String TEST = "TEST";
    private static final String DATASTORAGE_URL = SERVLET_PATH + "/datastorage";
    private static final String LOAD_ALL_URL = DATASTORAGE_URL + "/loadAll";
    private static final String LOAD_AVAILABLE_URL = DATASTORAGE_URL + "/available";
    private static final String LOAD_AVAILABLE_WITH_MOUNTS = DATASTORAGE_URL + "/availableWithMounts";
    private static final String LOAD_WRITABLE_URL = DATASTORAGE_URL + "/mount";
    private static final String BY_ID_URL = DATASTORAGE_URL + "/%d";
    private static final String LOAD_DATASTORAGE = BY_ID_URL + "/load";
    private static final String FIND_URL = DATASTORAGE_URL + "/find";
    private static final String FIND_BY_PATH_URL = DATASTORAGE_URL + "/findByPath";
    private static final String DATASTORAGE_ITEMS_URL = BY_ID_URL + "/list";
    private static final String DATASTORAGE_LISTING_URL = DATASTORAGE_ITEMS_URL + "/page";
    private static final String DATASTORAGE_ITEMS_UPLOAD_URL = DATASTORAGE_ITEMS_URL + "/upload";
    private static final String DATASTORAGE_UPLOAD_STREAM_URL = BY_ID_URL + "/upload/stream";
    private static final String DATASTORAGE_DOWNLOAD_STREAM_URL = BY_ID_URL + "/download";
    private static final String DATASTORAGE_ITEMS_CONTENT = BY_ID_URL + "/content";
    private static final String DOWNLOAD_REDIRECT_URL = BY_ID_URL + "/downloadRedirect";
    private static final String GENERATE_URL = BY_ID_URL + "/generateUrl";
    private static final String GENERATE_UPLOAD_URL = BY_ID_URL + "/generateUploadUrl";
    private static final String CONTENT_URL = BY_ID_URL + "/content";
    private static final String RESTORE_VERSION_URL = DATASTORAGE_ITEMS_URL + "/restore";
    private static final String DATASTORAGE_SAVE_URL= DATASTORAGE_URL + "/save";
    private static final String DATASTORAGE_UPDATE_URL = DATASTORAGE_URL + "/update";
    private static final String DATASTORAGE_POLICY_URL = DATASTORAGE_URL + "/policy";
    private static final String DATASTORAGE_DELETE_URL = BY_ID_URL + "/delete";
    private static final String DATASTORAGE_RULE_URL = DATASTORAGE_URL + "/rule";
    private static final String SAVE_RULE_URL = DATASTORAGE_RULE_URL + "/register";
    private static final String LOAD_RULES_URL = DATASTORAGE_RULE_URL + "/load";
    private static final String DELETE_RULES_URL = DATASTORAGE_RULE_URL + "/delete";
    private static final String TEMP_CREDENTIALS_URL = DATASTORAGE_URL + "/tempCredentials/";
    private static final String TAGS_URL = BY_ID_URL + "/tags";
    private static final String TAGS_LIST_URL = TAGS_URL + "/list";
    private static final String SHARED_LINK_URL = BY_ID_URL + "/sharedLink";
    private static final String PERMISSION_URL = DATASTORAGE_URL + "/permission";
    private static final String PATH_URL = DATASTORAGE_URL + "/path";
    private static final String PATH_SIZE_URL = PATH_URL + "/size";
    private static final String PATH_USAGE_URL = PATH_URL + "/usage";
    private static final String SHARED_STORAGE_URL = DATASTORAGE_URL + "/sharedStorage";
    private static final String OCTET_STREAM_CONTENT_TYPE = "application/octet-stream";
    private static final String ID_AS_STRING = String.valueOf(ID);
    private static final String TRUE_AS_STRING = String.valueOf(true);
    private static final String FALSE_AS_STRING = String.valueOf(false);
    private static final String FROM_REGION = "fromRegion";
    private static final String ID_PARAM = "id";
    private static final String PATH = "path";
    private static final String SHOW_VERSION = "showVersion";
    private static final String PAGE_SIZE = "pageSize";
    private static final String MARKER = "marker";
    private static final String VERSION = "version";
    private static final String TOTALLY = "totally";
    private static final String TEST_PATH = "localhost:root/";
    private static final String CONTENT_DISPOSITION_PARAM = "contentDisposition";
    private static final String CLOUD = "cloud";
    private static final String SKIP_POLICY = "skipPolicy";
    private static final String PIPELINE_ID = "pipelineId";
    private static final String FILE_MASK = "fileMask";
    private static final String FILTER_MASK = "filterMask";
    private static final String REWRITE = "rewrite";
    private static final String EMPTY_STRING = "";
    private static final String PAGE = "page";
    private static final String RUN_ID = "runId";
    private static final ContentDisposition CONTENT_DISPOSITION = ContentDisposition.INLINE;
    private static final Map<String, String> TAGS = Collections.singletonMap(TEST, TEST);
    private static final TypeReference<Result<Integer>> INTEGER_TYPE = new TypeReference<Result<Integer>>() { };
    private static final TypeReference<Result<Object>> OBJECT_TYPE = new TypeReference<Result<Object>>() { };
    private static final TypeReference<Result<String>> STRING_TYPE = new TypeReference<Result<String>>() { };
    private static final TypeReference<Result<Map<String, String>>> STRING_STRING_MAP_TYPE =
            new TypeReference<Result<Map<String, String>>>() { };
    private final DataStorageRule dataStorageRule = DatastorageCreatorUtils.getDataStorageRule();
    private final S3bucketDataStorage s3Bucket = DatastorageCreatorUtils.getS3bucketDataStorage();
    private final AzureBlobStorage azureBlob = DatastorageCreatorUtils.getAzureBlobStorage();
    private final GSBucketStorage gsBucket = DatastorageCreatorUtils.getGsBucketStorage();
    private final NFSDataStorage nfsDataStorage = DatastorageCreatorUtils.getNfsDataStorage();
    private final DataStorageFile file = DatastorageCreatorUtils.getDataStorageFile();
    private final DataStorageFolder folder = DatastorageCreatorUtils.getDataStorageFolder();
    private final UpdateDataStorageItemVO update = DatastorageCreatorUtils.getUpdateDataStorageItemVO();

    @Autowired
    private DataStorageApiService mockStorageApiService;

    @Test
    public void shouldFaiGetDataStoragesForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(LOAD_ALL_URL));
    }

    @Test
    @WithMockUser
    public void shouldGetDataStorages() throws Exception {
        final List<S3bucketDataStorage> dataStorages = Collections.singletonList(s3Bucket);
        Mockito.doReturn(dataStorages).when(mockStorageApiService).getDataStorages();

        final MvcResult mvcResult = performRequest(get(LOAD_ALL_URL));

        Mockito.verify(mockStorageApiService).getDataStorages();
        assertResponse(mvcResult, dataStorages, DatastorageCreatorUtils.S3BUCKET_LIST_TYPE);
    }

    @Test
    public void shouldFailGetAvailableStoragesForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(LOAD_AVAILABLE_URL));
    }

    @Test
    @WithMockUser
    public void shouldGetAvailableStorages() throws Exception {
        final List<S3bucketDataStorage> dataStorages = Collections.singletonList(s3Bucket);
        Mockito.doReturn(dataStorages).when(mockStorageApiService).getAvailableStorages();

        final MvcResult mvcResult = performRequest(get(LOAD_AVAILABLE_URL));

        Mockito.verify(mockStorageApiService).getAvailableStorages();
        assertResponse(mvcResult, dataStorages, DatastorageCreatorUtils.S3BUCKET_LIST_TYPE);
    }

    @Test
    public void shouldFailGetAvailableStoragesWithMountObjectsForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(LOAD_AVAILABLE_WITH_MOUNTS));
    }

    @Test
    @WithMockUser
    public void shouldGetAvailableStoragesWithMountObjects() throws Exception {
        final List<DataStorageWithShareMount> storagesWithShareMounts =
                Collections.singletonList(DatastorageCreatorUtils.getDataStorageWithShareMount());
        Mockito.doReturn(storagesWithShareMounts).when(mockStorageApiService).getAvailableStoragesWithShareMount(ID);

        final MvcResult mvcResult = performRequest(get(LOAD_AVAILABLE_WITH_MOUNTS).param(FROM_REGION, ID_AS_STRING));

        Mockito.verify(mockStorageApiService).getAvailableStoragesWithShareMount(ID);
        assertResponse(mvcResult, storagesWithShareMounts, DatastorageCreatorUtils.DS_WITH_SHARE_MOUNT_TYPE);
    }

    @Test
    public void shouldFailGetWritableDataStoragesForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(LOAD_WRITABLE_URL));
    }

    @Test
    @WithMockUser
    public void shouldGetWritableDataStorages() throws Exception {
        final List<S3bucketDataStorage> dataStorages = Collections.singletonList(s3Bucket);
        Mockito.doReturn(dataStorages).when(mockStorageApiService).getWritableStorages();

        final MvcResult mvcResult = performRequest(get(LOAD_WRITABLE_URL));

        Mockito.verify(mockStorageApiService).getWritableStorages();
        assertResponse(mvcResult, dataStorages, DatastorageCreatorUtils.S3BUCKET_LIST_TYPE);
    }

    @Test
    public void shouldFailLoadDataStorageForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(String.format(LOAD_DATASTORAGE, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadS3BucketDataStorage() throws Exception {
        Mockito.doReturn(s3Bucket).when(mockStorageApiService).load(ID);

        final MvcResult mvcResult = performRequest(get(String.format(LOAD_DATASTORAGE, ID)));

        Mockito.verify(mockStorageApiService).load(ID);
        assertResponse(mvcResult, s3Bucket, DatastorageCreatorUtils.S3_BUCKET_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldLoadAzureBlobStorage() throws Exception {
        Mockito.doReturn(azureBlob).when(mockStorageApiService).load(ID);

        final MvcResult mvcResult = performRequest(get(String.format(LOAD_DATASTORAGE, ID)));

        Mockito.verify(mockStorageApiService).load(ID);
        assertResponse(mvcResult, azureBlob, DatastorageCreatorUtils.AZURE_BLOB_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldLoadGSBucketStorage() throws Exception {
        Mockito.doReturn(gsBucket).when(mockStorageApiService).load(ID);

        final MvcResult mvcResult = performRequest(get(String.format(LOAD_DATASTORAGE, ID)));

        Mockito.verify(mockStorageApiService).load(ID);
        assertResponse(mvcResult, gsBucket, DatastorageCreatorUtils.GS_BUCKET_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldLoadNFS3DataStorage() throws Exception {
        Mockito.doReturn(nfsDataStorage).when(mockStorageApiService).load(ID);

        final MvcResult mvcResult = performRequest(get(String.format(LOAD_DATASTORAGE, ID)));

        Mockito.verify(mockStorageApiService).load(ID);
        assertResponse(mvcResult, nfsDataStorage, DatastorageCreatorUtils.NFS_STORAGE_TYPE);
    }

    @Test
    public void shouldFailFindDataStorageForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(FIND_URL));
    }

    @Test
    @WithMockUser
    public void shouldFindS3BucketStorage() throws Exception {
        Mockito.doReturn(s3Bucket).when(mockStorageApiService).loadByNameOrId(TEST);

        final MvcResult mvcResult = performRequest(get(FIND_URL).param(ID_PARAM, TEST));

        Mockito.verify(mockStorageApiService).loadByNameOrId(TEST);
        assertResponse(mvcResult, s3Bucket, DatastorageCreatorUtils.S3_BUCKET_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldFindAzureBlobStorage() throws Exception {
        Mockito.doReturn(azureBlob).when(mockStorageApiService).loadByNameOrId(TEST);

        final MvcResult mvcResult = performRequest(get(FIND_URL).param(ID_PARAM, TEST));

        Mockito.verify(mockStorageApiService).loadByNameOrId(TEST);
        assertResponse(mvcResult, azureBlob, DatastorageCreatorUtils.AZURE_BLOB_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldFindGSBucketStorage() throws Exception {
        Mockito.doReturn(gsBucket).when(mockStorageApiService).loadByNameOrId(TEST);

        final MvcResult mvcResult = performRequest(get(FIND_URL).param(ID_PARAM, TEST));

        Mockito.verify(mockStorageApiService).loadByNameOrId(TEST);
        assertResponse(mvcResult, gsBucket, DatastorageCreatorUtils.GS_BUCKET_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldFindNFSDataStorage() throws Exception {
        Mockito.doReturn(nfsDataStorage).when(mockStorageApiService).loadByNameOrId(TEST);

        final MvcResult mvcResult = performRequest(get(FIND_URL).param(ID_PARAM, TEST));

        Mockito.verify(mockStorageApiService).loadByNameOrId(TEST);
        assertResponse(mvcResult, nfsDataStorage, DatastorageCreatorUtils.NFS_STORAGE_TYPE);
    }

    @Test
    public void shouldFailFindDataStorageByPathForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(FIND_BY_PATH_URL));
    }

    @Test
    @WithMockUser
    public void shouldFindS3BucketStorageByPath() throws Exception {
        Mockito.doReturn(s3Bucket).when(mockStorageApiService).loadByPathOrId(TEST);

        final MvcResult mvcResult = performRequest(get(FIND_BY_PATH_URL).param(ID_PARAM, TEST));

        Mockito.verify(mockStorageApiService).loadByPathOrId(TEST);
        assertResponse(mvcResult, s3Bucket, DatastorageCreatorUtils.S3_BUCKET_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldFindAzureBlobStorageByPath() throws Exception {
        Mockito.doReturn(azureBlob).when(mockStorageApiService).loadByPathOrId(TEST);

        final MvcResult mvcResult = performRequest(get(FIND_BY_PATH_URL).param(ID_PARAM, TEST));

        Mockito.verify(mockStorageApiService).loadByPathOrId(TEST);
        assertResponse(mvcResult, azureBlob, DatastorageCreatorUtils.AZURE_BLOB_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldFindGSBucketStorageByPath() throws Exception {
        Mockito.doReturn(gsBucket).when(mockStorageApiService).loadByPathOrId(TEST);

        final MvcResult mvcResult = performRequest(get(FIND_BY_PATH_URL).param(ID_PARAM, TEST));

        Mockito.verify(mockStorageApiService).loadByPathOrId(TEST);
        assertResponse(mvcResult, gsBucket, DatastorageCreatorUtils.GS_BUCKET_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldFindNFSDataStorageByPath() throws Exception {
        Mockito.doReturn(nfsDataStorage).when(mockStorageApiService).loadByPathOrId(TEST);

        final MvcResult mvcResult = performRequest(get(FIND_BY_PATH_URL).param(ID_PARAM, TEST));

        Mockito.verify(mockStorageApiService).loadByPathOrId(TEST);
        assertResponse(mvcResult, nfsDataStorage, DatastorageCreatorUtils.NFS_STORAGE_TYPE);
    }

    @Test
    public void shouldFailGetDataStorageItemsForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(String.format(DATASTORAGE_ITEMS_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGetDataStorageFolder() throws Exception {
        final DataStorageListing dataStorageListing = DatastorageCreatorUtils.getDataStorageListing();
        final List<DataStorageFolder> folders = Collections.singletonList(folder);
        dataStorageListing.setResults(Collections.singletonList(folder));
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PATH, TEST);
        params.add(SHOW_VERSION, FALSE_AS_STRING);
        Mockito.doReturn(dataStorageListing).when(
                mockStorageApiService).getDataStorageItems(ID, TEST, false, null, null
        );

        final MvcResult mvcResult = performRequest(get(String.format(DATASTORAGE_ITEMS_URL, ID)).params(params));

        Mockito.verify(mockStorageApiService).getDataStorageItems(ID, TEST, false, null, null);
        assertResponse(mvcResult, folders, DatastorageCreatorUtils.DATA_STORAGE_FOLDER_LIST_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldGetDataStorageOwnerFile() throws Exception {
        final DataStorageListing dataStorageListing = DatastorageCreatorUtils.getDataStorageListing();
        final List<DataStorageFile> files = Collections.singletonList(file);
        dataStorageListing.setResults(Collections.singletonList(file));
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PATH, TEST);
        params.add(SHOW_VERSION, TRUE_AS_STRING);
        Mockito.doReturn(dataStorageListing).when(
                mockStorageApiService).getDataStorageItemsOwner(ID, TEST, true, null, null
        );

        final MvcResult mvcResult = performRequest(get(String.format(DATASTORAGE_ITEMS_URL, ID)).params(params));

        Mockito.verify(mockStorageApiService).getDataStorageItemsOwner(ID, TEST, true, null, null);
        assertResponse(mvcResult, files, DatastorageCreatorUtils.DATA_STORAGE_FILE_LIST_TYPE);
    }

    @Test
    public void shouldFailGetDataStorageItemsListingForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(String.format(DATASTORAGE_LISTING_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGetDataStorageItems() throws Exception {
        final DataStorageListing dataStorageListing = DatastorageCreatorUtils.getDataStorageListing();
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PATH, TEST);
        params.add(SHOW_VERSION, FALSE_AS_STRING);
        params.add(PAGE_SIZE, ID_AS_STRING);
        params.add(MARKER, TEST);
        Mockito.doReturn(dataStorageListing).when(
                mockStorageApiService).getDataStorageItems(ID, TEST, false, TEST_INT, TEST
        );

        final MvcResult mvcResult = performRequest(get(String.format(DATASTORAGE_LISTING_URL, ID)).params(params));

        Mockito.verify(mockStorageApiService).getDataStorageItems(ID, TEST, false, TEST_INT, TEST);
        assertResponse(mvcResult, dataStorageListing, DatastorageCreatorUtils.DATA_STORAGE_LISTING_TYPE);
    }

    @Test
    public void shouldFailUpdateDataStorageItemsForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(post(String.format(DATASTORAGE_ITEMS_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldUpdateDataStorageItems() throws Exception {
        final List<UpdateDataStorageItemVO> updateList = Collections.singletonList(update);
        final List<DataStorageFile> files = Collections.singletonList(file);
        final String content = getObjectMapper().writeValueAsString(updateList);
        Mockito.doReturn(files).when(mockStorageApiService).updateDataStorageItems(ID, updateList);

        final MvcResult mvcResult = performRequest(post(String.format(DATASTORAGE_ITEMS_URL, ID)).content(content));

        Mockito.verify(mockStorageApiService).updateDataStorageItems(ID, updateList);
        assertResponse(mvcResult, files, DatastorageCreatorUtils.DATA_STORAGE_FILE_LIST_TYPE);
    }

    @Test
    public void shouldFailUploadFileForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(post(String.format(DATASTORAGE_ITEMS_UPLOAD_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldUploadFile() throws Exception {
        final UploadFileMetadata uploadFileMetadata = new UploadFileMetadata();
        final String contentType = MediaType.MULTIPART_FORM_DATA_VALUE + ";boundary=";
        final MockMultipartFile file = new MockMultipartFile("data", "file.txt", "text/plain", "some xml".getBytes());
        final MockMultipartFile feli = new MockMultipartFile("data", "file2.txt", "text/plain", "some xml".getBytes());
        final MvcResult mvcResult = mvc().perform(fileUpload(String.format(DATASTORAGE_ITEMS_UPLOAD_URL, ID))
                .file(file)
                .file(feli)
                .servletPath(SERVLET_PATH)
                .contentType(contentType)
                .param(PATH, TEST))
                .andExpect(status().isOk())
                .andReturn();

        Mockito.verify(mockStorageApiService).createDataStorageFile(ID, TEST, TEST, TEST.getBytes());

        assertResponse(mvcResult, uploadFileMetadata, new TypeReference<Result<UploadFileMetadata>>() { });
    }

    @Test
    public void shouldFailUploadStreamForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(post(String.format(DATASTORAGE_UPLOAD_STREAM_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldUploadStream() throws Exception {
        final UploadFileMetadata uploadFileMetadata = new UploadFileMetadata();
        final String contentType = MediaType.MULTIPART_FORM_DATA_VALUE + ";boundary=";
        final MockMultipartFile file =
                new MockMultipartFile("data", "file.txt", "text/plain", "some xml".getBytes());
        final MockMultipartFile feli =
                new MockMultipartFile("data", "file2.txt", "text/plain", "some xml".getBytes());
        final MvcResult mvcResult = mvc().perform(fileUpload(String.format(DATASTORAGE_ITEMS_UPLOAD_URL, ID))
                .file(feli)
                .file(file)
                .servletPath(SERVLET_PATH)
                .contentType(contentType)
                .param(PATH, TEST))
                .andExpect(status().isOk())
                .andReturn();


        Mockito.verify(mockStorageApiService).createDataStorageFile(ID, TEST, TEST, TEST.getBytes());

        assertResponse(mvcResult, uploadFileMetadata, new TypeReference<Result<UploadFileMetadata>>() { });
    }

    @Test
    public void shouldFailDownloadStreamForUnauthorizedUser() throws Exception {
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

        final MvcResult mvcResult = performRequest(
                get(String.format(DATASTORAGE_DOWNLOAD_STREAM_URL, ID)).params(params), OCTET_STREAM_CONTENT_TYPE
        );

        Mockito.verify(mockStorageApiService).getStreamingContent(ID, TEST, TEST);

        final String actualData = mvcResult.getResponse().getContentAsString();
        Assert.assertEquals(TEST, actualData);
    }

    @Test
    public void shouldFailUploadStorageItemForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(post(String.format(DATASTORAGE_ITEMS_CONTENT, ID)));
    }

    @Test
    @WithMockUser
    public void shouldUploadStorageItem() throws Exception {
        Mockito.doReturn(file).when(
                mockStorageApiService).createDataStorageFile(ID, TEST, TEST.getBytes(Charset.defaultCharset())
        );
        final String content = getObjectMapper().writeValueAsString(TEST);

        final MvcResult mvcResult = performRequest(
                post(String.format(DATASTORAGE_ITEMS_CONTENT, ID)).param(PATH, TEST).content(content)
        );

        Mockito.verify(mockStorageApiService).createDataStorageFile(ID, TEST, TEST.getBytes(Charset.defaultCharset()));
        assertResponse(mvcResult, file, DatastorageCreatorUtils.DATA_STORAGE_FILE_TYPE);
    }

    @Test
    public void shouldFailDeleteDataStorageItemForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(delete(String.format(DATASTORAGE_ITEMS_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDeleteDataStorageItems() throws Exception {
        final List<UpdateDataStorageItemVO> updateList = Collections.singletonList(update);
        final String content = getObjectMapper().writeValueAsString(updateList);
        Mockito.doReturn(TEST_INT).when(mockStorageApiService).deleteDataStorageItems(ID, updateList, false);

        final MvcResult mvcResult = performRequest(
                delete(String.format(DATASTORAGE_ITEMS_URL, ID)).param(TOTALLY, FALSE_AS_STRING).content(content)
        );

        Mockito.verify(mockStorageApiService).deleteDataStorageItems(ID, updateList, false);
        assertResponse(mvcResult, TEST_INT, INTEGER_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldDeleteDataStorageItemsOwner() throws Exception {
        final List<UpdateDataStorageItemVO> updateList = Collections.singletonList(update);
        final String content = getObjectMapper().writeValueAsString(updateList);
        Mockito.doReturn(1).when(mockStorageApiService).deleteDataStorageItemsOwner(ID, updateList, true);

        final MvcResult mvcResult = performRequest(
                delete(String.format(DATASTORAGE_ITEMS_URL, ID)).param(TOTALLY, TRUE_AS_STRING).content(content)
        );

        Mockito.verify(mockStorageApiService).deleteDataStorageItemsOwner(ID, updateList, true);
        assertResponse(mvcResult, 1, INTEGER_TYPE);
    }

    @Test
    public void shouldFailGenerateItemUrlAndRedirectForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(String.format(DOWNLOAD_REDIRECT_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGenerateItemUrlAndRedirect() throws Exception {
        final String result = "redirect:/TEST";
        final DataStorageDownloadFileUrl url = DatastorageCreatorUtils.getDataStorageDownloadFileUrl();
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PATH, TEST_PATH);
        params.add(CONTENT_DISPOSITION_PARAM, CONTENT_DISPOSITION.name());
        Mockito.doReturn(url).when(mockStorageApiService).generateDataStorageItemUrl(
                ID, TEST_PATH, null, CONTENT_DISPOSITION
        );

        final MvcResult mvcResult = performRequest(get(String.format(DOWNLOAD_REDIRECT_URL, ID)).params(params));

        Mockito.verify(mockStorageApiService).generateDataStorageItemUrl(
                ID, TEST_PATH, null, CONTENT_DISPOSITION
        );
        final String actual = mvcResult.getResponse().getContentAsString();
        assertThat("\"" + result + "\"").isEqualTo(actual);
    }

    @Test
    public void shouldFailGenerateDataStorageItemUrl() throws Exception {
        performUnauthorizedRequest(get(String.format(GENERATE_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGenerateDataStorageItemUrlOwner() throws Exception {
        final DataStorageDownloadFileUrl url = DatastorageCreatorUtils.getDataStorageDownloadFileUrl();
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PATH, TEST_PATH);
        params.add(VERSION, TEST);
        params.add(CONTENT_DISPOSITION_PARAM, CONTENT_DISPOSITION.name());
        Mockito.doReturn(url).when(mockStorageApiService).generateDataStorageItemUrlOwner(
                ID, TEST_PATH, TEST, CONTENT_DISPOSITION
        );

        final MvcResult mvcResult = performRequest(get(String.format(GENERATE_URL, ID)).params(params));

        Mockito.verify(mockStorageApiService).generateDataStorageItemUrlOwner(
                ID, TEST_PATH, TEST, CONTENT_DISPOSITION
        );

        assertResponse(mvcResult, url, DatastorageCreatorUtils.DOWNLOAD_FILE_URL_TYPE);
    }

    @Test
    public void shouldFailGenerateDataStorageItemUploadUrlForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(String.format(GENERATE_UPLOAD_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGenerateDataStorageItemUploadUrl() throws Exception {
        final DataStorageDownloadFileUrl url = DatastorageCreatorUtils.getDataStorageDownloadFileUrl();
        Mockito.doReturn(url).when(mockStorageApiService).generateDataStorageItemUploadUrl(ID, TEST);

        final MvcResult mvcResult = performRequest(get(String.format(GENERATE_UPLOAD_URL, ID)).param(PATH, TEST));

        Mockito.verify(mockStorageApiService).generateDataStorageItemUploadUrl(ID, TEST);
        assertResponse(mvcResult, url, DatastorageCreatorUtils.DOWNLOAD_FILE_URL_TYPE);
    }

    @Test
    public void shouldFailGetDataStorageItemContentForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(String.format(CONTENT_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGetDataStorageItemContent() throws Exception {
        final DataStorageItemContent dataStorageItemContent = DatastorageCreatorUtils.getDataStorageItemContent();
        Mockito.doReturn(dataStorageItemContent).when(mockStorageApiService)
                .getDataStorageItemContent(ID, TEST, null);

        final MvcResult mvcResult = performRequest(get(String.format(CONTENT_URL, ID)).param(PATH, TEST));

        Mockito.verify(mockStorageApiService).getDataStorageItemContent(ID, TEST, null);
        assertResponse(mvcResult, dataStorageItemContent, DatastorageCreatorUtils.ITEM_CONTENT_URL);
    }

    @Test
    @WithMockUser
    public void shouldGetDataStorageItemContentOwner() throws Exception {
        final DataStorageItemContent dataStorageItemContent = DatastorageCreatorUtils.getDataStorageItemContent();
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PATH, TEST);
        params.add(VERSION, TEST);
        Mockito.doReturn(dataStorageItemContent).when(mockStorageApiService)
                .getDataStorageItemContentOwner(ID, TEST, TEST);

        final MvcResult mvcResult = performRequest(get(String.format(CONTENT_URL, ID)).params(params));

        Mockito.verify(mockStorageApiService).getDataStorageItemContentOwner(ID, TEST, TEST);
        assertResponse(mvcResult, dataStorageItemContent, DatastorageCreatorUtils.ITEM_CONTENT_URL);
    }

    @Test
    public void shouldFailGenerateDataStorageItemsUrlsForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(post(String.format(GENERATE_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGenerateDataStorageItemsUrls() throws Exception {
        final DataStorageDownloadFileUrl url = DatastorageCreatorUtils.getDataStorageDownloadFileUrl();
        final List<DataStorageDownloadFileUrl> urls = Collections.singletonList(url);
        final GenerateDownloadUrlVO generateDownloadUrlVO = DatastorageCreatorUtils.getGenerateDownloadUrlVO();
        final String content = getObjectMapper().writeValueAsString(generateDownloadUrlVO);
        Mockito.doReturn(urls).when(mockStorageApiService).generateDataStorageItemUrl(
                ID, generateDownloadUrlVO.getPaths(), generateDownloadUrlVO.getPermissions(),
                generateDownloadUrlVO.getHours()
        );

        final MvcResult mvcResult = performRequest(post(String.format(GENERATE_URL, ID)).content(content));

        Mockito.verify(mockStorageApiService).generateDataStorageItemUrl(
                ID, generateDownloadUrlVO.getPaths(), generateDownloadUrlVO.getPermissions(),
                generateDownloadUrlVO.getHours()
        );
        assertResponse(mvcResult, urls, DatastorageCreatorUtils.DOWNLOAD_FILE_URL_LIST_TYPE);
    }

    @Test
    public void shouldFailGenerateDataStorageItemsUploadUrlsForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(post(String.format(GENERATE_UPLOAD_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGenerateDataStorageItemsUploadUrls() throws Exception {
        final DataStorageDownloadFileUrl url = DatastorageCreatorUtils.getDataStorageDownloadFileUrl();
        final List<DataStorageDownloadFileUrl> urls = Collections.singletonList(url);
        final List<String> paths = Collections.singletonList(TEST);
        final String content = getObjectMapper().writeValueAsString(paths);
        Mockito.doReturn(urls).when(mockStorageApiService).generateDataStorageItemUploadUrl(ID, paths);

        final MvcResult mvcResult = performRequest(post(String.format(GENERATE_UPLOAD_URL, ID)).content(content));

        Mockito.verify(mockStorageApiService).generateDataStorageItemUploadUrl(ID, paths);
        assertResponse(mvcResult, urls, DatastorageCreatorUtils.DOWNLOAD_FILE_URL_LIST_TYPE);
    }

    @Test
    public void shouldFailRestoreFileVersionForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(post(String.format(RESTORE_VERSION_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldRestoreFileVersion() throws Exception {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PATH, TEST);
        params.add(VERSION, TEST);

        final MvcResult mvcResult = performRequest(post(String.format(RESTORE_VERSION_URL, ID)).params(params));

        Mockito.verify(mockStorageApiService).restoreFileVersion(ID, TEST, TEST);
        assertResponse(mvcResult, null, OBJECT_TYPE);
    }

    @Test
    public void shouldFailRegisterDataStorage() throws Exception {
        performUnauthorizedRequest(post(DATASTORAGE_SAVE_URL));
    }

    @Test
    @WithMockUser
    public void shouldRegisterS3BucketDataStorage() throws Exception {
        final SecuredEntityWithAction<S3bucketDataStorage> securedS3bucket = new SecuredEntityWithAction<>();
        securedS3bucket.setEntity(s3Bucket);
        final DataStorageVO dataStorageVO = DatastorageCreatorUtils.getDataStorageVO();
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(CLOUD, TRUE_AS_STRING);
        params.add(SKIP_POLICY, TRUE_AS_STRING);
        final String content = getObjectMapper().writeValueAsString(dataStorageVO);
        Mockito.doReturn(securedS3bucket).when(
                mockStorageApiService).create(Mockito.refEq(dataStorageVO), Mockito.eq(true), Mockito.eq(true)
        );

        final MvcResult mvcResult = performRequest(post(DATASTORAGE_SAVE_URL).params(params).content(content));

        Mockito.verify(mockStorageApiService).create(Mockito.refEq(dataStorageVO), Mockito.eq(true), Mockito.eq(true));
        assertResponse(mvcResult, securedS3bucket, DatastorageCreatorUtils.SECURED_S3_BUCKET_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldRegisterAzureBlobStorage() throws Exception {
        final SecuredEntityWithAction<AzureBlobStorage> securedAzureStorage = new SecuredEntityWithAction<>();
        securedAzureStorage.setEntity(azureBlob);
        final DataStorageVO dataStorageVO = DatastorageCreatorUtils.getDataStorageVO();
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(CLOUD, TRUE_AS_STRING);
        params.add(SKIP_POLICY, TRUE_AS_STRING);
        final String content = getObjectMapper().writeValueAsString(dataStorageVO);
        Mockito.doReturn(securedAzureStorage).when(
                mockStorageApiService).create(Mockito.refEq(dataStorageVO), Mockito.eq(true), Mockito.eq(true)
        );

        final MvcResult mvcResult = performRequest(post(DATASTORAGE_SAVE_URL).params(params).content(content));

        Mockito.verify(mockStorageApiService).create(Mockito.refEq(dataStorageVO), Mockito.eq(true), Mockito.eq(true));
        assertResponse(mvcResult, securedAzureStorage, DatastorageCreatorUtils.SECURED_AZURE_BLOB_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldRegisterGSBucketStorage() throws Exception {
        final SecuredEntityWithAction<GSBucketStorage> securedGSbucket = new SecuredEntityWithAction<>();
        securedGSbucket.setEntity(gsBucket);
        final DataStorageVO dataStorageVO = DatastorageCreatorUtils.getDataStorageVO();
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(CLOUD, TRUE_AS_STRING);
        params.add(SKIP_POLICY, TRUE_AS_STRING);
        final String content = getObjectMapper().writeValueAsString(dataStorageVO);
        Mockito.doReturn(securedGSbucket).when(
                mockStorageApiService).create(Mockito.refEq(dataStorageVO), Mockito.eq(true), Mockito.eq(true)
        );

        final MvcResult mvcResult = performRequest(post(DATASTORAGE_SAVE_URL).params(params).content(content));

        Mockito.verify(mockStorageApiService).create(Mockito.refEq(dataStorageVO), Mockito.eq(true), Mockito.eq(true));
        assertResponse(mvcResult, securedGSbucket, DatastorageCreatorUtils.SECURED_GS_BUCKET_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldRegisterNFSStorage() throws Exception {
        final SecuredEntityWithAction<NFSDataStorage> securedNFSStorage = new SecuredEntityWithAction<>();
        securedNFSStorage.setEntity(nfsDataStorage);
        final DataStorageVO dataStorageVO = DatastorageCreatorUtils.getDataStorageVO();
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(CLOUD, TRUE_AS_STRING);
        params.add(SKIP_POLICY, TRUE_AS_STRING);
        final String content = getObjectMapper().writeValueAsString(dataStorageVO);
        Mockito.doReturn(securedNFSStorage).when(
                mockStorageApiService).create(Mockito.refEq(dataStorageVO), Mockito.eq(true), Mockito.eq(true)
        );

        final MvcResult mvcResult = performRequest(post(DATASTORAGE_SAVE_URL).params(params).content(content));

        Mockito.verify(mockStorageApiService).create(Mockito.refEq(dataStorageVO), Mockito.eq(true), Mockito.eq(true));
        assertResponse(mvcResult, securedNFSStorage, DatastorageCreatorUtils.SECURED_NFS_STORAGE_TYPE);
    }

    @Test
    public void shouldFailUpdateDataStorage() throws Exception {
        performUnauthorizedRequest(post(DATASTORAGE_UPDATE_URL));
    }

    @Test
    @WithMockUser
    public void shouldUpdateS3BucketDataStorage() throws Exception {
        final DataStorageVO dataStorageVO = DatastorageCreatorUtils.getDataStorageVO();
        final String content = getObjectMapper().writeValueAsString(dataStorageVO);
        Mockito.doReturn(s3Bucket).when(mockStorageApiService).update(Mockito.refEq(dataStorageVO));

        final MvcResult mvcResult = performRequest(post(DATASTORAGE_UPDATE_URL).content(content));

        Mockito.verify(mockStorageApiService).update(Mockito.refEq(dataStorageVO));
        assertResponse(mvcResult, s3Bucket, DatastorageCreatorUtils.S3_BUCKET_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldUpdateAzureBlobStorage() throws Exception {
        final DataStorageVO dataStorageVO = DatastorageCreatorUtils.getDataStorageVO();
        final String content = getObjectMapper().writeValueAsString(dataStorageVO);
        Mockito.doReturn(azureBlob).when(mockStorageApiService).update(Mockito.refEq(dataStorageVO));

        final MvcResult mvcResult = performRequest(post(DATASTORAGE_UPDATE_URL).content(content));

        Mockito.verify(mockStorageApiService).update(Mockito.refEq(dataStorageVO));
        assertResponse(mvcResult, azureBlob, DatastorageCreatorUtils.AZURE_BLOB_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldUpdateGSBucketStorage() throws Exception {
        final DataStorageVO dataStorageVO = DatastorageCreatorUtils.getDataStorageVO();
        final String content = getObjectMapper().writeValueAsString(dataStorageVO);
        Mockito.doReturn(gsBucket).when(mockStorageApiService).update(Mockito.refEq(dataStorageVO));

        final MvcResult mvcResult = performRequest(post(DATASTORAGE_UPDATE_URL).content(content));

        Mockito.verify(mockStorageApiService).update(Mockito.refEq(dataStorageVO));
        assertResponse(mvcResult, gsBucket, DatastorageCreatorUtils.GS_BUCKET_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldUpdateNFSStorage() throws Exception {
        final DataStorageVO dataStorageVO = DatastorageCreatorUtils.getDataStorageVO();
        final String content = getObjectMapper().writeValueAsString(dataStorageVO);
        Mockito.doReturn(nfsDataStorage).when(mockStorageApiService).update(Mockito.refEq(dataStorageVO));

        final MvcResult mvcResult = performRequest(post(DATASTORAGE_UPDATE_URL).content(content));

        Mockito.verify(mockStorageApiService).update(Mockito.refEq(dataStorageVO));
        assertResponse(mvcResult, nfsDataStorage, DatastorageCreatorUtils.NFS_STORAGE_TYPE);
    }

    @Test
    public void shouldFailUpdateStoragePolicyForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(post(DATASTORAGE_POLICY_URL));
    }

    @Test
    @WithMockUser
    public void shouldUpdateS3BucketDataStoragePolicy() throws Exception {
        final DataStorageVO dataStorageVO = DatastorageCreatorUtils.getDataStorageVO();
        final String content = getObjectMapper().writeValueAsString(dataStorageVO);
        Mockito.doReturn(s3Bucket).when(mockStorageApiService).updatePolicy(Mockito.refEq(dataStorageVO));

        final MvcResult mvcResult = performRequest(post(DATASTORAGE_POLICY_URL).content(content));

        Mockito.verify(mockStorageApiService).updatePolicy(Mockito.refEq(dataStorageVO));
        assertResponse(mvcResult, s3Bucket, DatastorageCreatorUtils.S3_BUCKET_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldUpdateAzureBlobStoragePolicy() throws Exception {
        final DataStorageVO dataStorageVO = DatastorageCreatorUtils.getDataStorageVO();
        final String content = getObjectMapper().writeValueAsString(dataStorageVO);
        Mockito.doReturn(azureBlob).when(mockStorageApiService).updatePolicy(Mockito.refEq(dataStorageVO));

        final MvcResult mvcResult = performRequest(post(DATASTORAGE_POLICY_URL).content(content));

        Mockito.verify(mockStorageApiService).updatePolicy(Mockito.refEq(dataStorageVO));
        assertResponse(mvcResult, azureBlob, DatastorageCreatorUtils.AZURE_BLOB_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldUpdateGSBucketStoragePolicy() throws Exception {
        final DataStorageVO dataStorageVO = DatastorageCreatorUtils.getDataStorageVO();
        final String content = getObjectMapper().writeValueAsString(dataStorageVO);
        Mockito.doReturn(gsBucket).when(mockStorageApiService).updatePolicy(Mockito.refEq(dataStorageVO));

        final MvcResult mvcResult = performRequest(post(DATASTORAGE_POLICY_URL).content(content));

        Mockito.verify(mockStorageApiService).updatePolicy(Mockito.refEq(dataStorageVO));
        assertResponse(mvcResult, gsBucket, DatastorageCreatorUtils.GS_BUCKET_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldUpdateNFSStoragePolicy() throws Exception {
        final DataStorageVO dataStorageVO = DatastorageCreatorUtils.getDataStorageVO();
        final String content = getObjectMapper().writeValueAsString(dataStorageVO);
        Mockito.doReturn(nfsDataStorage).when(mockStorageApiService).updatePolicy(Mockito.refEq(dataStorageVO));

        final MvcResult mvcResult = performRequest(post(DATASTORAGE_POLICY_URL).content(content));

        Mockito.verify(mockStorageApiService).updatePolicy(Mockito.refEq(dataStorageVO));
        assertResponse(mvcResult, nfsDataStorage, DatastorageCreatorUtils.NFS_STORAGE_TYPE);
    }

    @Test
    public void shouldDeleteDataStorageForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(delete(DATASTORAGE_DELETE_URL, ID));
    }

    @Test
    @WithMockUser
    public void shouldDeleteS3bucketDataStorage() throws Exception {
        Mockito.doReturn(s3Bucket).when(mockStorageApiService).delete(ID, true);

        final MvcResult mvcResult = performRequest(
                delete(String.format(DATASTORAGE_DELETE_URL, ID)).param(CLOUD, TRUE_AS_STRING)
        );

        Mockito.verify(mockStorageApiService).delete(ID, true);
        assertResponse(mvcResult, s3Bucket, DatastorageCreatorUtils.S3_BUCKET_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldDeleteAzureBlobStorage() throws Exception {
        Mockito.doReturn(azureBlob).when(mockStorageApiService).delete(ID, true);

        final MvcResult mvcResult = performRequest(
                delete(String.format(DATASTORAGE_DELETE_URL, ID)).param(CLOUD, TRUE_AS_STRING)
        );

        Mockito.verify(mockStorageApiService).delete(ID, true);
        assertResponse(mvcResult, azureBlob, DatastorageCreatorUtils.AZURE_BLOB_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldDeleteGSbucketStorage() throws Exception {
        Mockito.doReturn(gsBucket).when(mockStorageApiService).delete(ID, true);

        final MvcResult mvcResult = performRequest(
                delete(String.format(DATASTORAGE_DELETE_URL, ID)).param(CLOUD, TRUE_AS_STRING)
        );

        Mockito.verify(mockStorageApiService).delete(ID, true);
        assertResponse(mvcResult, gsBucket, DatastorageCreatorUtils.GS_BUCKET_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldDeleteNFSStorage() throws Exception {
        Mockito.doReturn(nfsDataStorage).when(mockStorageApiService).delete(ID, true);

        final MvcResult mvcResult = performRequest(
                delete(String.format(DATASTORAGE_DELETE_URL, ID)).param(CLOUD, TRUE_AS_STRING)
        );

        Mockito.verify(mockStorageApiService).delete(ID, true);
        assertResponse(mvcResult, nfsDataStorage, DatastorageCreatorUtils.NFS_STORAGE_TYPE);
    }

    @Test
    public void shouldFailSaveDataStorageRuleForUnauthorizedUser() throws Exception {
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
    public void shouldFailLoadDataStorageRuleForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(LOAD_RULES_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadDataStorageRule() throws Exception {
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
    public void shouldFailDeleteDataStorageRuleForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(delete(DELETE_RULES_URL));
    }

    @Test
    @WithMockUser
    public void shouldDeleteDataStorageRule() throws Exception {
        Mockito.doReturn(dataStorageRule).when(mockStorageApiService).deleteRule(ID, TEST);
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(ID_PARAM, ID_AS_STRING);
        params.add(FILE_MASK, TEST);

        final MvcResult mvcResult = performRequest(delete(DELETE_RULES_URL).params(params));

        Mockito.verify(mockStorageApiService).deleteRule(ID, TEST);
        assertResponse(mvcResult, dataStorageRule, DatastorageCreatorUtils.DATA_STORAGE_RULE_URL);
    }

    @Test
    public void shouldFailGenerateTemporaryCredentialsForUnauthorizedUser() throws Exception {
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
    public void shouldFailUpdateTagsForUnauthorizedUser() throws Exception {
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
    public void shouldFailLoadTagsByIdForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(String.format(TAGS_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadTagsByIdOwner() throws Exception {
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
    public void shouldLoadTagsById() throws Exception {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PATH, TEST);
        params.add(VERSION, EMPTY_STRING);
        Mockito.doReturn(TAGS).when(mockStorageApiService).loadDataStorageObjectTags(ID, TEST, EMPTY_STRING);

        final MvcResult mvcResult = performRequest(get(String.format(TAGS_URL, ID)).params(params));

        Mockito.verify(mockStorageApiService).loadDataStorageObjectTags(ID, TEST, EMPTY_STRING);
        assertResponse(mvcResult, TAGS, STRING_STRING_MAP_TYPE);
    }

    @Test
    public void shouldFailDeleteTagsByIdForUnauthorizedUser() throws Exception {
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
    public void shouldFailGetDataStorageItemsWithTagsForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(TAGS_LIST_URL, ID));
    }

    @Test
    @WithMockUser
    public void shouldGetDataStorageFileWithTags() throws Exception {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(ID_PARAM, ID_AS_STRING);
        params.add(PATH, TEST);
        Mockito.doReturn(file).when(mockStorageApiService).getDataStorageItemWithTags(ID, TEST, false);

        final MvcResult mvcResult = performRequest(get(String.format(TAGS_LIST_URL, ID)).params(params));

        Mockito.verify(mockStorageApiService).getDataStorageItemWithTags(ID, TEST, false);
        assertResponse(mvcResult, file, DatastorageCreatorUtils.DATA_STORAGE_FILE_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldGetDataStorageFileOwnerWithTags() throws Exception {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(ID_PARAM, ID_AS_STRING);
        params.add(PATH, TEST);
        params.add(SHOW_VERSION, TRUE_AS_STRING);
        Mockito.doReturn(file).when(mockStorageApiService).getDataStorageItemOwnerWithTags(ID, TEST, true);

        final MvcResult mvcResult = performRequest(get(String.format(TAGS_LIST_URL, ID)).params(params));

        Mockito.verify(mockStorageApiService).getDataStorageItemOwnerWithTags(ID, TEST, true);
        assertResponse(mvcResult, file, DatastorageCreatorUtils.DATA_STORAGE_FILE_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldGetDataStorageFolderWithTags() throws Exception {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(ID_PARAM, ID_AS_STRING);
        params.add(PATH, TEST);
        Mockito.doReturn(folder).when(mockStorageApiService).getDataStorageItemWithTags(ID, TEST, false);

        final MvcResult mvcResult = performRequest(get(String.format(TAGS_LIST_URL, ID)).params(params));

        Mockito.verify(mockStorageApiService).getDataStorageItemWithTags(ID, TEST, false);
        assertResponse(mvcResult, folder, DatastorageCreatorUtils.DATA_STORAGE_FOLDER_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldGetDataStorageFolderOwnerWithTags() throws Exception {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(ID_PARAM, ID_AS_STRING);
        params.add(PATH, TEST);
        params.add(SHOW_VERSION, TRUE_AS_STRING);
        Mockito.doReturn(folder).when(mockStorageApiService).getDataStorageItemOwnerWithTags(ID, TEST, true);

        final MvcResult mvcResult = performRequest(get(String.format(TAGS_LIST_URL, ID)).params(params));

        Mockito.verify(mockStorageApiService).getDataStorageItemOwnerWithTags(ID, TEST, true);
        assertResponse(mvcResult, folder, DatastorageCreatorUtils.DATA_STORAGE_FOLDER_TYPE);
    }

    @Test
    public void shouldFailGetDataStorageSharedLinkForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(String.format(SHARED_LINK_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGetDataStorageSharedLink() throws Exception {
        Mockito.doReturn(TEST).when(mockStorageApiService).getDataStorageSharedLink(ID);

        final MvcResult mvcResult = performRequest(get(String.format(SHARED_LINK_URL, ID)));

        Mockito.verify(mockStorageApiService).getDataStorageSharedLink(ID);
        assertResponse(mvcResult, TEST, STRING_TYPE);
    }

    @Test
    public void shouldFailGetDataStoragePermissionsForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(PERMISSION_URL));
    }

    @Test
    @WithMockUser
    public void shouldGetStoragePermissions() throws Exception {
        final EntityWithPermissionVO entityWithPermissionVO = DatastorageCreatorUtils.getEntityWithPermissionVO();
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PAGE, ID_AS_STRING);
        params.add(PAGE_SIZE, ID_AS_STRING);
        params.add(FILTER_MASK, ID_AS_STRING);
        Mockito.doReturn(entityWithPermissionVO).when(
                mockStorageApiService).getStoragePermission(TEST_INT, TEST_INT, TEST_INT
        );

        final MvcResult mvcResult = performRequest(get(PERMISSION_URL).params(params));

        Mockito.verify(mockStorageApiService).getStoragePermission(TEST_INT, TEST_INT, TEST_INT);
        assertResponse(mvcResult, entityWithPermissionVO, DatastorageCreatorUtils.ENTITY_WITH_PERMISSION_VO_TYPE);
    }

    @Test
    public void shouldFailGetDataSizesForUnauthorizedUser() throws Exception {
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
    public void shouldFailGetDataStorageUsageForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(PATH_USAGE_URL));
    }

    @Test
    @WithMockUser
    public void shouldGetStorageUsage() throws Exception {
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
    public void shouldFailCreateSharedFSSPathForRunForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(post(SHARED_STORAGE_URL));
    }

    @Test
    @WithMockUser
    public void shouldCreateSharedFSSPathForRun() throws Exception {
        final StorageMountPath storageMountPath = DatastorageCreatorUtils.getStorageMountPath();
        Mockito.doReturn(storageMountPath).when(mockStorageApiService).getSharedFSSPathForRun(ID, true);

        final MvcResult mvcResult = performRequest(post(SHARED_STORAGE_URL).param(RUN_ID, ID_AS_STRING));

        Mockito.verify(mockStorageApiService).getSharedFSSPathForRun(ID, true);
        assertResponse(mvcResult, storageMountPath, DatastorageCreatorUtils.STORAGE_MOUNT_PATH_TYPE);
    }

    @Test
    public void shouldFailGetSharedFSSPathForRunForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(SHARED_STORAGE_URL));
    }

    @Test
    @WithMockUser
    public void shouldGetSharedFSSPathForRun() throws Exception {
        final StorageMountPath storageMountPath = DatastorageCreatorUtils.getStorageMountPath();

        Mockito.doReturn(storageMountPath).when(mockStorageApiService).getSharedFSSPathForRun(ID, false);

        final MvcResult mvcResult = performRequest(get(SHARED_STORAGE_URL).param(RUN_ID, ID_AS_STRING));

        Mockito.verify(mockStorageApiService).getSharedFSSPathForRun(ID, false);
        assertResponse(mvcResult, storageMountPath, DatastorageCreatorUtils.STORAGE_MOUNT_PATH_TYPE);
    }
}
