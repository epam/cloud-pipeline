/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.cluster.capacityreservation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One instance type a region currently sells as a capacity block, with enough of its specification - as AWS's
 * {@code DescribeInstanceTypes} answers it - for a user to tell the offered types apart before searching
 * {@link CapacityBlockOffering}s for one of them.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapacityBlockInstanceTypeInfo {

    private String instanceType;

    private Integer vCPU;
    private Long memoryInMiB;

    private Integer gpuCount;
    private String gpuDeviceName;
}
