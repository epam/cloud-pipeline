/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.test.web;

import static org.junit.Assert.assertNotNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.epam.pipeline.config.JsonMapper;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@RunWith(SpringRunner.class)
@WebTestConfiguration
public abstract class AbstractControllerTest {
    protected static final String EXPECTED_CONTENT_TYPE = "application/json;charset=UTF-8";
    protected static final String SERVLET_PATH = "/restapi";

    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    protected WebApplicationContext wac;

    @Before
    public void setup() throws Exception {
        // checks that all required dependencies are provided.
        assertNotNull("WebApplicationContext isn't provided.", wac);
        assertNotNull("ObjectMapper isn't provided.", objectMapper);

        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(springSecurity()).build();
    }

    protected final MockMvc mvc() {
        return mockMvc;
    }

    protected final JsonMapper getObjectMapper() {
        return objectMapper;
    }

}
