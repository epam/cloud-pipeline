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

import static com.epam.pipeline.manager.cluster.KubernetesConstants.TCP;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.Constants;
import com.epam.pipeline.dao.cluster.NatGatewayDao;
import com.epam.pipeline.entity.cluster.nat.NatRoute;
import com.epam.pipeline.entity.cluster.nat.NatRouteStatus;
import com.epam.pipeline.entity.cluster.nat.NatRoutingRuleDescription;
import com.epam.pipeline.entity.cluster.nat.NatRoutingRulesRequest;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Cache;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class NatGatewayManager {

    private static final String NAT_ROUTE_LABELS_PREFIX = "nat-route-";
    private static final String TARGET_STATUS_LABEL_PREFIX = NAT_ROUTE_LABELS_PREFIX + "target-status-";
    private static final String EXTERNAL_NAME_LABEL = NAT_ROUTE_LABELS_PREFIX + "name";
    private static final String EXTERNAL_IP_LABEL = NAT_ROUTE_LABELS_PREFIX + "ip";
    private static final String UNKNOWN = "UNKNOWN";
    private static final String NEW_LINE = "\n";
    private static final String PORT_FORWARDING_RULE_ATTRIBUTE_DESCRIPTOR = ":";
    private static final String EXTERNAL_NAME = "externalName";
    private static final String PORT = "port";
    private static final String EXTERNAL_IP = "externalIp";

    private final NatGatewayDao natGatewayDao;
    private final KubernetesManager kubernetesManager;
    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;
    private final String tinyproxyNatServiceName;
    private final String tinyproxyServiceLabelSelector;
    private final String dnsProxyConfigMapName;
    private final String globalConfigMapName;
    private final Map<String, String> portForwardingRuleMapping;
    private final String hostsKey;
    private final String defaultCustomDnsIP;

    public NatGatewayManager(final NatGatewayDao natGatewayDao,
                             final KubernetesManager kubernetesManager,
                             final MessageHelper messageHelper,
                             final PreferenceManager preferenceManager,
                             @Value("${nat.gateway.cp.service.name:cp-tinyproxy-nat}")
                             final String tinyproxyNatServiceName,
                             @Value("${nat.gateway.cp.service.label.selector:cp-tinyproxy}")
                             final String tinyproxyServiceLabelSelector,
                             @Value("${nat.gateway.cm.dns.proxy.name:cp-dnsmasq-hosts}")
                             final String dnsProxyConfigMapName,
                             @Value("${nat.gateway.cm.global.name:cp-config-global}")
                             final String globalConfigMapName,
                             @Value("#{${nat.gateway.port.forwarding.protocols.mapping:"
                                    + "{TCP:'CP_TP_TCP_DEST',UDP:'CP_TP_UDP_DEST'}}}")
                             final Map<String, String> portForwardingRuleMapping,
                             @Value("${nat.gateway.custom.dns.server.ip:}")
                             final String defaultCustomDnsIP,
                             @Value("${nat.gateway.hosts.key:hosts}") final String hostsKey) {
        this.natGatewayDao = natGatewayDao;
        this.kubernetesManager = kubernetesManager;
        this.messageHelper = messageHelper;
        this.preferenceManager = preferenceManager;
        this.tinyproxyServiceLabelSelector = tinyproxyServiceLabelSelector;
        this.tinyproxyNatServiceName = tinyproxyNatServiceName;
        this.dnsProxyConfigMapName = dnsProxyConfigMapName;
        this.globalConfigMapName = globalConfigMapName;
        this.portForwardingRuleMapping = portForwardingRuleMapping;
        this.hostsKey = hostsKey;
        this.defaultCustomDnsIP = defaultCustomDnsIP;
    }

    @SchedulerLock(name = "NatGatewayManager_updateStatus", lockAtMostForString = "PT5M")
    @Scheduled(fixedDelayString = "${nat.gateway.auto.config.poll:60000}")
    public void updateStatus() {
        if (preferenceManager.getPreference(SystemPreferences.SYSTEM_DISABLE_NAT_SYNC)) {
            log.debug("NAT routes synchronization is disabled.");
            return;
        }
        moveQueuedRoutesToKube();
        processRoutesInKube();
    }

    public Set<String> resolveAddress(final String hostname, final String dnsServer) {
        final Set<String> resolvedAddresses = tryResolveAddress(hostname, dnsServer);
        if (CollectionUtils.isEmpty(resolvedAddresses)) {
            throw new IllegalArgumentException("Unable to resolve the given hostname: " + hostname);
        }
        return resolvedAddresses;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<NatRoute> registerRoutingRulesCreation(final NatRoutingRulesRequest request) {
        return updateRoutesInDatabase(request, NatRouteStatus.CREATION_SCHEDULED);
    }

    public List<NatRoute> loadAllRoutes() {
        final Map<NatRoutingRuleDescription, NatRoute> routeMap =
            convertRoutesToMap(loadAllActiveRoutesFromKubernetes(), true);
        natGatewayDao.loadQueuedRouteUpdates().forEach(queuedRoute -> resolveQueuedRoutes(queuedRoute, routeMap));
        return new ArrayList<>(routeMap.values());
    }

    public List<NatRoute> loadAllActiveRoutesFromKubernetes() {
        return loadProxyServicesFromKube()
            .stream()
            .map(this::extractNatRoutesFromKubeService)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<NatRoute> registerRoutingRulesRemoval(final NatRoutingRulesRequest request) {
        return updateRoutesInDatabase(request, NatRouteStatus.TERMINATION_SCHEDULED);
    }

    private Set<String> tryResolveAddress(final String hostname, final String dnsServer) {
        final String dnsServerIp = Optional.ofNullable(dnsServer).orElse(defaultCustomDnsIP);
        return StringUtils.isBlank(dnsServerIp)
               ? resolveNameUsingSystemDefaultDns(hostname)
               : resolveNameUsingCustomDns(hostname, dnsServerIp);
    }

    private Set<String> resolveNameUsingCustomDns(final String hostname, final String dnsServerIp) {
        final String lookupHostname = StringUtils.endsWith(hostname, Constants.DOT)
                                      ? hostname
                                      : hostname + Constants.DOT;
        try {
            final Lookup lookup = new Lookup(lookupHostname, Type.A);
            lookup.setCache(new Cache());
            lookup.setResolver(new SimpleResolver(dnsServerIp));
            return Optional.ofNullable(lookup.run())
                .map(Stream::of)
                .orElse(Stream.empty())
                .filter(ARecord.class::isInstance)
                .map(ARecord.class::cast)
                .map(ARecord::getAddress)
                .map(InetAddress::getHostAddress)
                .collect(Collectors.toSet());
        } catch (UnknownHostException | TextParseException e) {
            log.error(messageHelper.getMessage(MessageConstants.NAT_ADDRESS_RESOLVING_EXCEPTION,
                                               hostname, e.getMessage()));
            return Collections.emptySet();
        }
    }

    private Set<String> resolveNameUsingSystemDefaultDns(final String hostname) {
        try {
            return Stream.of(InetAddress.getAllByName(hostname))
                .map(InetAddress::getHostAddress)
                .collect(Collectors.toSet());
        } catch (UnknownHostException e) {
            log.error(messageHelper.getMessage(MessageConstants.NAT_ADDRESS_RESOLVING_EXCEPTION,
                                               hostname, e.getMessage()));
            return Collections.emptySet();
        }
    }

    private void moveQueuedRoutesToKube() {
        final List<NatRoute> allQueuedRoutes = natGatewayDao.loadQueuedRouteUpdates();
        log.info(messageHelper.getMessage(MessageConstants.NAT_ROUTE_CONFIG_TRANSFER_ROUTES_TO_KUBE,
                                          allQueuedRoutes.size()));
        final Map<String, Service> proxyServicesMapping = loadProxyServicesFromKube().stream()
            .collect(Collectors.toMap(this::extractExternalRouteFromService, Function.identity()));
        allQueuedRoutes.forEach(route -> addQueuedRouteToKubeServices(proxyServicesMapping, route));
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void processRoutesInKube() {
        loadProxyServicesFromKube().forEach(service -> {
            try {
                processKubeService(service);
            } catch (Exception e) {
                log.error(messageHelper.getMessage(MessageConstants.NAT_SERVICE_CONFIG_GENERAL_ERROR,
                                                   getServiceName(service), e.getMessage()));
            }
        });
    }

    private void processKubeService(final Service service) {
        final Map<String, String> annotations = getServiceAnnotations(service);
        final List<Integer> allExternalPorts = getExternalPortsSpecifiedInAnnotations(annotations);
        if (CollectionUtils.isEmpty(allExternalPorts)) {
            if (MapUtils.isNotEmpty(loadServiceActivePortsFromKube(service))) {
                log.warn(messageHelper.getMessage(MessageConstants.NAT_SERVICE_CONFIG_EMPTY_ANNOTATIONS_WITH_PORTS,
                                                  getServiceName(service)));
                return;
            }
            final String serviceName = getServiceName(service);
            log.warn(messageHelper.getMessage(
                MessageConstants.NAT_ROUTE_REMOVAL_NO_ACTIVE_SERVICE_PORTS, serviceName));
            kubernetesManager.deleteServiceIfExists(serviceName);
        } else {
            allExternalPorts.forEach(port -> processExternalPort(service, annotations, port));
        }
    }

    private void processExternalPort(final Service service, final Map<String, String> annotations, final Integer port) {
        final Map<Integer, ServicePort> activePorts = loadServiceActivePortsFromKube(service);
        final String targetStatus = extractStringFromAnnotations(annotations, getTargetStatusLabelName(port));
        final String currentStatus = extractStringFromAnnotations(annotations, getCurrentStatusLabelName(port));
        if (currentStatus.equals(UNKNOWN) || targetStatus.equals(UNKNOWN)) {
            log.warn(messageHelper.getMessage(MessageConstants.NAT_ROUTE_CONFIG_WARN_UNKNOWN_STATUS_PORT,
                                              getServiceName(service), port, currentStatus, targetStatus));
            return;
        }
        if (!targetStatus.equals(currentStatus)) {
            if (targetStatus.equals(NatRouteStatus.ACTIVE.name())) {
                final String protocol = extractStringFromAnnotations(annotations, getProtocolLabelName(port));
                processScheduledStartup(service, activePorts, port, protocol);
            } else {
                processScheduledTermination(service, activePorts, port);
            }
        }
    }

    private Map<Integer, ServicePort> loadServiceActivePortsFromKube(final Service service) {
        return kubernetesManager.findServiceByName(getServiceName(service))
            .map(Service::getSpec)
            .map(ServiceSpec::getPorts)
            .map(Collection::stream)
            .orElse(Stream.empty())
            .collect(Collectors.toMap(ServicePort::getPort, Function.identity()));
    }

    private Map<String, String> getServiceAnnotations(final Service service) {
        return Optional.of(service)
            .map(Service::getMetadata)
            .map(ObjectMeta::getAnnotations)
            .orElse(Collections.emptyMap());
    }

    private String getServiceName(final Service service) {
        return Optional.of(service).map(Service::getMetadata).map(ObjectMeta::getName).orElse(UNKNOWN);
    }

    private List<Integer> getExternalPortsSpecifiedInAnnotations(final Map<String, String> annotations) {
        return annotations.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(TARGET_STATUS_LABEL_PREFIX))
            .sorted((statusEntry1, statusEntry2) -> {
                if (NatRouteStatus.ACTIVE.name().equals(statusEntry1.getValue())) {
                    return -1;
                } else if (NatRouteStatus.ACTIVE.name().equals(statusEntry2.getValue())) {
                    return 1;
                } else {
                    return 0;
                }
            })
            .map(Map.Entry::getKey)
            .map(key -> key.substring(TARGET_STATUS_LABEL_PREFIX.length()))
            .map(Integer::valueOf)
            .collect(Collectors.toList());
    }

    private boolean processScheduledTermination(final Service service, final Map<Integer, ServicePort> activePorts,
                                                final Integer port) {
        final String serviceName = getServiceName(service);
        log.info(messageHelper.getMessage(MessageConstants.NAT_ROUTE_REMOVAL_SERVICE_PORT, port, serviceName));
        updateStatusForRoutingRule(serviceName, port, NatRouteStatus.TERMINATING);
        if (!removePortForwardingRule(service, activePorts, port)) {
            return setStatusFailed(
                serviceName, port,
                messageHelper.getMessage(MessageConstants.NAT_ROUTE_REMOVAL_PORT_FORWARDING_REMOVAL_FAILED));
        }
        if (!removeDnsMaks(service, activePorts, port)) {
            return setStatusFailed(
                serviceName, port,
                messageHelper.getMessage(MessageConstants.NAT_ROUTE_REMOVAL_DNS_MASK_REMOVAL_FAILED));
        }
        if (removePortFromService(service, activePorts, port)) {
            final ServicePort removedPort = activePorts.remove(port);
            if (removedPort != null && !refreshTinyProxy()) {
                return setStatusFailed(
                    serviceName, port,
                    messageHelper.getMessage(MessageConstants.NAT_ROUTE_REMOVAL_DEPLOYMENT_REFRESH_FAILED));
            }
        } else {
            return setStatusFailed(
                serviceName, port,
                messageHelper.getMessage(MessageConstants.NAT_ROUTE_REMOVAL_PORT_REMOVAL_FAILED));
        }
        refreshKubeDns();
        return activePorts.size() == 0
               || removeStatusAnnotations(service, port);
    }

    private boolean removeStatusAnnotations(final Service service, final Integer port) {
        final Set<String> annotationsToRemove = Stream.of(getCurrentStatusLabelName(port),
                                                          getTargetStatusLabelName(port),
                                                          getLastUpdateTimeLabelName(port),
                                                          getDescriptionLabelName(port),
                                                          getErrorDetailsLabelName(port),
                                                          getProtocolLabelName(port))
            .collect(Collectors.toSet());
        return kubernetesManager.removeAnnotationsFromExistingService(getServiceName(service), annotationsToRemove);
    }

    private boolean processScheduledStartup(final Service service, final Map<Integer, ServicePort> activePorts,
                                            final Integer port, final String protocol) {
        final String serviceName = getServiceName(service);
        log.info(messageHelper.getMessage(MessageConstants.NAT_ROUTE_CONFIG_ROUTE_ON_SERVICE_PORT, port, serviceName));
        if (!addPortToService(serviceName, activePorts, port, protocol)) {
            return setStatusFailed(serviceName, port,
                                   messageHelper.getMessage(MessageConstants.NAT_ROUTE_CONFIG_PORT_ASSIGNING_FAILED));
        }
        if (!addDnsMask(service, port)) {
            return setStatusFailed(serviceName, port,
                                   messageHelper.getMessage(MessageConstants.NAT_ROUTE_CONFIG_DNS_CREATION_FAILED));
        }
        if (!addPortForwardingRule(service, activePorts, port)) {
            removeDnsMaks(service, activePorts, port);
            return setStatusFailed(serviceName, port,
                                   messageHelper.getMessage(MessageConstants.NAT_ROUTE_CONFIG_PORT_FORWARDING_FAILED));
        }
        if (!tryRefreshDeployment(serviceName, port)) {
            removePortForwardingRule(service, activePorts, port);
            removeDnsMaks(service, activePorts, port);
            return refreshTinyProxy();
        }
        return true;
    }

    private boolean tryRefreshDeployment(final String serviceName, final Integer port) {
        if (refreshTinyProxy()) {
            updateStatusForRoutingRule(serviceName, port, NatRouteStatus.ACTIVE);
            return true;
        } else {
            return setStatusFailed(serviceName, port, messageHelper.getMessage(
                MessageConstants.NAT_ROUTE_CONFIG_DEPLOYMENT_REFRESH_FAILED));
        }
    }

    private List<NatRoute> updateRoutesInDatabase(final NatRoutingRulesRequest request, final NatRouteStatus status) {
        final Predicate<NatRoute> additionalFilter;
        final UnaryOperator<NatRoutingRuleDescription> routeMapping;
        final boolean exceptionOnExisting;
        if (status == NatRouteStatus.TERMINATION_SCHEDULED) {
            additionalFilter = this::hasTerminatingState;
            routeMapping = this::toRuleWoDescription;
            exceptionOnExisting = false;
        } else {
            additionalFilter = route -> true;
            routeMapping = rule -> rule;
            exceptionOnExisting = true;
        }

        final List<NatRoutingRuleDescription> requestedRules = validateRequest(request);
        final List<NatRoute> updatedRoutes = updateStatusForQueuedRoutes(requestedRules, status);
        final Set<NatRoutingRuleDescription> existingRules = getActiveNatRules(additionalFilter, routeMapping);
        final List<NatRoutingRuleDescription> newRules = requestedRules.stream()
            .map(routeMapping)
            .filter(rule -> !existingRules.contains(rule))
            .collect(Collectors.toList());
        final Collection<NatRoutingRuleDescription> matchingRules =
            CollectionUtils.isEmpty(newRules)
            ? requestedRules
            : CollectionUtils.intersection(existingRules, newRules);
        if (CollectionUtils.isEmpty(matchingRules)) {
            updatedRoutes.addAll(natGatewayDao.registerRoutingRules(newRules, status));
        } else if (exceptionOnExisting) {
            throw new IllegalArgumentException(messageHelper.getMessage(MessageConstants.NAT_ROUTE_EXISTS_ALREADY,
                                                                        matchingRules));
        }
        return updatedRoutes;
    }

    private Collection<Service> loadProxyServicesFromKube() {
        return CollectionUtils.emptyIfNull(kubernetesManager.getCloudPipelineServiceInstances(tinyproxyNatServiceName));
    }

    private List<NatRoute> updateStatusForQueuedRoutes(final List<NatRoutingRuleDescription> requestedRules,
                                                       final NatRouteStatus requiredStatus) {
        final Map<NatRoutingRuleDescription, NatRoute> queuedRoutesMapping =
            convertRoutesToMap(natGatewayDao.loadQueuedRouteUpdates(), false);
        final List<NatRoute> updatedRoutes = requestedRules.stream()
            .map(this::toRuleWoDescription)
            .map(queuedRoutesMapping::get)
            .filter(Objects::nonNull)
            .filter(existingRoute -> !existingRoute.getStatus().equals(requiredStatus))
            .map(existingRoute -> existingRoute.toBuilder().status(requiredStatus).build())
            .collect(Collectors.toList());
        updatedRoutes.forEach(natGatewayDao::updateRoute);
        return updatedRoutes;
    }

    private Set<NatRoutingRuleDescription> getActiveNatRules(
        final Predicate<NatRoute> additionalFilter, final UnaryOperator<NatRoutingRuleDescription> routeMapping) {
        return loadAllRoutes().stream()
            .filter(additionalFilter::evaluate)
            .map(this::mapRouteToDescription)
            .map(routeMapping)
            .collect(Collectors.toSet());
    }

    private NatRoutingRuleDescription mapRouteToDescription(final NatRoute route) {
        return new NatRoutingRuleDescription(route.getExternalName(), route.getExternalIp(),
                                             route.getExternalPort(), route.getDescription(),
                                             Optional.ofNullable(route.getProtocol()).orElse(TCP));
    }

    public NatRoutingRuleDescription toRuleWoDescription(final NatRoutingRuleDescription rule) {
        return new NatRoutingRuleDescription(rule.getExternalName(), rule.getExternalIp(), rule.getPort(), null,
                                             Optional.ofNullable(rule.getProtocol()).orElse(TCP));
    }

    private void addQueuedRouteToKubeServices(final Map<String, Service> proxyServicesMapping, final NatRoute route) {
        log.info(messageHelper.getMessage(MessageConstants.NAT_ROUTE_CONFIG_ROUTE_TRANSFER_SUMMARY,
                                          getQueuedRouteSummary(route)));
        final String targetExternalRoute = route.getExternalName();
        final NatRouteStatus statusInQueue = route.getStatus();
        if (proxyServicesMapping.containsKey(targetExternalRoute)) {
            final Service correspondingService = proxyServicesMapping.get(targetExternalRoute);
            final String externalIp = getServiceAnnotations(correspondingService).get(EXTERNAL_IP_LABEL);
            if (!NatRouteStatus.TERMINATION_SCHEDULED.equals(statusInQueue)
                && !externalIp.equals(route.getExternalIp())) {
                final NatRoute routeUpdate = route.toBuilder()
                    .lastErrorMessage(messageHelper.getMessage(
                        MessageConstants.NAT_ROUTE_EXTENDING_INVALID_EXTERNAL_IP))
                    .status(NatRouteStatus.FAILED)
                    .build();
                natGatewayDao.updateRoute(routeUpdate);
                return;
            }
            if (!addQueuedRouteToExistingService(correspondingService, route)) {
                log.warn(messageHelper.getMessage(
                    MessageConstants.NAT_ROUTE_CONFIG_ADD_ROUTE_TO_EXISTING_SERVICE_FAILED,
                    route.getRouteId(), getServiceName(correspondingService)));
                return;
            }
        } else if (!statusInQueue.isTerminationState()
                   && !createServiceForQueuedRoute(proxyServicesMapping, route)) {
            return;
        }
        if (!NatRouteStatus.FAILED.equals(statusInQueue)) {
            natGatewayDao.deleteRouteById(route.getRouteId());
        }
    }

    private String getQueuedRouteSummary(final NatRoute route) {
        return String.format(
            "%d,%s,%s,%d",
            route.getRouteId(), route.getExternalName(), route.getExternalIp(), route.getExternalPort());
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private boolean createServiceForQueuedRoute(final Map<String, Service> proxyServicesMapping, final NatRoute route) {
        log.info(messageHelper.getMessage(MessageConstants.NAT_ROUTE_CONFIG_NEW_SERVICE_CREATION, route.getRouteId()));
        final Optional<Integer> freeTargetPort = kubernetesManager.generateFreeTargetPort();
        if (!freeTargetPort.isPresent()) {
            log.info(messageHelper.getMessage(MessageConstants.NAT_ROUTE_CONFIG_PORT_GENERATION_FAILED,
                                              route.getRouteId()));
            return false;
        }
        final String routeExternalName = route.getExternalName();
        final String correspondingServiceName = kubernetesManager.buildProxyServiceName(tinyproxyNatServiceName,
                                                                                        routeExternalName);
        final NatRouteStatus statusInQueue = route.getStatus();
        final Integer externalPort = route.getExternalPort();
        final String targetStatusLabelName = getTargetStatusLabelName(externalPort);
        final Map<String, String> annotations = getRouteUpdateAnnotations(targetStatusLabelName, statusInQueue, route);
        annotations.put(EXTERNAL_NAME_LABEL, routeExternalName);
        annotations.put(EXTERNAL_IP_LABEL, route.getExternalIp());
        final List<ServicePort> servicePorts =
            Collections.singletonList(buildNewServicePort(correspondingServiceName, externalPort,
                                                          freeTargetPort.map(IntOrString::new).get(),
                                                          route.getProtocol()));

        final Map<String, String> labels =
            Collections.singletonMap(KubernetesConstants.CP_LABEL_PREFIX + tinyproxyNatServiceName,
                                     KubernetesConstants.TRUE_LABEL_VALUE);
        final Map<String, String> selector =
            Collections.singletonMap(KubernetesConstants.CP_LABEL_PREFIX + tinyproxyServiceLabelSelector,
                                     KubernetesConstants.TRUE_LABEL_VALUE);
        try {
            proxyServicesMapping.put(routeExternalName,
                                     kubernetesManager.createService(correspondingServiceName, labels, annotations,
                                                                     servicePorts, selector));
            return true;
        } catch (RuntimeException e) {
            log.info(messageHelper.getMessage(MessageConstants.NAT_ROUTE_CONFIG_NEW_SERVICE_CREATION_FAILED,
                                              route.getRouteId(), e.getMessage()));
            return false;
        }
    }

    private boolean addQueuedRouteToExistingService(final Service service, final NatRoute route) {
        final String correspondingServiceName = getServiceName(service);
        log.info(messageHelper.getMessage(MessageConstants.NAT_ROUTE_CONFIG_ADD_ROUTE_TO_EXISTING_SERVICE,
                                          route.getRouteId(), correspondingServiceName));
        final NatRouteStatus statusInQueue = route.getStatus();
        final Integer externalPort = route.getExternalPort();
        if (NatRouteStatus.CREATION_SCHEDULED.equals(route.getStatus())) {
            final Map<Integer, ServicePort> activePorts = loadServiceActivePortsFromKube(service);
            if (!activePorts.containsKey(externalPort)) {
                final boolean portCreationResult = kubernetesManager.generateFreeTargetPort()
                    .map(newPort -> kubernetesManager.addPortToExistingService(
                        getServiceName(service), externalPort, newPort, route.getProtocol()))
                    .map(Optional::isPresent)
                    .orElse(false);
                if (!portCreationResult) {
                    return false;
                }
            }
        }
        final String targetStatusLabelName = getTargetStatusLabelName(externalPort);
        final NatRouteStatus targetStatusInKube =
            NatRouteStatus.valueOf(extractStringFromAnnotations(service, targetStatusLabelName));
        if (!targetStatusInKube.equals(statusInQueue)) {
            return kubernetesManager.updateAnnotationsOfExistingService(
                correspondingServiceName, getRouteUpdateAnnotations(targetStatusLabelName, statusInQueue, route));
        }
        return true;
    }

    private Map<String, String> getRouteUpdateAnnotations(final String targetStatusLabelName,
                                                          final NatRouteStatus statusInQueue, final NatRoute route) {
        final Integer externalPort = route.getExternalPort();
        final Map<String, String> requiredProxyServiceLabels = new HashMap<>();
        requiredProxyServiceLabels.put(getCurrentStatusLabelName(externalPort), statusInQueue.name());
        requiredProxyServiceLabels.put(getLastUpdateTimeLabelName(externalPort), getCurrentTimeString());
        requiredProxyServiceLabels.put(getProtocolLabelName(externalPort), route.getProtocol());
        requiredProxyServiceLabels.put(getErrorDetailsLabelName(externalPort), null);
        final NatRouteStatus targetStatus = statusInQueue.isTerminationState()
                                            ? NatRouteStatus.TERMINATED
                                            : NatRouteStatus.ACTIVE;
        requiredProxyServiceLabels.put(targetStatusLabelName, targetStatus.name());
        Optional.ofNullable(route.getDescription())
            .ifPresent(description ->
                           requiredProxyServiceLabels.put(getDescriptionLabelName(externalPort), description));
        return requiredProxyServiceLabels;
    }

    private String getCurrentTimeString() {
        return LocalDateTime.now().format(KubernetesConstants.KUBE_LABEL_DATE_FORMATTER);
    }

    private ServicePort buildNewServicePort(final String correspondingServiceName, final Integer externalPort,
                                            final IntOrString freeTargetPort, final String protocol) {
        final ServicePort newServicePort = new ServicePort();
        newServicePort.setProtocol(protocol);
        newServicePort.setName(kubernetesManager.getServicePortName(correspondingServiceName, externalPort));
        newServicePort.setTargetPort(freeTargetPort);
        newServicePort.setPort(externalPort);
        return newServicePort;
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

    private Map<NatRoutingRuleDescription, NatRoute> convertRoutesToMap(final List<NatRoute> natRoutes,
                                                                        final boolean useDescription) {
        return natRoutes.stream().collect(Collectors.toMap(route -> useDescription
                                                                    ? mapRouteToDescription(route)
                                                                    : toRuleWoDescription(mapRouteToDescription(route)),
                                                           Function.identity()));
    }

    private boolean hasTerminatingState(final NatRoute route) {
        final NatRouteStatus status = route.getStatus();
        return status.isTerminationState();
    }

    private List<NatRoutingRuleDescription> validateRequest(final NatRoutingRulesRequest request) {
        final List<NatRoutingRuleDescription> ruleDescriptions = Optional.ofNullable(request)
            .map(NatRoutingRulesRequest::getRules)
            .filter(CollectionUtils::isNotEmpty)
            .orElseThrow(() -> new IllegalArgumentException(messageHelper.getMessage(
                MessageConstants.NAT_ROUTE_CONFIG_ERROR_EMPTY_RULE)));
        return ruleDescriptions.stream()
            .map(this::setProtocolIfMissing)
            .peek(this::validateRuleFields)
            .collect(Collectors.toList());
    }

    private NatRoutingRuleDescription setProtocolIfMissing(final NatRoutingRuleDescription rule) {
        return rule.getProtocol() != null
               ? rule
               : new NatRoutingRuleDescription(rule.getExternalName(), rule.getExternalIp(), rule.getPort(),
                                               rule.getDescription(), TCP);
    }

    private void validateRuleFields(final NatRoutingRuleDescription rule) {
        validateMandatoryRuleField(EXTERNAL_NAME, rule.getExternalName());
        validateMandatoryRuleField(PORT, Optional.ofNullable(rule.getPort()).map(Object::toString).orElse(null));
        validateMandatoryRuleField(EXTERNAL_IP, rule.getExternalIp());
        validateRuleProtocol(rule);
        Optional.ofNullable(rule.getDescription())
            .ifPresent(description -> Assert.isTrue(
                kubernetesManager.isValidAnnotation(description),
                messageHelper.getMessage(MessageConstants.NAT_ROUTE_CONFIG_INVALID_DESCRIPTION, description)));
    }

    private void validateRuleProtocol(final NatRoutingRuleDescription rule) {
        Optional.ofNullable(rule.getProtocol()).ifPresent(protocol -> {
            final Set<String> allowedProtocols = portForwardingRuleMapping.keySet();
            Assert.isTrue(allowedProtocols.contains(protocol),
                          messageHelper.getMessage(MessageConstants.NAT_ROUTE_CONFIG_INVALID_PROTOCOL,
                                                   protocol, allowedProtocols));
        });
    }

    private void validateMandatoryRuleField(final String fieldName, final String value) {
        Assert.isTrue(kubernetesManager.isValidAnnotation(value),
                      messageHelper.getMessage(MessageConstants.NAT_ROUTE_CONFIG_INVALID_MANDATORY_FIELD, fieldName));
    }

    private List<NatRoute> extractNatRoutesFromKubeService(final Service kubeService) {
        final Map<String, String> annotations = getServiceAnnotations(kubeService);
        final Optional<ServiceSpec> serviceSpecs = Optional.of(kubeService).map(Service::getSpec);
        final String internalName = getServiceName(kubeService);
        final String internalIp = serviceSpecs.map(ServiceSpec::getClusterIP).orElse(UNKNOWN);

        return serviceSpecs.map(ServiceSpec::getPorts)
            .map(Collection::stream)
            .orElseGet(Stream::empty)
            .map(port -> buildRouteFromPort(internalName, internalIp, port, annotations))
            .collect(Collectors.toList());
    }

    private NatRoute buildRouteFromPort(final String serviceName, final String internalIp, final ServicePort portInfo,
                                        final Map<String, String> annotations) {
        final Integer externalPort = Optional.ofNullable(portInfo).map(ServicePort::getPort).orElse(null);
        final String statusLabelName = getCurrentStatusLabelName(externalPort);
        final String lastUpdateTimeLabelName = getLastUpdateTimeLabelName(externalPort);
        final String protocolLabelName = getProtocolLabelName(externalPort);
        return NatRoute.builder()
            .externalName(extractStringFromAnnotations(annotations, EXTERNAL_NAME_LABEL))
            .externalIp(extractStringFromAnnotations(annotations, EXTERNAL_IP_LABEL))
            .description(extractStringFromAnnotations(annotations, getDescriptionLabelName(externalPort), null))
            .externalPort(externalPort)
            .protocol(extractStringFromAnnotations(annotations, protocolLabelName, TCP))
            .internalName(serviceName)
            .internalIp(internalIp)
            .internalPort(getInternalPort(portInfo))
            .status(NatRouteStatus.valueOf(extractStringFromAnnotations(annotations, statusLabelName)))
            .lastUpdateTime(tryExtractTimeFromLabels(annotations, lastUpdateTimeLabelName))
            .lastErrorMessage(extractStringFromAnnotations(annotations, getErrorDetailsLabelName(externalPort), null))
            .build();
    }

    private Integer getInternalPort(final ServicePort portInfo) {
        return Optional.ofNullable(portInfo).map(ServicePort::getTargetPort).map(IntOrString::getIntVal).orElse(null);
    }

    private LocalDateTime tryExtractTimeFromLabels(final Map<String, String> serviceLabels, final String labelName) {
        return Optional.ofNullable(serviceLabels.get(labelName))
            .map(value -> {
                try {
                    return LocalDateTime.parse(value, KubernetesConstants.KUBE_LABEL_DATE_FORMATTER);
                } catch (DateTimeParseException e) {
                    return null;
                }
            })
            .orElse(null);
    }

    private boolean removePortFromService(final Service service, final Map<Integer, ServicePort> activePorts,
                                          final Integer port) {
        if (!activePorts.containsKey(port)) {
            return true;
        }
        final String serviceName = getServiceName(service);
        final List<ServicePort> portsUpdate = activePorts.entrySet().stream()
            .filter(entry -> !entry.getKey().equals(port))
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
        return portsUpdate.size() == 0
               ? kubernetesManager.deleteServiceIfExists(serviceName)
               : kubernetesManager.setPortsToService(serviceName, portsUpdate);
    }

    private boolean removeDnsMaks(final Service service, final Map<Integer, ServicePort> activePorts,
                                  final Integer port) {
        if (!activePorts.containsKey(port)) {
            return true;
        }
        if (activePorts.size() > 1) {
            log.info(messageHelper.getMessage(MessageConstants.NAT_ROUTE_REMOVAL_KEEP_DNS_MASK));
            return true;
        }
        final Optional<ConfigMap> dnsMap =
            kubernetesManager.findConfigMap(dnsProxyConfigMapName, KubernetesConstants.SYSTEM_NAMESPACE);
        if (!dnsMap.isPresent()) {
            return false;
        }
        final Set<String> existingDnsRecords = getExistingDnsRecords(dnsMap);

        final String externalName = extractExternalRouteFromService(service);
        final String correspondingDnsRecord = getCorrespondingDnsRecord(externalName, getClusterIP(service));
        if (!existingDnsRecords.contains(correspondingDnsRecord)) {
            return true;
        }
        existingDnsRecords.remove(correspondingDnsRecord);
        return kubernetesManager.updateValueInConfigMap(dnsProxyConfigMapName,
                                                        KubernetesConstants.SYSTEM_NAMESPACE,
                                                        hostsKey,
                                                        convertDnsRecordsListToString(existingDnsRecords));
    }

    private String extractExternalRouteFromService(final Service service) {
        return getServiceAnnotations(service).get(EXTERNAL_NAME_LABEL);
    }

    private String getCorrespondingDnsRecord(final String externalName, final String clusterIP) {
        return clusterIP + " " + externalName;
    }

    private boolean removePortForwardingRule(final Service service, final Map<Integer, ServicePort> activePorts,
                                             final Integer port) {
        if (!activePorts.containsKey(port)) {
            log.warn(messageHelper.getMessage(MessageConstants.NAT_ROUTE_REMOVAL_NO_PORT_SPECIFIED,
                                              port, getServiceName(service)));
            return true;
        }
        final Optional<ConfigMap> globalConfigMap = kubernetesManager.findConfigMap(globalConfigMapName, null);
        if (!globalConfigMap.isPresent()) {
            log.warn(messageHelper.getMessage(MessageConstants.NAT_ROUTE_CONFIG_CANT_FIND_CONFIG_MAP,
                                              globalConfigMapName, getServiceName(service), port));
            return false;
        }
        final String portForwardingRuleKey = getPortForwardingRuleKey(activePorts, port);
        final Set<String> activeRules = getActivePortForwardingRules(globalConfigMap, portForwardingRuleKey);
        final String externalIp = extractStringFromAnnotations(service, EXTERNAL_IP_LABEL);
        final String correspondingPortForwardingRule = getForwardingRule(externalIp, activePorts.get(port));
        if (!activeRules.contains(correspondingPortForwardingRule)) {
            return true;
        }
        activeRules.remove(correspondingPortForwardingRule);
        return kubernetesManager.updateValueInConfigMap(globalConfigMapName,
                                                        null,
                                                        portForwardingRuleKey,
                                                        String.join(Constants.COMMA, activeRules));
    }

    private String getPortForwardingRuleKey(final Map<Integer, ServicePort> activePorts, final Integer port) {
        final String portProtocol = Optional.of(activePorts.get(port))
            .map(ServicePort::getProtocol)
            .orElse(TCP);
        return portForwardingRuleMapping.get(portProtocol);
    }

    private Set<String> getActivePortForwardingRules(final Optional<ConfigMap> globalConfigMap,
                                                     final String portForwardingRuleKey) {
        return globalConfigMap.map(ConfigMap::getData)
            .map(data -> data.get(portForwardingRuleKey))
            .map(portForwardingString -> portForwardingString.split(Constants.COMMA))
            .map(Stream::of)
            .orElse(Stream.empty())
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toSet());
    }

    private boolean addDnsMask(final Service service, final Integer newPort) {
        final Optional<ConfigMap> dnsMap = kubernetesManager.findConfigMap(dnsProxyConfigMapName,
                                                                           KubernetesConstants.SYSTEM_NAMESPACE);
        if (!dnsMap.isPresent()) {
            return false;
        }
        final Set<String> existingDnsRecords = getExistingDnsRecords(dnsMap);
        final String externalName = extractExternalRouteFromService(service);
        final String clusterIP = getClusterIP(service);
        final String requiredDnsRecord = getCorrespondingDnsRecord(externalName, clusterIP);
        if (!existingDnsRecords.contains(requiredDnsRecord)) {
            final Set<String> newDnsRecords = new HashSet<>(existingDnsRecords);
            newDnsRecords.add(requiredDnsRecord);
            kubernetesManager.updateValueInConfigMap(dnsProxyConfigMapName, KubernetesConstants.SYSTEM_NAMESPACE,
                                                     hostsKey, convertDnsRecordsListToString(newDnsRecords));
        }
        if (!checkNewDnsRecord(externalName, clusterIP)) {
            log.error(messageHelper.getMessage(MessageConstants.NAT_ROUTE_CONFIG_UNABLE_TO_RESOLVE_ADDRESS,
                                               externalName));
            kubernetesManager.updateValueInConfigMap(dnsProxyConfigMapName,
                                                     KubernetesConstants.SYSTEM_NAMESPACE,
                                                     hostsKey,
                                                     convertDnsRecordsListToString(existingDnsRecords));
            return false;
        }
        updateStatusForRoutingRule(getServiceName(service), newPort, NatRouteStatus.DNS_CONFIGURED);
        return true;
    }

    private String getClusterIP(final Service service) {
        return Optional.of(service)
            .map(Service::getSpec)
            .map(ServiceSpec::getClusterIP)
            .orElseThrow(() -> new IllegalStateException(
                messageHelper.getMessage(MessageConstants.NAT_SERVICE_CONFIG_ERROR_NO_CLUSTER_IP,
                                         getServiceName(service))));
    }

    private Set<String> getExistingDnsRecords(final Optional<ConfigMap> dnsMap) {
        return dnsMap.map(ConfigMap::getData)
            .map(map -> map.get(hostsKey))
            .map(hostRules -> hostRules.split(NEW_LINE))
            .map(Arrays::asList)
            .orElse(Collections.emptyList())
            .stream()
            .map(StringUtils::trim)
            .filter(StringUtils::isNotEmpty)
            .collect(Collectors.toSet());
    }

    @SneakyThrows
    private boolean checkNewDnsRecord(final String externalName, final String clusterIP) {
        if (!refreshKubeDns()) {
            log.warn(messageHelper.getMessage(MessageConstants.NAT_ROUTE_CONFIG_KUBE_DNS_RESTART_FAILED));
            return false;
        }
        int attempts = preferenceManager.getPreference(SystemPreferences.SYSTEM_NAT_HOST_CHECK_ATTEMPTS);
        final int retry = preferenceManager.getPreference(SystemPreferences.SYSTEM_NAT_HOST_CHECK_RETRY_MS);
        while (attempts > 0) {
            if (isDnsResolved(externalName, clusterIP)) {
                return true;
            }
            Thread.sleep(retry);
            attempts--;
        }
        return false;
    }

    private boolean isDnsResolved(final String externalName, final String clusterIP) {
        final Set<String> resolvedAddresses = resolveNameUsingSystemDefaultDns(externalName);
        return resolvedAddresses.size() == 1 && resolvedAddresses.contains(clusterIP);
    }

    private boolean refreshTinyProxy() {
        return kubernetesManager.refreshDeployment(
            tinyproxyServiceLabelSelector,
            Collections.singletonMap(KubernetesConstants.CP_LABEL_PREFIX + tinyproxyServiceLabelSelector,
                                     KubernetesConstants.TRUE_LABEL_VALUE));
    }

    private boolean refreshKubeDns() {
        return kubernetesManager.refreshDeployment(
            KubernetesConstants.SYSTEM_NAMESPACE, KubernetesConstants.KUBE_DNS_APP,
            Collections.singletonMap(KubernetesConstants.KUBERNETES_APP_LABEL, KubernetesConstants.KUBE_DNS_APP));
    }

    private String convertDnsRecordsListToString(final Set<String> dnsRecords) {
        return String.join(NEW_LINE, dnsRecords);
    }

    private boolean addPortForwardingRule(final Service service, final Map<Integer, ServicePort> activePorts,
                                          final Integer port) {

        if (!activePorts.containsKey(port)) {
            log.warn(messageHelper.getMessage(MessageConstants.NAT_ROUTE_CONFIG_CANT_FIND_PORT,
                                              port, getServiceName(service)));
            return false;
        }
        final Optional<ConfigMap> globalConfigMap = kubernetesManager.findConfigMap(globalConfigMapName, null);
        if (!globalConfigMap.isPresent()) {
            return false;
        }
        final String portForwardingRuleKey = getPortForwardingRuleKey(activePorts, port);
        final Set<String> portForwardingRules = getActivePortForwardingRules(globalConfigMap, portForwardingRuleKey);
        final String externalIp = getServiceAnnotations(service).get(EXTERNAL_IP_LABEL);
        final String newPortForwardingRule = getForwardingRule(externalIp, activePorts.get(port));
        if (portForwardingRules.contains(newPortForwardingRule)) {
            return true;
        }
        portForwardingRules.add(newPortForwardingRule);
        final boolean portForwardingCreationResult = kubernetesManager.updateValueInConfigMap(
            globalConfigMapName,
            null,
            portForwardingRuleKey,
            String.join(Constants.COMMA, portForwardingRules));
        if (portForwardingCreationResult) {
            updateStatusForRoutingRule(getServiceName(service), port, NatRouteStatus.PORT_FORWARDING_CONFIGURED);
            return true;
        }
        return false;
    }

    private String getForwardingRule(final String externalIp, final ServicePort correspondingPort) {
        return String.join(PORT_FORWARDING_RULE_ATTRIBUTE_DESCRIPTOR,
                           correspondingPort.getTargetPort().getIntVal().toString(),
                           externalIp,
                           correspondingPort.getPort().toString());
    }

    private boolean addPortToService(final String serviceName, final Map<Integer, ServicePort> activePorts,
                                     final Integer port, final String protocol) {
        if (!activePorts.containsKey(port)) {
            final Optional<Integer> newPort = kubernetesManager.generateFreeTargetPort();
            if (!newPort.isPresent()) {
                return false;
            }
            final Optional<ServicePort> portAddingResult =
                kubernetesManager.addPortToExistingService(serviceName, port, newPort.get(), protocol);
            portAddingResult.ifPresent(servicePort -> {
                updateStatusForRoutingRule(serviceName, port, NatRouteStatus.SERVICE_CONFIGURED);
                activePorts.put(servicePort.getPort(), servicePort);
            });
            return portAddingResult.isPresent();
        }
        return true;
    }

    private boolean setStatusFailed(final String serviceName, final Integer externalPort, final String errorCause) {
        log.error(messageHelper.getMessage(MessageConstants.NAT_ROUTE_CONFIG_FAILURE_SUMMARY,
                                           serviceName, externalPort, errorCause));
        updateStatusForRoutingRule(serviceName, externalPort, NatRouteStatus.TERMINATING, errorCause);
        return false;
    }

    private void updateStatusForRoutingRule(final String serviceName, final Integer externalPort,
                                            final NatRouteStatus status) {
        updateStatusForRoutingRule(serviceName, externalPort, status, null);
    }

    private void updateStatusForRoutingRule(final String serviceName, final Integer externalPort,
                                            final NatRouteStatus status, final String errorMessage) {
        final Map<String, String> annotations = new HashMap<>();
        annotations.put(getCurrentStatusLabelName(externalPort), status.name());
        annotations.put(getLastUpdateTimeLabelName(externalPort), getCurrentTimeString());
        annotations.put(getErrorDetailsLabelName(externalPort), null);
        if (errorMessage != null) {
            annotations.put(getErrorDetailsLabelName(externalPort), errorMessage);
            annotations.put(getTargetStatusLabelName(externalPort), NatRouteStatus.TERMINATED.name());
        }
        kubernetesManager.updateAnnotationsOfExistingService(serviceName, annotations);
    }

    private String extractStringFromAnnotations(final Map<String, String> serviceAnnotations, final String labelName) {
        return extractStringFromAnnotations(serviceAnnotations, labelName, UNKNOWN);
    }

    private String extractStringFromAnnotations(final Map<String, String> serviceAnnotations, final String labelName,
                                                final String defaultValue) {
        return Optional.ofNullable(serviceAnnotations)
            .map(annotations -> annotations.get(labelName))
            .map(StringUtils::trimToNull)
            .orElse(defaultValue);
    }

    private String extractStringFromAnnotations(final Service service, final String labelName) {
        return extractStringFromAnnotations(getServiceAnnotations(service), labelName);
    }

    private String getProtocolLabelName(final Integer port) {
        return NAT_ROUTE_LABELS_PREFIX + "protocol-" + port;
    }

    private String getCurrentStatusLabelName(final Integer port) {
        return NAT_ROUTE_LABELS_PREFIX + "current-status-" + port;
    }

    private String getErrorDetailsLabelName(final Integer port) {
        return NAT_ROUTE_LABELS_PREFIX + "error-message-" + port;
    }

    private String getTargetStatusLabelName(final Integer port) {
        return TARGET_STATUS_LABEL_PREFIX + port;
    }

    private String getLastUpdateTimeLabelName(final Integer port) {
        return NAT_ROUTE_LABELS_PREFIX + "last-update-" + port;
    }

    private String getDescriptionLabelName(final Integer port) {
        return NAT_ROUTE_LABELS_PREFIX + "description-" + port;
    }
}
