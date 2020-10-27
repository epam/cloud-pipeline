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

package com.epam.pipeline.controller.configuration;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.manager.configuration.ServerlessConfigurationApiService;
import com.epam.pipeline.test.web.AbstractControllerTest;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.Matchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@WebMvcTest(controllers = ServerlessConfigurationController.class)
public class ServerlessConfigurationControllerTest extends AbstractControllerTest {

    private static final long ID = 1L;
    private static final String TEST_CONFIG = "testConfig";
    private static final String RESULT = "RESULT";
    private static final String SERVERLESS_URL = SERVLET_PATH + "/serverless";
    private static final String GENERATE_URL = SERVERLESS_URL + "/url/%d";
    private static final String RUN_URL = SERVERLESS_URL + "/%d/%s";
    private static final String CONFIG = "config";
    private static final TypeReference<Result<String>> STRING_TYPE = new TypeReference<Result<String>>() { };

    @Autowired
    private ServerlessConfigurationApiService mockServerlessConfigurationApiService;

    @Test
    public void shouldFailGenerateUrlForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(String.format(GENERATE_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldGenerateUrl() throws Exception {
        Mockito.doReturn(RESULT).when(mockServerlessConfigurationApiService).generateUrl(ID, TEST_CONFIG);

        final MvcResult mvcResult = performRequest(
                get(String.format(GENERATE_URL, ID)).param(CONFIG, TEST_CONFIG)
        );

        Mockito.verify(mockServerlessConfigurationApiService).generateUrl(ID, TEST_CONFIG);
        assertResponse(mvcResult, RESULT, STRING_TYPE);
    }

    @Test
    public void shouldFailRunForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(String.format(RUN_URL, ID, TEST_CONFIG)));
    }

    @Test
    @WithMockUser
    public void shouldRun() throws Exception {
        final String expectedResult = "\"" + RESULT + "\"";
        Mockito.doReturn(RESULT).when(mockServerlessConfigurationApiService)
                .run(eq(ID), eq(TEST_CONFIG), Mockito.any());

        final MvcResult mvcResult = performRequest(get(String.format(RUN_URL, ID, TEST_CONFIG)));
        performRequest(put(String.format(RUN_URL, ID, TEST_CONFIG)));
        performRequest(post(String.format(RUN_URL, ID, TEST_CONFIG)));
        performRequest(delete(String.format(RUN_URL, ID, TEST_CONFIG)));

        Mockito.verify(mockServerlessConfigurationApiService, Mockito.times(4))
                .run(eq(ID), eq(TEST_CONFIG), Mockito.any());
        final String actualResult = mvcResult.getResponse().getContentAsString();
        Assert.assertEquals(expectedResult, actualResult);
    }
}
