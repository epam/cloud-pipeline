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
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.exception.cloud.azure.AzureException;
import com.microsoft.azure.Page;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.InstanceViewStatus;
import com.microsoft.azure.management.compute.PowerState;
import com.microsoft.azure.management.compute.VirtualMachine;
import com.microsoft.azure.management.network.NetworkInterface;
import com.microsoft.azure.management.resources.GenericResource;
import com.microsoft.rest.LogLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
@Service
public class AzureVMService {
    private static final String VM_FAILED_STATE = "ProvisioningState/failed";
    private static final String TAG_NAME = "Name";
    private static final String VIRTUAL_MACHINES = "virtualMachines";
    private final MessageHelper messageHelper;

    public void startInstance(final AzureRegion region, final String instanceId) {
        getVmByName(region.getAuthFile(), region.getResourceGroup(), instanceId).start();
    }

    public void stopInstance(final AzureRegion region, final String instanceId) {
        getVmByName(region.getAuthFile(), region.getResourceGroup(), instanceId).powerOff();
    }

    public VirtualMachine getRunningVMByRunId(final AzureRegion region, final String tagValue) {
        final Azure azure = buildClient(region.getAuthFile());
        final VirtualMachine virtualMachine = findVMByTag(region, tagValue, azure);
        final PowerState powerState = virtualMachine.powerState();
        if (!powerState.equals(PowerState.RUNNING) && !powerState.equals(PowerState.STARTING)) {
            throw new AzureException(messageHelper.getMessage(
                    MessageConstants.ERROR_AZURE_INSTANCE_NOT_RUNNING, tagValue, powerState));
        }
        return virtualMachine;
    }

    public Optional<VirtualMachine> findVmByName(final AzureRegion region,
                                                 final String instanceId) {
        try {
            return Optional.ofNullable(getVmByName(region.getAuthFile(), region.getResourceGroup(), instanceId));
        } catch (NoSuchElementException e) {
            log.warn("Azure virtual machine retrieving has failed.", e);
            return Optional.empty();
        }
    }

    public NetworkInterface getVMNetworkInterface(final String authFile, final VirtualMachine vm) {
        final String interfaceId = vm.primaryNetworkInterfaceId();
        return buildClient(authFile).networkInterfaces().getById(interfaceId);
    }

    public Optional<InstanceViewStatus> getFailingVMStatus(final AzureRegion region, final String vmName) {
        final VirtualMachine virtualMachine = buildClient(region.getAuthFile()).virtualMachines()
                .getByResourceGroup(region.getResourceGroup(), vmName);
        if (virtualMachine == null) {
            return Optional.empty();
        }

        final List<InstanceViewStatus> statuses = virtualMachine.instanceView().statuses();
        if (CollectionUtils.isEmpty(statuses)) {
            log.debug("Virtual machine found, but status is not available");
            return Optional.empty();
        }

        return statuses.stream()
                .filter(status -> status.code().startsWith(VM_FAILED_STATE))
                .findFirst();
    }

    private VirtualMachine getVmByName(final String authFile,
                                       final String resourceGroup,
                                       final String instanceId) {
        return buildClient(authFile).virtualMachines().getByResourceGroup(resourceGroup, instanceId);
    }

    private VirtualMachine findVMByTag(final AzureRegion region,
                                       final String tagValue,
                                       final Azure azure) {
        final PagedList<GenericResource> resources = azure.genericResources()
                .listByTag(region.getResourceGroup(), TAG_NAME, tagValue);
        return findVMInPagedResult(resources.currentPage(), resources)
                .map(resource  -> azure.virtualMachines().getById(resource.id()))
                .orElseThrow(() -> new AzureException(messageHelper.getMessage(
                        MessageConstants.ERROR_AZURE_INSTANCE_NOT_FOUND, tagValue)));
    }

    private Optional<GenericResource> findVMInPagedResult(final Page<GenericResource> currentPage,
                                                          final PagedList<GenericResource> resources) {
        if (currentPage == null || CollectionUtils.isEmpty(currentPage.items())) {
            return Optional.empty();
        }
        final Optional<GenericResource> virtualMachine = currentPage.items().stream()
                .filter(r -> r.resourceType().equals(VIRTUAL_MACHINES)).findFirst();
        return virtualMachine.isPresent() ? virtualMachine : checkNextPage(currentPage, resources);
    }

    private Optional<GenericResource> checkNextPage(final Page<GenericResource> currentPage,
                                                    final PagedList<GenericResource> resources) {
        if (resources.hasNextPage()) {
            try {
                return findVMInPagedResult(resources.nextPage(currentPage.nextPageLink()), resources);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
        return Optional.empty();
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
