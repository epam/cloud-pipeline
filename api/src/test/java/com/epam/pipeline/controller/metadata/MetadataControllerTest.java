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

package com.epam.pipeline.controller.metadata;

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.MetadataVO;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.MetadataEntryWithIssuesCount;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.metadata.MetadataApiService;
import com.epam.pipeline.test.creator.CommonCreatorConstants;
import com.epam.pipeline.test.creator.metadata.MetadataCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING_SET;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(controllers = MetadataController.class)
public class MetadataControllerTest extends AbstractControllerTest {

    private static final String METADATA_URL = SERVLET_PATH + "/metadata";
    private static final String UPDATE_KEY_URL = METADATA_URL + "/updateKey";
    private static final String UPDATE_KEYS_URL = METADATA_URL + "/updateKeys";
    private static final String UPDATE_ITEM_URL = METADATA_URL + "/update";
    private static final String LOAD_ITEMS_URL = METADATA_URL + "/load";
    private static final String KEYS_URL = METADATA_URL + "/keys";
    private static final String FIND_ENTITY_URL = METADATA_URL + "/find";
    private static final String DELETE_ITEM_URL = METADATA_URL + "/delete";
    private static final String DELETE_ITEM_KEY_URL = METADATA_URL + "/deleteKey";
    private static final String DELETE_ITEM_KEYS_URL = METADATA_URL + "/deleteKeys";
    private static final String UPLOAD_URL = METADATA_URL + "/upload";
    private static final String FOLDER_URL = METADATA_URL + "/folder";
    private static final String SEARCH_URL = METADATA_URL + "/search";

    private static final String ENTITY_CLASS = "entityClass";
    private static final String ACL_CLASS = "class";
    private static final String ENTITY_NAME = "entityName";
    private static final String KEY = "key";
    private static final String VALUE = "value";
    private static final String ENTITY_ID = "id";
    private static final String MERGE_WITH_EXISTING_METADATA = "merge";
    private static final String PARENT_FOLDER_ID = "parentFolderId";

    private final MetadataEntry metadataEntry = MetadataCreatorUtils.getMetadataEntry();
    private final MetadataVO metadataVO = MetadataCreatorUtils.getMetadataVO();
    private final EntityVO entityVO = MetadataCreatorUtils.getEntityVO();
    private final AclClass aclClass = AclClass.DATA_STORAGE;
    private final List<EntityVO> entityVOList = Collections.singletonList(entityVO);

    @Autowired
    private MetadataApiService mockMetadataApiService;

    @Test
    @WithMockUser
    public void shouldUpdateMetadataItemKey() throws Exception {
        final String content = getObjectMapper().writeValueAsString(metadataVO);
        doReturn(metadataEntry).when(mockMetadataApiService).updateMetadataItemKey(metadataVO);

        final MvcResult mvcResult = performRequest(post(UPDATE_KEY_URL).content(content));

        verify(mockMetadataApiService).updateMetadataItemKey(metadataVO);
        assertResponse(mvcResult, metadataEntry, MetadataCreatorUtils.METADATA_ENTRY_TYPE);
    }

    @Test
    public void shouldFailUpdateMetadataItemKey() {
        performUnauthorizedRequest(post(UPDATE_KEY_URL));
    }

    @Test
    @WithMockUser
    public void shouldUpdateMetadataItemKeys() throws Exception {
        final String content = getObjectMapper().writeValueAsString(metadataVO);
        doReturn(metadataEntry).when(mockMetadataApiService).updateMetadataItemKeys(metadataVO);

        final MvcResult mvcResult = performRequest(post(UPDATE_KEYS_URL).content(content));

        verify(mockMetadataApiService).updateMetadataItemKeys(metadataVO);
        assertResponse(mvcResult, metadataEntry, MetadataCreatorUtils.METADATA_ENTRY_TYPE);
    }

    @Test
    public void shouldFailUpdateMetadataItemKeys() {
        performUnauthorizedRequest(post(UPDATE_KEYS_URL));
    }

    @Test
    @WithMockUser
    public void shouldUpdateMetadataItem() throws Exception {
        final String content = getObjectMapper().writeValueAsString(metadataVO);
        doReturn(metadataEntry).when(mockMetadataApiService).updateMetadataItem(metadataVO);

        final MvcResult mvcResult = performRequest(post(UPDATE_ITEM_URL).content(content));

        verify(mockMetadataApiService).updateMetadataItem(metadataVO);
        assertResponse(mvcResult, metadataEntry, MetadataCreatorUtils.METADATA_ENTRY_TYPE);
    }

    @Test
    public void shouldFailUpdateMetadataItem() {
        performUnauthorizedRequest(post(UPDATE_ITEM_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadMetadataItems() throws Exception {
        final List<MetadataEntry> metadataEntries = Collections.singletonList(metadataEntry);
        final String content = getObjectMapper().writeValueAsString(entityVOList);
        doReturn(metadataEntries).when(mockMetadataApiService).listMetadataItems(entityVOList);

        final MvcResult mvcResult = performRequest(post(LOAD_ITEMS_URL).content(content));

        verify(mockMetadataApiService).listMetadataItems(entityVOList);
        assertResponse(mvcResult, metadataEntries, MetadataCreatorUtils.METADATA_ENTRY_LIST_TYPE);
    }

    @Test
    public void shouldFailLoadMetadataItems() {
        performUnauthorizedRequest(post(LOAD_ITEMS_URL));
    }

    @Test
    @WithMockUser
    public void shouldGetMetadataKeys() {
        doReturn(TEST_STRING_SET).when(mockMetadataApiService).getMetadataKeys(aclClass);

        final MvcResult mvcResult = performRequest(get(KEYS_URL).params(multiValueMapOf(ENTITY_CLASS, aclClass)));

        verify(mockMetadataApiService).getMetadataKeys(aclClass);
        assertResponse(mvcResult, TEST_STRING_SET, CommonCreatorConstants.STRING_SET_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailGetMetadataKeys() {
        performUnauthorizedRequest(get(KEYS_URL));
    }

    @Test
    @WithMockUser
    public void shouldFindMetadataEntityIdByName() {
        doReturn(metadataEntry).when(mockMetadataApiService).findMetadataEntityIdByName(TEST_STRING, aclClass);

        final MvcResult mvcResult = performRequest(get(FIND_ENTITY_URL)
                .params(multiValueMapOf(ENTITY_NAME, TEST_STRING,
                                        ENTITY_CLASS, aclClass)));

        verify(mockMetadataApiService).findMetadataEntityIdByName(TEST_STRING, aclClass);
        assertResponse(mvcResult, metadataEntry, MetadataCreatorUtils.METADATA_ENTRY_TYPE);
    }

    @Test
    public void shouldFailFindMetadataEntityIdByName() {
        performUnauthorizedRequest(get(FIND_ENTITY_URL));
    }

    @Test
    @WithMockUser
    public void shouldDeleteMetadataItem() throws Exception {
        final String content = getObjectMapper().writeValueAsString(entityVO);
        doReturn(metadataEntry).when(mockMetadataApiService).deleteMetadataItem(entityVO);

        final MvcResult mvcResult = performRequest(delete(DELETE_ITEM_URL).content(content));

        verify(mockMetadataApiService).deleteMetadataItem(entityVO);
        assertResponse(mvcResult, metadataEntry, MetadataCreatorUtils.METADATA_ENTRY_TYPE);
    }

    @Test
    public void shouldFailDeleteMetadataItem() {
        performUnauthorizedRequest(delete(DELETE_ITEM_URL));
    }

    @Test
    @WithMockUser
    public void shouldDeleteMetadataItemKey() throws Exception {
        final String content = getObjectMapper().writeValueAsString(entityVO);
        doReturn(metadataEntry).when(mockMetadataApiService).deleteMetadataItemKey(entityVO, TEST_STRING);

        final MvcResult mvcResult = performRequest(delete(DELETE_ITEM_KEY_URL).content(content)
                .params(multiValueMapOf(KEY, TEST_STRING)));

        verify(mockMetadataApiService).deleteMetadataItemKey(entityVO, TEST_STRING);
        assertResponse(mvcResult, metadataEntry, MetadataCreatorUtils.METADATA_ENTRY_TYPE);
    }

    @Test
    public void shouldFailDeleteMetadataItemKey() {
        performUnauthorizedRequest(delete(DELETE_ITEM_KEY_URL));
    }

    @Test
    @WithMockUser
    public void shouldDeleteMetadataItemKeys() throws Exception {
        final String content = getObjectMapper().writeValueAsString(metadataVO);
        doReturn(metadataEntry).when(mockMetadataApiService).deleteMetadataItemKeys(metadataVO);

        final MvcResult mvcResult = performRequest(delete(DELETE_ITEM_KEYS_URL).content(content));

        verify(mockMetadataApiService).deleteMetadataItemKeys(metadataVO);
        assertResponse(mvcResult, metadataEntry, MetadataCreatorUtils.METADATA_ENTRY_TYPE);
    }

    @Test
    public void shouldFailDeleteMetadataItemKeys() {
        performUnauthorizedRequest(delete(DELETE_ITEM_KEYS_URL));
    }

    @Test
    @WithMockUser
    public void shouldUploadMetadataFromFile() throws Exception {
        doReturn(metadataEntry).when(mockMetadataApiService)
                .uploadMetadataFromFile(eq(new EntityVO(ID, aclClass)), any(), eq(true));

        final MvcResult mvcResult = performRequest(post(UPLOAD_URL).content(MULTIPART_CONTENT)
                        .params(multiValueMapOf(ENTITY_ID, ID,
                                                ACL_CLASS, aclClass,
                                                MERGE_WITH_EXISTING_METADATA, true)),
                                                MULTIPART_CONTENT_TYPE, EXPECTED_CONTENT_TYPE);
        final ArgumentCaptor<MultipartFile> multipartFileCaptor = ArgumentCaptor.forClass(MultipartFile.class);

        verify(mockMetadataApiService).uploadMetadataFromFile(any(EntityVO.class),
                multipartFileCaptor.capture(), eq(true));
        assertRequestFile(multipartFileCaptor.getValue(), MULTIPART_CONTENT_FILE_NAME,
                                                          MULTIPART_CONTENT_FILE_CONTENT.getBytes());
        assertResponse(mvcResult, metadataEntry, MetadataCreatorUtils.METADATA_ENTRY_TYPE);
    }

    @Test
    public void shouldFailUploadMetadataFromFile() {
        performUnauthorizedRequest(post(UPLOAD_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadEntitiesMetadataFromFolder() {
        final List<MetadataEntryWithIssuesCount> list = MetadataCreatorUtils.getListOfEntryWithIssuesCount();
        doReturn(list).when(mockMetadataApiService).loadEntitiesMetadataFromFolder(ID);

        final MvcResult mvcResult = performRequest(get(FOLDER_URL)
                .params(multiValueMapOf(PARENT_FOLDER_ID, ID)));

        verify(mockMetadataApiService).loadEntitiesMetadataFromFolder(ID);
        assertResponse(mvcResult, list, MetadataCreatorUtils.METADATA_ENTRY_WITH_ISSUES_COUNT_LIST_TYPE);
    }

    @Test
    public void shouldFailLoadEntitiesMetadataFromFolder() {
        performUnauthorizedRequest(get(FOLDER_URL));
    }

    @Test
    @WithMockUser
    public void shouldSearchMetadataByClassAndKeyValue() {
        doReturn(entityVOList).when(mockMetadataApiService)
                .searchMetadataByClassAndKeyValue(aclClass, TEST_STRING, TEST_STRING);

        final MvcResult mvcResult = performRequest(get(SEARCH_URL)
                .params(multiValueMapOf(ENTITY_CLASS, aclClass,
                                        KEY, TEST_STRING,
                                        VALUE, TEST_STRING)));

        verify(mockMetadataApiService).searchMetadataByClassAndKeyValue(aclClass, TEST_STRING, TEST_STRING);
        assertResponse(mvcResult, entityVOList, MetadataCreatorUtils.ENTITY_VO_LIST_TYPE);
    }

    @Test
    public void shouldFailSearchMetadataByClassAndKeyValue() {
        performUnauthorizedRequest(get(SEARCH_URL));
    }
}
