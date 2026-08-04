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

package com.epam.pipeline.entity.credits;

import com.epam.pipeline.entity.cluster.InstanceOffer;
import lombok.Value;

/**
 * A single instance-type group within a cluster launch: one offer and how many replicas of it are requested.
 * Used to compute the total credit cost for heterogeneous clusters where different node groups may use
 * different instance types.
 */
@Value
public class ClusterReplicaGroup {
    InstanceOffer offer;
    int replicas;
}
