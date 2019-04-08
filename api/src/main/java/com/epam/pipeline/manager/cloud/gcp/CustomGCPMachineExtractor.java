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

package com.epam.pipeline.manager.cloud.gcp;

import com.epam.pipeline.entity.region.GCPCustomInstanceType;
import com.epam.pipeline.entity.region.GCPRegion;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class CustomGCPMachineExtractor implements GCPMachineExtractor {

    private static final String CUSTOM_FAMILY = "custom";

    @Override
    public List<GCPMachine> extract(final GCPRegion region) {
        return CollectionUtils.emptyIfNull(region.getCustomInstanceTypes())
                .stream()
                .filter(type -> type.getCpu() > 0 && type.getRam() >= 0)
                .map(type -> {
                    if (type.getGpu() > 0 && StringUtils.isNotBlank(type.getGpuType())) {
                        final String name = gpuCustomGpuMachineName(type);
                        return GCPMachine.withGpu(name, CUSTOM_FAMILY, type.getCpu(), type.getRam(), type.getGpu(),
                                type.getGpuType());
                    } else {
                        final String name = gpuCustomCpuMachineName(type);
                        return GCPMachine.withCpu(name, CUSTOM_FAMILY, type.getCpu(), type.getRam());
                    }
                })
                .collect(Collectors.toList());
    }

    private String gpuCustomGpuMachineName(final GCPCustomInstanceType type) {
        final String cpuMachineName = gpuCustomCpuMachineName(type);
        return String.format("gpu-%s-%s-%s", cpuMachineName, type.getGpuType().toLowerCase(), type.getGpu());
    }

    private String gpuCustomCpuMachineName(final GCPCustomInstanceType type) {
        return String.format("%s-%s-%s", CUSTOM_FAMILY, type.getCpu(), (int) (type.getRam() * 1024));
    }
}
