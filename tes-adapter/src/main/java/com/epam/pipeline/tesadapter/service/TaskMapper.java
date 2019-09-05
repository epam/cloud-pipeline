package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.entity.cluster.AllowedInstanceAndPriceTypes;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.configuration.ExecutionEnvironment;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.pipeline.Tool;
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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TaskMapper {
    private final Integer defaultHddSize;
    private MessageHelper messageHelper;
    private final CloudPipelineAPIClient cloudPipelineAPIClient;

    private static final String SEPARATOR = " ";
    private static final String INPUT_TYPE = "input";
    private static final String OUTPUT_TYPE = "output";
    private static final String DEFAULT_TYPE = "string";
    private static final String IMAGE = "image";
    private static final String EXECUTORS = "executors";
    private static final String ZONES = "zones";
    private static final String MiB = "MiB";
    private static final Integer FIRST = 0;
    private static final Integer ONLY_ONE = 1;
    private static final Double GiB_TO_GiB = 1.0;
    private static final Double MiB_TO_GiB = 0.0009765625;


    @Autowired
    public TaskMapper(@Value("${cloud.pipeline.hddSize}") Integer hddSize,
                      CloudPipelineAPIClient cloudPipelineAPIClient, MessageHelper messageHelper) {
        this.defaultHddSize = hddSize;
        this.cloudPipelineAPIClient = cloudPipelineAPIClient;
        this.messageHelper = messageHelper;
    }

    public PipelineStart mapToPipelineStart(TesTask tesTask) {
        PipelineStart pipelineStart = new PipelineStart();
        Map<String, PipeConfValueVO> params = new HashMap<>();
        TesExecutor tesExecutor = getExecutorFromTesExecutorsList(tesTask.getExecutors());
        Assert.notNull(tesExecutor.getImage(), messageHelper.getMessage(
                MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY, IMAGE));
        Tool pipelineTool = loadToolByTesImage(tesExecutor.getImage());

        pipelineStart.setInstanceType(getProperInstanceType(tesTask, pipelineTool));
        pipelineStart.setCmdTemplate(String.join(SEPARATOR, tesExecutor.getCommand()));
        pipelineStart.setDockerImage(tesExecutor.getImage());
        pipelineStart.setExecutionEnvironment(ExecutionEnvironment.CLOUD_PLATFORM);
        pipelineStart.setHddSize(defaultHddSize);
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
        Assert.isTrue(tesExecutors.size() == ONLY_ONE, messageHelper.getMessage(
                MessageConstants.ERROR_PARAMETER_INCOMPATIBLE_CONTENT, EXECUTORS, tesExecutors));
        return tesExecutors.get(FIRST);
    }

    private String getProperInstanceType(TesTask tesTask, Tool pipelineTool) {
        Double ramGb = tesTask.getResources().getRamGb();
        Long cpuCores = tesTask.getResources().getCpuCores();
        Long toolId = pipelineTool.getId();
        Long regionId = getProperRegionIdInCloudRegionsByTesZone(tesTask.getResources().getZones());
        Boolean spot = tesTask.getResources().getPreemptible();

        AllowedInstanceAndPriceTypes allowedInstanceAndPriceTypes = cloudPipelineAPIClient
                .loadAllowedInstanceAndPriceTypes(toolId, regionId, spot);
        Assert.isTrue(allowedInstanceAndPriceTypes.getAllowedInstanceTypes() != null, messageHelper.getMessage(
                MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY, allowedInstanceAndPriceTypes));
        return evaluateMostProperInstanceType(allowedInstanceAndPriceTypes, ramGb, cpuCores);
    }

    private Tool loadToolByTesImage(String image) {
        return cloudPipelineAPIClient.loadTool(image);
    }

    private Long getProperRegionIdInCloudRegionsByTesZone(List<String> zones) {
        Assert.isTrue(zones.size() == ONLY_ONE, messageHelper.getMessage(
                MessageConstants.ERROR_PARAMETER_INCOMPATIBLE_CONTENT, ZONES, zones));
        return cloudPipelineAPIClient.loadAllRegions().stream().filter(
                region -> region.getName().equalsIgnoreCase(zones.get(FIRST)))
                .collect(Collectors.toList()).get(FIRST).getId();
    }

    private String evaluateMostProperInstanceType(AllowedInstanceAndPriceTypes allowedInstanceAndPriceTypes,
                                                  Double ramGb, Long cpuCores) {
        return allowedInstanceAndPriceTypes.getAllowedInstanceTypes().stream()
                .collect(Collectors.toMap(InstanceType::getName, instancaType ->
                        calculateInstanceCoef(instancaType, ramGb, cpuCores))).entrySet().stream()
                .min(Comparator.comparing(Map.Entry::getValue)).orElseThrow(NullPointerException::new).getKey();
    }

    private Double calculateInstanceCoef(InstanceType instanceType, Double ramGb, Long cpuCores) {
        return Math.abs((instanceType.getMemory() * parseInstanceMemoryUnit(instanceType.getMemoryUnit())
                / ramGb + instanceType.getVCPU() / cpuCores) / 2 - 1);
    }

    private Double parseInstanceMemoryUnit(String memoryUnit) {
        if (memoryUnit != null && memoryUnit.equalsIgnoreCase(MiB)) {
            return MiB_TO_GiB;
        }
        return GiB_TO_GiB;
    }
}
