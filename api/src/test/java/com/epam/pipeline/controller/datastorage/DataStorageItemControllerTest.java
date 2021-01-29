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
import com.epam.pipeline.controller.vo.GenerateDownloadUrlVO;
import com.epam.pipeline.controller.vo.UploadFileMetadata;
import com.epam.pipeline.controller.vo.data.storage.UpdateDataStorageItemVO;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagDeleteBulkRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagBulkLoadRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagDeleteRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagInsertRequest;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTagInsertBulkRequest;
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
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.INTEGER_TYPE;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.OBJECT_TYPE;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.STRING_MAP_INSTANCE_TYPE;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils.DATA_STORAGE_TAG_LIST_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class DataStorageItemControllerTest extends AbstractDataStorageControllerTest {

    @Test
    public void shouldFailGetDataStorageItemsForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(DATASTORAGE_ITEMS_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGetDataStorageFolder() {
        final DataStorageListing dataStorageListing = DatastorageCreatorUtils.getDataStorageListing();
        final List<DataStorageFolder> folders = Collections.singletonList(folder);
        dataStorageListing.setResults(Collections.singletonList(folder));
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PATH, TEST);
        params.add(SHOW_VERSION, FALSE_AS_STRING);
        Mockito.doReturn(dataStorageListing)
                .when(mockStorageApiService).getDataStorageItems(ID, TEST, false, null, null);

        final MvcResult mvcResult = performRequest(get(String.format(DATASTORAGE_ITEMS_URL, ID)).params(params));

        Mockito.verify(mockStorageApiService).getDataStorageItems(ID, TEST, false, null, null);
        assertResponse(mvcResult, folders, DatastorageCreatorUtils.DATA_STORAGE_FOLDER_LIST_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldGetDataStorageOwnerFile() {
        final DataStorageListing dataStorageListing = DatastorageCreatorUtils.getDataStorageListing();
        final List<DataStorageFile> files = Collections.singletonList(file);
        dataStorageListing.setResults(Collections.singletonList(file));
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PATH, TEST);
        params.add(SHOW_VERSION, TRUE_AS_STRING);
        Mockito.doReturn(dataStorageListing)
                .when(mockStorageApiService).getDataStorageItemsOwner(ID, TEST, true, null, null);

        final MvcResult mvcResult = performRequest(get(String.format(DATASTORAGE_ITEMS_URL, ID)).params(params));

        Mockito.verify(mockStorageApiService).getDataStorageItemsOwner(ID, TEST, true, null, null);
        assertResponse(mvcResult, files, DatastorageCreatorUtils.DATA_STORAGE_FILE_LIST_TYPE);
    }

    @Test
    public void shouldFailGetDataStorageItemsListingForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(DATASTORAGE_LISTING_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGetDataStorageItems() {
        final DataStorageListing dataStorageListing = DatastorageCreatorUtils.getDataStorageListing();
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PATH, TEST);
        params.add(SHOW_VERSION, FALSE_AS_STRING);
        params.add(PAGE_SIZE, ID_AS_STRING);
        params.add(MARKER, TEST);
        Mockito.doReturn(dataStorageListing)
                .when(mockStorageApiService).getDataStorageItems(ID, TEST, false, TEST_INT, TEST);

        final MvcResult mvcResult = performRequest(get(String.format(DATASTORAGE_LISTING_URL, ID)).params(params));

        Mockito.verify(mockStorageApiService).getDataStorageItems(ID, TEST, false, TEST_INT, TEST);
        assertResponse(mvcResult, dataStorageListing, DatastorageCreatorUtils.DATA_STORAGE_LISTING_TYPE);
    }

    @Test
    public void shouldFailUpdateDataStorageItemsForUnauthorizedUser() {
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
    public void shouldFailUploadStorageItemForUnauthorizedUser() {
        performUnauthorizedRequest(post(String.format(DATASTORAGE_ITEMS_CONTENT, ID)));
    }

    @Test
    @WithMockUser
    public void shouldUploadStorageItem() throws Exception {
        Mockito.doReturn(file).when(mockStorageApiService)
                .createDataStorageFile(ID, TEST, TEST.getBytes(Charset.defaultCharset()));
        final String content = getObjectMapper().writeValueAsString(TEST);

        final MvcResult mvcResult = performRequest(post(String.format(DATASTORAGE_ITEMS_CONTENT, ID))
                .param(PATH, TEST).content(content));

        Mockito.verify(mockStorageApiService).createDataStorageFile(ID, TEST, TEST.getBytes(Charset.defaultCharset()));
        assertResponse(mvcResult, file, DatastorageCreatorUtils.DATA_STORAGE_FILE_TYPE);
    }

    @Test
    public void shouldFailDeleteDataStorageItemForUnauthorizedUser() {
        performUnauthorizedRequest(delete(String.format(DATASTORAGE_ITEMS_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDeleteDataStorageItems() throws Exception {
        final List<UpdateDataStorageItemVO> updateList = Collections.singletonList(update);
        final String content = getObjectMapper().writeValueAsString(updateList);
        Mockito.doReturn(TEST_INT).when(mockStorageApiService).deleteDataStorageItems(ID, updateList, false);

        final MvcResult mvcResult = performRequest(delete(String.format(DATASTORAGE_ITEMS_URL, ID))
                .param(TOTALLY, FALSE_AS_STRING).content(content));

        Mockito.verify(mockStorageApiService).deleteDataStorageItems(ID, updateList, false);
        assertResponse(mvcResult, TEST_INT, INTEGER_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldDeleteDataStorageItemsOwner() throws Exception {
        final List<UpdateDataStorageItemVO> updateList = Collections.singletonList(update);
        final String content = getObjectMapper().writeValueAsString(updateList);
        Mockito.doReturn(TEST_INT).when(mockStorageApiService).deleteDataStorageItemsOwner(ID, updateList, true);

        final MvcResult mvcResult = performRequest(delete(String.format(DATASTORAGE_ITEMS_URL, ID))
                .param(TOTALLY, TRUE_AS_STRING).content(content));

        Mockito.verify(mockStorageApiService).deleteDataStorageItemsOwner(ID, updateList, true);
        assertResponse(mvcResult, TEST_INT, INTEGER_TYPE);
    }

    @Test
    public void shouldFailGenerateItemUrlAndRedirectForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(DOWNLOAD_REDIRECT_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGenerateItemUrlAndRedirect() {
        final DataStorageDownloadFileUrl url = DatastorageCreatorUtils.getDataStorageDownloadFileUrl();
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PATH, TEST_PATH);
        params.add(CONTENT_DISPOSITION_PARAM, CONTENT_DISPOSITION.name());
        Mockito.doReturn(url).when(mockStorageApiService)
                .generateDataStorageItemUrl(ID, TEST_PATH, null, CONTENT_DISPOSITION);

        final MvcResult mvcResult = performRedirectedRequest(
                get(String.format(DOWNLOAD_REDIRECT_URL, ID)).params(params), TEST
        );

        Mockito.verify(mockStorageApiService).generateDataStorageItemUrl(ID, TEST_PATH, null, CONTENT_DISPOSITION);
        final String actual = mvcResult.getResponse().getRedirectedUrl();
        assertThat(actual).isEqualTo(TEST);
    }

    @Test
    @WithMockUser
    public void shouldGenerateItemUrlAndRedirectOwner() {
        final DataStorageDownloadFileUrl url = DatastorageCreatorUtils.getDataStorageDownloadFileUrl();
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PATH, TEST_PATH);
        params.add(VERSION, TEST);
        params.add(CONTENT_DISPOSITION_PARAM, CONTENT_DISPOSITION.name());
        Mockito.doReturn(url).when(mockStorageApiService)
                .generateDataStorageItemUrlOwner(ID, TEST_PATH, TEST, CONTENT_DISPOSITION);

        final MvcResult mvcResult = performRedirectedRequest(get(String.format(DOWNLOAD_REDIRECT_URL, ID))
                .params(params), TEST);

        Mockito.verify(mockStorageApiService).generateDataStorageItemUrlOwner(ID, TEST_PATH, TEST, CONTENT_DISPOSITION);
        final String actual = mvcResult.getResponse().getRedirectedUrl();
        assertThat(actual).isEqualTo(TEST);
    }

    @Test
    public void shouldFailGenerateDataStorageItemUrl() {
        performUnauthorizedRequest(get(String.format(GENERATE_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGenerateDataStorageItemUrlOwner() {
        final DataStorageDownloadFileUrl url = DatastorageCreatorUtils.getDataStorageDownloadFileUrl();
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PATH, TEST_PATH);
        params.add(VERSION, TEST);
        params.add(CONTENT_DISPOSITION_PARAM, CONTENT_DISPOSITION.name());
        Mockito.doReturn(url).when(mockStorageApiService)
                .generateDataStorageItemUrlOwner(ID, TEST_PATH, TEST, CONTENT_DISPOSITION);

        final MvcResult mvcResult = performRequest(get(String.format(GENERATE_URL, ID)).params(params));

        Mockito.verify(mockStorageApiService)
                .generateDataStorageItemUrlOwner(ID, TEST_PATH, TEST, CONTENT_DISPOSITION);

        assertResponse(mvcResult, url, DatastorageCreatorUtils.DOWNLOAD_FILE_URL_TYPE);
    }

    @Test
    public void shouldFailGenerateDataStorageItemUploadUrlForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(GENERATE_UPLOAD_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGenerateDataStorageItemUploadUrl() {
        final DataStorageDownloadFileUrl url = DatastorageCreatorUtils.getDataStorageDownloadFileUrl();
        Mockito.doReturn(url).when(mockStorageApiService).generateDataStorageItemUploadUrl(ID, TEST);

        final MvcResult mvcResult = performRequest(get(String.format(GENERATE_UPLOAD_URL, ID)).param(PATH, TEST));

        Mockito.verify(mockStorageApiService).generateDataStorageItemUploadUrl(ID, TEST);
        assertResponse(mvcResult, url, DatastorageCreatorUtils.DOWNLOAD_FILE_URL_TYPE);
    }

    @Test
    public void shouldFailGetDataStorageItemContentForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(CONTENT_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGetDataStorageItemContent() {
        final DataStorageItemContent dataStorageItemContent = DatastorageCreatorUtils.getDataStorageItemContent();
        Mockito.doReturn(dataStorageItemContent).when(mockStorageApiService)
                .getDataStorageItemContent(ID, TEST, null);

        final MvcResult mvcResult = performRequest(get(String.format(CONTENT_URL, ID)).param(PATH, TEST));

        Mockito.verify(mockStorageApiService).getDataStorageItemContent(ID, TEST, null);
        assertResponse(mvcResult, dataStorageItemContent, DatastorageCreatorUtils.ITEM_CONTENT_URL);
    }

    @Test
    @WithMockUser
    public void shouldGetDataStorageItemContentOwner() {
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
    public void shouldFailGenerateDataStorageItemsUrlsForUnauthorizedUser() {
        performUnauthorizedRequest(post(String.format(GENERATE_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGenerateDataStorageItemsUrls() throws Exception {
        final DataStorageDownloadFileUrl url = DatastorageCreatorUtils.getDataStorageDownloadFileUrl();
        final List<DataStorageDownloadFileUrl> urls = Collections.singletonList(url);
        final GenerateDownloadUrlVO generateDownloadUrlVO = DatastorageCreatorUtils.getGenerateDownloadUrlVO();
        final String content = getObjectMapper().writeValueAsString(generateDownloadUrlVO);
        Mockito.doReturn(urls).when(mockStorageApiService)
                .generateDataStorageItemUrl(ID, generateDownloadUrlVO.getPaths(),
                        generateDownloadUrlVO.getPermissions(), generateDownloadUrlVO.getHours());

        final MvcResult mvcResult = performRequest(post(String.format(GENERATE_URL, ID)).content(content));

        Mockito.verify(mockStorageApiService)
                .generateDataStorageItemUrl(ID, generateDownloadUrlVO.getPaths(),
                        generateDownloadUrlVO.getPermissions(), generateDownloadUrlVO.getHours());
        assertResponse(mvcResult, urls, DatastorageCreatorUtils.DOWNLOAD_FILE_URL_LIST_TYPE);
    }

    @Test
    public void shouldFailGenerateDataStorageItemsUploadUrlsForUnauthorizedUser() {
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
    public void shouldFailRestoreFileVersionForUnauthorizedUser() {
        performUnauthorizedRequest(post(String.format(RESTORE_VERSION_URL, ID)));
    }

    @Test
    public void shouldFailGetDataStorageItemsWithTagsForUnauthorizedUser() {
        performUnauthorizedRequest(get(TAGS_LIST_URL, ID));
    }

    @Test
    @WithMockUser
    public void shouldGetDataStorageFileWithTags() {
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
    public void shouldGetDataStorageFileOwnerWithTags() {
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
    public void shouldGetDataStorageFolderWithTags() {
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
    public void shouldGetDataStorageFolderOwnerWithTags() {
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
        assertResponse(mvcResult, TAGS, STRING_MAP_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailBulkUpsertTagsForUnauthorizedUser() {
        performUnauthorizedRequest(post(String.format(TAGS_BULK_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldBulkInsertTags() {
        final List<DataStorageTagInsertRequest> tagRequests = Collections.singletonList(
                new DataStorageTagInsertRequest(TEST_STRING, TEST_STRING, TEST_STRING, TEST_STRING));
        final DataStorageTagInsertBulkRequest request = new DataStorageTagInsertBulkRequest(tagRequests);
        Mockito.doReturn(OBJECT_TAGS).when(mockStorageApiService).bulkInsertDataStorageObjectTags(ID, request);

        final MvcResult mvcResult = performRequest(post(String.format(TAGS_BULK_URL, ID)).content(stringOf(request)));

        Mockito.verify(mockStorageApiService).bulkInsertDataStorageObjectTags(ID, request);
        assertResponse(mvcResult, null, OBJECT_TYPE);
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
        assertResponse(mvcResult, TAGS, STRING_MAP_INSTANCE_TYPE);
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
        assertResponse(mvcResult, TAGS, STRING_MAP_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailBulkLoadTagsForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(TAGS_BULK_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldBulkLoadTags() {
        final DataStorageTagBulkLoadRequest request =
                new DataStorageTagBulkLoadRequest(Collections.singletonList(TEST));
        Mockito.doReturn(OBJECT_TAGS).when(mockStorageApiService).bulkLoadDataStorageObjectTags(ID, request);

        final MvcResult mvcResult = performRequest(get(String.format(TAGS_BULK_URL, ID)).content(stringOf(request)));

        Mockito.verify(mockStorageApiService).bulkLoadDataStorageObjectTags(ID, request);
        assertResponse(mvcResult, OBJECT_TAGS, DATA_STORAGE_TAG_LIST_TYPE);
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
        Mockito.doReturn(TAGS).when(mockStorageApiService).deleteDataStorageObjectTags(ID, TEST, TEST, setTags);

        final MvcResult mvcResult = performRequest(delete(String.format(TAGS_URL, ID)).params(params).content(content));

        Mockito.verify(mockStorageApiService).deleteDataStorageObjectTags(ID, TEST, TEST, setTags);
        assertResponse(mvcResult, TAGS, STRING_MAP_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailBulkDeleteTagsForUnauthorizedUser() {
        performUnauthorizedRequest(delete(String.format(TAGS_BULK_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldBulkDeleteTags() {
        final DataStorageTagDeleteBulkRequest request = new DataStorageTagDeleteBulkRequest(
                        Collections.singletonList(new DataStorageTagDeleteRequest(TEST, TEST)));

        final MvcResult mvcResult = performRequest(delete(String.format(TAGS_BULK_URL, ID)).content(stringOf(request)));

        Mockito.verify(mockStorageApiService).bulkDeleteDataStorageObjectTags(ID, request);
        assertResponse(mvcResult, null, OBJECT_TYPE);
    }
}
