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

package com.epam.pipeline.controller.pipeline;

import com.epam.pipeline.acl.run.RunScheduleApiService;
import com.epam.pipeline.controller.vo.PipelineRunScheduleVO;
import com.epam.pipeline.entity.pipeline.run.RunSchedule;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.RUN_SCHEDULE_LIST_TYPE;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipelineRunScheduleVO;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getRunSchedule;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@WebMvcTest(controllers = DetachedConfigurationScheduleController.class)
public class DetachedConfigurationScheduleControllerTest extends AbstractControllerTest {

    private static final String SCHEDULE_URL = SERVLET_PATH + "/schedule/configuration";
    private static final String BY_ID_URL = SCHEDULE_URL + "/%d";
    private static final String ALL_BY_ID_URL = BY_ID_URL + "/all";
    private final RunSchedule runSchedule = getRunSchedule();
    private final PipelineRunScheduleVO pipelineRunScheduleVO = getPipelineRunScheduleVO();
    private final List<RunSchedule> runScheduleList = Collections.singletonList(runSchedule);
    private final List<PipelineRunScheduleVO> pipelineRunScheduleVOList =
            Collections.singletonList(pipelineRunScheduleVO);

    @Autowired
    private RunScheduleApiService mockRunScheduleApiService;

    @Test
    public void shouldFailCreateRunScheduleForUnauthorizedUser() {
        performUnauthorizedRequest(post(String.format(BY_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldCreateRunSchedule() throws Exception {
        final String content = getObjectMapper().writeValueAsString(pipelineRunScheduleVOList);
        doReturn(runScheduleList).when(mockRunScheduleApiService)
                .createRunConfigurationSchedules(ID, pipelineRunScheduleVOList);

        final MvcResult mvcResult = performRequest(post(String.format(BY_ID_URL, ID)).content(content));

        verify(mockRunScheduleApiService).createRunConfigurationSchedules(ID, pipelineRunScheduleVOList);
        assertResponse(mvcResult, runScheduleList, RUN_SCHEDULE_LIST_TYPE);
    }

    @Test
    public void shouldFailUpdateRunScheduleForUnauthorizedUser() {
        performUnauthorizedRequest(put(String.format(BY_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldUpdateRunSchedule() throws Exception {
        final String content = getObjectMapper().writeValueAsString(pipelineRunScheduleVOList);
        doReturn(runScheduleList).when(mockRunScheduleApiService)
                .updateRunConfigurationSchedules(ID, pipelineRunScheduleVOList);

        final MvcResult mvcResult = performRequest(put(String.format(BY_ID_URL, ID)).content(content));

        verify(mockRunScheduleApiService).updateRunConfigurationSchedules(ID, pipelineRunScheduleVOList);
        assertResponse(mvcResult, runScheduleList, RUN_SCHEDULE_LIST_TYPE);
    }

    @Test
    public void shouldFailLoadAllRunSchedulesForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(BY_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadAllRunSchedules() {
        doReturn(runScheduleList).when(mockRunScheduleApiService).loadAllRunConfigurationSchedulesByConfigurationId(ID);

        final MvcResult mvcResult = performRequest(get(String.format(BY_ID_URL, ID)));

        verify(mockRunScheduleApiService).loadAllRunConfigurationSchedulesByConfigurationId(ID);
        assertResponse(mvcResult, runScheduleList, RUN_SCHEDULE_LIST_TYPE);
    }

    @Test
    public void shouldFailDeleteRunScheduleForUnauthorizedUser() {
        performUnauthorizedRequest(delete(String.format(BY_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDeleteRunSchedule() throws Exception {
        final String content = getObjectMapper().writeValueAsString(pipelineRunScheduleVOList);
        doReturn(runScheduleList).when(mockRunScheduleApiService)
                .deleteRunConfigurationSchedule(ID, pipelineRunScheduleVOList);

        final MvcResult mvcResult = performRequest(delete(String.format(BY_ID_URL, ID)).content(content));

        verify(mockRunScheduleApiService).deleteRunConfigurationSchedule(ID, pipelineRunScheduleVOList);
        assertResponse(mvcResult, runScheduleList, RUN_SCHEDULE_LIST_TYPE);
    }

    @Test
    public void shouldFailDeleteAllRunSchedulesForUnauthorizedUser() {
        performUnauthorizedRequest(delete(String.format(ALL_BY_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDeleteAllRunSchedules() {
        performRequestWithoutResponse(delete(String.format(ALL_BY_ID_URL, ID)));

        verify(mockRunScheduleApiService).deleteAllRunConfigurationSchedules(ID);
    }
}
