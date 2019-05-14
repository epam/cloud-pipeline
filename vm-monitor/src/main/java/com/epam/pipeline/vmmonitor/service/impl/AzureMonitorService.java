/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
 */

package com.epam.pipeline.vmmonitor.service.impl;

import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.vmmonitor.model.vm.VMTag;
import com.epam.pipeline.vmmonitor.model.vm.VirtualMachine;
import com.epam.pipeline.vmmonitor.service.VMMonitorService;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.PowerState;
import com.microsoft.azure.management.resources.GenericResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
public class AzureMonitorService implements VMMonitorService<AzureRegion> {

    private static final String VIRTUAL_MACHINES = "virtualMachines";
    private static final String SUCCEEDED_STATE = "Succeeded";

    private final VMTag instanceTag;

    public AzureMonitorService(@Value("${monitor.instance.tag}") final String instanceTag) {
        this.instanceTag = VMTag.fromProperty(instanceTag);
    }

    @Override
    public List<VirtualMachine> fetchRunningVms(final AzureRegion region) {
        final Azure azure = buildClient(region.getAuthFile());
        final PagedList<GenericResource> resources = azure.genericResources()
                .listByTag(region.getResourceGroup(), instanceTag.getKey(), instanceTag.getValue());
        final Iterable<GenericResource> iterable = resources::listIterator;
        return StreamSupport.stream(iterable.spliterator(), false)
                .filter(resource -> resource.resourceType().equals(VIRTUAL_MACHINES))
                .map(resource -> azure.virtualMachines().getById(resource.id()))
                .filter(azureVm -> azureVm != null && azureVm.powerState().equals(PowerState.RUNNING)
                        && azureVm.provisioningState().equals(SUCCEEDED_STATE))
                .map(this::toVM)
                .collect(Collectors.toList());
    }

    @Override
    public CloudProvider provider() {
        return CloudProvider.AZURE;
    }

    private VirtualMachine toVM(final com.microsoft.azure.management.compute.VirtualMachine azureVM) {
        return VirtualMachine.builder()
                .instanceId(azureVM.computerName())
                .instanceName(azureVM.name())
                .privateIp(azureVM.getPrimaryNetworkInterface().primaryPrivateIP())
                .tags(azureVM.tags())
                .cloudProvider(CloudProvider.AZURE)
                .build();
    }

    private Azure buildClient(final String authFile) {
        try {
            final File credFile = new File(authFile);
            return Azure.configure()
                    .authenticate(credFile)
                    .withDefaultSubscription();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new IllegalArgumentException(e);
        }
    }
}
