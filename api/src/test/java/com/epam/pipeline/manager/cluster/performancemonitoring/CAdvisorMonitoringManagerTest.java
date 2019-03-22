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

package com.epam.pipeline.manager.cluster.performancemonitoring;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.NodeInstanceAddress;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.fabric8.kubernetes.api.model.NodeAddress;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;

import java.util.Collections;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD.TooManyStaticImports")
public class CAdvisorMonitoringManagerTest {

    private static final String NODE_WITH_INTERNALIP_ADRESS = "internalip_node";
    private static final String NODE_WITHOUT_INTERNALIP_ADRESS = "not_internalip_node";
    private static final String INTERNALIP_ADDRESS = "localhost";
    private static final String NOT_INTERNALIP_ADDRESS = "address";
    private static final String INTERNALIP_TYPE = "internalip";
    private static final String NOT_INTERNALIP_TYPE = "not_internal";

    private static final int OK_STATUS = 200;
    private static final String TEST_MESSAGE = "message";

    private static final String EXPECTED_END_DATE = "2018-09-10T11:19:10.122955822Z";
    private static final long EXPECTED_MAX_MEMORY = 16828661760L;
    private static final double EXPECTED_CPU_USAGE = 0.021954769144385027;
    private static final long EXPECTED_MEMORY_USAGE = 5220487168L;
    private static final long EXPECTED_MEMORY_CAPACITY = 16828661760L;
    private static final int C_ADVISOR_PORT = 4194;
    private static final int TIMEOUT = 10;


    private CAdvisorMonitoringManager cAdvisorMonitoringManager;

    @Mock
    private NodesManager nodesManager;

    @Mock
    private MessageHelper messageHelper;

    @Mock
    private KubernetesManager kubernetesManager;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        JsonMapper objectMapper = new JsonMapper();
        cAdvisorMonitoringManager = new CAdvisorMonitoringManager(nodesManager, messageHelper,
                objectMapper, kubernetesManager, TIMEOUT, C_ADVISOR_PORT, false);
    }

    @Test
    public void testGetStatsForNodeWithIp() {
        configureMocksForGetStatsForNodeWithIp();
        List<MonitoringStats> monitoringStatsList =
                cAdvisorMonitoringManager.getStatsForNode(NODE_WITH_INTERNALIP_ADRESS);

        assertNotNull(monitoringStatsList);
        assertThat(monitoringStatsList.size(), is(7));

        assertThat(monitoringStatsList.get(0).getEndTime(), is(EXPECTED_END_DATE));
        assertThat(monitoringStatsList.get(0).getContainerSpec().getMaxMemory(), is(EXPECTED_MAX_MEMORY));
        assertThat(monitoringStatsList.get(0).getCpuUsage().getLoad(), is(EXPECTED_CPU_USAGE));
        assertThat(monitoringStatsList.get(0).getMemoryUsage().getCapacity(), is(EXPECTED_MEMORY_CAPACITY));
        assertThat(monitoringStatsList.get(1).getMemoryUsage().getUsage(), is(EXPECTED_MEMORY_USAGE));
        assertThat(monitoringStatsList.get(1).getDisksUsage().getStatsByDevices().size(), is(2));
        assertThat(monitoringStatsList.get(2).getNetworkUsage().getStatsByInterface().size(), is(3));
    }

    @Test
    public void testGetStatsForNodeWithoutIp() {
        NodeInstance nodeWithoutIP = getNodeInstance(NOT_INTERNALIP_ADDRESS, NOT_INTERNALIP_TYPE,
                NODE_WITHOUT_INTERNALIP_ADRESS);
        when(nodesManager.getNode(NODE_WITHOUT_INTERNALIP_ADRESS)).thenReturn(nodeWithoutIP);

        assertThat(cAdvisorMonitoringManager.getStatsForNode(NODE_WITHOUT_INTERNALIP_ADRESS),
                is(Collections.emptyList()));
    }

    private NodeInstance getNodeInstance(String address, String type, String nodeName) {
        NodeAddress nodeAddress = new NodeAddress(address, type);
        NodeInstanceAddress nodeInstanceAddress = new NodeInstanceAddress(nodeAddress);

        NodeInstance nodeWithIP = new NodeInstance();
        nodeWithIP.setName(nodeName);
        nodeWithIP.setAddresses(Collections.singletonList(nodeInstanceAddress));
        return nodeWithIP;
    }

    private void configureMocksForGetStatsForNodeWithIp() {
        NodeInstance nodeWithIP = getNodeInstance(INTERNALIP_ADDRESS, INTERNALIP_TYPE, NODE_WITH_INTERNALIP_ADRESS);
        when(nodesManager.getNode(NODE_WITH_INTERNALIP_ADRESS)).thenReturn(nodeWithIP);
        when(messageHelper.getMessage(anyString(), anyObject())).thenReturn(TEST_MESSAGE);

        Whitebox.setInternalState(cAdvisorMonitoringManager, "cAdvisorPort", (wireMockRule.port()));

        stubFor(get("/api/v1.3/containers/").willReturn(aResponse().withBodyFile("cadvisor.json")
                .withStatus(OK_STATUS)));
    }

}
