package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.rest.PagedResult;
import com.epam.pipeline.tesadapter.common.MessageConstants;
import com.epam.pipeline.tesadapter.common.MessageHelper;
import com.epam.pipeline.tesadapter.configuration.AppConfiguration;
import com.epam.pipeline.tesadapter.entity.TaskView;
import com.epam.pipeline.tesadapter.entity.TesCancelTaskResponse;
import com.epam.pipeline.tesadapter.entity.TesTask;
import com.epam.pipeline.vo.PagingRunFilterExpressionVO;
import com.epam.pipeline.vo.PagingRunFilterVO;
import com.epam.pipeline.vo.RunStatusVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(value = SpringExtension.class)
@ContextConfiguration(classes = {AppConfiguration.class})
@SuppressWarnings({"unused", "PMD.TooManyStaticImports"})
class TesTaskServiceImplTest {
    private static final Integer NUMBER_OF_RUNS = 20;
    private static final TaskView DEFAULT_TASK_VIEW = TaskView.MINIMAL;
    private static final Long DEFAULT_PIPELINE_ID = 12410L;
    private static final String ID = "id";
    private static final Boolean LOAD_STORAGE_LINKS = true;

    @Value("${cloud.pipeline.service.name}")
    private String nameOfService;

    @Value("${cloud.pipeline.doc}")
    private String doc;

    @Autowired
    private MessageHelper messageHelper;

    private TesTaskServiceImpl tesTaskService;
    private TesTask tesTask = new TesTask();
    private PipelineRun pipelineRun = new PipelineRun();
    private PipelineStart pipelineStart = new PipelineStart();
    private List<PipelineRun> pipelineRunList = new ArrayList<>();
    private PagedResult<List<PipelineRun>> pagedPipelineRunList = new PagedResult<>(pipelineRunList, NUMBER_OF_RUNS);

    private TaskMapper taskMapper = mock(TaskMapper.class);
    private CloudPipelineAPIClient cloudPipelineAPIClient = mock(CloudPipelineAPIClient.class);

    @BeforeEach
    public void setUp() {
        when(taskMapper.mapToPipelineStart(tesTask)).thenReturn(pipelineStart);
        when(taskMapper.mapToTesTask(pipelineRun, DEFAULT_TASK_VIEW)).thenReturn(tesTask);
        when(cloudPipelineAPIClient.runPipeline(pipelineStart)).thenReturn(pipelineRun);
        when(cloudPipelineAPIClient.searchRuns(any(PagingRunFilterExpressionVO.class)))
                .thenReturn(pagedPipelineRunList);
        when(cloudPipelineAPIClient.filterRuns(any(PagingRunFilterVO.class), eq(LOAD_STORAGE_LINKS)))
                .thenReturn(pagedPipelineRunList);
        pipelineRun.setId(DEFAULT_PIPELINE_ID);
        tesTask.setId(DEFAULT_PIPELINE_ID.toString());
        pipelineRunList.add(pipelineRun);
        tesTaskService = new TesTaskServiceImpl(nameOfService, doc, cloudPipelineAPIClient, taskMapper, messageHelper);
    }

    @Test
    public void submitTesTaskShouldReturnTesCreateTaskResponse() {
        assertEquals(tesTaskService.submitTesTask(tesTask).getId(), pipelineRun.getId().toString());
    }

    @Test
    public void expectIllegalArgExceptionWhenRunSubmitTesTaskWithNullIdResponse() {
        pipelineRun.setId(null);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> tesTaskService.submitTesTask(tesTask));
        assertTrue(exception.getMessage().contains(messageHelper.getMessage(
                MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY, ID)));
    }

    @Test
    public void cancelTesTaskShouldStopTaskWithCorrespondId() {
        RunStatusVO updateStatus = new RunStatusVO();
        updateStatus.setStatus(TaskStatus.STOPPED);
        pipelineRun.setStatus(TaskStatus.STOPPED);
        when(cloudPipelineAPIClient.updateRunStatus(DEFAULT_PIPELINE_ID, updateStatus)).thenReturn(pipelineRun);
        assertEquals(new TesCancelTaskResponse(), tesTaskService.cancelTesTask(DEFAULT_PIPELINE_ID.toString()));
    }

    @ParameterizedTest
    @MethodSource("provideWrongIdInputForCancelTesTask")
    public void expectIllegalStateExceptionWhenRunCancelTesTaskWithWrongId(String id) {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> tesTaskService.cancelTesTask(id));
        assertEquals(exception.getMessage(), messageHelper.getMessage(
                MessageConstants.ERROR_PARAMETER_INCOMPATIBLE_CONTENT, id));
    }

    private static Stream<Arguments> provideWrongIdInputForCancelTesTask() {
        return Stream.of(
                Arguments.of("justText"),
                Arguments.of("12,44"),
                Arguments.of("1456_44"),
                Arguments.of("taskId-12410"),
                Arguments.of("14567 "),
                Arguments.of("null")
        );
    }
}