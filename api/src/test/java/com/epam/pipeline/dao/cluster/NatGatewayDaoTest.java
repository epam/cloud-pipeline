/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dao.cluster;

import com.epam.pipeline.entity.cluster.nat.NatRoute;
import com.epam.pipeline.entity.cluster.nat.NatRouteStatus;
import com.epam.pipeline.entity.cluster.nat.NatRoutingRuleDescription;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
public class NatGatewayDaoTest extends AbstractJdbcTest {

    private static final String NAT_ROUTE_DOMAIN_NAME = "nowhere.com";
    private static final String EXTERNAL_IP = "87.1.1.1";
    private static final Integer EXTERNAL_PORT = 1;
    private static final String INTERNAL_SERVICE_NAME = "nowhere.com";
    private static final String INTERNAL_IP = "10.1.1.1";
    private static final Integer INTERNAL_PORT = 2;

    @Autowired
    private NatGatewayDao natGatewayDao;

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testCRUD() {
        final NatRoutingRuleDescription newRoutingRule = new NatRoutingRuleDescription(
            NAT_ROUTE_DOMAIN_NAME, EXTERNAL_IP, EXTERNAL_PORT);
        final List<NatRoutingRuleDescription> newRules = Collections.singletonList(newRoutingRule);
        final List<NatRoute> routingRulesCreated = natGatewayDao.registerRoutingRules(
            newRules, NatRouteStatus.CREATION_SCHEDULED);
        final NatRoute routingRuleCreated = routingRulesCreated.get(0);
        assertRequestEqualsRule(newRoutingRule, routingRuleCreated, NatRouteStatus.CREATION_SCHEDULED);

        final Optional<NatRoute> routingRuleFound = natGatewayDao.findRoute(newRoutingRule);
        Assert.assertTrue(routingRuleFound.isPresent());
        assertRequestEqualsRule(newRoutingRule, routingRuleFound.get(), NatRouteStatus.CREATION_SCHEDULED);
        Assert.assertEquals(routingRulesCreated, natGatewayDao.loadQueuedRouteUpdates());

        final NatRoute routeUpdate = routingRuleCreated.toBuilder()
            .status(NatRouteStatus.PORT_FORWARDING_CONFIGURED)
            .internalName(INTERNAL_SERVICE_NAME)
            .internalIp(INTERNAL_IP)
            .internalPort(INTERNAL_PORT)
            .build();
        natGatewayDao.updateRoute(routeUpdate);
        assertRouteUpdateWithLoaded(routeUpdate, natGatewayDao.loadQueuedRouteUpdates());

        Assert.assertTrue(natGatewayDao.deleteRouteById(routingRuleCreated.getRouteId()));

        assertThat(natGatewayDao.loadQueuedRouteUpdates()).isEmpty();
        Assert.assertFalse(natGatewayDao.findRoute(newRoutingRule).isPresent());
    }

    private void assertRequestEqualsRule(final NatRoutingRuleDescription request,
                                         final NatRoute routingRuleCreated,
                                         final NatRouteStatus status) {
        Assert.assertNotNull(routingRuleCreated);
        Assert.assertNotNull(routingRuleCreated.getRouteId());
        Assert.assertEquals(request.getExternalName(), routingRuleCreated.getExternalName());
        Assert.assertEquals(request.getExternalIp(), routingRuleCreated.getExternalIp());
        Assert.assertEquals(request.getPort(), routingRuleCreated.getExternalPort());
        Assert.assertEquals(status, routingRuleCreated.getStatus());
    }

    private void assertRouteUpdateWithLoaded(final NatRoute routeUpdate, final List<NatRoute> loadedRoutes) {
        assertThat(loadedRoutes).hasSize(1);
        final NatRoute loadedRouteAfterUpdate = loadedRoutes.get(0);
        Assert.assertTrue(routeUpdate.getLastUpdateTime().isBefore(loadedRouteAfterUpdate.getLastUpdateTime()));
        Assert.assertNotEquals(routeUpdate, loadedRouteAfterUpdate);
        final NatRoute routeUpdateWithCorrectUpdateTime = routeUpdate.toBuilder()
            .lastUpdateTime(loadedRouteAfterUpdate.getLastUpdateTime())
            .build();
        Assert.assertEquals(routeUpdateWithCorrectUpdateTime, loadedRouteAfterUpdate);
    }
}
