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

package com.epam.pipeline.manager.docker;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.Constants;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.DockerRegistryEvent;
import com.epam.pipeline.entity.pipeline.DockerRegistryEventEnvelope;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.pipeline.ToolScanStatus;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.pipeline.ToolGroupManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


public class DockerRegistryNotificationTest extends AbstractAclTest {

    private static final String ADMIN = "ADMIN";
    private static final String TEST_USER = "USER";
    private static final String TEST_REPO = "repository";
    private static final String TEST_REPO_WITH_EXTERNAL_PATH = "repository2";
    private static final String EXTERNAL_REPO_PATH = "external_repository";
    private static final String TEST_REPO_WITHOUT_WRITE_ACCESS = "repository3";
    private static final String TEST_IMAGE = "library/image";
    private static final String TEST_IMAGE_NEW_GROUP = "library2/image";
    private static final String PUSH_ACTION = "push";
    private static final String PULL_ACTION = "pull";
    private static final String ADMIN_ROLE = "ADMIN";
    public static final String LATEST = "latest";
    public static final long DOCKER_SIZE = 123456L;
    private static final Long REGISTRY_ID_1 = 1L;
    private static final Long REGISTRY_ID_2 = 8L;
    private static final Long TOOLGROUP_ID_1 = 2L;
    private static final Long TOOL_ID_1 = 3L;
    private static final Long CONTEXT_ID = 4L;
    private static final Long TOOL_ID_2 = 5L;
    private static final Long ROLE_ID = 6L;
    private static final Long TOOL_ID_3 = 7L;
    private static final Long REGISTRY_ID_3 = 9L;
    private static final CharSequence NOT_ALLOWED_MESSAGE = String.format("Permission is not granted for '%s' on '%S'",
            TEST_REPO_WITHOUT_WRITE_ACCESS, AclPermission.WRITE_NAME);
    private static final Long TOOLGROUP_ID_4 = 10L;
    private static final String ROLE_USER = "ROLE_USER";

    @Autowired
    private DockerRegistryDao mockDockerRegistryDao;

    @Autowired
    private GrantPermissionManager spyGrantPermissionManager;

    @Autowired
    private ToolManager mockToolManager;

    @Autowired
    private ToolGroupManager mockToolGroupManager;

    @Autowired
    private MetadataManager mockMetadataManager;

    @Autowired
    private ToolVersionManager mockToolVersionManager;

    @Autowired
    private MessageHelper mockMessageHelper;

    @Autowired
    private DockerClientFactory mockDockerClientFactory;

    @Autowired
    private UserManager mockUserManager;

    @InjectMocks
    private final DockerRegistryManager registryManager = new DockerRegistryManager();

    private DockerRegistry registry1;
    private DockerRegistry registry2;
    private DockerRegistry registry3;
    private ToolGroup toolGroup1;
    private ToolGroup toolGroup2;
    private ToolGroup toolGroup3;
    private Tool tool1;
    private Tool tool2;
    private Tool tool3;

    @Before
    public void setUp() {
        registry1 = new DockerRegistry();
        registry1.setId(REGISTRY_ID_1);
        registry1.setPath(TEST_REPO);
        registry1.setOwner(TEST_USER);

        registry2 = new DockerRegistry();
        registry2.setId(REGISTRY_ID_2);
        registry2.setPath(TEST_REPO_WITH_EXTERNAL_PATH);
        registry2.setExternalUrl(EXTERNAL_REPO_PATH);
        registry2.setOwner(ADMIN);

        registry3 = new DockerRegistry();
        registry3.setId(REGISTRY_ID_3);
        registry3.setPath(TEST_REPO_WITHOUT_WRITE_ACCESS);
        registry3.setOwner(ADMIN);

        toolGroup1 = new ToolGroup();
        toolGroup1.setId(TOOLGROUP_ID_1);
        toolGroup1.setName("library");
        toolGroup1.setRegistryId(registry1.getId());
        toolGroup1.setOwner(TEST_USER);

        toolGroup2 = new ToolGroup();
        toolGroup2.setName("library2");
        toolGroup2.setRegistryId(registry1.getId());
        toolGroup2.setOwner(TEST_USER);

        toolGroup3 = new ToolGroup();
        toolGroup3.setName("library");
        toolGroup3.setRegistryId(registry2.getId());
        toolGroup3.setOwner(TEST_USER);

        tool1 = buildTool(toolGroup1, TEST_IMAGE, registry1, TEST_USER);
        tool2 = buildTool(toolGroup2, TEST_IMAGE_NEW_GROUP, registry1, TEST_USER);
        tool3 = buildTool(toolGroup3, TEST_IMAGE, registry2, TEST_USER);

        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testEventCanEnableToolInExistingGroupWithRightUserAccess() {
        startUp1();
        DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Collections.singletonList(TEST_USER),
                Collections.singletonList(TEST_REPO),
                Collections.singletonList(TEST_IMAGE),
                Collections.singletonList(PUSH_ACTION));

        List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(TEST_REPO, eventsEnvelope);
        Assert.assertEquals(1, registeredTools.size());

        verify(mockToolVersionManager).updateOrCreateToolVersion(eq(TOOL_ID_1), eq(LATEST), eq(TEST_IMAGE),
                eq(registry1), eq(null));
        //TODO verify1;
    }

    @Test
    public void testEventCanCreateGroupIfItDoesntExist() {
        startUp2();
        DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Collections.singletonList(TEST_USER),
                Collections.singletonList(TEST_REPO),
                Collections.singletonList(TEST_IMAGE_NEW_GROUP),
                Collections.singletonList(PUSH_ACTION));

        List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(TEST_REPO, eventsEnvelope);
        Assert.assertEquals(1, registeredTools.size());

        verify(mockToolVersionManager).updateOrCreateToolVersion(eq(TOOL_ID_2), eq(LATEST), eq(TEST_IMAGE_NEW_GROUP),
                eq(registry1), eq(null));
        //TODO verify2;
    }

    @Test
    public void testEventWontProcessPullAction() {
        DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Collections.singletonList(TEST_USER),
                Collections.singletonList(TEST_REPO),
                Collections.singletonList(TEST_IMAGE),
                Collections.singletonList(PULL_ACTION));

        List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(TEST_REPO, eventsEnvelope);
        Assert.assertEquals(0, registeredTools.size());
    }

    @Test
    public void testLoadRegistryFromEventHostIfHeaderNull() {
        startUp4();
        DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Collections.singletonList(TEST_USER),
                Collections.singletonList(TEST_REPO),
                Collections.singletonList(TEST_IMAGE),
                Collections.singletonList(PUSH_ACTION));

        List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(null, eventsEnvelope);
        Assert.assertEquals(1, registeredTools.size());

        verify(mockToolVersionManager).updateOrCreateToolVersion(eq(TOOL_ID_1), eq(LATEST), eq(TEST_IMAGE),
                eq(registry1), eq(null));
        //TODO verify4
    }

    @Test
    public void testLoadRegistryByExternalHostName() {
        startUp5();
        List<AbstractGrantPermission> permissions = new ArrayList<>();
        permissions.add(new UserPermission(TEST_USER, AclPermission.WRITE.getMask()));
        permissions.add(new UserPermission(ADMIN, AclPermission.WRITE.getMask()));
        initAclEntity(registry2, permissions);
        DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Collections.singletonList(TEST_USER),
                Collections.singletonList(EXTERNAL_REPO_PATH),
                Collections.singletonList(TEST_IMAGE),
                Collections.singletonList(PUSH_ACTION));

        List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(EXTERNAL_REPO_PATH, eventsEnvelope);
        Assert.assertEquals(1, registeredTools.size());

        verify(mockToolVersionManager).updateOrCreateToolVersion(eq(TOOL_ID_3), eq(LATEST), eq(TEST_IMAGE),
                eq(registry2), eq(null));
        //TODO verify5
    }

    @Test
    public void testToolWontBeEnabledIfUserIsntPermittedToWriteToRegistryAndGroupDoesntExist() {
        startUp6();
        initAclEntity(registry3);
        DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Collections.singletonList(TEST_USER),
                Collections.singletonList(TEST_REPO_WITHOUT_WRITE_ACCESS),
                Collections.singletonList(TEST_IMAGE_NEW_GROUP),
                Collections.singletonList(PUSH_ACTION));

        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED_MESSAGE),
                () -> registryManager.notifyDockerRegistryEvents(TEST_REPO_WITHOUT_WRITE_ACCESS, eventsEnvelope));
        //TODO verify6
    }

    @Test
    public void testEventWontEnableAlreadyExistingTool() {
        startUp7();
        DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Arrays.asList(TEST_USER, TEST_USER),
                Arrays.asList(TEST_REPO, TEST_REPO),
                Arrays.asList(TEST_IMAGE, TEST_IMAGE),
                Arrays.asList(PUSH_ACTION, PUSH_ACTION));

        List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(TEST_REPO, eventsEnvelope);
        Assert.assertEquals(2, registeredTools.size());
        Assert.assertEquals(registeredTools.get(0).getId(), registeredTools.get(1).getId());

        //TODO verify7
        verify(mockToolManager, times(2)).updateToolVersionScanStatus(eq(TOOL_ID_1), eq(ToolScanStatus.NOT_SCANNED),
                any(Date.class), eq(LATEST), eq(null), eq(null));
    }

    private void permissionGroup() {
        doReturn(getUserContext()).when(mockUserManager).loadUserContext(eq(TEST_USER));
    }

    private void startUp1() {
        doReturn(registry1).when(mockDockerRegistryDao).loadDockerRegistry(eq(TEST_REPO));
        doReturn(false).when(mockMetadataManager).hasMetadata(
                eq(new EntityVO(registry1.getId(), AclClass.DOCKER_REGISTRY)));
        doReturn(splitAndGetPairOf(TEST_IMAGE)).when(mockToolGroupManager).getGroupAndTool(eq(TEST_IMAGE));
        doReturn(true).when(mockToolGroupManager).doesToolGroupExist(eq(TEST_REPO), eq(getGroupFrom(TEST_IMAGE)));
        doReturn(toolGroup1).when(mockToolGroupManager).loadByNameOrId(eq(getIdentifierFrom(registry1, TEST_IMAGE)));
        doReturn(Optional.empty()).when(mockToolManager).loadToolInGroup(eq(TEST_IMAGE), eq(TOOLGROUP_ID_1));
        permissionGroup();
        doReturn(cloneAndSetParameters(tool1, TOOL_ID_1)).when(mockToolManager).create(eq(tool1), eq(false));
    }

    private void startUp2() {
        doReturn(registry1).when(mockDockerRegistryDao).loadDockerRegistry(eq(TEST_REPO));
        doReturn(false).when(mockMetadataManager).hasMetadata(
                eq(new EntityVO(registry1.getId(), AclClass.DOCKER_REGISTRY)));
        doReturn(splitAndGetPairOf(TEST_IMAGE_NEW_GROUP)).when(mockToolGroupManager).getGroupAndTool(eq(TEST_IMAGE_NEW_GROUP));
        doReturn(false).when(mockToolGroupManager).doesToolGroupExist(
                eq(TEST_REPO), eq(getGroupFrom(TEST_IMAGE_NEW_GROUP)));
        doReturn(Optional.empty()).when(mockToolManager).loadToolInGroup(eq(TEST_IMAGE_NEW_GROUP), eq(null));
        permissionGroup();
        doReturn(cloneAndSetParameters(tool2, TOOL_ID_2)).when(mockToolManager).create(eq(tool2), eq(false));
    }

    private void startUp4() {
        doReturn(registry1).when(mockDockerRegistryDao).loadDockerRegistry(eq(TEST_REPO));
        doReturn(false).when(mockMetadataManager).hasMetadata(
                eq(new EntityVO(registry1.getId(), AclClass.DOCKER_REGISTRY)));
        doReturn(splitAndGetPairOf(TEST_IMAGE)).when(mockToolGroupManager).getGroupAndTool(eq(TEST_IMAGE));
        doReturn(true).when(mockToolGroupManager).doesToolGroupExist(eq(TEST_REPO), eq(getGroupFrom(TEST_IMAGE)));
        doReturn(toolGroup1).when(mockToolGroupManager).loadByNameOrId(eq(getIdentifierFrom(registry1, TEST_IMAGE)));
        doReturn(Optional.empty()).when(mockToolManager).loadToolInGroup(eq(TEST_IMAGE), eq(TOOLGROUP_ID_1));
        permissionGroup();
        doReturn(cloneAndSetParameters(tool1, TOOL_ID_1)).when(mockToolManager).create(eq(tool1), eq(false));
    }

    private void startUp5() {
        doReturn(null).when(mockDockerRegistryDao).loadDockerRegistry(eq(EXTERNAL_REPO_PATH));
        doReturn(registry2).when(mockDockerRegistryDao).loadDockerRegistryByExternalUrl(eq(EXTERNAL_REPO_PATH));
        doReturn(false).when(mockMetadataManager).hasMetadata(
                eq(new EntityVO(registry2.getId(), AclClass.DOCKER_REGISTRY)));
        doReturn(splitAndGetPairOf(TEST_IMAGE)).when(mockToolGroupManager).getGroupAndTool(eq(TEST_IMAGE));
        doReturn(false).when(mockToolGroupManager).doesToolGroupExist(eq(EXTERNAL_REPO_PATH), eq(getGroupFrom(TEST_IMAGE)));
        permissionGroup();
        doReturn(Optional.empty()).when(mockToolManager).loadToolInGroup(eq(TEST_IMAGE), eq(null));
        doReturn(cloneAndSetParameters(tool3, TOOL_ID_3)).when(mockToolManager).create(eq(tool3), eq(false));
    }

    private void startUp6() {
        doReturn(registry3).when(mockDockerRegistryDao).loadDockerRegistry(eq(TEST_REPO_WITHOUT_WRITE_ACCESS));
        doReturn(false).when(mockMetadataManager).hasMetadata(
                eq(new EntityVO(registry3.getId(), AclClass.DOCKER_REGISTRY)));
        doReturn(splitAndGetPairOf(TEST_IMAGE_NEW_GROUP)).when(mockToolGroupManager).getGroupAndTool(eq(TEST_IMAGE_NEW_GROUP));
        doReturn(false).when(mockToolGroupManager).doesToolGroupExist(
                eq(TEST_REPO_WITHOUT_WRITE_ACCESS), eq(getGroupFrom(TEST_IMAGE_NEW_GROUP)));
        permissionGroup();
        doReturn(NOT_ALLOWED_MESSAGE).when(mockMessageHelper).getMessage(eq(MessageConstants.ERROR_PERMISSION_IS_NOT_GRANTED),
                eq(TEST_REPO_WITHOUT_WRITE_ACCESS), eq(AclPermission.WRITE_PERMISSION));
    }

    private void startUp7() {
        startUp1();
        doReturn(Optional.of(cloneAndSetParameters(tool1, TOOL_ID_1)))
                .when(mockToolManager).loadToolInGroup(eq(TEST_IMAGE), eq(TOOLGROUP_ID_1));
    }

    private Tool buildTool(ToolGroup toolGroup, String toolName, DockerRegistry dockerRegistry, String actor) {
        Tool tool = new Tool();
        tool.setToolGroup(toolGroup.getName());
        tool.setToolGroupId(toolGroup.getId());
        tool.setImage(toolName);
        tool.setCpu("0mi");
        tool.setRam("0Gi");
        tool.setRegistry(dockerRegistry.getPath());
        tool.setRegistryId(dockerRegistry.getId());
        tool.setOwner(actor);
        return tool;
    }

    private ImmutablePair<String, String> splitAndGetPairOf(String string) {
        String[] groupAndTool = string.split("/");
        return new ImmutablePair<>(groupAndTool[0], groupAndTool[1]);
    }

    private Tool cloneAndSetParameters(Tool tool, Long id) {
        Tool newTool = new Tool();
        newTool.setToolGroup(tool.getToolGroup());
        newTool.setToolGroupId(tool.getToolGroupId());
        newTool.setImage(tool.getImage());
        newTool.setCpu(tool.getCpu());
        newTool.setRam(tool.getRam());
        newTool.setRegistry(tool.getRegistry());
        newTool.setRegistryId(tool.getRegistryId());
        newTool.setOwner(tool.getOwner());
        newTool.setId(id);
        newTool.setParent(tool.getParent());
        return newTool;
    }

    private String getIdentifierFrom(DockerRegistry registry, String image) {
        return registry.getPath() + Constants.PATH_DELIMITER + getGroupFrom(image);
    }

    private String getGroupFrom(String testImage) {
        return testImage.substring(0, testImage.indexOf(Constants.PATH_DELIMITER));
    }

    private DockerRegistryEventEnvelope generateDockerRegistryEvents(List<String> users,
                                                                     List<String> testRepos,
                                                                     List<String> testImageNewGroups,
                                                                     List<String> pullActions) {
        DockerRegistryEventEnvelope eventsEnvelope = new DockerRegistryEventEnvelope();
        List<DockerRegistryEvent> events = new ArrayList<>();
        for (int i = 0; i < testImageNewGroups.size(); i++) {
            events.add(generateEvent(users.get(i), testRepos.get(i), testImageNewGroups.get(i), pullActions.get(i)));
        }
        eventsEnvelope.setEvents(events);
        return eventsEnvelope;
    }

    private DockerRegistryEvent generateEvent(String user, String registry, String groupAndTool, String action) {
        DockerRegistryEvent event = new DockerRegistryEvent();

        event.setAction(action);

        DockerRegistryEvent.Actor actor = new DockerRegistryEvent.Actor();
        actor.setName(user);
        event.setActor(actor);

        DockerRegistryEvent.Request request = new DockerRegistryEvent.Request();
        request.setHost(registry);
        event.setRequest(request);

        DockerRegistryEvent.Target target = new DockerRegistryEvent.Target();
        target.setRepository(groupAndTool);
        target.setTag(LATEST);
        event.setTarget(target);
        return event;
    }

    private UserContext getUserContext() {
        UserContext context = new UserContext();
        context.setUserId(CONTEXT_ID);
        context.setUserName(TEST_USER);
        Role role = new Role();
        role.setId(ROLE_ID);
        role.setName(ROLE_USER);
        role.setUserDefault(true);
        role.setPredefined(true);
        context.setRoles(Collections.singletonList(role));
        return context;
    }
}
