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

package com.epam.pipeline.manager.docker;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.DockerRegistryEvent;
import com.epam.pipeline.entity.pipeline.DockerRegistryEventEnvelope;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.pipeline.ToolGroupManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_3;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.getDockerRegistry;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.getTool;
import static com.epam.pipeline.test.creator.docker.DockerCreatorUtils.getToolGroup;
import static com.epam.pipeline.test.creator.security.SecurityCreatorUtils.getEntityVO;
import static com.epam.pipeline.test.creator.security.SecurityCreatorUtils.getUserContext;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.apache.commons.lang3.StringUtils.containsIgnoreCase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

@SuppressWarnings("PMD.UnusedPrivateField")
public class DockerRegistryNotificationTest extends AbstractAclTest {
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
    private static final ImmutablePair<String, String> IMMUTABLE_PAIR_1 = ImmutablePair.of(IMAGE_GROUP_1, "image");
    private static final ImmutablePair<String, String> IMMUTABLE_PAIR_2 = ImmutablePair.of(IMAGE_GROUP_2, "image");
    private static final String NOT_ALLOWED_MESSAGE_PART = "not granted";

    @Autowired
    private DockerRegistryDao mockDockerRegistryDao;

    @Autowired
    private GrantPermissionManager spyPermissionManager;

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

    @InjectMocks
    private final DockerRegistryManager registryManager = new DockerRegistryManager();

    private final DockerRegistry registry = getDockerRegistry(ID, REPO, ANOTHER_SIMPLE_USER, "");
    private final DockerRegistry registryWithExternalPath = getDockerRegistry(ID_2, REPO_EXTERNAL_PATH,
            ANOTHER_SIMPLE_USER, EXTERNAL_PATH);
    private final DockerRegistry registryWithoutWriteAccess = getDockerRegistry(ID_3, REPO_NO_WRITE_ACCESS,
            ANOTHER_SIMPLE_USER, "");
    private final ToolGroup toolGroup1 = getToolGroup(ID, IMAGE_GROUP_1, ID, ANOTHER_SIMPLE_USER);
    private final Tool tool1 = getTool(toolGroup1, IMAGE, registry, ANOTHER_SIMPLE_USER);
    private final Tool tool2 = getTool(getToolGroup(null, IMAGE_GROUP_2, ID, ANOTHER_SIMPLE_USER),
            IMAGE_NEW_GROUP, registry, ANOTHER_SIMPLE_USER);;
    private final Tool tool3 = getTool(getToolGroup(null, IMAGE_GROUP_1, ID_2, ANOTHER_SIMPLE_USER),
            IMAGE, registryWithExternalPath, ANOTHER_SIMPLE_USER);

    @Before
    public void setUp() {
        mockUserContext(getUserContext(ID, SIMPLE_USER, ID, SIMPLE_USER_ROLE));
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldEventEnableToolInExistingGroupWithRightUserAccess() {
        mockRegistryDao(registry, REPO);
        mockMetadataCheck(registry);
        mockToolGroupExistence();
        mockToolCreation(IMAGE, ID, tool1);
        initAclEntity(registry, AclPermission.WRITE);
        initAclEntity(toolGroup1, AclPermission.WRITE);

        final DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Collections.singletonList(SIMPLE_USER),
                Collections.singletonList(REPO),
                Collections.singletonList(IMAGE),
                Collections.singletonList(PUSH_ACTION));

        final List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(REPO, eventsEnvelope);
        assertThat(registeredTools)
                .hasSize(1)
                .contains(tool1);
    }

    @Test
    public void shouldEventCreateGroupIfItDoesntExist() {
        mockRegistryDao(registry, REPO);
        mockMetadataCheck(registry);
        mockToolGroupNotExistence(IMMUTABLE_PAIR_2, IMAGE_GROUP_2, IMAGE_NEW_GROUP, REPO);
        mockToolCreation(IMAGE_NEW_GROUP, null, tool2);
        initAclEntity(registry, AclPermission.WRITE);

        final DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Collections.singletonList(SIMPLE_USER),
                Collections.singletonList(REPO),
                Collections.singletonList(IMAGE_NEW_GROUP),
                Collections.singletonList(PUSH_ACTION));

        final List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(REPO, eventsEnvelope);
        assertThat(registeredTools)
                .hasSize(1)
                .contains(tool2);
    }

    @Test
    public void shouldNotEventProcessPullAction() {
        final DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Collections.singletonList(SIMPLE_USER),
                Collections.singletonList(REPO),
                Collections.singletonList(IMAGE),
                Collections.singletonList(PULL_ACTION));

        final List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(REPO, eventsEnvelope);
        assertThat(registeredTools).isEmpty();
    }

    @Test
    public void shouldLoadRegistryFromEventHostIfHeaderNull() {
        mockRegistryDao(registry, REPO);
        mockMetadataCheck(registry);
        mockToolGroupExistence();
        mockToolCreation(IMAGE, ID, tool1);
        initAclEntity(registry, AclPermission.WRITE);
        initAclEntity(toolGroup1, AclPermission.WRITE);

        final DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Collections.singletonList(SIMPLE_USER),
                Collections.singletonList(REPO),
                Collections.singletonList(IMAGE),
                Collections.singletonList(PUSH_ACTION));

        final List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(null, eventsEnvelope);
        assertThat(registeredTools)
                .hasSize(1)
                .contains(tool1);
    }

    @Test
    public void shouldLoadRegistryByExternalHostName() {
        mockDockerRegistryDaoWithExternalPath(registryWithExternalPath);
        mockMetadataCheck(registryWithExternalPath);
        mockToolGroupNotExistence(IMMUTABLE_PAIR_1, IMAGE_GROUP_1, IMAGE, EXTERNAL_PATH);
        mockToolCreation(IMAGE, null, tool3);

        initAclEntity(registryWithExternalPath, getWritePermissions());
        final DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Collections.singletonList(SIMPLE_USER),
                Collections.singletonList(EXTERNAL_PATH),
                Collections.singletonList(IMAGE),
                Collections.singletonList(PUSH_ACTION));

        final List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(EXTERNAL_PATH, eventsEnvelope);
        assertThat(registeredTools)
                .hasSize(1)
                .contains(tool3);
    }

    @Test
    public void shouldToolNotBeEnabledIfUserIsntPermittedToWriteToRegistryAndGroupDoesntExist() {
        mockRegistryDao(registryWithoutWriteAccess, REPO_NO_WRITE_ACCESS);
        mockMetadataCheck(registryWithoutWriteAccess);
        mockToolGroupNotExistence(IMMUTABLE_PAIR_2, IMAGE_GROUP_2, IMAGE_NEW_GROUP, REPO_NO_WRITE_ACCESS);
        doReturn(NOT_ALLOWED_MESSAGE_PART).when(mockMessageHelper).getMessage(
                eq(MessageConstants.ERROR_PERMISSION_IS_NOT_GRANTED),
                eq(REPO_NO_WRITE_ACCESS), eq(AclPermission.WRITE_PERMISSION));

        initAclEntity(registryWithoutWriteAccess);
        final DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Collections.singletonList(SIMPLE_USER),
                Collections.singletonList(REPO_NO_WRITE_ACCESS),
                Collections.singletonList(IMAGE_NEW_GROUP),
                Collections.singletonList(PUSH_ACTION));

        final Runnable result = () -> registryManager.notifyDockerRegistryEvents(REPO_NO_WRITE_ACCESS, eventsEnvelope);
        assertThrows(e -> containsIgnoreCase(e.getMessage(), NOT_ALLOWED_MESSAGE_PART), result);
    }

    @Test
    public void shouldEventNotEnableAlreadyExistingTool() {
        mockRegistryDao(registry, REPO);
        mockMetadataCheck(registry);
        mockToolGroupExistence();
        doReturn(Optional.of(getTool(SIMPLE_USER))).when(mockToolManager).loadToolInGroup(eq(IMAGE), eq(ID));

        final DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Arrays.asList(SIMPLE_USER, SIMPLE_USER),
                Arrays.asList(REPO, REPO),
                Arrays.asList(IMAGE, IMAGE),
                Arrays.asList(PUSH_ACTION, PUSH_ACTION));

        final List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(REPO, eventsEnvelope);
        assertThat(registeredTools)
                .hasSize(2);
        assertEquals(registeredTools.get(0).getId(), registeredTools.get(1).getId());
    }

    private void mockToolCreation(final String image, final Long groupId, final Tool tool) {
        doReturn(Optional.empty()).when(mockToolManager).loadToolInGroup(eq(image), eq(groupId));
        doReturn(tool).when(mockToolManager).create(eq(tool), eq(false));
    }

    private void mockToolGroupExistence() {
        doReturn(IMMUTABLE_PAIR_1).when(mockToolGroupManager).getGroupAndTool(eq(IMAGE));
        doReturn(true).when(mockToolGroupManager).doesToolGroupExist(eq(REPO), eq(IMAGE_GROUP_1));
        doReturn(toolGroup1).when(mockToolGroupManager).loadByNameOrId(eq(TEST_IDENTIFIER));
    }

    private void mockToolGroupNotExistence(final ImmutablePair<String, String> pair, final String imageGroup,
                                           final String image, final String repo) {
        doReturn(pair).when(mockToolGroupManager).getGroupAndTool(eq(image));
        doReturn(false).when(mockToolGroupManager).doesToolGroupExist(eq(repo), eq(imageGroup));
    }

    private void mockMetadataCheck(final DockerRegistry registry) {
        doReturn(false).when(mockMetadataManager).hasMetadata(
                eq(getEntityVO(registry.getId(), AclClass.DOCKER_REGISTRY)));
    }

    private void mockRegistryDao(final DockerRegistry registry, final String repo) {
        doReturn(registry).when(mockDockerRegistryDao).loadDockerRegistry(eq(repo));
    }

    private void mockDockerRegistryDaoWithExternalPath(final DockerRegistry registry) {
        doReturn(registry).when(mockDockerRegistryDao).loadDockerRegistryByExternalUrl(eq(EXTERNAL_PATH));
    }

    private List<AbstractGrantPermission> getWritePermissions() {
        final List<AbstractGrantPermission> permissions = new ArrayList<>();
        permissions.add(new UserPermission(SIMPLE_USER, AclPermission.WRITE.getMask()));
        permissions.add(new UserPermission(ADMIN_ROLE, AclPermission.WRITE.getMask()));
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
