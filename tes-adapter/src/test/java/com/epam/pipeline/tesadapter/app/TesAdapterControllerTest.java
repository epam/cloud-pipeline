package com.epam.pipeline.tesadapter.app;


import com.epam.pipeline.tesadapter.common.MessageConstants;
import com.epam.pipeline.tesadapter.common.MessageHelper;
import com.epam.pipeline.tesadapter.controller.TesAdapterController;
import com.epam.pipeline.tesadapter.entity.TaskView;
import com.epam.pipeline.tesadapter.entity.TesCancelTaskResponse;
import com.epam.pipeline.tesadapter.entity.TesCreateTaskResponse;
import com.epam.pipeline.tesadapter.entity.TesExecutor;
import com.epam.pipeline.tesadapter.entity.TesListTasksResponse;
import com.epam.pipeline.tesadapter.entity.TesServiceInfo;
import com.epam.pipeline.tesadapter.entity.TesTask;
import com.epam.pipeline.tesadapter.service.CloudPipelineAPIClient;
import com.epam.pipeline.tesadapter.service.TesTaskServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(TesAdapterController.class)
@SuppressWarnings({"unused", "PMD.TooManyStaticImports", "PMD.AvoidUsingHardCodedIP"})
@TestPropertySource(locations = "classpath:test-application.properties")
public class TesAdapterControllerTest {
    private static final String DEFAULT_TASK_ID = "5";
    private static final String PAGE_TOKEN = "1";
    private static final String NAME_PREFIX = "pipeline";
    private static final String EMPTY_INPUT = " ";
    private static final String DEFAULT_COMMAND = "sleep 300";
    private static final String DEFAULT_IMAGE =
            "cp-docker-registry.default.svc.cluster.local:31443/library/centos:latest";
    private static final Long PAGE_SIZE = 20L;
    private static final TaskView DEFAULT_VIEW = TaskView.MINIMAL;
    private static final String STUBBED_SUBMIT_JSON_REQUEST = "{}";
    private static final String STUBBED_SUBMIT_JSON_RESPONSE = "{\"id\":\"5\"}";
    private static final String CANCEL_REQUEST_DESCRIPTION = "uri=/v1/tasks/%20:cancel;client=127.0.0.1";
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String HTTP_AUTH_COOKIE = "HttpAuthorization";
    private static final String WRONG_TOKEN = "wrongPipelineToken";
    private static final String GET_SERVICE_INFO = "/v1/tasks/service-info";
    private static final String IP_IN_RANGE = "192.168.1.1";
    private static final String IP_OUT_OF_RANGE = "10.0.0.0";

    @Value("${cloud.pipeline.service.name}")
    private String nameOfService;

    @Value("${cloud.pipeline.doc}")
    private String doc;

    @Value("${cloud.pipeline.token}")
    private String defaultPipelineToken;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TesTaskServiceImpl tesTaskService;

    @MockBean
    private CloudPipelineAPIClient cloudPipelineAPIClient;

    private TesCreateTaskResponse tesCreateTaskResponse = new TesCreateTaskResponse();
    private TesServiceInfo tesServiceInfo = new TesServiceInfo();
    private TesTask tesTask = new TesTask();
    private TesExecutor tesExecutor = new TesExecutor();

    @BeforeEach
    void setUp() {

        when(tesTaskService.cancelTesTask(DEFAULT_TASK_ID)).thenReturn(new TesCancelTaskResponse());
        when(tesTaskService.listTesTask(NAME_PREFIX, PAGE_SIZE, PAGE_TOKEN, DEFAULT_VIEW))
                .thenReturn(mock(TesListTasksResponse.class));
        when(tesTaskService.getServiceInfo()).thenReturn(tesServiceInfo);
        when(tesTaskService.getTesTask(DEFAULT_TASK_ID, DEFAULT_VIEW)).thenReturn(tesTask);
        tesServiceInfo.setName(nameOfService);
        tesServiceInfo.setDoc(doc);
        tesServiceInfo.setStorage(new ArrayList<>());
    }

    @Test
    void submitTesTaskWhenRequestingTesTaskBodyAndReturnId() throws Exception {
        tesCreateTaskResponse.setId(DEFAULT_TASK_ID);
        when(tesTaskService.submitTesTask(any(TesTask.class))).thenReturn(tesCreateTaskResponse);
        this.mockMvc.perform(post("/v1/tasks")
                .header(HttpHeaders.AUTHORIZATION, defaultPipelineToken)
                .contentType(JSON_CONTENT_TYPE)
                .content(STUBBED_SUBMIT_JSON_REQUEST))
                .andDo(print()).andExpect(status().isOk()).andExpect(content()
                .json(new ObjectMapper().writeValueAsString(tesCreateTaskResponse)));
    }

    @Test
    void expectIllegalArgExceptionWhenRunSubmitTesTasWithNullId() throws Exception {
        tesTask.setExecutors(null);
        when(tesTaskService.submitTesTask(tesTask)).thenThrow(new IllegalArgumentException());
        this.mockMvc.perform(post("/v1/tasks")
                .header(HttpHeaders.AUTHORIZATION, defaultPipelineToken)
                .contentType(JSON_CONTENT_TYPE)
                .content(STUBBED_SUBMIT_JSON_REQUEST))
                .andDo(print()).andExpect(status().isInternalServerError());
    }

    @Test
    void cancelTesTaskWhenRequestingIdReturnCanceledTask() throws Exception {
        when(tesTaskService.cancelTesTask(DEFAULT_TASK_ID)).thenReturn(new TesCancelTaskResponse());
        this.mockMvc.perform(post("/v1/tasks/{id}:cancel", DEFAULT_TASK_ID)
                .header(HttpHeaders.AUTHORIZATION, defaultPipelineToken))
                .andDo(print()).andExpect(status().isOk());
    }

    @Test
    void expectIllegalStateExceptionWhenRunCancelTesTaskWithWrongId() throws Exception {
        when(tesTaskService.cancelTesTask(EMPTY_INPUT)).thenThrow(new IllegalStateException(messageHelper
                .getMessage(MessageConstants.ERROR_PARAMETER_INCOMPATIBLE_CONTENT, "taskId")));
        this.mockMvc.perform(post("/v1/tasks/{id}:cancel", EMPTY_INPUT)
                .header(HttpHeaders.AUTHORIZATION, defaultPipelineToken))
                .andDo(print())
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString(messageHelper
                        .getMessage(MessageConstants.ERROR_PARAMETER_INCOMPATIBLE_CONTENT, "taskId")
                        + CANCEL_REQUEST_DESCRIPTION)));
    }

    @Test
    void listTesTaskWhenRequestingReturnTesListTasksResponse() throws Exception {
        this.mockMvc.perform(get("/v1/tasks?name_prefix={name_prefix}?page_size={page_size}" +
                        "?page_token={page_token}?view={view}",
                NAME_PREFIX, PAGE_SIZE, PAGE_TOKEN, DEFAULT_VIEW)
                .header(HttpHeaders.AUTHORIZATION, defaultPipelineToken))
                .andDo(print()).andExpect(status().isOk());
    }

    @Test
    void getTesTaskWhenRequestingReturnTesTaskResponse() throws Exception {
        tesExecutor.setImage(DEFAULT_IMAGE);
        tesExecutor.setCommand(Collections.singletonList(DEFAULT_COMMAND));
        tesTask.setExecutors(Collections.singletonList(tesExecutor));
        this.mockMvc.perform(get("/v1/tasks/{id}", DEFAULT_TASK_ID)
                .header(HttpHeaders.AUTHORIZATION, defaultPipelineToken)
                .contentType(JSON_CONTENT_TYPE))
                .andDo(print()).andExpect(status().isOk()).andExpect(content()
                .json(new ObjectMapper().writeValueAsString(tesTask)));
    }

    @Test
    void serviceInfoRequestShouldReturnCurrentServiceState() throws Exception {
        this.mockMvc.perform(get("/v1/tasks/service-info")
                .header(HttpHeaders.AUTHORIZATION, defaultPipelineToken)
                .contentType(JSON_CONTENT_TYPE))
                .andDo(print()).andExpect(status().isOk())
                .andExpect(content().json(new ObjectMapper().writeValueAsString(tesServiceInfo)));
    }

    @Test
    void preHandleMethodShouldCheckAuthorizationContext() throws Exception {
        this.mockMvc.perform(get(GET_SERVICE_INFO)
                .header(HttpHeaders.AUTHORIZATION, defaultPipelineToken).contentType(JSON_CONTENT_TYPE))
                .andDo(print()).andExpect(status().isOk())
                .andExpect(content().json(new ObjectMapper().writeValueAsString(tesServiceInfo)));

        this.mockMvc.perform(get(GET_SERVICE_INFO)
                .cookie(new Cookie(HTTP_AUTH_COOKIE, defaultPipelineToken)).contentType(JSON_CONTENT_TYPE))
                .andDo(print()).andExpect(status().isOk())
                .andExpect(content().json(new ObjectMapper().writeValueAsString(tesServiceInfo)));

        this.mockMvc.perform(get(GET_SERVICE_INFO)
                .contentType(JSON_CONTENT_TYPE))
                .andDo(print()).andExpect(status().isUnauthorized());
    }

    @Test
    void preHandleMethodShouldCheckAuthorizationContextWithIpRange() throws Exception {
        this.mockMvc.perform(get(GET_SERVICE_INFO)
                .with(request -> {
                    request.setRemoteAddr(IP_IN_RANGE);
                    return request;
                })
                .contentType(JSON_CONTENT_TYPE))
                .andDo(print()).andExpect(status().isOk());
        this.mockMvc.perform(get(GET_SERVICE_INFO)
                .with(request -> {
                    request.setRemoteAddr(IP_OUT_OF_RANGE);
                    return request;
                })
                .contentType(JSON_CONTENT_TYPE))
                .andDo(print()).andExpect(status().isUnauthorized());
    }
}
