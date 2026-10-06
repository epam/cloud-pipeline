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

package com.epam.pipeline.manager.cluster.capacityreservation.cloud;

import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityReservation;
import com.epam.pipeline.entity.region.CloudProvider;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CapacityReservationCloudService {

    CloudProvider getProvider();

    Duration getMinimumLead();

    List<String> candidateZones(CapacityReservation reservation);

    Map<String, Object> launchSpecification(CapacityReservation reservation);

    CloudCapacityReservation createFutureDated(CapacityReservation reservation);

    CloudCapacityReservation describe(CapacityReservation reservation);

    void cancel(CapacityReservation reservation);

    Optional<CloudCapacityReservation> findByClientToken(CapacityReservation reservation, String clientToken);
}
