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

import com.epam.pipeline.app.TestApplicationWithAclSecurity;
import com.epam.pipeline.config.Constants;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.dao.tool.ToolGroupDao;
import com.epam.pipeline.dao.util.AclTestDao;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.DockerRegistryEvent;
import com.epam.pipeline.entity.pipeline.DockerRegistryEventEnvelope;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.pipeline.ToolGroupManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.util.TestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.util.CustomMatchers.matches;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
public class DockerRegistryNotificationTest extends AbstractManagerTest {

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
    private static final Long TOOLGROUP_ID_2 = 4L;
    private static final Long TOOL_ID_2 = 5L;
    private static final Long TOOLGROUP_ID_3 = 6L;
    private static final Long TOOL_ID_3 = 7L;

    @Autowired
    private AclTestDao aclTestDao;

    @Autowired
    private UserManager userManager;

    @Autowired
    private DockerRegistryDao mockRegistryDao;

    private GrantPermissionManager mockGrantPermissionManager;

    private ToolManager mockToolManager;

    private ToolGroupManager mockToolGroupManager;

    private MetadataManager mockMetadataManager;

    @Autowired
    private DockerRegistryManager registryManager;

    @Autowired
    private ToolGroupDao toolGroupDao;

    @MockBean
    private DockerClientFactory dockerClientFactoryMock;

    @Mock
    private DockerClient dockerClient;

    @MockBean
    private ToolVersionManager mockToolVersionManager;

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

        AclTestDao.AclSid adminSid = new AclTestDao.AclSid(true, ADMIN);
        aclTestDao.createAclSid(adminSid);
        userManager.createUser(ADMIN, Collections.singletonList(2L), Collections.emptyList(), null, null);

        AclTestDao.AclSid testUserSid = new AclTestDao.AclSid(true, TEST_USER);
        aclTestDao.createAclSid(testUserSid);
        userManager.createUser(TEST_USER, Collections.singletonList(2L),  Collections.emptyList(), null, null);

        registry1 = new DockerRegistry();
        registry1.setId(REGISTRY_ID_1);
        registry1.setPath(TEST_REPO);
        registry1.setOwner(TEST_USER);
        mockRegistryDao.createDockerRegistry(registry1);

        registry2 = new DockerRegistry();
        registry2.setId(REGISTRY_ID_2);
        registry2.setPath(TEST_REPO_WITH_EXTERNAL_PATH);
        registry2.setExternalUrl(EXTERNAL_REPO_PATH);
        registry2.setOwner(ADMIN);
        mockRegistryDao.createDockerRegistry(registry2);

        registry3 = new DockerRegistry();
        registry3.setPath(TEST_REPO_WITHOUT_WRITE_ACCESS);
        registry3.setOwner(ADMIN);
        mockRegistryDao.createDockerRegistry(registry3);

        toolGroup1 = new ToolGroup();
        toolGroup1.setId(TOOLGROUP_ID_1);
        toolGroup1.setName("library");
        toolGroup1.setRegistryId(registry1.getId());
        toolGroup1.setOwner(TEST_USER);
        toolGroupDao.createToolGroup(toolGroup1);

        toolGroup2 = new ToolGroup();
        toolGroup1.setId(TOOLGROUP_ID_2);
        toolGroup1.setName("library2");
        toolGroup1.setRegistryId(registry1.getId());
        toolGroup1.setOwner(TEST_USER);

        toolGroup3 = new ToolGroup();
        toolGroup3.setId(TOOLGROUP_ID_3);
        toolGroup3.setName("library");
        toolGroup3.setRegistryId(registry2.getId());
        toolGroup3.setOwner(TEST_USER);

        tool1 = buildTool(toolGroup1, TEST_IMAGE, registry1, TEST_USER);
        tool2 = buildTool(toolGroup2, TEST_IMAGE_NEW_GROUP, registry1, TEST_USER);
        tool3 = buildTool(toolGroup3, TEST_IMAGE, registry2, TEST_USER);

        // And for USER group, which all users are belong to
        AclTestDao.AclSid userGroupSid = new AclTestDao.AclSid(false, "ROLE_USER");
        aclTestDao.createAclSid(userGroupSid);

        // Mock ACL stuff
        AclTestDao.AclClass groupAclClass = new AclTestDao.AclClass(ToolGroup.class.getCanonicalName());
        aclTestDao.createAclClassIfNotPresent(groupAclClass);

        AclTestDao.AclClass toolAclClass = new AclTestDao.AclClass(Tool.class.getCanonicalName());
        aclTestDao.createAclClassIfNotPresent(toolAclClass);

        AclTestDao.AclClass registryAclClass = new AclTestDao.AclClass(DockerRegistry.class.getCanonicalName());
        aclTestDao.createAclClassIfNotPresent(registryAclClass);

        AclTestDao.AclObjectIdentity registryIdentity = new AclTestDao.AclObjectIdentity(testUserSid, registry1.getId(),
                                                                 registryAclClass.getId(), null, true);
        aclTestDao.createObjectIdentity(registryIdentity);

        AclTestDao.AclObjectIdentity registryIdentity2 = new AclTestDao.AclObjectIdentity(adminSid, registry2.getId(),
                registryAclClass.getId(), null, true);
        aclTestDao.createObjectIdentity(registryIdentity2);

        AclTestDao.AclObjectIdentity registryIdentity3 = new AclTestDao.AclObjectIdentity(adminSid, registry3.getId(),
                registryAclClass.getId(), null, true);
        aclTestDao.createObjectIdentity(registryIdentity3);

        AclTestDao.AclObjectIdentity groupIdentity = new AclTestDao.AclObjectIdentity(testUserSid, toolGroup1.getId(),
                                                                groupAclClass.getId(), null, true);
        aclTestDao.createObjectIdentity(groupIdentity);

        // All Test users can write to registry
        AclTestDao.AclEntry registryAclEntry = new AclTestDao.AclEntry(registryIdentity, 1, adminSid,
                                                                       AclPermission.WRITE.getMask(), true);
        aclTestDao.createAclEntry(registryAclEntry);

        registryAclEntry.setSid(testUserSid);
        registryAclEntry.setOrder(2);
        aclTestDao.createAclEntry(registryAclEntry);

        // All Test users can write to group
        AclTestDao.AclEntry groupAclEntry = new AclTestDao.AclEntry(groupIdentity, 1, adminSid,
                AclPermission.WRITE.getMask(), true);
        aclTestDao.createAclEntry(groupAclEntry);

        groupAclEntry.setSid(testUserSid);
        groupAclEntry.setOrder(2);
        aclTestDao.createAclEntry(groupAclEntry);

        AclTestDao.AclEntry registryAclEntry2 = new AclTestDao.AclEntry(registryIdentity2, 1, adminSid,
                AclPermission.WRITE.getMask(), true);
        aclTestDao.createAclEntry(registryAclEntry2);
        registryAclEntry2.setSid(testUserSid);
        registryAclEntry2.setOrder(2);
        aclTestDao.createAclEntry(registryAclEntry2);

        //Only Admin can write to second registry
        AclTestDao.AclEntry registryAclEntry3 = new AclTestDao.AclEntry(registryIdentity3, 1, adminSid,
                AclPermission.WRITE.getMask(), true);
        aclTestDao.createAclEntry(registryAclEntry3);

        MockitoAnnotations.initMocks(this);

        TestUtils.configureDockerClientMock(dockerClient, dockerClientFactoryMock);

        dockerClientFactoryMock.setObjectMapper(new ObjectMapper());

        when(dockerClientFactoryMock.getDockerClient(any(DockerRegistry.class)))
                .thenReturn(dockerClient);
        when(dockerClient.getImageTags(any(String.class), any(String.class)))
                .thenReturn(Collections.singletonList("latest"));

        ToolVersion toolVersion = new ToolVersion();
        toolVersion.setDigest("test_digest");
        toolVersion.setSize(DOCKER_SIZE);
        toolVersion.setVersion("test_version");
        Mockito.doReturn(toolVersion).when(dockerClient)
                .getVersionAttributes(Mockito.any(), Mockito.any(), Mockito.any());

        doNothing().when(mockToolVersionManager).updateOrCreateToolVersion(anyLong(), anyString(), anyString(),
                any(DockerRegistry.class), any(DockerClient.class));
    }

    private void startUp1() {
        doReturn(registry1).when(mockRegistryDao).loadDockerRegistry(eq(TEST_REPO));
        doReturn(false).when(mockMetadataManager).hasMetadata(
                eq(new EntityVO(registry1.getId(), AclClass.DOCKER_REGISTRY)));
        doReturn(splitAndGetPairOf(TEST_IMAGE)).when(mockToolGroupManager).getGroupAndTool(eq(TEST_IMAGE));
        doReturn(true).when(mockToolGroupManager).doesToolGroupExist(eq(TEST_REPO), eq(getGroupFrom(TEST_IMAGE)));
        doReturn(toolGroup1).when(mockToolGroupManager).loadByNameOrId(eq(getIdentifierFrom(registry1, TEST_IMAGE)));
        doReturn(Optional.empty()).when(mockToolManager).loadToolInGroup(eq(TEST_IMAGE), eq(TOOLGROUP_ID_1));
        doReturn(true).when(mockGrantPermissionManager).isActionAllowedForUser(
                eq(toolGroup1), TEST_USER, eq(AclPermission.WRITE));
        doReturn(cloneToolAndSetId(tool1, TOOL_ID_1)).when(mockToolManager).create(eq(tool1), eq(false));
        verify(mockToolVersionManager).updateOrCreateToolVersion(eq(TOOL_ID_1), eq(LATEST), eq(TEST_IMAGE),
                argThat(matches(registry -> REGISTRY_ID_1.equals(registry.getId()) &&
                        registry.getTools().contains(eq(tool1)))), eq(null));
    }

    private void startUp2() {
        doReturn(registry1).when(mockRegistryDao).loadDockerRegistry(eq(TEST_REPO));
        doReturn(false).when(mockMetadataManager).hasMetadata(
                eq(new EntityVO(registry1.getId(), AclClass.DOCKER_REGISTRY)));
        doReturn(splitAndGetPairOf(TEST_IMAGE_NEW_GROUP)).when(mockToolGroupManager).getGroupAndTool(eq(TEST_IMAGE_NEW_GROUP));
        doReturn(false).when(mockToolGroupManager).doesToolGroupExist(
                eq(TEST_REPO), eq(getGroupFrom(TEST_IMAGE_NEW_GROUP)));
        doReturn(true).when(mockGrantPermissionManager).isActionAllowedForUser(
                eq(registry1), TEST_USER, eq(AclPermission.WRITE));
        doReturn(Optional.empty()).when(mockToolManager).loadToolInGroup(eq(TEST_IMAGE_NEW_GROUP), eq(TOOLGROUP_ID_2));
        doReturn(true).when(mockGrantPermissionManager).isActionAllowedForUser(
                eq(toolGroup2), TEST_USER, eq(AclPermission.WRITE));
        doReturn(cloneToolAndSetId(tool2, TOOL_ID_2)).when(mockToolManager).create(eq(tool2), eq(false));
        verify(mockToolVersionManager).updateOrCreateToolVersion(eq(TOOL_ID_2), eq(LATEST), eq(TEST_IMAGE_NEW_GROUP),
                argThat(matches(registry -> REGISTRY_ID_1.equals(registry.getId()) &&
                        registry.getTools().contains(eq(tool2)))), eq(null));
    }

    private void startUp3() {}

    private void startUp4() {
        doReturn(registry1).when(mockRegistryDao).loadDockerRegistry(eq(TEST_REPO));
        doReturn(false).when(mockMetadataManager).hasMetadata(
                eq(new EntityVO(registry1.getId(), AclClass.DOCKER_REGISTRY)));
        doReturn(splitAndGetPairOf(TEST_IMAGE)).when(mockToolGroupManager).getGroupAndTool(eq(TEST_IMAGE));
        doReturn(true).when(mockToolGroupManager).doesToolGroupExist(eq(TEST_REPO), eq(getGroupFrom(TEST_IMAGE)));
        doReturn(toolGroup1).when(mockToolGroupManager).loadByNameOrId(eq(getIdentifierFrom(registry1, TEST_IMAGE)));
        doReturn(Optional.empty()).when(mockToolManager).loadToolInGroup(eq(TEST_IMAGE), eq(TOOLGROUP_ID_1));
        doReturn(true).when(mockGrantPermissionManager).isActionAllowedForUser(
                eq(toolGroup1), TEST_USER, eq(AclPermission.WRITE));
        doReturn(cloneToolAndSetId(tool1, TOOL_ID_1)).when(mockToolManager).create(eq(tool1), eq(false));
        verify(mockToolVersionManager).updateOrCreateToolVersion(eq(TOOL_ID_1), eq(LATEST), eq(TEST_IMAGE),
                argThat(matches(registry -> REGISTRY_ID_1.equals(registry.getId()) &&
                        registry.getTools().contains(eq(tool1)))), eq(null));
    }

    private void startUp5() {
        doReturn(null).when(mockRegistryDao).loadDockerRegistry(eq(EXTERNAL_REPO_PATH));
        doReturn(registry2).when(mockRegistryDao).loadDockerRegistryByExternalUrl(eq(EXTERNAL_REPO_PATH));
        doReturn(false).when(mockMetadataManager).hasMetadata(
                eq(new EntityVO(registry1.getId(), AclClass.DOCKER_REGISTRY)));
        doReturn(splitAndGetPairOf(TEST_IMAGE)).when(mockToolGroupManager).getGroupAndTool(eq(TEST_IMAGE));
        doReturn(false).when(mockToolGroupManager).doesToolGroupExist(eq(EXTERNAL_REPO_PATH), eq(getGroupFrom(TEST_IMAGE)));
        doReturn(true).when(mockGrantPermissionManager).isActionAllowedForUser(
                eq(registry2), TEST_USER, eq(AclPermission.WRITE));
        doReturn(Optional.empty()).when(mockToolManager).loadToolInGroup(eq(TEST_IMAGE), eq(TOOLGROUP_ID_3));
        //TODO Parent в ToolGroup
        doReturn(true).when(mockGrantPermissionManager).isActionAllowedForUser(
                eq(toolGroup3), TEST_USER, eq(AclPermission.WRITE));
        doReturn(cloneToolAndSetId(tool3, TOOL_ID_3)).when(mockToolManager).create(eq(tool3), eq(false));
        verify(mockToolVersionManager).updateOrCreateToolVersion(eq(TOOL_ID_3), eq(LATEST), eq(TEST_IMAGE),
                argThat(matches(registry -> REGISTRY_ID_2.equals(registry.getId()) &&
                        registry.getTools().contains(eq(tool3)))), eq(null));
        //TODO registy везде принимается без id?
    }


    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = ADMIN, roles = ADMIN_ROLE)
    public void testEventCanEnableToolInExistingGroupWithRightUserAccess() {
        DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Collections.singletonList(TEST_USER),
                Collections.singletonList(TEST_REPO),
                Collections.singletonList(TEST_IMAGE),
                Collections.singletonList(PUSH_ACTION));

        List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(TEST_REPO, eventsEnvelope);
        Assert.assertEquals(1, registeredTools.size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = ADMIN,  roles = ADMIN_ROLE)
    public void testEventCanCreateGroupIfItDoesntExist() {
        DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Collections.singletonList(TEST_USER),
                Collections.singletonList(TEST_REPO),
                Collections.singletonList(TEST_IMAGE_NEW_GROUP),
                Collections.singletonList(PUSH_ACTION));

        List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(TEST_REPO, eventsEnvelope);
        Assert.assertEquals(1, registeredTools.size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = ADMIN, roles = ADMIN_ROLE)
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
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = ADMIN, roles = ADMIN_ROLE)
    public void testLoadRegistryFromEventHostIfHeaderNull() {
        DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Collections.singletonList(TEST_USER),
                Collections.singletonList(TEST_REPO),
                Collections.singletonList(TEST_IMAGE),
                Collections.singletonList(PUSH_ACTION));

        List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(null, eventsEnvelope);
        Assert.assertEquals(1, registeredTools.size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = ADMIN, roles = ADMIN_ROLE)
    public void testLoadRegistryByExternalHostName() {
        DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Collections.singletonList(TEST_USER),
                Collections.singletonList(EXTERNAL_REPO_PATH),
                Collections.singletonList(TEST_IMAGE),
                Collections.singletonList(PUSH_ACTION));

        List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(EXTERNAL_REPO_PATH, eventsEnvelope);
        Assert.assertEquals(1, registeredTools.size());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = ADMIN, roles = ADMIN_ROLE)
    public void testToolWontBeEnabledIfUserIsntPermittedToWriteToRegistryAndGroupDoesntExist() {
        DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Collections.singletonList(TEST_USER),
                Collections.singletonList(TEST_REPO_WITHOUT_WRITE_ACCESS),
                Collections.singletonList(TEST_IMAGE_NEW_GROUP),
                Collections.singletonList(PUSH_ACTION));

        List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(TEST_REPO_WITHOUT_WRITE_ACCESS,
                eventsEnvelope);
        Assert.assertEquals(0, registeredTools.size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = ADMIN, roles = ADMIN_ROLE)
    public void testEventWontEnableAlreadyExistingTool() {
        DockerRegistryEventEnvelope eventsEnvelope = generateDockerRegistryEvents(
                Arrays.asList(TEST_USER, TEST_USER),
                Arrays.asList(TEST_REPO, TEST_REPO),
                Arrays.asList(TEST_IMAGE, TEST_IMAGE),
                Arrays.asList(PUSH_ACTION, PUSH_ACTION));

        List<Tool> registeredTools = registryManager.notifyDockerRegistryEvents(TEST_REPO, eventsEnvelope);
        Assert.assertEquals(2, registeredTools.size());
        Assert.assertEquals(registeredTools.get(0).getId(), registeredTools.get(1).getId());
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

    private Tool cloneToolAndSetId(Tool tool, Long id) {
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
}
