package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.entity.cluster.AllowedInstanceAndPriceTypes;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.configuration.ExecutionEnvironment;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.tesadapter.common.MessageConstants;
import com.epam.pipeline.tesadapter.common.MessageHelper;
import com.epam.pipeline.tesadapter.entity.PipelineDiskMemoryTypes;
import com.epam.pipeline.tesadapter.entity.TesExecutor;
import com.epam.pipeline.tesadapter.entity.TesResources;
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
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TaskMapper {
    private final Integer defaultHddSize;
    private final Double defaultRamGb;
    private final Long defaultCpuCore;
    private final Boolean defaultPreemptible;
    private MessageHelper messageHelper;
    private final CloudPipelineAPIClient cloudPipelineAPIClient;

    private static final String SEPARATOR = " ";
    private static final String INPUT_TYPE = "input";
    private static final String OUTPUT_TYPE = "output";
    private static final String DEFAULT_TYPE = "string";
    private static final String IMAGE = "image";
    private static final String EXECUTORS = "executors";
    private static final String ZONES = "zones";
    private static final Integer FIRST = 0;
    private static final Integer ONLY_ONE = 1;
    private static final Double KIB_TO_GIB = 0.00000095367432;
    private static final Double MIB_TO_GIB = 0.0009765625;
    private static final Double GIB_TO_GIB = 1.0;
    private static final Double TIB_TO_GIB = 1024.0;
    private static final Double PIB_TO_GIB = 1048576.0;
    private static final Double EIB_TO_GIB = 1073741824.0;


    @Autowired
    public TaskMapper(@Value("${cloud.pipeline.hddSize}") Integer hddSize,
                      @Value("${cloud.pipeline.ramGb}") Double defaultRamGb,
                      @Value("${cloud.pipeline.cpuCore}") Long defaultCpuCore,
                      @Value("${cloud.pipeline.preemtible}") Boolean defaultPreemptible,
                      CloudPipelineAPIClient cloudPipelineAPIClient, MessageHelper messageHelper) {
        this.defaultHddSize = hddSize;
        this.defaultRamGb = defaultRamGb;
        this.defaultCpuCore = defaultCpuCore;
        this.cloudPipelineAPIClient = cloudPipelineAPIClient;
        this.messageHelper = messageHelper;
        this.defaultPreemptible = defaultPreemptible;
    }

    public PipelineStart mapToPipelineStart(TesTask tesTask) {
        Assert.notNull(tesTask, messageHelper.getMessage(
                MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY, tesTask));
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
        pipelineStart.setHddSize(Optional.ofNullable(tesTask.getResources())
                .map(tesResources -> tesResources.getDiskGb().intValue()).orElse(defaultHddSize));
        pipelineStart.setIsSpot(Optional.ofNullable(tesTask.getResources())
                .map(TesResources::getPreemptible).orElse(defaultPreemptible));
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
        Double ramGb =
                Optional.ofNullable(tesTask.getResources()).map(TesResources::getRamGb).orElse(defaultRamGb);
        Long cpuCores =
                Optional.ofNullable(tesTask.getResources()).map(TesResources::getCpuCores).orElse(defaultCpuCore);
        Long toolId = pipelineTool.getId();
        Long regionId = getProperRegionIdInCloudRegionsByTesZone(tesTask.getResources().getZones());
        Boolean spot = tesTask.getResources().getPreemptible();

        AllowedInstanceAndPriceTypes allowedInstanceAndPriceTypes = cloudPipelineAPIClient
                .loadAllowedInstanceAndPriceTypes(toolId, regionId, spot);
        Assert.notEmpty(allowedInstanceAndPriceTypes.getAllowedInstanceTypes(), messageHelper.getMessage(
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
                .min(Comparator.comparing(i -> calculateInstanceCoef(i, ramGb, cpuCores)))
                .orElseThrow(IllegalArgumentException::new)
                .getName();
    }

    /**
     * Calculates the effective coefficient for {@code instanceType}.The result
     * depends on the level of difference between the used values {@code memory} and
     * {@code vCPU} in {@code instanceType} and the entered values {@code ramGb} and
     * {@code cpuCores}, respectively. From greater difference, comes greater coefficient.
     *
     * @param instanceType InstanceType - a set of using parameters
     * @param ramGb        double - entered RAM (Gb) as resource parameter
     * @param cpuCores     long - entered CPU (Cores) as resource parameter
     * @return double - correspond coefficient for {@code instanceType}
     */
    private Double calculateInstanceCoef(InstanceType instanceType, Double ramGb, Long cpuCores) {
        return Math.abs((convertMemoryUnitTypeToGiB(instanceType.getMemoryUnit()) * instanceType.getMemory()
                / ramGb + (double) instanceType.getVCPU() / (double) cpuCores) / 2 - 1);
    }

    private Double convertMemoryUnitTypeToGiB(String memoryUnit) {
        if (memoryUnit != null) {
            if (memoryUnit.equalsIgnoreCase(PipelineDiskMemoryTypes.KIB.getValue())) {
                return KIB_TO_GIB;
            } else if (memoryUnit.equalsIgnoreCase(PipelineDiskMemoryTypes.MIB.getValue())) {
                return MIB_TO_GIB;
            } else if (memoryUnit.equalsIgnoreCase(PipelineDiskMemoryTypes.TIB.getValue())) {
                return TIB_TO_GIB;
            } else if (memoryUnit.equalsIgnoreCase(PipelineDiskMemoryTypes.PIB.getValue())) {
                return PIB_TO_GIB;
            } else if (memoryUnit.equalsIgnoreCase(PipelineDiskMemoryTypes.EIB.getValue())) {
                return EIB_TO_GIB;
            }
        }
        return GIB_TO_GIB;
    }
}
