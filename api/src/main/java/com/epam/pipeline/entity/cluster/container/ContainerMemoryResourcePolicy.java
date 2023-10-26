/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.cluster.container;

public enum ContainerMemoryResourcePolicy {

    /**
     * container_mem_request = 0
     * container_mem_limit = 0
     */

    NO_LIMIT,
    /**
     * container_mem_request = $launch.container.memory.resource.request
     * container_mem_limit = node_mem - 1 GiB
     */
    NODE,

    /**
     * container_mem_request = $launch.container.memory.resource.request
     * container_mem_limit = node_mem + swap_mem - 1 GiB
     */
    NODE_SWAP,

    /**
     * container_mem_request = $launch.container.memory.resource.request
     * container_mem_limit = node_mem - $cluster.node.kubelet.mem - $cluster.node.system.mem
     */
    AUTO
}
