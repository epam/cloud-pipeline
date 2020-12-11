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

package com.epam.pipeline.acl.run;

import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.PagingRunFilterVO;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.PipeRunCmdStartVO;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.preference.PreferenceType;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.contextual.ContextualPreferenceManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.run.RunVisibilityPolicy;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.getTool;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipeline;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipelineRun;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

public class RunApiServiceTest extends AbstractAclTest {

    private static final String VISIBILITY_PREFERENCE_KEY = SystemPreferences.RUN_VISIBILITY_POLICY.getKey();
    private final PipelineRun pipelineRun = getPipelineRun(ID, ANOTHER_SIMPLE_USER);
    private final Pipeline pipeline = getPipeline(ANOTHER_SIMPLE_USER);

    @Autowired
    private RunApiService runApiService;

    @Autowired
    private PipelineRunManager mockRunManager;

    @Autowired
    private PipelineManager mockPipelineManager;

    @Autowired
    private ContextualPreferenceManager mockPreferenceManager;

    @Autowired
    private EntityManager mockEntityManager;

    @Autowired
    private ToolManager mockToolManager;

    @Test
    @WithMockUser
    public void shouldLoadToolRunForOwner() {
        doReturn(pipelineRun).when(mockRunManager).loadPipelineRun(ID);
        mockAuthUser(ANOTHER_SIMPLE_USER);

        assertThat(runApiService.loadPipelineRun(ID)).isEqualTo(pipelineRun);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadToolRunForNonOwner() {
        doReturn(pipelineRun).when(mockRunManager).loadPipelineRun(ID);

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
        pipelineRun.setPipelineId(pipelineRun.getId());
        doReturn(pipelineRun).when(mockRunManager).loadPipelineRun(ID);
        mockAuthUser(SIMPLE_USER);

        assertThat(runApiService.loadPipelineRun(ID).getId()).isEqualTo(pipeline.getId());
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadPipelineRunForNonOwner() {
        doReturn(pipelineRun).when(mockRunManager).loadPipelineRun(ID);
        doReturn(pipeline).when(mockRunManager).loadRunParent(pipelineRun);
        initAclEntity(pipeline);
        mockSecurityContext();

        assertThrows(AccessDeniedException.class, () ->runApiService.loadPipelineRun(ID));
    }

    @Test
    @WithMockUser(username = ANOTHER_SIMPLE_USER)
    public void shouldLoadPipelineRunWhenUserIsOwnerOfPipeline() {
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
        doReturn(pipelineRun).when(mockRunManager).loadPipelineRun(ID);
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
}
