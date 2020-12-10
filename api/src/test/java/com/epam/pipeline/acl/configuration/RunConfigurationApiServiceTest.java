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

package com.epam.pipeline.acl.configuration;

import com.epam.pipeline.controller.vo.configuration.RunConfigurationVO;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.configuration.RunConfigurationEntry;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.manager.configuration.RunConfigurationManager;
import com.epam.pipeline.manager.pipeline.runner.ConfigurationProviderManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.configuration.ConfigurationCreatorUtils;
import com.epam.pipeline.test.creator.folder.FolderCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class RunConfigurationApiServiceTest extends AbstractAclTest {

    private final RunConfiguration runConfiguration =
            ConfigurationCreatorUtils.getRunConfiguration(ID, SIMPLE_USER);
    private final RunConfigurationVO runConfigurationVO =
            ConfigurationCreatorUtils.getRunConfigurationVO(ID, ID);
    private final RunConfigurationEntry runConfigurationEntry = ConfigurationCreatorUtils.getRunConfigurationEntry();
    private final Folder folder = FolderCreatorUtils.getFolder(ID, SIMPLE_USER);
    private final Authentication authentication = new TestingAuthenticationToken(new Object(), new Object());

    @Autowired
    private RunConfigurationApiService runConfigurationApiService;

    @Autowired
    private RunConfigurationManager mockRunConfigurationManager;

    @Autowired
    private AuthManager mockAuthManager;

    @Autowired
    private ConfigurationProviderManager mockConfigurationProviderManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldSaveRunConfigurationForAdmin() {
        initAclEntity(folder);
        doReturn(runConfiguration).when(mockRunConfigurationManager).create(runConfigurationVO);

        assertThat(runConfigurationApiService.save(runConfigurationVO)).isEqualTo(runConfiguration);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = CONFIGURATION_MANAGER_ROLE)
    public void shouldSaveRunConfigurationWhenPermissionIsGranted() {
        final RunConfiguration runConfiguration =
                ConfigurationCreatorUtils.getRunConfiguration(ID, ANOTHER_SIMPLE_USER);
        initAclEntity(folder, AclPermission.WRITE);
        initAclEntity(runConfiguration, AclPermission.WRITE);
        mockUser(SIMPLE_USER);
        doReturn(runConfiguration).when(mockRunConfigurationManager).create(runConfigurationVO);

        final RunConfiguration returnedRunConfiguration = runConfigurationApiService.save(runConfigurationVO);

        assertThat(returnedRunConfiguration).isEqualTo(runConfiguration);
        assertThat(returnedRunConfiguration.getMask()).isEqualTo(2);
    }

    @Test
    @WithMockUser(roles = CONFIGURATION_MANAGER_ROLE)
    public void shouldDenySavingRunConfigurationWhenParentIdIsNull() {
        final RunConfigurationVO runConfigurationVO = ConfigurationCreatorUtils.getRunConfigurationVO(ID, null);
        initAclEntity(folder, AclPermission.WRITE);
        mockUser(SIMPLE_USER);
        doReturn(runConfiguration).when(mockRunConfigurationManager).create(runConfigurationVO);

        assertThrows(AccessDeniedException.class, () -> runConfigurationApiService.save(runConfigurationVO));
    }

    @Test
    @WithMockUser
    public void shouldDenySavingRunConfigurationWithInvalidRole() {
        initAclEntity(folder, AclPermission.WRITE);
        mockUser(SIMPLE_USER);
        doReturn(runConfiguration).when(mockRunConfigurationManager).create(runConfigurationVO);

        assertThrows(AccessDeniedException.class, () -> runConfigurationApiService.save(runConfigurationVO));
    }

    @Test
    @WithMockUser(roles = CONFIGURATION_MANAGER_ROLE)
    public void shouldDenySavingRunConfigurationWhenParentPermissionIsNotGranted() {
        final Folder folder = FolderCreatorUtils.getFolder(ID, ANOTHER_SIMPLE_USER);
        initAclEntity(folder);
        mockUser(SIMPLE_USER);
        doReturn(runConfiguration).when(mockRunConfigurationManager).create(runConfigurationVO);

        assertThrows(AccessDeniedException.class, () -> runConfigurationApiService.save(runConfigurationVO));
    }

    @Test
    @WithMockUser(roles = CONFIGURATION_MANAGER_ROLE)
    public void shouldDenySavingRunConfigurationWithInvalidPermissionToConfiguration() {
        initAclEntity(folder, AclPermission.WRITE);
        mockUser(SIMPLE_USER);
        doReturn(true).when(mockConfigurationProviderManager)
                .hasNoPermission(runConfigurationEntry, PERMISSION_EXECUTE);
        doReturn(runConfiguration).when(mockRunConfigurationManager).create(runConfigurationVO);

        assertThrows(AccessDeniedException.class, () -> runConfigurationApiService.save(runConfigurationVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateRunConfigurationForAdmin() {
        doReturn(runConfiguration).when(mockRunConfigurationManager).update(runConfigurationVO);

        assertThat(runConfigurationApiService.update(runConfigurationVO)).isEqualTo(runConfiguration);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateRunConfigurationWhenPermissionIsGranted() {
        final RunConfiguration runConfiguration = ConfigurationCreatorUtils.getRunConfiguration(ID, OWNER_USER);
        final RunConfigurationVO runConfigurationVO = mock(RunConfigurationVO.class);
        initAclEntity(runConfiguration, AclPermission.WRITE);
        mockUser(OWNER_USER);
        doReturn(runConfiguration).when(runConfigurationVO).toEntity();
        doReturn(runConfiguration).when(mockRunConfigurationManager).load(anyLong());
        doReturn(runConfiguration).when(mockRunConfigurationManager).update(runConfigurationVO);

        final RunConfiguration returnedRunConfiguration = runConfigurationApiService.update(runConfigurationVO);

        assertThat(returnedRunConfiguration).isEqualTo(runConfiguration);
        assertThat(returnedRunConfiguration.getMask()).isEqualTo(2);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateRunConfigurationWhenPermissionIsNotGranted() {
        initAclEntity(runConfiguration);
        mockUser(SIMPLE_USER);
        doReturn(authentication).when(mockAuthManager).getAuthentication();
        doReturn(true).when(mockConfigurationProviderManager)
                .hasNoPermission(runConfigurationEntry, PERMISSION_EXECUTE);
        doReturn(runConfiguration).when(mockRunConfigurationManager).update(runConfigurationVO);

        assertThrows(AccessDeniedException.class, () -> runConfigurationApiService.update(runConfigurationVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteRunConfigurationForAdmin() {
        doReturn(runConfiguration).when(mockRunConfigurationManager).delete(ID);

        assertThat(runConfigurationApiService.delete(ID)).isEqualTo(runConfiguration);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = CONFIGURATION_MANAGER_ROLE)
    public void shouldDeleteRunConfigurationWhenPermissionIsGranted() {
        initAclEntity(runConfiguration, AclPermission.WRITE);
        mockUser(SIMPLE_USER);
        doReturn(runConfiguration).when(mockRunConfigurationManager).delete(ID);

        assertThat(runConfigurationApiService.delete(ID)).isEqualTo(runConfiguration);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeletionRunConfigurationWhenPermissionIsNotGranted() {
        initAclEntity(runConfiguration);
        mockUser(SIMPLE_USER);
        doReturn(runConfiguration).when(mockRunConfigurationManager).delete(ID);

        assertThrows(AccessDeniedException.class, () -> runConfigurationApiService.delete(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadRunConfigurationForAdmin() {
        doReturn(runConfiguration).when(mockRunConfigurationManager).load(ID);

        assertThat(runConfigurationApiService.load(ID)).isEqualTo(runConfiguration);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadRunConfigurationWhenPermissionIsGranted() {
        final RunConfiguration runConfiguration =
                ConfigurationCreatorUtils.getRunConfiguration(ID, ANOTHER_SIMPLE_USER);
        initAclEntity(runConfiguration, AclPermission.READ);
        mockUser(SIMPLE_USER);
        doReturn(runConfiguration).when(mockRunConfigurationManager).load(ID);

        final RunConfiguration returnedRunConfiguration = runConfigurationApiService.load(ID);

        assertThat(returnedRunConfiguration).isEqualTo(runConfiguration);
        assertThat(returnedRunConfiguration.getMask()).isEqualTo(1);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadingRunConfigurationWhenPermissionIsNotGranted() {
        final RunConfiguration runConfiguration =
                ConfigurationCreatorUtils.getRunConfiguration(ID, ANOTHER_SIMPLE_USER);
        initAclEntity(runConfiguration);
        mockUser(SIMPLE_USER);
        doReturn(runConfiguration).when(mockRunConfigurationManager).load(ID);

        assertThrows(AccessDeniedException.class, () -> runConfigurationApiService.load(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAllRunConfigurationForAdmin() {
        doReturn(mutableListOf(runConfiguration)).when(mockRunConfigurationManager).loadAll();

        assertThat(runConfigurationApiService.loadAll()).hasSize(1).contains(runConfiguration);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadAllRunConfigurationWhenPermissionIsGranted() {
        final RunConfiguration runConfiguration =
                ConfigurationCreatorUtils.getRunConfiguration(ID, ANOTHER_SIMPLE_USER);
        initAclEntity(runConfiguration, AclPermission.READ);
        mockUser(SIMPLE_USER);
        doReturn(mutableListOf(runConfiguration)).when(mockRunConfigurationManager).loadAll();

        final List<RunConfiguration> returnedRunConfiguration = runConfigurationApiService.loadAll();

        assertThat(returnedRunConfiguration).hasSize(1).contains(runConfiguration);
        assertThat(returnedRunConfiguration.get(0).getMask()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadAllRunConfigurationWhichPermissionIsGranted() {
        final RunConfiguration anotherRunConfiguration =
                ConfigurationCreatorUtils.getRunConfiguration(ID_2, ANOTHER_SIMPLE_USER);
        initAclEntity(runConfiguration, AclPermission.READ);
        initAclEntity(anotherRunConfiguration);
        mockUser(SIMPLE_USER);
        doReturn(mutableListOf(runConfiguration, anotherRunConfiguration)).when(mockRunConfigurationManager).loadAll();

        assertThat(runConfigurationApiService.loadAll()).hasSize(1).contains(runConfiguration);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadingAllRunConfigurationWhichPermissionIsNotGranted() {
        final RunConfiguration runConfiguration =
                ConfigurationCreatorUtils.getRunConfiguration(ID, ANOTHER_SIMPLE_USER);
        initAclEntity(runConfiguration);
        mockUser(SIMPLE_USER);
        doReturn(mutableListOf(runConfiguration)).when(mockRunConfigurationManager).loadAll();

        assertThat(runConfigurationApiService.loadAll()).isEmpty();
    }
}
