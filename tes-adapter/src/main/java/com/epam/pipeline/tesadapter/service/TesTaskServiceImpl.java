package com.epam.pipeline.tesadapter.service;


import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.tesadapter.common.MessageConstants;
import com.epam.pipeline.tesadapter.common.MessageHelper;
import com.epam.pipeline.tesadapter.entity.TesCancelTaskResponse;
import com.epam.pipeline.tesadapter.entity.TesCreateTaskResponse;
import com.epam.pipeline.tesadapter.entity.TesListTasksResponse;
import com.epam.pipeline.tesadapter.entity.TesServiceInfo;
import com.epam.pipeline.tesadapter.entity.TesTask;
import com.epam.pipeline.vo.RunStatusVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;
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
    private final static String ID = "id";

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
    public TesListTasksResponse listTesTask() {
        return new TesListTasksResponse();
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
    public TesTask getTesTask(String id) {
        return taskMapper.mapToTesTask(cloudPipelineAPIClient.loadPipelineRun(parseRunId(id)));
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
                .stream().map(storage -> storage.getPath())
                .collect(Collectors.toList());
    }
}
