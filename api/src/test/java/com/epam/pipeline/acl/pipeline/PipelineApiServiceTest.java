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

import com.epam.pipeline.controller.vo.CheckRepositoryVO;
import com.epam.pipeline.controller.vo.GenerateFileVO;
import com.epam.pipeline.controller.vo.PipelineVO;
import com.epam.pipeline.controller.vo.PipelinesWithPermissionsVO;
import com.epam.pipeline.controller.vo.RegisterPipelineVersionVO;
import com.epam.pipeline.controller.vo.TaskGraphVO;
import com.epam.pipeline.entity.cluster.InstancePrice;
import com.epam.pipeline.entity.pipeline.DocumentGenerationProperty;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.pipeline.DocumentGenerationPropertyManager;
import com.epam.pipeline.manager.pipeline.PipelineFileGenerationManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.PipelineVersionManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.folder.FolderCreatorUtils;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.READ_PERMISSION;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_ARRAY;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.WRITE_PERMISSION;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class PipelineApiServiceTest extends AbstractAclTest {

    protected final Pipeline pipeline = PipelineCreatorUtils.getPipeline(ANOTHER_SIMPLE_USER);
    private final Pipeline anotherPipeline = PipelineCreatorUtils.getPipeline(ID_2, ANOTHER_SIMPLE_USER, ID_2);
    private final PipelineVO pipelineVO = PipelineCreatorUtils.getPipelineVO(ID);
    private final Folder folder = FolderCreatorUtils.getFolder(ID, ANOTHER_SIMPLE_USER);
    private final CheckRepositoryVO checkRepositoryVO = PipelineCreatorUtils.getCheckRepositoryVO();
    private final PipelinesWithPermissionsVO pipelinesWithPermissions =
            PipelineCreatorUtils.getPipelinesWithPermissionsVO();
    private final PipelineRun pipelineRun = PipelineCreatorUtils.getPipelineRun(ID, ANOTHER_SIMPLE_USER);
    private final Revision revision = PipelineCreatorUtils.getRevision();
    private final InstancePrice instancePrice = PipelineCreatorUtils.getInstancePrice();
    private final TaskGraphVO taskGraphVO = PipelineCreatorUtils.getTaskGraphVO();
    private final GenerateFileVO fileVO = PipelineCreatorUtils.getGenerateFileVO();
    private final RegisterPipelineVersionVO pipelineVersionVO = PipelineCreatorUtils.getRegisterPipelineVersionVO();
    private final DocumentGenerationProperty property = PipelineCreatorUtils.getDocumentGenerationProperty();
    private final List<PipelineRun> pipelineRunList = Collections.singletonList(pipelineRun);
    private final List<Revision> revisionList = Collections.singletonList(revision);
    private final List<DocumentGenerationProperty> propertyList = Collections.singletonList(property);

    @Autowired
    private PipelineApiService pipelineApiService;
    @Autowired
    private PipelineManager mockPipelineManager;
    @Autowired
    private PipelineRunManager mockPipelineRunManager;
    @Autowired
    private PipelineVersionManager mockVersionManager;
    @Autowired
    private InstanceOfferManager mockInstanceOfferManager;
    @Autowired
    private PipelineFileGenerationManager mockFileGenerationManager;
    @Autowired
    private DocumentGenerationPropertyManager mockPropertyManager;
    @Autowired
    private GrantPermissionManager spyGrantPermissionManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreatePipelineForAdmin() throws GitClientException {
        doReturn(pipeline).when(mockPipelineManager).create(pipelineVO);

        assertThat(pipelineApiService.create(pipelineVO)).isEqualTo(pipeline);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = PIPELINE_MANAGER_ROLE)
    public void shouldCreatePipelineWhenPermissionIsGranted() throws GitClientException {
        initAclEntity(folder, AclPermission.WRITE);
        doReturn(pipeline).when(mockPipelineManager).create(pipelineVO);

        assertThat(pipelineApiService.create(pipelineVO)).isEqualTo(pipeline);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = PIPELINE_MANAGER_ROLE)
    public void shouldDenyCreatePipelineWhenParentIdIsNull() throws GitClientException {
        initAclEntity(folder, AclPermission.WRITE);
        final PipelineVO pipelineVO = PipelineCreatorUtils.getPipelineVO(null);
        doReturn(pipeline).when(mockPipelineManager).create(pipelineVO);

        assertThrows(AccessDeniedException.class, () -> {
            try {
                pipelineApiService.create(pipelineVO);
            } catch (GitClientException e) {
                e.printStackTrace();
            }
        });
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyCreatePipelineForUserRole() throws GitClientException {
        initAclEntity(folder, AclPermission.WRITE);
        doReturn(pipeline).when(mockPipelineManager).create(pipelineVO);

        assertThrows(AccessDeniedException.class, () -> {
            try {
                pipelineApiService.create(pipelineVO);
            } catch (GitClientException e) {
                e.printStackTrace();
            }
        });
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = PIPELINE_MANAGER_ROLE)
    public void shouldDenyCreatePipelineWhenPermissionIsNotGranted() throws GitClientException {
        initAclEntity(folder);
        doReturn(pipeline).when(mockPipelineManager).create(pipelineVO);

        assertThrows(AccessDeniedException.class, () -> {
            try {
                pipelineApiService.create(pipelineVO);
            } catch (GitClientException e) {
                e.printStackTrace();
            }
        });
    }

    @Test
    public void setCheckRepositoryVO() throws GitClientException {
        doReturn(checkRepositoryVO).when(mockPipelineManager).check(checkRepositoryVO);

        assertThat(pipelineApiService.check(checkRepositoryVO)).isEqualTo(checkRepositoryVO);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdatePipelineForAdmin() {
        doReturn(pipeline).when(mockPipelineManager).update(pipelineVO);

        assertThat(pipelineApiService.update(pipelineVO)).isEqualTo(pipeline);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdatePipelineWhenPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.WRITE);
        doReturn(pipeline).when(mockPipelineManager).update(pipelineVO);

        final Pipeline returnedPipeline = pipelineApiService.update(pipelineVO);

        assertThat(returnedPipeline).isEqualTo(pipeline);
        assertThat(returnedPipeline.getMask()).isEqualTo(WRITE_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyUpdatePipelineWhenPermissionIsNotGranted() {
        initAclEntity(pipeline);
        doReturn(pipeline).when(mockPipelineManager).update(pipelineVO);

        assertThrows(AccessDeniedException.class, () -> pipelineApiService.update(pipelineVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateTokenForAdmin() {
        doReturn(pipeline).when(mockPipelineManager).updateToken(pipelineVO);

        assertThat(pipelineApiService.updateToken(pipelineVO)).isEqualTo(pipeline);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateTokenWhenPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.WRITE);
        doReturn(pipeline).when(mockPipelineManager).updateToken(pipelineVO);

        final Pipeline returnedPipeline = pipelineApiService.updateToken(pipelineVO);

        assertThat(returnedPipeline).isEqualTo(pipeline);
        assertThat(returnedPipeline.getMask()).isEqualTo(WRITE_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyUpdateTokenWhenPermissionIsNotGranted() {
        initAclEntity(pipeline);
        doReturn(pipeline).when(mockPipelineManager).updateToken(pipelineVO);

        assertThrows(AccessDeniedException.class, () -> pipelineApiService.updateToken(pipelineVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAllPipelinesForAdmin() {
        doReturn(mutableListOf(pipeline)).when(mockPipelineManager).loadAllPipelines(true);

        assertThat(pipelineApiService.loadAllPipelines(true)).hasSize(1).contains(pipeline);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadAllPipelinesWhenPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.READ);
        doReturn(mutableListOf(pipeline)).when(mockPipelineManager).loadAllPipelines(true);

        final List<Pipeline> returnedPipelines = pipelineApiService.loadAllPipelines(true);

        assertThat(returnedPipelines).hasSize(1).contains(pipeline);
        assertThat(returnedPipelines.get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadPipelinesWhichPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.READ);
        initAclEntity(anotherPipeline);
        doReturn(mutableListOf(pipeline, anotherPipeline)).when(mockPipelineManager).loadAllPipelines(true);

        final List<Pipeline> returnedPipelines = pipelineApiService.loadAllPipelines(true);

        assertThat(returnedPipelines).hasSize(1).contains(pipeline);
        assertThat(returnedPipelines.get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldNotLoadAllPipelinesWhenPermissionIsNotGranted() {
        initAclEntity(pipeline);
        doReturn(mutableListOf(pipeline)).when(mockPipelineManager).loadAllPipelines(true);

        assertThat(pipelineApiService.loadAllPipelines(true)).isEmpty();
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAllPipelinesWithPermissionsForAdmin() {
        doReturn(pipelinesWithPermissions).when(spyGrantPermissionManager)
                .loadAllPipelinesWithPermissions(TEST_INT, TEST_INT);

        assertThat(pipelineApiService.loadAllPipelinesWithPermissions(TEST_INT, TEST_INT))
                .isEqualTo(pipelinesWithPermissions);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadAllPipelinesWithPermissionsForNotAdmin() {
        doReturn(pipelinesWithPermissions).when(spyGrantPermissionManager)
                .loadAllPipelinesWithPermissions(TEST_INT, TEST_INT);

        assertThrows(AccessDeniedException.class, () ->
                pipelineApiService.loadAllPipelinesWithPermissions(TEST_INT, TEST_INT));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadPipelineForAdmin() {
        doReturn(pipeline).when(mockPipelineManager).load(ID, true);

        assertThat(pipelineApiService.load(ID)).isEqualTo(pipeline);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadPipelineWhenPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.READ);
        doReturn(pipeline).when(mockPipelineManager).load(ID, true);

        final Pipeline returnedPipeline = pipelineApiService.load(ID);

        assertThat(returnedPipeline).isEqualTo(pipeline);
        assertThat(returnedPipeline.getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadPipelineWhenPermissionIsNotGranted() {
        initAclEntity(pipeline);
        doReturn(pipeline).when(mockPipelineManager).load(ID, true);

        assertThrows(AccessDeniedException.class, () -> pipelineApiService.load(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadPipelineByIdOrNameForAdmin() {
        doReturn(pipeline).when(mockPipelineManager).loadByNameOrId(TEST_STRING);

        assertThat(pipelineApiService.loadPipelineByIdOrName(TEST_STRING)).isEqualTo(pipeline);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadPipelineByIdOrNameWhenPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.READ);
        doReturn(pipeline).when(mockPipelineManager).loadByNameOrId(TEST_STRING);

        final Pipeline returnedPipeline = pipelineApiService.loadPipelineByIdOrName(TEST_STRING);

        assertThat(returnedPipeline).isEqualTo(pipeline);
        assertThat(returnedPipeline.getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadPipelineByIdOrNameWhenPermissionIsNotGranted() {
        initAclEntity(pipeline);
        doReturn(pipeline).when(mockPipelineManager).loadByNameOrId(TEST_STRING);

        assertThrows(AccessDeniedException.class, () -> pipelineApiService.loadPipelineByIdOrName(TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAllPipelinesWithoutVersionForAdmin() {
        doReturn(mutableListOf(pipeline)).when(mockPipelineManager).loadAllPipelines(false);

        assertThat(pipelineApiService.loadAllPipelinesWithoutVersion()).hasSize(1).contains(pipeline);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadAllPipelinesWithoutVersionWhenPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.READ);
        doReturn(mutableListOf(pipeline)).when(mockPipelineManager).loadAllPipelines(false);

        final List<Pipeline> returnedPipelines = pipelineApiService.loadAllPipelinesWithoutVersion();

        assertThat(returnedPipelines).hasSize(1).contains(pipeline);
        assertThat(returnedPipelines.get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadPipelinesWithoutVersionWhichPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.READ);
        initAclEntity(anotherPipeline);
        doReturn(mutableListOf(pipeline, anotherPipeline)).when(mockPipelineManager).loadAllPipelines(false);

        final List<Pipeline> returnedPipelines = pipelineApiService.loadAllPipelinesWithoutVersion();

        assertThat(returnedPipelines).hasSize(1).contains(pipeline);
        assertThat(returnedPipelines.get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldNotLoadAllPipelinesWithoutVersionWhenPermissionIsNotGranted() {
        initAclEntity(pipeline);
        doReturn(mutableListOf(pipeline)).when(mockPipelineManager).loadAllPipelines(false);

        assertThat(pipelineApiService.loadAllPipelinesWithoutVersion()).isEmpty();
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeletePipelineForAdmin() {
        doReturn(pipeline).when(mockPipelineManager).delete(ID, true);

        assertThat(pipelineApiService.delete(ID, true)).isEqualTo(pipeline);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = PIPELINE_MANAGER_ROLE)
    public void shouldDeletePipelineWhenPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.WRITE);
        doReturn(pipeline).when(mockPipelineManager).delete(ID, true);

        assertThat(pipelineApiService.delete(ID, true)).isEqualTo(pipeline);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = PIPELINE_MANAGER_ROLE)
    public void shouldDenyDeletePipelineWhenPermissionIsNotGranted() {
        initAclEntity(pipeline);
        doReturn(pipeline).when(mockPipelineManager).delete(ID, true);

        assertThrows(AccessDeniedException.class, () -> pipelineApiService.delete(ID, true));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyDeletePipelineForUserRole() {
        initAclEntity(pipeline, AclPermission.WRITE);
        doReturn(pipeline).when(mockPipelineManager).delete(ID, true);

        assertThrows(AccessDeniedException.class, () -> pipelineApiService.delete(ID, true));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAllRunsByPipelineForAdmin() {
        doReturn(pipelineRunList).when(mockPipelineRunManager).loadAllRunsByPipeline(ID);

        assertThat(pipelineApiService.loadAllRunsByPipeline(ID)).isEqualTo(pipelineRunList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadAllRunsByPipelineWhenPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.READ);
        initAclEntity(pipelineRun, AclPermission.READ);
        doReturn(pipelineRunList).when(mockPipelineRunManager).loadAllRunsByPipeline(ID);

        final List<PipelineRun> returnedPipelineRunList = pipelineApiService.loadAllRunsByPipeline(ID);

        assertThat(returnedPipelineRunList).hasSize(1).contains(pipelineRun);
        assertThat(returnedPipelineRunList.get(0).getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadAllRunsByPipelineWhenPermissionIsNotGranted() {
        initAclEntity(pipeline);
        doReturn(pipelineRunList).when(mockPipelineRunManager).loadAllRunsByPipeline(ID);

        assertThrows(AccessDeniedException.class, () -> pipelineApiService.loadAllRunsByPipeline(ID));
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

        assertThrows(AccessDeniedException.class, () -> {
            try {
                pipelineApiService.loadAllVersionFromGit(ID);
            } catch (GitClientException e) {
                e.printStackTrace();
            }
        });
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetInstanceEstimatedPriceForAdmin() throws GitClientException {
        doReturn(instancePrice).when(mockInstanceOfferManager)
                .getInstanceEstimatedPrice(ID, TEST_STRING, TEST_STRING, TEST_STRING, TEST_INT, true, ID);

        assertThat(pipelineApiService.
                getInstanceEstimatedPrice(ID, TEST_STRING, TEST_STRING, TEST_STRING, TEST_INT, true, ID))
                .isEqualTo(instancePrice);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetInstanceEstimatedPriceWhenPermissionIsGranted() throws GitClientException {
        initAclEntity(pipeline, AclPermission.READ);
        doReturn(instancePrice).when(mockInstanceOfferManager)
                .getInstanceEstimatedPrice(ID, TEST_STRING, TEST_STRING, TEST_STRING, TEST_INT, true, ID);

        assertThat(pipelineApiService.
                getInstanceEstimatedPrice(ID, TEST_STRING, TEST_STRING, TEST_STRING, TEST_INT, true, ID))
                .isEqualTo(instancePrice);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyGetInstanceEstimatedPriceWhenPermissionIsNotGranted() throws GitClientException {
        initAclEntity(pipeline);
        doReturn(instancePrice).when(mockInstanceOfferManager)
                .getInstanceEstimatedPrice(ID, TEST_STRING, TEST_STRING, TEST_STRING, TEST_INT, true, ID);

        assertThrows(AccessDeniedException.class, () -> {
            try {
                pipelineApiService.getInstanceEstimatedPrice(ID, TEST_STRING, TEST_STRING,
                        TEST_STRING, TEST_INT, true, ID);
            } catch (GitClientException e) {
                e.printStackTrace();
            }
        });
    }

    @Test
    public void shouldGetInstanceEstimatedPrice() {
        doReturn(instancePrice).when(mockInstanceOfferManager)
                .getInstanceEstimatedPrice(TEST_STRING, TEST_INT, true, ID);

        assertThat(pipelineApiService.getInstanceEstimatedPrice(TEST_STRING, TEST_INT, true, ID))
                .isEqualTo(instancePrice);
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
    public void shouldFillTemplateForPipelineVersionForAdmin() {
        doReturn(TEST_ARRAY).when(mockFileGenerationManager)
                .fillTemplateForPipelineVersion(ID, TEST_STRING, TEST_STRING, fileVO);

        assertThat(pipelineApiService.fillTemplateForPipelineVersion(ID, TEST_STRING, TEST_STRING, fileVO))
                .isEqualTo(TEST_ARRAY);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldFillTemplateForPipelineVersionWhenPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.READ);
        doReturn(TEST_ARRAY).when(mockFileGenerationManager)
                .fillTemplateForPipelineVersion(ID, TEST_STRING, TEST_STRING, fileVO);

        assertThat(pipelineApiService.fillTemplateForPipelineVersion(ID, TEST_STRING, TEST_STRING, fileVO))
                .isEqualTo(TEST_ARRAY);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyFillTemplateForPipelineVersionWhenPermissionIsNotGranted() {
        initAclEntity(pipeline);
        doReturn(TEST_ARRAY).when(mockFileGenerationManager)
                .fillTemplateForPipelineVersion(ID, TEST_STRING, TEST_STRING, fileVO);

        assertThrows(AccessDeniedException.class, () ->
                pipelineApiService.fillTemplateForPipelineVersion(ID, TEST_STRING, TEST_STRING, fileVO));
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

        assertThrows(AccessDeniedException.class, () -> {
            try {
                pipelineApiService.registerPipelineVersion(pipelineVersionVO);
            } catch (GitClientException e) {
                e.printStackTrace();
            }
        });
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetPipelineCloneUrlForAdmin() {
        doReturn(TEST_STRING).when(mockPipelineManager).getPipelineCloneUrl(ID);

        assertThat(pipelineApiService.getPipelineCloneUrl(ID)).isEqualTo(TEST_STRING);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetPipelineCloneUrlWhenPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.READ);
        doReturn(TEST_STRING).when(mockPipelineManager).getPipelineCloneUrl(ID);

        assertThat(pipelineApiService.getPipelineCloneUrl(ID)).isEqualTo(TEST_STRING);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyGetPipelineCloneUrlWhenPermissionIsNotGranted() {
        initAclEntity(pipeline);
        doReturn(TEST_STRING).when(mockPipelineManager).getPipelineCloneUrl(ID);

        assertThrows(AccessDeniedException.class, () -> pipelineApiService.getPipelineCloneUrl(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAllPropertiesByPipelineIdForAdmin() {
        doReturn(propertyList).when(mockPropertyManager).loadAllPropertiesByPipelineId(ID);

        assertThat(pipelineApiService.loadAllPropertiesByPipelineId(ID)).isEqualTo(propertyList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadAllPropertiesByPipelineIdWhenPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.READ);
        doReturn(propertyList).when(mockPropertyManager).loadAllPropertiesByPipelineId(ID);

        assertThat(pipelineApiService.loadAllPropertiesByPipelineId(ID)).isEqualTo(propertyList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadAllPropertiesByPipelineIdWhenPermissionIsNotGranted() {
        initAclEntity(pipeline);
        doReturn(propertyList).when(mockPropertyManager).loadAllPropertiesByPipelineId(ID);

        assertThrows(AccessDeniedException.class, () -> pipelineApiService.loadAllPropertiesByPipelineId(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadPropertyForAdmin() {
        doReturn(property).when(mockPropertyManager).loadProperty(TEST_STRING, ID);

        assertThat(pipelineApiService.loadProperty(TEST_STRING, ID)).isEqualTo(property);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadPropertyWhenPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.READ);
        doReturn(property).when(mockPropertyManager).loadProperty(TEST_STRING, ID);

        assertThat(pipelineApiService.loadProperty(TEST_STRING, ID)).isEqualTo(property);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadPropertyWhenPermissionIsNotGranted() {
        initAclEntity(pipeline);
        doReturn(property).when(mockPropertyManager).loadProperty(TEST_STRING, ID);

        assertThrows(AccessDeniedException.class, () -> pipelineApiService.loadProperty(TEST_STRING, ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldSavePropertyForAdmin() {
        doReturn(property).when(mockPropertyManager).saveProperty(property);

        assertThat(pipelineApiService.saveProperty(property)).isEqualTo(property);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldSavePropertyWhenPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.WRITE);
        doReturn(property).when(mockPropertyManager).saveProperty(property);

        assertThat(pipelineApiService.saveProperty(property)).isEqualTo(property);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenySavePropertyWhenPermissionIsNotGranted() {
        initAclEntity(pipeline);
        doReturn(property).when(mockPropertyManager).saveProperty(property);

        assertThrows(AccessDeniedException.class, () -> pipelineApiService.saveProperty(property));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeletePropertyForAdmin() {
        doReturn(property).when(mockPropertyManager).deleteProperty(TEST_STRING, ID);

        assertThat(pipelineApiService.deleteProperty(TEST_STRING, ID)).isEqualTo(property);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDeletePropertyWhenPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.WRITE);
        doReturn(property).when(mockPropertyManager).deleteProperty(TEST_STRING, ID);

        assertThat(pipelineApiService.deleteProperty(TEST_STRING, ID)).isEqualTo(property);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyDeletePropertyWhenPermissionIsNotGranted() {
        initAclEntity(pipeline);
        doReturn(property).when(mockPropertyManager).deleteProperty(TEST_STRING, ID);

        assertThrows(AccessDeniedException.class, () -> pipelineApiService.deleteProperty(TEST_STRING, ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadPipelineByRepoUrlForAdmin() {
        doReturn(pipeline).when(mockPipelineManager).loadByRepoUrl(TEST_STRING);

        assertThat(pipelineApiService.loadPipelineByRepoUrl(TEST_STRING)).isEqualTo(pipeline);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadPipelineByRepoUrlWhenPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.READ);
        doReturn(pipeline).when(mockPipelineManager).loadByRepoUrl(TEST_STRING);

        final Pipeline returnedPipeline = pipelineApiService.loadPipelineByRepoUrl(TEST_STRING);

        assertThat(returnedPipeline).isEqualTo(pipeline);
        assertThat(returnedPipeline.getMask()).isEqualTo(READ_PERMISSION);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadPipelineByRepoUrlWhenPermissionIsNotGranted() {
        initAclEntity(pipeline);
        doReturn(pipeline).when(mockPipelineManager).loadByRepoUrl(TEST_STRING);

        assertThrows(AccessDeniedException.class, () -> pipelineApiService.loadPipelineByRepoUrl(TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCopyPipelineForAdmin() {
        doReturn(pipeline).when(mockPipelineManager).copyPipeline(ID, ID, TEST_STRING);

        assertThat(pipelineApiService.copyPipeline(ID, ID, TEST_STRING)).isEqualTo(pipeline);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = PIPELINE_MANAGER_ROLE)
    public void shouldCopyPipelineWhenPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.READ);
        initAclEntity(folder, AclPermission.WRITE);
        doReturn(pipeline).when(mockPipelineManager).copyPipeline(ID, ID, TEST_STRING);

        assertThat(pipelineApiService.copyPipeline(ID, ID, TEST_STRING)).isEqualTo(pipeline);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = PIPELINE_MANAGER_ROLE)
    public void shouldDenyCopyPipelineWhenPipelinePermissionIsNotGranted() {
        initAclEntity(pipeline);
        initAclEntity(folder, AclPermission.WRITE);
        doReturn(pipeline).when(mockPipelineManager).copyPipeline(ID, ID, TEST_STRING);

        assertThrows(AccessDeniedException.class, () -> pipelineApiService.copyPipeline(ID, ID, TEST_STRING));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = PIPELINE_MANAGER_ROLE)
    public void shouldDenyCopyPipelineWhenFolderPermissionIsNotGranted() {
        initAclEntity(pipeline, AclPermission.READ);
        initAclEntity(folder);
        doReturn(pipeline).when(mockPipelineManager).copyPipeline(ID, ID, TEST_STRING);

        assertThrows(AccessDeniedException.class, () -> pipelineApiService.copyPipeline(ID, ID, TEST_STRING));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyCopyPipelineForUserRole() {
        initAclEntity(pipeline, AclPermission.READ);
        initAclEntity(folder, AclPermission.WRITE);
        doReturn(pipeline).when(mockPipelineManager).copyPipeline(ID, ID, TEST_STRING);

        assertThrows(AccessDeniedException.class, () -> pipelineApiService.copyPipeline(ID, ID, TEST_STRING));
    }
}
