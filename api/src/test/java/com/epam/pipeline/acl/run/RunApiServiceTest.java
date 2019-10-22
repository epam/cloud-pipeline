package com.epam.pipeline.acl.run;

import com.epam.pipeline.app.AclTestConfiguration;
import com.epam.pipeline.controller.vo.PermissionGrantVO;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.security.acl.AclPermission;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = AclTestConfiguration.class)
@TestPropertySource(value = {"classpath:test-application.properties"})
@Transactional
public class RunApiServiceTest {

    private static final Long TEST_RUN_ID = 1L;
    private static final String TEST_OWNER = "OWNER";
    private static final String TEST_USER1 = "USER_1";
    private static final String TEST_ADMIN_NAME = "ADMIN";
    private static final String TEST_ADMIN_ROLE = "ADMIN";
    private static final Long TEST_PIPELINE_ID = 10L;

    @Autowired
    private RunApiService runApiService;

    @Autowired
    private GrantPermissionManager grantPermissionManager;

    @Autowired
    private PipelineRunManager mockRunManager;

    @Autowired
    private PipelineManager mockPipelineManager;

    @Autowired
    private EntityManager mockEntityManager;

    @Autowired
    private UserManager mockUserManager;

    @Test
    @WithMockUser(username = TEST_OWNER)
    public void loadToolRunShouldBeAllowedForOwner() {
        initTestToolPipelineRun();
        PipelineRun loaded = runApiService.loadPipelineRun(TEST_RUN_ID);
        Assert.assertEquals(TEST_RUN_ID, loaded.getId());
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(username = TEST_USER1)
    public void loadToolRunShouldBeDeniedForNonOwner() {
        initTestToolPipelineRun();
        runApiService.loadPipelineRun(TEST_RUN_ID);
    }

    @Test
    @WithMockUser(username = TEST_ADMIN_NAME, roles = {TEST_ADMIN_ROLE})
    public void loadToolRunShouldBeAllowedForAdmin() {
        initTestToolPipelineRun();
        PipelineRun loaded = runApiService.loadPipelineRun(TEST_RUN_ID);
        Assert.assertEquals(TEST_RUN_ID, loaded.getId());
    }

    @Test
    @WithMockUser(username = TEST_OWNER)
    public void loadPipelineRunShouldBeAllowedForOwner() {
        initTestPipelineRun();
        PipelineRun loaded = runApiService.loadPipelineRun(TEST_RUN_ID);
        Assert.assertEquals(TEST_RUN_ID, loaded.getId());
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(username = TEST_USER1)
    public void loadPipelineRunShouldBeDeniedForNonOwner() {
        initTestPipelineRun();
        runApiService.loadPipelineRun(TEST_RUN_ID);
    }

    @Test
    @WithMockUser(username = TEST_USER1)
    public void loadPipelineRunShouldBeAllowedWhenUserIsOwnerOfPipeline() {
        final Pipeline pipeline = initTestPipeline();
        pipeline.setOwner(TEST_USER1);
        initTestPipelineRun(pipeline);
        final PipelineRun loaded = runApiService.loadPipelineRun(TEST_RUN_ID);
        Assert.assertEquals(TEST_RUN_ID, loaded.getId());
    }

    @Test
    @WithMockUser(username = TEST_USER1)
    public void loadPipelineRunShouldBeAllowedWhenUserHasReadPermissionOnPipeline() {
        final Pipeline pipeline = initTestPipeline();
        initTestPipelineRun(pipeline);
        grantPermissionOnPipeline(pipeline, TEST_USER1, TEST_OWNER, AclPermission.READ.getMask());
        final PipelineRun loaded = runApiService.loadPipelineRun(TEST_RUN_ID);
        Assert.assertEquals(TEST_RUN_ID, loaded.getId());
    }

    @Test
    @WithMockUser(username = TEST_ADMIN_NAME, roles = {TEST_ADMIN_ROLE})
    public void loadPipelineRunShouldBeAllowedForAdmin() {
        initTestToolPipelineRun();
        final PipelineRun loaded = runApiService.loadPipelineRun(TEST_RUN_ID);
        Assert.assertEquals(TEST_RUN_ID, loaded.getId());
    }

    private PipelineRun initTestToolPipelineRun() {
        final PipelineRun pipelineRun = new PipelineRun();
        pipelineRun.setId(TEST_RUN_ID);
        pipelineRun.setOwner(TEST_OWNER);
        doReturn(pipelineRun).when(mockRunManager).loadPipelineRun(eq(TEST_RUN_ID));
        return pipelineRun;
    }

    private PipelineRun initTestPipelineRun() {
        return initTestPipelineRun(null);
    }

    private PipelineRun initTestPipelineRun(final Pipeline parent) {
        final PipelineRun pipelineRun = new PipelineRun();
        pipelineRun.setId(TEST_RUN_ID);
        pipelineRun.setOwner(TEST_OWNER);
        pipelineRun.setPipelineId(TEST_PIPELINE_ID);
        doReturn(pipelineRun).when(mockRunManager).loadPipelineRun(eq(TEST_RUN_ID));
        doReturn(parent).when(mockRunManager).loadRunParent(eq(pipelineRun));
        return pipelineRun;
    }

    private Pipeline initTestPipeline() {
        final Pipeline pipeline = new Pipeline();
        pipeline.setId(TEST_PIPELINE_ID);
        pipeline.setOwner(TEST_OWNER);
        doReturn(pipeline).when(mockPipelineManager).load(eq(TEST_PIPELINE_ID));
        doReturn(pipeline).when(mockPipelineManager).load(eq(TEST_PIPELINE_ID), anyBoolean());
        return pipeline;
    }

    private void grantPermissionOnPipeline(final AbstractSecuredEntity entity,
                                           final String userName,
                                           final String owner,
                                           final int mask) {
        final PermissionGrantVO permissionGrant = new PermissionGrantVO();
        permissionGrant.setAclClass(entity.getAclClass());
        permissionGrant.setId(entity.getId());
        permissionGrant.setPrincipal(true);
        permissionGrant.setUserName(userName);
        permissionGrant.setMask(mask);
        doReturn(entity).when(mockEntityManager).load(eq(entity.getAclClass()), eq(entity.getId()));
        grantPermissionManager.setPermissions(permissionGrant);

        doReturn(new UserContext(null, owner)).when(mockUserManager).loadUserContext(eq(owner));
        grantPermissionManager.changeOwner(entity.getId(), entity.getAclClass(), owner);
    }
}