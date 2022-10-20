/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
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
 *
 */

package com.epam.pipeline.vmmonitor.service.vm;

import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.exception.PipelineResponseException;
import com.epam.pipeline.vmmonitor.model.vm.VirtualMachine;
import com.epam.pipeline.vmmonitor.service.Monitor;
import com.epam.pipeline.vmmonitor.service.pipeline.CloudPipelineAPIClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Main monitoring service class, checks VM status on a scheduled basis
 */
@Slf4j
@Service
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class VMMonitor implements Monitor {

    private static final String POOL_RUN_ID_PREFIX = "p-";
    private static final int SECONDS_IN_MINUTES = 60;

    private final CloudPipelineAPIClient apiClient;
    private final VMNotifier notifier;
    private final Map<CloudProvider, VMMonitorService> services;
    private final List<String> requiredLabels;
    private final String runIdLabel;
    private final String poolIdLabel;
    private final long vmMaxLiveMinutes;

    public VMMonitor(final CloudPipelineAPIClient apiClient,
                     final VMNotifier notifier,
                     final List<VMMonitorService> services,
                     @Value("${monitor.required.labels:}") final String requiredLabels,
                     @Value("${monitor.runid.label:}") final String runIdLabel,
                     @Value("${monitor.poolid.label:}") final String poolIdLabel,
                     @Value("${monitor.vm.max.live.minutes:60}") final long vmMaxLiveMinutes) {
        this.apiClient = apiClient;
        this.notifier = notifier;
        this.services = ListUtils.emptyIfNull(services).stream()
                .collect(Collectors.toMap(VMMonitorService::provider, Function.identity()));
        this.requiredLabels = Arrays.asList(requiredLabels.split(","));
        this.runIdLabel = runIdLabel;
        this.poolIdLabel = poolIdLabel;
        this.vmMaxLiveMinutes = vmMaxLiveMinutes;
    }

    @Override
    public void monitor() {
        try {
            ListUtils.emptyIfNull(apiClient.loadRegions())
                    .forEach(this::checkVMs);
        } finally {
            notifier.sendNotifications();
        }
    }

    @SuppressWarnings("unchecked")
    private void checkVMs(final AbstractCloudRegion region) {
        log.debug("Checking VMs in region {} {}", region.getRegionCode(), region.getProvider());
        getVmService(region)
                .ifPresent(service -> {
                    final List<VirtualMachine> vms = ListUtils.emptyIfNull(service.fetchRunningVms(region));
                    log.debug("Found {} running VM(s) in {} {}", vms.size(),
                            region.getRegionCode(), region.getProvider());
                    vms.forEach(this::checkVmState);
                });
    }

    private Optional<VMMonitorService> getVmService(final AbstractCloudRegion region) {
        final CloudProvider provider = region.getProvider();
        if (!services.containsKey(provider)) {
            log.error("Skipping unsupported cloud provider {}", provider);
            return Optional.empty();
        }
        return Optional.of(services.get(provider));
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void checkVmState(final VirtualMachine vm) {
        try {
            final List<NodeInstance> nodes = apiClient.findNodes(vm.getPrivateIp());
            if (CollectionUtils.isNotEmpty(nodes)) {
                log.debug("Found {} node(s) matching VM {} {}", nodes.size(),
                        vm.getInstanceId(), vm.getCloudProvider());
                checkMatchingNodes(nodes, vm);
            } else {
                log.debug("No matching nodes were found for VM {} {}.", vm.getInstanceId(), vm.getCloudProvider());
                if (!matchingRunExists(vm) && !checkVMPoolNode(vm)) {
                    notifier.queueMissingNodeNotification(vm);
                }
            }
        } catch (Exception e) {
            log.error("An error occurred during checking state of VM {} {}: {}",
                    vm.getInstanceId(), vm.getCloudProvider(), e.getMessage());
        }
    }

    private boolean matchingRunExists(final VirtualMachine vm) {
        log.debug("Checking whether a run exists matching instance.");
        final String runIdValue = MapUtils.emptyIfNull(vm.getTags()).get(runIdLabel);
        if (StringUtils.isNotBlank(runIdValue) && NumberUtils.isDigits(runIdValue)) {
            final long runId = Long.parseLong(runIdValue);
            log.debug("VM {} {} is associated with run id {}. Checking run status.",
                    vm.getInstanceId(), vm.getCloudProvider(), runId);
            return isRunActive(vm, runId);
        }
        return false;
    }

    private boolean poolIdExists(final VirtualMachine vm, final NodeInstance node) {
        log.debug("Checking whether a node pool with corresponding pool id exists.");
        final String poolIdValue = MapUtils.emptyIfNull(node.getLabels()).get(poolIdLabel);
        if (StringUtils.isNotBlank(poolIdValue) && NumberUtils.isDigits(poolIdValue)) {
            final long poolId = Long.parseLong(poolIdValue);
            log.debug("NodeInstance {} {} is associated with pool id {}. Checking node pool existence.",
                    node.getUid(), node.getClusterName(), poolId);
            return isNodePoolExists(poolId);
        }
        return checkVMPoolNode(vm);
    }

    private boolean checkVMPoolNode(final VirtualMachine vm) {
        log.debug("Checking whether VM {} is created for some node pool.", vm.getInstanceId());
        final String runIdValue = MapUtils.emptyIfNull(vm.getTags()).get(runIdLabel);
        if (StringUtils.isNotBlank(runIdValue) && runIdValue.startsWith(POOL_RUN_ID_PREFIX)) {
            log.debug("VM {} is labeled with {} and it possibly matches a node pool.",
                    vm.getInstanceId(), runIdValue);
            final LocalDateTime created = vm.getCreated();
            if (created == null) {
                return false;
            }
            final long minutesAlive = Duration.between(created, LocalDateTime.now(Clock.systemUTC()))
                    .getSeconds() / SECONDS_IN_MINUTES;
            log.debug("VM {} was launched at {} and is alive for {} minutes",
                    vm.getInstanceId(), created, minutesAlive);
            return minutesAlive <= vmMaxLiveMinutes;
        }
        return false;
    }

    private boolean isNodePoolExists(final long poolId) {
        final List<NodePool> nodePools = apiClient.loadNodePools();
        return nodePools.stream().map(NodePool::getId).collect(Collectors.toList()).contains(poolId);
    }

    private boolean isRunActive(final VirtualMachine vm, final long runId) {
        try {
            final PipelineRun run = apiClient.loadRun(runId);
            if (run.getStatus().isFinal()) {
                log.debug("Run {} is in final status, but VM {} is still up.", runId, vm.getInstanceId());
                return false;
            }
            return true;
        } catch (PipelineResponseException e) {
            log.error("Failed to load run {}: {}", runId, e.getMessage());
            return false;
        }
    }

    private void checkMatchingNodes(final List<NodeInstance> nodes,
                                    final VirtualMachine vm) {
        nodes.forEach(node -> checkLabels(node, vm));
    }

    private void checkLabels(final NodeInstance node, final VirtualMachine vm) {
        log.debug("Checking status of node {} for VM {} {}", node.getName(), vm.getInstanceId(), vm.getCloudProvider());
        if (matchingRunExists(vm) || poolIdExists(vm, node)) {
            return;
        }
        log.debug("Checking whether node {} is labeled with required tags.", node.getName());
        final List<String> labels = getMissingLabels(node);
        if (CollectionUtils.isNotEmpty(labels)) {
            notifier.queueMissingLabelsNotification(node, labels);
        } else {
            log.debug("All required labels are present on node {}.", node.getName());
        }
    }

    private List<String> getMissingLabels(final NodeInstance node) {
        return requiredLabels.stream()
                .filter(label -> {
                    final Map<String, String> nodeLabels = MapUtils.emptyIfNull(node.getLabels());
                    return !nodeLabels.containsKey(label) || StringUtils.isBlank(nodeLabels.get(label));
                })
                .collect(Collectors.toList());
    }
}
