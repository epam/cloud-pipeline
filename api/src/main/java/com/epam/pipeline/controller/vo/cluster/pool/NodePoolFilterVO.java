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

package com.epam.pipeline.controller.vo.cluster.pool;

import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.cluster.pool.NodePoolType;
import lombok.Data;

/**
 * Filter for listing node pools: each field that is set narrows the result, and one left {@code null} does not.
 *
 * <p>{@code isCapacityReservation} tells reservation-backed pools from ordinary ones. {@code active} is matched
 * against the pool's state at the moment of the request, so {@code true} keeps the pools that are active then and
 * {@code false} the ones that are not: a pool is active when it has nodes, the request falls inside its window, and
 * its schedule, if it has one, says so too.
 */
@Data
public class NodePoolFilterVO {

    private String instanceType;
    private Long regionId;
    private PriceType priceType;
    private NodePoolType poolType;

    private Boolean isCapacityReservation;

    private Boolean active;
}
