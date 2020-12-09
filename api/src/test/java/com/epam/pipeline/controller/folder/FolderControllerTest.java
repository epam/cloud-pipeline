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

package com.epam.pipeline.controller.folder;

import com.epam.pipeline.entity.metadata.FolderWithMetadata;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.acl.metadata.MetadataEntityApiService;
import com.epam.pipeline.manager.pipeline.FolderApiService;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.folder.FolderCreatorUtils.FOLDER_TYPE;
import static com.epam.pipeline.test.creator.folder.FolderCreatorUtils.FOLDER_WITH_METADATA_TYPE;
import static com.epam.pipeline.test.creator.folder.FolderCreatorUtils.getFolder;
import static com.epam.pipeline.test.creator.folder.FolderCreatorUtils.getFolderWithMetadata;
import static com.epam.pipeline.test.creator.metadata.MetadataCreatorUtils.METADATA_ENTITY_LIST_TYPE;
import static com.epam.pipeline.test.creator.metadata.MetadataCreatorUtils.getMetadataEntity;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class FolderControllerTest extends AbstractControllerTest {

    private static final String FOLDER_URL = SERVLET_PATH + "/folder";
    private static final String REGISTER_FOLDER_URL = FOLDER_URL + "/register";
    private static final String UPDATE_FOLDER_URL = FOLDER_URL + "/update";
    private static final String PROJECT_URL = FOLDER_URL + "/project";
    private static final String PROJECTS_URL = FOLDER_URL + "/projects";
    private static final String FOLDER_TREE_URL = FOLDER_URL + "/loadTree";
    private static final String BY_ID_URL = FOLDER_URL + "/%d";
    private static final String LOAD_FOLDER = BY_ID_URL + "/load";
    private static final String FIND_FOLDER_URL = FOLDER_URL + "/find";
    private static final String FOLDER_METADATA_URL = BY_ID_URL + "/metadata";
    private static final String DELETE_FOLDER_URL = BY_ID_URL + "/delete";
    private static final String CLONE_FOLDER_URL = BY_ID_URL + "/clone";
    private static final String LOCK_FOLDER_URL = BY_ID_URL + "/lock";
    private static final String UNLOCK_FOLDER_URL = BY_ID_URL + "/unlock";
    private final Folder folder = getFolder();
    private final FolderWithMetadata folderWithMetadata = getFolderWithMetadata();
    private final List<MetadataEntity> metadataEntities = Collections.singletonList(getMetadataEntity());

    @Autowired
    private FolderApiService mockFolderApiService;

    @Autowired
    private MetadataEntityApiService mockMetadataEntityApiService;

    @Test
    public void shouldFailRegisterFolderForUnauthorizedUser() {
        performUnauthorizedRequest(post(REGISTER_FOLDER_URL));
    }

    @Test
    @WithMockUser
    public void shouldRegisterFolderFromTemplate() throws Exception {
        final String content = getObjectMapper().writeValueAsString(folder);
        doReturn(folder).when(mockFolderApiService).createFromTemplate(folder, TEST_STRING);

        final MvcResult mvcResult = performRequest(post(REGISTER_FOLDER_URL)
                .param("templateName", TEST_STRING)
                .content(content));

        verify(mockFolderApiService).createFromTemplate(folder, TEST_STRING);
        assertResponse(mvcResult, folder, FOLDER_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldRegisterFolder() throws Exception {
        final String content = getObjectMapper().writeValueAsString(folder);
        doReturn(folder).when(mockFolderApiService).create(folder);

        final MvcResult mvcResult = performRequest(post(REGISTER_FOLDER_URL).content(content));

        verify(mockFolderApiService).create(folder);
        assertResponse(mvcResult, folder, FOLDER_TYPE);
    }

    @Test
    public void shouldFailUpdateFolderForUnauthorizedUser() {
        performUnauthorizedRequest(post(UPDATE_FOLDER_URL));
    }

    @Test
    @WithMockUser
    public void shouldUpdateFolder() throws Exception {
        final String content = getObjectMapper().writeValueAsString(folder);
        doReturn(folder).when(mockFolderApiService).update(folder);

        final MvcResult mvcResult = performRequest(post(UPDATE_FOLDER_URL).content(content));

        verify(mockFolderApiService).update(folder);
        assertResponse(mvcResult, folder, FOLDER_TYPE);
    }

    @Test
    public void shouldFailLoadProjectForUnauthorizedUser() {
        performUnauthorizedRequest(get(PROJECT_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadProject() {
        doReturn(folderWithMetadata).when(mockFolderApiService).getProject(ID, AclClass.FOLDER);

        final MvcResult mvcResult = performRequest(get(PROJECT_URL)
                .params(multiValueMapOf("id", ID,
                                        "aclClass", AclClass.FOLDER)));

        verify(mockFolderApiService).getProject(ID, AclClass.FOLDER);
        assertResponse(mvcResult, folderWithMetadata, FOLDER_WITH_METADATA_TYPE);
    }

    @Test
    public void shouldFailLoadProjectsForUnauthorizedUser() {
        performUnauthorizedRequest(get(PROJECTS_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadProjects() {
        doReturn(folder).when(mockFolderApiService).loadProjects();

        final MvcResult mvcResult = performRequest(get(PROJECTS_URL));

        verify(mockFolderApiService).loadProjects();
        assertResponse(mvcResult, folder, FOLDER_TYPE);
    }

    @Test
    public void shouldFailLoadTreeForUnauthorizedUser() {
        performUnauthorizedRequest(get(FOLDER_TREE_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadTree() {
        doReturn(folder).when(mockFolderApiService).loadTree();

        final MvcResult mvcResult = performRequest(get(FOLDER_TREE_URL));

        verify(mockFolderApiService).loadTree();
        assertResponse(mvcResult, folder, FOLDER_TYPE);
    }

    @Test
    public void shouldFailLoadFolderForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(LOAD_FOLDER, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadFolder() {
        doReturn(folder).when(mockFolderApiService).load(ID);

        final MvcResult mvcResult = performRequest(get(String.format(LOAD_FOLDER, ID)));

        verify(mockFolderApiService).load(ID);
        assertResponse(mvcResult, folder, FOLDER_TYPE);
    }

    @Test
    public void shouldFailFindFolderForUnauthorizedUser() {
        performUnauthorizedRequest(get(FIND_FOLDER_URL));
    }

    @Test
    @WithMockUser
    public void shouldFindFolder() {
        doReturn(folder).when(mockFolderApiService).loadByIdOrPath(TEST_STRING);

        final MvcResult mvcResult = performRequest(get(FIND_FOLDER_URL).param("id", TEST_STRING));

        verify(mockFolderApiService).loadByIdOrPath(TEST_STRING);
        assertResponse(mvcResult, folder, FOLDER_TYPE);
    }

    @Test
    public void shouldFailLoadFolderMetadataEntitiesByClassForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(FOLDER_METADATA_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadFolderMetadataEntitiesByClass() {
        doReturn(metadataEntities).when(mockMetadataEntityApiService).loadMetadataEntityByClass(ID, TEST_STRING);

        final MvcResult mvcResult = performRequest(get(String.format(FOLDER_METADATA_URL, ID))
                .param("class", TEST_STRING));

        verify(mockMetadataEntityApiService).loadMetadataEntityByClass(ID, TEST_STRING);
        assertResponse(mvcResult, metadataEntities, METADATA_ENTITY_LIST_TYPE);
    }

    @Test
    public void shouldFailDeleteFolderForUnauthorizedUser() {
        performUnauthorizedRequest(delete(String.format(DELETE_FOLDER_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDeleteFolderForce() {
        doReturn(folder).when(mockFolderApiService).deleteForce(ID);

        final MvcResult mvcResult = performRequest(delete(String.format(DELETE_FOLDER_URL, ID)).param("force", "true"));

        verify(mockFolderApiService).deleteForce(ID);
        assertResponse(mvcResult, folder, FOLDER_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldDeleteFolder() {
        doReturn(folder).when(mockFolderApiService).delete(ID);

        final MvcResult mvcResult = performRequest(delete(String.format(DELETE_FOLDER_URL, ID)));

        verify(mockFolderApiService).delete(ID);
        assertResponse(mvcResult, folder, FOLDER_TYPE);
    }

    @Test
    public void shouldFailCloneFolderForUnauthorizedUser() {
        performUnauthorizedRequest(post(String.format(CLONE_FOLDER_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldCloneFolderWithParentId() {
        doReturn(folder).when(mockFolderApiService).cloneFolder(ID, ID_2, TEST_STRING);

        final MvcResult mvcResult = performRequest(post(String.format(CLONE_FOLDER_URL, ID))
                .params(multiValueMapOf("parentId", ID_2,
                                        "name", TEST_STRING)));

        verify(mockFolderApiService).cloneFolder(ID, ID_2, TEST_STRING);
        assertResponse(mvcResult, folder, FOLDER_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldCloneFolderWithoutParentId() {
        doReturn(folder).when(mockFolderApiService).cloneFolder(ID, ID, TEST_STRING);

        final MvcResult mvcResult = performRequest(post(String.format(CLONE_FOLDER_URL, ID))
                .param("name", TEST_STRING));

        verify(mockFolderApiService).cloneFolder(ID, ID, TEST_STRING);
        assertResponse(mvcResult, folder, FOLDER_TYPE);
    }

    @Test
    public void shouldFailLockFolderForUnauthorizedUser() {
        performUnauthorizedRequest(post(String.format(LOCK_FOLDER_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLockFolder() {
        doReturn(folder).when(mockFolderApiService).lockFolder(ID);

        final MvcResult mvcResult = performRequest(post(String.format(LOCK_FOLDER_URL, ID)));

        verify(mockFolderApiService).lockFolder(ID);
        assertResponse(mvcResult, folder, FOLDER_TYPE);
    }

    @Test
    public void shouldFailUnlockFolderForUnauthorizedUser() {
        performUnauthorizedRequest(post(String.format(UNLOCK_FOLDER_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldUnlockFolder() {
        doReturn(folder).when(mockFolderApiService).unlockFolder(ID);

        final MvcResult mvcResult = performRequest(post(String.format(UNLOCK_FOLDER_URL, ID)));

        verify(mockFolderApiService).unlockFolder(ID);
        assertResponse(mvcResult, folder, FOLDER_TYPE);
    }
}
