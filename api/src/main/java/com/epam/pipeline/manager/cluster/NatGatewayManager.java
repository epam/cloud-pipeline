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

package com.epam.pipeline.manager.cluster;

import com.epam.pipeline.dao.cluster.NatGatewayDao;
import com.epam.pipeline.entity.cluster.nat.NatRoute;
import com.epam.pipeline.entity.cluster.nat.NatRouteStatus;
import com.epam.pipeline.entity.cluster.nat.NatRoutingRuleDescription;
import com.epam.pipeline.entity.cluster.nat.NatRoutingRulesRequest;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@AllArgsConstructor
public class NatGatewayManager {

    private final NatGatewayDao natGatewayDao;

    public Set<String> resolveAddress(final String hostname) {
        try {
            return Stream.of(InetAddress.getAllByName(hostname))
                .map(InetAddress::getHostAddress)
                .collect(Collectors.toSet());
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Unable to resolve the given hostname: " + hostname);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<NatRoute> registerRoutingRulesCreation(final NatRoutingRulesRequest request) {
        final List<NatRoutingRuleDescription> requestedRules = validateRequestIsNotEmpty(request);
        final List<NatRoute> updatedRoutes = updateStatusForQueuedRoutes(requestedRules,
                                                                         NatRouteStatus.CREATION_SCHEDULED);

        final Set<NatRoutingRuleDescription> existingRules = getActiveNatRules(route -> true);
        final List<NatRoutingRuleDescription> newRules = requestedRules.stream()
            .filter(rule -> !existingRules.contains(rule))
            .collect(Collectors.toList());
        final List<NatRoute> queuedRoutes = CollectionUtils.isNotEmpty(newRules)
                                         ? natGatewayDao.registerRoutingRules(newRules,
                                                                              NatRouteStatus.CREATION_SCHEDULED)
                                         : Collections.emptyList();
        updatedRoutes.addAll(queuedRoutes);
        return updatedRoutes;
    }

    public List<NatRoute> loadAllRoutes() {
        final Map<NatRoutingRuleDescription, NatRoute> routeMap =
            convertRoutesToMap(loadAllActiveRoutesFromKubernetes());
        natGatewayDao.loadQueuedRouteUpdates().forEach(queuedRoute -> resolveQueuedRoutes(queuedRoute, routeMap));
        return new ArrayList<>(routeMap.values());
    }

    public List<NatRoute> loadAllActiveRoutesFromKubernetes() {
        return Collections.emptyList();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<NatRoute> registerRoutingRulesRemoval(final NatRoutingRulesRequest request) {
        final List<NatRoutingRuleDescription> requestedRules = validateRequestIsNotEmpty(request);
        final List<NatRoute> updatedRoutes = updateStatusForQueuedRoutes(requestedRules,
                                                                         NatRouteStatus.TERMINATION_SCHEDULED);
        final Set<NatRoutingRuleDescription> existingTerminatingRules = getActiveNatRules(this::hasTerminatingState);
        final List<NatRoutingRuleDescription> newRules = requestedRules.stream()
            .filter(rule -> !existingTerminatingRules.contains(rule))
            .collect(Collectors.toList());
        final List<NatRoute> queuedRoutes = CollectionUtils.isNotEmpty(newRules)
                                         ? natGatewayDao.registerRoutingRules(newRules,
                                                                              NatRouteStatus.TERMINATION_SCHEDULED)
                                         : Collections.emptyList();
        updatedRoutes.addAll(queuedRoutes);
        return updatedRoutes;
    }

    private List<NatRoute> updateStatusForQueuedRoutes(final List<NatRoutingRuleDescription> requestedRules,
                                                       final NatRouteStatus requiredStatus) {
        final Map<NatRoutingRuleDescription, NatRoute> queuedRoutesMapping =
            convertRoutesToMap(natGatewayDao.loadQueuedRouteUpdates());
        final List<NatRoute> updatedRoutes = requestedRules.stream()
            .map(queuedRoutesMapping::get)
            .filter(Objects::nonNull)
            .filter(existingRoute -> !existingRoute.getStatus().equals(requiredStatus))
            .map(existingRoute -> existingRoute.toBuilder().status(requiredStatus).build())
            .collect(Collectors.toList());
        updatedRoutes.forEach(natGatewayDao::updateRoute);
        return updatedRoutes;
    }

    private Set<NatRoutingRuleDescription> getActiveNatRules(final Predicate<NatRoute> additionalFilter) {
        return loadAllRoutes().stream()
            .filter(additionalFilter::evaluate)
            .map(this::mapRouteToDescription)
            .collect(Collectors.toSet());
    }

    private NatRoutingRuleDescription mapRouteToDescription(final NatRoute route) {
        return new NatRoutingRuleDescription(route.getExternalName(), route.getExternalIp(), route.getExternalPort());
    }

    private void resolveQueuedRoutes(final NatRoute queuedRoute,
                                     final Map<NatRoutingRuleDescription, NatRoute> routeMap) {
        final NatRoutingRuleDescription correspondingRule = mapRouteToDescription(queuedRoute);
        final NatRoute resolvedRoute = Optional.ofNullable(routeMap.get(correspondingRule))
            .map(existingRoute -> existingRoute.toBuilder()
                .status(queuedRoute.getStatus())
                .lastUpdateTime(queuedRoute.getLastUpdateTime())
                .build())
            .orElse(queuedRoute);
        routeMap.put(correspondingRule, resolvedRoute);
    }

    private Map<NatRoutingRuleDescription, NatRoute> convertRoutesToMap(final List<NatRoute> natRoutes) {
        return natRoutes.stream().collect(Collectors.toMap(this::mapRouteToDescription, Function.identity()));
    }

    private boolean hasTerminatingState(final NatRoute route) {
        final NatRouteStatus status = route.getStatus();
        return status.isTerminationState();
    }

    private List<NatRoutingRuleDescription> validateRequestIsNotEmpty(final NatRoutingRulesRequest request) {
        return Optional.ofNullable(request)
            .map(NatRoutingRulesRequest::getRules)
            .filter(CollectionUtils::isNotEmpty)
            .orElseThrow(() -> new IllegalArgumentException("Empty rules are passed!"));
    }
}
