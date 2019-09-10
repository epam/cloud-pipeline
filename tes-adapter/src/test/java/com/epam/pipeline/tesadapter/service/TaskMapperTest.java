package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.entity.cluster.AllowedInstanceAndPriceTypes;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.tesadapter.common.MessageConstants;
import com.epam.pipeline.tesadapter.common.MessageHelper;
import com.epam.pipeline.tesadapter.configuration.AppConfiguration;
import com.epam.pipeline.tesadapter.entity.TesExecutor;
import com.epam.pipeline.tesadapter.entity.TesResources;
import com.epam.pipeline.tesadapter.entity.TesTask;
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
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@ExtendWith(value = SpringExtension.class)
@ContextConfiguration(classes = {AppConfiguration.class})
@SuppressWarnings("unused")
class TaskMapperTest {
    private static final String EXECUTORS = "executors";
    private static final Integer STUBBED_HDD_SIZE = 50;
    private static final Double STUBBED_RAM_GB = 16.0;
    private static final Long STUBBED_CPU_CORES = 2L;
    private static final Boolean STUBBED_PREEMPTIBLE = true;
    private static final String STUBBED_REGION_NAME = "eu-central-1";
    private static final Long STUBBED_REGION_ID = 1L;
    private static final Long STUBBED_TOOL_ID = 11584L;
    private static final String IMAGE = "cp-docker-registry.default.svc.cluster.local:31443/library/centos:latest";

    @Autowired
    private MessageHelper messageHelper;

    private TaskMapper taskMapper;


    private List<String> zones = new ArrayList<>();
    private List<AbstractCloudRegion> abstractCloudRegions = new ArrayList<>();
    private List<InstanceType> allowedInstanceTypes = new ArrayList<>();

    private AbstractCloudRegion abstractCloudRegion = mock(AbstractCloudRegion.class);
    private CloudPipelineAPIClient cloudPipelineAPIClient = mock(CloudPipelineAPIClient.class);
    private AllowedInstanceAndPriceTypes allowedInstanceAndPriceTypes = mock(AllowedInstanceAndPriceTypes.class);
    private Tool tool = mock(Tool.class);
    private TesTask tesTask = mock(TesTask.class);


    @BeforeEach
    void setUp() {
        when(abstractCloudRegion.getName()).thenReturn(STUBBED_REGION_NAME);
        when(abstractCloudRegion.getId()).thenReturn(STUBBED_REGION_ID);
        when(cloudPipelineAPIClient.loadAllRegions()).thenReturn(abstractCloudRegions);
        when(cloudPipelineAPIClient.loadTool(IMAGE)).thenReturn(tool);
        when(cloudPipelineAPIClient.loadAllowedInstanceAndPriceTypes(STUBBED_TOOL_ID,
                STUBBED_REGION_ID, STUBBED_PREEMPTIBLE)).thenReturn(allowedInstanceAndPriceTypes);
        when(tool.getId()).thenReturn(STUBBED_TOOL_ID);
        when(tesTask.getResources()).thenReturn(mock(TesResources.class));
        when(tesTask.getResources().getPreemptible()).thenReturn(true);

        this.taskMapper = new TaskMapper(STUBBED_HDD_SIZE, STUBBED_RAM_GB, STUBBED_CPU_CORES,
                STUBBED_PREEMPTIBLE, STUBBED_REGION_NAME, cloudPipelineAPIClient, this.messageHelper);
    }

    @Test()
    void expectIllegalArgExceptionWhenRunGetExecutorFromTesExecutorsListTest() {
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
    void getProperInstanceTypeShouldReturnProperInstanceName(String instanceTypeName, Double ramGb, Long cpuCore) {
        zones.add(STUBBED_REGION_NAME);
        abstractCloudRegions.add(abstractCloudRegion);
        when(tesTask.getResources().getRamGb()).thenReturn(ramGb);
        when(tesTask.getResources().getCpuCores()).thenReturn(cpuCore);
        when(tesTask.getResources().getZones()).thenReturn(zones);

        when(allowedInstanceAndPriceTypes.getAllowedInstanceTypes()).thenReturn(allowedInstanceTypes);
        fillWithAllowedInstanceTypes(allowedInstanceTypes);
        assertEquals(instanceTypeName, taskMapper.getProperInstanceType(tesTask, tool));
    }

    private static Stream<Arguments> provideInputForGetProperInstanceTypeTest() {
        return Stream.of(
                Arguments.of("m5.large", 8.0, 2L),
                Arguments.of("c5.2xlarge ", 16.0, 8L),
                Arguments.of("c5.4xlarge", 32.0, 16L),
                Arguments.of("m5.4xlarge", 64.0, 16L),
                Arguments.of("m5.8xlarge", 128.0, 32L),
                Arguments.of("m5.16xlarge", 256.0, 64L),
                Arguments.of("c5.18xlarge", 144.0, 72L),
                Arguments.of("m5.24xlarge", 384.0, 96L)
        );
    }

    private void fillWithAllowedInstanceTypes(List<InstanceType> instanceTypeList) {
        instanceTypeList.add(getInstanceWithNameRamAndCpu("m5.large", 8.0, 2L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("c5.2xlarge ", 16.0, 8L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("c5.4xlarge", 32.0, 16L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("m5.4xlarge", 64.0, 16L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("m5.8xlarge", 128.0, 32L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("m5.16xlarge", 256.0, 64L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("c5.18xlarge", 144.0, 72L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("c5.24xlarge", 192.0, 96L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("m5.24xlarge", 384.0, 96L));
        instanceTypeList.add(getInstanceWithNameRamAndCpu("p2.16xlarge", 768.0, 64L));
    }

    private InstanceType getInstanceWithNameRamAndCpu(String instanceName, Double instanceRam, Long instanceCpu) {
        InstanceType instanceType = new InstanceType();
        instanceType.setName(instanceName);
        instanceType.setMemory(instanceRam.floatValue());
        instanceType.setVCPU(instanceCpu.intValue());
        return instanceType;
    }
}