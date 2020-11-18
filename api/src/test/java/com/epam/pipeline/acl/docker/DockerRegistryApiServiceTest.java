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

package com.epam.pipeline.acl.docker;

import com.epam.pipeline.controller.vo.docker.DockerRegistryVO;
import com.epam.pipeline.entity.AbstractHierarchicalEntity;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.docker.DockerRegistryList;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.DockerRegistryEventEnvelope;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.docker.DockerCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_3;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class DockerRegistryApiServiceTest extends AbstractAclTest {

    private static final byte[] BYTE_RESULT = TEST_STRING.getBytes();
    private final DockerRegistry dockerRegistry = DockerCreatorUtils.getDockerRegistry(ANOTHER_SIMPLE_USER);
    private final DockerRegistryVO dockerRegistryVO = DockerCreatorUtils.getDockerRegistryVO();
    private final DockerRegistryList dockerRegistryList = DockerCreatorUtils.getDockerRegistryList(dockerRegistry);
    private final Tool tool = DockerCreatorUtils.getTool(ANOTHER_SIMPLE_USER);
    private final List<Tool> tools = Collections.singletonList(tool);
    private final DockerRegistryEventEnvelope eventEnvelope = DockerCreatorUtils.getDockerRegistryEventEnvelope();
    private final JwtRawToken jwtRawToken = new JwtRawToken(TEST_STRING);
    private final DockerRegistry dockerRegistryWithTools = DockerCreatorUtils.getDockerRegistry(ANOTHER_SIMPLE_USER);
    private final ToolGroup toolGroupWithoutPermission = DockerCreatorUtils.getToolGroup(ANOTHER_SIMPLE_USER);
    private final ToolGroup toolGroup = DockerCreatorUtils.getToolGroup(ID_2, ANOTHER_SIMPLE_USER);
    private final ToolGroup emptyToolGroupWithoutPermission = DockerCreatorUtils.getToolGroup(ID_3, ANOTHER_SIMPLE_USER);
    private final Tool toolRead = DockerCreatorUtils.getTool(ANOTHER_SIMPLE_USER);
    private final Tool toolWithoutPermission = DockerCreatorUtils.getTool(ID_2, ANOTHER_SIMPLE_USER);
    private final List<Tool> toolList = Arrays.asList(toolRead, toolWithoutPermission);
    private final List<ToolGroup> toolGroups =
            Arrays.asList(toolGroup, toolGroupWithoutPermission, emptyToolGroupWithoutPermission);


    @Autowired
    private DockerRegistryApiService dockerRegistryApiService;

    @Autowired
    private DockerRegistryManager mockDockerRegistryManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateDockerRegistryForAdmin() {
        doReturn(dockerRegistry).when(mockDockerRegistryManager).create(dockerRegistryVO);

        assertThat(dockerRegistryApiService.create(dockerRegistryVO)).isEqualTo(dockerRegistry);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateDockerRegistryForNonAdminUser() {
        assertThrows(AccessDeniedException.class, () -> dockerRegistryApiService.create(dockerRegistryVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateDockerRegistryForAdmin() {
        doReturn(dockerRegistry).when(mockDockerRegistryManager).updateDockerRegistry(dockerRegistry);

        assertThat(dockerRegistryApiService.updateDockerRegistry(dockerRegistry)).isEqualTo(dockerRegistry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateDockerRegistryWhenPermissionIsGranted() {
        initAclEntity(dockerRegistry, AclPermission.WRITE);
        doReturn(dockerRegistry).when(mockDockerRegistryManager).updateDockerRegistry(dockerRegistry);

        assertThat(dockerRegistryApiService.updateDockerRegistry(dockerRegistry)).isEqualTo(dockerRegistry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateDockerRegistryHierarchyWhenPermissionIsGranted() {
        initDockerRegistryAclTree();
        initAclEntity(dockerRegistryWithTools, AclPermission.WRITE);
        doReturn(dockerRegistryWithTools).when(mockDockerRegistryManager).updateDockerRegistry(dockerRegistryWithTools);

        final DockerRegistry returnedDr = dockerRegistryApiService.updateDockerRegistry(dockerRegistryWithTools);
        assertPartialDockerRegistryAclTree(returnedDr);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyUpdateDockerRegistryWhenPermissionIsNotGranted() {
        initAclEntity(dockerRegistry);

        assertThrows(AccessDeniedException.class,
            () -> dockerRegistryApiService.updateDockerRegistry(dockerRegistry));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateDockerRegistryCredentialsForAdmin() {
        doReturn(dockerRegistry).when(mockDockerRegistryManager).updateDockerRegistryCredentials(dockerRegistryVO);

        assertThat(dockerRegistryApiService.updateDockerRegistryCredentials(dockerRegistryVO))
                .isEqualTo(dockerRegistry);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateDockerRegistryCredentialsForNonAdminUser() {
        assertThrows(AccessDeniedException.class,
            () -> dockerRegistryApiService.updateDockerRegistryCredentials(dockerRegistryVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateWholeDockerRegistryHierarchyCredentialsForAdmin() {
        initDockerRegistryAclTree();
        doReturn(dockerRegistryWithTools).when(mockDockerRegistryManager)
                .updateDockerRegistryCredentials(dockerRegistryVO);

        final DockerRegistry returnedDr = dockerRegistryApiService.updateDockerRegistryCredentials(dockerRegistryVO);

        assertWholeDockerRegistryAclTree(returnedDr);
    }

    @Test
    @WithMockUser
    public void shouldListDockerRegistriesWithCerts() {
        doReturn(dockerRegistryList).when(mockDockerRegistryManager).listAllDockerRegistriesWithCerts();

        assertThat(dockerRegistryApiService.listDockerRegistriesWithCerts()).isEqualTo(dockerRegistryList);
    }

    @Test
    @WithMockUser(SIMPLE_USER)
    public void shouldListDockerRegistryHierarchyWithCerts() {
        final DockerRegistryList dockerRegistryList = DockerCreatorUtils.getDockerRegistryList(dockerRegistry);
        final DockerRegistry anotherRegistry = DockerCreatorUtils.getDockerRegistry(ID_3, ANOTHER_SIMPLE_USER);
        dockerRegistryList.setRegistries(Arrays.asList(dockerRegistryWithTools, anotherRegistry));
        initDockerRegistryAclTree();
        doReturn(dockerRegistryList).when(mockDockerRegistryManager).listAllDockerRegistriesWithCerts();

        final DockerRegistry returnedDr = (DockerRegistry) dockerRegistryApiService
                .listDockerRegistriesWithCerts().getChildren().get(0);

        assertPartialDockerRegistryAclTree(returnedDr);
        assertThat(dockerRegistryApiService.listDockerRegistriesWithCerts().getChildren().size()).isEqualTo(1);
    }

    @Test
    @WithMockUser
    public void shouldLoadAllRegistriesContent() {
        doReturn(dockerRegistryList).when(mockDockerRegistryManager).loadAllRegistriesContent();

        assertThat(dockerRegistryApiService.loadAllRegistriesContent()).isEqualTo(dockerRegistryList);
    }

    @Test
    @WithMockUser(SIMPLE_USER)
    public void shouldLoadAllRegistriesHierarchyContent() {
        final DockerRegistryList dockerRegistryList = DockerCreatorUtils.getDockerRegistryList(dockerRegistry);
        final DockerRegistry anotherRegistry = DockerCreatorUtils.getDockerRegistry(ID_3, ANOTHER_SIMPLE_USER);
        dockerRegistryList.setRegistries(Arrays.asList(dockerRegistryWithTools, anotherRegistry));
        initDockerRegistryAclTree();
        doReturn(dockerRegistryList).when(mockDockerRegistryManager).loadAllRegistriesContent();

        final DockerRegistry returnedDr = (DockerRegistry) dockerRegistryApiService
                .loadAllRegistriesContent().getChildren().get(0);

        assertPartialDockerRegistryAclTree(returnedDr);
        assertThat(dockerRegistryApiService.loadAllRegistriesContent().getChildren().size()).isEqualTo(1);
    }

    @Test
    @WithMockUser
    public void shouldLoadDockerRegistry() {
        doReturn(dockerRegistry).when(mockDockerRegistryManager).load(ID);

        assertThat(dockerRegistryApiService.load(ID)).isEqualTo(dockerRegistry);
    }

    @Test
    @WithMockUser(SIMPLE_USER)
    public void shouldLoadDockerRegistryHierarchy() {
        initDockerRegistryAclTree();
        doReturn(dockerRegistryWithTools).when(mockDockerRegistryManager).load(ID);

        final DockerRegistry returnedDr = dockerRegistryApiService.load(ID);

        assertPartialDockerRegistryAclTree(returnedDr);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteDockerRegistryForAdmin() {
        doReturn(dockerRegistry).when(mockDockerRegistryManager).delete(ID, true);

        assertThat(dockerRegistryApiService.delete(ID, true)).isEqualTo(dockerRegistry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDeleteDockerRegistryWhenPermissionIsGranted() {
        doReturn(dockerRegistry).when(mockDockerRegistryManager).delete(ID, true);
        initAclEntity(dockerRegistry, AclPermission.WRITE);

        assertThat(dockerRegistryApiService.delete(ID, true)).isEqualTo(dockerRegistry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyDeleteDockerRegistryWhenPermissionIsNotGranted() {
        initAclEntity(dockerRegistry);

        assertThrows(AccessDeniedException.class, () -> dockerRegistryApiService.delete(ID, true));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldNotifyDockerRegistryEventsForAdmin() {
        doReturn(tools).when(mockDockerRegistryManager).notifyDockerRegistryEvents(TEST_STRING, eventEnvelope);

        assertThat(dockerRegistryApiService.notifyDockerRegistryEvents(TEST_STRING, eventEnvelope)).isEqualTo(tools);
    }

    @Test
    @WithMockUser
    public void shouldDenyNotifyDockerRegistryEventsForNotAdmin() {
        doReturn(tools).when(mockDockerRegistryManager).notifyDockerRegistryEvents(TEST_STRING, eventEnvelope);

        assertThrows(AccessDeniedException.class,
            () -> dockerRegistryApiService.notifyDockerRegistryEvents(TEST_STRING, eventEnvelope));
    }

    @Test
    public void shouldIssueTokenForDockerRegistry() {
        doReturn(jwtRawToken).when(mockDockerRegistryManager)
                .issueTokenForDockerRegistry(TEST_STRING, TEST_STRING, TEST_STRING, TEST_STRING);

        assertThat(dockerRegistryApiService
                .issueTokenForDockerRegistry(TEST_STRING, TEST_STRING, TEST_STRING, TEST_STRING))
                .isEqualTo(jwtRawToken);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetCertificateContentForAdmin() {
        doReturn(BYTE_RESULT).when(mockDockerRegistryManager).getCertificateContent(ID);

        assertThat(dockerRegistryApiService.getCertificateContent(ID)).isEqualTo(BYTE_RESULT);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldGetCertificateContentWhenPermissionIsGranted() {
        doReturn(BYTE_RESULT).when(mockDockerRegistryManager).getCertificateContent(ID);
        initAclEntity(dockerRegistry, AclPermission.READ);

        assertThat(dockerRegistryApiService.getCertificateContent(ID)).isEqualTo(BYTE_RESULT);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyGetCertificateContentWhenPermissionIsNotGranted() {
        initAclEntity(dockerRegistry);

        assertThrows(AccessDeniedException.class, () -> dockerRegistryApiService.getCertificateContent(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetConfigScript() {
        doReturn(BYTE_RESULT).when(mockDockerRegistryManager).getConfigScript(ID);

        assertThat(dockerRegistryApiService.getConfigScript(ID)).isEqualTo(BYTE_RESULT);
    }

    @Test
    @WithMockUser(SIMPLE_USER)
    public void shouldGetConfigScriptWhenPermissionIsGranted() {
        doReturn(BYTE_RESULT).when(mockDockerRegistryManager).getConfigScript(ID);
        initAclEntity(dockerRegistry, AclPermission.READ);

        assertThat(dockerRegistryApiService.getConfigScript(ID)).isEqualTo(BYTE_RESULT);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyGetConfigScriptWhenPermissionIsNotGranted() {
        initAclEntity(dockerRegistry);

        assertThrows(AccessDeniedException.class, () -> dockerRegistryApiService.getConfigScript(ID));
    }

    private void initDockerRegistryAclTree() {
        toolGroup.setTools(toolList);
        toolGroupWithoutPermission.setTools(toolList);
        dockerRegistryWithTools.setGroups(toolGroups);
        initAclEntity(toolRead, AclPermission.READ);
        initAclEntity(toolWithoutPermission);
        initAclEntity(toolGroup, AclPermission.READ);
        initAclEntity(toolGroupWithoutPermission);
        initAclEntity(emptyToolGroupWithoutPermission, AclPermission.READ);
    }

    private void assertPartialDockerRegistryAclTree(final DockerRegistry registry) {
        assertDockerRegistryAclHierarchy(registry);
        final List<? extends AbstractSecuredEntity> toolGroupWithoutPermissionLeaves =
                registry.getChildren().get(1).getLeaves();
        assertThat(toolGroupWithoutPermissionLeaves.size()).isEqualTo(1);
        assertThat(toolGroupWithoutPermissionLeaves.get(0)).isEqualTo(toolRead);
    }

    private void assertWholeDockerRegistryAclTree(final DockerRegistry registry) {
        assertDockerRegistryAclHierarchy(registry);
        final List<? extends AbstractSecuredEntity> toolGroupWithoutPermissionLeaves =
                registry.getChildren().get(1).getLeaves();

        assertThat(toolGroupWithoutPermissionLeaves.size()).isEqualTo(toolList.size());
        assertThat(toolGroupWithoutPermissionLeaves).isEqualTo(toolList);
    }

    private void assertDockerRegistryAclHierarchy(final DockerRegistry registry) {
        final List<AbstractHierarchicalEntity> dockerRegistryChildren = registry.getChildren();
        final List<? extends AbstractSecuredEntity> toolGroupLeaves = registry.getChildren().get(0).getLeaves();
        final List<? extends AbstractSecuredEntity> emptyToolGroupLeaves = registry.getChildren().get(2).getLeaves();

        assertThat(dockerRegistryChildren.size()).isEqualTo(toolGroups.size());
        assertThat(dockerRegistryChildren).isEqualTo(toolGroups);
        assertThat(toolGroupLeaves.size()).isEqualTo(toolList.size());
        assertThat(toolGroupLeaves).isEqualTo(toolList);
        assertThat(emptyToolGroupLeaves).isEmpty();
    }
}
