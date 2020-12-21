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

package com.epam.pipeline.acl.pipeline;

import com.epam.pipeline.controller.vo.PipelineSourceItemVO;
import com.epam.pipeline.controller.vo.PipelineSourceItemsVO;
import com.epam.pipeline.controller.vo.RegisterPipelineVersionVO;
import com.epam.pipeline.controller.vo.TaskGraphVO;
import com.epam.pipeline.controller.vo.UploadFileMetadata;
import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.PipelineVersionManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.git.GitCreatorUtils;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_ARRAY;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static com.epam.pipeline.util.CustomAssertions.assertThrowsChecked;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class PipelineApiServiceGitTest extends AbstractAclTest {

    private final Pipeline pipeline = PipelineCreatorUtils.getPipeline(ANOTHER_SIMPLE_USER);
    private final GitTagEntry gitTagEntry = GitCreatorUtils.getGitTagEntry();
    private final GitCommitEntry gitCommitEntry = GitCreatorUtils.getGitCommitEntry();
    private final PipelineSourceItemVO sourceItemVO = PipelineCreatorUtils.getPipelineSourceItemVO();
    private final PipelineSourceItemsVO sourceItemsVO = PipelineCreatorUtils.getPipelineSourceItemsVO();
    private final GitCredentials gitCredentials = GitCreatorUtils.getGitCredentials();
    private final GitRepositoryEntry gitRepositoryEntry = GitCreatorUtils.getGitRepositoryEntry();
    private final UploadFileMetadata fileMetadata = PipelineCreatorUtils.getUploadFileMetadata();
    private final Revision revision = PipelineCreatorUtils.getRevision();
    private final TaskGraphVO taskGraphVO = PipelineCreatorUtils.getTaskGraphVO();
    private final RegisterPipelineVersionVO pipelineVersionVO = PipelineCreatorUtils.getRegisterPipelineVersionVO();
    private final List<GitRepositoryEntry> gitRepositoryEntries = Collections.singletonList(gitRepositoryEntry);
    private final List<UploadFileMetadata> files = Collections.singletonList(fileMetadata);
    private final List<Revision> revisionList = Collections.singletonList(revision);

    @Autowired
    private PipelineApiService pipelineApiService;
    @Autowired
    private PipelineManager mockPipelineManager;
    @Autowired
    private GitManager mockGitManager;
    @Autowired
    private PipelineVersionManager mockVersionManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadRevisionForAdmin() throws GitClientException {
        doReturn(pipeline).when(mockPipelineManager).load(ID);
        doReturn(gitTagEntry).when(mockGitManager).loadRevision(pipeline, TEST_STRING);

        assertThat(pipelineApiService.loadRevision(ID, TEST_STRING)).isEqualTo(gitTagEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadRevisionWhenPermissionIsGranted() throws GitClientException {
        initAclEntity(pipeline, AclPermission.READ);
        doReturn(pipeline).when(mockPipelineManager).load(ID);
        doReturn(gitTagEntry).when(mockGitManager).loadRevision(pipeline, TEST_STRING);

        assertThat(pipelineApiService.loadRevision(ID, TEST_STRING)).isEqualTo(gitTagEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadRevisionWhenPermissionIsNotGranted() throws GitClientException {
        initAclEntity(pipeline);
        doReturn(pipeline).when(mockPipelineManager).load(ID);
        doReturn(gitTagEntry).when(mockGitManager).loadRevision(pipeline, TEST_STRING);

        assertThrowsChecked(AccessDeniedException.class, () -> pipelineApiService.loadRevision(ID, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldAddHookToPipelineRepositoryForAdmin() throws GitClientException {
        doReturn(gitRepositoryEntry).when(mockGitManager).addHookToPipelineRepository(ID);

        assertThat(pipelineApiService.addHookToPipelineRepository(ID)).isEqualTo(gitRepositoryEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = PIPELINE_MANAGER_ROLE)
    public void shouldAddHookToPipelineRepositoryWhenPermissionIsGranted() throws GitClientException {
        initAclEntity(pipeline, AclPermission.WRITE);
        doReturn(gitRepositoryEntry).when(mockGitManager).addHookToPipelineRepository(ID);

        assertThat(pipelineApiService.addHookToPipelineRepository(ID)).isEqualTo(gitRepositoryEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = PIPELINE_MANAGER_ROLE)
    public void shouldDenyAddHookToPipelineRepositoryWhenPermissionIsNotGranted() throws GitClientException {
        initAclEntity(pipeline);
        doReturn(gitRepositoryEntry).when(mockGitManager).addHookToPipelineRepository(ID);

        assertThrowsChecked(AccessDeniedException.class, () -> pipelineApiService.addHookToPipelineRepository(ID));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyAddHookToPipelineRepositoryForUserRole() throws GitClientException {
        initAclEntity(pipeline, AclPermission.WRITE);
        doReturn(gitRepositoryEntry).when(mockGitManager).addHookToPipelineRepository(ID);

        assertThrowsChecked(AccessDeniedException.class, () -> pipelineApiService.addHookToPipelineRepository(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateOrRenameFolderForAdmin() throws GitClientException {
        doReturn(gitCommitEntry).when(mockGitManager).createOrRenameFolder(ID, sourceItemVO);

        assertThat(pipelineApiService.createOrRenameFolder(ID, sourceItemVO)).isEqualTo(gitCommitEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldCreateOrRenameFolderWhenPermissionIsGranted() throws GitClientException {
        initAclEntity(pipeline, AclPermission.WRITE);
        doReturn(gitCommitEntry).when(mockGitManager).createOrRenameFolder(ID, sourceItemVO);

        assertThat(pipelineApiService.createOrRenameFolder(ID, sourceItemVO)).isEqualTo(gitCommitEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyCreateOrRenameFolderWhenPermissionIsNotGranted() throws GitClientException {
        initAclEntity(pipeline);
        doReturn(gitCommitEntry).when(mockGitManager).createOrRenameFolder(ID, sourceItemVO);

        assertThrowsChecked(AccessDeniedException.class, () ->
                pipelineApiService.createOrRenameFolder(ID, sourceItemVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldRemoveFolderForAdmin() throws GitClientException {
        doReturn(pipeline).when(mockPipelineManager).load(ID, true);
        doReturn(gitCommitEntry).when(mockGitManager).removeFolder(pipeline, TEST_STRING, TEST_STRING, TEST_STRING);

        assertThat(pipelineApiService.removeFolder(ID, TEST_STRING, TEST_STRING, TEST_STRING))
                .isEqualTo(gitCommitEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldRemoveFolderWhenPermissionIsGranted() throws GitClientException {
        initAclEntity(pipeline, AclPermission.WRITE);
        doReturn(pipeline).when(mockPipelineManager).load(ID, true);
        doReturn(gitCommitEntry).when(mockGitManager).removeFolder(pipeline, TEST_STRING, TEST_STRING, TEST_STRING);

        assertThat(pipelineApiService.removeFolder(ID, TEST_STRING, TEST_STRING, TEST_STRING))
                .isEqualTo(gitCommitEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyRemoveFolderWhenPermissionIsNotGranted() throws GitClientException {
        initAclEntity(pipeline);
        doReturn(pipeline).when(mockPipelineManager).load(ID, true);
        doReturn(gitCommitEntry).when(mockGitManager).removeFolder(pipeline, TEST_STRING, TEST_STRING, TEST_STRING);

        assertThrowsChecked(AccessDeniedException.class, () ->
                pipelineApiService.removeFolder(ID, TEST_STRING, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldModifyFileForAdmin() throws GitClientException {
        doReturn(pipeline).when(mockPipelineManager).load(ID, true);
        doReturn(gitCommitEntry).when(mockGitManager).modifyFile(pipeline, sourceItemVO);

        assertThat(pipelineApiService.modifyFile(ID, sourceItemVO)).isEqualTo(gitCommitEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldModifyFileWhenPermissionIsGranted() throws GitClientException {
        initAclEntity(pipeline, AclPermission.WRITE);
        doReturn(pipeline).when(mockPipelineManager).load(ID, true);
        doReturn(gitCommitEntry).when(mockGitManager).modifyFile(pipeline, sourceItemVO);

        assertThat(pipelineApiService.modifyFile(ID, sourceItemVO)).isEqualTo(gitCommitEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyModifyFileWhenPermissionIsNotGranted() throws GitClientException {
        initAclEntity(pipeline);
        doReturn(pipeline).when(mockPipelineManager).load(ID, true);
        doReturn(gitCommitEntry).when(mockGitManager).modifyFile(pipeline, sourceItemVO);

        assertThrowsChecked(AccessDeniedException.class, () -> pipelineApiService.modifyFile(ID, sourceItemVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldModifyFilesForAdmin() throws GitClientException {
        doReturn(pipeline).when(mockPipelineManager).load(ID, true);
        doReturn(gitCommitEntry).when(mockGitManager).updateFiles(pipeline, sourceItemsVO);

        assertThat(pipelineApiService.modifyFiles(ID, sourceItemsVO)).isEqualTo(gitCommitEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldModifyFilesWhenPermissionIsGranted() throws GitClientException {
        initAclEntity(pipeline, AclPermission.WRITE);
        doReturn(pipeline).when(mockPipelineManager).load(ID, true);
        doReturn(gitCommitEntry).when(mockGitManager).updateFiles(pipeline, sourceItemsVO);

        assertThat(pipelineApiService.modifyFiles(ID, sourceItemsVO)).isEqualTo(gitCommitEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyModifyFilesWhenPermissionIsNotGranted() throws GitClientException {
        initAclEntity(pipeline);
        doReturn(pipeline).when(mockPipelineManager).load(ID, true);
        doReturn(gitCommitEntry).when(mockGitManager).updateFiles(pipeline, sourceItemsVO);

        assertThrowsChecked(AccessDeniedException.class, () -> pipelineApiService.modifyFiles(ID, sourceItemsVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUploadFilesForAdmin() throws GitClientException {
        doReturn(pipeline).when(mockPipelineManager).load(ID, true);
        doReturn(gitCommitEntry).when(mockGitManager).uploadFiles(pipeline, TEST_STRING, files, TEST_STRING, null);

        assertThat(pipelineApiService.uploadFiles(ID, TEST_STRING, files)).isEqualTo(gitCommitEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUploadFilesWhenPermissionIsGranted() throws GitClientException {
        initAclEntity(pipeline, AclPermission.WRITE);
        doReturn(pipeline).when(mockPipelineManager).load(ID, true);
        doReturn(gitCommitEntry).when(mockGitManager).uploadFiles(pipeline, TEST_STRING, files, TEST_STRING, null);

        assertThat(pipelineApiService.uploadFiles(ID, TEST_STRING, files)).isEqualTo(gitCommitEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyUploadFilesWhenPermissionIsNotGranted() throws GitClientException {
        initAclEntity(pipeline);
        doReturn(pipeline).when(mockPipelineManager).load(ID, true);
        doReturn(gitCommitEntry).when(mockGitManager).uploadFiles(pipeline, TEST_STRING, files, TEST_STRING, null);

        assertThrowsChecked(AccessDeniedException.class, () -> pipelineApiService.uploadFiles(ID, TEST_STRING, files));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteFileForAdmin() throws GitClientException {
        doReturn(pipeline).when(mockPipelineManager).load(ID, true);
        doReturn(gitCommitEntry).when(mockGitManager).deleteFile(pipeline, TEST_STRING, TEST_STRING, TEST_STRING);

        assertThat(pipelineApiService.deleteFile(ID, TEST_STRING, TEST_STRING, TEST_STRING)).isEqualTo(gitCommitEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDeleteFileWhenPermissionIsGranted() throws GitClientException {
        initAclEntity(pipeline, AclPermission.WRITE);
        doReturn(pipeline).when(mockPipelineManager).load(ID, true);
        doReturn(gitCommitEntry).when(mockGitManager).deleteFile(pipeline, TEST_STRING, TEST_STRING, TEST_STRING);

        assertThat(pipelineApiService.deleteFile(ID, TEST_STRING, TEST_STRING, TEST_STRING)).isEqualTo(gitCommitEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyDeleteFileWhenPermissionIsNotGranted() throws GitClientException {
        initAclEntity(pipeline);
        doReturn(pipeline).when(mockPipelineManager).load(ID, true);
        doReturn(gitCommitEntry).when(mockGitManager).deleteFile(pipeline, TEST_STRING, TEST_STRING, TEST_STRING);

        assertThrowsChecked(AccessDeniedException.class, () ->
                pipelineApiService.deleteFile(ID, TEST_STRING, TEST_STRING, TEST_STRING));
    }

    @Test
    public void shouldGetPipelineCredentials() {
        doReturn(gitCredentials).when(mockGitManager).getGitlabCredentials(ID);

        assertThat(pipelineApiService.getPipelineCredentials(ID)).isEqualTo(gitCredentials);
    }


    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetPipelineSourcesForAdmin() throws GitClientException {
        doReturn(gitRepositoryEntries).when(mockGitManager)
                .getPipelineSources(ID, TEST_STRING, TEST_STRING, true, true);

        assertThat(pipelineApiService.getPipelineSources(ID, TEST_STRING, TEST_STRING, true, true))
                .isEqualTo(gitRepositoryEntries);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetPipelineSourcesWhenPermissionIsGranted() throws GitClientException {
        initAclEntity(pipeline, AclPermission.READ);
        doReturn(gitRepositoryEntries).when(mockGitManager)
                .getPipelineSources(ID, TEST_STRING, TEST_STRING, true, true);

        assertThat(pipelineApiService.getPipelineSources(ID, TEST_STRING, TEST_STRING, true, true))
                .isEqualTo(gitRepositoryEntries);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyGetPipelineSourcesWhenPermissionIsNotGranted() throws GitClientException {
        initAclEntity(pipeline);
        doReturn(gitRepositoryEntries).when(mockGitManager)
                .getPipelineSources(ID, TEST_STRING, TEST_STRING, true, true);

        assertThrowsChecked(AccessDeniedException.class, () ->
                pipelineApiService.getPipelineSources(ID, TEST_STRING, TEST_STRING, true, true));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetPipelineRepositoryContentsForAdmin() throws GitClientException {
        doReturn(gitRepositoryEntries).when(mockGitManager).getRepositoryContents(ID, TEST_STRING, TEST_STRING);

        assertThat(pipelineApiService.getPipelineRepositoryContents(ID, TEST_STRING, TEST_STRING))
                .isEqualTo(gitRepositoryEntries);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetPipelineRepositoryContentsWhenPermissionIsGranted() throws GitClientException {
        initAclEntity(pipeline, AclPermission.READ);
        doReturn(gitRepositoryEntries).when(mockGitManager).getRepositoryContents(ID, TEST_STRING, TEST_STRING);

        assertThat(pipelineApiService.getPipelineRepositoryContents(ID, TEST_STRING, TEST_STRING))
                .isEqualTo(gitRepositoryEntries);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyGetPipelineRepositoryContentsWhenPermissionIsNotGranted() throws GitClientException {
        initAclEntity(pipeline);
        doReturn(gitRepositoryEntries).when(mockGitManager).getRepositoryContents(ID, TEST_STRING, TEST_STRING);

        assertThrowsChecked(AccessDeniedException.class, () ->
                pipelineApiService.getPipelineRepositoryContents(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetPipelineDocsForAdmin() throws GitClientException {
        doReturn(gitRepositoryEntries).when(mockGitManager).getPipelineDocs(ID, TEST_STRING);

        assertThat(pipelineApiService.getPipelineDocs(ID, TEST_STRING)).isEqualTo(gitRepositoryEntries);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetPipelineDocsWhenPermissionIsGranted() throws GitClientException {
        initAclEntity(pipeline, AclPermission.READ);
        doReturn(gitRepositoryEntries).when(mockGitManager).getPipelineDocs(ID, TEST_STRING);

        assertThat(pipelineApiService.getPipelineDocs(ID, TEST_STRING)).isEqualTo(gitRepositoryEntries);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyGetPipelineDocsWhenPermissionIsNotGranted() throws GitClientException {
        initAclEntity(pipeline);
        doReturn(gitRepositoryEntries).when(mockGitManager).getPipelineDocs(ID, TEST_STRING);

        assertThrowsChecked(AccessDeniedException.class, () -> pipelineApiService.getPipelineDocs(ID, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetPipelineFileContentsForAdmin() throws GitClientException {
        doReturn(pipeline).when(mockPipelineManager).load(ID);
        doReturn(TEST_ARRAY).when(mockGitManager).getPipelineFileContents(pipeline, TEST_STRING, TEST_STRING);

        assertThat(pipelineApiService.getPipelineFileContents(ID, TEST_STRING, TEST_STRING)).isEqualTo(TEST_ARRAY);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetPipelineFileContentsWhenPermissionIsGranted() throws GitClientException {
        initAclEntity(pipeline, AclPermission.READ);
        doReturn(pipeline).when(mockPipelineManager).load(ID);
        doReturn(TEST_ARRAY).when(mockGitManager).getPipelineFileContents(pipeline, TEST_STRING, TEST_STRING);

        assertThat(pipelineApiService.getPipelineFileContents(ID, TEST_STRING, TEST_STRING)).isEqualTo(TEST_ARRAY);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyGetPipelineFileContentsWhenPermissionIsNotGranted() throws GitClientException {
        initAclEntity(pipeline);
        doReturn(pipeline).when(mockPipelineManager).load(ID);
        doReturn(TEST_ARRAY).when(mockGitManager).getPipelineFileContents(pipeline, TEST_STRING, TEST_STRING);

        assertThrowsChecked(AccessDeniedException.class, () ->
                pipelineApiService.getPipelineFileContents(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetTruncatedPipelineFileContentForAdmin() throws GitClientException {
        doReturn(pipeline).when(mockPipelineManager).load(ID);
        doReturn(TEST_ARRAY).when(mockGitManager)
                .getTruncatedPipelineFileContent(pipeline, TEST_STRING, TEST_STRING, TEST_INT);

        assertThat(pipelineApiService.getTruncatedPipelineFileContent(ID, TEST_STRING, TEST_STRING, TEST_INT))
                .isEqualTo(TEST_ARRAY);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetTruncatedPipelineFileContentWhenPermissionIsGranted() throws GitClientException {
        initAclEntity(pipeline, AclPermission.READ);
        doReturn(pipeline).when(mockPipelineManager).load(ID);
        doReturn(TEST_ARRAY).when(mockGitManager)
                .getTruncatedPipelineFileContent(pipeline, TEST_STRING, TEST_STRING, TEST_INT);

        assertThat(pipelineApiService.getTruncatedPipelineFileContent(ID, TEST_STRING, TEST_STRING, TEST_INT))
                .isEqualTo(TEST_ARRAY);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyGetTruncatedPipelineFileContentWhenPermissionIsNotGranted() throws GitClientException {
        initAclEntity(pipeline);
        doReturn(pipeline).when(mockPipelineManager).load(ID);
        doReturn(TEST_ARRAY).when(mockGitManager)
                .getTruncatedPipelineFileContent(pipeline, TEST_STRING, TEST_STRING, TEST_INT);

        assertThrowsChecked(AccessDeniedException.class, () ->
                pipelineApiService.getTruncatedPipelineFileContent(ID, TEST_STRING, TEST_STRING, TEST_INT));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadVersionFromGitForAdmin() throws GitClientException {
        doReturn(revisionList).when(mockVersionManager).loadAllVersionFromGit(ID);

        assertThat(pipelineApiService.loadAllVersionFromGit(ID)).isEqualTo(revisionList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadAllVersionFromGitWhenPermissionIsGranted() throws GitClientException {
        initAclEntity(pipeline, AclPermission.READ);
        doReturn(revisionList).when(mockVersionManager).loadAllVersionFromGit(ID);

        assertThat(pipelineApiService.loadAllVersionFromGit(ID)).isEqualTo(revisionList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadAllVersionFromGitWhenPermissionIsNotGranted() throws GitClientException {
        initAclEntity(pipeline);
        doReturn(revisionList).when(mockVersionManager).loadAllVersionFromGit(ID);

        assertThrowsChecked(AccessDeniedException.class, () -> pipelineApiService.loadAllVersionFromGit(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetWorkflowGraphForAdmin() {
        doReturn(taskGraphVO).when(mockVersionManager).getWorkflowGraph(ID, TEST_STRING);

        assertThat(pipelineApiService.getWorkflowGraph(ID, TEST_STRING)).isEqualTo(taskGraphVO);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetWorkflowGraphWhenPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.READ);
        doReturn(taskGraphVO).when(mockVersionManager).getWorkflowGraph(ID, TEST_STRING);

        assertThat(pipelineApiService.getWorkflowGraph(ID, TEST_STRING)).isEqualTo(taskGraphVO);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyGetWorkflowGraphWhenPermissionIsNotGranted() {
        initAclEntity(pipeline);
        doReturn(taskGraphVO).when(mockVersionManager).getWorkflowGraph(ID, TEST_STRING);

        assertThrows(AccessDeniedException.class, () -> pipelineApiService.getWorkflowGraph(ID, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldRegisterPipelineVersionForAdmin() throws GitClientException {
        doReturn(revision).when(mockVersionManager).registerPipelineVersion(pipelineVersionVO);

        assertThat(pipelineApiService.registerPipelineVersion(pipelineVersionVO)).isEqualTo(revision);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldRegisterPipelineVersionWhenPermissionIsGranted() throws GitClientException {
        initAclEntity(pipeline, AclPermission.WRITE);
        doReturn(revision).when(mockVersionManager).registerPipelineVersion(pipelineVersionVO);

        assertThat(pipelineApiService.registerPipelineVersion(pipelineVersionVO)).isEqualTo(revision);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyRegisterPipelineVersionWhenPermissionIsNotGranted() throws GitClientException {
        initAclEntity(pipeline);
        doReturn(revision).when(mockVersionManager).registerPipelineVersion(pipelineVersionVO);

        assertThrowsChecked(AccessDeniedException.class, () ->
                pipelineApiService.registerPipelineVersion(pipelineVersionVO));
    }
}
