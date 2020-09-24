package com.epam.pipeline.controller.log;

import com.epam.pipeline.manager.log.LogApiService;
import com.epam.pipeline.test.web.AbstractWebTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class LogControllerTest extends AbstractWebTest {

    @Autowired
    private LogApiService mockLogApiService;

    @Test
    public void shouldFailGetFilterIfUnauthorizedUser() throws Exception {
        mvc().perform(get("/restapi/log/filter").servletPath(SERVLET_PATH))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void testFilterGet() throws Exception {
        mvc().perform(get("/restapi/log/filter").servletPath(SERVLET_PATH))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andReturn();

        verify(mockLogApiService).getFilters();
    }
}
