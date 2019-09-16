package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.rest.PagedResult;
import com.epam.pipeline.tesadapter.common.MessageHelper;
import com.epam.pipeline.tesadapter.entity.TaskView;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TesTaskServiceImplTest {
    private TesTaskServiceImpl tesTaskService;
    private static final String PAGE_TOKEN = "1";
    private static final String NAME_PREFIX = "pipeline";
    private static final Long PAGE_SIZE = 20L;
    private static final Integer NUMBER_OF_RUNS = 20;
    private static final TaskView DEFAULT_VIEW = TaskView.BASIC;

    private List<PipelineRun> pipelineRunList = new ArrayList<>();
    private PagedResult<List<PipelineRun>> pagedPipelineRunList = new PagedResult<>(pipelineRunList, NUMBER_OF_RUNS);
    private CloudPipelineAPIClient cloudPipelineAPIClient = mock(CloudPipelineAPIClient.class);

    @BeforeEach
    public void setUp() {
        when(cloudPipelineAPIClient.searchRuns(any())).thenReturn(pagedPipelineRunList);
        tesTaskService = new TesTaskServiceImpl(cloudPipelineAPIClient, mock(TaskMapper.class),
                mock(MessageHelper.class));
    }

    @Test
    void listTesTask() {
        Assertions.assertNotNull(tesTaskService.listTesTask(NAME_PREFIX, PAGE_SIZE, PAGE_TOKEN, DEFAULT_VIEW));
    }
}