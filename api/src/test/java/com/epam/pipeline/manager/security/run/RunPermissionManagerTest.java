/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.security.run;

import com.epam.pipeline.acl.pipeline.PipelineApiService;
import com.epam.pipeline.controller.vo.PagingRunFilterVO;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.contextual.ContextualPreferenceManager;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.pipeline.PipelineRunAsManager;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.ToolGroupManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import com.epam.pipeline.security.acl.AclPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The permissions that a child run with no pipeline gets from its parent run. The parent run is readable by its
 * pipeline: the user can read the pipeline, and runs inherit the permissions of their pipelines by default. The mocks
 * the tests do not use are the other dependencies of the manager.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("PMD.UnusedPrivateField")
public class RunPermissionManagerTest {

    private static final String USER = "USER";
    private static final String OWNER = "OWNER";
    private static final String ANOTHER_OWNER = "ANOTHER_OWNER";
    private static final Long PARENT_RUN_ID = 1L;
    private static final Long CHILD_RUN_ID = 2L;
    private static final Long SECOND_CHILD_RUN_ID = 3L;
    private static final Long GRANDCHILD_RUN_ID = 4L;
    private static final Long PARENT_PIPELINE_ID = 10L;
    private static final Long CHILD_PIPELINE_ID = 11L;

    @Mock
    private PipelineRunManager runManager;
    @Mock
    private PipelineRunCRUDService runCRUDService;
    @Mock
    private PipelineApiService pipelineApiService;
    @Mock
    private CheckPermissionHelper permissionsHelper;
    @Mock
    private AuthManager authManager;
    @Mock
    private DockerRegistryManager registryManager;
    @Mock
    private ToolGroupManager toolGroupManager;
    @Mock
    private ToolManager toolManager;
    @Mock
    private ContextualPreferenceManager preferenceManager;
    @Mock
    private PipelineRunAsManager runAsManager;

    @InjectMocks
    private RunPermissionManager runPermissionManager;

    private Pipeline parentPipeline;
    private PipelineRun parentRun;

    @BeforeEach
    public void setUp() {
        doReturn(USER).when(authManager).getAuthorizedUser();
        parentPipeline = pipeline(PARENT_PIPELINE_ID);
        parentRun = run(PARENT_RUN_ID, OWNER, PARENT_PIPELINE_ID, null);
        doReturn(parentPipeline).when(runManager).loadRunParent(parentRun);
        doReturn(Optional.of(parentRun)).when(runCRUDService).findRunByRunId(PARENT_RUN_ID);
    }

    @AfterEach
    public void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void shouldReadChildRunWhenUserCanReadParentRun() {
        allowOnParentPipeline(AclPermission.READ_NAME);

        assertTrue(runPermissionManager.runPermission(childRun(CHILD_RUN_ID), AclPermission.READ_NAME));
    }

    @Test
    public void shouldNotReadChildRunWhenUserCannotReadParentRun() {
        assertFalse(runPermissionManager.runPermission(childRun(CHILD_RUN_ID), AclPermission.READ_NAME));
    }

    @Test
    public void shouldNotReadChildRunWhenParentRunDoesNotExist() {
        allowOnParentPipeline(AclPermission.READ_NAME);
        doReturn(Optional.empty()).when(runCRUDService).findRunByRunId(PARENT_RUN_ID);

        assertFalse(runPermissionManager.runPermission(childRun(CHILD_RUN_ID), AclPermission.READ_NAME));
    }

    @Test
    public void shouldNotReadChildRunOfPipelineWhenUserCanReadParentRunOnly() {
        allowOnParentPipeline(AclPermission.READ_NAME);
        final PipelineRun childRun = run(CHILD_RUN_ID, OWNER, CHILD_PIPELINE_ID, PARENT_RUN_ID);
        doReturn(pipeline(CHILD_PIPELINE_ID)).when(runManager).loadRunParent(childRun);

        assertFalse(runPermissionManager.runPermission(childRun, AclPermission.READ_NAME));
    }

    @Test
    public void shouldReadChildRunWithZeroPipelineIdWhenUserCanReadParentRun() {
        allowOnParentPipeline(AclPermission.READ_NAME);
        final PipelineRun childRun = run(CHILD_RUN_ID, OWNER, 0L, PARENT_RUN_ID);

        assertTrue(runPermissionManager.runPermission(childRun, AclPermission.READ_NAME));
    }

    @Test
    public void shouldNotReadGrandchildRunWhenUserCanReadGrandparentRunOnly() {
        allowOnParentPipeline(AclPermission.READ_NAME);
        final PipelineRun childRun = childRun(CHILD_RUN_ID);
        doReturn(Optional.of(childRun)).when(runCRUDService).findRunByRunId(CHILD_RUN_ID);
        final PipelineRun grandchildRun = run(GRANDCHILD_RUN_ID, OWNER, null, CHILD_RUN_ID);

        assertTrue(runPermissionManager.runPermission(childRun, AclPermission.READ_NAME));
        assertFalse(runPermissionManager.runPermission(grandchildRun, AclPermission.READ_NAME));
    }

    @Test
    public void shouldNotReadChildRunOfAnotherOwnerWhenUserCanReadParentRun() {
        allowOnParentPipeline(AclPermission.READ_NAME);
        final PipelineRun childRun = run(CHILD_RUN_ID, ANOTHER_OWNER, null, PARENT_RUN_ID);

        assertFalse(runPermissionManager.runPermission(childRun, AclPermission.READ_NAME));
    }

    @Test
    public void shouldGiveOnlyReadOnChildRunWhenUserCanExecuteParentRun() {
        allowOnParentPipeline(AclPermission.READ_NAME);
        allowOnParentPipeline(AclPermission.EXECUTE_NAME);
        allowOnParentPipeline(AclPermission.WRITE_NAME);
        final PipelineRun childRun = childRun(CHILD_RUN_ID);

        assertTrue(runPermissionManager.runPermission(parentRun, AclPermission.EXECUTE_NAME));
        assertTrue(runPermissionManager.runPermission(childRun, AclPermission.READ_NAME));
        assertFalse(runPermissionManager.runPermission(childRun, AclPermission.EXECUTE_NAME));
        assertFalse(runPermissionManager.runPermission(childRun, AclPermission.WRITE_NAME));
    }

    @Test
    public void shouldCheckParentRunOncePerRequest() {
        allowOnParentPipeline(AclPermission.READ_NAME);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        assertTrue(runPermissionManager.runPermission(childRun(CHILD_RUN_ID), AclPermission.READ_NAME));
        assertTrue(runPermissionManager.runPermission(childRun(SECOND_CHILD_RUN_ID), AclPermission.READ_NAME));
        verify(runCRUDService, times(1)).findRunByRunId(PARENT_RUN_ID);
    }

    @Test
    public void shouldCheckParentRunEachTimeOutsideOfRequest() {
        allowOnParentPipeline(AclPermission.READ_NAME);

        assertTrue(runPermissionManager.runPermission(childRun(CHILD_RUN_ID), AclPermission.READ_NAME));
        assertTrue(runPermissionManager.runPermission(childRun(SECOND_CHILD_RUN_ID), AclPermission.READ_NAME));
        verify(runCRUDService, times(2)).findRunByRunId(PARENT_RUN_ID);
    }

    @Test
    public void shouldAllowChildRunsInFilterWhenUserCanReadParentRun() {
        allowOnParentPipeline(AclPermission.READ_NAME);
        final PagingRunFilterVO filter = new PagingRunFilterVO();
        filter.setParentId(PARENT_RUN_ID);

        runPermissionManager.extendFilter(filter);

        assertEquals(USER.toLowerCase(), filter.getOwnershipFilter());
        assertEquals(PARENT_RUN_ID, filter.getAllowedParentRunId());
    }

    @Test
    public void shouldNotAllowChildRunsInFilterWhenUserCannotReadParentRun() {
        final PagingRunFilterVO filter = new PagingRunFilterVO();
        filter.setParentId(PARENT_RUN_ID);
        filter.setAllowedParentRunId(PARENT_RUN_ID);

        runPermissionManager.extendFilter(filter);

        assertEquals(USER.toLowerCase(), filter.getOwnershipFilter());
        assertNull(filter.getAllowedParentRunId());
    }

    @Test
    public void shouldNotAllowChildRunsInFilterWithoutParentRun() {
        allowOnParentPipeline(AclPermission.READ_NAME);
        final PagingRunFilterVO filter = new PagingRunFilterVO();

        runPermissionManager.extendFilter(filter);

        assertNull(filter.getAllowedParentRunId());
        verify(runCRUDService, never()).findRunByRunId(any());
    }

    private void allowOnParentPipeline(final String permissionName) {
        doReturn(true).when(permissionsHelper)
                .isAllowedIgnoringScopedReaderAndExecutor(permissionName, parentPipeline);
    }

    /**
     * A child run with no pipeline, of the parent run's owner: a tool run, as the workers of an auto-scaled cluster
     * are.
     */
    private PipelineRun childRun(final Long runId) {
        return run(runId, OWNER, null, PARENT_RUN_ID);
    }

    private static PipelineRun run(final Long runId, final String owner, final Long pipelineId,
                                   final Long parentRunId) {
        final PipelineRun run = new PipelineRun();
        run.setId(runId);
        run.setOwner(owner);
        run.setPipelineId(pipelineId);
        run.setParentRunId(parentRunId);
        return run;
    }

    private static Pipeline pipeline(final Long pipelineId) {
        final Pipeline pipeline = new Pipeline();
        pipeline.setId(pipelineId);
        pipeline.setOwner(OWNER);
        return pipeline;
    }
}
