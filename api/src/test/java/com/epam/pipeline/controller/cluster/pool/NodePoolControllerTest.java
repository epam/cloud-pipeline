/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.cluster.pool;

import com.epam.pipeline.acl.cluster.pool.NodePoolApiService;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.cluster.AMIConfiguration;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.test.web.AbstractControllerTest;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@WebMvcTest(controllers = NodePoolController.class)
public class NodePoolControllerTest extends AbstractControllerTest {

    private static final long ID = 1L;
    private static final String AMI_CONFIGURATION_URL = SERVLET_PATH + "/cluster/pool/%d/ami-configuration";
    private static final TypeReference<Result<NodePool>> POOL_TYPE = new TypeReference<Result<NodePool>>() {};

    @Autowired
    private NodePoolApiService mockPoolApiService;

    @Test
    public void shouldFailUpdateAmiConfigurationForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(put(String.format(AMI_CONFIGURATION_URL, ID)));
    }

    /**
     * The pool comes back with its configuration in the shape the launch script reads: {@code amiConfiguration}, with
     * the launch scripts' own snake_case names inside.
     */
    @Test
    @WithMockUser
    public void shouldUpdateAmiConfiguration() throws Exception {
        final AMIConfiguration configuration = new AMIConfiguration();
        configuration.setAmi("ami-0123456789abcdef0");
        configuration.setAvailabilityZone("us-east-1c");
        final NodePool pool = new NodePool();
        pool.setId(ID);
        pool.setAmiConfiguration(configuration);
        Mockito.doReturn(pool).when(mockPoolApiService).updateAmiConfiguration(ID, configuration);

        final MvcResult mvcResult = performRequest(put(String.format(AMI_CONFIGURATION_URL, ID))
                .content(getObjectMapper().writeValueAsString(configuration)));

        Mockito.verify(mockPoolApiService).updateAmiConfiguration(ID, configuration);
        assertResponse(mvcResult, pool, POOL_TYPE);
        assertThat(mvcResult.getResponse().getContentAsString())
                .contains("\"amiConfiguration\"")
                .contains("\"availability_zone\":\"us-east-1c\"");
    }
}
