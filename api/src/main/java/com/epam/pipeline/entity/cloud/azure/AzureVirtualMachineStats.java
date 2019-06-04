/*
 *
 *  * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.epam.pipeline.entity.cloud.azure;

import com.epam.pipeline.exception.cloud.azure.AzureException;
import com.microsoft.azure.management.compute.PowerState;
import com.microsoft.azure.management.compute.VirtualMachine;
import com.microsoft.azure.management.compute.VirtualMachineScaleSetVM;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AzureVirtualMachineStats {

    public static final String NO_NET_INTERFACE_FOR_VM = "No Network interface were found for machine: %s";

    private PowerState powerState;
    private String name;
    private String privateIP;

    public static AzureVirtualMachineStats fromVirtualMachine(final VirtualMachine machine) {
        return new AzureVirtualMachineStats(
                machine.powerState(),
                machine.name(),
                machine.getPrimaryNetworkInterface().primaryIPConfiguration().privateIPAddress());
    }

    public static AzureVirtualMachineStats fromScaleSetVirtualMachine(final VirtualMachineScaleSetVM machine) {
        return new AzureVirtualMachineStats(
                machine.powerState(),
                machine.computerName(),
                machine.listNetworkInterfaces().stream()
                        .findFirst()
                        .orElseThrow(() -> new AzureException(
                                String.format(NO_NET_INTERFACE_FOR_VM, machine.computerName())))
                        .primaryIPConfiguration().privateIPAddress());
    }
}
