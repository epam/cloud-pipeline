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

import com.amazonaws.util.StringInputStream;
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.metadata.MetadataEntityVO;
import com.epam.pipeline.entity.metadata.FireCloudClass;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataClassDescription;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.MetadataField;
import com.epam.pipeline.entity.metadata.MetadataFilter;
import com.epam.pipeline.manager.metadata.MetadataEntityApiService;
import com.epam.pipeline.test.creator.CommonCreatorConstants;
import com.epam.pipeline.test.creator.metadata.MetadataCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_LONG_SET;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING_MAP;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(controllers = MetadataEntityController.class)
public class MetadataEntityControllerTest extends AbstractControllerTest {

    private static final String METADATA_CLASS_URL = SERVLET_PATH + "/metadataClass";
    private static final String METADATA_CLASS_REGISTER_URL = METADATA_CLASS_URL + "/register";
    private static final String METADATA_CLASSES_LOAD_URL = METADATA_CLASS_URL + "/loadAll";
    private static final String METADATA_CLASS_DELETE_URL = METADATA_CLASS_URL + "/%d/delete";
    private static final String METADATA_CLASS_UPDATE_EXTERNAL_URL = METADATA_CLASS_URL + "/%d/external";
    private static final String METADATA_ENTITY_URL = SERVLET_PATH + "/metadataEntity";
    private static final String METADATA_ENTITY_LOAD_URL = METADATA_ENTITY_URL + "/%d/load";
    private static final String METADATA_ENTITY_LOAD_EXTERNAL_URL = METADATA_ENTITY_URL + "/loadExternal";
    private static final String METADATA_ENTITY_FILTER_URL = METADATA_ENTITY_URL + "/filter";
    private static final String METADATA_ENTITY_UPLOAD_URL = METADATA_ENTITY_URL + "/upload";
    private static final String METADATA_ENTITY_GET_KEYS_URL = METADATA_ENTITY_URL + "/keys";
    private static final String METADATA_ENTITY_GET_FIELDS_URL = METADATA_ENTITY_URL + "/fields";
    private static final String METADATA_ENTITY_DELETE_URL = METADATA_ENTITY_URL + "/%d/delete";
    private static final String METADATA_ENTITY_SAVE_URL = METADATA_ENTITY_URL + "/save";
    private static final String METADATA_ENTITY_UPDATE_KEY_URL = METADATA_ENTITY_URL + "/updateKey";
    private static final String METADATA_ENTITY_DELETE_KEY_URL = METADATA_ENTITY_URL + "/%d/deleteKey";
    private static final String METADATA_ENTITY_DELETE_LIST_URL = METADATA_ENTITY_URL + "/deleteList";
    private static final String METADATA_ENTITY_DELETE_FROM_PROJECT_URL = METADATA_ENTITY_URL + "/deleteFromProject";
    private static final String METADATA_ENTITY_ENTITIES_URL = METADATA_ENTITY_URL + "/entities";
    private static final String METADATA_ENTITY_DOWNLOAD_URL = METADATA_ENTITY_URL + "/download";

    private static final String ENTITY_NAME = "name";
    private static final String EXTERNAL_CLASS_NAME = "externalClassName";
    private static final String CLASS_NAME = "className";
    private static final String FOLDER_ID = "folderId";
    private static final String STRING_ID = "id";
    private static final String PARENT_ID = "parentId";
    private static final String METADATA_CLASS = "metadataClass";
    private static final String KEY = "key";
    private static final String PROJECT_ID = "projectId";
    private static final String ENTITY_CLASS = "entityClass";
    private static final String FILE_FORMAT = "fileFormat";

    private final MetadataClass metadataClass = MetadataCreatorUtils.getMetadataClass();
    private final MetadataEntity metadataEntity = MetadataCreatorUtils.getMetadataEntity();
    private final MetadataEntityVO metadataEntityVO = MetadataCreatorUtils.getMetadataEntityVO();

    @Autowired
    private MetadataEntityApiService mockMetadataEntityApiService;

    @Test
    @WithMockUser
    public void shouldRegisterMetadataClass() {
        doReturn(metadataClass).when(mockMetadataEntityApiService).createMetadataClass(TEST_STRING);

        final MvcResult mvcResult = performRequest(post(METADATA_CLASS_REGISTER_URL)
                .params(multiValueMapOf(ENTITY_NAME, TEST_STRING)));

        verify(mockMetadataEntityApiService).createMetadataClass(TEST_STRING);
        assertResponse(mvcResult, metadataClass, MetadataCreatorUtils.METADATA_CLASS_TYPE);
    }

    @Test
    public void shouldFailRegisterMetadataClass() {
        performUnauthorizedRequest(post(METADATA_CLASS_REGISTER_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadAllMetadataClasses() {
        final List<MetadataClass> metadataClassList = Collections.singletonList(metadataClass);
        doReturn(metadataClassList).when(mockMetadataEntityApiService).loadAllMetadataClasses();

        final MvcResult mvcResult = performRequest(get(METADATA_CLASSES_LOAD_URL));

        verify(mockMetadataEntityApiService).loadAllMetadataClasses();
        assertResponse(mvcResult, metadataClassList, MetadataCreatorUtils.METADATA_CLASS_LIST_TYPE);
    }

    @Test
    public void shouldFailLoadAllMetadataClasses() {
        performUnauthorizedRequest(get(METADATA_CLASSES_LOAD_URL));
    }

    @Test
    @WithMockUser
    public void shouldDeleteMetadataClass() {
        doReturn(metadataClass).when(mockMetadataEntityApiService).deleteMetadataClass(ID);

        final MvcResult mvcResult = performRequest(delete(String.format(METADATA_CLASS_DELETE_URL, ID)));

        verify(mockMetadataEntityApiService).deleteMetadataClass(ID);
        assertResponse(mvcResult, metadataClass, MetadataCreatorUtils.METADATA_CLASS_TYPE);
    }

    @Test
    public void shouldFailDeleteMetadataClass() {
        performUnauthorizedRequest(get(METADATA_CLASSES_LOAD_URL));
    }

    @Test
    @WithMockUser
    public void shouldUpdateExternalClass() {
        doReturn(metadataClass).when(mockMetadataEntityApiService)
                .updateExternalClassName(ID, FireCloudClass.PARTICIPANT);

        final MvcResult mvcResult = performRequest(post(String.format(METADATA_CLASS_UPDATE_EXTERNAL_URL, ID))
                .params(multiValueMapOf(EXTERNAL_CLASS_NAME, FireCloudClass.PARTICIPANT)));

        verify(mockMetadataEntityApiService).updateExternalClassName(ID, FireCloudClass.PARTICIPANT);
        assertResponse(mvcResult, metadataClass, MetadataCreatorUtils.METADATA_CLASS_TYPE);
    }

    @Test
    public void shouldFailUpdateExternalClass() {
        performUnauthorizedRequest(post(String.format(METADATA_CLASS_UPDATE_EXTERNAL_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadMetadataEntity() {
        doReturn(metadataEntity).when(mockMetadataEntityApiService).loadMetadataEntity(ID);

        final MvcResult mvcResult = performRequest(get(String.format(METADATA_ENTITY_LOAD_URL, ID)));

        verify(mockMetadataEntityApiService).loadMetadataEntity(ID);
        assertResponse(mvcResult, metadataEntity, MetadataCreatorUtils.METADATA_ENTITY_TYPE);
    }

    @Test
    public void shouldFailLoadMetadataEntity() {
        performUnauthorizedRequest(get(String.format(METADATA_ENTITY_LOAD_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadExternalMetadataEntity() {
        doReturn(metadataEntity).when(mockMetadataEntityApiService).loadByExternalId(TEST_STRING, TEST_STRING, ID);

        final MvcResult mvcResult = performRequest(get(METADATA_ENTITY_LOAD_EXTERNAL_URL)
                .params(multiValueMapOf(STRING_ID, TEST_STRING,
                                        CLASS_NAME, TEST_STRING,
                                        FOLDER_ID, ID)));

        verify(mockMetadataEntityApiService).loadByExternalId(TEST_STRING, TEST_STRING, ID);
        assertResponse(mvcResult, metadataEntity, MetadataCreatorUtils.METADATA_ENTITY_TYPE);
    }

    @Test
    public void shouldFailLoadExternalMetadataEntity() {
        performUnauthorizedRequest(get(METADATA_ENTITY_LOAD_EXTERNAL_URL));
    }

    @Test
    @WithMockUser
    public void shouldFilterMetadataEntities() throws Exception {
        final PagedResult<List<MetadataEntity>> pagedResult = MetadataCreatorUtils.getPagedResult();
        final MetadataFilter metadataFilter = MetadataCreatorUtils.getMetadataFilter();
        final String content = getObjectMapper().writeValueAsString(metadataFilter);
        doReturn(pagedResult).when(mockMetadataEntityApiService).filterMetadata(metadataFilter);

        final MvcResult mvcResult = performRequest(post(METADATA_ENTITY_FILTER_URL).content(content));

        verify(mockMetadataEntityApiService).filterMetadata(metadataFilter);
        assertResponse(mvcResult, pagedResult, MetadataCreatorUtils.PAGED_RESULT_ENTITY_LIST_TYPE);
    }

    @Test
    public void shouldFailFilterMetadataEntities() {
        performUnauthorizedRequest(post(METADATA_ENTITY_FILTER_URL));
    }

    @Test
    @WithMockUser
    public void shouldUploadMetadataFromFile() {
        final List<MetadataEntity> entities = Collections.singletonList(metadataEntity);
        doReturn(entities).when(mockMetadataEntityApiService).uploadMetadataFromFile(eq(ID), any(MultipartFile.class));

        final MvcResult mvcResult = performRequest(post(METADATA_ENTITY_UPLOAD_URL).content(MULTIPART_CONTENT)
                .params(multiValueMapOf(PARENT_ID, ID)), MULTIPART_CONTENT_TYPE, EXPECTED_CONTENT_TYPE);

        verify(mockMetadataEntityApiService).uploadMetadataFromFile(eq(ID), any(MultipartFile.class));
        assertResponse(mvcResult, entities, MetadataCreatorUtils.METADATA_ENTITY_LIST_TYPE);
    }

    @Test
    public void shouldFailUploadMetadataFromFile() {
        performUnauthorizedRequest(post(METADATA_ENTITY_UPLOAD_URL));
    }

    @Test
    @WithMockUser
    public void shouldGetMetadataKeys() {
        final List<MetadataField> fields = Collections.singletonList(MetadataCreatorUtils.getMetadataField());
        doReturn(fields).when(mockMetadataEntityApiService).getMetadataKeys(ID, TEST_STRING);

        final MvcResult mvcResult = performRequest(get(METADATA_ENTITY_GET_KEYS_URL)
                .params(multiValueMapOf(FOLDER_ID, ID,
                                        METADATA_CLASS, TEST_STRING)));

        verify(mockMetadataEntityApiService).getMetadataKeys(ID, TEST_STRING);
        assertResponse(mvcResult, fields, MetadataCreatorUtils.METADATA_FIELD_LIST_TYPE);
    }

    @Test
    public void shouldFailGetMetadataKeys() {
        performUnauthorizedRequest(get(METADATA_ENTITY_GET_KEYS_URL));
    }

    @Test
    @WithMockUser
    public void shouldGetMetadataFields() {
        final List<MetadataClassDescription> list =
                Collections.singletonList(MetadataCreatorUtils.getMetadataClassDescription());
        doReturn(list).when(mockMetadataEntityApiService).getMetadataFields(ID);

        final MvcResult mvcResult = performRequest(get(METADATA_ENTITY_GET_FIELDS_URL)
                .params(multiValueMapOf(FOLDER_ID, ID)));

        verify(mockMetadataEntityApiService).getMetadataFields(ID);
        assertResponse(mvcResult, list, MetadataCreatorUtils.CLASS_DESCRIPTION_LIST_TYPE);
    }

    @Test
    public void shouldFailGetMetadataFields() {
        performUnauthorizedRequest(get(METADATA_ENTITY_GET_FIELDS_URL));
    }

    @Test
    @WithMockUser
    public void shouldDeleteMetadataEntity() {
        doReturn(metadataEntity).when(mockMetadataEntityApiService).deleteMetadataEntity(ID);

        final MvcResult mvcResult = performRequest(delete(String.format(METADATA_ENTITY_DELETE_URL, ID)));

        verify(mockMetadataEntityApiService).deleteMetadataEntity(ID);
        assertResponse(mvcResult, metadataEntity, MetadataCreatorUtils.METADATA_ENTITY_TYPE);
    }

    @Test
    public void shouldFailDeleteMetadataEntity() {
        performUnauthorizedRequest(delete(String.format(METADATA_ENTITY_DELETE_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldUpdateMetadataEntityWithId() throws Exception {
        metadataEntityVO.setEntityId(ID);
        final String content = getObjectMapper().writeValueAsString(metadataEntityVO);
        doReturn(metadataEntity).when(mockMetadataEntityApiService).updateMetadataEntity(metadataEntityVO);

        final MvcResult mvcResult = performRequest(post(METADATA_ENTITY_SAVE_URL).content(content));

        verify(mockMetadataEntityApiService).updateMetadataEntity(metadataEntityVO);
        assertResponse(mvcResult, metadataEntity, MetadataCreatorUtils.METADATA_ENTITY_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldUpdateMetadataEntityWithoutId() throws Exception {
        final String content = getObjectMapper().writeValueAsString(metadataEntityVO);
        doReturn(metadataEntity).when(mockMetadataEntityApiService).createMetadataEntity(metadataEntityVO);

        final MvcResult mvcResult = performRequest(post(METADATA_ENTITY_SAVE_URL).content(content));

        verify(mockMetadataEntityApiService).createMetadataEntity(metadataEntityVO);
        assertResponse(mvcResult, metadataEntity, MetadataCreatorUtils.METADATA_ENTITY_TYPE);
    }

    @Test
    public void shouldFailUpdateMetadataEntity() {
        performUnauthorizedRequest(post(METADATA_ENTITY_DELETE_URL, ID));
    }

    @Test
    @WithMockUser
    public void shouldUpdateMetadataEntityItemKey() throws Exception {
        final String content = getObjectMapper().writeValueAsString(metadataEntityVO);
        doReturn(metadataEntity).when(mockMetadataEntityApiService).updateMetadataItemKey(metadataEntityVO);

        final MvcResult mvcResult = performRequest(post(METADATA_ENTITY_UPDATE_KEY_URL).content(content));

        verify(mockMetadataEntityApiService).updateMetadataItemKey(metadataEntityVO);
        assertResponse(mvcResult, metadataEntity, MetadataCreatorUtils.METADATA_ENTITY_TYPE);
    }

    @Test
    public void shouldFailUpdateMetadataEntityItemKey() {
        performUnauthorizedRequest(post(METADATA_ENTITY_UPDATE_KEY_URL, ID));
    }

    @Test
    @WithMockUser
    public void shouldDeleteMetadataEntityItemKey() {
        doReturn(metadataEntity).when(mockMetadataEntityApiService).deleteMetadataItemKey(ID, TEST_STRING);

        final MvcResult mvcResult = performRequest(delete(String.format(METADATA_ENTITY_DELETE_KEY_URL, ID))
                .params(multiValueMapOf(KEY, TEST_STRING)));

        verify(mockMetadataEntityApiService).deleteMetadataItemKey(ID, TEST_STRING);
        assertResponse(mvcResult, metadataEntity, MetadataCreatorUtils.METADATA_ENTITY_TYPE);
    }

    @Test
    public void shouldFailDeleteMetadataEntityItemKey() {
        performUnauthorizedRequest(delete(String.format(METADATA_ENTITY_DELETE_KEY_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDeleteMetadataEntities() throws Exception {
        final String content = getObjectMapper().writeValueAsString(TEST_LONG_SET);
        doReturn(TEST_LONG_SET).when(mockMetadataEntityApiService).deleteMetadataEntities(TEST_LONG_SET);

        final MvcResult mvcResult = performRequest(delete(METADATA_ENTITY_DELETE_LIST_URL).content(content));

        verify(mockMetadataEntityApiService).deleteMetadataEntities(TEST_LONG_SET);
        assertResponse(mvcResult, TEST_LONG_SET, CommonCreatorConstants.LONG_SET_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailDeleteMetadataEntities() {
        performUnauthorizedRequest(delete(METADATA_ENTITY_DELETE_LIST_URL));
    }

    @Test
    @WithMockUser
    public void shouldDeleteMetadataEntitiesFromProject() {
        doNothing().when(mockMetadataEntityApiService).deleteMetadataFromProject(ID, TEST_STRING);

        final MvcResult mvcResult = performRequest(delete(METADATA_ENTITY_DELETE_FROM_PROJECT_URL)
                .params(multiValueMapOf(PROJECT_ID, ID,
                                        ENTITY_CLASS, TEST_STRING)));

        verify(mockMetadataEntityApiService).deleteMetadataFromProject(ID, TEST_STRING);
        assertResponse(mvcResult, null, MetadataCreatorUtils.RESULT_TYPE);
    }

    @Test
    public void shouldFailDeleteMetadataEntitiesFromProject() {
        performUnauthorizedRequest(delete(METADATA_ENTITY_DELETE_LIST_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadEntitiesData() throws Exception {
        final String content = getObjectMapper().writeValueAsString(TEST_LONG_SET);
        doReturn(TEST_STRING_MAP).when(mockMetadataEntityApiService).loadEntitiesData(TEST_LONG_SET);

        final MvcResult mvcResult = performRequest(post(METADATA_ENTITY_ENTITIES_URL).content(content));

        verify(mockMetadataEntityApiService).loadEntitiesData(TEST_LONG_SET);
        assertResponse(mvcResult, TEST_STRING_MAP, CommonCreatorConstants.STRING_MAP_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadEntitiesData() {
        performUnauthorizedRequest(post(METADATA_ENTITY_ENTITIES_URL));
    }

    @Test
    @WithMockUser
    public void shouldDownloadEntityAsFile() throws Exception {
        final InputStream inputStream = new StringInputStream(TEST_STRING);
        doReturn(inputStream).when(mockMetadataEntityApiService).getMetadataEntityFile(ID, TEST_STRING, TEST_STRING);

        final MvcResult mvcResult = performRequest(get(METADATA_ENTITY_DOWNLOAD_URL)
                        .params(multiValueMapOf(FOLDER_ID, ID,
                                                ENTITY_CLASS, TEST_STRING,
                                                FILE_FORMAT, TEST_STRING)),
                MediaType.APPLICATION_OCTET_STREAM_VALUE);

        verify(mockMetadataEntityApiService).getMetadataEntityFile(ID, TEST_STRING, TEST_STRING);
        Assert.assertEquals(TEST_STRING, mvcResult.getResponse().getContentAsString());
    }

    @Test
    public void shouldFailDownloadEntityAsFile() {
        performUnauthorizedRequest(get(METADATA_ENTITY_DOWNLOAD_URL));
    }
}
