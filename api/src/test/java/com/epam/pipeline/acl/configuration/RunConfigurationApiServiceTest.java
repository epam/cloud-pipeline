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
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
import java.util.List;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class RunConfigurationApiServiceTest extends AbstractAclTest {

    private final String TEST_STRING = "Test";
    private final RunConfiguration runConfiguration =
            ConfigurationCreatorUtils.getRunConfiguration(1L, SIMPLE_USER, null);
    private final RunConfigurationVO runConfigurationVO =
            ConfigurationCreatorUtils.getRunConfigurationVO(1L, SIMPLE_USER);
    private final RunConfigurationEntry runConfigurationEntry = ConfigurationCreatorUtils.getRunConfigurationEntry();
    private final Folder folder = FolderCreatorUtils.getFolder(1L, SIMPLE_USER);

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
        RunConfiguration runConfiguration =
                ConfigurationCreatorUtils.getRunConfiguration(1L, TEST_STRING, null);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(folder, AclPermission.WRITE);
        initAclEntity(runConfiguration, AclPermission.WRITE);
        doReturn(runConfiguration).when(mockRunConfigurationManager).create(runConfigurationVO);

        final RunConfiguration returnedRunConfiguration = runConfigurationApiService.save(runConfigurationVO);

        assertThat(returnedRunConfiguration).isEqualTo(runConfiguration);
        assertThat(returnedRunConfiguration.getMask()).isEqualTo(2);
    }

    @Test
    @WithMockUser(roles = CONFIGURATION_MANAGER_ROLE)
    public void shouldDenySavingRunConfigurationWhenParentIdIsNull() {
        runConfigurationVO.setParentId(null);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(folder, AclPermission.WRITE);
        doReturn(runConfiguration).when(mockRunConfigurationManager).create(runConfigurationVO);

        assertThrows(AccessDeniedException.class, () -> runConfigurationApiService.save(runConfigurationVO));
    }

    @Test
    @WithMockUser
    public void shouldDenySavingRunConfigurationWithInvalidRole() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(folder, AclPermission.WRITE);
        doReturn(runConfiguration).when(mockRunConfigurationManager).create(runConfigurationVO);

        assertThrows(AccessDeniedException.class, () -> runConfigurationApiService.save(runConfigurationVO));
    }

    @Test
    @WithMockUser(roles = CONFIGURATION_MANAGER_ROLE)
    public void shouldDenySavingRunConfigurationWithInvalidAclPermission() {
        folder.setOwner(TEST_STRING);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(folder);
        doReturn(runConfiguration).when(mockRunConfigurationManager).create(runConfigurationVO);

        assertThrows(AccessDeniedException.class, () -> runConfigurationApiService.save(runConfigurationVO));
    }

    @Test
    @WithMockUser(roles = CONFIGURATION_MANAGER_ROLE)
    public void shouldDenySavingRunConfigurationWithInvalidPermissionToConfiguration() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(folder, AclPermission.WRITE);
        doReturn(true).when(mockConfigurationProviderManager)
                .hasNoPermission(runConfigurationEntry, "EXECUTE");

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
        RunConfiguration runConfiguration =
                ConfigurationCreatorUtils.getRunConfiguration(1L, TEST_STRING, null);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration, AclPermission.WRITE);
        doReturn(runConfiguration).when(mockRunConfigurationManager).update(runConfigurationVO);

        final RunConfiguration returnedRunConfiguration = runConfigurationApiService.update(runConfigurationVO);

        assertThat(returnedRunConfiguration).isEqualTo(runConfiguration);
        assertThat(returnedRunConfiguration.getMask()).isEqualTo(2);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateRunConfigurationWhenPermissionIsNotGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration);
        doReturn(true).when(mockConfigurationProviderManager)
                .hasNoPermission(runConfigurationEntry, "EXECUTE");
        doReturn(runConfiguration).when(mockRunConfigurationManager).update(runConfigurationVO);

        assertThrows(AccessDeniedException.class, () -> runConfigurationApiService.update(runConfigurationVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteRunConfigurationForAdmin() {
        doReturn(runConfiguration).when(mockRunConfigurationManager).delete(1L);

        assertThat(runConfigurationApiService.delete(1L)).isEqualTo(runConfiguration);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = CONFIGURATION_MANAGER_ROLE)
    public void shouldDeleteRunConfigurationWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration, AclPermission.WRITE);
        doReturn(runConfiguration).when(mockRunConfigurationManager).delete(1L);

        assertThat(runConfigurationApiService.delete(1L)).isEqualTo(runConfiguration);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeletionRunConfigurationWhenPermissionIsNotGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration);
        doReturn(runConfiguration).when(mockRunConfigurationManager).delete(1L);

        assertThrows(AccessDeniedException.class, () -> runConfigurationApiService.delete(1L));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadRunConfigurationForAdmin() {
        doReturn(runConfiguration).when(mockRunConfigurationManager).load(1L);

        assertThat(runConfigurationApiService.load(1L)).isEqualTo(runConfiguration);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadRunConfigurationWhenPermissionIsGranted() {
        RunConfiguration runConfiguration =
                ConfigurationCreatorUtils.getRunConfiguration(1L, TEST_STRING, null);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration, AclPermission.READ);
        doReturn(runConfiguration).when(mockRunConfigurationManager).load(1L);

        final RunConfiguration returnedRunConfiguration = runConfigurationApiService.load(1L);

        assertThat(returnedRunConfiguration).isEqualTo(runConfiguration);
        assertThat(returnedRunConfiguration.getMask()).isEqualTo(1);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadingRunConfigurationWhenPermissionIsNotGranted() {
        runConfiguration.setOwner(TEST_STRING);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration);

        doReturn(runConfiguration).when(mockRunConfigurationManager).load(1L);

        assertThrows(AccessDeniedException.class, () -> runConfigurationApiService.load(1L));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAllRunConfigurationForAdmin() {
        doReturn(initSingleRunConfigurationList()).when(mockRunConfigurationManager).loadAll();

        assertThat(runConfigurationApiService.loadAll()).hasSize(1).contains(runConfiguration);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadAllRunConfigurationWhenPermissionIsGranted() {
        RunConfiguration runConfiguration =
                ConfigurationCreatorUtils.getRunConfiguration(1L, TEST_STRING, null);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration, AclPermission.READ);
        doReturn(initSingleRunConfigurationList()).when(mockRunConfigurationManager).loadAll();

        final List<RunConfiguration> returnedRunConfiguration = runConfigurationApiService.loadAll();

        assertThat(returnedRunConfiguration).hasSize(1).contains(runConfiguration);
        assertThat(returnedRunConfiguration.get(0).getMask()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadAllRunConfigurationWhichPermissionIsGranted() {
        final RunConfiguration runConfigurationWithoutPermission =
                ConfigurationCreatorUtils.getRunConfiguration(2L, TEST_STRING, null);
        final List<RunConfiguration> twoRunConfigurationsList = new ArrayList<>();
        twoRunConfigurationsList.add(runConfiguration);
        twoRunConfigurationsList.add(runConfigurationWithoutPermission);

        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration, AclPermission.READ);
        initAclEntity(runConfigurationWithoutPermission);
        doReturn(twoRunConfigurationsList).when(mockRunConfigurationManager).loadAll();

        assertThat(runConfigurationApiService.loadAll()).hasSize(1).contains(runConfiguration);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadingAllRunConfigurationWhichPermissionIsNotGranted() {
        runConfiguration.setOwner(TEST_STRING);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration);
        doReturn(initSingleRunConfigurationList()).when(mockRunConfigurationManager).loadAll();

        assertThat(runConfigurationApiService.loadAll()).isEmpty();
    }

    private List<RunConfiguration> initSingleRunConfigurationList() {
        final List<RunConfiguration> singleRunConfigurationList = new ArrayList<>();
        singleRunConfigurationList.add(runConfiguration);
        return singleRunConfigurationList;
    }
}
