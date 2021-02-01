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
    private static final String IMAGE = "library/image";
    private static final String IMAGE_NEW_GROUP = "library2/image";
    private static final String PUSH_ACTION = "push";
    private static final String PULL_ACTION = "pull";
    private static final String LATEST = "latest";
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
        setMockBehaviour1();
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
        verify(mockToolGroupManager).doesToolGroupExist(eq(REPO), eq(getGroupFrom(IMAGE)));
        verify(mockToolGroupManager).loadByNameOrId(eq(getIdentifierFrom(registry1, IMAGE)));
        verify(mockToolManager).loadToolInGroup(eq(IMAGE), eq(TOOLGROUP_ID_1));
        verify(mockUserManager).loadUserContext(eq(USER));
        verify(mockToolManager).create(eq(tool1), eq(false));
    }

    @Test
    public void testEventCanCreateGroupIfItDoesntExist() {
        setMockBehaviour2();
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
        verify(mockToolGroupManager).doesToolGroupExist(eq(REPO), eq(getGroupFrom(IMAGE_NEW_GROUP)));
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
        setMockBehaviour4();
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
        verify(mockToolGroupManager).doesToolGroupExist(eq(REPO), eq(getGroupFrom(IMAGE)));
        verify(mockToolGroupManager).loadByNameOrId(eq(getIdentifierFrom(registry1, IMAGE)));
        verify(mockToolManager).loadToolInGroup(eq(IMAGE), eq(TOOLGROUP_ID_1));
        verify(mockUserManager).loadUserContext(eq(USER));
        verify(mockToolManager).create(eq(tool1), eq(false));
    }

    @Test
    public void testLoadRegistryByExternalHostName() {
        setMockBehaviour5();
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
        verify(mockToolGroupManager).doesToolGroupExist(eq(REPO_EXTERNAL_PATH), eq(getGroupFrom(IMAGE)));
        verify(mockUserManager, times(2)).loadUserContext(eq(USER));
        verify(mockToolManager).loadToolInGroup(eq(IMAGE), eq(null));
        verify(mockToolManager).create(eq(tool3), eq(false));
    }

    @Test
    public void testToolWontBeEnabledIfUserIsntPermittedToWriteToRegistryAndGroupDoesntExist() {
        setMockBehaviour6();
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
        verify(mockToolGroupManager).doesToolGroupExist(eq(REPO_NO_WRITE_ACCESS), eq(getGroupFrom(IMAGE_NEW_GROUP)));
        verify(mockUserManager).loadUserContext(eq(USER));
        verify(mockMessageHelper).getMessage(eq(MessageConstants.ERROR_PERMISSION_IS_NOT_GRANTED),
                eq(REPO_NO_WRITE_ACCESS), eq(AclPermission.WRITE_PERMISSION));
    }

    @Test
    public void testEventWontEnableAlreadyExistingTool() {
        setMockBehaviour7();
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
        verify(mockToolGroupManager, times(2)).doesToolGroupExist(eq(REPO), eq(getGroupFrom(IMAGE)));
        verify(mockToolGroupManager, times(2)).loadByNameOrId(eq(getIdentifierFrom(registry1, IMAGE)));
        verify(mockToolManager, times(2)).loadToolInGroup(eq(IMAGE), eq(TOOLGROUP_ID_1));
    }

    private void setMockBehaviour1() {
        setCommonBehaviour(registry1, REPO, IMAGE, toolGroup1, true);
        doReturn(Optional.empty()).when(mockToolManager).loadToolInGroup(eq(IMAGE), eq(TOOLGROUP_ID_1));
        doReturn(getClonedTool(tool1, TOOL_ID_1)).when(mockToolManager).create(eq(tool1), eq(false));
    }

    private void setMockBehaviour2() {
        setCommonBehaviour(registry1, REPO, IMAGE_NEW_GROUP, null, false);
        doReturn(Optional.empty()).when(mockToolManager).loadToolInGroup(eq(IMAGE_NEW_GROUP), eq(null));
        doReturn(getClonedTool(tool2, TOOL_ID_2)).when(mockToolManager).create(eq(tool2), eq(false));
    }

    private void setMockBehaviour4() {
        setCommonBehaviour(registry1, REPO, IMAGE, toolGroup1, true);
        doReturn(Optional.empty()).when(mockToolManager).loadToolInGroup(eq(IMAGE), eq(TOOLGROUP_ID_1));
        doReturn(getClonedTool(tool1, TOOL_ID_1)).when(mockToolManager).create(eq(tool1), eq(false));
    }

    private void setMockBehaviour5() {
        setCommonBehaviour(registry2, EXTERNAL_PATH, IMAGE, null, false);
        doReturn(Optional.empty()).when(mockToolManager).loadToolInGroup(eq(IMAGE), eq(null));
        doReturn(getClonedTool(tool3, TOOL_ID_3)).when(mockToolManager).create(eq(tool3), eq(false));
    }

    private void setMockBehaviour6() {
        setCommonBehaviour(registry3, REPO_NO_WRITE_ACCESS, IMAGE_NEW_GROUP, null, false);
        doReturn(NOT_ALLOWED_MESSAGE).when(mockMessageHelper).getMessage(
                eq(MessageConstants.ERROR_PERMISSION_IS_NOT_GRANTED),
                eq(REPO_NO_WRITE_ACCESS), eq(AclPermission.WRITE_PERMISSION));
    }

    private void setMockBehaviour7() {
        setMockBehaviour1();
        doReturn(Optional.of(getClonedTool(tool1, TOOL_ID_1)))
                .when(mockToolManager).loadToolInGroup(eq(IMAGE), eq(TOOLGROUP_ID_1));
    }

    private void setCommonBehaviour(final DockerRegistry registry, final String repo, final String image,
                                    final ToolGroup toolGroup, final boolean doesToolGroupExist) {
        if (EXTERNAL_PATH.equals(repo)) {
            doReturn(registry).when(mockDockerRegistryDao).loadDockerRegistryByExternalUrl(eq(EXTERNAL_PATH));
        } else {
            doReturn(registry).when(mockDockerRegistryDao).loadDockerRegistry(eq(repo));
        }
        doReturn(false).when(mockMetadataManager).hasMetadata(
                eq(getEntityVO(registry.getId(), AclClass.DOCKER_REGISTRY)));
        doReturn(splitAndGetPairOf(image)).when(mockToolGroupManager).getGroupAndTool(eq(image));
        doReturn(doesToolGroupExist).when(mockToolGroupManager).doesToolGroupExist(eq(repo), eq(getGroupFrom(image)));
        doReturn(getUserContext(CONTEXT_ID, USER, ROLE_ID, ROLE_USER)).when(mockUserManager).loadUserContext(eq(USER));
        if (toolGroup != null) {
            doReturn(toolGroup).when(mockToolGroupManager).loadByNameOrId(eq(getIdentifierFrom(registry1, IMAGE)));
        }
    }

    private ImmutablePair<String, String> splitAndGetPairOf(String string) {
        final String[] groupAndTool = string.split("/");
        return new ImmutablePair<>(groupAndTool[0], groupAndTool[1]);
    }

    private List<AbstractGrantPermission> getWritePermissions() {
        final List<AbstractGrantPermission> permissions = new ArrayList<>();
        permissions.add(new UserPermission(USER, AclPermission.WRITE.getMask()));
        permissions.add(new UserPermission(ADMIN, AclPermission.WRITE.getMask()));
        return permissions;
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
        final DockerRegistryEventEnvelope eventsEnvelope = new DockerRegistryEventEnvelope();
        final List<DockerRegistryEvent> events = new ArrayList<>();
        for (int i = 0; i < testImageNewGroups.size(); i++) {
            events.add(generateEvent(users.get(i), testRepos.get(i), testImageNewGroups.get(i), pullActions.get(i)));
        }
        eventsEnvelope.setEvents(events);
        return eventsEnvelope;
    }

    private DockerRegistryEvent generateEvent(String user, String registry, String groupAndTool, String action) {
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
