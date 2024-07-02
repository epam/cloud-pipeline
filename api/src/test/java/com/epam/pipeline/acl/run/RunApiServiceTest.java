/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.acl.run;

import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.PagingRunFilterVO;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.PipeRunCmdStartVO;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.pipeline.run.RunVisibilityPolicy;
import com.epam.pipeline.entity.preference.PreferenceType;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.contextual.ContextualPreferenceManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.PipelineRunAsManager;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.IMAGE1;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.getTool;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipeline;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipelineRun;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipelineStart;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;

import static com.epam.pipeline.util.CustomAssertions.notInvoked;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class RunApiServiceTest extends AbstractAclTest {

    private static final String VISIBILITY_PREFERENCE_KEY = SystemPreferences.RUN_VISIBILITY_POLICY.getKey();
    private final PipelineRun pipelineRun = getPipelineRun(ID, ANOTHER_SIMPLE_USER);
    private final Pipeline pipeline = getPipeline(ANOTHER_SIMPLE_USER);
    private final UserContext anotherUserContext = new UserContext(ID, ANOTHER_SIMPLE_USER);

    @Autowired
    private RunApiService runApiService;

    @Autowired
    private PipelineRunManager mockRunManager;

    @Autowired
    private PipelineRunCRUDService mockRunCRUDService;

    @Autowired
    private PipelineManager mockPipelineManager;

    @Autowired
    private ContextualPreferenceManager mockPreferenceManager;

    @Autowired
    private EntityManager mockEntityManager;

    @Autowired
    private ToolManager mockToolManager;

    @Autowired
    private PipelineRunAsManager mockPipelineRunAsManager;

    @Test
    @WithMockUser
    public void shouldLoadToolRunForOwner() {
        doReturn(pipelineRun).when(mockRunCRUDService).loadRunById(ID);
        doReturn(pipelineRun).when(mockRunManager).loadPipelineRun(ID);
        mockAuthUser(ANOTHER_SIMPLE_USER);

        assertThat(runApiService.loadPipelineRun(ID)).isEqualTo(pipelineRun);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadToolRunForNonOwner() {
        doReturn(pipelineRun).when(mockRunCRUDService).loadRunById(ID);

        assertThrows(AccessDeniedException.class, () -> runApiService.loadPipelineRun(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadToolRunForAdmin() {
        doReturn(pipelineRun).when(mockRunManager).loadPipelineRun(ID);

        assertThat(runApiService.loadPipelineRun(ID)).isEqualTo(pipelineRun);
    }

    @Test
    @WithMockUser
    public void shouldLoadPipelineRunForOwner() {
        final PipelineRun pipelineRun = getPipelineRun(ID, SIMPLE_USER);
        doReturn(pipelineRun).when(mockRunCRUDService).loadRunById(ID);
        doReturn(pipelineRun).when(mockRunManager).loadPipelineRun(ID);
        mockAuthUser(SIMPLE_USER);

        assertThat(runApiService.loadPipelineRun(ID).getId()).isEqualTo(ID);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadPipelineRunForNonOwner() {
        doReturn(pipelineRun).when(mockRunCRUDService).loadRunById(ID);
        doReturn(pipeline).when(mockRunManager).loadRunParent(pipelineRun);
        initAclEntity(pipeline);
        mockSecurityContext();

        assertThrows(AccessDeniedException.class, () -> runApiService.loadPipelineRun(ID));
    }

    @Test
    @WithMockUser(username = ANOTHER_SIMPLE_USER)
    public void shouldLoadPipelineRunWhenUserIsOwnerOfPipeline() {
        doReturn(pipelineRun).when(mockRunCRUDService).loadRunById(ID);
        doReturn(pipelineRun).when(mockRunManager).loadPipelineRun(ID);
        doReturn(pipeline).when(mockRunManager).loadRunParent(pipelineRun);
        initAclEntity(pipeline);
        mockSecurityContext();

        assertThat(runApiService.loadPipelineRun(ID)).isEqualTo(pipelineRun);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadPipelineRunWhenUserHasReadPermissionOnPipeline() {
        final PipelineRun pipelineRun = getPipelineRun(ID, ANOTHER_SIMPLE_USER);
        final Pipeline pipeline = getPipeline(ANOTHER_SIMPLE_USER);
        doReturn(pipelineRun).when(mockRunManager).loadPipelineRun(ID);
        doReturn(pipelineRun).when(mockRunCRUDService).loadRunById(ID);
        doReturn(pipeline).when(mockRunManager).loadRunParent(pipelineRun);
        initAclEntity(pipelineRun, AclPermission.READ);
        initAclEntity(pipeline, AclPermission.READ);
        mockSecurityContext();

        assertThat(runApiService.loadPipelineRun(ID)).isEqualTo(pipelineRun);
    }

    @Test
    @WithMockUser(username = ANOTHER_SIMPLE_USER)
    public void shouldNotInheritPermissionsLoadPipelineRunWithOwnerVisibilityEnabled() {
        enableVisibilityPolicy(RunVisibilityPolicy.OWNER);
        doReturn(pipelineRun).when(mockRunCRUDService).loadRunById(ID);
        doReturn(pipeline).when(mockRunManager).loadRunParent(pipelineRun);
        initAclEntity(pipeline, AclPermission.READ);
        mockSecurityContext();

        assertThrows(AccessDeniedException.class, () -> runApiService.loadPipelineRun(ID));
    }

    @Test
    @WithMockUser(username = ANOTHER_SIMPLE_USER)
    public void shouldLoadPipelineRunWithPipelineInheritVisibilityEnabled() {
        enableVisibilityPolicy(RunVisibilityPolicy.OWNER);

        final Pipeline pipeline = getPipeline(ANOTHER_SIMPLE_USER);
        pipeline.setVisibility(RunVisibilityPolicy.INHERIT);
        doReturn(pipelineRun).when(mockRunManager).loadPipelineRun(ID);
        doReturn(pipelineRun).when(mockRunCRUDService).loadRunById(ID);
        doReturn(pipeline).when(mockRunManager).loadRunParent(pipelineRun);
        initAclEntity(pipelineRun, AclPermission.READ);
        initAclEntity(pipeline, AclPermission.READ);
        mockSecurityContext();

        assertThat(runApiService.loadPipelineRun(ID)).isEqualTo(pipelineRun);
    }

    @Test
    @WithMockUser(username = ANOTHER_SIMPLE_USER)
    public void shouldNotInheritPermissionsLoadPipelineRunWithPipelineOwnerVisibilityEnabled() {
        enableVisibilityPolicy(RunVisibilityPolicy.INHERIT);

        final Pipeline pipeline = getPipeline(ANOTHER_SIMPLE_USER);
        pipeline.setVisibility(RunVisibilityPolicy.OWNER);
        doReturn(pipelineRun).when(mockRunCRUDService).loadRunById(ID);
        doReturn(pipeline).when(mockRunManager).loadRunParent(pipelineRun);
        initAclEntity(pipeline, AclPermission.READ);
        mockSecurityContext();

        assertThrows(AccessDeniedException.class, () -> runApiService.loadPipelineRun(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadPipelineRunForAdmin() {
        doReturn(pipelineRun).when(mockRunManager).loadPipelineRun(ID);
        doReturn(pipeline).when(mockRunManager).loadRunParent(pipelineRun);
        initAclEntity(pipeline);

        assertThat(runApiService.loadPipelineRun(ID)).isEqualTo(pipelineRun);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldFailLoadPipelineRunWithoutPipelineForNotOwner() {
        doReturn(pipelineRun).when(mockRunCRUDService).loadRunById(ID);
        mockAuthUser(SIMPLE_USER);

        assertThrows(AccessDeniedException.class, () -> runApiService.loadPipelineRun(ID));
        verify(mockRunManager).loadRunParent(pipelineRun);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldIncludeOwnerAndPipelinesWithInheritedVisibility() {
        final Pipeline pipeline = getPipeline(SIMPLE_USER);
        doReturn(mutableListOf(pipeline)).when(mockPipelineManager).loadAllPipelines(eq(false));
        initAclEntity(pipeline);
        mockAuthUser(SIMPLE_USER);

        final PagingRunFilterVO filter = new PagingRunFilterVO();
        doReturn(new PagedResult<>(Collections.singletonList(pipelineRun), 1))
                .when(mockRunManager)
                .searchPipelineRuns(eq(filter), eq(false));
        final PagedResult<List<PipelineRun>> result = runApiService.searchPipelineRuns(filter, false);

        assertThat(filter.getOwnershipFilter()).isEqualToIgnoringCase(SIMPLE_USER);
        assertThat(filter.getAllowedPipelines()).contains(ID);
        assertThat(result.getTotalCount()).isEqualTo(1);
        assertThat(result.getElements()).contains(pipelineRun);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldNotIncludePipelinesWhenPipelineOwnerVisibilityAndGlobalInherit() {
        final Pipeline pipeline = getPipeline(SIMPLE_USER);
        pipeline.setVisibility(RunVisibilityPolicy.OWNER);
        doReturn(mutableListOf(pipeline)).when(mockPipelineManager).loadAllPipelines(eq(false));
        initAclEntity(pipeline);
        mockAuthUser(SIMPLE_USER);

        final PagingRunFilterVO filter = new PagingRunFilterVO();
        doReturn(new PagedResult<>(Collections.singletonList(pipelineRun), 1))
                .when(mockRunManager).searchPipelineRuns(eq(filter), eq(false));
        runApiService.searchPipelineRuns(filter, false);

        assertThat(filter.getOwnershipFilter()).isEqualToIgnoringCase(SIMPLE_USER);
        assertThat(filter.getAllowedPipelines()).isEmpty();
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldIncludePipelinesWhenPipelineInheritVisibilityAndGlobalOwner() {
        enableVisibilityPolicy(RunVisibilityPolicy.OWNER);

        final Pipeline pipeline = getPipeline(SIMPLE_USER);
        pipeline.setVisibility(RunVisibilityPolicy.INHERIT);
        doReturn(mutableListOf(pipeline)).when(mockPipelineManager).loadAllPipelines(eq(false));
        initAclEntity(pipeline);
        mockAuthUser(SIMPLE_USER);

        final PagingRunFilterVO filter = new PagingRunFilterVO();
        doReturn(new PagedResult<>(Collections.singletonList(pipelineRun), 1))
                .when(mockRunManager).searchPipelineRuns(eq(filter), eq(false));
        runApiService.searchPipelineRuns(filter, false);

        assertThat(filter.getOwnershipFilter()).isEqualToIgnoringCase(SIMPLE_USER);
        assertThat(filter.getAllowedPipelines()).contains(ID);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGenerateRunCmdIfUserHasPermissionOnPipeline() {
        final PipeRunCmdStartVO pipeRunCmdStartVO = initPipeRunCmdStartVO(pipeline, null);

        doReturn(pipeline).when(mockEntityManager).load(AclClass.PIPELINE, pipeline.getId());
        doReturn(TEST_STRING).when(mockRunManager).generateLaunchCommand(pipeRunCmdStartVO);
        initAclEntity(pipeline, AclPermission.EXECUTE);
        mockSecurityContext();

        assertThat(runApiService.generateLaunchCommand(pipeRunCmdStartVO)).isEqualTo(TEST_STRING);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGenerateCmdIfUserHasPermissionOnTool() {
        final Tool tool = getTool(ID, ANOTHER_SIMPLE_USER);
        tool.setImage(TEST_STRING);
        final PipeRunCmdStartVO pipeRunCmdStartVO = initPipeRunCmdStartVO(null, tool);
        doReturn(tool).when(mockToolManager).load(eq(ID));
        doReturn(tool).when(mockToolManager).loadByNameOrId(eq(TEST_STRING));
        doReturn(TEST_STRING).when(mockRunManager).generateLaunchCommand(pipeRunCmdStartVO);
        initAclEntity(tool, AclPermission.EXECUTE);
        mockSecurityContext();

        assertThat(runApiService.generateLaunchCommand(pipeRunCmdStartVO)).isEqualTo(TEST_STRING);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyGenerateRunCmdIfUserHasNoPermissionOnPipeline() {
        final PipeRunCmdStartVO pipeRunCmdStartVO = initPipeRunCmdStartVO(pipeline, null);
        doReturn(pipeline).when(mockEntityManager).load(AclClass.PIPELINE, pipeline.getId());
        initAclEntity(pipeline);
        mockSecurityContext();

        assertThrows(AccessDeniedException.class, () -> runApiService.generateLaunchCommand(pipeRunCmdStartVO));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyGenerateRunCmdIfUserHasNoPermissionOnTool() {
        final Tool tool = getTool(ID, ANOTHER_SIMPLE_USER);
        tool.setImage(TEST_STRING);
        final PipeRunCmdStartVO pipeRunCmdStartVO = initPipeRunCmdStartVO(null, tool);
        initAclEntity(tool);
        doReturn(tool).when(mockToolManager).load(eq(ID));
        doReturn(tool).when(mockToolManager).loadByNameOrId(eq(TEST_STRING));
        mockSecurityContext();

        assertThrows(AccessDeniedException.class, () -> runApiService.generateLaunchCommand(pipeRunCmdStartVO));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyGenerateRunCmdIfNoPipelineOrToolSpecified() {
        final PipeRunCmdStartVO pipeRunCmdStartVO = initPipeRunCmdStartVO(null, null);

        assertThrows(IllegalArgumentException.class, () -> runApiService.generateLaunchCommand(pipeRunCmdStartVO));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = ADMIN_ROLE)
    public void shouldRunToolForAdmin() {
        mockAuthUser(SIMPLE_USER);
        final PipelineStart pipelineStart = runVOForTool();
        doReturn(getTool(ID, OWNER_USER)).when(mockToolManager).loadByNameOrId(IMAGE1);
        doReturn(getPipelineRun()).when(mockRunManager).runCmd(pipelineStart);

        verifyRunCmd(pipelineStart);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldRunToolForNonAdmin() {
        mockAuthUser(SIMPLE_USER);
        final Tool tool = getTool(ID, OWNER_USER);
        doReturn(tool).when(mockToolManager).loadByNameOrId(IMAGE1);
        initAclEntity(tool, AclPermission.EXECUTE);
        final PipelineStart pipelineStart = runVOForTool();
        doReturn(getPipelineRun()).when(mockRunManager).runCmd(pipelineStart);

        verifyRunCmd(pipelineStart);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldFailRunToolIfPermissionIsNotGranted() {
        mockAuthUser(SIMPLE_USER);
        final Tool tool = getTool(ID, OWNER_USER);
        doReturn(tool).when(mockToolManager).loadByNameOrId(IMAGE1);
        initAclEntity(tool);
        final PipelineStart pipelineStart = runVOForTool();
        doReturn(getPipelineRun()).when(mockRunManager).runCmd(pipelineStart);

        assertThrows(AccessDeniedException.class, () -> runApiService.runCmd(pipelineStart));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = ADMIN_ROLE)
    public void shouldRunToolForAdminOnBehalfOfOtherUser() {
        mockAuthUser(SIMPLE_USER);
        final Tool tool = getTool(ID, OWNER_USER);
        doReturn(tool).when(mockToolManager).loadByNameOrId(IMAGE1);
        initAclEntity(tool, Collections.singletonList(
                new UserPermission(ANOTHER_SIMPLE_USER, AclPermission.EXECUTE.getMask())));
        final PipelineStart pipelineStart = runVOForTool();
        mockRunToolOnBehalfOfAnotherUser(pipelineStart);

        verifyRunToolOnBehalfOfAnotherUser(pipelineStart);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldRunToolForNonAdminOnBehalfOfOtherUser() {
        mockAuthUser(SIMPLE_USER);
        final Tool tool = getTool(ID, OWNER_USER);
        doReturn(tool).when(mockToolManager).loadByNameOrId(IMAGE1);
        initAclEntity(tool, Arrays.asList(
                new UserPermission(SIMPLE_USER, AclPermission.EXECUTE.getMask()),
                new UserPermission(ANOTHER_SIMPLE_USER, AclPermission.EXECUTE.getMask())));
        final PipelineStart pipelineStart = runVOForTool();
        mockRunToolOnBehalfOfAnotherUser(pipelineStart);

        verifyRunToolOnBehalfOfAnotherUser(pipelineStart);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = ADMIN_ROLE)
    public void shouldFailRunToolIfPermissionIsNotGrantedOnBehalfOfOtherUser() {
        mockAuthUser(SIMPLE_USER);
        final Tool tool = getTool(ID, OWNER_USER);
        doReturn(tool).when(mockToolManager).loadByNameOrId(IMAGE1);
        initAclEntity(tool);
        final PipelineStart pipelineStart = runVOForTool();
        mockRunToolOnBehalfOfAnotherUser(pipelineStart);

        assertThrows(AccessDeniedException.class, () -> runApiService.runCmd(pipelineStart));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = ADMIN_ROLE)
    public void shouldRunPipelineForAdmin() {
        mockAuthUser(SIMPLE_USER);
        final PipelineStart pipelineStart = runVOForPipeline();
        doReturn(getPipeline(ID, OWNER_USER)).when(mockEntityManager).load(AclClass.PIPELINE, ID);
        doReturn(getPipelineRun()).when(mockRunManager).runPipeline(pipelineStart);

        verifyRunPipeline(pipelineStart);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldRunPipelineForNonAdmin() {
        mockAuthUser(SIMPLE_USER);
        final Pipeline pipeline = getPipeline(ID, OWNER_USER);
        doReturn(pipeline).when(mockEntityManager).load(AclClass.PIPELINE, ID);
        initAclEntity(pipeline, AclPermission.EXECUTE);
        final PipelineStart pipelineStart = runVOForPipeline();
        doReturn(getPipelineRun()).when(mockRunManager).runPipeline(pipelineStart);

        verifyRunPipeline(pipelineStart);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldFailRunPipelineIfPermissionIsNotGranted() {
        mockAuthUser(SIMPLE_USER);
        final Pipeline pipeline = getPipeline(ID, OWNER_USER);
        doReturn(pipeline).when(mockEntityManager).load(AclClass.PIPELINE, ID);
        initAclEntity(pipeline);
        final PipelineStart pipelineStart = runVOForPipeline();
        doReturn(getPipelineRun()).when(mockRunManager).runPipeline(pipelineStart);

        assertThrows(AccessDeniedException.class, () -> runApiService.runPipeline(pipelineStart));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = ADMIN_ROLE)
    public void shouldRunPipelineForAdminOnBehalfOfOtherUser() {
        mockAuthUser(SIMPLE_USER);
        final Pipeline pipeline = getPipeline(ID, OWNER_USER);
        doReturn(pipeline).when(mockEntityManager).load(AclClass.PIPELINE, ID);
        initAclEntity(pipeline, Collections.singletonList(
                new UserPermission(ANOTHER_SIMPLE_USER, AclPermission.EXECUTE.getMask())));
        final PipelineStart pipelineStart = runVOForPipeline();
        mockRunPipelineOnBehalfOfAnotherUser(pipelineStart);

        verifyRunPipelineOnBehalfOfAnotherUser(pipelineStart);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldRunPipelineForNonAdminOnBehalfOfOtherUser() {
        mockAuthUser(SIMPLE_USER);
        final Pipeline pipeline = getPipeline(ID, OWNER_USER);
        doReturn(pipeline).when(mockEntityManager).load(AclClass.PIPELINE, ID);
        initAclEntity(pipeline, Arrays.asList(
                new UserPermission(SIMPLE_USER, AclPermission.EXECUTE.getMask()),
                new UserPermission(ANOTHER_SIMPLE_USER, AclPermission.EXECUTE.getMask())));
        final PipelineStart pipelineStart = runVOForPipeline();
        mockRunPipelineOnBehalfOfAnotherUser(pipelineStart);

        verifyRunPipelineOnBehalfOfAnotherUser(pipelineStart);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = ADMIN_ROLE)
    public void shouldNotRunPipelineOnBehalfOfOtherUserIfPermissionIsNotGranted() {
        mockAuthUser(SIMPLE_USER);
        final Pipeline pipeline = getPipeline(ID, OWNER_USER);
        doReturn(pipeline).when(mockEntityManager).load(AclClass.PIPELINE, ID);
        initAclEntity(pipeline);
        final PipelineStart pipelineStart = runVOForPipeline();
        mockRunPipelineOnBehalfOfAnotherUser(pipelineStart);

        assertThrows(AccessDeniedException.class, () -> runApiService.runPipeline(pipelineStart));
    }

    private void mockRunToolOnBehalfOfAnotherUser(final PipelineStart pipelineStart) {
        doReturn(ANOTHER_SIMPLE_USER).when(mockPipelineRunAsManager).getRunAsUserName(pipelineStart);
        doReturn(true).when(mockPipelineRunAsManager).hasCurrentUserAsRunner(ANOTHER_SIMPLE_USER);
        doReturn(getPipelineRun()).when(mockPipelineRunAsManager).runTool(pipelineStart);
        mockUserContext(anotherUserContext);
    }

    private void mockRunPipelineOnBehalfOfAnotherUser(final PipelineStart pipelineStart) {
        doReturn(ANOTHER_SIMPLE_USER).when(mockPipelineRunAsManager).getRunAsUserName(pipelineStart);
        doReturn(true).when(mockPipelineRunAsManager).hasCurrentUserAsRunner(ANOTHER_SIMPLE_USER);
        doReturn(true).when(mockPipelineRunAsManager).runAsAnotherUser(pipelineStart);
        doReturn(getPipelineRun()).when(mockPipelineRunAsManager).runPipeline(pipelineStart);
        mockUserContext(anotherUserContext);
    }

    private void verifyRunToolOnBehalfOfAnotherUser(final PipelineStart pipelineStart) {
        final PipelineRun pipelineRun = runApiService.runCmd(pipelineStart);
        assertThat(pipelineRun).isNotNull();

        verify(mockPipelineRunAsManager).runTool(pipelineStart);
        notInvoked(mockRunManager).runCmd(pipelineStart);
    }

    private void verifyRunCmd(final PipelineStart pipelineStart) {
        final PipelineRun pipelineRun = runApiService.runCmd(pipelineStart);
        assertThat(pipelineRun).isNotNull();

        verify(mockRunManager).runCmd(pipelineStart);
        notInvoked(mockPipelineRunAsManager).runTool(pipelineStart);
    }

    private void verifyRunPipeline(final PipelineStart pipelineStart) {
        final PipelineRun pipelineRun = runApiService.runPipeline(pipelineStart);
        assertThat(pipelineRun).isNotNull();

        verify(mockRunManager).runPipeline(pipelineStart);
        notInvoked(mockPipelineRunAsManager).runPipeline(pipelineStart);
    }

    private void verifyRunPipelineOnBehalfOfAnotherUser(final PipelineStart pipelineStart) {
        final PipelineRun pipelineRun = runApiService.runPipeline(pipelineStart);
        assertThat(pipelineRun).isNotNull();

        verify(mockPipelineRunAsManager).runPipeline(pipelineStart);
        notInvoked(mockRunManager).runPipeline(pipelineStart);
    }

    private PipeRunCmdStartVO initPipeRunCmdStartVO(final Pipeline pipeline, final Tool tool) {
        final PipelineStart pipelineStart = new PipelineStart();
        final PipeRunCmdStartVO pipeRunCmdStartVO = new PipeRunCmdStartVO();

        if (Objects.nonNull(pipeline)) {
            pipelineStart.setPipelineId(pipeline.getId());
        }

        if (Objects.nonNull(tool)) {
            pipelineStart.setDockerImage(tool.getImage());
        }

        pipeRunCmdStartVO.setPipelineStart(pipelineStart);
        return pipeRunCmdStartVO;
    }

    private void enableVisibilityPolicy(final RunVisibilityPolicy policy) {
        final ContextualPreference preference = new ContextualPreference(VISIBILITY_PREFERENCE_KEY,
                policy.name(), PreferenceType.OBJECT);
        doReturn(preference)
                .when(mockPreferenceManager)
                .search(eq(Collections.singletonList(VISIBILITY_PREFERENCE_KEY)));
    }

    private PipelineStart runVOForTool() {
        return getPipelineStart(Collections.emptyMap(), IMAGE1);
    }

    private PipelineStart runVOForPipeline() {
        final PipelineStart pipelineStart = getPipelineStart(Collections.emptyMap(), IMAGE1);
        pipelineStart.setPipelineId(ID);
        return pipelineStart;
    }
}
