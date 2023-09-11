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

package com.epam.pipeline.manager.cloud.gcp.extractor;

import com.epam.pipeline.entity.cluster.GpuDevice;
import com.epam.pipeline.entity.region.GCPCustomInstanceType;
import com.epam.pipeline.entity.region.GCPCustomVMType;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.cloud.gcp.resource.AbstractGCPObject;
import com.epam.pipeline.manager.cloud.gcp.resource.GCPMachine;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Precision;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Google Cloud Provider custom machines extractor.
 *
 * Extracts all configured custom instance type for a specific region.
 */
@Component
public class GCPCustomMachineExtractor implements GCPObjectExtractor {

    private static final int MEGABYTES_IN_GIGABYTE = 1024;
    private static final String CUSTOM_FAMILY = "custom";
    private static final double FACTOR_PRECISION = 0.1;
    private static final String NVIDIA = "NVIDIA";

    @Override
    public List<AbstractGCPObject> extract(final GCPRegion region) {
        return CollectionUtils.emptyIfNull(region.getCustomInstanceTypes())
                .stream()
                .filter(type -> type.getCpu() > 0 && type.getRam() >= 0)
                .map(this::toMachine)
                .collect(Collectors.toList());
    }

    private GCPMachine toMachine(final GCPCustomInstanceType type) {
        final GCPCustomVMType instanceFamily = StringUtils.isNotBlank(type.getFamily())
                ? GCPCustomVMType.valueOf(type.getFamily())
                : GCPCustomVMType.n1;

        final boolean isExtendedInstance = isInstanceExtended(type, instanceFamily);
        final String name = buildMachineType(type, isExtendedInstance, instanceFamily);
        final double extendedMemory = calculateExtendedMemory(isExtendedInstance, type, instanceFamily);
        final double defaultMemory = type.getRam() - extendedMemory;
        if (type.getGpu() > 0 && StringUtils.isNotBlank(type.getGpuType())) {
            return GCPMachine.withGpu(name, CUSTOM_FAMILY, type.getCpu(), defaultMemory, extendedMemory,
                    type.getGpu(), GpuDevice.from(type.getGpuType(), NVIDIA), instanceFamily);
        }
        return GCPMachine.withCpu(name, CUSTOM_FAMILY, type.getCpu(), defaultMemory, extendedMemory, instanceFamily);
    }

    private String gpuCustomGpuMachine(final GCPCustomInstanceType type, final String cpuMachineName) {
        return String.format("gpu-%s-%s-%s", cpuMachineName, type.getGpuType().toLowerCase(), type.getGpu());
    }

    private String customCpuMachine(final GCPCustomInstanceType type, final GCPCustomVMType instanceFamily) {
        return String.format("%s%s-%s-%s", instanceFamily.getPrefix(), CUSTOM_FAMILY, type.getCpu(),
                (int) (type.getRam() * MEGABYTES_IN_GIGABYTE));
    }

    private String withExtendedMachineType(final String name, final boolean isExtendedInstance) {
        return isExtendedInstance ? String.format("%s-ext", name) : name;
    }

    private String getCustomMachineType(final GCPCustomInstanceType type, final GCPCustomVMType instanceFamily) {
        final String cpuMachineName = customCpuMachine(type, instanceFamily);
        return type.getGpu() > 0 && StringUtils.isNotBlank(type.getGpuType())
                ? gpuCustomGpuMachine(type, cpuMachineName)
                : cpuMachineName;
    }

    private String buildMachineType(final GCPCustomInstanceType type, final boolean isExtendedInstance,
                                    final GCPCustomVMType instanceFamily) {
        return withExtendedMachineType(getCustomMachineType(type, instanceFamily), isExtendedInstance);
    }

    private boolean isInstanceExtended(final GCPCustomInstanceType type, final GCPCustomVMType instanceFamily) {
        final double ramCpuFactor = type.getRam() / type.getCpu();
        return instanceFamily.isSupportExternal()
                && Precision.compareTo(ramCpuFactor, instanceFamily.getExtendedFactor(), FACTOR_PRECISION) > 0;
    }

    private double calculateExtendedMemory(final boolean isExtendedInstance, final GCPCustomInstanceType type,
                                           final GCPCustomVMType instanceFamily) {
        return isExtendedInstance ? type.getRam() - type.getCpu() * instanceFamily.getExtendedFactor() : 0.0;
    }
}
