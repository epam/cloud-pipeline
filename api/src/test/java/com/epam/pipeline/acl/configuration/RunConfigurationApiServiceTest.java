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
import com.epam.pipeline.manager.configuration.RunConfigurationManager;
import com.epam.pipeline.manager.pipeline.runner.ConfigurationProviderManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.configuration.ConfigurationCreatorUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class RunConfigurationApiServiceTest extends AbstractAclTest {

    @Autowired
    private RunConfigurationApiService runConfigurationApiService;

    @Autowired
    private RunConfigurationManager mockRunConfigurationManager;

    @Autowired
    private AuthManager mockAuthManager;

    @Autowired
    protected ConfigurationProviderManager mockConfigurationProviderManager;

    private final RunConfiguration runConfiguration = ConfigurationCreatorUtils.getRunConfiguration();

    private final RunConfiguration runConfigurationOnlyForAdmin = ConfigurationCreatorUtils.getRunConfiguration();

    private final RunConfigurationVO runConfigurationVO = ConfigurationCreatorUtils.getRunConfigurationVO();

    private final RunConfigurationEntry runConfigurationEntry = ConfigurationCreatorUtils.getRunConfigurationEntry();

    private List<RunConfiguration> singleRunConfigurationList;

    private List<RunConfiguration> twoRunConfigurationsList;

    @Before
    public void setUp() {
        runConfiguration.setId(1L);
        runConfiguration.setParent(null);

        runConfigurationOnlyForAdmin.setId(2L);
        runConfigurationOnlyForAdmin.setParent(null);

        runConfigurationVO.setOwner(SIMPLE_USER);

        singleRunConfigurationList = new ArrayList<>();
        singleRunConfigurationList.add(runConfiguration);

        twoRunConfigurationsList = new ArrayList<>();
        twoRunConfigurationsList.add(runConfiguration);
        twoRunConfigurationsList.add(runConfigurationOnlyForAdmin);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateRunConfigurationForAdmin() {
        doReturn(runConfiguration).when(mockRunConfigurationManager).update(runConfigurationVO);

        RunConfiguration resultConfiguration = runConfigurationApiService.update(runConfigurationVO);

        assertThat(resultConfiguration).isEqualTo(resultConfiguration);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateRunConfigurationWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.WRITE.getMask())));
        doReturn(runConfiguration).when(mockRunConfigurationManager).update(runConfigurationVO);

        RunConfiguration resultConfiguration = runConfigurationApiService.update(runConfigurationVO);

        assertThat(resultConfiguration).isEqualTo(resultConfiguration);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyUpdateRunConfigurationWhenPermissionIsNotGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration);
        doReturn(true).when(mockConfigurationProviderManager)
                .hasNoPermission(runConfigurationEntry, "EXECUTE");
        doReturn(runConfiguration).when(mockRunConfigurationManager).update(runConfigurationVO);

        runConfigurationApiService.update(runConfigurationVO);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteRunConfigurationForAdmin() {
        doReturn(runConfiguration).when(mockRunConfigurationManager).delete(2L);

        RunConfiguration resultConfiguration = runConfigurationApiService.delete(2L);

        assertThat(resultConfiguration).isEqualTo(resultConfiguration);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = CONFIGURATION_MANAGER)
    public void shouldDeleteRunConfigurationWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.WRITE.getMask())));
        doReturn(runConfiguration).when(mockRunConfigurationManager).delete(1L);

        RunConfiguration resultConfiguration = runConfigurationApiService.delete(1L);

        assertThat(resultConfiguration).isEqualTo(resultConfiguration);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyDeletionRunConfigurationWhenPermissionIsNotGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration);
        doReturn(runConfiguration).when(mockRunConfigurationManager).delete(1L);

        runConfigurationApiService.delete(1L);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadRunConfigurationForAdmin() {
        doReturn(runConfigurationOnlyForAdmin).when(mockRunConfigurationManager).load(2L);

        RunConfiguration resultConfiguration = runConfigurationApiService.load(2L);

        assertThat(resultConfiguration).isEqualTo(resultConfiguration);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadRunConfigurationWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.READ.getMask())));
        doReturn(runConfiguration).when(mockRunConfigurationManager).load(1L);

        RunConfiguration resultConfiguration = runConfigurationApiService.load(1L);

        assertThat(resultConfiguration).isEqualTo(resultConfiguration);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadingRunConfigurationWhenPermissionIsNotGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration);

        doReturn(runConfiguration).when(mockRunConfigurationManager).load(1L);

        runConfigurationApiService.load(1L);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAllRunConfigurationForAdmin() {
        doReturn(singleRunConfigurationList).when(mockRunConfigurationManager).loadAll();

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
        doReturn(singleRunConfigurationList).when(mockRunConfigurationManager).loadAll();

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
        initAclEntity(runConfigurationOnlyForAdmin);
        doReturn(twoRunConfigurationsList).when(mockRunConfigurationManager).loadAll();

        List<RunConfiguration> resultConfigurationList = runConfigurationApiService.loadAll();

        assertThat(resultConfigurationList.size()).isEqualTo(1);
        assertThat(resultConfigurationList.get(0)).isEqualTo(runConfiguration);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadingAllRunConfigurationWhichPermissionIsNotGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration);
        doReturn(singleRunConfigurationList).when(mockRunConfigurationManager).loadAll();

        List<RunConfiguration> resultConfigurationList = runConfigurationApiService.loadAll();

        assertThat(resultConfigurationList).isEmpty();
    }
}
