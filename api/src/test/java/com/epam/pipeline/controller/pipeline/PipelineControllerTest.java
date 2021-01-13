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

package com.epam.pipeline.controller.pipeline;

import com.epam.pipeline.acl.pipeline.PipelineApiService;
import com.epam.pipeline.controller.vo.CheckRepositoryVO;
import com.epam.pipeline.controller.vo.GenerateFileVO;
import com.epam.pipeline.controller.vo.InstanceOfferParametersVO;
import com.epam.pipeline.controller.vo.PipelineSourceItemVO;
import com.epam.pipeline.controller.vo.PipelineSourceItemsVO;
import com.epam.pipeline.controller.vo.PipelineVO;
import com.epam.pipeline.controller.vo.PipelinesWithPermissionsVO;
import com.epam.pipeline.controller.vo.RegisterPipelineVersionVO;
import com.epam.pipeline.controller.vo.TaskGraphVO;
import com.epam.pipeline.controller.vo.UploadFileMetadata;
import com.epam.pipeline.entity.cluster.InstancePrice;
import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.pipeline.DocumentGenerationProperty;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.test.creator.CommonCreatorConstants;
import com.epam.pipeline.test.creator.git.GitCreatorUtils;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_ARRAY;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class PipelineControllerTest extends AbstractControllerTest {

    private static final String PIPELINE_URL = SERVLET_PATH + "/pipeline";
    private static final String PIPELINE_REGISTER_URL = PIPELINE_URL + "/register";
    private static final String PIPELINE_CHECK_URL = PIPELINE_URL + "/check";
    private static final String PIPELINE_UPDATE_URL = PIPELINE_URL + "/update";
    private static final String PIPELINE_UPDATE_TOKEN_URL = PIPELINE_URL + "/updateToken";
    private static final String PIPELINE_LOAD_ALL_URL = PIPELINE_URL + "/loadAll";
    private static final String PIPELINE_FIND_URL = PIPELINE_URL + "/find";
    private static final String PIPELINE_LOAD_ALL_PERMISSIONS_URL = PIPELINE_URL + "/permissions";
    private static final String PIPELINE_ID_URL = PIPELINE_URL + "/%d";
    private static final String PIPELINE_ID_LOAD_URL = PIPELINE_ID_URL + "/load";
    private static final String PIPELINE_ID_DELETE_URL = PIPELINE_ID_URL + "/delete";
    private static final String PIPELINE_ID_RUNS_URL = PIPELINE_ID_URL + "/runs";
    private static final String PIPELINE_ID_VERSIONS_URL = PIPELINE_ID_URL + "/versions";
    private static final String PIPELINE_ID_VERSION_URL = PIPELINE_ID_URL + "/version";
    private static final String PIPELINE_VERSION_REGISTER_URL = PIPELINE_URL + "/version/register";
    private static final String PIPELINE_ID_CLONE_URL = PIPELINE_ID_URL + "/clone";
    private static final String PIPELINE_ID_PRICE_URL = PIPELINE_ID_URL + "/price";
    private static final String PIPELINE_ID_GRAPH_URL = PIPELINE_ID_URL + "/graph";
    private static final String PIPELINE_ID_SOURCES_URL = PIPELINE_ID_URL + "/sources";
    private static final String PIPELINE_ID_FOLDER_URL = PIPELINE_ID_URL + "/folder";
    private static final String PIPELINE_ID_DOCS_URL = PIPELINE_ID_URL + "/docs";
    private static final String PIPELINE_ID_FILE_URL = PIPELINE_ID_URL + "/file";
    private static final String PIPELINE_ID_FILE_TRUNCATE_URL = PIPELINE_ID_FILE_URL + "/truncate";
    private static final String PIPELINE_ID_FILE_DOWNLOAD_URL = PIPELINE_ID_FILE_URL + "/download";
    private static final String PIPELINE_ID_FILE_UPLOAD_URL = PIPELINE_ID_FILE_URL + "/upload";
    private static final String PIPELINE_ID_FILE_GENERATE_URL = PIPELINE_ID_FILE_URL + "/generate";
    private static final String PIPELINE_ID_FILES_URL = PIPELINE_ID_URL + "/files";
    private static final String PIPELINE_GIT_URL = PIPELINE_URL + "/git";
    private static final String PIPELINE_PRICE_URL = PIPELINE_URL + "/price";
    private static final String PIPELINE_GIT_CREDENTIALS_URL = PIPELINE_GIT_URL + "/credentials";
    private static final String PIPELINE_ID_TEMPLATE_URL = PIPELINE_ID_URL + "/template";
    private static final String PIPELINE_ID_TEMPLATE_PROPERTIES_URL = PIPELINE_ID_TEMPLATE_URL + "/properties";
    private static final String PIPELINE_ID_TEMPLATE_PROPERTIES_NAME_URL = PIPELINE_ID_TEMPLATE_PROPERTIES_URL + "/%s";
    private static final String PIPELINE_TEMPLATE_PROPERTIES_URL = PIPELINE_URL + "/template" + "/properties";
    private static final String PIPELINE_FIND_BY_URL_URL = PIPELINE_URL + "/findByUrl";
    private static final String PIPELINE_ID_ADD_HOOK_URL = PIPELINE_ID_URL + "/addHook";
    private static final String PIPELINE_ID_REPOSITORY_URL = PIPELINE_ID_URL + "/repository";
    private static final String PIPELINE_ID_COPY_URL = PIPELINE_ID_URL + "/copy";

    private static final String LOAD_VERSION = "loadVersion";
    private static final String PAGE_NUM = "pageNum";
    private static final String PAGE_SIZE = "pageSize";
    private static final String STRING_ID = "id";
    private static final String KEEP_REPOSITORY = "keepRepository";
    private static final String VERSION = "version";
    private static final String DURATION = "duration";
    private static final String CONFIG = "config";
    private static final String PATH = "path";
    private static final String RECURSIVE = "recursive";
    private static final String URL = "url";
    private static final String PARENT_ID = "parentId";
    private static final String NAME = "name";
    private static final String BYTE_LIMIT = "byteLimit";
    private static final String FILE_SIZE = "0 Kb";

    private final Pipeline pipeline = PipelineCreatorUtils.getPipeline();
    private final PipelineVO pipelineVO = PipelineCreatorUtils.getPipelineVO();
    private final CheckRepositoryVO repositoryVO = PipelineCreatorUtils.getCheckRepositoryVO();
    private final PipelinesWithPermissionsVO pipelinesWithPermissionsVO =
            PipelineCreatorUtils.getPipelinesWithPermissionsVO();
    private final PipelineRun pipelineRun = PipelineCreatorUtils.getPipelineRun();
    private final Revision revision = PipelineCreatorUtils.getRevision();
    private final GitTagEntry gitTagEntry = GitCreatorUtils.getGitTagEntry();
    private final GitCredentials gitCredentials = GitCreatorUtils.getGitCredentials();
    private final InstancePrice instancePrice = PipelineCreatorUtils.getInstancePrice();
    private final InstanceOfferParametersVO instance = PipelineCreatorUtils.getInstanceOfferParametersVO();
    private final TaskGraphVO taskGraphVO = PipelineCreatorUtils.getTaskGraphVO();
    private final GitRepositoryEntry gitRepositoryEntry = GitCreatorUtils.getGitRepositoryEntry();
    private final PipelineSourceItemVO sourceItemVO = PipelineCreatorUtils.getPipelineSourceItemVO();
    private final PipelineSourceItemsVO sourceItemsVO = PipelineCreatorUtils.getPipelineSourceItemsVO();
    private final GitCommitEntry gitCommitEntry = GitCreatorUtils.getGitCommitEntry();
    private final UploadFileMetadata fileMetadata =
            PipelineCreatorUtils.getUploadFileMetadata(
                    MULTIPART_CONTENT_FILE_NAME, FILE_SIZE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
    private final GenerateFileVO generateFileVO = PipelineCreatorUtils.getGenerateFileVO();
    private final RegisterPipelineVersionVO pipelineVersionVO = PipelineCreatorUtils.getRegisterPipelineVersionVO();
    private final DocumentGenerationProperty documentGenerationProperty =
            PipelineCreatorUtils.getDocumentGenerationProperty();

    private final List<Pipeline> pipelineList = Collections.singletonList(pipeline);
    private final List<PipelineRun> pipelineRunList = Collections.singletonList(pipelineRun);
    private final List<Revision> revisionList = Collections.singletonList(revision);
    private final List<GitRepositoryEntry> gitRepositoryEntries = Collections.singletonList(gitRepositoryEntry);
    private final List<UploadFileMetadata> fileMetadataList = Collections.singletonList(fileMetadata);
    private final List<DocumentGenerationProperty> generationProperties =
            Collections.singletonList(documentGenerationProperty);

    @Autowired
    private PipelineApiService mockPipelineApiService;

    @Test
    @WithMockUser
    public void shouldRegisterPipeline() throws Exception {
        doReturn(pipeline).when(mockPipelineApiService).create(pipelineVO);

        final MvcResult mvcResult = performRequest(post(PIPELINE_REGISTER_URL).content(stringOf(pipelineVO)));

        verify(mockPipelineApiService).create(pipelineVO);
        assertResponse(mvcResult, pipeline, PipelineCreatorUtils.PIPELINE_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailRegisterPipelineForUnauthorizedUser() {
        performUnauthorizedRequest(post(PIPELINE_REGISTER_URL));
    }

    @Test
    @WithMockUser
    public void shouldCheckPipelineRepository() throws Exception {
        doReturn(repositoryVO).when(mockPipelineApiService).check(repositoryVO);

        final MvcResult mvcResult = performRequest(post(PIPELINE_CHECK_URL).content(stringOf(repositoryVO)));

        verify(mockPipelineApiService).check(repositoryVO);
        assertResponse(mvcResult, repositoryVO, PipelineCreatorUtils.CHECK_REPOSITORY_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailCheckPipelineRepositoryForUnauthorizedUser() {
        performUnauthorizedRequest(post(PIPELINE_CHECK_URL));
    }

    @Test
    @WithMockUser
    public void shouldUpdatePipeline() {
        doReturn(pipeline).when(mockPipelineApiService).update(pipelineVO);

        final MvcResult mvcResult = performRequest(post(PIPELINE_UPDATE_URL).content(stringOf(pipelineVO)));

        verify(mockPipelineApiService).update(pipelineVO);
        assertResponse(mvcResult, pipeline, PipelineCreatorUtils.PIPELINE_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailUpdatePipelineForUnauthorizedUser() {
        performUnauthorizedRequest(post(PIPELINE_UPDATE_URL));
    }

    @Test
    @WithMockUser
    public void shouldUpdatePipelineToken() {
        doReturn(pipeline).when(mockPipelineApiService).updateToken(pipelineVO);

        final MvcResult mvcResult = performRequest(post(PIPELINE_UPDATE_TOKEN_URL).content(stringOf(pipelineVO)));

        verify(mockPipelineApiService).updateToken(pipelineVO);
        assertResponse(mvcResult, pipeline, PipelineCreatorUtils.PIPELINE_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailUpdatePipelineTokenForUnauthorizedUser() {
        performUnauthorizedRequest(post(PIPELINE_UPDATE_TOKEN_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadAllPipelines() {
        doReturn(pipelineList).when(mockPipelineApiService).loadAllPipelines(true);

        final MvcResult mvcResult = performRequest(get(PIPELINE_LOAD_ALL_URL)
                .params(multiValueMapOf(LOAD_VERSION, true)));

        verify(mockPipelineApiService).loadAllPipelines(true);
        assertResponse(mvcResult, pipelineList, PipelineCreatorUtils.PIPELINE_LIST_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadAllPipelinesForUnauthorizedUser() {
        performUnauthorizedRequest(get(PIPELINE_LOAD_ALL_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadAllPipelinesWithPermissions() {
        doReturn(pipelinesWithPermissionsVO).when(mockPipelineApiService)
                .loadAllPipelinesWithPermissions(TEST_INT, TEST_INT);

        final MvcResult mvcResult = performRequest(get(PIPELINE_LOAD_ALL_PERMISSIONS_URL)
                .params(multiValueMapOf(PAGE_NUM, TEST_INT,
                                        PAGE_SIZE, TEST_INT)));

        verify(mockPipelineApiService).loadAllPipelinesWithPermissions(TEST_INT, TEST_INT);
        assertResponse(mvcResult, pipelinesWithPermissionsVO, PipelineCreatorUtils.PIPELINE_WITH_PERMISSIONS_TYPE);
    }

    @Test
    public void shouldFailLoadAllPipelinesWithPermissionsForUnauthorizedUser() {
        performUnauthorizedRequest(get(PIPELINE_LOAD_ALL_PERMISSIONS_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadPipeline() {
        doReturn(pipeline).when(mockPipelineApiService).load(ID);

        final MvcResult mvcResult = performRequest(get(String.format(PIPELINE_ID_LOAD_URL, ID)));

        verify(mockPipelineApiService).load(ID);
        assertResponse(mvcResult, pipeline, PipelineCreatorUtils.PIPELINE_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadPipelineForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(PIPELINE_ID_LOAD_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldFindPipeline() {
        doReturn(pipeline).when(mockPipelineApiService).loadPipelineByIdOrName(TEST_STRING);

        final MvcResult mvcResult = performRequest(get(PIPELINE_FIND_URL)
                .params(multiValueMapOf(STRING_ID, TEST_STRING)));

        verify(mockPipelineApiService).loadPipelineByIdOrName(TEST_STRING);
        assertResponse(mvcResult, pipeline, PipelineCreatorUtils.PIPELINE_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailFindPipelineForUnauthorizedUser() {
        performUnauthorizedRequest(get(PIPELINE_FIND_URL));
    }

    @Test
    @WithMockUser
    public void shouldDeletePipeline() {
        doReturn(pipeline).when(mockPipelineApiService).delete(ID, false);

        final MvcResult mvcResult = performRequest(delete(String.format(PIPELINE_ID_DELETE_URL, ID))
                .params(multiValueMapOf(KEEP_REPOSITORY, false)));

        verify(mockPipelineApiService).delete(ID, false);
        assertResponse(mvcResult, pipeline, PipelineCreatorUtils.PIPELINE_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailDeletePipelineForUnauthorizedUser() {
        performUnauthorizedRequest(delete(String.format(PIPELINE_ID_DELETE_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadRunsByPipeline() {
        doReturn(pipelineRunList).when(mockPipelineApiService).loadAllRunsByPipeline(ID);

        final MvcResult mvcResult = performRequest(get(String.format(PIPELINE_ID_RUNS_URL, ID)));

        verify(mockPipelineApiService).loadAllRunsByPipeline(ID);
        assertResponse(mvcResult, pipelineRunList, PipelineCreatorUtils.PIPELINE_RUN_LIST_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadRunsByPipelineForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(PIPELINE_ID_RUNS_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadVersionsByPipeline() throws Exception {
        doReturn(revisionList).when(mockPipelineApiService).loadAllVersionFromGit(ID);

        final MvcResult mvcResult = performRequest(get(String.format(PIPELINE_ID_VERSIONS_URL, ID)));

        verify(mockPipelineApiService).loadAllVersionFromGit(ID);
        assertResponse(mvcResult, revisionList, PipelineCreatorUtils.REVISION_LIST_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadVersionsByPipelineForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(PIPELINE_ID_VERSIONS_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadPipelineVersion() throws Exception {
        doReturn(gitTagEntry).when(mockPipelineApiService).loadRevision(ID, TEST_STRING);

        final MvcResult mvcResult = performRequest(get(String.format(PIPELINE_ID_VERSION_URL, ID))
                .params(multiValueMapOf(VERSION, TEST_STRING)));

        verify(mockPipelineApiService).loadRevision(ID, TEST_STRING);
        assertResponse(mvcResult, gitTagEntry, GitCreatorUtils.GIT_TAG_ENTRY_TYPE);
    }

    @Test
    public void shouldFailLoadPipelineVersionForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(PIPELINE_ID_VERSION_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGetPipelineCloneURL() {
        doReturn(TEST_STRING).when(mockPipelineApiService).getPipelineCloneUrl(ID);

        final MvcResult mvcResult = performRequest(get(String.format(PIPELINE_ID_CLONE_URL, ID)));

        verify(mockPipelineApiService).getPipelineCloneUrl(ID);
        assertResponse(mvcResult, TEST_STRING, CommonCreatorConstants.STRING_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailGetPipelineCloneURLForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(PIPELINE_ID_CLONE_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGetPipelineCredentials() {
        doReturn(gitCredentials).when(mockPipelineApiService).getPipelineCredentials(ID);

        final MvcResult mvcResult = performRequest(get(PIPELINE_GIT_CREDENTIALS_URL)
                .params(multiValueMapOf(DURATION, ID)));

        verify(mockPipelineApiService).getPipelineCredentials(ID);
        assertResponse(mvcResult, gitCredentials, GitCreatorUtils.GIT_CREDENTIALS_TYPE);
    }

    @Test
    public void shouldFailGetPipelineCredentialsForUnauthorizedUser() {
        performUnauthorizedRequest(get(PIPELINE_GIT_CREDENTIALS_URL));
    }

    @Test
    @WithMockUser
    public void shouldGetPipelineEstimatedPrice() throws Exception {
        doReturn(instancePrice).when(mockPipelineApiService)
                .getInstanceEstimatedPrice(ID, TEST_STRING, TEST_STRING, TEST_STRING, TEST_INT, true, ID);

        final MvcResult mvcResult = performRequest(post(String.format(PIPELINE_ID_PRICE_URL, ID))
                .content(stringOf(instance))
                .params(multiValueMapOf(VERSION, TEST_STRING,
                                        CONFIG, TEST_STRING)));

        verify(mockPipelineApiService)
                .getInstanceEstimatedPrice(ID, TEST_STRING, TEST_STRING, TEST_STRING, TEST_INT, true, ID);
        assertResponse(mvcResult, instancePrice, PipelineCreatorUtils.INSTANCE_PRICE_TYPE);
    }

    @Test
    public void shouldFailGetPipelineEstimatedPriceForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(PIPELINE_ID_PRICE_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGetEstimatedPrice() {
        doReturn(instancePrice).when(mockPipelineApiService)
                .getInstanceEstimatedPrice(TEST_STRING, TEST_INT, true, ID);

        final MvcResult mvcResult = performRequest(post(PIPELINE_PRICE_URL).content(stringOf(instance)));

        verify(mockPipelineApiService).getInstanceEstimatedPrice(TEST_STRING, TEST_INT, true, ID);
        assertResponse(mvcResult, instancePrice, PipelineCreatorUtils.INSTANCE_PRICE_TYPE);
    }

    @Test
    public void shouldFailGetEstimatedPriceForUnauthorizedUser() {
        performUnauthorizedRequest(post(PIPELINE_PRICE_URL));
    }

    @Test
    @WithMockUser
    public void shouldGetWorkflowGraph() {
        doReturn(taskGraphVO).when(mockPipelineApiService).getWorkflowGraph(ID, TEST_STRING);

        final MvcResult mvcResult = performRequest(get(String.format(PIPELINE_ID_GRAPH_URL, ID))
                .params(multiValueMapOf(VERSION, TEST_STRING)));

        verify(mockPipelineApiService).getWorkflowGraph(ID, TEST_STRING);
        assertResponse(mvcResult, taskGraphVO, PipelineCreatorUtils.TASK_GRAPH_VO_TYPE);
    }

    @Test
    public void shouldFailGetWorkflowGraphForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(PIPELINE_ID_GRAPH_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGetPipelineSources() throws GitClientException {
        doReturn(gitRepositoryEntries).when(mockPipelineApiService)
                .getPipelineSources(ID, TEST_STRING, TEST_STRING, true, true);

        final MvcResult mvcResult = performRequest(get(String.format(PIPELINE_ID_SOURCES_URL, ID))
                .params(multiValueMapOf(VERSION, TEST_STRING,
                                        PATH, TEST_STRING,
                                        RECURSIVE, true)));

        verify(mockPipelineApiService).getPipelineSources(ID, TEST_STRING, TEST_STRING, true, true);
        assertResponse(mvcResult, gitRepositoryEntries, GitCreatorUtils.GIT_REPOSITORY_ENTRY_LIST_TYPE);
    }

    @Test
    public void shouldFailGetPipelineSourcesForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(PIPELINE_ID_SOURCES_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldCreateOrRenamePipelineFolder() throws Exception {
        doReturn(gitCommitEntry).when(mockPipelineApiService).createOrRenameFolder(ID, sourceItemVO);

        final MvcResult mvcResult = performRequest(post(String.format(PIPELINE_ID_FOLDER_URL, ID))
                .content(stringOf(sourceItemVO)));

        verify(mockPipelineApiService).createOrRenameFolder(ID, sourceItemVO);
        assertResponse(mvcResult, gitCommitEntry, GitCreatorUtils.GIT_COMMIT_ENTRY_TYPE);
    }

    @Test
    public void shouldFailCreateOrRenamePipelineFolderForUnauthorizedUser() {
        performUnauthorizedRequest(post(String.format(PIPELINE_ID_FOLDER_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldRemovePipelineFolder() throws Exception {
        doReturn(gitCommitEntry).when(mockPipelineApiService).removeFolder(ID, TEST_STRING, TEST_STRING, TEST_STRING);

        final MvcResult mvcResult = performRequest(delete(String.format(PIPELINE_ID_FOLDER_URL, ID))
                .content(stringOf(sourceItemVO)));

        verify(mockPipelineApiService).removeFolder(ID, TEST_STRING, TEST_STRING, TEST_STRING);
        assertResponse(mvcResult, gitCommitEntry, GitCreatorUtils.GIT_COMMIT_ENTRY_TYPE);
    }

    @Test
    public void shouldFailRemovePipelineFolderForUnauthorizedUser() {
        performUnauthorizedRequest(delete(String.format(PIPELINE_ID_FOLDER_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGetPipelineDocs() throws Exception {
        doReturn(gitRepositoryEntries).when(mockPipelineApiService).getPipelineDocs(ID, TEST_STRING);

        final MvcResult mvcResult = performRequest(get(String.format(PIPELINE_ID_DOCS_URL, ID))
                .params(multiValueMapOf(VERSION, TEST_STRING)));

        verify(mockPipelineApiService).getPipelineDocs(ID, TEST_STRING);
        assertResponse(mvcResult, gitRepositoryEntries, GitCreatorUtils.GIT_REPOSITORY_ENTRY_LIST_TYPE);
    }

    @Test
    public void shouldFailGetPipelineDocsForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(PIPELINE_ID_DOCS_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGetPipelineFile() throws Exception {
        doReturn(TEST_ARRAY).when(mockPipelineApiService).getPipelineFileContents(ID, TEST_STRING, TEST_STRING);

        final MvcResult mvcResult = performRequest(get(String.format(PIPELINE_ID_FILE_URL, ID))
                .params(multiValueMapOf(VERSION, TEST_STRING,
                                        PATH, TEST_STRING)));

        verify(mockPipelineApiService).getPipelineFileContents(ID, TEST_STRING, TEST_STRING);
        assertResponseAsBytes(mvcResult, TEST_ARRAY);
    }

    @Test
    public void shouldFailGetPipelineFileForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(PIPELINE_ID_FILE_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGetTruncatedPipelineFile() throws Exception {
        doReturn(TEST_ARRAY).when(mockPipelineApiService)
                .getTruncatedPipelineFileContent(ID, TEST_STRING, TEST_STRING, TEST_INT);

        final MvcResult mvcResult = performRequest(get(String.format(PIPELINE_ID_FILE_TRUNCATE_URL, ID))
                .params(multiValueMapOf(VERSION, TEST_STRING,
                                        PATH, TEST_STRING,
                                        BYTE_LIMIT, TEST_INT)));

        verify(mockPipelineApiService).getTruncatedPipelineFileContent(ID, TEST_STRING, TEST_STRING, TEST_INT);
        assertResponseAsBytes(mvcResult, TEST_ARRAY);
    }

    @Test
    public void shouldFailGetTruncatedPipelineFileForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(PIPELINE_ID_FILE_TRUNCATE_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldModifyPipelineFile() throws Exception {
        doReturn(gitCommitEntry).when(mockPipelineApiService).modifyFile(ID, sourceItemVO);

        final MvcResult mvcResult = performRequest(post(String.format(PIPELINE_ID_FILE_URL, ID))
                .content(stringOf(sourceItemVO)));

        verify(mockPipelineApiService).modifyFile(ID, sourceItemVO);
        assertResponse(mvcResult, gitCommitEntry, GitCreatorUtils.GIT_COMMIT_ENTRY_TYPE);
    }

    @Test
    public void shouldFailModifyPipelineFileForUnauthorizedUser() {
        performUnauthorizedRequest(post(String.format(PIPELINE_ID_FILE_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldModifyPipelineFiles() throws Exception {
        doReturn(gitCommitEntry).when(mockPipelineApiService).modifyFiles(ID, sourceItemsVO);

        final MvcResult mvcResult = performRequest(post(String.format(PIPELINE_ID_FILES_URL, ID))
                .content(stringOf(sourceItemsVO)));

        verify(mockPipelineApiService).modifyFiles(ID, sourceItemsVO);
        assertResponse(mvcResult, gitCommitEntry, GitCreatorUtils.GIT_COMMIT_ENTRY_TYPE);
    }

    @Test
    public void shouldFailModifyPipelineFilesForUnauthorizedUser() {
        performUnauthorizedRequest(post(String.format(PIPELINE_ID_FILES_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldUploadFile() throws Exception {
        doReturn(gitCommitEntry).when(mockPipelineApiService).uploadFiles(ID, TEST_STRING, fileMetadataList);

        final MvcResult mvcResult = performRequest(post(String.format(PIPELINE_ID_FILE_UPLOAD_URL, ID))
                        .params(multiValueMapOf(PATH, TEST_STRING))
                        .content(MULTIPART_CONTENT),
                        MULTIPART_CONTENT_TYPE, EXPECTED_CONTENT_TYPE);
        final ArgumentCaptor<UploadFileMetadata> captor = ArgumentCaptor.forClass(UploadFileMetadata.class);

        verify(mockPipelineApiService).uploadFiles(eq(ID), eq(TEST_STRING),
                Collections.singletonList(captor.capture()));
        assertUnwrappedResponse(mvcResult, fileMetadataList);
    }

    @Test
    public void shouldFailUploadFileForUnauthorizedUser() {
        performUnauthorizedRequest(post(String.format(PIPELINE_ID_FILE_UPLOAD_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDeletePipelineFile() throws Exception {
        doReturn(gitCommitEntry).when(mockPipelineApiService).deleteFile(ID, TEST_STRING, TEST_STRING, TEST_STRING);

        final MvcResult mvcResult = performRequest(delete(String.format(PIPELINE_ID_FILE_URL, ID))
                .content(stringOf(sourceItemVO)));

        verify(mockPipelineApiService).deleteFile(ID, TEST_STRING, TEST_STRING, TEST_STRING);
        assertResponse(mvcResult, gitCommitEntry, GitCreatorUtils.GIT_COMMIT_ENTRY_TYPE);
    }

    @Test
    public void shouldFailDeletePipelineFileForUnauthorizedUser() {
        performUnauthorizedRequest(delete(String.format(PIPELINE_ID_FILE_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDownloadPipelineFile() throws Exception {
        doReturn(TEST_ARRAY).when(mockPipelineApiService).getPipelineFileContents(ID, TEST_STRING, TEST_STRING);

        final MvcResult mvcResult = performRequest(get(String.format(PIPELINE_ID_FILE_DOWNLOAD_URL, ID))
                        .params(multiValueMapOf(VERSION, TEST_STRING,
                                                PATH, TEST_STRING)),
                                                MediaType.APPLICATION_OCTET_STREAM_VALUE);

        verify(mockPipelineApiService).getPipelineFileContents(ID, TEST_STRING, TEST_STRING);
        assertFileResponse(mvcResult, TEST_STRING, TEST_ARRAY);
    }

    @Test
    public void shouldFailDownloadPipelineFileForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(PIPELINE_ID_FILE_DOWNLOAD_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGenerateFileByTemplate() {
        doReturn(TEST_ARRAY).when(mockPipelineApiService)
                .fillTemplateForPipelineVersion(ID, TEST_STRING, TEST_STRING, generateFileVO);

        final MvcResult mvcResult = performRequest(post(String.format(PIPELINE_ID_FILE_GENERATE_URL, ID))
                        .content(stringOf(generateFileVO)).params(multiValueMapOf(VERSION, TEST_STRING,
                                                                                  PATH, TEST_STRING)),
                                                                                  EXPECTED_CONTENT_TYPE,
                                                                 MediaType.APPLICATION_OCTET_STREAM_VALUE);

        verify(mockPipelineApiService).fillTemplateForPipelineVersion(ID, TEST_STRING, TEST_STRING, generateFileVO);
        assertFileResponse(mvcResult, TEST_STRING, TEST_ARRAY);
    }

    @Test
    public void shouldFailGenerateFileByTemplateForUnauthorizedUser() {
        performUnauthorizedRequest(post(String.format(PIPELINE_ID_FILE_GENERATE_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldRegisterPipelineVersion() throws Exception {
        doReturn(revision).when(mockPipelineApiService).registerPipelineVersion(pipelineVersionVO);

        final MvcResult mvcResult = performRequest(post(PIPELINE_VERSION_REGISTER_URL)
                .content(stringOf(pipelineVersionVO)));

        verify(mockPipelineApiService).registerPipelineVersion(pipelineVersionVO);
        assertResponse(mvcResult, revision, PipelineCreatorUtils.REVISION_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailRegisterPipelineVersionForUnauthorizedUser() {
        performUnauthorizedRequest(post(PIPELINE_VERSION_REGISTER_URL));
    }

    @Test
    @WithMockUser
    public void shouldGetPipelineDocumentGenerationProperties() {
        doReturn(generationProperties).when(mockPipelineApiService).loadAllPropertiesByPipelineId(ID);

        final MvcResult mvcResult = performRequest(get(String.format(PIPELINE_ID_TEMPLATE_PROPERTIES_URL, ID)));

        verify(mockPipelineApiService).loadAllPropertiesByPipelineId(ID);
        assertResponse(mvcResult, generationProperties, PipelineCreatorUtils.DOCUMENT_GENERATION_PROPERTY_LIST_TYPE);
    }

    @Test
    public void shouldFailGetPipelineDocumentGenerationPropertiesForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(PIPELINE_ID_TEMPLATE_PROPERTIES_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGetPipelineDocumentGenerationProperty() {
        doReturn(documentGenerationProperty).when(mockPipelineApiService).loadProperty(TEST_STRING, ID);

        final MvcResult mvcResult = performRequest(get(
                String.format(PIPELINE_ID_TEMPLATE_PROPERTIES_NAME_URL, ID, TEST_STRING)));

        verify(mockPipelineApiService).loadProperty(TEST_STRING, ID);
        assertResponse(mvcResult, documentGenerationProperty, PipelineCreatorUtils.DOCUMENT_GENERATION_PROPERTY_TYPE);
    }

    @Test
    public void shouldFailGetPipelineDocumentGenerationPropertyForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(PIPELINE_ID_TEMPLATE_PROPERTIES_NAME_URL, ID, TEST_STRING)));
    }

    @Test
    @WithMockUser
    public void shouldSavePipelineDocumentGenerationProperty() {
        doReturn(documentGenerationProperty).when(mockPipelineApiService).saveProperty(documentGenerationProperty);

        final MvcResult mvcResult = performRequest(post(PIPELINE_TEMPLATE_PROPERTIES_URL)
                .content(stringOf(documentGenerationProperty)));

        verify(mockPipelineApiService).saveProperty(documentGenerationProperty);
        assertResponse(mvcResult, documentGenerationProperty, PipelineCreatorUtils.DOCUMENT_GENERATION_PROPERTY_TYPE);
    }

    @Test
    public void shouldFailSavePipelineDocumentGenerationPropertyForUnauthorizedUser() {
        performUnauthorizedRequest(post(PIPELINE_TEMPLATE_PROPERTIES_URL));
    }

    @Test
    @WithMockUser
    public void shouldDeletePipelineDocumentGenerationProperty() {
        doReturn(documentGenerationProperty).when(mockPipelineApiService).deleteProperty(TEST_STRING, ID);

        final MvcResult mvcResult = performRequest(delete(PIPELINE_TEMPLATE_PROPERTIES_URL)
                .content(stringOf(documentGenerationProperty)));

        verify(mockPipelineApiService).deleteProperty(TEST_STRING, ID);
        assertResponse(mvcResult, documentGenerationProperty, PipelineCreatorUtils.DOCUMENT_GENERATION_PROPERTY_TYPE);
    }

    @Test
    public void shouldFailDeletePipelineDocumentGenerationPropertyForUnauthorizedUser() {
        performUnauthorizedRequest(delete(PIPELINE_TEMPLATE_PROPERTIES_URL));
    }

    @Test
    @WithMockUser
    public void shouldDeleteFindPipelineByRepoUrl() {
        doReturn(pipeline).when(mockPipelineApiService).loadPipelineByRepoUrl(TEST_STRING);

        final MvcResult mvcResult = performRequest(get(PIPELINE_FIND_BY_URL_URL)
                .params(multiValueMapOf(URL, TEST_STRING)));

        verify(mockPipelineApiService).loadPipelineByRepoUrl(TEST_STRING);
        assertResponse(mvcResult, pipeline, PipelineCreatorUtils.PIPELINE_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailFindPipelineByRepoUrlForUnauthorizedUser() {
        performUnauthorizedRequest(get(PIPELINE_FIND_BY_URL_URL));
    }

    @Test
    @WithMockUser
    public void shouldDeleteAddHookToPipelineRepository() throws Exception {
        doReturn(gitRepositoryEntry).when(mockPipelineApiService).addHookToPipelineRepository(ID);

        final MvcResult mvcResult = performRequest(post(String.format(PIPELINE_ID_ADD_HOOK_URL, ID)));

        verify(mockPipelineApiService).addHookToPipelineRepository(ID);
        assertResponse(mvcResult, gitRepositoryEntry, GitCreatorUtils.GIT_REPOSITORY_ENTRY_TYPE);
    }

    @Test
    public void shouldFailAddHookToPipelineRepositoryForUnauthorizedUser() {
        performUnauthorizedRequest(post(String.format(PIPELINE_ID_ADD_HOOK_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadRepositoryContent() throws Exception {
        doReturn(gitRepositoryEntries).when(mockPipelineApiService)
                .getPipelineRepositoryContents(ID, TEST_STRING, TEST_STRING);

        final MvcResult mvcResult = performRequest(get(String.format(PIPELINE_ID_REPOSITORY_URL, ID))
                .params(multiValueMapOf(VERSION, TEST_STRING,
                                        PATH, TEST_STRING)));

        verify(mockPipelineApiService).getPipelineRepositoryContents(ID, TEST_STRING, TEST_STRING);
        assertResponse(mvcResult, gitRepositoryEntries, GitCreatorUtils.GIT_REPOSITORY_ENTRY_LIST_TYPE);
    }

    @Test
    public void shouldFailLoadRepositoryContentForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(PIPELINE_ID_REPOSITORY_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldCopyPipeline() {
        doReturn(pipeline).when(mockPipelineApiService).copyPipeline(ID, ID, TEST_STRING);

        final MvcResult mvcResult = performRequest(post(String.format(PIPELINE_ID_COPY_URL, ID))
                .params(multiValueMapOf(PARENT_ID, ID,
                                        NAME, TEST_STRING)));

        verify(mockPipelineApiService).copyPipeline(ID, ID, TEST_STRING);
        assertResponse(mvcResult, pipeline, PipelineCreatorUtils.PIPELINE_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailCopyPipelineForUnauthorizedUser() {
        performUnauthorizedRequest(post(String.format(PIPELINE_ID_COPY_URL, ID)));
    }

    private void assertResponseAsBytes(final MvcResult mvcResult, final byte[] bytes) throws Exception {
        final String contentAsString = mvcResult.getResponse().getContentAsString();
        final byte[] returnedEntityBody = getObjectMapper().readValue(contentAsString, byte[].class);

        assertThat(returnedEntityBody).isEqualTo(bytes);
    }

    private void assertUnwrappedResponse(final MvcResult mvcResult, final List<UploadFileMetadata> list)
                                                                                      throws Exception {
        final String actualResult = mvcResult.getResponse().getContentAsString();
        final List<UploadFileMetadata> returnedList =
                getObjectMapper().readValue(actualResult, PipelineCreatorUtils.UPLOAD_METADATA_LIST_TYPE);

        assertThat(returnedList).isEqualTo(list);
    }
}
