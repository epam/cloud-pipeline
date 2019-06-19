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

package com.epam.pipeline.manager.cloud.azure;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.cloud.azure.AzureVirtualMachineStats;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.exception.cloud.azure.AzureException;
import com.microsoft.azure.Page;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.InstanceViewStatus;
import com.microsoft.azure.management.compute.PowerState;
import com.microsoft.azure.management.compute.VirtualMachine;
import com.microsoft.azure.management.compute.VirtualMachineInstanceView;
import com.microsoft.azure.management.compute.VirtualMachineScaleSet;
import com.microsoft.azure.management.compute.VirtualMachineScaleSetVM;
import com.microsoft.azure.management.network.NetworkInterface;
import com.microsoft.azure.management.resources.GenericResource;
import com.microsoft.azure.management.resources.fluentcore.arm.models.Resource;
import com.microsoft.rest.LogLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@RequiredArgsConstructor
@Slf4j
@Service
public class AzureVMService {

    private static final String VM_FAILED_STATE = "ProvisioningState/failed";
    private static final String TAG_NAME = "Name";
    private static final String VIRTUAL_MACHINE_PREFIX = "virtualMachine";
    private static final String VIRTUAL_MACHINES_TYPE = "virtualMachines";
    private static final String VIRTUAL_MACHINE_SCALE_SET_TYPE = "virtualMachineScaleSets";
    private static final String NETWORK_INTERFACES = "networkInterfaces";
    private static final String RESOURCE_DELIMITER = "/";
    private static final String LOW_PRIORITY_INSTANCE_ID_TEMPLATE = "(az-[a-z0-9]{16})[0-9A-Z]{6}";
    private static final Pattern LOW_PRIORITY_VM_NAME_PATTERN = Pattern.compile(LOW_PRIORITY_INSTANCE_ID_TEMPLATE);
    private static final InstanceViewStatus SCALE_SET_FAILED_STATUS;
    private static final String SUCCEEDED = "Succeeded";

    static {
        SCALE_SET_FAILED_STATUS = new InstanceViewStatus();
        SCALE_SET_FAILED_STATUS.withCode(VM_FAILED_STATE + "/preempted");
        SCALE_SET_FAILED_STATUS.withMessage("Low priority instance was preempted");
    }

    private final MessageHelper messageHelper;

    public void startInstance(final AzureRegion region, final String instanceId) {
        getVmByName(region.getAuthFile(), region.getResourceGroup(), instanceId).start();
    }

    public void stopInstance(final AzureRegion region, final String instanceId) {
        getVmByName(region.getAuthFile(), region.getResourceGroup(), instanceId).powerOff();
    }

    public void terminateInstance(final AzureRegion region, final String instanceId) {
        final Azure azure = buildClient(region.getAuthFile());
        final String instanceName = getInstanceResourceName(region, instanceId);
        resourcesByTag(region, instanceName)
                .sorted(this::resourcesTerminationOrder)
                .map(GenericResource::id)
                .forEach(resource -> azure.genericResources().deleteById(resource));
    }

    private String getInstanceResourceName(final AzureRegion region, final String instanceId) {
        return getVmResource(region, instanceId)
                .map(Resource::tags)
                .map(Map::entrySet)
                .map(Set::stream)
                .orElseGet(Stream::empty)
                .filter(it -> it.getKey().equals(TAG_NAME))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new AzureException(messageHelper.getMessage(
                        MessageConstants.ERROR_AZURE_INSTANCE_NOT_FOUND, instanceId)));
    }

    private Optional<? extends Resource> getVmResource(final AzureRegion region, final String instanceId) {
        final Optional<String> scaleSetName = getScaleSetName(instanceId);
        if(scaleSetName.isPresent()) {
            return findVmScaleSetByName(region, scaleSetName.get());
        } else {
            return findVmByName(region, instanceId);
        }
    }

    private Optional<String> getScaleSetName(final String instanceId) {
        final Matcher matcher = LOW_PRIORITY_VM_NAME_PATTERN.matcher(instanceId);
        return matcher.matches() ? Optional.ofNullable(matcher.group(1)) : Optional.empty();
    }

    private int resourcesTerminationOrder(final GenericResource r1, final GenericResource r2) {
        return resourceType(r1).startsWith(VIRTUAL_MACHINE_PREFIX)
                || resourceType(r1).equals(NETWORK_INTERFACES) && !resourceType(r2).startsWith(VIRTUAL_MACHINE_PREFIX)
                ? -1 : 0;
    }

    private String resourceType(final GenericResource resource) {
        return last(resource.resourceType().split(RESOURCE_DELIMITER));
    }

    private String last(final String[] items) {
        return items[items.length - 1];
    }

    public AzureVirtualMachineStats getRunningVMByRunId(final AzureRegion region, final String tagValue) {
        final AzureVirtualMachineStats virtualMachine = getVMStatsByTag(region, tagValue);
        final PowerState powerState = virtualMachine.getPowerState();
        if (!powerState.equals(PowerState.RUNNING) && !powerState.equals(PowerState.STARTING)) {
            throw new AzureException(messageHelper.getMessage(
                    MessageConstants.ERROR_AZURE_INSTANCE_NOT_RUNNING, tagValue, powerState));
        }
        return virtualMachine;
    }

    public AzureVirtualMachineStats getAliveVMByRunId(final AzureRegion region, final String tagValue) {
        return getVMStatsByTag(region, tagValue);
    }

    public Optional<VirtualMachine> findVmByName(final AzureRegion region, final String instanceId) {
        return findVmByName(region.getAuthFile(), region.getResourceGroup(), instanceId);
    }

    private Optional<VirtualMachineScaleSet> findVmScaleSetByName(final AzureRegion region,
                                                                  final String scaleSetName) {
        return findVmScaleSetByName(region.getAuthFile(), region.getResourceGroup(), scaleSetName);
    }

    public NetworkInterface getVMNetworkInterface(final String authFile, final VirtualMachine vm) {
        final String interfaceId = vm.primaryNetworkInterfaceId();
        return buildClient(authFile).networkInterfaces().getById(interfaceId);
    }

    public Optional<InstanceViewStatus> getFailingVMStatus(final AzureRegion region, final String vmName) {
        final Optional<String> scaleSetName = getScaleSetName(vmName);
        if(scaleSetName.isPresent()) {
            Optional<VirtualMachineScaleSet> scaleSet = findVmScaleSetByName(region, scaleSetName.get());
            if (scaleSet.isPresent() && scaleSet.get().inner().provisioningState().equals(SUCCEEDED)) {
                PagedList<VirtualMachineScaleSetVM> scaleSetVMs = scaleSet.get().virtualMachines().list();
                return scaleSetVMs.size() > 0
                        ? findFailedStatus(scaleSetVMs.get(0).instanceView().statuses())
                        : Optional.of(SCALE_SET_FAILED_STATUS);
            }
            return Optional.empty();
        } else {
            final Optional<VirtualMachine> virtualMachine = findVmByName(region, vmName);
            if (!virtualMachine.isPresent()) {
                return Optional.empty();
            }

            final List<InstanceViewStatus> statuses = virtualMachine
                    .map(VirtualMachine::instanceView)
                    .map(VirtualMachineInstanceView::statuses)
                    .orElseGet(Collections::emptyList);
            if (CollectionUtils.isEmpty(statuses)) {
                log.debug("Virtual machine found, but status is not available");
                return Optional.empty();
            }

            return findFailedStatus(statuses);
        }
    }

    private Optional<InstanceViewStatus> findFailedStatus(final List<InstanceViewStatus> statuses) {
        return statuses.stream()
                .filter(status -> status.code().startsWith(VM_FAILED_STATE))
                .findFirst();
    }

    private VirtualMachine getVmByName(final String authFile,
                                       final String resourceGroup,
                                       final String instanceId) {
        return findVmByName(authFile, resourceGroup, instanceId)
                .orElseThrow(() -> new AzureException(messageHelper.getMessage(
                        MessageConstants.ERROR_AZURE_INSTANCE_NOT_FOUND, instanceId)));
    }

    private Optional<VirtualMachine> findVmByName(final String authFile,
                                                  final String resourceGroup,
                                                  final String instanceId) {
        return Optional.of(buildClient(authFile).virtualMachines())
                .map(client -> client.getByResourceGroup(resourceGroup, instanceId));
    }

    private Optional<VirtualMachineScaleSet> findVmScaleSetByName(final String authFile,
                                                  final String resourceGroup,
                                                  final String scaleSetName) {
        return Optional.of(buildClient(authFile).virtualMachineScaleSets())
                .map(client -> client.getByResourceGroup(resourceGroup, scaleSetName));
    }

    private AzureVirtualMachineStats getVMStatsByTag(final AzureRegion region, final String tagValue) {
        final Azure azure = buildClient(region.getAuthFile());
        final PagedList<GenericResource> resources = azure.genericResources()
                .listByTag(region.getResourceGroup(), TAG_NAME, tagValue);
        return findVMContainerInPagedResult(resources.currentPage(), resources)
                .map(vmc -> getVmStatsByVmContainer(azure, vmc))
                .orElseThrow(() -> new AzureException(messageHelper.getMessage(
                        MessageConstants.ERROR_AZURE_INSTANCE_NOT_FOUND, tagValue)));
    }

    private AzureVirtualMachineStats getVmStatsByVmContainer(final Azure azure, final GenericResource vmc) {
        if (vmc.resourceType().equals(VIRTUAL_MACHINE_SCALE_SET_TYPE)) {
            final VirtualMachineScaleSetVM scaleSetVM = azure.virtualMachineScaleSets().getById(vmc.id())
                    .virtualMachines().list().stream().findFirst()
                    .orElseThrow(() -> new AzureException(messageHelper.getMessage(
                            MessageConstants.ERROR_AZURE_SCALE_SET_DOESNT_CONTAIN_VMS, vmc.id())));
            return AzureVirtualMachineStats.fromScaleSetVirtualMachine(scaleSetVM);
        } else if (vmc.resourceType().equals(VIRTUAL_MACHINES_TYPE)){
            return AzureVirtualMachineStats.fromVirtualMachine(azure.virtualMachines().getById(vmc.id()));
        }
        throw new AzureException(messageHelper.getMessage(
                MessageConstants.ERROR_AZURE_RESOURCE_IS_NOT_VM_LIKE, vmc.id()));
    }

    private Optional<GenericResource> findVMContainerInPagedResult(final Page<GenericResource> currentPage,
                                                                   final PagedList<GenericResource> resources) {
        if (currentPage == null || CollectionUtils.isEmpty(currentPage.items())) {
            return Optional.empty();
        }
        final Optional<GenericResource> virtualMachineContainer = currentPage.items().stream()
                .filter(r -> r.resourceType().startsWith(VIRTUAL_MACHINE_PREFIX)).findFirst();
        return virtualMachineContainer.isPresent() ? virtualMachineContainer : checkNextPage(currentPage, resources);
    }

    private Optional<GenericResource> checkNextPage(final Page<GenericResource> currentPage,
                                                    final PagedList<GenericResource> resources) {
        if (resources.hasNextPage()) {
            try {
                return findVMContainerInPagedResult(resources.nextPage(currentPage.nextPageLink()), resources);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
        return Optional.empty();
    }

    private Stream<GenericResource> resourcesByTag(final AzureRegion region, final String tagValue) {
        final Azure azure = buildClient(region.getAuthFile());
        final PagedList<GenericResource> resources = azure.genericResources()
                .listByTag(region.getResourceGroup(), TAG_NAME, tagValue);
        return resourcesInPagedResult(resources.currentPage(), resources);
    }

    private Stream<GenericResource> resourcesInPagedResult(final Page<GenericResource> currentPage,
                                                           final PagedList<GenericResource> resources) {
        if (currentPage == null || CollectionUtils.isEmpty(currentPage.items())) {
            return Stream.empty();
        }
        return Stream.concat(currentPage.items().stream(), resourcesFromNextPage(currentPage, resources));
    }

    private Stream<GenericResource> resourcesFromNextPage(final Page<GenericResource> currentPage,
                                                          final PagedList<GenericResource> resources) {
        if (resources.hasNextPage()) {
            try {
                return resourcesInPagedResult(resources.nextPage(currentPage.nextPageLink()), resources);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
        return Stream.empty();
    }

    private Azure buildClient(final String authFile) {
        try {
            final File credFile = new File(authFile);
            return Azure.configure()
                    .withLogLevel(LogLevel.BASIC)
                    .authenticate(credFile)
                    .withDefaultSubscription();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new AzureException(e);
        }
    }
}
