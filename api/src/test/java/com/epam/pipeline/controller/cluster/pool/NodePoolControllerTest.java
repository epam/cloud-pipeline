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
import com.epam.pipeline.controller.vo.cluster.pool.NodePoolVO;
import com.epam.pipeline.entity.cluster.AMIConfiguration;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.test.web.AbstractControllerTest;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(controllers = NodePoolController.class)
public class NodePoolControllerTest extends AbstractControllerTest {

    private static final long ID = 1L;
    private static final String POOL_URL = SERVLET_PATH + "/cluster/pool";
    private static final String ZONE = "us-east-1c";
    private static final TypeReference<Result<NodePool>> POOL_TYPE = new TypeReference<Result<NodePool>>() {};

    @Autowired
    private NodePoolApiService mockPoolApiService;

    @Test
    @WithMockUser
    public void shouldCreateAPoolTheRequestNamesNoIdFor() throws Exception {
        final NodePool pool = new NodePool();
        pool.setId(ID);
        Mockito.doReturn(pool).when(mockPoolApiService).create(any(NodePoolVO.class));

        final MvcResult mvcResult = performRequest(post(POOL_URL)
                .content(getObjectMapper().writeValueAsString(new NodePoolVO())));

        Mockito.verify(mockPoolApiService).create(any(NodePoolVO.class));
        Mockito.verify(mockPoolApiService, Mockito.never()).update(anyLong(), any(NodePoolVO.class));
        assertResponse(mvcResult, pool, POOL_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldUpdateThePoolTheRequestNamesTheIdOf() throws Exception {
        final AMIConfiguration configuration = new AMIConfiguration();
        configuration.setAmi("ami-0123456789abcdef0");
        configuration.setAvailabilityZone(ZONE);
        final NodePoolVO vo = new NodePoolVO();
        vo.setId(ID);
        vo.setAmiConfiguration(configuration);
        final NodePool pool = new NodePool();
        pool.setId(ID);
        pool.setAmiConfiguration(configuration);
        Mockito.doReturn(pool).when(mockPoolApiService).update(Mockito.eq(ID), any(NodePoolVO.class));

        final MvcResult mvcResult = performRequest(post(POOL_URL).content(getObjectMapper().writeValueAsString(vo)));

        final ArgumentCaptor<NodePoolVO> request = ArgumentCaptor.forClass(NodePoolVO.class);
        Mockito.verify(mockPoolApiService).update(Mockito.eq(ID), request.capture());
        Mockito.verify(mockPoolApiService, Mockito.never()).create(any(NodePoolVO.class));
        assertThat(request.getValue().getAmiConfiguration()).isEqualTo(configuration);
        assertResponse(mvcResult, pool, POOL_TYPE);
        assertThat(mvcResult.getResponse().getContentAsString())
                .contains("\"amiConfiguration\"")
                .contains("\"availability_zone\":\"" + ZONE + "\"");
    }

    @Test
    public void shouldFailCreateOrUpdateForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(post(POOL_URL));
    }
}
