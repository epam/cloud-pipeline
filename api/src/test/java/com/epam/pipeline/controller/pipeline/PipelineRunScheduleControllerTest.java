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

import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.RUN_SCHEDULE_LIST_TYPE;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipelineRunScheduleVO;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getRunSchedule;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@WebMvcTest(controllers = PipelineRunScheduleController.class)
public class PipelineRunScheduleControllerTest extends AbstractControllerTest {

    private static final String RUN_SCHEDULE_URL = SERVLET_PATH + "/schedule/run";
    private static final String BY_ID = RUN_SCHEDULE_URL + "/%d";
    private static final String ALL_BY_ID = BY_ID + "/all";
    private final RunSchedule runSchedule = getRunSchedule();
    private final PipelineRunScheduleVO pipelineRunScheduleVO = getPipelineRunScheduleVO();
    private final List<RunSchedule> runScheduleList = Collections.singletonList(runSchedule);
    private final List<PipelineRunScheduleVO> pipelineRunScheduleVOList =
            Collections.singletonList(pipelineRunScheduleVO);

    @Autowired
    private RunScheduleApiService mockRunScheduleApiService;

    @Test
    public void shouldFailCreateRunScheduleForUnauthorizedUser() {
        performUnauthorizedRequest(post(String.format(BY_ID, ID)));
    }

    @Test
    @WithMockUser
    public void shouldCreateRunSchedule() throws Exception {
        final String content = getObjectMapper().writeValueAsString(pipelineRunScheduleVOList);
        doReturn(runScheduleList).when(mockRunScheduleApiService).createRunSchedules(ID, pipelineRunScheduleVOList);

        final MvcResult mvcResult = performRequest(post(String.format(BY_ID, ID)).content(content));

        verify(mockRunScheduleApiService).createRunSchedules(ID, pipelineRunScheduleVOList);
        assertResponse(mvcResult, runScheduleList, RUN_SCHEDULE_LIST_TYPE);
    }

    @Test
    public void shouldFailUpdateRunScheduleForUnauthorizedUser() {
        performUnauthorizedRequest(put(String.format(BY_ID, ID)));
    }

    @Test
    @WithMockUser
    public void shouldUpdateRunSchedule() throws Exception {
        final String content = getObjectMapper().writeValueAsString(pipelineRunScheduleVOList);
        doReturn(runScheduleList).when(mockRunScheduleApiService).updateRunSchedules(ID, pipelineRunScheduleVOList);

        final MvcResult mvcResult = performRequest(put(String.format(BY_ID, ID)).content(content));

        verify(mockRunScheduleApiService).updateRunSchedules(ID, pipelineRunScheduleVOList);
        assertResponse(mvcResult, runScheduleList, RUN_SCHEDULE_LIST_TYPE);
    }

    @Test
    public void shouldFailLoadAllRunSchedulesForUnauthorizedUser() {
        performUnauthorizedRequest(get(String.format(BY_ID, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadAllRunSchedules() throws Exception {
        doReturn(runScheduleList).when(mockRunScheduleApiService).loadAllRunSchedulesByRunId(ID);

        final MvcResult mvcResult = performRequest(get(String.format(BY_ID, ID)));

        verify(mockRunScheduleApiService).loadAllRunSchedulesByRunId(ID);
        assertResponse(mvcResult, runScheduleList, RUN_SCHEDULE_LIST_TYPE);
    }

    @Test
    public void shouldFailDeleteRunSchedulesForUnauthorizedUser() {
        performUnauthorizedRequest(delete(String.format(BY_ID, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDeleteRunSchedules() throws Exception {
        final String content = getObjectMapper().writeValueAsString(pipelineRunScheduleVOList);
        doReturn(runScheduleList).when(mockRunScheduleApiService).deleteRunSchedule(ID, pipelineRunScheduleVOList);

        final MvcResult mvcResult = performRequest(delete(String.format(BY_ID, ID)).content(content));

        verify(mockRunScheduleApiService).deleteRunSchedule(ID, pipelineRunScheduleVOList);
        assertResponse(mvcResult, runScheduleList, RUN_SCHEDULE_LIST_TYPE);
    }

    @Test
    public void shouldFailDeleteAllRunScheduleForUnauthorizedUser() {
        performUnauthorizedRequest(delete(String.format(ALL_BY_ID, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDeleteAllRunSchedule() throws Exception {
        performRequestWithoutResponse(delete(String.format(ALL_BY_ID, ID)));

        verify(mockRunScheduleApiService).deleteAllRunSchedules(ID);
    }
}
