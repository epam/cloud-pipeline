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

/**
 * What a {@link CapacityReservationType#CAPACITY_BLOCK} offering or reservation is made of.
 *
 * <p>AWS keeps no discriminator of its own - a {@code CapacityBlockOffering} simply carries one of two field pairs,
 * {@code instanceType}/{@code instanceCount} or {@code ultraserverType}/{@code ultraserverCount}. This is the
 * platform's own classification of which pair applies.
 *
 * <ul>
 *   <li>{@link #INSTANCE} - a block of ordinary instances. The only kind the platform submits today.</li>
 *   <li>{@link #ULTRASERVER} - a block of whole UltraServers. A purchased UltraServer block's
 *       {@code capacityReservationIds} is a list, not the single id a {@link CapacityReservation} holds, so
 *       supporting it needs more than this constant. Rejected wherever it is checked.</li>
 * </ul>
 */
public enum CapacityBlockResourceType {

    INSTANCE,

    ULTRASERVER
}
