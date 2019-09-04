package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.entity.cluster.AllowedInstanceAndPriceTypes;
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

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TaskMapper {
    private final Integer defaultHddSize;
    private final Long defaultToolId;
    private final Long defaultRegionId;
    private MessageHelper messageHelper;
    private final CloudPipelineAPIClient cloudPipelineAPIClient;

    private static final String SEPARATOR = " ";
    private static final String INPUT_TYPE = "input";
    private static final String OUTPUT_TYPE = "output";
    private static final String DEFAULT_TYPE = "string";
    private static final Integer FIRST = 0;

    @Autowired
    public TaskMapper(@Value("${cloud.pipeline.hddSize}") Integer hddSize,
                      @Value("${cloud.pipeline.instanceType.toolId}") Long defaultToolId,
                      @Value("${cloud.pipeline.instanceType.regionId}") Long defaultRegionId,
                      CloudPipelineAPIClient cloudPipelineAPIClient, MessageHelper messageHelper) {
        this.defaultHddSize = hddSize;
        this.defaultToolId = defaultToolId;
        this.defaultRegionId = defaultRegionId;
        this.cloudPipelineAPIClient = cloudPipelineAPIClient;
        this.messageHelper = messageHelper;

    }

    public PipelineStart mapToPipelineStart(TesTask tesTask) {
        PipelineStart pipelineStart = new PipelineStart();
        Map<String, PipeConfValueVO> params = new HashMap<>();
        TesExecutor tesExecutor = getExecutorFromTesExecutorsList(tesTask.getExecutors());

        pipelineStart.setCmdTemplate(String.join(SEPARATOR, tesExecutor.getCommand()));
        pipelineStart.setDockerImage(tesExecutor.getImage());
        pipelineStart.setExecutionEnvironment(ExecutionEnvironment.CLOUD_PLATFORM);
        pipelineStart.setHddSize(defaultHddSize);
        pipelineStart.setInstanceType(getProperInstanceType(tesTask));
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

    private String getProperInstanceType(TesTask tesTask) {
        Double ramGb = tesTask.getResources().getRamGb();
        Long cpuCores = tesTask.getResources().getCpuCores();
        //TODO Need clear mappping to loadAllowedInstanceAndPriceTypes() method
        //TODO toolId, regionId and spot
        Long toolId = 4L;
        Long regionId = Long.valueOf(String.join("", tesTask.getResources().getZones()));
        HashMap<String, Map> initialTypesMap =
                loadAllowedInstanceAndPriceTypes(toolId, regionId,
                        tesTask.getResources().getPreemptible());
        return evaluateInstanceTypeFromMapOfTypes(ramGb, cpuCores, initialTypesMap);
    }

    public HashMap<String, Map> loadAllowedInstanceAndPriceTypes(Long toolId,
                                                                 Long regionId, Boolean spot) {
        HashMap<String, Map> allowedInstanceTypesMap = new HashMap<>();
        AllowedInstanceAndPriceTypes allowedInstanceAndPriceTypes =
                cloudPipelineAPIClient.loadAllowedInstanceAndPriceTypes(toolId, regionId, spot);
        allowedInstanceAndPriceTypes.getAllowedInstanceTypes().forEach(instanceType ->
                allowedInstanceTypesMap.put(instanceType.getName(), Collections.singletonMap(instanceType.getMemory(),
                        instanceType.getVCPU())));
        return allowedInstanceTypesMap;
    }

    private String evaluateInstanceTypeFromMapOfTypes(Double ramGb, Long cpuCores,
                                                      HashMap<String, Map> initialTypesMap) {
        Map<String, Double> weightedTypesMap = new HashMap<>();
        initialTypesMap.forEach((instanceName, instanceParams) ->
                weightedTypesMap.put(instanceName, calculateInstanceWeight(instanceParams, ramGb, cpuCores)));
        return weightedTypesMap.entrySet()
                .stream().min(Comparator.comparing(Map.Entry::getValue)).get().getKey();
    }

    private Double calculateInstanceWeight(Map<Double, Long> instanceParamsMap, Double ramGb, Long cpuCores) {
        Map.Entry<Double, Long> doubleLongEntry = instanceParamsMap.entrySet().iterator().next();
        return Math.abs((doubleLongEntry.getKey() / ramGb + doubleLongEntry.getValue() / cpuCores) / 2 - 1);
    }
}
