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
 * How a caller narrows the pool list. Every field is optional and an unset field means "any", so an empty filter
 * is the same request as an unfiltered list.
 *
 * <p>This is how a client finds the pools it could actually use for a given instance type - the question the
 * {@code launch.reservation.parameters} preference answers today, and which this replaces once sharable pools are
 * the way that is configured.
 */
@Data
public class NodePoolFilterVO {

    private String instanceType;
    private Long regionId;
    private PriceType priceType;
    private NodePoolType poolType;

    /**
     * Restricts to reservation-backed pools, or to plain ones. A plain column predicate rather than a join,
     * because whether a pool is reservation-backed is recorded on the pool itself.
     */
    private Boolean capacityReservation;

    /**
     * Restricts to pools that can currently take work. Evaluated in the manager rather than in SQL, because
     * whether a pool is active depends on its schedule and its date window, not on a single column.
     */
    private Boolean active;
}
