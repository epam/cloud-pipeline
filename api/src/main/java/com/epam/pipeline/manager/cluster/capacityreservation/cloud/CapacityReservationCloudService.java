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

import com.epam.pipeline.controller.vo.cluster.capacityreservation.CapacityBlockOfferingRequest;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockInstanceTypeInfo;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockOffering;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CloudCapacityReservation;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a cloud provider has to offer before the platform can reserve capacity on it.
 *
 * <p>One implementation per provider, discovered by {@code CapacityReservationCloudFacade} through
 * {@link #getProvider()}. A provider with no implementation cannot back a node pool with a reservation at all.
 *
 * <p>The contract, in the order the monitor uses it: {@link #getMinimumLead()} and {@link #candidateZones} say what
 * the provider will even consider, {@link #createFutureDated} asks for the capacity (or
 * {@link #purchaseCapacityBlock}, for an exact offering named rather than a window to search), {@link #describe}
 * follows what became of the request, {@link #launchSpecification} says how a node consumes the reservation, and
 * {@link #cancel} gives it back - except a capacity block, which it refuses outright rather than attempting,
 * since no provider lets one be given back once bought; a caller must not call it for one at all, rather than
 * rely on it to fail safely. {@link #findByClientToken} answers what an attempt bought when its reply was lost.
 *
 * <p>The calls that can lead to capacity being paid for - {@link #candidateZones}, {@link #createFutureDated},
 * {@link #purchaseCapacityBlock} and {@link #findByClientToken} - raise
 * {@link CapacityReservationSubmissionUncertainException} rather than reporting a refusal when they fail in a way
 * that leaves the outcome unknown, so the caller can retry the same attempt. Unlike {@link #createFutureDated},
 * {@link #purchaseCapacityBlock} has no provider-side idempotency token to make that retry safe on its own - the
 * caller must not attempt it again without first confirming through {@link #findByClientToken} that the earlier,
 * uncertain attempt did not already buy it. {@link #describe} and {@link #cancel} report their failures as they
 * come.
 *
 * <p>{@link #listCapacityBlockOfferings} and {@link #listCapacityBlockInstanceTypes} stand apart from the rest: they
 * search a catalogue before any {@link CapacityReservation} exists, so they take a region directly rather than a
 * reservation to read one from.
 */
public interface CapacityReservationCloudService {

    CloudProvider getProvider();

    Duration getMinimumLead();

    List<String> candidateZones(CapacityReservation reservation);

    Map<String, Object> launchSpecification(CapacityReservation reservation);

    CloudCapacityReservation createFutureDated(CapacityReservation reservation);

    CloudCapacityReservation purchaseCapacityBlock(CapacityReservation reservation);

    CloudCapacityReservation describe(CapacityReservation reservation);

    void cancel(CapacityReservation reservation);

    Optional<CloudCapacityReservation> findByClientToken(CapacityReservation reservation, String clientToken);

    List<CapacityBlockOffering> listCapacityBlockOfferings(AbstractCloudRegion region,
                                                            CapacityBlockOfferingRequest request);

    List<CapacityBlockInstanceTypeInfo> listCapacityBlockInstanceTypes(AbstractCloudRegion region);
}
