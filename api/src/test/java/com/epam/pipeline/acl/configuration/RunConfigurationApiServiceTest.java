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
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class RunConfigurationApiServiceTest extends AbstractAclTest {

    private final RunConfiguration runConfiguration = ConfigurationCreatorUtils.getFirstRunConfigurationWithoutParent();
    private final RunConfiguration secondRunConfiguration =
            ConfigurationCreatorUtils.getSecondRunConfigurationWithoutParent();
    private final RunConfigurationVO runConfigurationVO = ConfigurationCreatorUtils.getRunConfigurationVOWithId();
    private final RunConfigurationEntry runConfigurationEntry = ConfigurationCreatorUtils.getRunConfigurationEntry();
    private final Folder folder = FolderCreatorUtils.getFolder();

    private final String TEST_STRING = "TEST";

    @Autowired
    private RunConfigurationApiService runConfigurationApiService;

    @Autowired
    private RunConfigurationManager mockRunConfigurationManager;

    @Autowired
    private AuthManager mockAuthManager;

    @Autowired
    protected ConfigurationProviderManager mockConfigurationProviderManager;

    private List<RunConfiguration> initSingleRunConfigurationList() {
        List<RunConfiguration> singleRunConfigurationList = new ArrayList<>();
        singleRunConfigurationList.add(runConfiguration);
        return singleRunConfigurationList;
    }

    private List<RunConfiguration> initTwoRunConfigurationsList() {
        List<RunConfiguration> twoRunConfigurationsList = new ArrayList<>();
        twoRunConfigurationsList.add(runConfiguration);
        twoRunConfigurationsList.add(secondRunConfiguration);
        return twoRunConfigurationsList;
    }

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
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(folder,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.WRITE.getMask())));
        doReturn(runConfiguration).when(mockRunConfigurationManager).create(runConfigurationVO);

        assertThat(runConfigurationApiService.save(runConfigurationVO)).isEqualTo(runConfiguration);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = CONFIGURATION_MANAGER_ROLE)
    public void shouldDenySavingRunConfigurationWhenParentIdIsNull() {
        final RunConfigurationVO runConfigVoWithoutParentId = ConfigurationCreatorUtils.getRunConfigurationVO();
        runConfigVoWithoutParentId.setParentId(null);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(folder,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.WRITE.getMask())));
        doReturn(runConfiguration).when(mockRunConfigurationManager).create(runConfigVoWithoutParentId);

        assertThrows(AccessDeniedException.class, () -> runConfigurationApiService.save(runConfigVoWithoutParentId));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = SIMPLE_USER_ROLE)
    public void shouldDenySavingRunConfigurationWithInvalidRole() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(folder,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.WRITE.getMask())));
        doReturn(runConfiguration).when(mockRunConfigurationManager).create(runConfigurationVO);

        assertThrows(AccessDeniedException.class, () -> runConfigurationApiService.save(runConfigurationVO));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = CONFIGURATION_MANAGER_ROLE)
    public void shouldDenySavingRunConfigurationWithInvalidAclPermission() {
        folder.setOwner(TEST_STRING);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(folder);
        doReturn(runConfiguration).when(mockRunConfigurationManager).create(runConfigurationVO);

        assertThrows(AccessDeniedException.class, () -> runConfigurationApiService.save(runConfigurationVO));
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = CONFIGURATION_MANAGER_ROLE)
    public void shouldDenySavingRunConfigurationWithInvalidPermissionToConfiguration() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(folder,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.WRITE.getMask())));
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
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.WRITE.getMask())));
        doReturn(runConfiguration).when(mockRunConfigurationManager).update(runConfigurationVO);

        assertThat(runConfigurationApiService.update(runConfigurationVO)).isEqualTo(runConfiguration);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
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
        doReturn(secondRunConfiguration).when(mockRunConfigurationManager).delete(2L);

        assertThat(runConfigurationApiService.delete(2L)).isEqualTo(secondRunConfiguration);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = CONFIGURATION_MANAGER_ROLE)
    public void shouldDeleteRunConfigurationWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.WRITE.getMask())));
        doReturn(runConfiguration).when(mockRunConfigurationManager).delete(1L);

        assertThat(runConfigurationApiService.delete(1L)).isEqualTo(runConfiguration);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
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
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.READ.getMask())));
        doReturn(runConfiguration).when(mockRunConfigurationManager).load(1L);

        assertThat(runConfigurationApiService.load(1L)).isEqualTo(runConfiguration);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
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

        List<RunConfiguration> resultConfigurationList = runConfigurationApiService.loadAll();

        assertThat(resultConfigurationList.size()).isEqualTo(1);
        assertThat(resultConfigurationList.get(0)).isEqualTo(runConfiguration);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadAllRunConfigurationWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.READ.getMask())));
        doReturn(initSingleRunConfigurationList()).when(mockRunConfigurationManager).loadAll();

        List<RunConfiguration> resultConfigurationList = runConfigurationApiService.loadAll();

        assertThat(resultConfigurationList.size()).isEqualTo(1);
        assertThat(resultConfigurationList.get(0)).isEqualTo(runConfiguration);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadAllRunConfigurationWhichPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.READ.getMask())));
        initAclEntity(secondRunConfiguration);
        doReturn(initTwoRunConfigurationsList()).when(mockRunConfigurationManager).loadAll();

        List<RunConfiguration> resultConfigurationList = runConfigurationApiService.loadAll();

        assertThat(resultConfigurationList.size()).isEqualTo(1);
        assertThat(resultConfigurationList.get(0)).isEqualTo(runConfiguration);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadingAllRunConfigurationWhichPermissionIsNotGranted() {
        runConfiguration.setOwner(TEST_STRING);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration);
        doReturn(initSingleRunConfigurationList()).when(mockRunConfigurationManager).loadAll();

        assertThat(runConfigurationApiService.loadAll()).isEmpty();
    }
}
