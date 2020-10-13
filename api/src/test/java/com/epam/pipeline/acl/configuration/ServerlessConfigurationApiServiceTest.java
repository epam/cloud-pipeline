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
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.configuration.ConfigurationCreatorUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doReturn;

public class ServerlessConfigurationApiServiceTest extends AbstractAclTest {

    @Autowired
    private ServerlessConfigurationApiService serverlessConfigurationApiService;

    @Autowired
    private ServerlessConfigurationManager mockServerlessConfigurationManager;

    @Autowired
    private AuthManager mockAuthManager;

    @Autowired
    private RunConfigurationManager mockRunConfigurationManager;

    private HttpServletRequest httpServletRequest;

    private final String config = "config";

    private final RunConfiguration runConfiguration = ConfigurationCreatorUtils.getRunConfiguration();

    private final RunConfigurationVO runConfigurationVO = ConfigurationCreatorUtils.getRunConfigurationVO();

    @Before
    public void setUp() {
        runConfiguration.setId(1L);
        runConfiguration.setOwner(SIMPLE_USER);
        runConfiguration.setName(TEST_NAME);

        runConfigurationVO.setOwner(SIMPLE_USER);
        runConfigurationVO.setName(TEST_NAME_2);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnUrlForAdmin() {
        doReturn(TEST_STRING).when(mockServerlessConfigurationManager).generateUrl(ID, config);

        String result = serverlessConfigurationApiService.generateUrl(ID, config);

        assertThat(result).isEqualTo(TEST_STRING);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnUrlWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.EXECUTE.getMask())));
        doReturn(TEST_STRING).when(mockServerlessConfigurationManager).generateUrl(ID, config);

        String result = serverlessConfigurationApiService.generateUrl(ID, config);

        assertThat(result).isEqualTo(TEST_STRING);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldRunForAdmin() {
        doReturn(TEST_STRING).when(mockServerlessConfigurationManager).run(ID, config, httpServletRequest);

        String result = serverlessConfigurationApiService.run(ID, config, httpServletRequest);

        assertThat(result).isEqualTo(TEST_STRING);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldRunWhenPermissionIsGranted() {
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();
        initAclEntity(runConfiguration,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.EXECUTE.getMask())));
        doReturn(runConfiguration).when(mockRunConfigurationManager).load(anyLong());
        doReturn(TEST_STRING).when(mockServerlessConfigurationManager).run(ID, config, httpServletRequest);

        String result = serverlessConfigurationApiService.run(ID, config, httpServletRequest);

        assertThat(result).isEqualTo(TEST_STRING);
    }
}

