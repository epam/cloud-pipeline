package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.entity.cluster.AllowedInstanceAndPriceTypes;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.configuration.ExecutionEnvironment;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.tesadapter.common.MessageConstants;
import com.epam.pipeline.tesadapter.common.MessageHelper;
import com.epam.pipeline.tesadapter.entity.PipelineDiskMemoryTypes;
import com.epam.pipeline.tesadapter.entity.TaskView;
import com.epam.pipeline.tesadapter.entity.TesExecutor;
import com.epam.pipeline.tesadapter.entity.TesExecutorLog;
import com.epam.pipeline.tesadapter.entity.TesInput;
import com.epam.pipeline.tesadapter.entity.TesOutput;
import com.epam.pipeline.tesadapter.entity.TesResources;
import com.epam.pipeline.tesadapter.entity.TesState;
import com.epam.pipeline.tesadapter.entity.TesTask;
import com.epam.pipeline.tesadapter.entity.TesTaskLog;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class TaskMapper {

    private final Integer defaultHddSize;
    private final Double defaultRamGb;
    private final Long defaultCpuCore;
    private final Boolean defaultPreemptible;
    private final String defaultRegion;
    private MessageHelper messageHelper;
    private final CloudPipelineAPIClient cloudPipelineAPIClient;

    private static final String SEPARATOR = " ";
    private static final String INPUT_TYPE = "input";
    private static final String OUTPUT_TYPE = "output";
    private static final String DEFAULT_TYPE = "string";
    private static final String TOOL = "tool";
    private static final String INSTANCE_TYPES = "instanceList";
    private static final String MIN_INSTANCE = "instance";
    private static final String REGION_ID = "id";
    private static final String IMAGE = "image";
    private static final String EXECUTORS = "executors";
    private static final String ZONES = "zones";
    private static final Integer FIRST = 0;
    private static final String OUTPUT_LOG_STRING_FORMAT = "%s - %s - %s";
    private static final String CARRIAGE_RETURN = "\n";
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
                      @Value("${cloud.pipeline.region}") String defaultRegion,
                      CloudPipelineAPIClient cloudPipelineAPIClient, MessageHelper messageHelper) {
        this.defaultHddSize = hddSize;
        this.defaultRamGb = defaultRamGb;
        this.defaultCpuCore = defaultCpuCore;
        this.cloudPipelineAPIClient = cloudPipelineAPIClient;
        this.messageHelper = messageHelper;
        this.defaultPreemptible = defaultPreemptible;
        this.defaultRegion = defaultRegion;
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
                .map(tesResources -> {
                    if (Optional.ofNullable(tesResources.getDiskGb()).isPresent()) {
                        return tesResources.getDiskGb().intValue();
                    }
                    return defaultHddSize;
                }).orElse(defaultHddSize));
        pipelineStart.setIsSpot(Optional.ofNullable(tesTask.getResources())
                .map(tesResources -> Optional.ofNullable(tesResources.getPreemptible()).orElse(defaultPreemptible))
                .orElse(defaultPreemptible));
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

    public TesExecutor getExecutorFromTesExecutorsList(List<TesExecutor> tesExecutors) {
        Assert.isTrue(tesExecutors.size() == ONLY_ONE, messageHelper.getMessage(
                MessageConstants.ERROR_PARAMETER_INCOMPATIBLE_CONTENT, EXECUTORS, tesExecutors));
        return tesExecutors.get(FIRST);
    }


    public String getProperInstanceType(TesTask tesTask, Tool pipelineTool) {
        Double ramGb = Optional.ofNullable(tesTask.getResources())
                .map(TesResources::getRamGb).orElse(defaultRamGb);
        Long cpuCores = Optional.ofNullable(tesTask.getResources())
                .map(TesResources::getCpuCores).orElse(defaultCpuCore);
        Long toolId = pipelineTool.getId();
        Long regionId = getProperRegionIdInCloudRegionsByTesZone(Optional.ofNullable(tesTask.getResources())
                .map(TesResources::getZones)
                .orElse(Collections.singletonList(defaultRegion)));
        Boolean spot = Optional.ofNullable(tesTask.getResources())
                .map(TesResources::getPreemptible).orElse(defaultPreemptible);
        AllowedInstanceAndPriceTypes allowedInstanceAndPriceTypes = cloudPipelineAPIClient
                .loadAllowedInstanceAndPriceTypes(toolId, regionId, spot);
        Assert.notEmpty(allowedInstanceAndPriceTypes.getAllowedInstanceTypes(), messageHelper.getMessage(
                MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY, allowedInstanceAndPriceTypes));
        return evaluateMostProperInstanceType(allowedInstanceAndPriceTypes, ramGb, cpuCores);
    }

    public Tool loadToolByTesImage(String image) {
        Assert.hasText(image, messageHelper.getMessage(
                MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY, image));
        return Optional.ofNullable(cloudPipelineAPIClient.loadTool(image)).orElseThrow(() ->
                new IllegalArgumentException(messageHelper
                        .getMessage(MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY, TOOL)));
    }

    public Long getProperRegionIdInCloudRegionsByTesZone(List<String> zones) {
        Assert.isTrue(zones.size() == ONLY_ONE, messageHelper.getMessage(
                MessageConstants.ERROR_PARAMETER_INCOMPATIBLE_CONTENT, ZONES, zones));
        return Optional.ofNullable(cloudPipelineAPIClient.loadAllRegions().stream().filter(
                region -> region.getName().equalsIgnoreCase(zones.get(FIRST)))
                .collect(Collectors.toList()).get(FIRST).getId()).orElseThrow(() ->
                new IllegalArgumentException(messageHelper
                        .getMessage(MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY, REGION_ID)));
    }

    private String evaluateMostProperInstanceType(AllowedInstanceAndPriceTypes allowedInstanceAndPriceTypes,
                                                  Double ramGb, Long cpuCores) {
        return Optional.ofNullable(allowedInstanceAndPriceTypes.getAllowedInstanceTypes())
                .orElseThrow(() ->
                        new IllegalArgumentException(messageHelper
                                .getMessage(MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY, INSTANCE_TYPES))).stream()
                .min(Comparator.comparing(i -> calculateInstanceCoef(i, ramGb, cpuCores)))
                .orElseThrow(() ->
                        new IllegalArgumentException(messageHelper
                                .getMessage(MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY, MIN_INSTANCE)))
                .getName();
    }

    /**
     * Calculates the coefficient of deviation for {@code instanceType}.The result
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
        return Math.abs(convertMemoryUnitTypeToGiB(instanceType.getMemoryUnit()) * instanceType.getMemory()
                - ramGb) / ramGb + Math.abs((double) (instanceType.getVCPU() - cpuCores)) / cpuCores;
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

    public TesTask mapToTesTask(PipelineRun run, TaskView view) {
        return filterTesTaskWithView(run, view);
    }

    private TesTask filterTesTaskWithView(PipelineRun run, TaskView view) {
        final TesTask.TesTaskBuilder tesTask = TesTask.builder()
                .id(String.valueOf(run.getId()))
                .state(createTesState(run));
        if (view == TaskView.MINIMAL) {
            return tesTask.build();
        }
        tesTask.name(run.getPodId())
                .resources(createTesResources(run))
                .executors(createListExecutor(run))
                .outputs(createTesOutput(ListUtils.emptyIfNull(run.getPipelineRunParameters())))
                .creationTime(run.getStartDate().toString());
        if (view == TaskView.BASIC) {
            return tesTask.build();
        }
        tesTask.inputs(createTesInput(ListUtils.emptyIfNull(run.getPipelineRunParameters())))
                .logs(createTesTaskLog(run.getId()));
        if (view == TaskView.FULL) {
            return tesTask.build();
        } else {
            throw new IllegalArgumentException(messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_REQUIRED));
        }
    }

    private TesState createTesState(PipelineRun run) {
        List<PipelineTask> pipelineTaskList = cloudPipelineAPIClient.loadPipelineTasks(run.getId());
        switch (run.getStatus()) {
            case RUNNING:
                if (pipelineTaskList.size() == 1 &&
                        pipelineTaskList.get(0).getName().equalsIgnoreCase("Console")) {
                    return TesState.QUEUED;
                } else if (pipelineTaskList.size() > 1 && pipelineTaskList.stream()
                        .noneMatch(p -> p.getName().equalsIgnoreCase("InitializeEnvironment"))) {
                    return TesState.INITIALIZING;
                } else {
                    return TesState.RUNNING;
                }
            case PAUSED:
                return TesState.PAUSED;
            case SUCCESS:
                return TesState.COMPLETE;
            case FAILURE:
                return TesState.EXECUTOR_ERROR;
            case STOPPED:
                return TesState.CANCELED;
            default:
                return TesState.UNKNOWN;
        }
    }

    private List<TesInput> createTesInput(List<PipelineRunParameter> parameters) {
        final TesInput tesInput = new TesInput();
        parameters.stream()
                .filter(pipelineRunParameter -> pipelineRunParameter.getType().contains(INPUT_TYPE))
                .forEach(pipelineRunParameter -> {
                    tesInput.setName(pipelineRunParameter.getName());
                    tesInput.setUrl(pipelineRunParameter.getValue());
                });
        return ListUtils.emptyIfNull(Arrays.asList(tesInput));
    }

    private List<TesOutput> createTesOutput(List<PipelineRunParameter> parameters) {
        final TesOutput tesOutput = new TesOutput();
        parameters.stream()
                .filter(pipelineRunParameter -> pipelineRunParameter.getType().contains(OUTPUT_TYPE))
                .forEach(pipelineRunParameter -> {
                    tesOutput.setName(pipelineRunParameter.getName());
                    tesOutput.setUrl(pipelineRunParameter.getValue());
                });
        return ListUtils.emptyIfNull(Arrays.asList(tesOutput));
    }

    private TesResources createTesResources(PipelineRun run) {
        return TesResources.builder()
                .preemptible(run.getInstance().getSpot())
                .diskGb(new Double(run.getInstance().getNodeDisk()))
                .ramGb(getInstanceType(run).getMemory() * convertMemoryUnitTypeToGiB(getInstanceType(run).getMemoryUnit()))
                .cpuCores((long) getInstanceType(run).getVCPU())
                .build();
    }

    private List<TesExecutor> createListExecutor(PipelineRun run) {
        return ListUtils.emptyIfNull(Arrays.asList(TesExecutor.builder()
                .command(ListUtils.emptyIfNull(Arrays.asList(run.getActualCmd().split(SEPARATOR))))
                .env(run.getEnvVars())
                .build()));
    }

    private List<TesTaskLog> createTesTaskLog(final Long runId) {
        List<RunLog> runLogList = ListUtils.emptyIfNull(cloudPipelineAPIClient.getRunLog(runId));
        List<TesExecutorLog> tesExecutorLogList = Collections.singletonList(TesExecutorLog.builder()
                .stdout(runLogList.stream()
                        .map(i -> String.format(OUTPUT_LOG_STRING_FORMAT, i.getDate(), i.getTaskName(), i.getLogText()))
                        .collect(Collectors.joining(CARRIAGE_RETURN)))
                .build());
        return Collections.singletonList(TesTaskLog.builder()
                .logs(tesExecutorLogList)
                .build());
    }

    private InstanceType getInstanceType(PipelineRun run) {
        Long regionId = run.getInstance().getCloudRegionId();
        Boolean spot = run.getInstance().getSpot();
        Tool pipeLineTool = loadToolByTesImage(run.getDockerImage());
        String nodeType = run.getInstance().getNodeType();
        AllowedInstanceAndPriceTypes allowedInstanceAndPriceTypes =
                cloudPipelineAPIClient.loadAllowedInstanceAndPriceTypes(pipeLineTool.getId(), regionId, spot);
        return Stream.of(allowedInstanceAndPriceTypes)
                .flatMap(instance -> instance.getAllowedInstanceTypes().stream())
                .filter(instance -> instance
                        .getName()
                        .equalsIgnoreCase(nodeType))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(messageHelper
                        .getMessage(MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY)));
    }
}
