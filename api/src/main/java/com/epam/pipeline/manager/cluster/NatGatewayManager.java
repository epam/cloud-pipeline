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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.Constants;
import com.epam.pipeline.dao.cluster.NatGatewayDao;
import com.epam.pipeline.entity.cluster.nat.NatRoute;
import com.epam.pipeline.entity.cluster.nat.NatRouteStatus;
import com.epam.pipeline.entity.cluster.nat.NatRoutingRuleDescription;
import com.epam.pipeline.entity.cluster.nat.NatRoutingRulesRequest;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss.SSSSSS");
    private static final String NEW_LINE = "\n";
    private static final long DEPLOYMENT_REFRESH_TIMEOUT_SEC = 3;
    private static final int DEPLOYMENT_REFRESH_RETRIES = 10;
    private static final String PORT_FORWARDING_RULE_ATTRIBUTE_DESCRIPTOR = ":";
    private static final String HYPHEN = "-";

    private final NatGatewayDao natGatewayDao;
    private final KubernetesManager kubernetesManager;
    private final MessageHelper messageHelper;
    private final String tinyproxyServiceName;
    private final String dnsProxyConfigMapName;
    private final String globalConfigMapName;
    private final String portForwardingRuleKey;
    private final String hostsKey;

    public NatGatewayManager(@Autowired final NatGatewayDao natGatewayDao,
                             @Autowired final KubernetesManager kubernetesManager,
                             @Autowired final MessageHelper messageHelper,
                             @Value("${nat.gateway.cp.core.service.name:cp-tinyproxy}")
                             final String tinyproxyServiceName,
                             @Value("${nat.gateway.cm.dns.proxy.name:cp-dnsmasq-hosts}")
                             final String dnsProxyConfigMapName,
                             @Value("${nat.gateway.cm.global.name:cp-config-global}")
                             final String globalConfigMapName,
                             @Value("${nat.gateway.port.forwarding.key:CP_TP_TCP_DEST}")
                             final String portForwardingRuleKey,
                             @Value("${nat.gateway.hosts.key:hosts}") final String hostsKey) {
        this.natGatewayDao = natGatewayDao;
        this.kubernetesManager = kubernetesManager;
        this.messageHelper = messageHelper;
        this.tinyproxyServiceName = tinyproxyServiceName;
        this.dnsProxyConfigMapName = dnsProxyConfigMapName;
        this.globalConfigMapName = globalConfigMapName;
        this.portForwardingRuleKey = portForwardingRuleKey;
        this.hostsKey = hostsKey;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    @SchedulerLock(name = "NatGatewayManager_updateStatus", lockAtMostForString = "PT5M")
    @Scheduled(fixedDelayString = "${nat.gateway.auto.config.poll:60000}")
    public void updateStatus() {
        moveQueuedRoutesToKube();
        processRoutesInKube();
    }

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
        return updateRoutesInDatabase(request, NatRouteStatus.CREATION_SCHEDULED, route -> true);
    }

    public List<NatRoute> loadAllRoutes() {
        final Map<NatRoutingRuleDescription, NatRoute> routeMap =
            convertRoutesToMap(loadAllActiveRoutesFromKubernetes());
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
        return updateRoutesInDatabase(request, NatRouteStatus.TERMINATION_SCHEDULED, this::hasTerminatingState);
    }

    private void moveQueuedRoutesToKube() {
        final List<NatRoute> allQueuedRoutes = natGatewayDao.loadQueuedRouteUpdates();
        final Map<String, Service> proxyServicesMapping = loadProxyServicesFromKube().stream()
            .collect(Collectors.toMap(this::getServiceName, Function.identity()));
        allQueuedRoutes.forEach(route -> addQueuedRouteToKubeServices(proxyServicesMapping, route));
    }

    private void processRoutesInKube() {
        loadProxyServicesFromKube().forEach(service -> {
            final Map<String, String> labels = getServiceLabels(service);
            final List<Integer> allExternalPorts = getExternalPortsSpecifiedInLabels(labels);
            if (CollectionUtils.isEmpty(allExternalPorts)) {
                kubernetesManager.deleteServiceIfExists(getServiceName(service));
            } else {
                allExternalPorts.forEach(port -> processExternalPort(service, labels, port));
            }
        });
    }

    private void processExternalPort(final Service service, final Map<String, String> labels, final Integer port) {
        final Map<Integer, ServicePort> activePorts = service.getSpec().getPorts().stream()
            .collect(Collectors.toMap(ServicePort::getPort, Function.identity()));
        final String targetStatus = labels.get(getTargetStatusLabelName(port));
        final String currentStatus = labels.get(getCurrentStatusLabelName(port));
        if (!targetStatus.equals(currentStatus)) {
            if (targetStatus.equals(NatRouteStatus.ACTIVE.name())) {
                processScheduledStartup(service, activePorts, port);
            } else {
                processScheduledTermination(service, activePorts, port);
            }
        }
    }

    private Map<String, String> getServiceLabels(final Service service) {
        return service.getMetadata().getLabels();
    }

    private String getServiceName(final Service service) {
        return service.getMetadata().getName();
    }

    private List<Integer> getExternalPortsSpecifiedInLabels(final Map<String, String> labels) {
        return labels.keySet().stream()
            .filter(key -> key.startsWith(TARGET_STATUS_LABEL_PREFIX))
            .map(key -> key.substring(TARGET_STATUS_LABEL_PREFIX.length()))
            .map(Integer::valueOf)
            .collect(Collectors.toList());
    }

    private boolean processScheduledTermination(final Service service, final Map<Integer, ServicePort> activePorts,
                                                final Integer port) {
        final String serviceName = getServiceName(service);
        updateStatusForRoutingRule(serviceName, port, NatRouteStatus.TERMINATING);
        if (removePortForwardingRule(service, activePorts, port)) {
            if (removeDnsMaks(service, activePorts, port)) {
                if (removePortFromService(service, activePorts, port)) {
                    if (activePorts.containsKey(port)) {
                        if (!kubernetesManager.refreshCloudPipelineServiceDeployment(tinyproxyServiceName,
                                                                                     DEPLOYMENT_REFRESH_RETRIES,
                                                                                     DEPLOYMENT_REFRESH_TIMEOUT_SEC)) {
                            return false;
                        } else {
                            setStatusFailed(serviceName, port, messageHelper.getMessage(
                                MessageConstants.NAT_ROUTE_REMOVAL_DEPLOYMENT_REFRESH_FAILED));
                        }
                    }
                    return removeStatusLabels(service, port);
                } else {
                    setStatusFailed(serviceName, port, messageHelper.getMessage(
                        MessageConstants.NAT_ROUTE_REMOVAL_PORT_REMOVAL_FAILED));
                }
            } else {
                setStatusFailed(serviceName, port, messageHelper.getMessage(
                    MessageConstants.NAT_ROUTE_REMOVAL_DNS_MASK_REMOVAL_FAILED));
            }
        } else {
            setStatusFailed(serviceName, port, messageHelper.getMessage(
                MessageConstants.NAT_ROUTE_REMOVAL_PORT_FORWARDING_REMOVAL_FAILED));
        }
        return false;
    }

    private boolean removeStatusLabels(final Service service, final Integer port) {
        final HashSet<String> labelsToRemove =
            new HashSet<>(Arrays.asList(getCurrentStatusLabelName(port), getTargetStatusLabelName(port)));
        return kubernetesManager.removeLabelsFromExistingService(getServiceName(service), labelsToRemove);
    }

    private boolean processScheduledStartup(final Service service, final Map<Integer, ServicePort> activePorts,
                                            final Integer port) {
        final String serviceName = getServiceName(service);
        final boolean portIsAssigned = addPortToService(serviceName, activePorts, port);
        if (portIsAssigned) {
            if (addDnsMask(service, port)) {
                if (addPortForwardingRule(service, activePorts, port)) {
                    if (tryRefreshDeployment(serviceName, port)) {
                        return true;
                    }
                    removePortForwardingRule(service, activePorts, port);
                } else {
                    setStatusFailed(serviceName, port, messageHelper.getMessage(
                        MessageConstants.NAT_ROUTE_CONFIG_PORT_FORWARDING_FAILED));
                }
                removeDnsMaks(service, activePorts, port);
            } else {
                setStatusFailed(serviceName, port, messageHelper.getMessage(
                    MessageConstants.NAT_ROUTE_CONFIG_DNS_CREATION_FAILED));
            }
            removePortFromService(service, activePorts, port);
            kubernetesManager.refreshCloudPipelineServiceDeployment(tinyproxyServiceName,
                                                                    DEPLOYMENT_REFRESH_RETRIES,
                                                                    DEPLOYMENT_REFRESH_TIMEOUT_SEC);
        } else {
            setStatusFailed(serviceName, port, messageHelper.getMessage(
                MessageConstants.NAT_ROUTE_CONFIG_PORT_ASSIGNING_FAILED));
        }
        return false;
    }

    private boolean tryRefreshDeployment(final String serviceName, final Integer port) {
        if (kubernetesManager.refreshCloudPipelineServiceDeployment(tinyproxyServiceName,
                                                                    DEPLOYMENT_REFRESH_RETRIES,
                                                                    DEPLOYMENT_REFRESH_TIMEOUT_SEC)) {
            updateStatusForRoutingRule(serviceName, port, NatRouteStatus.ACTIVE);
            return true;
        } else {
            setStatusFailed(serviceName, port, messageHelper.getMessage(
                MessageConstants.NAT_ROUTE_CONFIG_DEPLOYMENT_REFRESH_FAILED));
        }
        return false;
    }

    private List<NatRoute> updateRoutesInDatabase(final NatRoutingRulesRequest request, final NatRouteStatus status,
                                                  final Predicate<NatRoute> additionalFilter) {
        final List<NatRoutingRuleDescription> requestedRules = validateRequestIsNotEmpty(request);
        final List<NatRoute> updatedRoutes = updateStatusForQueuedRoutes(requestedRules, status);
        final Set<NatRoutingRuleDescription> existingRules = getActiveNatRules(additionalFilter);
        final List<NatRoutingRuleDescription> newRules = requestedRules.stream()
            .filter(rule -> !existingRules.contains(rule))
            .collect(Collectors.toList());
        final List<NatRoute> queuedRoutes = CollectionUtils.isNotEmpty(newRules)
                                            ? natGatewayDao.registerRoutingRules(newRules, status)
                                            : Collections.emptyList();
        updatedRoutes.addAll(queuedRoutes);
        return updatedRoutes;
    }

    private Collection<Service> loadProxyServicesFromKube() {
        return CollectionUtils.emptyIfNull(kubernetesManager.getCloudPipelineServiceInstances(tinyproxyServiceName));
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


    private void addQueuedRouteToKubeServices(final Map<String, Service> proxyServicesMapping, final NatRoute route) {
        final String correspondingServiceName = getProxyServiceName(route.getExternalName());
        final NatRouteStatus statusInQueue = route.getStatus();
        if (proxyServicesMapping.containsKey(correspondingServiceName)) {
            if (!addQueuedRouteToExistingService(proxyServicesMapping.get(correspondingServiceName), route)) {
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

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private boolean createServiceForQueuedRoute(final Map<String, Service> proxyServicesMapping, final NatRoute route) {
        final Optional<Integer> freeTargetPort = kubernetesManager.generateFreeTargetPort();
        if (!freeTargetPort.isPresent()) {
            return false;
        }
        final String correspondingServiceName = getProxyServiceName(route.getExternalName());
        final NatRouteStatus statusInQueue = route.getStatus();
        final Integer externalPort = route.getExternalPort();
        final String targetStatusLabelName = getTargetStatusLabelName(externalPort);
        final Map<String, String> serviceLabels = getRouteUpdateLabels(targetStatusLabelName, statusInQueue, route);
        final List<ServicePort> servicePorts =
            Collections.singletonList(buildNewServicePort(correspondingServiceName, externalPort,
                                                          freeTargetPort.map(IntOrString::new).get()));
        final Map<String, String> selector =
            Collections.singletonMap(KubernetesConstants.CP_LABEL_PREFIX + tinyproxyServiceName,
                                     KubernetesConstants.TRUE_LABEL_VALUE);
        try {
            proxyServicesMapping.put(correspondingServiceName,
                                     kubernetesManager.createService(correspondingServiceName, serviceLabels,
                                                                     servicePorts, selector));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean addQueuedRouteToExistingService(final Service service, final NatRoute route) {
        final String correspondingServiceName = getProxyServiceName(route.getExternalName());
        final NatRouteStatus statusInQueue = route.getStatus();
        final Integer externalPort = route.getExternalPort();
        final String targetStatusLabelName = getTargetStatusLabelName(externalPort);
        final NatRouteStatus targetStatusInKube =
            NatRouteStatus.valueOf(extractStringFromLabels(service, targetStatusLabelName));
        if (!targetStatusInKube.equals(statusInQueue)) {
            return kubernetesManager.updateLabelsOfExistingService(
                correspondingServiceName, getRouteUpdateLabels(targetStatusLabelName, statusInQueue, route));
        }
        return true;
    }

    private Map<String, String> getRouteUpdateLabels(final String targetStatusLabelName,
                                                     final NatRouteStatus statusInQueue, final NatRoute route) {
        final Integer externalPort = route.getExternalPort();
        final Map<String, String> requiredProxyServiceLabels = new HashMap<>();
        requiredProxyServiceLabels.put(EXTERNAL_NAME_LABEL, route.getExternalName());
        requiredProxyServiceLabels.put(EXTERNAL_IP_LABEL, route.getExternalIp());
        requiredProxyServiceLabels.put(getCurrentStatusLabelName(externalPort), statusInQueue.name());
        requiredProxyServiceLabels.put(KubernetesConstants.CP_LABEL_PREFIX + tinyproxyServiceName,
                                       KubernetesConstants.TRUE_LABEL_VALUE);
        requiredProxyServiceLabels.put(getLastUpdateTimeLabelName(externalPort), getCurrentTimeString());
        final NatRouteStatus targetStatus = statusInQueue.isTerminationState()
                                            ? NatRouteStatus.TERMINATED
                                            : NatRouteStatus.ACTIVE;
        requiredProxyServiceLabels.put(targetStatusLabelName, targetStatus.name());
        return requiredProxyServiceLabels;
    }

    private String getCurrentTimeString() {
        return LocalDateTime.now().format(DATE_FORMATTER);
    }

    private ServicePort buildNewServicePort(final String correspondingServiceName, final Integer externalPort,
                                            final IntOrString freeTargetPort) {
        final ServicePort newServicePort = new ServicePort();
        newServicePort.setName(
            String.join(HYPHEN, tinyproxyServiceName, correspondingServiceName, externalPort.toString()));
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
            .orElseThrow(() -> new IllegalArgumentException(messageHelper.getMessage(
                MessageConstants.NAT_ROUTE_CONFIG_ERROR_EMPTY_RULE)));
    }

    private List<NatRoute> extractNatRoutesFromKubeService(final Service kubeService) {
        final ObjectMeta serviceMetadata = kubeService.getMetadata();
        final Map<String, String> serviceLabels = serviceMetadata.getLabels();
        final ServiceSpec serviceSpecs = kubeService.getSpec();
        final String externalName = extractStringFromLabels(serviceLabels, EXTERNAL_NAME_LABEL);
        final String externalIp = extractStringFromLabels(serviceLabels, EXTERNAL_IP_LABEL);
        final String internalName = serviceMetadata.getName();
        final String internalIp = serviceSpecs.getClusterIP();

        return CollectionUtils.emptyIfNull(serviceSpecs.getPorts())
            .stream()
            .map(port -> buildRouteFromPort(port, serviceLabels, externalName, externalIp, internalName, internalIp))
            .collect(Collectors.toList());
    }

    private NatRoute buildRouteFromPort(final ServicePort portInfo, final Map<String, String> serviceLabels,
                                        final String externalName, final String externalIp, final String serviceName,
                                        final String internalIp) {
        final Integer externalPort = portInfo.getPort();
        final String statusLabelName = getCurrentStatusLabelName(externalPort);
        final String lastUpdateTimeLabelName = getLastUpdateTimeLabelName(externalPort);
        return NatRoute.builder()
            .externalName(externalName)
            .externalIp(externalIp)
            .externalPort(externalPort)
            .internalName(serviceName)
            .internalIp(internalIp)
            .internalPort(portInfo.getTargetPort().getIntVal())
            .status(NatRouteStatus.valueOf(extractStringFromLabels(serviceLabels, statusLabelName)))
            .lastUpdateTime(tryExtractTimeFromLabels(serviceLabels, lastUpdateTimeLabelName))
            .build();
    }

    private LocalDateTime tryExtractTimeFromLabels(final Map<String, String> serviceLabels, final String labelName) {
        return Optional.ofNullable(serviceLabels.get(labelName))
            .map(value -> {
                try {
                    return LocalDateTime.parse(value, DATE_FORMATTER);
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
        return activePorts.size() == 1
               ? kubernetesManager.deleteServiceIfExists(serviceName)
               : kubernetesManager.removePortFromExistingService(serviceName, activePorts.get(port).getName());
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

        final String externalName = getServiceLabels(service).get(EXTERNAL_NAME_LABEL);
        final String clusterIP = service.getSpec().getClusterIP();
        final String correspondingDnsRecord = getCorrespondingDnsRecord(externalName, clusterIP);
        if (!existingDnsRecords.contains(correspondingDnsRecord)) {
            return true;
        }
        existingDnsRecords.remove(correspondingDnsRecord);
        return kubernetesManager.updateValueInConfigMap(dnsProxyConfigMapName,
                                                        KubernetesConstants.SYSTEM_NAMESPACE,
                                                        hostsKey,
                                                        convertDnsRecordsListToString(existingDnsRecords));
    }

    private String getCorrespondingDnsRecord(final String externalName, final String clusterIP) {
        return clusterIP + " " + externalName;
    }

    private boolean removePortForwardingRule(final Service service, final Map<Integer, ServicePort> activePorts,
                                             final Integer port) {
        if (!activePorts.containsKey(port)) {
            log.warn("No active port {} is  defined for {}", port, getServiceName(service));
            return true;
        }
        final Optional<ConfigMap> globalConfigMap = kubernetesManager.findConfigMap(globalConfigMapName, null);
        if (!globalConfigMap.isPresent()) {
            log.warn(messageHelper.getMessage(MessageConstants.NAT_ROUTE_CONFIG_CANT_FIND_CONFIG_MAP,
                                              globalConfigMapName, getServiceName(service), port));
            return false;
        }
        final Set<String> activeRules = getActivePortForwardingRules(globalConfigMap);
        final String externalIp = getServiceLabels(service).get(EXTERNAL_IP_LABEL);
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

    private HashSet<String> getActivePortForwardingRules(final Optional<ConfigMap> globalConfigMap) {
        return globalConfigMap.map(ConfigMap::getData)
            .map(data -> data.get(portForwardingRuleKey))
            .map(portForwardingString -> portForwardingString.split(Constants.COMMA))
            .map(Arrays::asList)
            .map(HashSet::new)
            .orElse(new HashSet<>());
    }

    private boolean addDnsMask(final Service service, final Integer newPort) {
        final Optional<ConfigMap> dnsMap = kubernetesManager.findConfigMap(dnsProxyConfigMapName,
                                                                           KubernetesConstants.SYSTEM_NAMESPACE);
        if (!dnsMap.isPresent()) {
            return false;
        }
        final Set<String> existingDnsRecords = getExistingDnsRecords(dnsMap);
        final String externalName = getServiceLabels(service).get(EXTERNAL_NAME_LABEL);
        final String clusterIP = service.getSpec().getClusterIP();
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

    private boolean checkNewDnsRecord(final String externalName, final String clusterIP) {
        final Set<String> resolvedAddresses = resolveAddress(externalName);
        return resolvedAddresses.size() == 1 && resolvedAddresses.contains(clusterIP);
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
        final Set<String> portForwardingRules = getActivePortForwardingRules(globalConfigMap);
        final String externalIp = getServiceLabels(service).get(EXTERNAL_IP_LABEL);
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
                                     final Integer port) {
        if (!activePorts.containsKey(port)) {
            final Optional<Integer> newPort = kubernetesManager.generateFreeTargetPort();
            if (!newPort.isPresent()) {
                return false;
            }
            final Optional<ServicePort> portAddingResult =
                kubernetesManager.addPortToExistingService(serviceName, port, newPort.get());
            portAddingResult.ifPresent(servicePort -> {
                updateStatusForRoutingRule(serviceName, port, NatRouteStatus.SERVICE_CONFIGURED);
                activePorts.put(servicePort.getPort(), servicePort);
            });
            return portAddingResult.isPresent();
        }
        return true;
    }

    private void setStatusFailed(final String serviceName, final Integer externalPort, final String errorCause) {
        updateStatusForRoutingRule(serviceName, externalPort, NatRouteStatus.TERMINATING, errorCause);
    }

    private void updateStatusForRoutingRule(final String serviceName, final Integer externalPort,
                                            final NatRouteStatus status) {
        updateStatusForRoutingRule(serviceName, externalPort, status, null);
    }

    private void updateStatusForRoutingRule(final String serviceName, final Integer externalPort,
                                            final NatRouteStatus status, final String errorMessage) {
        final Map<String, String> labels = new HashMap<>();
        labels.put(getCurrentStatusLabelName(externalPort), status.name());
        labels.put(getLastUpdateTimeLabelName(externalPort), getCurrentTimeString());
        if (errorMessage != null) {
            labels.put(getErrorDetailsLabelName(externalPort), errorMessage);
            labels.put(getTargetStatusLabelName(externalPort), NatRouteStatus.TERMINATED.name());
        }
        kubernetesManager.updateLabelsOfExistingService(serviceName, labels);
    }

    private String extractStringFromLabels(final Map<String, String> serviceLabels, final String labelName) {
        return Optional.ofNullable(serviceLabels.get(labelName)).orElse(UNKNOWN);
    }

    private String extractStringFromLabels(final Service service, final String labelName) {
        return Optional.ofNullable(getServiceLabels(service).get(labelName)).orElse(UNKNOWN);
    }

    public String getProxyServiceName(final String externalName) {
        return tinyproxyServiceName + HYPHEN + externalName.replaceAll("\\.", HYPHEN);
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
}
