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

import com.epam.pipeline.entity.region.GCPCustomInstanceType;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.cloud.gcp.resource.AbstractGCPObject;
import com.epam.pipeline.manager.cloud.gcp.resource.GCPMachine;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
    private static final double EXTENDED_FACTOR = 6.5;

    @Override
    public List<AbstractGCPObject> extract(final GCPRegion region) {
        return CollectionUtils.emptyIfNull(region.getCustomInstanceTypes())
                .stream()
                .filter(type -> type.getCpu() > 0 && type.getRam() >= 0)
                .map(this::toGcpMachine)
                .collect(Collectors.toList());
    }

    private GCPMachine toGcpMachine(final GCPCustomInstanceType type) {
        final String name = type.getGpu() > 0 && StringUtils.isNotBlank(type.getGpuType())
                ? gpuCustomGpuMachine(type)
                : customCpuMachine(type);
        final double extendedMemory = type.getRam() / type.getCpu() > EXTENDED_FACTOR
                ? type.getRam() - type.getCpu() * EXTENDED_FACTOR
                : 0.0;
        final double defaultMemory = type.getRam() - extendedMemory;
        return new GCPMachine(name, CUSTOM_FAMILY, type.getCpu(), defaultMemory, extendedMemory,
                type.getGpu(), type.getGpuType());
    }

    private String gpuCustomGpuMachine(final GCPCustomInstanceType type) {
        final String cpuMachineName = customCpuMachine(type);
        return String.format("gpu-%s-%s-%s", cpuMachineName, type.getGpuType().toLowerCase(), type.getGpu());
    }

    private String customCpuMachine(final GCPCustomInstanceType type) {
        return String.format("%s-%s-%s", CUSTOM_FAMILY, type.getCpu(), (int) (type.getRam() * MEGABYTES_IN_GIGABYTE));
    }
}
