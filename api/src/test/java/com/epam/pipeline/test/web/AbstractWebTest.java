package com.epam.pipeline.test.web;

import com.epam.pipeline.config.JsonMapper;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@RunWith(SpringJUnit4ClassRunner.class)
@WebTestConfiguration
public abstract class AbstractWebTest {

    protected static final String SERVLET_PATH = "/restapi";

    @Autowired
    protected WebApplicationContext wac;

    private MockMvc mockMvc;

    @Before
    public void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .apply(springSecurity())
                .build();
    }

    protected final MockMvc mvc() {
        return mockMvc;
    }

    @Autowired
    private JsonMapper objectMapper;

    protected final JsonMapper getObjectMapper() {
        return objectMapper;
    }
}
