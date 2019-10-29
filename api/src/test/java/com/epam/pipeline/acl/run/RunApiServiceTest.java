/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.app.AclTestConfiguration;
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.PagingRunFilterVO;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.preference.PreferenceType;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.contextual.ContextualPreferenceManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.security.run.RunVisibilityPolicy;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.security.acl.JdbcMutableAclServiceImpl;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.acls.model.AclCache;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.acl.run.PipelineAclFactory.TEST_PIPELINE_ID;
import static com.epam.pipeline.acl.run.RunAclFactory.TEST_RUN_ID;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = AclTestConfiguration.class)
@TestPropertySource(value = {"classpath:test-application.properties"})
@Transactional
public class RunApiServiceTest {

    private static final String TEST_OWNER = "OWNER";
    private static final String TEST_USER1 = "USER_1";
    private static final String TEST_ADMIN_NAME = "ADMIN";
    private static final String TEST_ADMIN_ROLE = "ADMIN";
    private static final String VISIBILITY_PREFERENCE_KEY = SystemPreferences.RUN_VISIBILITY_POLICY.getKey();

    @Autowired
    private RunApiService runApiService;

    @Autowired
    private AclCache aclCache;

    @Autowired
    private PipelineRunManager mockRunManager;

    @Autowired
    private PipelineManager mockPipelineManager;

    @Autowired
    private ContextualPreferenceManager mockPreferenceManager;

    private RunAclFactory runAclFactory;
    private PipelineAclFactory pipelineAclFactory;

    @Autowired
    public void setRunAclFactory(final AuthManager authManager,
                                 final PipelineRunManager mockRunManager) {
        this.runAclFactory = new RunAclFactory(authManager, mockRunManager);
    }

    @Autowired
    public void setPipelineAclFactory(final AuthManager authManager,
                                      final GrantPermissionManager grantPermissionManager,
                                      final JdbcMutableAclServiceImpl aclService,
                                      final UserManager mockUserManager,
                                      final PipelineManager mockPipelineManager,
                                      final EntityManager mockEntityManager) {
        this.pipelineAclFactory = new PipelineAclFactory(authManager,
                grantPermissionManager, aclService, mockUserManager,
                mockPipelineManager, mockEntityManager);
    }

    @After
    public void tearDown() {
        aclCache.clearCache();
    }

    @Test
    @WithMockUser(username = TEST_OWNER)
    public void loadToolRunShouldBeAllowedForOwner() {
        runAclFactory.initToolPipelineRunForCurrentUser();
        assertThat(runApiService.loadPipelineRun(TEST_RUN_ID).getId(), equalTo(TEST_RUN_ID));
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(username = TEST_USER1)
    public void loadToolRunShouldBeDeniedForNonOwner() {
        runAclFactory.initToolPipelineRunForOwner(TEST_OWNER);
        runApiService.loadPipelineRun(TEST_RUN_ID);
    }

    @Test
    @WithMockUser(username = TEST_ADMIN_NAME, roles = {TEST_ADMIN_ROLE})
    public void loadToolRunShouldBeAllowedForAdmin() {
        runAclFactory.initToolPipelineRunForOwner(TEST_OWNER);
        assertThat(runApiService.loadPipelineRun(TEST_RUN_ID).getId(), equalTo(TEST_RUN_ID));
    }

    @Test
    @WithMockUser(username = TEST_USER1)
    public void loadPipelineRunShouldBeAllowedForOwner() {
        final Pipeline pipeline = pipelineAclFactory.initPipelineForCurrentUser();
        runAclFactory.initPipelineRunForCurrentUser(pipeline);
        assertThat(runApiService.loadPipelineRun(TEST_RUN_ID).getId(), equalTo(TEST_RUN_ID));
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(username = TEST_USER1)
    public void loadPipelineRunShouldBeDeniedForNonOwner() {
        final Pipeline pipeline = pipelineAclFactory.initPipelineForOwner(TEST_OWNER);
        runAclFactory.initPipelineRunForOwner(pipeline, TEST_OWNER);
        runApiService.loadPipelineRun(TEST_RUN_ID);
    }

    @Test
    @WithMockUser(username = TEST_USER1)
    public void loadPipelineRunShouldBeAllowedWhenUserIsOwnerOfPipeline() {
        final Pipeline pipeline = pipelineAclFactory.initPipelineForCurrentUser();
        runAclFactory.initPipelineRunForOwner(pipeline, TEST_OWNER);
        assertThat(runApiService.loadPipelineRun(TEST_RUN_ID).getId(), equalTo(TEST_RUN_ID));
    }

    @Test
    @WithMockUser(username = TEST_USER1)
    public void loadPipelineRunShouldBeAllowedWhenUserHasReadPermissionOnPipeline() {
        final Pipeline pipeline = pipelineAclFactory.initPipelineForOwnerWithPermissions(TEST_OWNER, TEST_USER1,
                AclPermission.READ.getMask());
        runAclFactory.initPipelineRunForOwner(pipeline, TEST_OWNER);
        assertThat(runApiService.loadPipelineRun(TEST_RUN_ID).getId(), equalTo(TEST_RUN_ID));
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(username = TEST_USER1)
    public void loadPipelineRunShouldNotInheritPermissionsWithOwnerVisibilityEnabled() {
        final Pipeline pipeline = pipelineAclFactory.initPipelineForOwnerWithPermissions(TEST_OWNER, TEST_USER1,
                AclPermission.READ.getMask());
        runAclFactory.initPipelineRunForOwner(pipeline, TEST_OWNER);
        enableVisibilityPolicy(RunVisibilityPolicy.OWNER);
        runApiService.loadPipelineRun(TEST_RUN_ID);
    }

    @Test
    @WithMockUser(username = TEST_ADMIN_NAME, roles = {TEST_ADMIN_ROLE})
    public void loadPipelineRunShouldBeAllowedForAdmin() {
        final Pipeline pipeline = pipelineAclFactory.initPipelineForOwner(TEST_OWNER);
        runAclFactory.initPipelineRunForOwner(pipeline, TEST_OWNER);
        assertThat(runApiService.loadPipelineRun(TEST_RUN_ID).getId(), equalTo(TEST_RUN_ID));
    }

    @Test
    @WithMockUser(username = TEST_USER1)
    public void filterShallIncludeOwnerAndPipelinesWithInheritedVisibility() {
        final Pipeline pipeline = pipelineAclFactory.initPipelineForCurrentUser();
        final PipelineRun run = runAclFactory.initPipelineRunForOwner(pipeline, TEST_OWNER);
        //we need a mutable list for ACL @PostFilter annotation
        doReturn(new ArrayList<>(Collections.singletonList(pipeline)))
                .when(mockPipelineManager)
                .loadAllPipelines(eq(false));

        final PagingRunFilterVO filter = new PagingRunFilterVO();
        doReturn(new PagedResult<>(Collections.singletonList(run), 1))
                .when(mockRunManager)
                .searchPipelineRuns(eq(filter), eq(false));
        final PagedResult<List<PipelineRun>> result = runApiService.searchPipelineRuns(filter, false);

        assertThat(filter.getOwnershipFilter(), equalToIgnoringCase(TEST_USER1));
        assertThat(filter.getAllowedPipelines(), contains(TEST_PIPELINE_ID));
        assertThat(result.getTotalCount(), equalTo(1));
        assertThat(result.getElements(), contains(run));
    }

    @Test
    @WithMockUser(username = TEST_USER1)
    public void filterShallIncludeOnlyOwnerWithOwnerVisibility() {
        final Pipeline pipeline = pipelineAclFactory.initPipelineForCurrentUser();
        final PipelineRun run = runAclFactory.initPipelineRunForOwner(pipeline, TEST_OWNER);
        //we need a mutable list for ACL @PostFilter annotation
        doReturn(new ArrayList<>(Collections.singletonList(pipeline)))
                .when(mockPipelineManager)
                .loadAllPipelines(eq(false));

        enableVisibilityPolicy(RunVisibilityPolicy.OWNER);

        final PagingRunFilterVO filter = new PagingRunFilterVO();
        doReturn(new PagedResult<>(Collections.emptyList(), 0))
                .when(mockRunManager)
                .searchPipelineRuns(eq(filter), eq(false));
        final PagedResult<List<PipelineRun>> result = runApiService.searchPipelineRuns(filter, false);

        assertThat(filter.getOwnershipFilter(), equalToIgnoringCase(TEST_USER1));
        assertThat(filter.getAllowedPipelines(), nullValue());
        assertThat(result.getElements(), empty());
        assertThat(result.getTotalCount(), equalTo(0));
    }

    @Test
    @WithMockUser(username = TEST_USER1)
    public void runPipelineShouldBeAllowedForPipelineOwner() {
        final Pipeline pipeline = pipelineAclFactory.initPipelineForCurrentUser();
        final PipelineRun pipelineRun = runAclFactory.initPipelineRunForCurrentUser(pipeline);
        final PipelineStart runVO = new PipelineStart();
        runVO.setPipelineId(pipeline.getId());
        doReturn(pipelineRun).when(mockRunManager).runPipeline(eq(runVO));
        final PipelineRun created = runApiService.runPipeline(runVO);
        assertThat(created.getId(), equalTo(pipelineRun.getId()));
    }

    @Test
    @WithMockUser(username = TEST_ADMIN_NAME, roles = {TEST_ADMIN_ROLE})
    public void runPipelineShouldBeAllowedForAdmin() {
        final Pipeline pipeline = pipelineAclFactory.initPipelineForOwner(TEST_OWNER);
        final PipelineRun pipelineRun = runAclFactory.initPipelineRunForOwner(pipeline, TEST_OWNER);
        final PipelineStart runVO = new PipelineStart();
        runVO.setPipelineId(pipeline.getId());
        doReturn(pipelineRun).when(mockRunManager).runPipeline(eq(runVO));
        final PipelineRun created = runApiService.runPipeline(runVO);
        assertThat(created.getId(), equalTo(pipelineRun.getId()));
    }

    @Test
    @WithMockUser(username = TEST_USER1)
    public void runPipelineShouldBeAllowedIfUserHasExecutePermissionOnPipeline() {
        final Pipeline pipeline = pipelineAclFactory.initPipelineForOwnerWithPermissions(TEST_OWNER, TEST_USER1,
                AclPermission.EXECUTE.getMask());
        final PipelineRun pipelineRun = runAclFactory.initPipelineRunForOwner(pipeline, TEST_OWNER);
        final PipelineStart runVO = new PipelineStart();
        runVO.setPipelineId(pipeline.getId());
        doReturn(pipelineRun).when(mockRunManager).runPipeline(eq(runVO));
        final PipelineRun created = runApiService.runPipeline(runVO);
        assertThat(created.getId(), equalTo(pipelineRun.getId()));
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(username = TEST_USER1)
    public void runPipelineShouldBeDeniedIfUserHasNoExecutePermissionOnPipeline() {
        final Pipeline pipeline = pipelineAclFactory.initPipelineForOwnerWithPermissions(TEST_OWNER, TEST_USER1,
                AclPermission.READ.getMask());
        final PipelineRun pipelineRun = runAclFactory.initPipelineRunForOwner(pipeline, TEST_OWNER);
        final PipelineStart runVO = new PipelineStart();
        runVO.setPipelineId(pipeline.getId());
        doReturn(pipelineRun).when(mockRunManager).runPipeline(eq(runVO));
        runApiService.runPipeline(runVO);
    }

    private void enableVisibilityPolicy(final RunVisibilityPolicy policy) {
        final ContextualPreference preference = new ContextualPreference(VISIBILITY_PREFERENCE_KEY,
                policy.name(), PreferenceType.OBJECT);
        doReturn(preference)
                .when(mockPreferenceManager)
                .search(eq(Collections.singletonList(VISIBILITY_PREFERENCE_KEY)));
    }
}