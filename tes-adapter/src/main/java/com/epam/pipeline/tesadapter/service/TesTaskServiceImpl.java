package com.epam.pipeline.tesadapter.service;


import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.tesadapter.common.MessageConstants;
import com.epam.pipeline.tesadapter.common.MessageHelper;
import com.epam.pipeline.tesadapter.entity.TaskView;
import com.epam.pipeline.tesadapter.entity.TesCancelTaskResponse;
import com.epam.pipeline.tesadapter.entity.TesCreateTaskResponse;
import com.epam.pipeline.tesadapter.entity.TesListTasksResponse;
import com.epam.pipeline.tesadapter.entity.TesServiceInfo;
import com.epam.pipeline.tesadapter.entity.TesTask;
import com.epam.pipeline.vo.PagingRunFilterExpressionVO;
import com.epam.pipeline.vo.PagingRunFilterVO;
import com.epam.pipeline.vo.RunStatusVO;
import com.epam.pipeline.vo.filter.FilterExpressionTypeVO;
import com.epam.pipeline.vo.filter.FilterExpressionVO;
import com.epam.pipeline.vo.filter.FilterOperandTypeVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TesTaskServiceImpl implements TesTaskService {

    private String nameOfService;
    private String doc;
    private final CloudPipelineAPIClient cloudPipelineAPIClient;
    private final TaskMapper taskMapper;
    private final MessageHelper messageHelper;
    private static final TaskView DEFAULT_TASK_VIEW = TaskView.MINIMAL;
    private static final String ID = "id";
    private static final String NAME_PREFIX = "pod.id";
    private static final Integer DEFAULT_PAGE_TOKEN = 1;
    private static final String DEFAULT_NAME_SERVICE = "CloudPipeline";
    private static final String DEFAULT_DOC = " ";
    private static final Boolean LOAD_STORAGE_LINKS = true;
    private static final Long DEFAULT_PAGE_SIZE = 256L;

    @Autowired
    public TesTaskServiceImpl(@Value("${cloud.pipeline.service.name}") String nameOfService,
                              @Value("${cloud.pipeline.doc}") String doc,
                              CloudPipelineAPIClient cloudPipelineAPIClient, TaskMapper taskMapper,
                              MessageHelper messageHelper) {
        this.nameOfService = Optional.ofNullable(nameOfService).filter(StringUtils::isNotEmpty).orElse(DEFAULT_NAME_SERVICE);
        this.doc = Optional.ofNullable(doc).filter(StringUtils::isNotEmpty).orElse(DEFAULT_DOC);
        this.cloudPipelineAPIClient = cloudPipelineAPIClient;
        this.taskMapper = taskMapper;
        this.messageHelper = messageHelper;
    }

    @Override
    public TesCreateTaskResponse submitTesTask(TesTask body) {
        TesCreateTaskResponse tesCreateTaskResponse = new TesCreateTaskResponse();
        PipelineRun pipelineRun = cloudPipelineAPIClient.runPipeline(taskMapper.mapToPipelineStart(body));
        Assert.notNull(pipelineRun.getId(), messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY,
                ID));
        log.debug(messageHelper.getMessage(MessageConstants.PIPELINE_RUN_SUBMITTED, pipelineRun.getId()));
        tesCreateTaskResponse.setId(String.valueOf(pipelineRun.getId()));
        return tesCreateTaskResponse;
    }

    @Override
    public TesListTasksResponse listTesTask(String namePrefix, Long pageSize, String pageToken, TaskView view) {
        final int pageNumber = Optional.ofNullable(pageToken).map(p -> parseStringNumber(p).intValue())
                .orElse(DEFAULT_PAGE_TOKEN);
        final List<TesTask> tesTaskList = (StringUtils.isNotEmpty(namePrefix)
                ? searchRunsWithNamePrefix(namePrefix, pageSize, pageNumber)
                : filterRunsWithOutNamePrefix(pageSize, pageNumber)
        ).stream()
                .map(pipelineRun ->
                        taskMapper.mapToTesTask(
                                pipelineRun,
                                Optional.ofNullable(view).orElse(DEFAULT_TASK_VIEW)
                        ))
                .collect(Collectors.toList());
        return TesListTasksResponse.builder()
                .tasks(tesTaskList)
                .nextPageToken(String.valueOf(pageNumber + 1))
                .build();
    }

    private List<PipelineRun> searchRunsWithNamePrefix(String namePrefix, Long pageSize, Integer pageNumber) {
        PagingRunFilterExpressionVO filterExpressionVO = new PagingRunFilterExpressionVO();
        FilterExpressionVO expression = new FilterExpressionVO();
        expression.setField(NAME_PREFIX);
        expression.setValue(namePrefix);
        expression.setOperand(FilterOperandTypeVO.EQUALS.getOperand());
        expression.setFilterExpressionType(FilterExpressionTypeVO.LOGICAL);
        filterExpressionVO.setFilterExpression(expression);
        filterExpressionVO.setPage(pageNumber);
        filterExpressionVO.setPageSize(Optional.ofNullable(pageSize).orElse(DEFAULT_PAGE_SIZE).intValue());
        log.debug(messageHelper.getMessage(MessageConstants.GET_LIST_TASKS_BY_NAME_PREFIX, namePrefix));
        return ListUtils.emptyIfNull(cloudPipelineAPIClient.searchRuns(filterExpressionVO).getElements());
    }

    private List<PipelineRun> filterRunsWithOutNamePrefix(Long pageSize, Integer pageNumber) {
        PagingRunFilterVO filterVO = new PagingRunFilterVO();
        filterVO.setPage(pageNumber);
        filterVO.setPageSize(Optional.ofNullable(pageSize).orElse(DEFAULT_PAGE_SIZE).intValue());
        log.debug(messageHelper.getMessage(MessageConstants.GET_LIST_TASKS_BY_DEFAULT_PREFIX));
        return ListUtils.emptyIfNull(cloudPipelineAPIClient.filterRuns(filterVO, LOAD_STORAGE_LINKS).getElements());
    }

    @Override
    public TesCancelTaskResponse cancelTesTask(String id) {
        RunStatusVO updateStatus = new RunStatusVO();
        updateStatus.setStatus(TaskStatus.STOPPED);
        cloudPipelineAPIClient.updateRunStatus(parseStringNumber(id), updateStatus);
        log.debug(messageHelper.getMessage(MessageConstants.CANCEL_PIPELINE_RUN_BY_ID, id));
        return new TesCancelTaskResponse();
    }

    @Override
    public TesTask getTesTask(String id, TaskView view) {
        log.debug(messageHelper.getMessage(MessageConstants.GET_PIPELINE_RUN_BY_ID, id));
        return taskMapper.mapToTesTask(cloudPipelineAPIClient.loadPipelineRun(parseStringNumber(id)), view);
    }

    private Long parseStringNumber(String number) {
        Assert.state(StringUtils.isNumeric(number),
                messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_INCOMPATIBLE_CONTENT, number));
        return Long.parseLong(number);
    }

    @Override
    public TesServiceInfo getServiceInfo() {
        log.debug(messageHelper.getMessage(MessageConstants.GET_SERVICE_INFO));
        final TesServiceInfo tesServiceInfo = new TesServiceInfo();
        tesServiceInfo.setName(nameOfService);
        tesServiceInfo.setDoc(doc);
        tesServiceInfo.setStorage(getDataStorage());
        return tesServiceInfo;
    }

    private List<String> getDataStorage() {
        return ListUtils.emptyIfNull(cloudPipelineAPIClient.loadAllDataStorages())
                .stream().map(AbstractDataStorage::getPath)
                .collect(Collectors.toList());
    }
}
