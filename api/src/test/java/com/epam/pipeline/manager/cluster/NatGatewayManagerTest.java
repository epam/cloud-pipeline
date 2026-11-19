/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.epam.pipeline.dao.cluster.NatGatewayDao;
import com.epam.pipeline.entity.cluster.nat.NatRouteStatus;
import com.epam.pipeline.entity.cluster.nat.NatRoutingRuleDescription;
import com.epam.pipeline.entity.cluster.nat.NatRoutingRulesRequest;
import com.epam.pipeline.manager.AbstractManagerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Transactional
@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
@ExtendWith(MockitoExtension.class)
public class NatGatewayManagerTest extends AbstractManagerTest {

    private static final String NAT_ROUTE_DOMAIN_NAME = "nowhere.com";
    private static final String EXTERNAL_IP = "87.1.1.1";
    private static final Integer EXTERNAL_PORT_1 = 1;
    private static final Integer EXTERNAL_PORT_2 = 2;
    private static final String DESCRIPTION = "test";
    private static final String TCP = "TCP";

    @Autowired
    private NatGatewayManager natGatewayManager;

    @SpyBean
    private NatGatewayDao natGatewayDao;

    @SpyBean
    private KubernetesManager kubernetesManager;

    @BeforeEach
    public void init() {
        doReturn(Collections.emptyList()).when(kubernetesManager)
                .getCloudPipelineServiceInstances(anyString());
    }

    @Test
    public void testRulesRegistration() {
        final NatRoutingRuleDescription newRoutingRule1 = buildRoutingRule(EXTERNAL_PORT_1);
        final NatRoutingRuleDescription newRoutingRule2 = buildRoutingRule(EXTERNAL_PORT_2);

        final NatRoutingRulesRequest natRoutingRulesRequest1 =
                new NatRoutingRulesRequest(Collections.singletonList(newRoutingRule1));
        assertThat(natGatewayManager.registerRoutingRulesCreation(natRoutingRulesRequest1)).hasSize(1);
        assertThat(natGatewayManager.loadAllRoutes()).hasSize(1);

        final NatRoutingRulesRequest natRoutingRulesRequest2 =
                new NatRoutingRulesRequest(Collections.singletonList(newRoutingRule2));
        assertThat(natGatewayManager.registerRoutingRulesCreation(natRoutingRulesRequest2)).hasSize(1);
        assertThat(natGatewayManager.loadAllRoutes()).hasSize(2);

        assertThrows(IllegalArgumentException.class, () ->
                natGatewayManager.registerRoutingRulesCreation(natRoutingRulesRequest1)
        );
    }

    @Test
    public void testRulesRemoval() {
        final NatRoutingRuleDescription newRoutingRule = buildRoutingRule(EXTERNAL_PORT_1);
        final List<NatRoutingRuleDescription> newRules = Collections.singletonList(newRoutingRule);
        final NatRoutingRulesRequest natRoutingRulesRequest = new NatRoutingRulesRequest(newRules);

        assertThat(natGatewayManager.registerRoutingRulesRemoval(natRoutingRulesRequest)).hasSize(1);
        assertThat(natGatewayManager.loadAllRoutes()).hasSize(1);

        assertThat(natGatewayManager.registerRoutingRulesRemoval(natRoutingRulesRequest)).isEmpty();
        assertThat(natGatewayManager.loadAllRoutes()).hasSize(1);
    }

    @Test
    public void testRulesQueuedRuleStatusChange() {
        final NatRoutingRuleDescription newRoutingRule = buildRoutingRule(EXTERNAL_PORT_1);
        final List<NatRoutingRuleDescription> newRules = Collections.singletonList(newRoutingRule);
        final NatRoutingRulesRequest natRoutingRulesRequest = new NatRoutingRulesRequest(newRules);
        assertThat(natGatewayManager.registerRoutingRulesCreation(natRoutingRulesRequest)).hasSize(1);
        verify(natGatewayDao, times(newRules.size()))
                .registerRoutingRules(anyList(), eq(NatRouteStatus.CREATION_SCHEDULED));
        assertThat(natGatewayManager.loadAllRoutes()).hasSize(1);

        assertThat(natGatewayManager.registerRoutingRulesRemoval(natRoutingRulesRequest)).hasSize(1);
        verify(natGatewayDao, times(0))
                .registerRoutingRules(anyList(), eq(NatRouteStatus.TERMINATION_SCHEDULED));
        verify(natGatewayDao, times(newRules.size())).updateRoute(any());
        assertThat(natGatewayManager.loadAllRoutes()).hasSize(1);
    }

    private NatRoutingRuleDescription buildRoutingRule(final Integer externalPort) {
        return new NatRoutingRuleDescription(NAT_ROUTE_DOMAIN_NAME, EXTERNAL_IP, externalPort, DESCRIPTION, TCP);
    }
}