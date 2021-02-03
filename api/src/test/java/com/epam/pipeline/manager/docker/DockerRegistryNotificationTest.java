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
import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.DockerRegistryEvent;
import com.epam.pipeline.entity.pipeline.DockerRegistryEventEnvelope;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.pipeline.ToolScanStatus;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.pipeline.ToolGroupManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.user.UserManager;
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

import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.getClonedTool;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.getDockerRegistry;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.getTool;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.getToolGroup;
import static com.epam.pipeline.test.creator.security.SecurityCreatorUtils.getEntityVO;
import static com.epam.pipeline.test.creator.security.SecurityCreatorUtils.getUserContext;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


public class DockerRegistryNotificationTest extends AbstractAclTest {

    private static final String ADMIN = "ADMIN";
    private static final String USER = "USER";
    private static final String REPO = "repository";
    private static final String REPO_EXTERNAL_PATH = "repository2";
    private static final String EXTERNAL_PATH = "external_repository";
    private static final String REPO_NO_WRITE_ACCESS = "repository3";
    private static final String IMAGE_GROUP_1 = "library";
    private static final String IMAGE_GROUP_2 = "library2";
    private static final String IMAGE = IMAGE_GROUP_1 + "/image";
    private static final String IMAGE_NEW_GROUP = IMAGE_GROUP_2 + "/image";
    private static final String PUSH_ACTION = "push";
    private static final String PULL_ACTION = "pull";
    private static final String LATEST = "latest";
    private static final String TEST_IDENTIFIER = REPO + "/" + IMAGE_GROUP_1;
    private static final ImmutablePair<String, String> IMMUTABLE_PAIR_1 = new ImmutablePair(IMAGE_GROUP_1, "image");
    private static final ImmutablePair<String, String> IMMUTABLE_PAIR_2 = new ImmutablePair(IMAGE_GROUP_2, "image");
    private static final Long REG_ID_1 = 1L;
    private static final Long REG_ID_2 = 2L;
    private static final Long REG_ID_3 = 3L;
    private static final Long TOOLGROUP_ID_1 = 4L;
    private static final Long TOOL_ID_1 = 5L;
    private static final Long TOOL_ID_2 = 6L;
    private static final Long TOOL_ID_3 = 7L;
    private static final Long CONTEXT_ID = 8L;
    private static final Long ROLE_ID = 9L;
    private static final CharSequence NOT_ALLOWED_MESSAGE = String.format("Permission is not granted for '%s' on '%S'",
            REPO_NO_WRITE_ACCESS, AclPermission.WRITE_NAME);
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
        registry1 = getDockerRegistry(REG_ID_1, REPO, USER, "");
        registry2 = getDockerRegistry(REG_ID_2, REPO_EXTERNAL_PATH, EXTERNAL_PATH, ADMIN);
        registry3 = getDockerRegistry(REG_ID_3, REPO_NO_WRITE_ACCESS, ADMIN, "");

        toolGroup1 = getToolGroup(TOOLGROUP_ID_1, "library", registry1.getId(), USER);
        toolGroup2 = getToolGroup(null, "library2", registry1.getId(), USER);
        toolGroup3 = getToolGroup(null, "library", registry2.getId(), USER);

        tool1 = getTool(toolGroup1, IMAGE, registry1, USER);
        tool2 = getTool(toolGroup2, IMAGE_NEW_GROUP, registry1, USER);
        tool3 = getTool(toolGroup3, IMAGE, registry2, USER);

        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testEventCanEnableToolInExistingGroupWithRightUserAccess() {
        mockDockerRegistryDao(registry1, REPO);
        mockMetadataManager(registry1);
        mockToolGroupManager(IMMUTABLE_PAIR_1, IMAGE_GROUP_1, IMAGE, REPO, toolGroup1);
        mockUserManager();
        mockToolManager(Optional.empty(), IMAGE, TOOLGROUP_ID_1, tool1, TOOL_ID_1);

        final DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Collections.singletonList(USER),
                Collections.singletonList(REPO),
                Collections.singletonList(IMAGE),
                Collections.singletonList(PUSH_ACTION));

        final List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(REPO, eventsEnvelope);
        Assert.assertEquals(1, registeredTools.size());

        verify(mockToolVersionManager).updateOrCreateToolVersion(eq(TOOL_ID_1), eq(LATEST), eq(IMAGE),
                eq(registry1), eq(null));
        verify(mockDockerRegistryDao, times(2)).loadDockerRegistry(eq(REPO));
        verify(mockMetadataManager, times(2)).hasMetadata(
                eq(getEntityVO(registry1.getId(), AclClass.DOCKER_REGISTRY)));
        verify(mockToolGroupManager).getGroupAndTool(eq(IMAGE));
        verify(mockToolGroupManager).doesToolGroupExist(eq(REPO), eq(IMAGE_GROUP_1));
        verify(mockToolGroupManager).loadByNameOrId(eq(TEST_IDENTIFIER));
        verify(mockToolManager).loadToolInGroup(eq(IMAGE), eq(TOOLGROUP_ID_1));
        verify(mockUserManager).loadUserContext(eq(USER));
        verify(mockToolManager).create(eq(tool1), eq(false));
    }

    @Test
    public void testEventCanCreateGroupIfItDoesntExist() {
        mockDockerRegistryDao(registry1, REPO);
        mockMetadataManager(registry1);
        mockToolGroupManager(IMMUTABLE_PAIR_2, IMAGE_GROUP_2, IMAGE_NEW_GROUP, REPO, null);
        mockUserManager();
        mockToolManager(Optional.empty(), IMAGE_NEW_GROUP, null, tool2, TOOL_ID_2);

        final DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Collections.singletonList(USER),
                Collections.singletonList(REPO),
                Collections.singletonList(IMAGE_NEW_GROUP),
                Collections.singletonList(PUSH_ACTION));

        final List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(REPO, eventsEnvelope);
        Assert.assertEquals(1, registeredTools.size());

        verify(mockToolVersionManager).updateOrCreateToolVersion(eq(TOOL_ID_2), eq(LATEST), eq(IMAGE_NEW_GROUP),
                eq(registry1), eq(null));
        verify(mockDockerRegistryDao, times(2)).loadDockerRegistry(eq(REPO));
        verify(mockMetadataManager, times(2)).hasMetadata(
                eq(getEntityVO(registry1.getId(), AclClass.DOCKER_REGISTRY)));
        verify(mockToolGroupManager).getGroupAndTool(eq(IMAGE_NEW_GROUP));
        verify(mockToolGroupManager).doesToolGroupExist(eq(REPO), eq(IMAGE_GROUP_2));
        verify(mockToolManager).loadToolInGroup(eq(IMAGE_NEW_GROUP), eq(null));
        verify(mockUserManager, times(2)).loadUserContext(eq(USER));
        verify(mockToolManager).create(eq(tool2), eq(false));
    }

    @Test
    public void testEventWontProcessPullAction() {
        final DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Collections.singletonList(USER),
                Collections.singletonList(REPO),
                Collections.singletonList(IMAGE),
                Collections.singletonList(PULL_ACTION));

        final List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(REPO, eventsEnvelope);
        Assert.assertEquals(0, registeredTools.size());
    }

    @Test
    public void testLoadRegistryFromEventHostIfHeaderNull() {
        mockDockerRegistryDao(registry1, REPO);
        mockMetadataManager(registry1);
        mockToolGroupManager(IMMUTABLE_PAIR_1, IMAGE_GROUP_1, IMAGE, REPO, toolGroup1);
        mockUserManager();
        mockToolManager(Optional.empty(), IMAGE, TOOLGROUP_ID_1, tool1, TOOL_ID_1);
        final DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Collections.singletonList(USER),
                Collections.singletonList(REPO),
                Collections.singletonList(IMAGE),
                Collections.singletonList(PUSH_ACTION));

        final List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(null, eventsEnvelope);
        Assert.assertEquals(1, registeredTools.size());

        verify(mockToolVersionManager).updateOrCreateToolVersion(eq(TOOL_ID_1), eq(LATEST), eq(IMAGE),
                eq(registry1), eq(null));
        verify(mockDockerRegistryDao, times(2)).loadDockerRegistry(eq(REPO));
        verify(mockMetadataManager, times(2)).hasMetadata(
                eq(getEntityVO(registry1.getId(), AclClass.DOCKER_REGISTRY)));
        verify(mockToolGroupManager).getGroupAndTool(eq(IMAGE));
        verify(mockToolGroupManager).doesToolGroupExist(eq(REPO), eq(IMAGE_GROUP_1));
        verify(mockToolGroupManager).loadByNameOrId(eq(TEST_IDENTIFIER));
        verify(mockToolManager).loadToolInGroup(eq(IMAGE), eq(TOOLGROUP_ID_1));
        verify(mockUserManager).loadUserContext(eq(USER));
        verify(mockToolManager).create(eq(tool1), eq(false));
    }

    @Test
    public void testLoadRegistryByExternalHostName() {
        mockDockerRegistryDao(registry2, EXTERNAL_PATH);
        mockMetadataManager(registry2);
        mockToolGroupManager(IMMUTABLE_PAIR_1, IMAGE_GROUP_1, IMAGE, EXTERNAL_PATH, null);
        mockUserManager();
        mockToolManager(Optional.empty(), IMAGE, null, tool3, TOOL_ID_3);

        initAclEntity(registry2, getWritePermissions());
        final DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Collections.singletonList(USER),
                Collections.singletonList(EXTERNAL_PATH),
                Collections.singletonList(IMAGE),
                Collections.singletonList(PUSH_ACTION));

        final List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(EXTERNAL_PATH, eventsEnvelope);
        Assert.assertEquals(1, registeredTools.size());

        verify(mockToolVersionManager).updateOrCreateToolVersion(eq(TOOL_ID_3), eq(LATEST), eq(IMAGE),
                eq(registry2), eq(null));
        verify(mockDockerRegistryDao, times(2)).loadDockerRegistry(eq(EXTERNAL_PATH));
        verify(mockDockerRegistryDao, times(2)).loadDockerRegistryByExternalUrl(eq(EXTERNAL_PATH));
        verify(mockMetadataManager, times(2)).hasMetadata(
                eq(getEntityVO(registry2.getId(), AclClass.DOCKER_REGISTRY)));
        verify(mockToolGroupManager).getGroupAndTool(eq(IMAGE));
        verify(mockToolGroupManager).doesToolGroupExist(eq(REPO_EXTERNAL_PATH), eq(IMAGE_GROUP_1));
        verify(mockUserManager, times(2)).loadUserContext(eq(USER));
        verify(mockToolManager).loadToolInGroup(eq(IMAGE), eq(null));
        verify(mockToolManager).create(eq(tool3), eq(false));
    }

    @Test
    public void testToolWontBeEnabledIfUserIsntPermittedToWriteToRegistryAndGroupDoesntExist() {
        mockDockerRegistryDao(registry3, REPO_NO_WRITE_ACCESS);
        mockMetadataManager(registry3);
        mockToolGroupManager(IMMUTABLE_PAIR_2, IMAGE_GROUP_2, IMAGE_NEW_GROUP, REPO_NO_WRITE_ACCESS, null);
        mockUserManager();
        doReturn(NOT_ALLOWED_MESSAGE).when(mockMessageHelper).getMessage(
                eq(MessageConstants.ERROR_PERMISSION_IS_NOT_GRANTED),
                eq(REPO_NO_WRITE_ACCESS), eq(AclPermission.WRITE_PERMISSION));

        initAclEntity(registry3);
        final DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Collections.singletonList(USER),
                Collections.singletonList(REPO_NO_WRITE_ACCESS),
                Collections.singletonList(IMAGE_NEW_GROUP),
                Collections.singletonList(PUSH_ACTION));

        Runnable task = () -> registryManager.notifyDockerRegistryEvents(REPO_NO_WRITE_ACCESS, eventsEnvelope);
        assertThrows(e -> e.getMessage().contains(NOT_ALLOWED_MESSAGE), task);

        verify(mockDockerRegistryDao).loadDockerRegistry(eq(REPO_NO_WRITE_ACCESS));
        verify(mockMetadataManager).hasMetadata(eq(getEntityVO(registry3.getId(), AclClass.DOCKER_REGISTRY)));
        verify(mockToolGroupManager).getGroupAndTool(eq(IMAGE_NEW_GROUP));
        verify(mockToolGroupManager).doesToolGroupExist(eq(REPO_NO_WRITE_ACCESS), eq(IMAGE_GROUP_2));
        verify(mockUserManager).loadUserContext(eq(USER));
        verify(mockMessageHelper).getMessage(eq(MessageConstants.ERROR_PERMISSION_IS_NOT_GRANTED),
                eq(REPO_NO_WRITE_ACCESS), eq(AclPermission.WRITE_PERMISSION));
    }

    @Test
    public void testEventWontEnableAlreadyExistingTool() {
        mockDockerRegistryDao(registry1, REPO);
        mockMetadataManager(registry1);
        mockToolGroupManager(IMMUTABLE_PAIR_1, IMAGE_GROUP_1, IMAGE, REPO, toolGroup1);
        mockUserManager();
        doReturn(Optional.of(getClonedTool(tool1, TOOL_ID_1)))
                .when(mockToolManager).loadToolInGroup(eq(IMAGE), eq(TOOLGROUP_ID_1));

        final DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Arrays.asList(USER, USER),
                Arrays.asList(REPO, REPO),
                Arrays.asList(IMAGE, IMAGE),
                Arrays.asList(PUSH_ACTION, PUSH_ACTION));

        final List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(REPO, eventsEnvelope);
        Assert.assertEquals(2, registeredTools.size());
        Assert.assertEquals(registeredTools.get(0).getId(), registeredTools.get(1).getId());

        verify(mockToolManager, times(2)).updateToolVersionScanStatus(eq(TOOL_ID_1), eq(ToolScanStatus.NOT_SCANNED),
                any(Date.class), eq(LATEST), eq(null), eq(null));
        verify(mockDockerRegistryDao, times(4)).loadDockerRegistry(eq(REPO));
        verify(mockMetadataManager, times(4)).hasMetadata(
                eq(getEntityVO(registry1.getId(), AclClass.DOCKER_REGISTRY)));
        verify(mockToolGroupManager, times(2)).getGroupAndTool(eq(IMAGE));
        verify(mockToolGroupManager, times(2)).doesToolGroupExist(eq(REPO), eq(IMAGE_GROUP_1));
        verify(mockToolGroupManager, times(2)).loadByNameOrId(eq(TEST_IDENTIFIER));
        verify(mockToolManager, times(2)).loadToolInGroup(eq(IMAGE), eq(TOOLGROUP_ID_1));
    }

    private void mockToolManager(final Optional<Tool> optional, final String image,
                                 final Long groupId, final Tool tool, final Long toolId) {
        doReturn(optional).when(mockToolManager).loadToolInGroup(eq(image), eq(groupId));
        doReturn(getClonedTool(tool, toolId)).when(mockToolManager).create(eq(tool), eq(false));
    }

    private void mockUserManager() {
        doReturn(getUserContext(CONTEXT_ID, USER, ROLE_ID, ROLE_USER)).when(mockUserManager).loadUserContext(eq(USER));
    }

    private void mockToolGroupManager(final ImmutablePair<String, String> pair, final String imageGroup,
                                      final String image, final String repo, final ToolGroup group) {
        boolean doesToolGroupExist = false;
        if (group != null) {
            doReturn(group).when(mockToolGroupManager).loadByNameOrId(eq(TEST_IDENTIFIER));
            doesToolGroupExist = true;
        }
        doReturn(pair).when(mockToolGroupManager).getGroupAndTool(eq(image));
        doReturn(doesToolGroupExist).when(mockToolGroupManager).doesToolGroupExist(eq(repo), eq(imageGroup));
    }

    private void mockMetadataManager(final DockerRegistry registry) {
        doReturn(false).when(mockMetadataManager).hasMetadata(
                eq(getEntityVO(registry.getId(), AclClass.DOCKER_REGISTRY)));
    }

    private void mockDockerRegistryDao(final DockerRegistry registry, final String repo) {
        if (EXTERNAL_PATH.equals(repo)) {
            doReturn(registry).when(mockDockerRegistryDao).loadDockerRegistryByExternalUrl(eq(EXTERNAL_PATH));
        } else {
            doReturn(registry).when(mockDockerRegistryDao).loadDockerRegistry(eq(repo));
        }
    }

    private List<AbstractGrantPermission> getWritePermissions() {
        final List<AbstractGrantPermission> permissions = new ArrayList<>();
        permissions.add(new UserPermission(USER, AclPermission.WRITE.getMask()));
        permissions.add(new UserPermission(ADMIN, AclPermission.WRITE.getMask()));
        return permissions;
    }

    private DockerRegistryEventEnvelope generateDockerRegistryEvents(final List<String> users,
                                                                     final List<String> testRepos,
                                                                     final List<String> testImageNewGroups,
                                                                     final List<String> pullActions) {
        final DockerRegistryEventEnvelope eventsEnvelope = new DockerRegistryEventEnvelope();
        final List<DockerRegistryEvent> events = new ArrayList<>();
        for (int i = 0; i < testImageNewGroups.size(); i++) {
            events.add(generateEvent(users.get(i), testRepos.get(i), testImageNewGroups.get(i), pullActions.get(i)));
        }
        eventsEnvelope.setEvents(events);
        return eventsEnvelope;
    }

    private DockerRegistryEvent generateEvent(final String user, final String registry,
                                              final String groupAndTool, final String action) {
        final DockerRegistryEvent event = new DockerRegistryEvent();
        event.setAction(action);

        final DockerRegistryEvent.Actor actor = new DockerRegistryEvent.Actor();
        actor.setName(user);
        event.setActor(actor);

        final DockerRegistryEvent.Request request = new DockerRegistryEvent.Request();
        request.setHost(registry);
        event.setRequest(request);

        final DockerRegistryEvent.Target target = new DockerRegistryEvent.Target();
        target.setRepository(groupAndTool);
        target.setTag(LATEST);
        event.setTarget(target);
        return event;
    }
}
