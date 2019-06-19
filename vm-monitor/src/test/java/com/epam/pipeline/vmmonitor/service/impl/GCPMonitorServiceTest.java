/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.vmmonitor.service.impl;

import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.vmmonitor.model.vm.VirtualMachine;
import com.epam.pipeline.vmmonitor.service.VMMonitorService;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceList;
import com.google.api.services.compute.model.NetworkInterface;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
public class GCPMonitorServiceTest {
    private static final String CREDENTIALS_PATH = ""; // path to the Google credentials
    private static final String PROJECT_NAME = ""; // Project ID (for example: "resounding-rune-122345")
    private static final String REGION_CODE = ""; // the name of the zone (for example: "us-central1-a")
    private static final String TAG = "monitored=true"; // tag for VM instances to be searched
    private static final String TEST_INSTANCE_NAME1 = "testInstance1";
    private static final String TEST_INSTANCE_NAME2 = "testInstance2";
    private static final String TEST_INSTANCE_ID1 = "1";
    private static final String TEST_INSTANCE_ID2 = "2";
    private static final String TEST_INSTANCE_INTERNAL_IP1 = "10.128.0.1";
    private static final String TEST_INSTANCE_INTERNAL_IP2 = "10.128.0.2";

    @Spy
    private final GCPMonitorService service = new GCPMonitorService(TAG);
    private final GCPRegion testGcpRegion = new GCPRegion();

    @Before
    public void initializeTestGcpRegion() {
        testGcpRegion.setAuthFile(CREDENTIALS_PATH);
        testGcpRegion.setProject(PROJECT_NAME);
        testGcpRegion.setRegionCode(REGION_CODE);
    }

    @Test
    public void shouldReturnValidProvider() {
        final VMMonitorService service = new GCPMonitorService(TAG);
        assertEquals(CloudProvider.GCP, service.provider());
    }

    @Test
    public void shouldThrowExceptionOnEmptyProjectName() {
        final GCPRegion gcpRegion = new GCPRegion();
        gcpRegion.setAuthFile(CREDENTIALS_PATH);
        gcpRegion.setRegionCode(REGION_CODE);
        assertThrows(IllegalArgumentException.class,
            () -> new GCPMonitorService(TAG).fetchRunningVms(gcpRegion));
    }

    @Test
    public void shouldThrowExceptionOnEmptyZoneId() {
        final GCPRegion gcpRegion = new GCPRegion();
        gcpRegion.setAuthFile(CREDENTIALS_PATH);
        gcpRegion.setProject(PROJECT_NAME);
        assertThrows(IllegalArgumentException.class,
            () -> new GCPMonitorService(TAG).fetchRunningVms(gcpRegion));
    }

    @Test
    public void shouldReturnEmptyInstanceList() {
        final InstanceList list = new InstanceList();
        list.setItems(Collections.emptyList());
        final Compute mockedCompute = Mockito.mock(Compute.class);
        Mockito.doReturn(mockedCompute).when(service).getGCPCompute(Mockito.any());
        Mockito.doReturn(list).when(service).getGcpVmInstances(Mockito.any(), Mockito.any());
        assertEquals(0, service.fetchRunningVms(testGcpRegion).size());
    }

    @Test
    public void shouldReturnNotEmptyInstanceList() {
        final List<VirtualMachine> expectedVMs = createExpectedVmsList();
        final List<Instance> receivedInstances = createReceivedInstancesList();
        final InstanceList receivedGcpInstances = new InstanceList();
        receivedGcpInstances.setItems(receivedInstances);

        final Compute mockedCompute = Mockito.mock(Compute.class);
        Mockito.doReturn(mockedCompute).when(service).getGCPCompute(Mockito.any());
        Mockito.doReturn(receivedGcpInstances).when(service).getGcpVmInstances(Mockito.any(), Mockito.any());
        final List<VirtualMachine> virtualMachines = service.fetchRunningVms(testGcpRegion);
        final Map<String, VirtualMachine> actualVMs = virtualMachines.stream()
                 .collect(Collectors.toMap(VirtualMachine::getInstanceName, Function.identity()));

        assertEquals(expectedVMs.size(), virtualMachines.size());
        expectedVMs.forEach(vm -> Assertions.assertTrue(assertVMs(vm, actualVMs.get(vm.getInstanceName()))));
    }

    private List <VirtualMachine> createExpectedVmsList() {
        return Arrays.asList(
            VirtualMachine.builder()
                          .instanceName(TEST_INSTANCE_NAME1)
                          .instanceId(TEST_INSTANCE_ID1)
                          .cloudProvider(CloudProvider.GCP)
                          .privateIp(TEST_INSTANCE_INTERNAL_IP1)
                          .build(),
            VirtualMachine.builder()
                          .instanceName(TEST_INSTANCE_NAME2)
                          .instanceId(TEST_INSTANCE_ID2)
                          .cloudProvider(CloudProvider.GCP)
                          .privateIp(TEST_INSTANCE_INTERNAL_IP2)
                          .build());
    }

    private List <Instance> createReceivedInstancesList() {
        final List<NetworkInterface> networkInterfaces1 =
            Collections.singletonList(new NetworkInterface().setNetworkIP(TEST_INSTANCE_INTERNAL_IP1));
        final List<NetworkInterface> networkInterfaces2 =
            Collections.singletonList(new NetworkInterface().setNetworkIP(TEST_INSTANCE_INTERNAL_IP2));
        return Arrays.asList(
            new Instance().setName(TEST_INSTANCE_NAME1)
                          .setId(new BigInteger(TEST_INSTANCE_ID1))
                          .setZone(REGION_CODE)
                          .setNetworkInterfaces(networkInterfaces1),
            new Instance().setName(TEST_INSTANCE_NAME2)
                          .setId(new BigInteger(TEST_INSTANCE_ID2))
                          .setZone(REGION_CODE)
                          .setNetworkInterfaces(networkInterfaces2));
    }

    private static boolean assertVMs(final VirtualMachine expected, final VirtualMachine actual) {
        return expected.getInstanceName().equals(actual.getInstanceName())
                   && expected.getInstanceId().equals(actual.getInstanceId())
                   && expected.getCloudProvider().equals(actual.getCloudProvider())
                   && expected.getPrivateIp().equals(actual.getPrivateIp());
    }
}
