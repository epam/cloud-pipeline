package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.entity.configuration.ExecutionEnvironment;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.tesadapter.common.MessageConstants;
import com.epam.pipeline.tesadapter.common.MessageHelper;
import com.epam.pipeline.tesadapter.entity.TesExecutor;
import com.epam.pipeline.tesadapter.entity.TesTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TaskMapper {
    private final String defaultInstanceType;

    private final Integer defaultHddSize;

    @Autowired
    private MessageHelper messageHelper;

    private static final String SEPARATOR = " ";
    private static final String INPUT_TYPE = "input";
    private static final String OUTPUT_TYPE = "output";
    private static final String DEFAULT_TYPE = "string";
    private static final Integer FIRST = 0;

    public TaskMapper(@Value("${cloud.pipeline.instanceType}") String instanceType,
                      @Value("${cloud.pipeline.hddSize}") Integer hddSize) {
        this.defaultInstanceType = instanceType;
        this.defaultHddSize = hddSize;
    }

    public PipelineStart mapToPipelineStart(TesTask tesTask) {
        PipelineStart pipelineStart = new PipelineStart();
        Map<String, PipeConfValueVO> params = new HashMap<>();
        TesExecutor tesExecutor = getExecutorFromTesExecutorsList(tesTask.getExecutors());

        pipelineStart.setCmdTemplate(String.join(SEPARATOR, tesExecutor.getCommand()));
        pipelineStart.setDockerImage(tesExecutor.getImage());
        pipelineStart.setExecutionEnvironment(ExecutionEnvironment.CLOUD_PLATFORM);
        pipelineStart.setHddSize(defaultHddSize);
        pipelineStart.setInstanceType(defaultInstanceType);
        pipelineStart.setIsSpot(tesTask.getResources().getPreemptible());
        pipelineStart.setForce(false);
        pipelineStart.setNonPause(true);
        ListUtils.emptyIfNull(tesTask.getInputs()).forEach(tesInput ->
                params.put(tesInput.getName(), new PipeConfValueVO(tesInput.getUrl(), INPUT_TYPE)));
        ListUtils.emptyIfNull(tesTask.getOutputs()).forEach(tesOutput ->
                params.put(tesOutput.getName(), new PipeConfValueVO(tesOutput.getUrl(), OUTPUT_TYPE)));
        MapUtils.emptyIfNull(tesExecutor.getEnv()).forEach((name, value) ->
                params.put(name, new PipeConfValueVO(value, DEFAULT_TYPE)));
        pipelineStart.setParams(params);
        return pipelineStart;
    }

    private TesExecutor getExecutorFromTesExecutorsList(List<TesExecutor> tesExecutors) {
        Assert.notEmpty(tesExecutors, messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY,
                "executors"));
        return tesExecutors.get(FIRST);
    }
}
