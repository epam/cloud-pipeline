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

import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.cloud.gcp.GCPClient;
import com.epam.pipeline.manager.cloud.gcp.resource.GCPMachine;
import com.epam.pipeline.manager.cloud.gcp.resource.GCPObject;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.MachineType;
import com.google.api.services.compute.model.MachineTypeList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Google Cloud Provider predefined compute machines extractor.
 *
 * Extracts all available Google Cloud Provider predefined machines in a specific region.
 *
 * Retrieves machine family from a corresponding machine type name using one of the following patterns:
 * {prefix}-{instance_family}-{postfix}
 * {prefix}-{instance_family}
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class GCPPredefinedMachineExtractor implements GCPObjectExtractor {

    private final GCPClient gcpClient;

    @Override
    public List<GCPObject> extract(final GCPRegion region) {
        try {
            final Compute client = gcpClient.buildComputeClient(region);
            final String zone = region.getRegionCode();
            final MachineTypeList response = client.machineTypes().list(region.getProject(), zone).execute();
            final List<MachineType> machineTypes = Optional.of(response)
                    .map(MachineTypeList::getItems)
                    .orElseGet(Collections::emptyList);
            return machineTypes.stream()
                    .filter(machine -> machine.getName() != null)
                    .map(this::toMachine)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.warn("Predefined GCP machines extraction has failed.", e);
            return Collections.emptyList();
        }
    }

    private Optional<GCPMachine> toMachine(final MachineType machineType) {
        final String[] elements = Optional.ofNullable(machineType.getName()).orElse("").split("-");
        if (elements.length < 2) {
            return Optional.empty();
        }
        final String name = machineType.getName();
        final String family = elements[1];
        if (elements.length == 2) {
            return Optional.of(new GCPMachine(name, family, 1, 0, 0, null));
        }
        if (elements.length == 3) {
            try {
                final int cpu = machineType.getGuestCpus();
                final double memory = new BigDecimal((double) machineType.getMemoryMb() / 1024)
                        .setScale(2, RoundingMode.HALF_EVEN)
                        .doubleValue();
                return Optional.of(new GCPMachine(name, family, cpu, memory, 0, null));
            } catch (NumberFormatException e) {
                log.warn(String.format("GCP Machine Type name '%s' parsing has failed.", machineType), e);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
