package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.entity.cluster.AllowedInstanceAndPriceTypes;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.tesadapter.common.MessageConstants;
import com.epam.pipeline.tesadapter.common.MessageHelper;
import com.epam.pipeline.tesadapter.configuration.AppConfiguration;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@ExtendWith(value = SpringExtension.class)
@ContextConfiguration(classes = {AppConfiguration.class})
@SuppressWarnings("unused")
class TaskMapperTest {
    private static final Integer DEFAULT_HDD_SIZE = 50;
    private static final Double DEFAULT_RAM_GB = 8.0;
    private static final Long DEFAULT_CPU_CORES = 2L;
    private static final Boolean DEFAULT_PREEMPTIBLE = true;
    private static final String DEFAULT_REGION_NAME = "eu-central-1";
    private static final String EXECUTORS = "executors";
    private static final Long STUBBED_REGION_ID = 1L;
    private static final Long STUBBED_TOOL_ID = 11584L;
    private static final Long TOOL_ID = 1l;
    private static final String STUBBED_IMAGE = "cp-docker-registry.default.svc.cluster.local:31443/library/centos:latest";
    private static final String INPUT = "input";
    private static final String OUTPUT = "output";
    private static final Double KIB_TO_GIB = 0.00000095367432;
    private static final String STUBBED_NAME_PIPELINE_PARAMETER = "pipelineRunParameter";
    private static final String STUBBED_VALUE_PIPELINE_PARAMETER = "www.url.ru";
    private static final String OUTPUT_LOG_STRING_FORMAT = "%s - %s - %s";
    private static final String TYPE_NODE = "nodeType";
    private static final String STUBBED_TEXT_FOR_LOG = "log";
    private static final String PIPELINE_TASK_NAME_CONSOLE = "Console";
    private static final String PIPELINE_TASK_NAME_InitializeEnvironment =  "InitializeEnvironment";
    private static final String ANY_STRING = "any_string";
    private static List<InstanceType> allowedInstanceTypes;

    @Autowired
    private MessageHelper messageHelper;

    private static TaskMapper taskMapper;
    private static PipelineRun run = getPipelineRun();
    private List<String> zones = new ArrayList<>();
    private List<AbstractCloudRegion> abstractCloudRegions = new ArrayList<>();

    private AbstractCloudRegion abstractCloudRegion = mock(AbstractCloudRegion.class);
    private static CloudPipelineAPIClient cloudPipelineAPIClient = mock(CloudPipelineAPIClient.class);
    private AllowedInstanceAndPriceTypes allowedInstanceAndPriceTypes = mock(AllowedInstanceAndPriceTypes.class);
    private Tool tool = mock(Tool.class);
    private TesTask tesTask = mock(TesTask.class);
    private PipelineRun pipelineRun = mock(PipelineRun.class);


    @BeforeAll
    public static void setUpAll() {
        allowedInstanceTypes = new ArrayList<>();
        fillWithAllowedInstanceTypes(allowedInstanceTypes);

        AwsRegion awsRegion = new AwsRegion();
        awsRegion.setRegionCode(DEFAULT_REGION_NAME);
        when(cloudPipelineAPIClient.loadRegion(anyLong())).thenReturn(awsRegion);
        when(cloudPipelineAPIClient.loadAllowedInstanceAndPriceTypes(TOOL_ID, 1l,
                true)).thenReturn(getAllowedInstanceAndPriceTypes(run));
        Tool tool = new Tool();
        tool.setId(TOOL_ID);
        tool.setName(STUBBED_IMAGE);
        when(cloudPipelineAPIClient.loadTool(anyString())).thenReturn(tool);
    }

    @BeforeEach
    public void setUp() {
        when(abstractCloudRegion.getName()).thenReturn(DEFAULT_REGION_NAME);
        when(abstractCloudRegion.getId()).thenReturn(STUBBED_REGION_ID);
        when(cloudPipelineAPIClient.loadAllRegions()).thenReturn(abstractCloudRegions);
        when(cloudPipelineAPIClient.loadTool(STUBBED_IMAGE)).thenReturn(tool);
        when(cloudPipelineAPIClient.loadAllowedInstanceAndPriceTypes(STUBBED_TOOL_ID,
                STUBBED_REGION_ID, DEFAULT_PREEMPTIBLE)).thenReturn(allowedInstanceAndPriceTypes);
        when(tool.getId()).thenReturn(STUBBED_TOOL_ID);
        when(tesTask.getResources()).thenReturn(mock(TesResources.class));
        when(tesTask.getResources().getPreemptible()).thenReturn(true);
        zones.add(DEFAULT_REGION_NAME);
        abstractCloudRegions.add(abstractCloudRegion);
        this.taskMapper = new TaskMapper(DEFAULT_HDD_SIZE, DEFAULT_RAM_GB, DEFAULT_CPU_CORES,
                DEFAULT_PREEMPTIBLE, DEFAULT_REGION_NAME, cloudPipelineAPIClient, this.messageHelper);
    }

    @Test()
    public void expectIllegalArgExceptionWhenRunGetExecutorFromTesExecutorsList() {
        List<TesExecutor> tesExecutors = new ArrayList<>();
        tesExecutors.add(new TesExecutor());
        tesExecutors.add(new TesExecutor());
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> taskMapper.getExecutorFromTesExecutorsList(tesExecutors));
        assertTrue(exception.getMessage().contains(messageHelper.getMessage(
                MessageConstants.ERROR_PARAMETER_INCOMPATIBLE_CONTENT, EXECUTORS, tesExecutors)));
    }

    @ParameterizedTest
    @MethodSource("provideInputForGetProperInstanceTypeTest")
    public void getProperInstanceTypeShouldReturnProperInstanceName(String instanceTypeName, Double ramGb, Long cpuCore) {
        when(tesTask.getResources().getRamGb()).thenReturn(ramGb);
        when(tesTask.getResources().getCpuCores()).thenReturn(cpuCore);
        when(tesTask.getResources().getZones()).thenReturn(zones);
        when(allowedInstanceAndPriceTypes.getAllowedInstanceTypes()).thenReturn(allowedInstanceTypes);
        assertEquals(instanceTypeName, taskMapper.getProperInstanceType(tesTask, tool));
    }

    @Test
    public void expectIllegalArgExceptionWhenRunGetProperRegionIdInCloudRegionsByTesZone() {
        zones.add("Second zone");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> taskMapper.getProperRegionIdInCloudRegionsByTesZone(zones));
        assertTrue(exception.getMessage().contains(messageHelper.getMessage(
                MessageConstants.ERROR_PARAMETER_INCOMPATIBLE_CONTENT, EXECUTORS, zones)));
    }

    @Test
    public void getProperRegionIdInCloudRegionsByTesZoneShouldReturnRegionId() {
        assertEquals(STUBBED_REGION_ID, taskMapper.getProperRegionIdInCloudRegionsByTesZone(zones));
    }

    @Test
    public void expectIllegalArgExceptionWhenRunLoadToolByTesImageWithEmptyOrNullImage() {
        IllegalArgumentException exception1 = assertThrows(IllegalArgumentException.class,
                () -> taskMapper.loadToolByTesImage(""));
        assertTrue(exception1.getMessage().contains(messageHelper.getMessage(
                MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY, "")));

        IllegalArgumentException exception2 = assertThrows(IllegalArgumentException.class,
                () -> taskMapper.loadToolByTesImage(null));
        assertTrue(exception2.getMessage().contains(messageHelper.getMessage(
                MessageConstants.ERROR_PARAMETER_NULL_OR_EMPTY, "null")));
    }

    @Test
    public void loadToolByTesImageShouldReturnToolByRequestingImage() {
        assertEquals(tool, taskMapper.loadToolByTesImage(STUBBED_IMAGE));
    }

    private static Stream<Arguments> provideInputForGetProperInstanceTypeTest() {
        return Stream.of(
                //Perfect cases with 0.0 deviation
                Arguments.of("c5.12xlarge", 96.0, 48L),
                Arguments.of("c5.18xlarge", 144.0, 72L),
                Arguments.of("c5.24xlarge", 192.0, 96L),
                Arguments.of("c5.2xlarge", 16.0, 8L),
                Arguments.of("c5.4xlarge", 32.0, 16L),
                Arguments.of("c5.9xlarge", 72.0, 36L),
                Arguments.of("c5.large", 4.0, 2L),
//                Arguments.of("c5.metal", 192.0, 96L), duplicate with "c5.24xlarge", 192.0, 96L
                Arguments.of("c5.xlarge", 8.0, 4L),
                Arguments.of("m5.12xlarge", 192.0, 48L),
                Arguments.of("m5.16xlarge", 256.0, 64L),
                Arguments.of("m5.24xlarge", 384.0, 96L),
                Arguments.of("m5.2xlarge", 32.0, 8L),
                Arguments.of("m5.4xlarge", 64.0, 16L),
                Arguments.of("m5.8xlarge", 128.0, 32L),
                Arguments.of("m5.large", 8.0, 2L),
//                Arguments.of("m5.metal", 384.0, 96L), duplicate with "m5.24xlarge", 384.0, 96L
                Arguments.of("m5.xlarge", 16.0, 4L),
                Arguments.of("p2.16xlarge", 768.0, 64L),
                Arguments.of("p2.8xlarge", 488.0, 32L),
                Arguments.of("p2.xlarge", 61.0, 4L),
                Arguments.of("r5.12xlarge", 384.0, 48L),
                Arguments.of("r5.16xlarge", 512.0, 64L),
                Arguments.of("r5.24xlarge", 768.0, 96L),
                Arguments.of("r5.2xlarge", 64.0, 8L),
                Arguments.of("r5.4xlarge", 128.0, 16L),
                Arguments.of("r5.8xlarge", 256.0, 32L),
                Arguments.of("r5.large", 16.0, 2L),
                Arguments.of("r5.xlarge", 32.0, 4L),

                //Different cases with non 0.0 deviation
                Arguments.of("m5.24xlarge", 384.0, 110L), //"m5.24xlarge", 384.0, 96L has closest coef.
                Arguments.of("r5.2xlarge", 150.0, 8L), //"r5.2xlarge", 64.0, 8L has closest coef.
                Arguments.of("r5.4xlarge", 128.0, 10L), //"r5.4xlarge", 128.0, 16L has closest coef.
                Arguments.of("p2.16xlarge", 650.0, 64L), //"p2.16xlarge", 768.0, 64L has closest coef.
                Arguments.of("p2.8xlarge", 650.0, 32L), //"p2.8xlarge", 488.0, 32L has closest coef.

                //Check that the default values will be used if the request does not have them, respectively
                Arguments.of("r5.large", 650.0, null), //defaultCpuCore = 2.0, closest should be "r5.large", 16.0, 2L
                Arguments.of("c5.xlarge", null, 32L), //defaultRamGb = 8.0, closest should be "c5.xlarge", 8.0, 4L
                Arguments.of("m5.large", null, null) //defaultRamGb = 8.0 and defaultCpuCore = 2 => "m5.large", 8.0, 2L
        );
    }

    private static void fillWithAllowedInstanceTypes(List<InstanceType> instanceTypeList) {
        instanceTypeList.add(getInstanceWithNameRamAndCpu("c5.12xlarge", 96.0, 48L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("c5.18xlarge", 144.0, 72L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("c5.24xlarge", 192.0, 96L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("c5.2xlarge", 16.0, 8L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("c5.4xlarge", 32.0, 16L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("c5.9xlarge", 72.0, 36L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("c5.large", 4.0, 2L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("c5.metal", 192.0, 96L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("c5.xlarge", 8.0, 4L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("m5.12xlarge", 192.0, 48L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("m5.16xlarge", 256.0, 64L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("m5.24xlarge", 384.0, 96L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("m5.2xlarge", 32.0, 8L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("m5.4xlarge", 64.0, 16L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("m5.8xlarge", 128.0, 32L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("m5.large", 8.0, 2L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("m5.metal", 384.0, 96L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("m5.xlarge", 16.0, 4L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("p2.16xlarge", 768.0, 64L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("p2.8xlarge", 488.0, 32L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("p2.xlarge", 61.0, 4L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("r5.12xlarge", 384.0, 48L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("r5.16xlarge", 512.0, 64L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("r5.24xlarge", 768.0, 96L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("r5.2xlarge", 64.0, 8L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("r5.4xlarge", 128.0, 16L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("r5.8xlarge", 256.0, 32L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("r5.large", 16.0, 2L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("r5.xlarge", 32.0, 4L));
    }

    private static InstanceType getInstanceWithNameRamAndCpu(String instanceName, Double instanceRam, Long instanceCpu) {
        InstanceType instanceType = new InstanceType();
        instanceType.setName(instanceName);
        instanceType.setMemory(instanceRam.floatValue());
        instanceType.setVCPU(instanceCpu.intValue());
        return instanceType;
    }

    @ParameterizedTest
    @MethodSource("provideTesTaskForTestTaskMapper")
    public void testTaskMapperMapPipelineRunToTesTask(PipelineRun run, TaskView view, TesTask tesTaskExpected) {
        TesTask tesTaskActual = taskMapper.mapToTesTask(run, view);
        assertEquals(tesTaskExpected, tesTaskActual);
    }

    private static Stream<Arguments> provideTesTaskForTestTaskMapper() {
        //data for TaskView.Minimal
        TesTask tesTaskForMinimal = TesTask.builder()
                .id(String.valueOf(run.getId()))
                .state(TesState.RUNNING)
                .build();
        //data for TaskView.basic
        TesTask tesTaskForBasic = TesTask.builder()
                .id(String.valueOf(run.getId()))
                .state(TesState.RUNNING)
                .resources(createResources(run))
                .executors(createListExecutor(run))
                .outputs(getListPipelineRunParameter().stream()
                        .filter(pipelineRunParameter -> pipelineRunParameter.getType().contains(OUTPUT))
                        .map(pipelineRunParameter ->
                                TesOutput.builder()
                                        .name(pipelineRunParameter.getName())
                                        .url(pipelineRunParameter.getValue())
                                        .build()).collect(Collectors.toList()))
                .creationTime(run.getStartDate().toString())
                .build();
        //data for TaskView.FULL
        TesTask tesTaskForFull = TesTask.builder()
                .id(String.valueOf(run.getId()))
                .state(TesState.RUNNING)
                .resources(createResources(run))
                .executors(createListExecutor(run))
                .outputs(getListPipelineRunParameter().stream()
                        .filter(pipelineRunParameter -> pipelineRunParameter.getType().contains(OUTPUT))
                        .map(pipelineRunParameter ->
                                TesOutput.builder()
                                        .name(pipelineRunParameter.getName())
                                        .url(pipelineRunParameter.getValue())
                                        .build()).collect(Collectors.toList()))
                .creationTime(run.getStartDate().toString())
                .inputs(getListPipelineRunParameter().stream()
                        .filter(pipelineRunParameter -> pipelineRunParameter.getType().contains(INPUT))
                        .map(pipelineRunParameter ->
                                TesInput.builder()
                                        .name(pipelineRunParameter.getName())
                                        .url(pipelineRunParameter.getValue())
                                        .build()).collect(Collectors.toList()))
                .logs(createTesTaskLog(run.getId()))
                .build();
        return Stream.of(
                Arguments.of(run, TaskView.MINIMAL, tesTaskForMinimal),
                Arguments.of(run, TaskView.BASIC, tesTaskForBasic),
                Arguments.of(run, TaskView.FULL, tesTaskForFull)
        );
    }

    private static PipelineRun getPipelineRun() {
        PipelineRun pipelineRun = new PipelineRun();
        pipelineRun.setId(1L);
        pipelineRun.setStatus(TaskStatus.RUNNING);
        pipelineRun.setPodIP("pipelineRun");
        pipelineRun.setPipelineRunParameters(getListPipelineRunParameter());
        pipelineRun.setStartDate(new Date(2000));
        pipelineRun.setInstance(getRunInstance());
        pipelineRun.setActualCmd(ANY_STRING);
        HashMap<String, String> env = new HashMap<>();
        env.put(ANY_STRING, ANY_STRING);
        pipelineRun.setEnvVars(env);
        pipelineRun.setDockerImage("centos");
        return pipelineRun;
    }

    private static List<PipelineRunParameter> getListPipelineRunParameter() {
        PipelineRunParameter pipelineRunParameterInput = new PipelineRunParameter();
        pipelineRunParameterInput.setType(INPUT);
        pipelineRunParameterInput.setName(STUBBED_NAME_PIPELINE_PARAMETER);
        pipelineRunParameterInput.setValue(STUBBED_VALUE_PIPELINE_PARAMETER);

        PipelineRunParameter pipelineRunParameterOuput = new PipelineRunParameter();
        pipelineRunParameterOuput.setType(OUTPUT);
        pipelineRunParameterOuput.setName(STUBBED_NAME_PIPELINE_PARAMETER);
        pipelineRunParameterOuput.setValue(STUBBED_VALUE_PIPELINE_PARAMETER);

        List<PipelineRunParameter> pipelineRunParameterList = new ArrayList<>();
        pipelineRunParameterList.add(pipelineRunParameterInput);
        pipelineRunParameterList.add(pipelineRunParameterOuput);
        return pipelineRunParameterList;
    }

    private static TesResources createResources(PipelineRun run) {
        InstanceType instanceType = getInstanceType(run);
        RunInstance runInstance = getRunInstance();
        return TesResources.builder()
                .preemptible(runInstance.getSpot())
                .diskGb(new Double(runInstance.getNodeDisk()))
                .ramGb(instanceType.getMemory() * KIB_TO_GIB)
                .cpuCores((long) instanceType.getVCPU())
                .zones(Collections.singletonList(DEFAULT_REGION_NAME))
                .build();
    }

    private static List<TesExecutor> createListExecutor(PipelineRun run) {
        return Collections.singletonList(TesExecutor.builder()
                .command(Collections.singletonList(run.getActualCmd()))
                .env(run.getEnvVars())
                .image(run.getDockerImage())
                .build());
    }

    private static List<TesTaskLog> createTesTaskLog(final Long runId) {
        RunLog runLog = RunLog.builder()
                .date(new Date(12, 12, 12))
                .taskName(STUBBED_TEXT_FOR_LOG)
                .logText(STUBBED_TEXT_FOR_LOG)
                .build();
        when(cloudPipelineAPIClient.getRunLog(anyLong())).thenReturn(Collections.singletonList(runLog));
        List<TesExecutorLog> tesExecutorLogList = Collections.singletonList(TesExecutorLog.builder()
                .stdout(String.format(OUTPUT_LOG_STRING_FORMAT, runLog.getDate(), runLog.getTaskName(), runLog.getLogText()))
                .build());
        List<TesTaskLog> tesTaskLogsList = Collections.singletonList(TesTaskLog.builder()
                .logs(tesExecutorLogList)
                .build());
        return tesTaskLogsList;
    }

    private static RunInstance getRunInstance() {
        RunInstance runInstance = new RunInstance();
        runInstance.setNodeType(TYPE_NODE);
        runInstance.setSpot(DEFAULT_PREEMPTIBLE);
        runInstance.setNodeDisk(DEFAULT_HDD_SIZE);
        runInstance.setCloudRegionId(STUBBED_REGION_ID);
        return runInstance;
    }

    private static InstanceType getInstanceType(PipelineRun run) {
        return InstanceType.builder()
                .name(run.getInstance().getNodeType())
                .memory(DEFAULT_RAM_GB.floatValue())
                .memoryUnit(PipelineDiskMemoryTypes.KIB.getValue())
                .vCPU(DEFAULT_CPU_CORES.intValue())
                .build();
    }

    private static AllowedInstanceAndPriceTypes getAllowedInstanceAndPriceTypes(PipelineRun run) {
        InstanceType instanceType = getInstanceType(run);
        return new AllowedInstanceAndPriceTypes(Collections.singletonList(instanceType),
                Collections.singletonList(instanceType), Collections.singletonList(ANY_STRING));
    }

    @Test
    public void testTesStateForPipelineTaskWithOneTask() {
        ArrayList<PipelineTask> listPipelineTasks = new ArrayList<>();
        PipelineTask pipelineTask = new PipelineTask();
        pipelineTask.setName(PIPELINE_TASK_NAME_CONSOLE);
        listPipelineTasks.add(pipelineTask);

        when(cloudPipelineAPIClient.loadPipelineTasks(run.getId())).thenReturn(listPipelineTasks);
        assertEquals(TesState.QUEUED, taskMapper.mapToTesTask(run, TaskView.MINIMAL).getState());
    }

    @Test
    public void testTesStateForPipelineTaskWithTwoTasksButWithoutInitializeEnvironment() {
        ArrayList<PipelineTask> listPipelineTasks = new ArrayList<>();
        PipelineTask pipelineTaskConsole = new PipelineTask();
        pipelineTaskConsole.setName(PIPELINE_TASK_NAME_CONSOLE);
        PipelineTask pipelineTaskAny = new PipelineTask();
        pipelineTaskAny.setName(ANY_STRING);
        listPipelineTasks.add(pipelineTaskConsole);
        listPipelineTasks.add(pipelineTaskAny);

        when(cloudPipelineAPIClient.loadPipelineTasks(run.getId())).thenReturn(listPipelineTasks);
        assertEquals(TesState.INITIALIZING, taskMapper.mapToTesTask(run, TaskView.MINIMAL).getState());
    }

    @Test
    public void testTesStateForPipelineTaskWithTwoTasksWithInitializeEnvironment() {
        ArrayList<PipelineTask> listPipelineTasks = new ArrayList<>();
        PipelineTask pipelineTaskConsole = new PipelineTask();
        pipelineTaskConsole.setName(PIPELINE_TASK_NAME_CONSOLE);
        PipelineTask pipelineTaskInitializeEnvironment = new PipelineTask();
        pipelineTaskInitializeEnvironment.setName(PIPELINE_TASK_NAME_InitializeEnvironment);
        listPipelineTasks.add(pipelineTaskConsole);
        listPipelineTasks.add(pipelineTaskInitializeEnvironment);

        when(cloudPipelineAPIClient.loadPipelineTasks(run.getId())).thenReturn(listPipelineTasks);
        assertEquals(TesState.RUNNING, taskMapper.mapToTesTask(run, TaskView.MINIMAL).getState());
    }
}