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
import com.epam.pipeline.controller.vo.UploadFileMetadata;
import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class PipelineApiServiceGitTest extends AbstractAclTest {

    protected final Pipeline pipeline = PipelineCreatorUtils.getPipeline(ANOTHER_SIMPLE_USER);
    private final GitTagEntry gitTagEntry = GitCreatorUtils.getGitTagEntry();
    private final GitCommitEntry gitCommitEntry = GitCreatorUtils.getGitCommitEntry();
    private final PipelineSourceItemVO sourceItemVO = PipelineCreatorUtils.getPipelineSourceItemVO();
    private final PipelineSourceItemsVO sourceItemsVO = PipelineCreatorUtils.getPipelineSourceItemsVO();
    private final GitCredentials gitCredentials = GitCreatorUtils.getGitCredentials();
    private final GitRepositoryEntry gitRepositoryEntry = GitCreatorUtils.getGitRepositoryEntry();
    private final UploadFileMetadata fileMetadata = PipelineCreatorUtils.getUploadFileMetadata();
    private final List<GitRepositoryEntry> gitRepositoryEntries = Collections.singletonList(gitRepositoryEntry);
    private final List<UploadFileMetadata> files = Collections.singletonList(fileMetadata);

    @Autowired
    private PipelineApiService pipelineApiService;
    @Autowired
    private PipelineManager mockPipelineManager;
    @Autowired
    private GitManager mockGitManager;

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

        assertThrows(AccessDeniedException.class, () -> {
            try {
                pipelineApiService.loadRevision(ID, TEST_STRING);
            } catch (GitClientException e) {
                e.printStackTrace();
            }
        });
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

        assertThrows(AccessDeniedException.class, () -> {
            try {
                pipelineApiService.addHookToPipelineRepository(ID);
            } catch (GitClientException e) {
                e.printStackTrace();
            }
        });
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyAddHookToPipelineRepositoryForUserRole() throws GitClientException {
        initAclEntity(pipeline, AclPermission.WRITE);
        doReturn(gitRepositoryEntry).when(mockGitManager).addHookToPipelineRepository(ID);

        assertThrows(AccessDeniedException.class, () -> {
            try {
                pipelineApiService.addHookToPipelineRepository(ID);
            } catch (GitClientException e) {
                e.printStackTrace();
            }
        });
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

        assertThrows(AccessDeniedException.class, () -> {
            try {
                pipelineApiService.createOrRenameFolder(ID, sourceItemVO);
            } catch (GitClientException e) {
                e.printStackTrace();
            }
        });
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

        assertThrows(AccessDeniedException.class, () -> {
            try {
                pipelineApiService.removeFolder(ID, TEST_STRING, TEST_STRING, TEST_STRING);
            } catch (GitClientException e) {
                e.printStackTrace();
            }
        });
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

        assertThrows(AccessDeniedException.class, () -> {
            try {
                pipelineApiService.modifyFile(ID, sourceItemVO);
            } catch (GitClientException e) {
                e.printStackTrace();
            }
        });
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

        assertThrows(AccessDeniedException.class, () -> {
            try {
                pipelineApiService.modifyFiles(ID, sourceItemsVO);
            } catch (GitClientException e) {
                e.printStackTrace();
            }
        });
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

        assertThrows(AccessDeniedException.class, () -> {
            try {
                pipelineApiService.uploadFiles(ID, TEST_STRING, files);
            } catch (GitClientException e) {
                e.printStackTrace();
            }
        });
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

        assertThrows(AccessDeniedException.class, () -> {
            try {
                pipelineApiService.deleteFile(ID, TEST_STRING, TEST_STRING, TEST_STRING);
            } catch (GitClientException e) {
                e.printStackTrace();
            }
        });
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

        assertThrows(AccessDeniedException.class, () -> {
            try {
                pipelineApiService.getPipelineSources(ID, TEST_STRING, TEST_STRING, true, true);
            } catch (GitClientException e) {
                e.printStackTrace();
            }
        });
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

        assertThrows(AccessDeniedException.class, () -> {
            try {
                pipelineApiService.getPipelineRepositoryContents(ID, TEST_STRING, TEST_STRING);
            } catch (GitClientException e) {
                e.printStackTrace();
            }
        });
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

        assertThrows(AccessDeniedException.class, () -> {
            try {
                pipelineApiService.getPipelineDocs(ID, TEST_STRING);
            } catch (GitClientException e) {
                e.printStackTrace();
            }
        });
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

        assertThrows(AccessDeniedException.class, () -> {
            try {
                pipelineApiService.getPipelineFileContents(ID, TEST_STRING, TEST_STRING);
            } catch (GitClientException e) {
                e.printStackTrace();
            }
        });
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

        assertThrows(AccessDeniedException.class, () -> {
            try {
                pipelineApiService.getTruncatedPipelineFileContent(ID, TEST_STRING, TEST_STRING, TEST_INT);
            } catch (GitClientException e) {
                e.printStackTrace();
            }
        });
    }
}
