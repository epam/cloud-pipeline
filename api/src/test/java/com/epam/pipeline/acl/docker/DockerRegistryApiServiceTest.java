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
import com.epam.pipeline.entity.docker.DockerRegistryList;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.DockerRegistryEventEnvelope;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.docker.DockerCreatorUtils;
import com.epam.pipeline.test.creator.docker.ToolCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class DockerRegistryApiServiceTest extends AbstractAclTest {

    private static final byte[] BYTE_RESULT = TEST_STRING.getBytes();
    private final DockerRegistry dockerRegistry = DockerCreatorUtils.getDockerRegistry();
    private final DockerRegistry dockerRegistryWithOwner = DockerCreatorUtils.getDockerRegistry(SIMPLE_USER);
    private final DockerRegistryVO dockerRegistryVO = DockerCreatorUtils.getDockerRegistryVO();
    private final DockerRegistryList dockerRegistryList = DockerCreatorUtils.getDockerRegistryList();
    private final Tool tool = ToolCreatorUtils.getTool();
    private final List<Tool> tools = Collections.singletonList(tool);
    private final DockerRegistryEventEnvelope eventEnvelope = DockerCreatorUtils.getDockerRegistryEventEnvelope();

    @Autowired
    private DockerRegistryApiService dockerRegistryApiService;

    @Autowired
    private DockerRegistryManager mockDockerRegistryManager;

    @Autowired
    private AuthManager mockAuthManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateDockerRegistryForAdmin() {
        doReturn(dockerRegistry).when(mockDockerRegistryManager).create(dockerRegistryVO);

        assertThat(dockerRegistryApiService.create(dockerRegistryVO)).isEqualTo(dockerRegistry);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateDockerRegistryForNonAdminUser() {
        doReturn(dockerRegistry).when(mockDockerRegistryManager).create(dockerRegistryVO);

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
        initAclEntity(dockerRegistryWithOwner, AclPermission.WRITE);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        doReturn(dockerRegistryWithOwner).when(mockDockerRegistryManager).updateDockerRegistry(dockerRegistry);

        final DockerRegistry resultRegistry = dockerRegistryApiService.updateDockerRegistry(dockerRegistry);

        assertThat(resultRegistry).isEqualTo(dockerRegistryWithOwner);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateDockerRegistryWhenPermissionIsNotGranted() {
        doReturn(dockerRegistryWithOwner).when(mockDockerRegistryManager).updateDockerRegistry(dockerRegistry);
        initAclEntity(dockerRegistryWithOwner);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();

        assertThrows(AccessDeniedException.class,
            () -> dockerRegistryApiService.updateDockerRegistry(dockerRegistryWithOwner));
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
        doReturn(dockerRegistry).when(mockDockerRegistryManager).updateDockerRegistryCredentials(dockerRegistryVO);

        assertThrows(AccessDeniedException.class,
            () -> dockerRegistryApiService.updateDockerRegistryCredentials(dockerRegistryVO));
    }

    @Test
    @WithMockUser
    public void shouldListDockerRegistriesWithCerts() {
        doReturn(dockerRegistryList).when(mockDockerRegistryManager).listAllDockerRegistriesWithCerts();

        assertThat(dockerRegistryApiService.listDockerRegistriesWithCerts()).isEqualTo(dockerRegistryList);
    }

    @Test
    @WithMockUser
    public void shouldLoadAllRegistriesContent() {
        doReturn(dockerRegistryList).when(mockDockerRegistryManager).loadAllRegistriesContent();

        assertThat(dockerRegistryApiService.loadAllRegistriesContent()).isEqualTo(dockerRegistryList);
    }

    @Test
    @WithMockUser
    public void shouldLoadDockerRegistry() {
        doReturn(dockerRegistry).when(mockDockerRegistryManager).load(ID);

        assertThat(dockerRegistryApiService.load(ID)).isEqualTo(dockerRegistry);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteDockerRegistryForAdmin() {
        doReturn(dockerRegistry).when(mockDockerRegistryManager).delete(ID, true);

        assertThat(mockDockerRegistryManager.delete(ID, true)).isEqualTo(dockerRegistry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDeleteDockerRegistryWhenPermissionIsGranted() {
        doReturn(dockerRegistryWithOwner).when(mockDockerRegistryManager).delete(ID, true);
        initAclEntity(dockerRegistryWithOwner, AclPermission.WRITE);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();

        assertThat(mockDockerRegistryManager.delete(ID, true)).isEqualTo(dockerRegistryWithOwner);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteDockerRegistryWhenPermissionIsNotGranted() {
        doReturn(dockerRegistryWithOwner).when(mockDockerRegistryManager).delete(ID, true);
        initAclEntity(dockerRegistryWithOwner);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();

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
        final JwtRawToken jwtRawToken = new JwtRawToken(TEST_STRING);
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
        initAclEntity(dockerRegistryWithOwner, AclPermission.READ);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();

        assertThat(dockerRegistryApiService.getCertificateContent(ID)).isEqualTo(BYTE_RESULT);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetCertificateContentWhenPermissionIsNotGranted() {
        doReturn(BYTE_RESULT).when(mockDockerRegistryManager).getCertificateContent(ID);
        initAclEntity(dockerRegistryWithOwner, AclPermission.READ);

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
        initAclEntity(dockerRegistryWithOwner, AclPermission.READ);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();

        assertThat(dockerRegistryApiService.getConfigScript(ID)).isEqualTo(BYTE_RESULT);
    }

    @Test
    @WithMockUser
    public void shouldDenyGetConfigScriptWhenPermissionIsNotGranted() {
        doReturn(BYTE_RESULT).when(mockDockerRegistryManager).getConfigScript(ID);
        initAclEntity(dockerRegistryWithOwner, AclPermission.READ);

        assertThrows(AccessDeniedException.class, () -> dockerRegistryApiService.getConfigScript(ID));
    }
}
