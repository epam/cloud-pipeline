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
import com.epam.pipeline.tesadapter.common.MessageConstants;
import com.epam.pipeline.tesadapter.common.MessageHelper;
import com.epam.pipeline.tesadapter.configuration.AppConfiguration;
import com.epam.pipeline.tesadapter.entity.TesExecutor;
import com.epam.pipeline.tesadapter.entity.TesExecutorLog;
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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
    private static final String STUBBED_IMAGE = "cp-docker-registry.default.svc.cluster.local:31443/library/centos:latest";
    private static List<InstanceType> allowedInstanceTypes;

    @Autowired
    private MessageHelper messageHelper;

    private TaskMapper taskMapper;
    private List<String> zones = new ArrayList<>();
    private List<AbstractCloudRegion> abstractCloudRegions = new ArrayList<>();

    private AbstractCloudRegion abstractCloudRegion = mock(AbstractCloudRegion.class);
    private CloudPipelineAPIClient cloudPipelineAPIClient = mock(CloudPipelineAPIClient.class);
    private AllowedInstanceAndPriceTypes allowedInstanceAndPriceTypes = mock(AllowedInstanceAndPriceTypes.class);
    private Tool tool = mock(Tool.class);
    private TesTask tesTask = mock(TesTask.class);
    private PipelineRun pipelineRun = mock(PipelineRun.class);

    @BeforeAll
    public static void setUpAll() {
        allowedInstanceTypes = new ArrayList<>();
        fillWithAllowedInstanceTypes(allowedInstanceTypes);
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

    @Test
    public void testCreateTesState() {
        when(pipelineRun.getStatus()).thenReturn(TaskStatus.RUNNING);
        assertEquals(TesState.RUNNING, taskMapper.createTesState(pipelineRun));

        when(pipelineRun.getStatus()).thenReturn(TaskStatus.STOPPED);
        assertEquals(TesState.CANCELED, taskMapper.createTesState(pipelineRun));

        when(pipelineRun.getStatus()).thenReturn(TaskStatus.PAUSED);
        assertEquals(TesState.PAUSED, taskMapper.createTesState(pipelineRun));

        List<PipelineTask> pipelineTaskListWithConsole = getPipelineTaskListWithConsole();
        when(cloudPipelineAPIClient.loadPipelineTasks(anyLong())).thenReturn(pipelineTaskListWithConsole);
        assertEquals(TesState.QUEUED, taskMapper.createTesState(getPipelineRun()));

        List<PipelineTask> pipelineTaskListWithEnv = getPipelineTaskListWithEnv();
        when(cloudPipelineAPIClient.loadPipelineTasks(anyLong())).thenReturn(pipelineTaskListWithEnv);
        assertEquals(TesState.RUNNING, taskMapper.createTesState(getPipelineRun()));

        List<PipelineTask> pipelineTaskListWithoutEnv = getPipelineTaskListWithoutEnv();
        when(cloudPipelineAPIClient.loadPipelineTasks(anyLong())).thenReturn(pipelineTaskListWithoutEnv);
        assertEquals(TesState.INITIALIZING, taskMapper.createTesState(getPipelineRun()));

        when(pipelineRun.getStatus()).thenReturn(TaskStatus.RESUMING);
        assertEquals(TesState.UNKNOWN, taskMapper.createTesState(pipelineRun));

        when(pipelineRun.getStatus()).thenReturn(TaskStatus.SUCCESS);
        assertEquals(TesState.COMPLETE, taskMapper.createTesState(pipelineRun));

        when(pipelineRun.getStatus()).thenReturn(TaskStatus.FAILURE);
        assertEquals(TesState.EXECUTOR_ERROR, taskMapper.createTesState(pipelineRun));

        assertNotNull(taskMapper.createTesState(pipelineRun));
    }

    private List<PipelineTask> getPipelineTaskListWithConsole(){
        ArrayList<PipelineTask> pipelineTasksList = new ArrayList<>();
        PipelineTask pipelineTaskConsole = new PipelineTask();
        pipelineTaskConsole.setName("Console");
        pipelineTasksList.add(pipelineTaskConsole);
        return pipelineTasksList;
    }

    private List<PipelineTask> getPipelineTaskListWithEnv(){
        ArrayList<PipelineTask> pipelineTasksList = new ArrayList<>();
        PipelineTask pipelineTaskInitializeEnvironment = new PipelineTask();
        pipelineTaskInitializeEnvironment.setName("InitializeEnvironment");
        pipelineTasksList.add(pipelineTaskInitializeEnvironment);
        return pipelineTasksList;
    }

    private List<PipelineTask> getPipelineTaskListWithoutEnv(){
        List<PipelineTask> pipelineTasksList = getPipelineTaskListWithConsole();
        PipelineTask pipelineTask = new PipelineTask();
        pipelineTask.setName("someState");
        pipelineTasksList.add(pipelineTask);
        return pipelineTasksList;
    }

    @Test
    public void testCreateTesInput() {
        assertNotNull(taskMapper.createTesInput(pipelineRun.getPipelineRunParameters()));
        assertEquals("pipelineRunParameter", taskMapper.createTesInput(getListPipelineRunParameter()).get(0).getName());
        assertEquals("www.url.ru", taskMapper.createTesInput(getListPipelineRunParameter()).get(0).getUrl());

    }

    private List<PipelineRunParameter> getListPipelineRunParameter() {
        PipelineRunParameter pipelineRunParameterInput = new PipelineRunParameter();
        pipelineRunParameterInput.setType("input");
        pipelineRunParameterInput.setName("pipelineRunParameter");
        pipelineRunParameterInput.setValue("www.url.ru");

        PipelineRunParameter pipelineRunParameterOuput = new PipelineRunParameter();
        pipelineRunParameterOuput.setType("output");
        pipelineRunParameterOuput.setName("pipelineRunParameter");
        pipelineRunParameterOuput.setValue("www.url.ru");

        List<PipelineRunParameter> pipelineRunParameterList = new ArrayList<>();
        pipelineRunParameterList.add(pipelineRunParameterInput);
        pipelineRunParameterList.add(pipelineRunParameterOuput);
        return pipelineRunParameterList;
    }

    @Test
    public void testCreateTesOutput() {
        assertNotNull(taskMapper.createTesOutput(pipelineRun.getPipelineRunParameters()));
        assertEquals("pipelineRunParameter", taskMapper.createTesOutput(getListPipelineRunParameter()).get(0).getName());
        assertEquals("www.url.ru", taskMapper.createTesOutput(getListPipelineRunParameter()).get(0).getUrl());
    }

    @Test
    public void testCreateListExecutor() {
        List<TesExecutor> listTesExecutor = new ArrayList<>();
        listTesExecutor.add(TesExecutor.builder()
                .command(Collections.singletonList(getPipelineRun().getActualCmd()))
                .env(getPipelineRun().getEnvVars())
                .image(getPipelineRun().getDockerImage())
                .build());
        assertEquals(listTesExecutor, taskMapper.createListExecutor(getPipelineRun()));
    }

    @Test
    public void testCreateTesTaskLog(){
        RunLog runLog = RunLog.builder()
                .date(new Date(12,12,12))
                .taskName("log")
                .logText("log")
                .build();
        when(cloudPipelineAPIClient.getRunLog(anyLong())).thenReturn(Collections.singletonList(runLog));
        List<TesExecutorLog> tesExecutorLogList = Collections.singletonList(TesExecutorLog.builder()
                .stdout(String.format("%s - %s - %s", runLog.getDate(), runLog.getTaskName(), runLog.getLogText()))
                .build());
        List<TesTaskLog> tesTaskLogsList = Collections.singletonList(TesTaskLog.builder()
                .logs(tesExecutorLogList)
                .build());
        assertEquals(tesTaskLogsList, taskMapper.createTesTaskLog(anyLong()));
    }

    private PipelineRun getPipelineRun() {
        PipelineRun pipelineRun = new PipelineRun();
        pipelineRun.setId(1L);
        pipelineRun.setStatus(TaskStatus.RUNNING);
        pipelineRun.setPodIP("pipelineRun");
        pipelineRun.setPipelineRunParameters(getListPipelineRunParameter());
        pipelineRun.setStartDate(new Date(2000));
        pipelineRun.setInstance(getRunInstance());
        pipelineRun.setActualCmd("cmd");
        HashMap<String, String> env = new HashMap<>();
        env.put("env", "env");
        pipelineRun.setEnvVars(env);
        pipelineRun.setDockerImage("centos");
        return pipelineRun;
    }

    private RunInstance getRunInstance() {
        RunInstance runInstance = new RunInstance();
        runInstance.setNodeType("nodeType");
        runInstance.setSpot(true);
        runInstance.setNodeDisk(1000);
        runInstance.setCloudRegionId(1L);
        return runInstance;
    }

    private InstanceType getInstanceType(){
        return InstanceType.builder()
                .name(getPipelineRun().getInstance().getNodeType())
                .memory(10)
                .memoryUnit("KiB")
                .vCPU(8)
                .build();
    }

    @Test
    public void testGetInstanceType() {
        when(cloudPipelineAPIClient.loadTool(anyString())).thenReturn(mock(Tool.class));
        when(cloudPipelineAPIClient.loadAllowedInstanceAndPriceTypes(anyLong(), anyLong(),
                anyBoolean())).thenReturn(getAllowedInstanceAndPriceTypes());
        InstanceType instanceType = getInstanceType();
        assertEquals(instanceType, taskMapper.getInstanceType(getPipelineRun()));
    }

    @Test
    public void testCreateTesResources() {
        TesResources tesResources = TesResources.builder()
                .preemptible(getPipelineRun().getInstance().getSpot())
                .diskGb(new Double(getPipelineRun().getInstance().getNodeDisk()))
                .ramGb(getInstanceType().getMemory() * taskMapper.convertMemoryUnitTypeToGiB(getInstanceType().getMemoryUnit()))
                .cpuCores((long) getInstanceType().getVCPU())
                .zones(Collections.singletonList(DEFAULT_REGION_NAME))
                .build();
        when(cloudPipelineAPIClient.loadRegion(Mockito.eq(getPipelineRun().getInstance().getCloudRegionId())).getRegionCode()).thenReturn(DEFAULT_REGION_NAME);

        when(cloudPipelineAPIClient.loadAllowedInstanceAndPriceTypes(anyLong(), anyLong(),
                getPipelineRun().getInstance().getSpot())).thenReturn(getAllowedInstanceAndPriceTypes());
        assertEquals(tesResources, taskMapper.createTesResources(getPipelineRun()));
    }

    private TesResources getTesResources() {
        return TesResources.builder()
                .preemptible(getPipelineRun().getInstance().getSpot())
                .diskGb(new Double(getPipelineRun().getInstance().getNodeDisk()))
                .ramGb(getInstanceType().getMemory() * taskMapper.convertMemoryUnitTypeToGiB(getInstanceType().getMemoryUnit()))
                .cpuCores((long) getInstanceType().getVCPU())
                .zones(Collections.singletonList(DEFAULT_REGION_NAME))
                .build();
    }

    private AllowedInstanceAndPriceTypes getAllowedInstanceAndPriceTypes() {
        return new AllowedInstanceAndPriceTypes(Collections.singletonList(getInstanceType()),
                Collections.singletonList(getInstanceType()), Collections.singletonList("string"));
    }

//    @Test
//    public void getMapToTesTask(){
//        assertEquals(TesTask.builder()
//                .id(String.valueOf(getPipelineRun().getId()))
//                .state(TesState.RUNNING).build(), taskMapper.mapToTesTask(getPipelineRun(), TaskView.MINIMAL));
//
//        when(cloudPipelineAPIClient.loadPipelineTasks(getPipelineRun().getId())).thenReturn(getPipelineTaskListWithConsole());
//        when(cloudPipelineAPIClient.loadTool(anyString())).thenReturn(new Tool());
//        List<TesExecutor> tesExecutors = new ArrayList<>();
//        tesExecutors.add(new TesExecutor());
//        tesExecutors.add(new TesExecutor());
//        assertEquals(TesTask.builder()
//                .id(String.valueOf(getPipelineRun().getId()))
//                .state(TesState.RUNNING)
//                .name(getPipelineRun().getPodId())
//                .resources(getTesResources())
//                .executors(tesExecutors)
//                .outputs(Collections.singletonList(new TesOutput()))
//                .creationTime(getPipelineRun().getStartDate().toString())
//                .build(), taskMapper.mapToTesTask(getPipelineRun(), TaskView.BASIC));
//    }
}