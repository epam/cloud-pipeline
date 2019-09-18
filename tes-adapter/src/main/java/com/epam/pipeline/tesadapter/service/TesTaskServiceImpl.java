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
    @Value("${cloud.pipeline.service.name}")
    private String nameOfService;

    @Value("${cloud.pipeline.doc}")
    private String doc;

    private final CloudPipelineAPIClient cloudPipelineAPIClient;
    private final TaskMapper taskMapper;
    private final MessageHelper messageHelper;
    private static final TaskView DEFAULT_TASK_VIEW = TaskView.MINIMAL;
    private static final String ID = "id";
    private static final String NAME_PREFIX = "pod.id";
    private static final String DEFAULT_PAGE_TOKEN = "1";
    private static final Boolean LOAD_STORAGE_LINKS = true;
    private static final Long DEFAULT_PAGE_SIZE = 256L;

    @Autowired
    public TesTaskServiceImpl(CloudPipelineAPIClient cloudPipelineAPIClient, TaskMapper taskMapper,
                              MessageHelper messageHelper) {
        this.cloudPipelineAPIClient = cloudPipelineAPIClient;
        this.taskMapper = taskMapper;
        this.messageHelper = messageHelper;
    }

    @Override
    public TesCreateTaskResponse submitTesTask(TesTask body) {
        TesCreateTaskResponse tesCreateTaskResponse = new TesCreateTaskResponse();
        PipelineRun pipelineRun = cloudPipelineAPIClient.runPipeline(taskMapper.mapToPipelineStart(body));
        Assert.notNull(pipelineRun.getId(), messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_REQUIRED,
                ID, TesCreateTaskResponse.class.getSimpleName()));
        tesCreateTaskResponse.setId(String.valueOf(pipelineRun.getId()));
        return tesCreateTaskResponse;
    }

    @Override
    public TesListTasksResponse listTesTask(String namePrefix, Long pageSize, String pageToken, TaskView view) {
        TesListTasksResponse tesListTasksResponse = new TesListTasksResponse();
        List<PipelineRun> pipelineRunList;
        if (StringUtils.isNotEmpty(namePrefix)) {
            pipelineRunList = searchRunsWithNamePrefix(namePrefix, pageSize, pageToken);
        } else {
            pipelineRunList = filterRunsWithOutNamePrefix(pageSize, pageToken);
        }
        tesListTasksResponse.setTasks(pipelineRunList.stream().map(pipelineRun ->
                taskMapper.mapToTesTask(pipelineRun, Optional.ofNullable(view).orElse(DEFAULT_TASK_VIEW)))
                .collect(Collectors.toList()));
        return tesListTasksResponse;
    }

    private List<PipelineRun> searchRunsWithNamePrefix(String namePrefix, Long pageSize, String pageToken) {
        PagingRunFilterExpressionVO filterExpressionVO = new PagingRunFilterExpressionVO();
        FilterExpressionVO expression = new FilterExpressionVO();
        expression.setField(NAME_PREFIX);
        expression.setValue(namePrefix);
        expression.setOperand(FilterOperandTypeVO.EQUALS.getOperand());
        expression.setFilterExpressionTypeVO(FilterExpressionTypeVO.LOGICAL);
        filterExpressionVO.setFilterExpressionVO(expression);
        filterExpressionVO.setPage(Integer.parseInt(Optional.ofNullable(pageToken).orElse(DEFAULT_PAGE_TOKEN)));
        filterExpressionVO.setPageSize(Optional.ofNullable(pageSize).orElse(DEFAULT_PAGE_SIZE).intValue());
        return cloudPipelineAPIClient.searchRuns(filterExpressionVO).getElements();
    }

    private List<PipelineRun> filterRunsWithOutNamePrefix(Long pageSize, String pageToken) {
        PagingRunFilterVO filterVO = new PagingRunFilterVO();
        filterVO.setPage(Integer.parseInt(Optional.ofNullable(pageToken).orElse(DEFAULT_PAGE_TOKEN)));
        filterVO.setPageSize(Optional.ofNullable(pageSize).orElse(DEFAULT_PAGE_SIZE).intValue());
        return cloudPipelineAPIClient.filterRuns(filterVO, LOAD_STORAGE_LINKS).getElements();
    }

    @Override
    public void stub() {
        //stubbed method
    }

    @Override
    public TesCancelTaskResponse cancelTesTask(String id) {
        RunStatusVO updateStatus = new RunStatusVO();
        updateStatus.setStatus(TaskStatus.STOPPED);
        cloudPipelineAPIClient.updateRunStatus(parseRunId(id), updateStatus);
        return new TesCancelTaskResponse();
    }

    @Override
    public TesTask getTesTask(String id, TaskView view) {
        return taskMapper.mapToTesTask(cloudPipelineAPIClient.loadPipelineRun(parseRunId(id)), view);
    }

    private Long parseRunId(String id) {
        Assert.state(StringUtils.isNumeric(id),
                messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_INCOMPATIBLE_CONTENT, "ID", id));
        return Long.parseLong(id);
    }

    @Override
    public TesServiceInfo getServiceInfo() {
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
