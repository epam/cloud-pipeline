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
import com.epam.pipeline.manager.configuration.RunConfigurationManager;
import com.epam.pipeline.manager.configuration.ServerlessConfigurationManager;
import com.epam.pipeline.manager.pipeline.runner.ConfigurationProviderManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.configuration.ConfigurationCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import javax.servlet.http.HttpServletRequest;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doReturn;

public class ServerlessConfigurationApiServiceTest extends AbstractAclTest {

    private final HttpServletRequest request = new MockHttpServletRequest();
    private final RunConfiguration runConfiguration =
            ConfigurationCreatorUtils.getRunConfiguration(1L, SIMPLE_USER, null);
    private final RunConfigurationVO runConfigurationVO =
            ConfigurationCreatorUtils.getRunConfigurationVO(1L, SIMPLE_USER);

    @Autowired
    private ServerlessConfigurationApiService serverlessConfigurationApiService;

    @Autowired
    private ServerlessConfigurationManager mockServerlessConfigurationManager;

    @Autowired
    private AuthManager mockAuthManager;

    @Autowired
    private RunConfigurationManager mockRunConfigurationManager;

    @Autowired
    private ConfigurationProviderManager mockConfigurationProviderManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnUrlForAdmin() {
        doReturn(TEST_STRING).when(mockServerlessConfigurationManager).generateUrl(1L, TEST_STRING);

        assertThat(serverlessConfigurationApiService.generateUrl(1L, TEST_STRING)).isEqualTo(TEST_STRING);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnUrlWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        createAclEntity(runConfigurationVO.toEntity(), AclPermission.EXECUTE);
        doReturn(TEST_STRING).when(mockServerlessConfigurationManager).generateUrl(1L, TEST_STRING);

        assertThat(serverlessConfigurationApiService.generateUrl(1L, TEST_STRING)).isEqualTo(TEST_STRING);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyUrlReturningWhenPermissionIsNotGranted() {
        final RunConfiguration runConfigurationWithoutPermission =
                ConfigurationCreatorUtils.getRunConfiguration(2L, TEST_STRING, null);
        runConfigurationWithoutPermission.setId(2L);
        runConfigurationWithoutPermission.setName(TEST_NAME_2);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfigurationWithoutPermission);
        doReturn(TEST_STRING).when(mockServerlessConfigurationManager).generateUrl(2L, TEST_STRING);

        assertThrows(AccessDeniedException.class,
                () -> serverlessConfigurationApiService.generateUrl(2L, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldRunForAdmin() {
        doReturn(TEST_STRING).when(mockServerlessConfigurationManager).run(1L, TEST_STRING, request);

        assertThat(serverlessConfigurationApiService.run(1L, TEST_STRING, request)).isEqualTo(TEST_STRING);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldRunWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        createAclEntity(runConfiguration, AclPermission.EXECUTE);
        doReturn(runConfiguration).when(mockRunConfigurationManager).load(anyLong());
        doReturn(TEST_STRING).when(mockServerlessConfigurationManager).run(1L, TEST_STRING, request);

        assertThat(serverlessConfigurationApiService.run(1L, TEST_STRING, request)).isEqualTo(TEST_STRING);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyRunningWhenPermissionIsNotGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration);
        doReturn(runConfiguration).when(mockRunConfigurationManager).load(anyLong());
        doReturn(true).when(mockConfigurationProviderManager)
                .hasNoPermission(runConfigurationVO.getEntries().get(0), "EXECUTE");
        doReturn(TEST_STRING).when(mockServerlessConfigurationManager).run(1L, TEST_STRING, request);

        assertThrows(AccessDeniedException.class,
                () -> serverlessConfigurationApiService.run(1L, TEST_STRING, request));
    }
}

