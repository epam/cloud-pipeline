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

package com.epam.pipeline.entity.region;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@EqualsAndHashCode
public class GCPCustomInstanceType {
    private int cpu;
    private double ram;
    private int gpu;
    private String gpuType;
    private String family;

    public static GCPCustomInstanceType withCpu(final int cpu,
                                                final double ram) {
        return new GCPCustomInstanceType(cpu, ram, 0, null, null);
    }

    public static GCPCustomInstanceType withCpu(final int cpu,
                                                final double ram,
                                                final String family) {
        return new GCPCustomInstanceType(cpu, ram, 0, null, family);
    }

    public static GCPCustomInstanceType withGpu(final int cpu,
                                                final double ram,
                                                final int gpu,
                                                final String gpuType) {
        return new GCPCustomInstanceType(cpu, ram, gpu, gpuType, null);
    }
}
