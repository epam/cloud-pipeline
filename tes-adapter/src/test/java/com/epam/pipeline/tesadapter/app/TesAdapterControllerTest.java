package com.epam.pipeline.tesadapter.app;


import com.epam.pipeline.tesadapter.controller.TesAdapterController;
import com.epam.pipeline.tesadapter.entity.TesCancelTaskResponse;
import com.epam.pipeline.tesadapter.entity.TesListTasksResponse;
import com.epam.pipeline.tesadapter.service.CloudPipelineAPIClient;
import com.epam.pipeline.tesadapter.service.TesTaskServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@ExtendWith(SpringExtension.class)
@WebMvcTest(TesAdapterController.class)
public class TesAdapterControllerTest {
    private static final String stubbedTaskId = "5";
    private static final Long stubbedPageSize = 55L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TesTaskServiceImpl tesTaskService;

    @MockBean
    private CloudPipelineAPIClient cloudPipelineAPIClient;

    @Test
    public void cancelTesTaskWhenRequestingIdReturnCanceledTask() throws Exception {
        when(tesTaskService.cancelTesTask(stubbedTaskId)).thenReturn(new TesCancelTaskResponse());
        this.mockMvc.perform(post("/v1/tasks/{id}:cancel", stubbedTaskId)).andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    public void listTesTaskWhenRequestingReturnTesListTasksResponse() throws Exception {
        when(tesTaskService.listTesTask()).thenReturn(new TesListTasksResponse());
        this.mockMvc.perform(get("/v1/tasks?page_size={size}", stubbedPageSize)).andDo(print())
                .andExpect(status().isOk());
    }
}
