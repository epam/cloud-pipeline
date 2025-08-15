/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.cluster;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeResources {
    private String nodeName;
    /**
     * Provides content from k8s node's allocatable field that provides the amount of resources on a Node
     * that is available to be consumed by normal Pods.
     */
    private Quantities total;
    /**
     * Contains available resources accumulated from all active pods (for corresponding node)
     * where quantities loaded from Requests section from pod container specification
     * (cpu/memory/nvidia.com/gpu).
     */
    private Quantities used;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Quantities {
        private Long cpu;
        private Long gpu;
        private Long memory;

        public static Quantities empty() {
            return Quantities.builder()
                    .cpu(0L)
                    .gpu(0L)
                    .memory(0L)
                    .build();
        }
    }
}
