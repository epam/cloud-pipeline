package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.entity.configuration.ExecutionEnvironment;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.tesadapter.entity.TesExecutor;
import com.epam.pipeline.tesadapter.entity.TesTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TaskMapper {
    private final String defaultInstanceType;
    private final Integer defaultHddSize;

    public TaskMapper(@Value("${cloud.pipeline.instanceType}") String instanceType,
                      @Value("${cloud.pipeline.hddSize}") Integer hddSize) {
        this.defaultInstanceType = instanceType;
        this.defaultHddSize = hddSize;
    }

    public PipelineStart mapToPipelineStart(TesTask tesTask) {
        PipelineStart pipelineStart = new PipelineStart();
        Map<String, PipeConfValueVO> params = new HashMap<>();
        TesExecutor tesExecutor = getExecutorFromTesExecutorsList(ListUtils.emptyIfNull(tesTask.getExecutors()));

        pipelineStart.setCmdTemplate(String.join(" ", tesExecutor.getCommand()));
        pipelineStart.setDockerImage(tesExecutor.getImage());
        pipelineStart.setExecutionEnvironment(ExecutionEnvironment.CLOUD_PLATFORM);
        pipelineStart.setHddSize(defaultHddSize);
        pipelineStart.setInstanceType(defaultInstanceType);
        pipelineStart.setIsSpot(tesTask.getResources().getPreemptible());
        pipelineStart.setForce(false);
        pipelineStart.setNonPause(true);

        ListUtils.emptyIfNull(tesTask.getInputs())
        .forEach(tesInput ->
                params.put(tesInput.getName(), new PipeConfValueVO(tesInput.getUrl(), "input")));

        ListUtils.emptyIfNull(tesTask.getOutputs()).forEach(tesOutput ->
                params.put(tesOutput.getName(), new PipeConfValueVO(tesOutput.getUrl(), "output")));

        MapUtils.emptyIfNull(tesExecutor.getEnv()).forEach((name, value) ->
                params.put(name, new PipeConfValueVO(value, "string")));
        pipelineStart.setParams(params);

        return pipelineStart;
    }

    private TesExecutor getExecutorFromTesExecutorsList(List<TesExecutor> tesExecutors) {
        if (CollectionUtils.isEmpty(tesExecutors)) {
            log.error("LIST OF EXECUTORS EMPTY OR NULL");
            throw new IllegalArgumentException();
        } else {
            return tesExecutors.get(0);
        }
    }
}
