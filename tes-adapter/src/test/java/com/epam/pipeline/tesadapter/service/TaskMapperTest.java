package com.epam.pipeline.tesadapter.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskMapperTest {
    @Test
    void mapToPipelineStartTest() {

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